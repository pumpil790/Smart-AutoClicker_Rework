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
#ifndef KLICK_R_TEXT_MATCHING_CORE_HPP
#define KLICK_R_TEXT_MATCHING_CORE_HPP

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace smartautoclicker {

    /**
     * Pure C++ UTF-8 normalization, Cyrillic homoglyph folding, and codepoint
     * Damerau-Levenshtein similarity helpers shared by TextMatcher and native unit tests.
     */
    class TextMatchingCore {
    public:
        static constexpr float DEFAULT_MIN_SIMILARITY = 0.80f;
        static constexpr float SHORT_TARGET_MIN_SIMILARITY = 0.65f;
        static constexpr size_t SHORT_TARGET_MAX_CODEPOINTS = 6;

        /**
         * Normalizes a single Unicode codepoint:
         * - ASCII A-Z -> a-z
         * - Cyrillic А-Я -> а-я, Ё/ё -> е
         * - When foldCyrillicHomoglyphs is true, folds unambiguous Latin lookalikes
         *   (A/a, C/c, E/e, K/k, O/o, P/p, X/x, Y/y, and uppercase B, H, M, T)
         *   into lowercase Cyrillic codepoints.
         */
        static uint32_t normalizeCodepoint(uint32_t cp, bool foldCyrillicHomoglyphs = false);

        /**
         * Decodes UTF-8 into normalized Unicode codepoints:
         * - Collapses whitespace and trims ends
         * - Optionally strips trailing punctuation (. , ! ?)
         * - When foldCyrillicHomoglyphs is true, folds Latin homoglyphs and also folds
         *   digits '3' -> 'з' and '6' -> 'б' strictly inside alphanumeric tokens that
         *   contain at least one Cyrillic letter (preserving purely numeric tokens).
         */
        static std::vector<uint32_t> decodeAndNormalizeUtf8(
                const std::string& text,
                bool stripTrailingPunctuation = true,
                bool foldCyrillicHomoglyphs = false);

        /** Returns adaptive minimum similarity threshold based on target length in codepoints. */
        static float getAdaptiveMinSimilarity(size_t targetCodepoints);

        /** Computes codepoint Damerau-Levenshtein similarity between two normalized sequences. */
        static float similarity(
                const std::vector<uint32_t>& recognized,
                const std::vector<uint32_t>& target,
                float minSimilarity,
                std::vector<int>& prevPrevRow,
                std::vector<int>& prevRow,
                std::vector<int>& currRow);

        /** Convenience overload allocating local DP buffers. */
        static float similarity(
                const std::vector<uint32_t>& recognized,
                const std::vector<uint32_t>& target,
                float minSimilarity);

        /** Finds the best substring similarity of target inside recognized. */
        static float bestSubstringSimilarity(
                const std::vector<uint32_t>& recognized,
                const std::vector<uint32_t>& target,
                float minSimilarity,
                std::vector<int>& prevPrevRow,
                std::vector<int>& prevRow,
                std::vector<int>& currRow);

        /** Convenience overload allocating local DP buffers. */
        static float bestSubstringSimilarity(
                const std::vector<uint32_t>& recognized,
                const std::vector<uint32_t>& target,
                float minSimilarity);

        /** Checks whether raw OCR text represents a numeric value. */
        static bool isNumber(const std::string& text);
    };

} // namespace smartautoclicker

#endif //KLICK_R_TEXT_MATCHING_CORE_HPP
