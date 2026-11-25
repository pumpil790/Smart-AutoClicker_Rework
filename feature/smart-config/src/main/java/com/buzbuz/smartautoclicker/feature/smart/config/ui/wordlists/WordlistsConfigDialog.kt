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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.monitoring.MonitoredOverlayType
import com.buzbuz.smartautoclicker.core.domain.wordlists.WordlistInfo
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonVisibility
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.setEmptyText
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.updateState
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogBaseListBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showDeleteConfirmationDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * Dialog for browsing, creating, editing, importing, exporting, and deleting `.txt` wordlists.
 * When [onWordlistSelected] is provided, tapping a list selects it and dismisses the dialog.
 */
class WordlistsConfigDialog(
    private val onWordlistSelected: ((String) -> Unit)? = null,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.WORDLISTS_CONFIG.name

    private val viewModel: WordlistsConfigViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { wordlistsConfigViewModel() },
    )

    private lateinit var viewBinding: DialogBaseListBinding
    private lateinit var wordlistsAdapter: WordlistsAdapter

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogBaseListBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_wordlists_config)
                setButtonVisibility(DialogNavigationButton.DELETE, View.GONE)
                setButtonVisibility(DialogNavigationButton.SAVE, View.GONE)
                setButtonVisibility(DialogNavigationButton.DISMISS, View.VISIBLE)
                buttonDismiss.setIconResource(R.drawable.ic_back)
                buttonDismiss.setDebouncedOnClickListener { back() }
            }

            floatingButtonsLayout.visibility = View.VISIBLE
            buttonCopy.apply {
                visibility = View.VISIBLE
                setImageResource(R.drawable.ic_copy)
                contentDescription = context.getString(R.string.button_title_import_txt)
                setDebouncedOnClickListener {
                    context.startActivity(WordlistFileActivity.createImportIntent(context))
                }
            }
            buttonNew.apply {
                visibility = View.VISIBLE
                setDebouncedOnClickListener { showWordlistEditor(null) }
            }

            wordlistsAdapter = WordlistsAdapter(
                onItemClicked = ::onItemClicked,
                onEditClicked = { showWordlistEditor(it.name) },
                onExportClicked = { item ->
                    context.startActivity(WordlistFileActivity.createExportIntent(context, item.name))
                },
                onDeleteClicked = { item ->
                    context.showDeleteConfirmationDialog {
                        viewModel.deleteWordlist(item.name)
                    }
                },
            )

            layoutLoadableList.apply {
                list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                list.adapter = wordlistsAdapter
                setEmptyText(
                    id = R.string.message_empty_wordlists_title,
                    secondaryId = R.string.message_empty_wordlists_desc,
                )
            }
        }

        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.wordlists.collect { lists ->
                        viewBinding.layoutLoadableList.updateState(lists)
                        wordlistsAdapter.submitList(lists)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    private fun onItemClicked(item: WordlistInfo) {
        val callback = onWordlistSelected
        if (callback != null) {
            callback(item.name)
            back()
        } else {
            showWordlistEditor(item.name)
        }
    }

    private fun showWordlistEditor(listName: String?) {
        overlayManager.navigateTo(
            context = context,
            newOverlay = WordlistEditorDialog(
                initialListName = listName,
                onSaved = { savedName ->
                    if (onWordlistSelected != null && listName == null && savedName.isNotEmpty()) {
                        onWordlistSelected.invoke(savedName)
                        back()
                    }
                },
            ),
            hideCurrent = true,
        )
    }
}
