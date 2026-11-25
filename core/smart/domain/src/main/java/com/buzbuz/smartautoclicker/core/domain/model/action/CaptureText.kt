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
package com.buzbuz.smartautoclicker.core.domain.model.action

import android.graphics.Rect
import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRAlphabet
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

/**
 * Action that reads text from a screen region via OCR and stores it in a runtime string variable
 * or appends it to a `.txt` wordlist file.
 */
data class CaptureText(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String? = null,
    override var priority: Int = 0,
    val detectionArea: Rect,
    val alphabet: OCRAlphabet,
    val targetType: TargetType,
    val targetName: String,
    val deduplicate: Boolean = true,
) : Action() {

    enum class TargetType {
        STRING_VAR,
        FILE_LIST,
    }

    override fun isComplete(): Boolean =
        super.isComplete() && targetName.isNotBlank() && !detectionArea.isEmpty

    override fun hashCodeNoIds(): Int =
        name.hashCode() + detectionArea.hashCode() + alphabet.hashCode() +
                targetType.hashCode() + targetName.hashCode() + deduplicate.hashCode()

    override fun deepCopy(): CaptureText = copy(
        name = name?.let { "" + it },
        detectionArea = Rect(detectionArea),
        targetName = "" + targetName,
    )
}
