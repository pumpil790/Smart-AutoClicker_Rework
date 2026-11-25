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
#include "text_matching_core.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>

using namespace smartautoclicker;

namespace {

    bool isWhitespaceCodepoint(uint32_t cp) {
        return cp == ' ' || cp == '\t' || cp == '\n' || cp == '\r' || cp == 0x00A0;
    }

    bool isTrailingPunctuation(uint32_t cp) {
        return cp == '.' || cp == ',' || cp == '!' || cp == '?';
    }

    bool isCyrillicCodepoint(uint32_t cp) {
        return cp >= 0x0400 && cp <= 0x04FF;
    }

    bool isTokenAlnumCodepoint(uint32_t cp) {
        if (cp >= '0' && cp <= '9') return true;
        if (cp >= 'a' && cp <= 'z') return true;
        if (cp >= 'A' && cp <= 'Z') return true;
        return isCyrillicCodepoint(cp);
    }

    bool containsSubsequence(
            const std::vector<uint32_t>& haystack,
            const std::vector<uint32_t>& needle) {
        if (needle.empty() || needle.size() > haystack.size()) return false;
        return std::search(haystack.begin(), haystack.end(), needle.begin(), needle.end()) != haystack.end();
    }

    /**
     * Folds '3' -> 'з' and '6' -> 'б' only inside alphanumeric tokens that also contain
     * at least one Cyrillic letter, leaving purely numeric tokens (e.g. "18", "577", "360") untouched.
     */
    void foldCyrillicDigitHomoglyphs(std::vector<uint32_t>& codepoints) {
        const size_t n = codepoints.size();
        size_t i = 0;
        while (i < n) {
            if (!isTokenAlnumCodepoint(codepoints[i])) {
                ++i;
                continue;
            }

            size_t start = i;
            bool hasCyrillic = false;
            while (i < n && isTokenAlnumCodepoint(codepoints[i])) {
                if (isCyrillicCodepoint(codepoints[i])) {
                    hasCyrillic = true;
                }
                ++i;
            }

            if (hasCyrillic) {
                for (size_t k = start; k < i; ++k) {
                    if (codepoints[k] == 0x0033) { // '3' -> 'з'
                        codepoints[k] = 0x0437;
                    } else if (codepoints[k] == 0x0036) { // '6' -> 'б'
                        codepoints[k] = 0x0431;
                    }
                }
            }
        }
    }

} // namespace

uint32_t TextMatchingCore::normalizeCodepoint(uint32_t cp, bool foldCyrillicHomoglyphs) {
    if (foldCyrillicHomoglyphs) {
        // Uppercase-only Latin homoglyphs (must be checked BEFORE ASCII A-Z -> a-z)
        switch (cp) {
            case 0x0042: return 0x0432; // B -> в
            case 0x0048: return 0x043D; // H -> н
            case 0x004D: return 0x043C; // M -> м
            case 0x0054: return 0x0442; // T -> т
            // Both-case unambiguous Latin homoglyphs
            case 0x0041:
            case 0x0061: return 0x0430; // A/a -> а
            case 0x0043:
            case 0x0063: return 0x0441; // C/c -> с
            case 0x0045:
            case 0x0065: return 0x0435; // E/e -> е
            case 0x004B:
            case 0x006B: return 0x043A; // K/k -> к
            case 0x004F:
            case 0x006F: return 0x043E; // O/o -> о
            case 0x0050:
            case 0x0070: return 0x0440; // P/p -> р
            case 0x0058:
            case 0x0078: return 0x0445; // X/x -> х
            case 0x0059:
            case 0x0079: return 0x0443; // Y/y -> у
            default: break;
        }
    }

    // ASCII A-Z -> a-z
    if (cp >= 0x0041 && cp <= 0x005A) return cp + 0x20;
    // Cyrillic А-Я -> а-я
    if (cp >= 0x0410 && cp <= 0x042F) return cp + 0x20;
    // Cyrillic Ё / ё -> е
    if (cp == 0x0401 || cp == 0x0451) return 0x0435;
    return cp;
}

