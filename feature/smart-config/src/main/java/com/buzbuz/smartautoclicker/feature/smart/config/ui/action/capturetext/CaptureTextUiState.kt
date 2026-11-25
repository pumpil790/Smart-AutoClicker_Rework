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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.capturetext

import com.buzbuz.smartautoclicker.core.domain.model.action.CaptureText

data class CaptureTextUiState(
    val canBeSaved: Boolean,
    val name: String,
    val nameError: Boolean,
    val targetType: CaptureText.TargetType,
    val targetName: String,
    val targetNameError: Boolean,
    val deduplicate: Boolean,
    val detectionAreaDescription: String,
    val detectionAreaError: Boolean,
    val alphabetDesc: String,
)
