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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.textlist

import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.navigation.getTutorialNavigator
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.Tip
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.monitoring.MonitoredOverlayType
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextListCompareMode
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextMatchType
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.MultiStateButtonConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setButtonConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnCheckedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnValueChangedFromUserListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setSliderRange
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setSliderValue
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setValueLabelState
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setupDescriptions
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigConditionTextListBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showDeleteConditionsWithAssociatedActionsDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.OnConditionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.areaselector.ConditionAreaSelectorMenu
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.text.alphabet.AlphabetActivity
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.text.alphabet.selection.AlphabetSelectionFragment
import com.buzbuz.smartautoclicker.feature.smart.config.ui.wordlists.WordlistsConfigDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TextListCheckConditionDialog(
    private val listener: OnConditionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.TEXT_LIST_CHECK_CONDITION.name

    private val viewModel: TextListCheckConditionViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { textListCheckConditionViewModel() },
    )

    private lateinit var viewBinding: DialogConfigConditionTextListBinding

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogConfigConditionTextListBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_text_list_condition)

                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener {
                        listener.onConfirmClicked()
                        super.back()
                    }
                }
                buttonDelete.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onDeleteClicked() }
                }
            }

            fieldEditName.apply {
                setLabel(R.string.generic_name)
                setOnTextChangedListener { viewModel.setName(it.toString()) }
                textField.filters = arrayOf<InputFilter>(
                    InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length)),
                )
            }
            hideSoftInputOnFocusLoss(fieldEditName.textField)

            fieldCompareMode.apply {
                setTitle(context.getString(R.string.field_text_list_mode_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_text_list_mode_desc_file),
                        context.getString(R.string.field_text_list_mode_desc_var),
                    )
                )
                setButtonConfig(
                    MultiStateButtonConfig(
                        icons = listOf(R.drawable.ic_text_condition, R.drawable.ic_change_counter),
                        selectionRequired = true,
                        singleSelection = true,
                    )
                )
                setOnCheckedListener { checkedId ->
                    checkedId?.let(viewModel::setCompareModeIndex)
                }
            }

            fieldTargetName.apply {
                setLabel(R.string.field_text_list_target_label_file)
                setOnTextChangedListener { viewModel.setTargetName(it.toString()) }
            }
            hideSoftInputOnFocusLoss(fieldTargetName.textField)

            buttonPickWordlist.setDebouncedOnClickListener { showWordlistPicker() }

            fieldAlphabet.apply {
                setTitle(context.getString(R.string.field_text_detection_alphabet_title))
                setOnClickListener { showAlphabetSelectionDialog() }
            }

            fieldMatchType.apply {
                setTitle(context.getString(R.string.field_text_list_match_type_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_text_list_match_type_desc_fuzzy),
                        context.getString(R.string.field_text_list_match_type_desc_exact),
                    )
                )
                setButtonConfig(
                    MultiStateButtonConfig(
                        icons = listOf(R.drawable.ic_detect_in_area, R.drawable.ic_detect_exact),
                        selectionRequired = true,
                        singleSelection = true,
                    )
                )
                setOnCheckedListener { checkedId ->
                    checkedId?.let(viewModel::setMatchTypeIndex)
                }
            }

            fieldShouldAppear.apply {
                setTitle(context.getString(R.string.field_condition_visibility_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_condition_visibility_desc_absent),
                        context.getString(R.string.field_condition_visibility_desc_present),
                    )
                )
                setOnClickListener { viewModel.toggleShouldBeDetected() }
            }

            fieldSelectArea.apply {
                setTitle(context.getString(R.string.generic_detection_area_title))
                setOnClickListener { showDetectionAreaSelector() }
            }

            fieldSliderThreshold.apply {
                setTitle(context.getString(R.string.field_text_list_threshold_title))
                setValueLabelState(isEnabled = true, prefix = "%")
                setSliderRange(50f, 100f)
                setOnValueChangedFromUserListener { value -> viewModel.setThreshold(value.roundToInt()) }
            }
        }

        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { viewModel.isEditingCondition.collect(::onConditionEditingStateChanged) }
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

    private fun updateUi(uiState: TextListCheckConditionUiState?) {
        if (uiState == null) return

        viewBinding.apply {
            layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, uiState.canBeSaved)
            if (fieldEditName.textField.text.isNullOrEmpty()) fieldEditName.setText(uiState.name)
            fieldEditName.setError(uiState.nameError)

            val compareModeIndex = if (uiState.compareMode == TextListCompareMode.EQUALS_STRING_VAR) 1 else 0
            fieldCompareMode.setChecked(compareModeIndex)
            fieldCompareMode.setDescription(compareModeIndex)

            val isFileList = uiState.compareMode == TextListCompareMode.IN_FILE_LIST
            fieldTargetName.setLabel(
                if (isFileList) R.string.field_text_list_target_label_file
                else R.string.field_text_list_target_label_var
            )
            if (fieldTargetName.textField.text?.toString() != uiState.targetName) {
                fieldTargetName.setText(uiState.targetName)
            }
            fieldTargetName.setError(uiState.targetNameError)
            buttonPickWordlist.visibility = if (isFileList) View.VISIBLE else View.GONE

            val matchTypeIndex = if (uiState.matchType == TextMatchType.EXACT_NORMALIZED) 1 else 0
            fieldMatchType.setChecked(matchTypeIndex)
            fieldMatchType.setDescription(matchTypeIndex)

            fieldAlphabet.setDescription(uiState.alphabetDesc)
            fieldSelectArea.setDescription(uiState.detectionAreaDescription)
            fieldSelectArea.setError(uiState.detectionAreaError)
            fieldShouldAppear.setChecked(uiState.shouldBeDetectedChecked)
            fieldShouldAppear.setDescription(if (uiState.shouldBeDetectedChecked) 1 else 0)

            cardThreshold.visibility =
                if (uiState.matchType == TextMatchType.FUZZY_SUBSTRING) View.VISIBLE else View.GONE
            fieldSliderThreshold.setSliderValue(uiState.detectionThreshold.toFloat())
        }
    }

    private fun onDeleteClicked() {
        if (viewModel.isConditionRelatedToClick()) {
            context.showDeleteConditionsWithAssociatedActionsDialog { onConfirmDelete() }
            return
        }
        onConfirmDelete()
    }

    private fun onConfirmDelete() {
        listener.onDeleteClicked()
        super.back()
    }

    private fun onConditionEditingStateChanged(isEditing: Boolean) {
        if (!isEditing) {
            Log.e(TAG, "Closing TextListCheckConditionDialog because there is no condition edited")
            finish()
        }
    }

    private fun showDetectionAreaSelector() {
        overlayManager.navigateTo(
            context = context,
            newOverlay = ConditionAreaSelectorMenu(
                onHelpClicked = { context.getTutorialNavigator().showTipDialog(context, Tip.TEXT_DETECTION_AREA) },
                onAreaSelected = viewModel::setDetectionArea,
            ),
            hideCurrent = true,
        )
    }

    private fun showAlphabetSelectionDialog() {
        context.startActivity(
            AlphabetActivity.getStartIntent(context, AlphabetSelectionFragment.FRAGMENT_TAG)
        )
    }

    private fun showWordlistPicker() {
        overlayManager.navigateTo(
            context = context,
            newOverlay = WordlistsConfigDialog(
                onWordlistSelected = { selectedName ->
                    viewModel.setTargetName(selectedName)
                },
            ),
            hideCurrent = true,
        )
    }
}

private const val TAG = "TextListCheckConditionDialog"
