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
package com.buzbuz.smartautoclicker.core.domain.wordlists

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

const val WORDLISTS_DIR_NAME = "wordlists"
const val WORDLIST_FILE_EXTENSION = ".txt"

data class WordlistInfo(
    val name: String,
    val entryCount: Int,
)

interface IWordlistRepository {
    val wordlists: StateFlow<List<WordlistInfo>>

    fun refresh()
    fun sanitizeName(rawName: String): String
    fun getWordlistFile(listName: String): File
    fun getWordlistRelativePath(listName: String): String
    fun readEntries(listName: String): List<String>
    fun saveEntries(listName: String, entries: List<String>)
    fun appendEntry(listName: String, rawEntry: String, deduplicate: Boolean): Boolean
    fun deleteWordlist(listName: String): Boolean
    fun importFromStream(listName: String, input: InputStream, merge: Boolean = false): Int
    fun exportToStream(listName: String, output: OutputStream)
}

@Singleton
class WordlistRepository @Inject constructor(
    @ApplicationContext context: Context,
) : IWordlistRepository {

    private val wordlistsDir: File = File(context.filesDir, WORDLISTS_DIR_NAME)
    private val lock = Any()

    private val _wordlists = MutableStateFlow<List<WordlistInfo>>(emptyList())
    override val wordlists: StateFlow<List<WordlistInfo>> = _wordlists.asStateFlow()

    init {
        refresh()
    }

    override fun refresh() {
        synchronized(lock) {
            _wordlists.value = scanWordlistsLocked()
        }
    }

    override fun sanitizeName(rawName: String): String {
        val withoutExt = rawName.trim().removeSuffix(WORDLIST_FILE_EXTENSION).trim()
        return withoutExt.replace(INVALID_FILENAME_CHARS, "_").trim()
    }

    override fun getWordlistFile(listName: String): File {
        val safeName = sanitizeName(listName)
        return File(wordlistsDir, "$safeName$WORDLIST_FILE_EXTENSION")
    }

    override fun getWordlistRelativePath(listName: String): String {
        val safeName = sanitizeName(listName)
        return "$WORDLISTS_DIR_NAME/$safeName$WORDLIST_FILE_EXTENSION"
    }

    override fun readEntries(listName: String): List<String> = synchronized(lock) {
        val safeName = sanitizeName(listName)
        if (safeName.isEmpty()) return emptyList()

        val file = File(wordlistsDir, "$safeName$WORDLIST_FILE_EXTENSION")
        if (!file.exists() || !file.isFile) return emptyList()

        file.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    override fun saveEntries(listName: String, entries: List<String>) {
        synchronized(lock) {
            val safeName = sanitizeName(listName)
            if (safeName.isEmpty()) return

            ensureDirExistsLocked()
            val cleaned = entries.map { it.trim() }.filter { it.isNotEmpty() }
            val file = File(wordlistsDir, "$safeName$WORDLIST_FILE_EXTENSION")
            val content = if (cleaned.isEmpty()) "" else cleaned.joinToString("\n", postfix = "\n")
            file.writeText(content, Charsets.UTF_8)
            _wordlists.value = scanWordlistsLocked()
        }
    }

    override fun appendEntry(listName: String, rawEntry: String, deduplicate: Boolean): Boolean = synchronized(lock) {
        val safeName = sanitizeName(listName)
        val cleaned = rawEntry.replace('\r', ' ').replace('\n', ' ').trim()
        val normalizedNew = TextMatchingUtils.normalize(cleaned)
        if (safeName.isEmpty() || cleaned.isEmpty() || normalizedNew.isEmpty()) return false

        ensureDirExistsLocked()
        val file = File(wordlistsDir, "$safeName$WORDLIST_FILE_EXTENSION")
        val existing = if (file.exists() && file.isFile) {
            file.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        if (deduplicate && existing.any { TextMatchingUtils.normalize(it) == normalizedNew }) {
            return false
        }

        val needsLeadingNewline = file.exists() && file.length() > 0L && !endsWithNewline(file)
        file.appendText((if (needsLeadingNewline) "\n" else "") + cleaned + "\n", Charsets.UTF_8)
        _wordlists.value = scanWordlistsLocked()
        true
    }

    override fun deleteWordlist(listName: String): Boolean = synchronized(lock) {
        val safeName = sanitizeName(listName)
        if (safeName.isEmpty()) return false

        val file = File(wordlistsDir, "$safeName$WORDLIST_FILE_EXTENSION")
        val deleted = if (file.exists()) file.delete() else false
        _wordlists.value = scanWordlistsLocked()
        deleted
    }

    override fun importFromStream(listName: String, input: InputStream, merge: Boolean): Int = synchronized(lock) {
        val safeName = sanitizeName(listName)
        if (safeName.isEmpty()) return 0

        val importedLines = input.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() }

        val finalLines = if (merge) {
            val existing = readEntries(safeName)
            val seen = existing.mapTo(mutableSetOf()) { TextMatchingUtils.normalize(it) }
            buildList {
                addAll(existing)
                importedLines.forEach { line ->
                    val norm = TextMatchingUtils.normalize(line)
                    if (norm.isNotEmpty() && seen.add(norm)) add(line)
                }
            }
        } else {
            importedLines
        }

        saveEntries(safeName, finalLines)
        importedLines.size
    }

    override fun exportToStream(listName: String, output: OutputStream) {
        val entries = readEntries(listName)
        val content = if (entries.isEmpty()) "" else entries.joinToString("\n", postfix = "\n")
        output.write(content.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun ensureDirExistsLocked() {
        if (!wordlistsDir.exists()) wordlistsDir.mkdirs()
    }

    private fun scanWordlistsLocked(): List<WordlistInfo> {
        if (!wordlistsDir.exists() || !wordlistsDir.isDirectory) return emptyList()
        return wordlistsDir.listFiles { file ->
            file.isFile && file.name.endsWith(WORDLIST_FILE_EXTENSION, ignoreCase = true)
        }?.map { file ->
            val name = file.name.removeSuffix(WORDLIST_FILE_EXTENSION)
            val count = file.readLines(Charsets.UTF_8).count { it.isNotBlank() }
            WordlistInfo(name = name, entryCount = count)
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    private fun endsWithNewline(file: File): Boolean =
        try {
            file.inputStream().use { stream ->
                val len = file.length()
                if (len <= 0L) return true
                stream.skip(len - 1)
                stream.read() == '\n'.code
            }
        } catch (_: Exception) {
            true
        }

    private companion object {
        val INVALID_FILENAME_CHARS = Regex("""[/\\:*?"<>|]""")
    }
}
