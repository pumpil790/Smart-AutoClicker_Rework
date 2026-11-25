/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.domain.wordlists

import com.buzbuz.smartautoclicker.core.domain.model.condition.TextMatchType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * UTF-8 text normalization and codepoint Damerau-Levenshtein fuzzy string matching
 * for Cyrillic & Latin OCR text comparison (handling case, ё/е, whitespace, punctuation,
 * Russian grammatical case endings, and minor OCR typos).
 */
object TextMatchingUtils {

    private val EDGE_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '"', '\'', '«', '»', '(', ')', '[', ']')

    /**
     * Normalizes a string:
     * - Lowercase (Unicode-aware, including Cyrillic А-Я -> а-я)
     * - Replaces 'ё' -> 'е'
     * - Collapses consecutive whitespace into single spaces and trims
     * - Optionally strips leading/trailing punctuation
     * - Optionally folds unambiguous Latin/digit lookalikes into Cyrillic (for CYRILLIC OCR)
     */
    fun normalize(
        text: String,
        stripEdgePunctuation: Boolean = true,
        foldCyrillicHomoglyphs: Boolean = false,
    ): String {
        if (text.isEmpty()) return ""

        val cps = ArrayList<Int>(text.length)
        var prevWasSpace = true
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            i += charCount

            if (Character.isWhitespace(cp) || cp == 0x00A0) {
                if (!prevWasSpace) {
                    cps.add(' '.code)
                    prevWasSpace = true
                }
            } else {
                cps.add(normalizeCodepoint(cp, foldCyrillicHomoglyphs))
                prevWasSpace = false
            }
        }

        // Trim spaces
        var end = cps.size
        while (end > 0 && cps[end - 1] == ' '.code) end--
        var start = 0
        while (start < end && cps[start] == ' '.code) start++

        if (stripEdgePunctuation) {
            while (start < end && (isEdgePunctuation(cps[start]) || cps[start] == ' '.code)) start++
            while (end > start && (isEdgePunctuation(cps[end - 1]) || cps[end - 1] == ' '.code)) end--
        }

        if (start >= end) return ""

        val trimmed = IntArray(end - start) { idx -> cps[start + idx] }
        if (foldCyrillicHomoglyphs) {
            foldCyrillicDigitHomoglyphs(trimmed)
        }

        val sb = StringBuilder(trimmed.size)
        for (cp in trimmed) {
            sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    /**
     * Matches [recognizedText] against [targetText] using [matchType] and [thresholdPercent] (0..100).
     * Returns Pair(isMatched, scorePercent 0.0..100.0).
     */
    fun matchText(
        recognizedText: String,
        targetText: String,
        matchType: TextMatchType,
        thresholdPercent: Int,
        foldCyrillicHomoglyphs: Boolean = false,
    ): Pair<Boolean, Double> {
        val normRecognized = normalize(recognizedText, foldCyrillicHomoglyphs = foldCyrillicHomoglyphs)
        val normTarget = normalize(targetText, foldCyrillicHomoglyphs = foldCyrillicHomoglyphs)
        if (normRecognized.isEmpty() || normTarget.isEmpty()) return false to 0.0

        return when (matchType) {
            TextMatchType.EXACT_NORMALIZED -> {
                if (normRecognized == normTarget) true to 100.0 else false to 0.0
            }

            TextMatchType.FUZZY_SUBSTRING -> {
                val minSim = (thresholdPercent.coerceIn(1, 100)) / 100.0f
                val recCodepoints = normRecognized.toCodePoints()
                val targetCodepoints = normTarget.toCodePoints()
                val score = bestSubstringSimilarity(recCodepoints, targetCodepoints, minSim)
                val scorePercent = score * 100.0
                (scorePercent + 1e-3 >= thresholdPercent.coerceIn(1, 100)) to scorePercent
            }
        }
    }

    /**
     * Matches [recognizedText] against a list of candidate [entries].
     * Returns Pair(isMatched, bestScorePercent 0.0..100.0).
     */
    fun matchAgainstList(
        recognizedText: String,
        entries: Collection<String>,
        matchType: TextMatchType,
        thresholdPercent: Int,
        foldCyrillicHomoglyphs: Boolean = false,
    ): Pair<Boolean, Double> {
        val normRecognized = normalize(recognizedText, foldCyrillicHomoglyphs = foldCyrillicHomoglyphs)
        if (normRecognized.isEmpty() || entries.isEmpty()) return false to 0.0

        if (matchType == TextMatchType.EXACT_NORMALIZED) {
            for (entry in entries) {
                if (normalize(entry, foldCyrillicHomoglyphs = foldCyrillicHomoglyphs) == normRecognized) {
                    return true to 100.0
                }
            }
            return false to 0.0
        }

        val threshold = thresholdPercent.coerceIn(1, 100)
        val minSim = threshold / 100.0f
        val recCodepoints = normRecognized.toCodePoints()
        var bestScorePercent = 0.0

        for (entry in entries) {
            val normEntry = normalize(entry, foldCyrillicHomoglyphs = foldCyrillicHomoglyphs)
            if (normEntry.isEmpty()) continue
            if (normRecognized == normEntry) return true to 100.0

            val entryCodepoints = normEntry.toCodePoints()
            val score = bestSubstringSimilarity(recCodepoints, entryCodepoints, minSim) * 100.0
            if (score > bestScorePercent) {
                bestScorePercent = score
                if (bestScorePercent + 1e-3 >= threshold) {
                    return true to bestScorePercent
                }
            }
        }

        return (bestScorePercent + 1e-3 >= threshold) to bestScorePercent
    }

    fun bestSubstringSimilarity(
        recognized: IntArray,
        target: IntArray,
        minSimilarity: Float,
    ): Float {
        if (recognized.isEmpty() || target.isEmpty()) return 0f
        if (containsSubsequence(recognized, target)) return 1f

        val targetLen = target.size
        val recognizedLen = recognized.size

        if (recognizedLen <= targetLen + 2) {
            return similarity(recognized, target, minSimilarity)
        }

        var bestScore = 0f
        val minWindow = max(1, targetLen - 2)
        val maxWindow = min(recognizedLen, targetLen + 4)

        for (windowSize in minWindow..maxWindow) {
            for (start in 0..(recognizedLen - windowSize)) {
                val score = similaritySlice(recognized, start, windowSize, target, minSimilarity)
                if (score > bestScore) {
                    bestScore = score
                    if (bestScore >= 0.95f) return bestScore
                }
            }
        }

        return bestScore
    }

    fun similarity(recognized: IntArray, target: IntArray, minSimilarity: Float): Float =
        similaritySlice(recognized, 0, recognized.size, target, minSimilarity)

    private fun similaritySlice(
        recognized: IntArray,
        offset: Int,
        n: Int,
        target: IntArray,
        minSimilarity: Float,
    ): Float {
        val m = target.size
        if (n == 0 || m == 0) return 0f

        val maxLen = max(n, m)
        val maxAllowedDistance = floor((1f - minSimilarity) * maxLen + 1e-5f).toInt()
        if (abs(n - m) > maxAllowedDistance) return 0f

        var prevPrevRow = IntArray(m + 1)
        var prevRow = IntArray(m + 1) { it }
        var currRow = IntArray(m + 1)

        for (i in 1..n) {
            currRow[0] = i
            var rowMin = i
            val ca = recognized[offset + i - 1]

            for (j in 1..m) {
                val cb = target[j - 1]
                val cost = if (ca == cb) 0 else 1

                var value = min(
                    min(prevRow[j] + 1, currRow[j - 1] + 1),
                    prevRow[j - 1] + cost,
                )

                if (i > 1 && j > 1 && ca == target[j - 2] && recognized[offset + i - 2] == cb) {
                    value = min(value, prevPrevRow[j - 2] + 1)
                }

                currRow[j] = value
                if (value < rowMin) rowMin = value
            }

            if (rowMin > maxAllowedDistance) return 0f

            val tmp = prevPrevRow
            prevPrevRow = prevRow
            prevRow = currRow
            currRow = tmp
        }

        val distance = prevRow[m]
        if (distance > maxAllowedDistance) return 0f

        val score = 1f - distance.toFloat() / maxLen.toFloat()
        return if (score + 1e-5f >= minSimilarity) max(0f, score) else 0f
    }

    private fun containsSubsequence(haystack: IntArray, needle: IntArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun String.toCodePoints(): IntArray {
        val count = codePointCount(0, length)
        val result = IntArray(count)
        var strIdx = 0
        var outIdx = 0
        while (strIdx < length) {
            val cp = codePointAt(strIdx)
            result[outIdx++] = cp
            strIdx += Character.charCount(cp)
        }
        return result
    }

    private fun isEdgePunctuation(cp: Int): Boolean =
        cp <= 0xFFFF && cp.toChar() in EDGE_PUNCTUATION

    private fun isCyrillicCodepoint(cp: Int): Boolean =
        cp in 0x0400..0x04FF

    private fun isTokenAlnumCodepoint(cp: Int): Boolean =
        (cp in '0'.code..'9'.code) ||
            (cp in 'a'.code..'z'.code) ||
            (cp in 'A'.code..'Z'.code) ||
            isCyrillicCodepoint(cp)

    private fun foldCyrillicDigitHomoglyphs(codepoints: IntArray) {
        val n = codepoints.size
        var i = 0
        while (i < n) {
            if (!isTokenAlnumCodepoint(codepoints[i])) {
                i++
                continue
            }

            val start = i
            var hasCyrillic = false
            while (i < n && isTokenAlnumCodepoint(codepoints[i])) {
                if (isCyrillicCodepoint(codepoints[i])) {
                    hasCyrillic = true
                }
                i++
            }

            if (hasCyrillic) {
                for (k in start until i) {
                    when (codepoints[k]) {
                        0x0033 -> codepoints[k] = 0x0437 // '3' -> 'з'
                        0x0036 -> codepoints[k] = 0x0431 // '6' -> 'б'
                    }
                }
            }
        }
    }

    private fun normalizeCodepoint(cp: Int, foldCyrillicHomoglyphs: Boolean = false): Int {
        if (foldCyrillicHomoglyphs) {
            // Uppercase-only Latin homoglyphs (checked BEFORE ASCII A-Z -> a-z)
            when (cp) {
                0x0042 -> return 0x0432 // B -> в
                0x0048 -> return 0x043D // H -> н
                0x004D -> return 0x043C // M -> м
                0x0054 -> return 0x0442 // T -> т
                // Both-case unambiguous Latin homoglyphs
                0x0041, 0x0061 -> return 0x0430 // A/a -> а
                0x0043, 0x0063 -> return 0x0441 // C/c -> с
                0x0045, 0x0065 -> return 0x0435 // E/e -> е
                0x004B, 0x006B -> return 0x043A // K/k -> к
                0x004F, 0x006F -> return 0x043E // O/o -> о
                0x0050, 0x0070 -> return 0x0440 // P/p -> р
                0x0058, 0x0078 -> return 0x0445 // X/x -> х
                0x0059, 0x0079 -> return 0x0443 // Y/y -> у
            }
        }

        if (cp in 0x0041..0x005A) return cp + 0x20
        if (cp in 0x0410..0x042F) return cp + 0x20
        if (cp == 0x0401 || cp == 0x0451) return 0x0435
        return Character.toLowerCase(cp)
    }
}
