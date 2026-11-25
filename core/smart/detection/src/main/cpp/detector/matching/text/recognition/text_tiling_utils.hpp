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
#ifndef KLICK_R_TEXT_TILING_UTILS_HPP
#define KLICK_R_TEXT_TILING_UTILS_HPP

#include <string>
#include <vector>

namespace smartautoclicker {

    /**
     * Pure C++ helpers for tiled OCR recognition of long LTR text lines (> 320 px)
     * without horizontal squishing.
     */
    class TextTilingUtils {
    public:
        static constexpr int TARGET_HEIGHT = 48;
        static constexpr int MAX_WIDTH = 320;
        static constexpr int TILE_STEP_X = 240;
        static constexpr int MAX_TILE_WINDOWS = 8;

        /**
         * Computes horizontal window start X offsets (each of width MAX_WIDTH) covering
         * [0, scaledWidth) with overlap and a right-aligned final window, capped at MAX_TILE_WINDOWS.
         * Returns {0} if scaledWidth <= MAX_WIDTH.
         */
        static std::vector<int> computeWindowStarts(int scaledWidth);

        /**
         * Merges decoded strings from consecutive overlapping horizontal tiles into a single
         * deduplicated line of text, handling exact overlaps, case/homoglyph differences,
         * single clipped boundary glyphs, and non-overlapping word gaps.
         */
        static std::string mergeWindowTexts(const std::vector<std::string>& windowTexts);
    };

} // namespace smartautoclicker

#endif //KLICK_R_TEXT_TILING_UTILS_HPP
