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
#include "text_tiling_utils.hpp"
#include "../text_matching_core.hpp"

#include <algorithm>
#include <cmath>
#include <cstdint>

using namespace smartautoclicker;

namespace {

    constexpr size_t MAX_OVERLAP_CODEPOINTS = 16;
    constexpr int MIN_TRAILING_STEP_PX = 16;

    bool isSpaceCp(uint32_t cp) {
        return cp == ' ' || cp == '\t' || cp == '\n' || cp == '\r' || cp == 0x00A0;
    }

    bool isAlnumCp(uint32_t cp) {
        if (cp >= '0' && cp <= '9') return true;
        if (cp >= 'a' && cp <= 'z') return true;
        if (cp >= 'A' && cp <= 'Z') return true;
        if (cp >= 0x0400 && cp <= 0x04FF) return true;
        if (cp >= 0x00C0 && cp <= 0x024F) return true;
        return false;
    }

    std::vector<uint32_t> decodeUtf8Raw(const std::string& text) {
        std::vector<uint32_t> out;
        out.reserve(text.size());

        const auto* data = reinterpret_cast<const unsigned char*>(text.data());
        const size_t len = text.size();
        size_t i = 0;

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
                i += 1;
                continue;
            }
            out.push_back(cp);
        }

        return out;
    }

    std::string encodeUtf8(const std::vector<uint32_t>& cps) {
        std::string out;
        out.reserve(cps.size() * 2);
        for (uint32_t cp : cps) {
            if (cp < 0x80) {
                out.push_back(static_cast<char>(cp));
            } else if (cp < 0x800) {
                out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
            } else if (cp < 0x10000) {
                out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
            } else if (cp <= 0x10FFFF) {
                out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
            }
        }
        return out;
    }

    uint32_t foldForOverlap(uint32_t cp) {
        if (isSpaceCp(cp)) return ' ';
        return TextMatchingCore::normalizeCodepoint(cp, true);
    }

    std::vector<uint32_t> buildFoldedForOverlap(const std::vector<uint32_t>& cps) {
        std::vector<uint32_t> folded(cps.size());
        for (size_t i = 0; i < cps.size(); ++i) {
            folded[i] = foldForOverlap(cps[i]);
        }

        // Fold '3'->'з' and '6'->'б' strictly inside alphanumeric tokens containing Cyrillic
        const size_t n = folded.size();
        size_t i = 0;
        while (i < n) {
            if (!isAlnumCp(folded[i])) {
                ++i;
                continue;
            }
            size_t start = i;
            bool hasCyrillic = false;
            while (i < n && isAlnumCp(folded[i])) {
                if (folded[i] >= 0x0400 && folded[i] <= 0x04FF) {
                    hasCyrillic = true;
                }
                ++i;
            }
            if (hasCyrillic) {
                for (size_t k = start; k < i; ++k) {
                    if (folded[k] == 0x0033) folded[k] = 0x0437;
                    else if (folded[k] == 0x0036) folded[k] = 0x0431;
                }
            }
        }

        return folded;
    }

    std::vector<uint32_t> trimSpaces(const std::vector<uint32_t>& input) {
        size_t start = 0;
        while (start < input.size() && isSpaceCp(input[start])) ++start;
        size_t end = input.size();
        while (end > start && isSpaceCp(input[end - 1])) --end;
        return {input.begin() + static_cast<std::ptrdiff_t>(start),
                input.begin() + static_cast<std::ptrdiff_t>(end)};
    }

    void mergePair(
            std::vector<uint32_t>& acc,
            const std::vector<uint32_t>& nextRaw,
            bool prevHadTrailingSpace) {
        if (nextRaw.empty()) return;

        const bool nextHasLeadingSpace = isSpaceCp(nextRaw.front());
        const bool hadBoundarySpace = prevHadTrailingSpace || nextHasLeadingSpace;

        const std::vector<uint32_t> next = trimSpaces(nextRaw);
        if (next.empty()) return;

        if (acc.empty()) {
            acc = next;
            return;
        }

        const std::vector<uint32_t> accFold = buildFoldedForOverlap(acc);
        const std::vector<uint32_t> nextFold = buildFoldedForOverlap(next);

        const size_t accLen = acc.size();
        const size_t nextLen = next.size();
        const size_t maxL = std::min({accLen, nextLen, MAX_OVERLAP_CODEPOINTS});

        // 1. Exact (case/homoglyph-folded) suffix-prefix overlap with L >= 2
        for (size_t L = maxL; L >= 2; --L) {
            bool exact = true;
            for (size_t k = 0; k < L; ++k) {
                if (accFold[accLen - L + k] != nextFold[k]) {
                    exact = false;
                    break;
                }
            }
            if (exact) {
                acc.insert(acc.end(), next.begin() + static_cast<std::ptrdiff_t>(L), next.end());
                return;
            }
        }

        // 2. Clipped boundary glyph match: allow dropping up to 2 garbled chars at the tile seam
        //    if an exact folded overlap of length >= 3 is found right behind them.
        size_t bestL = 0;
        size_t bestTrimAcc = 0;
        size_t bestTrimNext = 0;
        for (size_t trimAcc = 0; trimAcc <= 2 && trimAcc < accLen; ++trimAcc) {
            for (size_t trimNext = 0; trimNext <= 2 && trimNext < nextLen; ++trimNext) {
                if (trimAcc == 0 && trimNext == 0) continue;
                const size_t effAccLen = accLen - trimAcc;
                const size_t effNextLen = nextLen - trimNext;
                const size_t effMaxL = std::min({effAccLen, effNextLen, MAX_OVERLAP_CODEPOINTS});
                for (size_t L = effMaxL; L >= 3; --L) {
                    bool match = true;
                    for (size_t k = 0; k < L; ++k) {
                        if (accFold[effAccLen - L + k] != nextFold[trimNext + k]) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        const bool better = (L > bestL) ||
                                            (L == bestL && (trimAcc + trimNext) < (bestTrimAcc + bestTrimNext));
                        if (better) {
                            bestL = L;
                            bestTrimAcc = trimAcc;
                            bestTrimNext = trimNext;
                        }
                        break;
                    }
                }
            }
        }
        if (bestL >= 3) {
            acc.resize(accLen - bestTrimAcc);
            acc.insert(acc.end(), next.begin() + static_cast<std::ptrdiff_t>(bestTrimNext + bestL), next.end());
            return;
        }

        // 3. Near-exact suffix-prefix overlap (L >= 4, at most 1 substitution inside overlap)
        for (size_t L = maxL; L >= 4; --L) {
            int mismatches = 0;
            for (size_t k = 0; k < L; ++k) {
                if (accFold[accLen - L + k] != nextFold[k]) {
                    ++mismatches;
                    if (mismatches > 1) break;
                }
            }
            if (mismatches <= 1) {
                acc.insert(acc.end(), next.begin() + static_cast<std::ptrdiff_t>(L), next.end());
                return;
            }
        }

        // 4. Single alphanumeric character overlap when there was no whitespace separating tiles
        if (!hadBoundarySpace && maxL >= 1 &&
            isAlnumCp(acc.back()) &&
            accFold.back() == nextFold.front()) {
            acc.insert(acc.end(), next.begin() + 1, next.end());
            return;
        }

        // 5. No overlap found: join cleanly with a single space unless a hyphen/space is already present
        if (acc.back() != ' ' && acc.back() != '-' && next.front() != ' ') {
            acc.push_back(' ');
        }
        acc.insert(acc.end(), next.begin(), next.end());
    }

} // namespace

