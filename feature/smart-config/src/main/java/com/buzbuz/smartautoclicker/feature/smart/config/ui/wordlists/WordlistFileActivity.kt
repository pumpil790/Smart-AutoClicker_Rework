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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.buzbuz.smartautoclicker.core.common.overlays.di.OverlaysEntryPoint
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.domain.wordlists.IWordlistRepository
import com.buzbuz.smartautoclicker.feature.smart.config.R
import dagger.hilt.EntryPoints
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Translucent Activity used to bridge Overlay dialogs with Android Storage Access Framework (SAF)
 * for importing and exporting `.txt` wordlist files.
 */
@AndroidEntryPoint
class WordlistFileActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ACTION = "com.buzbuz.smartautoclicker.EXTRA_WORDLIST_ACTION"
        private const val EXTRA_LIST_NAME = "com.buzbuz.smartautoclicker.EXTRA_WORDLIST_NAME"
        private const val EXTRA_MERGE = "com.buzbuz.smartautoclicker.EXTRA_WORDLIST_MERGE"

        private const val ACTION_IMPORT = "IMPORT"
        private const val ACTION_EXPORT = "EXPORT"

        fun createImportIntent(context: Context, targetListName: String? = null, merge: Boolean = false): Intent =
            Intent(context, WordlistFileActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ACTION, ACTION_IMPORT)
                .putExtra(EXTRA_LIST_NAME, targetListName)
                .putExtra(EXTRA_MERGE, merge)

        fun createExportIntent(context: Context, listName: String): Intent =
            Intent(context, WordlistFileActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ACTION, ACTION_EXPORT)
                .putExtra(EXTRA_LIST_NAME, listName)
    }

    @Inject lateinit var wordlistRepository: IWordlistRepository

    private val overlayManager: OverlayManager by lazy {
        EntryPoints.get(applicationContext, OverlaysEntryPoint::class.java).overlayManager()
    }

    private var requestedListName: String? = null
    private var requestedMerge: Boolean = false
    private var launched: Boolean = false

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            handleImport(uri)
        }
        finish()
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            handleExport(uri)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent)

        overlayManager.hideAll()

        if (savedInstanceState != null) {
            launched = savedInstanceState.getBoolean("launched", false)
        }

        val action = intent?.getStringExtra(EXTRA_ACTION)
        requestedListName = intent?.getStringExtra(EXTRA_LIST_NAME)
        requestedMerge = intent?.getBooleanExtra(EXTRA_MERGE, false) ?: false

        if (!launched) {
            launched = true
            when (action) {
                ACTION_IMPORT -> importLauncher.launch(arrayOf("text/plain", "*/*"))
                ACTION_EXPORT -> {
                    val safeName = wordlistRepository.sanitizeName(requestedListName ?: "wordlist")
                        .ifEmpty { "wordlist" }
                    exportLauncher.launch("$safeName.txt")
                }
                else -> finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("launched", launched)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.restoreVisibility()
    }

    private fun handleImport(uri: Uri) {
        try {
            val resolvedName = requestedListName?.takeIf { it.isNotBlank() }
                ?: queryDisplayName(uri)
                ?: "imported_list"
            val safeName = wordlistRepository.sanitizeName(resolvedName).ifEmpty { "imported_list" }

            contentResolver.openInputStream(uri)?.use { stream ->
                val count = wordlistRepository.importFromStream(safeName, stream, merge = requestedMerge)
                Toast.makeText(
                    this,
                    getString(R.string.toast_wordlist_imported, count, safeName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to import wordlist from $uri", ex)
            Toast.makeText(this, R.string.toast_wordlist_io_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExport(uri: Uri) {
        val listName = requestedListName ?: return
        val safeName = wordlistRepository.sanitizeName(listName)
        if (safeName.isEmpty()) return

        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                wordlistRepository.exportToStream(safeName, stream)
                Toast.makeText(
                    this,
                    getString(R.string.toast_wordlist_exported, safeName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to export wordlist $safeName to $uri", ex)
            Toast.makeText(this, R.string.toast_wordlist_io_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
}

private const val TAG = "WordlistFileActivity"