std::vector<uint32_t> TextMatchingCore::decodeAndNormalizeUtf8(
        const std::string& text,
        bool stripTrailingPunctuation,
        bool foldCyrillicHomoglyphs) {
    std::vector<uint32_t> out;
    out.reserve(text.size());

    const auto* data = reinterpret_cast<const unsigned char*>(text.data());
    const size_t len = text.size();
    size_t i = 0;
    bool prevWasSpace = true;

    while (i < len) {
        uint32_t cp = 0;
        unsigned char c = data[i];
        if (c < 0x80) {
            cp = c;
            i += 1;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < len && (data[i + 1] & 0xC0) == 0x80) {
            cp = ((c & 0x1F) << 6) | (data[i + 1] & 0x3F);
            i += 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < len &&
                   (data[i + 1] & 0xC0) == 0x80 && (data[i + 2] & 0xC0) == 0x80) {
            cp = ((c & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F);
            i += 3;
        } else if ((c & 0xF8) == 0xF0 && i + 3 < len &&
                   (data[i + 1] & 0xC0) == 0x80 && (data[i + 2] & 0xC0) == 0x80 &&
                   (data[i + 3] & 0xC0) == 0x80) {
            cp = ((c & 0x07) << 18) | ((data[i + 1] & 0x3F) << 12) |
                 ((data[i + 2] & 0x3F) << 6) | (data[i + 3] & 0x3F);
            i += 4;
        } else {
            // Skip invalid UTF-8 byte safely
            i += 1;
            continue;
        }

        if (isWhitespaceCodepoint(cp)) {
            if (!prevWasSpace) {
                out.push_back(' ');
                prevWasSpace = true;
            }
        } else {
            out.push_back(normalizeCodepoint(cp, foldCyrillicHomoglyphs));
            prevWasSpace = false;
        }
    }

    while (!out.empty() && out.back() == ' ') {
        out.pop_back();
    }

    if (stripTrailingPunctuation) {
        while (!out.empty() && (isTrailingPunctuation(out.back()) || out.back() == ' ')) {
            out.pop_back();
        }
    }

    if (foldCyrillicHomoglyphs) {
        foldCyrillicDigitHomoglyphs(out);
    }

    return out;
}

float TextMatchingCore::getAdaptiveMinSimilarity(size_t targetCodepoints) {
    return (targetCodepoints <= SHORT_TARGET_MAX_CODEPOINTS)
           ? SHORT_TARGET_MIN_SIMILARITY
           : DEFAULT_MIN_SIMILARITY;
}

float TextMatchingCore::similarity(
        const std::vector<uint32_t>& recognized,
        const std::vector<uint32_t>& target,
        float minSimilarity,
        std::vector<int>& comparisonPrevPrevRow,
        std::vector<int>& comparisonPrevRow,
        std::vector<int>& comparisonCurrRow) {
    if (recognized.empty() || target.empty()) return 0.f;

    const int n = static_cast<int>(recognized.size());
    const int m = static_cast<int>(target.size());
    const int maxLen = std::max(n, m);

    // Early impossible length check (epsilon avoids float truncation like 0.99999994 -> 0)
    int maxAllowedDistance = static_cast<int>(std::floor((1.f - minSimilarity) * static_cast<float>(maxLen) + 1e-5f));
    if (std::abs(n - m) > maxAllowedDistance) return 0.f;

    comparisonPrevPrevRow.resize(static_cast<size_t>(m + 1));
    comparisonPrevRow.resize(static_cast<size_t>(m + 1));
    comparisonCurrRow.resize(static_cast<size_t>(m + 1));

    for (int j = 0; j <= m; ++j) comparisonPrevRow[j] = j;

    for (int i = 1; i <= n; ++i) {
        comparisonCurrRow[0] = i;
        int rowMin = comparisonCurrRow[0];

        const uint32_t ca = recognized[i - 1];
        for (int j = 1; j <= m; ++j) {
            const uint32_t cb = target[j - 1];

            int cost = (ca == cb) ? 0 : 1;

            int deletion = comparisonPrevRow[j] + 1;
            int insertion = comparisonCurrRow[j - 1] + 1;
            int substitution = comparisonPrevRow[j - 1] + cost;
            int value = std::min({ deletion, insertion, substitution });

            // Damerau transposition
            if (i > 1 && j > 1 && ca == target[j - 2] && recognized[i - 2] == cb) {
                value = std::min(value, comparisonPrevPrevRow[j - 2] + 1);
            }

            comparisonCurrRow[j] = value;
            rowMin = std::min(rowMin, value);
        }

        // Early exit
        if (rowMin > maxAllowedDistance) return 0.f;

        std::swap(comparisonPrevPrevRow, comparisonPrevRow);
        std::swap(comparisonPrevRow, comparisonCurrRow);
    }

    int distance = comparisonPrevRow[m];
    if (distance > maxAllowedDistance) return 0.f;

    float score = 1.f - static_cast<float>(distance) / static_cast<float>(maxLen);
    return (score + 1e-5f >= minSimilarity) ? std::max(0.f, score) : 0.f;
}

float TextMatchingCore::similarity(
        const std::vector<uint32_t>& recognized,
        const std::vector<uint32_t>& target,
        float minSimilarity) {
    std::vector<int> prevPrevRow;
    std::vector<int> prevRow;
    std::vector<int> currRow;
    return similarity(recognized, target, minSimilarity, prevPrevRow, prevRow, currRow);
}

float TextMatchingCore::bestSubstringSimilarity(
        const std::vector<uint32_t>& recognized,
        const std::vector<uint32_t>& target,
        float minSimilarity,
        std::vector<int>& prevPrevRow,
        std::vector<int>& prevRow,
        std::vector<int>& currRow) {
    if (recognized.empty() || target.empty()) return 0.f;

    // Fast exact normalized subsequence match
    if (containsSubsequence(recognized, target)) return 1.f;

    const int targetLen = static_cast<int>(target.size());
    const int recognizedLen = static_cast<int>(recognized.size());

    // Fast path
    if (recognizedLen <= targetLen + 2) {
        return similarity(recognized, target, minSimilarity, prevPrevRow, prevRow, currRow);
    }

    // Allow small OCR insertions/deletions
    float bestScore = 0.f;
    const int minWindow = std::max(1, targetLen - 2);
    const int maxWindow = std::min(recognizedLen, targetLen + 4);

    std::vector<uint32_t> window;
    window.reserve(static_cast<size_t>(maxWindow));
    for (int windowSize = minWindow; windowSize <= maxWindow; ++windowSize) {
        for (int start = 0; start <= recognizedLen - windowSize; ++start) {
            window.assign(recognized.begin() + start, recognized.begin() + start + windowSize);

            float score = similarity(window, target, minSimilarity, prevPrevRow, prevRow, currRow);
            if (score > bestScore) {
                bestScore = score;

                // Early success exit
                if (bestScore >= 0.95f) return bestScore;
            }
        }
    }

    return bestScore;
}

float TextMatchingCore::bestSubstringSimilarity(
        const std::vector<uint32_t>& recognized,
        const std::vector<uint32_t>& target,
        float minSimilarity) {
    std::vector<int> prevPrevRow;
    std::vector<int> prevRow;
    std::vector<int> currRow;
    return bestSubstringSimilarity(recognized, target, minSimilarity, prevPrevRow, prevRow, currRow);
}

bool TextMatchingCore::isNumber(const std::string& text) {
    if (text.empty()) return false;

    bool hasDigit = false;
    for (char c : text) {
        if (std::isdigit(static_cast<unsigned char>(c))) {
            hasDigit = true;
            continue;
        }

        if (c == '.' || c == ',' || c == ' ' || c == '-' || c == '+') continue;

        return false;
    }

    return hasDigit;
}
