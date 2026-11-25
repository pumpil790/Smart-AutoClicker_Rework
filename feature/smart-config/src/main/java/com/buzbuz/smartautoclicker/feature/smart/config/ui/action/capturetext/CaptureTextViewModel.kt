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

import android.content.Context
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.domain.model.action.CaptureText
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.formatters.getDisplayNameResId
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.formatters.toAreaDisplayText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class CaptureTextViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val configuredAction = editionRepository.editionState.editedActionState
        .mapNotNull { it.value }
        .filterIsInstance<CaptureText>()

    private val editedActionHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedActionState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<CaptureTextUiState?> = configuredAction
        .map { it.toUiState(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(FlowPreview::class)
    val isEditingAction: Flow<Boolean> = editionRepository.isEditingAction
        .distinctUntilChanged()
        .debounce(1000)

    fun hasUnsavedModifications(): Boolean = editedActionHasChanged.value

    fun setName(name: String) {
        updateEditedAction { it.copy(name = name) }
    }

    fun setTargetTypeIndex(index: Int) {
        val type = if (index == 1) CaptureText.TargetType.FILE_LIST else CaptureText.TargetType.STRING_VAR
        updateEditedAction { it.copy(targetType = type) }
    }

    fun setTargetName(targetName: String) {
        updateEditedAction { it.copy(targetName = targetName) }
    }

    fun toggleDeduplicate() {
        updateEditedAction { it.copy(deduplicate = !it.deduplicate) }
    }

    fun setDetectionArea(area: Rect) {
        updateEditedAction { it.copy(detectionArea = area) }
    }

    private fun updateEditedAction(closure: (oldValue: CaptureText) -> CaptureText?) {
        editionRepository.editionState.getEditedAction<CaptureText>()?.let { action ->
            closure(action)?.let { newValue ->
                editionRepository.updateEditedAction(newValue)
            }
        }
    }

    private fun CaptureText.toUiState(context: Context): CaptureTextUiState =
        CaptureTextUiState(
            canBeSaved = isComplete(),
            name = name ?: "",
            nameError = name.isNullOrEmpty(),
            targetType = targetType,
            targetName = targetName,
            targetNameError = targetName.isBlank(),
            deduplicate = deduplicate,
            detectionAreaDescription = detectionArea.toAreaDisplayText(context),
            detectionAreaError = detectionArea.isEmpty,
            alphabetDesc = context.getString(alphabet.getDisplayNameResId()),
        )
}
