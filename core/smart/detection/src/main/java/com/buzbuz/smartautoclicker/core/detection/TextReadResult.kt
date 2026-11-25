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
package com.buzbuz.smartautoclicker.core.detection

import android.graphics.Rect

/**
 * Result of reading all text within a screen region in reading order.
 *
 * @param text recognized and joined text (trimmed, collapsed whitespace).
 * @param confidence average recognition confidence in [0.0, 1.0].
 * @param boundingBox union bounding box in screen (scaled) coordinates.
 */
data class TextReadResult(
    val text: String = "",
    val confidence: Double = 0.0,
    val boundingBox: Rect = Rect(),
)
