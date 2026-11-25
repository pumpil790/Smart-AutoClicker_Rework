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

import android.content.Context
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextListCompareMode
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextMatchType
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

class TextListCheckConditionViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val configuredCondition = editionRepository.editionState.editedScreenConditionState
        .mapNotNull { it.value }
        .filterIsInstance<ScreenCondition.TextListCheck>()

    private val editedConditionHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedScreenConditionState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<TextListCheckConditionUiState?> = configuredCondition
        .map { it.toUiState(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(FlowPreview::class)
    val isEditingCondition: Flow<Boolean> = editionRepository.isEditingCondition
        .distinctUntilChanged()
        .debounce(1000)

    fun hasUnsavedModifications(): Boolean = editedConditionHasChanged.value

    fun isConditionRelatedToClick(): Boolean =
        editionRepository.editionState.isEditedConditionReferencedByAction()

    fun setName(name: String) {
        updateEditedCondition { it.copy(name = name) }
    }

    fun setCompareModeIndex(index: Int) {
        val mode = if (index == 1) TextListCompareMode.EQUALS_STRING_VAR else TextListCompareMode.IN_FILE_LIST
        updateEditedCondition { it.copy(compareMode = mode) }
    }

    fun setTargetName(targetName: String) {
        updateEditedCondition { it.copy(targetName = targetName) }
    }

    fun setMatchTypeIndex(index: Int) {
        val type = if (index == 1) TextMatchType.EXACT_NORMALIZED else TextMatchType.FUZZY_SUBSTRING
        updateEditedCondition { it.copy(matchType = type) }
    }

    fun toggleShouldBeDetected() {
        updateEditedCondition { it.copy(shouldBeDetected = !it.shouldBeDetected) }
    }

    fun setDetectionArea(area: Rect) {
        updateEditedCondition { it.copy(detectionArea = area) }
    }

    fun setThreshold(value: Int) {
        updateEditedCondition { it.copy(threshold = value.coerceIn(50, 100)) }
    }

    private fun updateEditedCondition(
        closure: (oldValue: ScreenCondition.TextListCheck) -> ScreenCondition.TextListCheck?,
    ) {
        editionRepository.editionState.getEditedCondition<ScreenCondition.TextListCheck>()?.let { condition ->
            closure(condition)?.let { newValue ->
                editionRepository.updateEditedCondition(newValue)
            }
        }
    }

    private fun ScreenCondition.TextListCheck.toUiState(context: Context): TextListCheckConditionUiState =
        TextListCheckConditionUiState(
            canBeSaved = isComplete(),
            name = name,
            nameError = name.isEmpty(),
            compareMode = compareMode,
            targetName = targetName,
            targetNameError = targetName.isBlank(),
            matchType = matchType,
            shouldBeDetectedChecked = shouldBeDetected,
            detectionAreaDescription = detectionArea.toAreaDisplayText(context),
            detectionAreaError = detectionArea.isEmpty,
            detectionThreshold = threshold.coerceIn(50, 100),
            alphabetDesc = context.getString(alphabet.getDisplayNameResId()),
        )
}