std::vector<int> TextTilingUtils::computeWindowStarts(int scaledWidth) {
    if (scaledWidth <= MAX_WIDTH) {
        return {0};
    }

    const int maxStart = scaledWidth - MAX_WIDTH;
    std::vector<int> starts;

    if (maxStart <= (MAX_TILE_WINDOWS - 1) * TILE_STEP_X) {
        for (int x = 0; x < maxStart; x += TILE_STEP_X) {
            if (x > 0 && (maxStart - x) < MIN_TRAILING_STEP_PX) {
                break;
            }
            starts.push_back(x);
        }
        starts.push_back(maxStart);
    } else {
        starts.reserve(MAX_TILE_WINDOWS);
        for (int i = 0; i < MAX_TILE_WINDOWS; ++i) {
            int x = static_cast<int>(std::round(
                    static_cast<double>(i) * static_cast<double>(maxStart) /
                    static_cast<double>(MAX_TILE_WINDOWS - 1)));
            if (starts.empty() || x > starts.back()) {
                starts.push_back(x);
            }
        }
    }

    return starts;
}

std::string TextTilingUtils::mergeWindowTexts(const std::vector<std::string>& windowTexts) {
    std::vector<uint32_t> acc;
    bool prevHadTrailingSpace = false;

    for (const auto& rawText : windowTexts) {
        std::vector<uint32_t> rawCps = decodeUtf8Raw(rawText);
        if (rawCps.empty()) continue;

        bool currentHasTrailingSpace = isSpaceCp(rawCps.back());
        std::vector<uint32_t> trimmed = trimSpaces(rawCps);
        if (trimmed.empty()) {
            if (!acc.empty()) prevHadTrailingSpace = true;
            continue;
        }

        mergePair(acc, rawCps, prevHadTrailingSpace);
        prevHadTrailingSpace = currentHasTrailingSpace;
    }

    return encodeUtf8(acc);
}
