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

import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.monitoring.MonitoredOverlayType
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.MultiStateButtonConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setButtonConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnCheckboxClickedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnCheckedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setup
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setupDescriptions
import com.buzbuz.smartautoclicker.core.ui.utils.MinMaxInputFilter
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigActionSwipeBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.IncludeFieldPositionSelectorBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.gesture.bindings.bindCondition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.gesture.bindings.enableClearCounterOnType
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.gesture.bindings.setPositionValue
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.gesture.editor.GestureEditorOverlayMenu
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.UiGesturePosition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.selection.ScreenConditionSelectionDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.counter.selection.CounterSelectionDialog

import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SwipeDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.SWIPE.name

    private val viewModel: SwipeViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { swipeViewModel() },
    )

    private lateinit var viewBinding: DialogConfigActionSwipeBinding
    private var fromConditionBitmapJob: Job? = null
    private var toConditionBitmapJob: Job? = null

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogConfigActionSwipeBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_swipe)

                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onSaveButtonClicked() }
                }
                buttonDelete.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onDeleteButtonClicked() }
                }
            }

            fieldName.apply {
                setLabel(R.string.generic_name)
                setOnTextChangedListener { viewModel.setName(it.toString()) }
                textField.filters = arrayOf<InputFilter>(
                    InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
            }
            hideSoftInputOnFocusLoss(fieldName.textField)

            fieldSwipeDuration.apply {
                textField.filters = arrayOf(MinMaxInputFilter(1, GESTURE_DURATION_MAX_VALUE.toInt()))
                setLabel(R.string.input_field_label_swipe_duration)
                setOnTextChangedListener {
                    viewModel.setSwipeDuration(if (it.isNotEmpty()) it.toString().toLongOrNull() else null)
                }
            }
            hideSoftInputOnFocusLoss(fieldSwipeDuration.textField)

            fieldSwipeMode.apply {
                setTitle(context.getString(R.string.field_swipe_mode_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_swipe_mode_desc_position),
                        context.getString(R.string.field_swipe_mode_desc_on_condition),
                    )
                )
                setButtonConfig(
                    MultiStateButtonConfig(
                        icons = listOf(R.drawable.ic_position, R.drawable.ic_screen_event),
                        selectionRequired = true,
                        singleSelection = true,
                    )
                )
                setOnCheckedListener(viewModel::setSwipeMode)
            }

            buttonPickSwipePositions.setDebouncedOnClickListener { showSwipePositionsSelector() }

            setupEndpointSelector(fieldPositionFrom, SwipeEndpoint.FROM)
            setupEndpointSelector(fieldPositionTo, SwipeEndpoint.TO)
        }

        return viewBinding.root
    }

    private fun setupEndpointSelector(
        selectorBinding: IncludeFieldPositionSelectorBinding,
        endpoint: SwipeEndpoint,
    ) {
        selectorBinding.apply {
            fieldX.apply {
                setup(
                    label = R.string.field_title_position_x,
                    icon = R.drawable.ic_append_counter,
                    disableInputWithCheckbox = false,
                )
                enableClearCounterOnType()
                setOnTextChangedListener { viewModel.setEndpointX(endpoint, it.toString()) }
                setOnCheckboxClickedListener {
                    showCounterSelectionDialog { counterName ->
                        viewModel.setEndpointXCounter(endpoint, counterName)
                    }
                }
            }
            hideSoftInputOnFocusLoss(fieldX.textField)

            fieldY.apply {
                setup(
                    label = R.string.field_title_position_y,
                    icon = R.drawable.ic_append_counter,
                    disableInputWithCheckbox = false,
                )
                enableClearCounterOnType()
                setOnTextChangedListener { viewModel.setEndpointY(endpoint, it.toString()) }
                setOnCheckboxClickedListener {
                    showCounterSelectionDialog { counterName ->
                        viewModel.setEndpointYCounter(endpoint, counterName)
                    }
                }
            }
            hideSoftInputOnFocusLoss(fieldY.textField)

            buttonSelectPosition.setDebouncedOnClickListener { showSingleEndpointSelector(endpoint) }
            buttonSelectCondition.setDebouncedOnClickListener { onSelectConditionClicked(endpoint) }
            conditionSplash.conditionCard.setDebouncedOnClickListener {
                val uiState = viewModel.uiState.value ?: return@setDebouncedOnClickListener
                val pos = if (endpoint == SwipeEndpoint.FROM) uiState.fromPosition else uiState.toPosition
                if (pos is UiGesturePosition.Condition &&
                    viewModel.shouldSelectCondition() &&
                    uiState.availableConditions.isNotEmpty()
                ) {
                    showConditionSelector(endpoint)
                }
            }
        }
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { viewModel.isEditingAction.collect(::onActionEditingStateChanged) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::updateUi) }
            }
        }
    }

    override fun back() {
        if (viewModel.hasUnsavedModifications()) {
            context.showCloseWithoutSavingDialog {
                listener.onDismissClicked()
                super.back()
            }
            return
        }

        listener.onDismissClicked()
        super.back()
    }

    private fun onSaveButtonClicked() {
        viewModel.saveLastConfig()
        listener.onConfirmClicked()
        super.back()
    }

    private fun onDeleteButtonClicked() {
        listener.onDeleteClicked()
        super.back()
    }

    private fun updateUi(state: SwipeUiState?) {
        state ?: return

        viewBinding.apply {
            layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, state.canBeSaved)
            fieldName.setText(state.name)
            fieldName.setError(state.nameError)
            fieldSwipeDuration.setText(state.swipeDuration, InputType.TYPE_CLASS_NUMBER)
            fieldSwipeDuration.setError(state.swipeDurationError)

            swipeModeCard.isVisible = state.isConditionSelectionVisible
            fieldSwipeMode.apply {
                setChecked(state.swipeMode.stateIndex)
                setDescription(state.swipeMode.stateIndex)
            }

            buttonPickSwipePositions.isVisible = state.swipeMode == SwipeMode.POSITION

            fromConditionBitmapJob?.cancel()
            fromConditionBitmapJob = updateEndpointUi(
                selectorBinding = fieldPositionFrom,
                position = state.fromPosition,
                isConditionSelectionVisible = state.isConditionSelectionVisible,
            )

            toConditionBitmapJob?.cancel()
            toConditionBitmapJob = updateEndpointUi(
                selectorBinding = fieldPositionTo,
                position = state.toPosition,
                isConditionSelectionVisible = state.isConditionSelectionVisible,
            )
        }
    }

    private fun updateEndpointUi(
        selectorBinding: IncludeFieldPositionSelectorBinding,
        position: UiGesturePosition,
        isConditionSelectionVisible: Boolean,
    ): Job? {
        selectorBinding.apply {
            buttonSelectCondition.isVisible = isConditionSelectionVisible

            return when (position) {
                is UiGesturePosition.Condition -> {
                    buttonSelectCondition.setText(R.string.button_title_condition_remove)
                    buttonSelectCondition.setIconResource(R.drawable.ic_cancel)
                    conditionSplash.bindCondition(
                        condition = position.condition,
                        inError = position.inError,
                        bitmapProvider = viewModel::getConditionBitmap,
                    )
                }

                is UiGesturePosition.Position -> {
                    conditionSplash.root.isVisible = false
                    buttonSelectCondition.setText(R.string.button_title_condition_pick)
                    buttonSelectCondition.setIconResource(R.drawable.ic_screen_event)
                    fieldX.setPositionValue(position.xValue)
                    fieldY.setPositionValue(position.yValue)
                    null
                }
            }
        }
    }

    private fun onSelectConditionClicked(endpoint: SwipeEndpoint) {
        val state = viewModel.uiState.value ?: return
        val currentPos = if (endpoint == SwipeEndpoint.FROM) state.fromPosition else state.toPosition
        if (currentPos is UiGesturePosition.Condition) {
            viewModel.removeEndpointCondition(endpoint)
            return
        }

        if (viewModel.shouldSelectCondition()) showConditionSelector(endpoint)
        else viewModel.setEndpointOnDetectedCondition(endpoint)
    }

    private fun showSwipePositionsSelector() {
        val swipe = viewModel.getEditedSwipe() ?: return
        overlayManager.navigateTo(
            context = context,
            newOverlay = GestureEditorOverlayMenu(
                gesture = swipe,
                mode = GestureEditorOverlayMenu.GestureEditorMode.AllMotions(
                    fingerIndex = 0,
                    onGestureEdited = viewModel::updateGesture,
                ),
            ),
            hideCurrent = true,
        )
    }

    private fun showSingleEndpointSelector(endpoint: SwipeEndpoint) {
        val state = viewModel.uiState.value ?: return
        val swipe = viewModel.getEditedSwipe() ?: return
        val motionId: Identifier = when (endpoint) {
            SwipeEndpoint.FROM -> state.fromMotionId
            SwipeEndpoint.TO -> state.toMotionId
        } ?: return

        overlayManager.navigateTo(
            context = context,
            newOverlay = GestureEditorOverlayMenu(
                gesture = swipe,
                mode = GestureEditorOverlayMenu.GestureEditorMode.SingleMotion(
                    motionId = motionId,
                    onPositionConfirmed = { point -> viewModel.setEndpointPosition(endpoint, point) },
                ),
            ),
            hideCurrent = true,
        )
    }

    private fun showConditionSelector(endpoint: SwipeEndpoint) =
        overlayManager.navigateTo(
            context = context,
            newOverlay = ScreenConditionSelectionDialog(
                conditionList = viewModel.uiState.value?.availableConditions ?: emptyList(),
                onConditionSelected = { condition -> viewModel.setEndpointCondition(endpoint, condition) },
            ),
            hideCurrent = false,
        )

    private fun showCounterSelectionDialog(onCounterSelected: (String) -> Unit) =
        overlayManager.navigateTo(
            context = context,
            newOverlay = CounterSelectionDialog(onCounterSelected),
            hideCurrent = false,
        )

    private fun onActionEditingStateChanged(isEditingAction: Boolean) {
        if (!isEditingAction) {
            Log.e(TAG, "Closing SwipeDialog because there is no action edited")
            finish()
        }
    }
}

private const val TAG = "SwipeDialog"
