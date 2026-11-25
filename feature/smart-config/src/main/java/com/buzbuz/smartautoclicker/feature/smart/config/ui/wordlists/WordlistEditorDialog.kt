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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.wordlists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.monitoring.MonitoredOverlayType
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigWordlistEditorBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showDeleteConfirmationDialog
import com.google.android.material.bottomsheet.BottomSheetDialog

class WordlistEditorDialog(
    private val initialListName: String?,
    private val onSaved: ((String) -> Unit)? = null,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.WORDLIST_EDITOR.name

    private val viewModel: WordlistsConfigViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { wordlistsConfigViewModel() },
    )

    private lateinit var viewBinding: DialogConfigWordlistEditorBinding
    private var currentName: String = initialListName ?: ""

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogConfigWordlistEditorBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_wordlist_editor)
                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onSaveClicked() }
                }
                buttonDelete.apply {
                    visibility = if (initialListName != null) View.VISIBLE else View.GONE
                    setDebouncedOnClickListener { onDeleteClicked() }
                }
            }

            fieldWordlistName.apply {
                setLabel(R.string.field_wordlist_name_label)
                setText(currentName)
                textField.isEnabled = initialListName == null
                setOnTextChangedListener { editable ->
                    currentName = editable.toString()
                    updateSaveState()
                }
            }
            hideSoftInputOnFocusLoss(fieldWordlistName.textField)
            hideSoftInputOnFocusLoss(editWordlistEntries)

            buttonImportTxt.setDebouncedOnClickListener {
                val safeName = viewModel.sanitizeName(currentName).takeIf { it.isNotEmpty() }
                context.startActivity(WordlistFileActivity.createImportIntent(context, safeName, merge = false))
            }

            buttonExportTxt.setDebouncedOnClickListener {
                val safeName = viewModel.sanitizeName(currentName)
                if (safeName.isNotEmpty()) {
                    viewModel.saveEntries(safeName, editWordlistEntries.text?.toString() ?: "")
                    context.startActivity(WordlistFileActivity.createExportIntent(context, safeName))
                }
            }
        }

        loadEntriesIntoEditor()
        updateSaveState()
        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) = Unit

    override fun onStart() {
        super.onStart()
        val safeName = viewModel.sanitizeName(currentName)
        if (safeName.isNotEmpty()) {
            loadEntriesIntoEditor()
        }
    }

    private fun loadEntriesIntoEditor() {
        val safeName = viewModel.sanitizeName(currentName)
        if (safeName.isEmpty()) return
        val entries = viewModel.readEntries(safeName)
        viewBinding.editWordlistEntries.setText(entries.joinToString("\n"))
    }

    private fun updateSaveState() {
        val valid = viewModel.sanitizeName(currentName).isNotEmpty()
        viewBinding.fieldWordlistName.setError(!valid)
        viewBinding.layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, valid)
        viewBinding.buttonExportTxt.isEnabled = valid
    }

    private fun onSaveClicked() {
        val savedName = viewModel.saveEntries(
            listName = currentName,
            rawMultilineText = viewBinding.editWordlistEntries.text?.toString() ?: "",
        )
        if (savedName.isNotEmpty()) {
            onSaved?.invoke(savedName)
            back()
        }
    }

    private fun onDeleteClicked() {
        val target = initialListName ?: return
        context.showDeleteConfirmationDialog {
            viewModel.deleteWordlist(target)
            back()
        }
    }
}
