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

import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.domain.wordlists.IWordlistRepository
import com.buzbuz.smartautoclicker.core.domain.wordlists.WordlistInfo
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class WordlistsConfigViewModel @Inject constructor(
    private val wordlistRepository: IWordlistRepository,
) : ViewModel() {

    val wordlists: StateFlow<List<WordlistInfo>> = wordlistRepository.wordlists

    fun refresh() {
        wordlistRepository.refresh()
    }

    fun readEntries(listName: String): List<String> =
        wordlistRepository.readEntries(listName)

    fun saveEntries(listName: String, rawMultilineText: String): String {
        val safeName = wordlistRepository.sanitizeName(listName)
        if (safeName.isEmpty()) return ""

        val entries = rawMultilineText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        wordlistRepository.saveEntries(safeName, entries)
        return safeName
    }

    fun sanitizeName(rawName: String): String =
        wordlistRepository.sanitizeName(rawName)

    fun deleteWordlist(listName: String) {
        wordlistRepository.deleteWordlist(listName)
    }
}
