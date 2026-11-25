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
package com.buzbuz.smartautoclicker.core.processing.data.processor

import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.detection.TextReadResult

/**
 * Per-frame OCR cache for [ImageDetector.readText].
 * Cleared whenever a new screen frame is loaded so multiple events reading the same (alphabet, rect)
 * in one frame execute OCR only once.
 */
internal class FrameTextReadCache(
    private val imageDetector: ImageDetector,
) {

    private data class Key(
        val recognitionModelId: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private val cache: MutableMap<Key, TextReadResult> = mutableMapOf()

    fun clear() {
        cache.clear()
    }

    fun readText(recognitionModelId: String, scaledArea: Rect): TextReadResult {
        val key = Key(
            recognitionModelId = recognitionModelId,
            left = scaledArea.left,
            top = scaledArea.top,
            right = scaledArea.right,
            bottom = scaledArea.bottom,
        )
        return cache.getOrPut(key) {
            imageDetector.readText(recognitionModelId, scaledArea)
        }
    }
}
