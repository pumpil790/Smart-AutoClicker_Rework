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

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Point
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.action.Gesture
import com.buzbuz.smartautoclicker.core.domain.model.action.gesture.GestureFingerMotion
import com.buzbuz.smartautoclicker.core.domain.model.action.position.CoordinatesValue
import com.buzbuz.smartautoclicker.core.domain.model.action.position.Position
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.condition.GetImageConditionBitmapJobUseCase
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.UiGesturePosition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.toPositionUiState
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getEventConfigPreferences
import com.buzbuz.smartautoclicker.feature.smart.config.utils.putSwipeDurationConfig

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

enum class SwipeEndpoint {
    FROM,
    TO,
}

@OptIn(FlowPreview::class)
class SwipeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val getImageConditionBitmapJobUseCase: GetImageConditionBitmapJobUseCase,
    private val displayConfigManager: DisplayConfigManager,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val sharedPreferences: SharedPreferences = context.getEventConfigPreferences()

    private val configuredSwipe = editionRepository.editionState.editedActionState
        .mapNotNull { action -> action.value }
        .filterIsInstance<Gesture>()

    private val editedActionHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedActionState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isEditingAction: Flow<Boolean> = editionRepository.isEditingAction
        .distinctUntilChanged()
        .debounce(1000.milliseconds)

    val uiState: StateFlow<SwipeUiState?> = combine(
        configuredSwipe,
        editionRepository.editionState.editedEventState,
        editionRepository.editionState.editedActionState,
        editionRepository.editionState.editedEventScreenConditionsState,
    ) { swipe, event, actionState, conditionsState ->
        val evt = event.value ?: return@combine null
        swipe.toSwipeUiState(
            event = evt,
            hasUnsavedModifications = actionState.hasChanged,
            canBeSaved = actionState.canBeSaved,
            availableConditions = conditionsState.value?.filter { it.shouldBeDetected } ?: emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun getEditedSwipe(): Gesture? =
        editionRepository.editionState.getEditedAction<Gesture>()

    fun updateGesture(gesture: Gesture) {
        editionRepository.updateEditedAction(gesture)
    }

    fun getConditionBitmap(condition: ScreenCondition.Image, onBitmapLoaded: (Bitmap?) -> Unit): Job =
        getImageConditionBitmapJobUseCase(viewModelScope, condition, onBitmapLoaded)

    fun shouldSelectCondition(): Boolean =
        editionRepository.editionState.getEditedEvent()?.conditionOperator == AND

    fun hasUnsavedModifications(): Boolean =
        editedActionHasChanged.value

    fun setName(name: String) {
        getEditedSwipe()?.let { swipe ->
            editionRepository.updateEditedAction(swipe.copy(name = "" + name))
        }
    }

    fun setSwipeDuration(durationMs: Long?) {
        updateSwipeMotions { _, move ->
            move.copy(durationMs = durationMs ?: 0L)
        }
    }

    fun setSwipeMode(modeIndex: Int?) {
        when (modeIndex) {
            SwipeMode.POSITION.stateIndex -> switchToPositionMode()
            SwipeMode.ON_CONDITION.stateIndex -> switchToOnConditionMode()
        }
    }

    fun setEndpointPosition(endpoint: SwipeEndpoint, position: Point) {
        updateEndpointPosition(endpoint) {
            Position.Coordinates(
                x = CoordinatesValue.Literal(position.x),
                y = CoordinatesValue.Literal(position.y),
            )
        }
    }

    fun setEndpointX(endpoint: SwipeEndpoint, newXValue: String) {
        val x = CoordinatesValue.Literal(newXValue.toIntOrNull() ?: 0)
        updateEndpointPosition(endpoint) { current ->
            when (current) {
                is Position.Coordinates -> current.copy(x = x)
                is Position.Condition -> Position.Coordinates(x = x, y = CoordinatesValue.Literal(0))
            }
        }
    }

    fun setEndpointY(endpoint: SwipeEndpoint, newYValue: String) {
        val y = CoordinatesValue.Literal(newYValue.toIntOrNull() ?: 0)
        updateEndpointPosition(endpoint) { current ->
            when (current) {
                is Position.Coordinates -> current.copy(y = y)
                is Position.Condition -> Position.Coordinates(x = CoordinatesValue.Literal(0), y = y)
            }
        }
    }

    fun setEndpointXCounter(endpoint: SwipeEndpoint, counterName: String) {
        val x = CoordinatesValue.Counter(counterName)
        updateEndpointPosition(endpoint) { current ->
            when (current) {
                is Position.Coordinates -> current.copy(x = x)
                is Position.Condition -> Position.Coordinates(x = x, y = CoordinatesValue.Literal(0))
            }
        }
    }

    fun setEndpointYCounter(endpoint: SwipeEndpoint, counterName: String) {
        val y = CoordinatesValue.Counter(counterName)
        updateEndpointPosition(endpoint) { current ->
            when (current) {
                is Position.Coordinates -> current.copy(y = y)
                is Position.Condition -> Position.Coordinates(x = CoordinatesValue.Literal(0), y = y)
            }
        }
    }

    fun setEndpointCondition(endpoint: SwipeEndpoint, condition: ScreenCondition) {
        updateEndpointPosition(endpoint) {
            Position.Condition(conditionId = condition.id)
        }
    }

    fun setEndpointOnDetectedCondition(endpoint: SwipeEndpoint) {
        updateEndpointPosition(endpoint) {
            Position.Condition(conditionId = null)
        }
    }

    fun removeEndpointCondition(endpoint: SwipeEndpoint) {
        val defaultPos = defaultCoordinates(endpoint)
        updateEndpointPosition(endpoint) { defaultPos }
    }

    fun saveLastConfig() {
        getEditedSwipe()?.let { swipe ->
            val move = swipe.fingers.firstOrNull()?.motions?.getOrNull(1) as? GestureFingerMotion.Move
            if (move != null && move.durationMs in 1 until Gesture.GESTURE_MAXIMUM_DURATION_MS) {
                sharedPreferences.edit { putSwipeDurationConfig(move.durationMs) }
            }
        }
    }

    /**
     * Switches to Condition mode:
     * - In AND events: Start ("From") defaults to Condition 2, End ("To") defaults to Condition 1.
     * - In OR events: both endpoints use the first detected condition (`conditionId = null`).
     */
    private fun switchToOnConditionMode() {
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        val conditions = event.conditions.filter { it.shouldBeDetected }

        val fromPosition: Position
        val toPosition: Position
        if (event.conditionOperator == AND) {
            val condition1Id = conditions.getOrNull(0)?.id
            val condition2Id = conditions.getOrNull(1)?.id ?: condition1Id
            fromPosition = Position.Condition(conditionId = condition2Id)
            toPosition = Position.Condition(conditionId = condition1Id)
        } else {
            fromPosition = Position.Condition(conditionId = null)
            toPosition = Position.Condition(conditionId = null)
        }

        updateSwipePositions(
            transformFrom = { fromPosition },
            transformTo = { toPosition },
        )
    }

    private fun switchToPositionMode() {
        updateSwipePositions(
            transformFrom = { current ->
                if (current is Position.Coordinates) current else defaultCoordinates(SwipeEndpoint.FROM)
            },
            transformTo = { current ->
                if (current is Position.Coordinates) current else defaultCoordinates(SwipeEndpoint.TO)
            },
        )
    }

    private fun defaultCoordinates(endpoint: SwipeEndpoint): Position.Coordinates {
        val displaySize = displayConfigManager.displayConfig.sizePx
        val y = when (endpoint) {
            SwipeEndpoint.FROM -> displaySize.y * 2 / 3
            SwipeEndpoint.TO -> displaySize.y / 3
        }
        return Position.Coordinates(
            x = CoordinatesValue.Literal(displaySize.x / 2),
            y = CoordinatesValue.Literal(y),
        )
    }

    private fun updateEndpointPosition(endpoint: SwipeEndpoint, transform: (Position) -> Position) {
        when (endpoint) {
            SwipeEndpoint.FROM -> updateSwipePositions(transformFrom = transform, transformTo = { it })
            SwipeEndpoint.TO -> updateSwipePositions(transformFrom = { it }, transformTo = transform)
        }
    }

    private fun updateSwipePositions(
        transformFrom: (Position) -> Position,
        transformTo: (Position) -> Position,
    ) {
        val swipe = getEditedSwipe() ?: return
        val finger = swipe.fingers.firstOrNull() ?: return
        val press = finger.motions.getOrNull(0) as? GestureFingerMotion.Press ?: return
        val move = finger.motions.getOrNull(1) as? GestureFingerMotion.Move ?: return
        val release = finger.motions.getOrNull(2) as? GestureFingerMotion.Release ?: return

        val updatedFinger = finger.copy(
            motions = listOf(
                press.copy(position = transformFrom(press.position)),
                move.copy(position = transformTo(move.position)),
                release,
            )
        )
        editionRepository.updateEditedAction(swipe.copy(fingers = listOf(updatedFinger)))
    }

    private fun updateSwipeMotions(
        transform: (GestureFingerMotion.Press, GestureFingerMotion.Move) -> GestureFingerMotion.Move,
    ) {
        val swipe = getEditedSwipe() ?: return
        val finger = swipe.fingers.firstOrNull() ?: return
        val press = finger.motions.getOrNull(0) as? GestureFingerMotion.Press ?: return
        val move = finger.motions.getOrNull(1) as? GestureFingerMotion.Move ?: return
        val release = finger.motions.getOrNull(2) as? GestureFingerMotion.Release ?: return

        val updatedMove = transform(press, move)
        val updatedFinger = finger.copy(motions = listOf(press, updatedMove, release))
        editionRepository.updateEditedAction(swipe.copy(fingers = listOf(updatedFinger)))
    }

    private fun Gesture.toSwipeUiState(
        event: Event,
        availableConditions: List<ScreenCondition>,
        hasUnsavedModifications: Boolean,
        canBeSaved: Boolean,
    ): SwipeUiState? {
        val finger = fingers.firstOrNull() ?: return null
        val press = finger.motions.getOrNull(0) as? GestureFingerMotion.Press ?: return null
        val move = finger.motions.getOrNull(1) as? GestureFingerMotion.Move ?: return null

        val isConditionMode = press.position is Position.Condition || move.position is Position.Condition
        return SwipeUiState(
            canBeSaved = canBeSaved,
            hasUnsavedModifications = hasUnsavedModifications,
            name = name,
            nameError = name.isNullOrEmpty(),
            swipeDuration = if (move.durationMs > 0L) move.durationMs.toString() else "",
            swipeDurationError = move.durationMs !in 1 until Gesture.GESTURE_MAXIMUM_DURATION_MS,
            isConditionSelectionVisible = event is ScreenEvent,
            swipeMode = if (isConditionMode) SwipeMode.ON_CONDITION else SwipeMode.POSITION,
            fromPosition = press.position.toPositionUiState(editionRepository),
            toPosition = move.position.toPositionUiState(editionRepository),
            fromMotionId = press.id,
            toMotionId = move.id,
            availableConditions = availableConditions,
        )
    }
}

