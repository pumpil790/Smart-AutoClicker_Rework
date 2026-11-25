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
     */
    fun normalize(text: String, stripEdgePunctuation: Boolean = true): String {
        if (text.isEmpty()) return ""

        val sb = StringBuilder(text.length)
        var prevWasSpace = true
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            i += charCount

            if (Character.isWhitespace(cp) || cp == 0x00A0) {
                if (!prevWasSpace) {
                    sb.append(' ')
                    prevWasSpace = true
                }
            } else {
                val normalizedCp = normalizeCodepoint(cp)
                sb.appendCodePoint(normalizedCp)
                prevWasSpace = false
            }
        }

        // Trim trailing space
        var end = sb.length
        while (end > 0 && sb[end - 1] == ' ') end--
        var start = 0
        while (start < end && sb[start] == ' ') start++

        if (stripEdgePunctuation) {
            while (start < end && (sb[start] in EDGE_PUNCTUATION || sb[start] == ' ')) start++
            while (end > start && (sb[end - 1] in EDGE_PUNCTUATION || sb[end - 1] == ' ')) end--
        }

        return if (start >= end) "" else sb.substring(start, end)
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
    ): Pair<Boolean, Double> {
        val normRecognized = normalize(recognizedText)
        val normTarget = normalize(targetText)
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
    ): Pair<Boolean, Double> {
        val normRecognized = normalize(recognizedText)
        if (normRecognized.isEmpty() || entries.isEmpty()) return false to 0.0

        if (matchType == TextMatchType.EXACT_NORMALIZED) {
            for (entry in entries) {
                if (normalize(entry) == normRecognized) return true to 100.0
            }
            return false to 0.0
        }

        val threshold = thresholdPercent.coerceIn(1, 100)
        val minSim = threshold / 100.0f
        val recCodepoints = normRecognized.toCodePoints()
        var bestScorePercent = 0.0

        for (entry in entries) {
            val normEntry = normalize(entry)
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

    private fun normalizeCodepoint(cp: Int): Int {
        if (cp in 0x0041..0x005A) return cp + 0x20
        if (cp in 0x0410..0x042F) return cp + 0x20
        if (cp == 0x0401 || cp == 0x0451) return 0x0435
        return Character.toLowerCase(cp)
    }
}
