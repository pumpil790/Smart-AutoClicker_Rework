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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.swipe

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.UiGesturePosition

enum class SwipeMode(val stateIndex: Int) {
    POSITION(0),
    ON_CONDITION(1),
}

data class SwipeUiState(
    val canBeSaved: Boolean,
    val hasUnsavedModifications: Boolean,
    val name: String?,
    val nameError: Boolean,
    val swipeDuration: String?,
    val swipeDurationError: Boolean,
    val isConditionSelectionVisible: Boolean,
    val swipeMode: SwipeMode,
    val fromPosition: UiGesturePosition,
    val toPosition: UiGesturePosition,
    val fromMotionId: Identifier?,
    val toMotionId: Identifier?,
    val availableConditions: List<ScreenCondition>,
)
