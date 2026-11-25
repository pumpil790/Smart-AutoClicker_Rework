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
import com.buzbuz.smartautoclicker.core.domain.model.action.CaptureText
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
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setupDescriptions
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigActionCaptureTextBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.areaselector.ConditionAreaSelectorMenu
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.text.alphabet.AlphabetActivity
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.text.alphabet.selection.AlphabetSelectionFragment
import com.buzbuz.smartautoclicker.feature.smart.config.ui.wordlists.WordlistsConfigDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class CaptureTextDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.CAPTURE_TEXT.name

    private val viewModel: CaptureTextViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { captureTextViewModel() },
    )

    private lateinit var viewBinding: DialogConfigActionCaptureTextBinding

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogConfigActionCaptureTextBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_capture_text_action)

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
                    setDebouncedOnClickListener {
                        listener.onDeleteClicked()
                        super.back()
                    }
                }
            }

            fieldName.apply {
                setLabel(R.string.generic_name)
                textField.filters = arrayOf<InputFilter>(
                    InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length)),
                )
                setOnTextChangedListener { viewModel.setName(it.toString()) }
            }
            hideSoftInputOnFocusLoss(fieldName.textField)

            fieldTargetType.apply {
                setTitle(context.getString(R.string.field_capture_text_target_type_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_capture_text_target_type_desc_var),
                        context.getString(R.string.field_capture_text_target_type_desc_list),
                    )
                )
                setButtonConfig(
                    MultiStateButtonConfig(
                        icons = listOf(R.drawable.ic_change_counter, R.drawable.ic_text_condition),
                        selectionRequired = true,
                        singleSelection = true,
                    )
                )
                setOnCheckedListener { checkedId ->
                    checkedId?.let(viewModel::setTargetTypeIndex)
                }
            }

            fieldTargetName.apply {
                setLabel(R.string.field_text_list_target_label_var)
                setOnTextChangedListener { viewModel.setTargetName(it.toString()) }
            }
            hideSoftInputOnFocusLoss(fieldTargetName.textField)

            buttonPickWordlist.setDebouncedOnClickListener { showWordlistPicker() }

            fieldDeduplicate.apply {
                setTitle(context.getString(R.string.field_capture_text_deduplicate_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_capture_text_deduplicate_desc_disabled),
                        context.getString(R.string.field_capture_text_deduplicate_desc_enabled),
                    )
                )
                setOnClickListener { viewModel.toggleDeduplicate() }
            }

            fieldAlphabet.apply {
                setTitle(context.getString(R.string.field_text_detection_alphabet_title))
                setOnClickListener { showAlphabetSelectionDialog() }
            }

            fieldSelectArea.apply {
                setTitle(context.getString(R.string.generic_detection_area_title))
                setOnClickListener { showDetectionAreaSelector() }
            }
        }

        return viewBinding.root
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

    private fun updateUi(uiState: CaptureTextUiState?) {
        if (uiState == null) return

        viewBinding.apply {
            layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, uiState.canBeSaved)
            if (fieldName.textField.text.isNullOrEmpty()) fieldName.setText(uiState.name)
            fieldName.setError(uiState.nameError)

            val isFileList = uiState.targetType == CaptureText.TargetType.FILE_LIST
            val targetTypeIndex = if (isFileList) 1 else 0
            fieldTargetType.setChecked(targetTypeIndex)
            fieldTargetType.setDescription(targetTypeIndex)

            fieldTargetName.setLabel(
                if (isFileList) R.string.field_text_list_target_label_file
                else R.string.field_text_list_target_label_var
            )
            if (fieldTargetName.textField.text?.toString() != uiState.targetName) {
                fieldTargetName.setText(uiState.targetName)
            }
            fieldTargetName.setError(uiState.targetNameError)

            val listVisibility = if (isFileList) View.VISIBLE else View.GONE
            buttonPickWordlist.visibility = listVisibility
            dividerDeduplicate.visibility = listVisibility
            fieldDeduplicate.root.visibility = listVisibility
            fieldDeduplicate.setChecked(uiState.deduplicate)
            fieldDeduplicate.setDescription(if (uiState.deduplicate) 1 else 0)

            fieldAlphabet.setDescription(uiState.alphabetDesc)
            fieldSelectArea.setDescription(uiState.detectionAreaDescription)
            fieldSelectArea.setError(uiState.detectionAreaError)
        }
    }

    private fun onActionEditingStateChanged(isEditingAction: Boolean) {
        if (!isEditingAction) {
            Log.e(TAG, "Closing CaptureTextDialog because there is no action edited")
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

private const val TAG = "CaptureTextDialog"
