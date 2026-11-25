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

#include <cmath>
#include <iostream>
#include <string>
#include <vector>

#include "../../main/cpp/detector/matching/text/text_matching_core.hpp"
#include "../../main/cpp/detector/matching/text/recognition/text_tiling_utils.hpp"

using namespace smartautoclicker;

namespace {

    int failures = 0;

    void expectTrue(bool cond, const std::string& msg) {
        if (!cond) {
            std::cerr << "[FAIL] " << msg << "\n";
            ++failures;
        }
    }

    void expectEqStr(const std::string& actual, const std::string& expected, const std::string& msg) {
        if (actual != expected) {
            std::cerr << "[FAIL] " << msg << ": expected=\"" << expected << "\" actual=\"" << actual << "\"\n";
            ++failures;
        }
    }

    std::string encodeUtf8(const std::vector<uint32_t>& cps) {
        std::string out;
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
            } else {
                out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
            }
        }
        return out;
    }

    float matchScore(const std::string& recognized, const std::string& target, bool cyrillicMode) {
        auto normTarget = TextMatchingCore::decodeAndNormalizeUtf8(target, true, cyrillicMode);
        auto normRecognized = TextMatchingCore::decodeAndNormalizeUtf8(recognized, true, cyrillicMode);
        float minSim = TextMatchingCore::getAdaptiveMinSimilarity(normTarget.size());
        return TextMatchingCore::bestSubstringSimilarity(normRecognized, normTarget, minSim);
    }

    void testCyrillicHomoglyphs() {
        // Real SVTR failures from Arcanum frames
        expectTrue(
                matchScore("ХочynyчшeyshаTьДоnиhy.", "Долину", true) >= 0.65f,
                "CYRILLIC: 'Долину' inside 'ХочynyчшeyshаTьДоnиhy.' must pass");
        expectTrue(
                matchScore("Долинy", "Долину", true) >= 0.99f,
                "CYRILLIC: 'Долинy' vs 'Долину' must be 100%");
        expectTrue(
                matchScore("ДолиHy", "Долину", true) >= 0.99f,
                "CYRILLIC: 'ДолиHy' vs 'Долину' must be 100%");
        expectTrue(
                matchScore("Доnиhy", "Долину", true) >= 0.65f,
                "CYRILLIC: 'Доnиhy' vs 'Долину' must pass adaptive threshold");
        expectTrue(
                matchScore("Хочу лучше y3наTь Долинy.", "Хочу лучше узнать Долину.", true) >= 0.99f,
                "CYRILLIC: full phrase with homoglyphs must match 100%");
        expectTrue(
                matchScore("бe3опаcHOсTи.", "безопасности", true) >= 0.99f,
                "CYRILLIC: 'бe3опаcHOсTи.' vs 'безопасности' must be 100%");
        expectTrue(
                matchScore("бe3OпаcHOCTи.", "безопасности", true) >= 0.99f,
                "CYRILLIC: 'бe3OпаcHOCTи.' vs 'безопасности' must be 100%");
        expectTrue(
                matchScore("6ыcTро", "быстро", true) >= 0.99f,
                "CYRILLIC: '6ыcTро' vs 'быстро' must be 100%");

        // Uppercase-only B/H/M/T fold to Cyrillic, lowercase b/h/m/t stay Latin
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("BHMT", true, true)),
                "внмт",
                "Uppercase BHMT must fold to внмт");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("bhmt", true, true)),
                "bhmt",
                "Lowercase bhmt must NOT fold to Cyrillic");
    }

    void testLatinModeUnchanged() {
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("ABCEHKMOPTXY", true, false)),
                "abcehkmoptxy",
                "LATIN mode must not fold Latin letters to Cyrillic");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("бe3опаcHOсTи.", true, false)),
                "бe3опаchoсtи",
                "LATIN mode must preserve Latin/digit chars");
        expectTrue(
                matchScore("бe3опаcHOсTи.", "безопасности", false) == 0.0f,
                "LATIN mode must not apply Cyrillic homoglyph folding");
    }

    void testNumericSafety() {
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("18", true, true)),
                "18",
                "Pure number '18' must remain unchanged");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("577", true, true)),
                "577",
                "Pure number '577' must remain unchanged");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("360", true, true)),
                "360",
                "Pure number '360' must remain unchanged even with 3 and 6");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("Наряд 3", true, true)),
                "наряд 3",
                "Standalone digit token '3' inside Cyrillic phrase must remain '3'");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("Глава 6", true, true)),
                "глава 6",
                "Standalone digit token '6' inside Cyrillic phrase must remain '6'");
        expectEqStr(
                encodeUtf8(TextMatchingCore::decodeAndNormalizeUtf8("3-й уровень", true, true)),
                "3-й уровень",
                "Hyphen-separated ordinal '3-й' must keep '3'");

        expectTrue(TextMatchingCore::isNumber("18"), "isNumber('18') must be true");
        expectTrue(TextMatchingCore::isNumber("577"), "isNumber('577') must be true");
        expectTrue(TextMatchingCore::isNumber("1,234.56"), "isNumber('1,234.56') must be true");
        expectTrue(!TextMatchingCore::isNumber("бe3опаcHOсTи"), "isNumber('бe3опаcHOсTи') must be false");
    }

    void testTilingWindowStarts() {
        auto sShort = TextTilingUtils::computeWindowStarts(200);
        expectTrue(sShort == std::vector<int>({0}), "scaledWidth=200 -> {0}");

        auto sExact = TextTilingUtils::computeWindowStarts(320);
        expectTrue(sExact == std::vector<int>({0}), "scaledWidth=320 -> {0}");

        auto s550 = TextTilingUtils::computeWindowStarts(550);
        expectTrue(s550 == std::vector<int>({0, 230}), "scaledWidth=550 -> {0, 230}");

        auto s561 = TextTilingUtils::computeWindowStarts(561);
        expectTrue(s561 == std::vector<int>({0, 241}), "scaledWidth=561 avoids near-duplicate trailing tile");

        auto s640 = TextTilingUtils::computeWindowStarts(640);
        expectTrue(s640 == std::vector<int>({0, 240, 320}), "scaledWidth=640 -> {0, 240, 320}");

        auto sHuge = TextTilingUtils::computeWindowStarts(5000);
        expectTrue(
                sHuge.size() == static_cast<size_t>(TextTilingUtils::MAX_TILE_WINDOWS),
                "scaledWidth=5000 must be capped at MAX_TILE_WINDOWS=8");
        expectTrue(sHuge.front() == 0 && sHuge.back() == 5000 - 320, "Capped tiles cover [0, 5000)");
    }

    void testMergeWindowTexts() {
        // Exact overlap
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({"Хочу лучше узнать", "узнать Долину."}),
                "Хочу лучше узнать Долину.",
                "Exact Cyrillic overlap merge");

        // Case & homoglyph difference in overlap
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({"Хочу лучше узнать", "y3HATь Долину."}),
                "Хочу лучше узнать Долину.",
                "Case/homoglyph-insensitive overlap merge");

        // Clipped boundary glyphs on both sides of the seam
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({"Хочу лучше узнатI", "cузнать Долину."}),
                "Хочу лучше узнать Долину.",
                "Clipped boundary glyph recovery");

        // 3-window chain (synthetic 640px+ line)
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({
                        "Это для нашей с",
                        "нашей с вами безо",
                        "с вами безопасности."
                }),
                "Это для нашей с вами безопасности.",
                "3-window synthetic 640px merge without loss or duplication");

        // Non-overlapping tiles preserve word boundary space
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({"Привет", "мир"}),
                "Привет мир",
                "Non-overlapping tiles join with single space");

        // Boundary space prevents false 1-char merge ("Хочу " + "узнать")
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({"Хочу ", "узнать"}),
                "Хочу узнать",
                "Trailing space prevents false single-char overlap merge");

        // Single alphanumeric char overlap without boundary space
        expectEqStr(
                TextTilingUtils::mergeWindowTexts({"abc", "cdef"}),
                "abcdef",
                "Single alphanumeric char overlap merges cleanly");
    }

} // namespace

int main() {
    testCyrillicHomoglyphs();
    testLatinModeUnchanged();
    testNumericSafety();
    testTilingWindowStarts();
    testMergeWindowTexts();

    if (failures > 0) {
        std::cerr << failures << " test(s) failed.\n";
        return 1;
    }

    std::cout << "All C++ OCR v3 unit tests passed!\n";
    return 0;
}
