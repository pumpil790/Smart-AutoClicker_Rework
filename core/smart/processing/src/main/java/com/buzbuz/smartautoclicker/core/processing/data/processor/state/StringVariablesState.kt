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
package com.buzbuz.smartautoclicker.core.processing.data.processor.state

import com.buzbuz.smartautoclicker.core.domain.model.action.CaptureText
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextListCompareMode
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.wordlists.IWordlistRepository
import com.buzbuz.smartautoclicker.core.domain.wordlists.TextMatchingUtils

internal interface IStringVariablesState {
    fun getStringVariable(name: String): String?
    fun setStringVariable(name: String, value: String)
    fun getWordlistEntries(listName: String): List<String>
    fun appendToWordlist(listName: String, value: String, deduplicate: Boolean): Boolean
}

internal class StringVariablesState(
    private val screenEvents: List<ScreenEvent>,
    private val wordlistRepository: IWordlistRepository? = null,
) : IStringVariablesState {

    private val stringVariables: MutableMap<String, String> = mutableMapOf()
    private val wordlistCache: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun onProcessingStarted() {
        stringVariables.clear()
        wordlistCache.clear()

        // Preload all referenced wordlists into memory once at scenario start
        val referencedLists = buildSet {
            screenEvents.forEach { event ->
                event.conditions.forEach { condition ->
                    if (condition is ScreenCondition.TextListCheck &&
                        condition.compareMode == TextListCompareMode.IN_FILE_LIST
                    ) {
                        add(normalizeKey(condition.targetName))
                    }
                }
                event.actions.forEach { action ->
                    if (action is CaptureText && action.targetType == CaptureText.TargetType.FILE_LIST) {
                        add(normalizeKey(action.targetName))
                    }
                }
            }
        }

        referencedLists.forEach { key ->
            if (key.isNotEmpty()) {
                wordlistCache[key] = wordlistRepository?.readEntries(key)?.toMutableList() ?: mutableListOf()
            }
        }
    }

    fun onProcessingStopped() {
        stringVariables.clear()
        wordlistCache.clear()
    }

    override fun getStringVariable(name: String): String? =
        stringVariables[name.trim()]

    override fun setStringVariable(name: String, value: String) {
        val key = name.trim()
        if (key.isEmpty()) return
        stringVariables[key] = value.trim()
    }

    override fun getWordlistEntries(listName: String): List<String> {
        val key = normalizeKey(listName)
        if (key.isEmpty()) return emptyList()

        return wordlistCache.getOrPut(key) {
            wordlistRepository?.readEntries(key)?.toMutableList() ?: mutableListOf()
        }
    }

    override fun appendToWordlist(listName: String, value: String, deduplicate: Boolean): Boolean {
        val key = normalizeKey(listName)
        val cleaned = value.replace('\r', ' ').replace('\n', ' ').trim()
        val normalized = TextMatchingUtils.normalize(cleaned)
        if (key.isEmpty() || cleaned.isEmpty() || normalized.isEmpty()) return false

        val entries = wordlistCache.getOrPut(key) {
            wordlistRepository?.readEntries(key)?.toMutableList() ?: mutableListOf()
        }

        if (deduplicate && entries.any { TextMatchingUtils.normalize(it) == normalized }) {
            return false
        }

        val written = wordlistRepository?.appendEntry(key, cleaned, deduplicate) ?: true
        if (written) {
            entries.add(cleaned)
        }
        return written
    }

    private fun normalizeKey(rawName: String): String =
        wordlistRepository?.sanitizeName(rawName)
            ?: rawName.trim().removeSuffix(".txt").trim()
}
