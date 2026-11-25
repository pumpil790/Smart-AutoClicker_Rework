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
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextMatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class TextMatchingAndWordlistTests {

    private lateinit var wordlistRepository: WordlistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        wordlistRepository = WordlistRepository(context)
        wordlistRepository.wordlists.value.forEach { wordlistRepository.deleteWordlist(it.name) }
    }

    @Test
    fun normalize_cyrillicCaseYoAndPunctuation() {
        assertEquals("роба", TextMatchingUtils.normalize("  Роба.  "))
        assertEquals("королевское одеяние", TextMatchingUtils.normalize("Королевское\n  одеяние!"))
        assertEquals("достойная прическа", TextMatchingUtils.normalize("ДОСТОЙНАЯ   ПРИЧЁСКА,"))
        assertEquals("выбрать", TextMatchingUtils.normalize("Выбрать."))
    }

    @Test
    fun matchText_exactNormalized_handlesYoAndCase() {
        val (matched, score) = TextMatchingUtils.matchText(
            recognizedText = "Достойная ПРИЧЁСКА.",
            targetText = "достойная прическа",
            matchType = TextMatchType.EXACT_NORMALIZED,
            thresholdPercent = 100,
        )
        assertTrue(matched)
        assertEquals(100.0, score, 0.01)
    }

    @Test
    fun matchText_fuzzySubstring_handlesRussianGrammaticalCases() {
        val (matched, score) = TextMatchingUtils.matchText(
            recognizedText = "Достойную причёску",
            targetText = "Достойная причёска",
            matchType = TextMatchType.FUZZY_SUBSTRING,
            thresholdPercent = 80,
        )
        assertTrue("Expected Russian case variants to match at >=80%, got $score%", matched)
        assertTrue(score >= 80.0)
    }

    @Test
    fun matchText_fuzzySubstring_rejectsUnrelatedTextAndEmpty() {
        val (matchedUnrelated, _) = TextMatchingUtils.matchText(
            recognizedText = "Платье принцессы",
            targetText = "Королевское одеяние",
            matchType = TextMatchType.FUZZY_SUBSTRING,
            thresholdPercent = 80,
        )
        assertFalse(matchedUnrelated)

        val (matchedEmpty, _) = TextMatchingUtils.matchText(
            recognizedText = "   ",
            targetText = "Королевское одеяние",
            matchType = TextMatchType.FUZZY_SUBSTRING,
            thresholdPercent = 80,
        )
        assertFalse(matchedEmpty)
    }

    @Test
    fun matchAgainstList_multilinePhraseAndCaseVariation() {
        val list = listOf(
            "Королевское одеяние",
            "Достойная причёска",
            "Поддержать подданных и дать им надежду",
        )

        val (matched1, _) = TextMatchingUtils.matchAgainstList(
            recognizedText = "Королевское одеяние",
            entries = list,
            matchType = TextMatchType.FUZZY_SUBSTRING,
            thresholdPercent = 80,
        )
        assertTrue(matched1)

        val (matched2, _) = TextMatchingUtils.matchAgainstList(
            recognizedText = "Достойную прическу",
            entries = list,
            matchType = TextMatchType.FUZZY_SUBSTRING,
            thresholdPercent = 80,
        )
        assertTrue(matched2)

        val (matched3, _) = TextMatchingUtils.matchAgainstList(
            recognizedText = "Поддержать подданных и дать им надежду.",
            entries = list,
            matchType = TextMatchType.EXACT_NORMALIZED,
            thresholdPercent = 100,
        )
        assertTrue(matched3)
    }

    @Test
    fun wordlistRepository_saveAppendDeduplicateImportExport() {
        wordlistRepository.saveEntries("wardrobe.txt", listOf("Королевское одеяние", "  "))
        assertEquals(listOf("Королевское одеяние"), wordlistRepository.readEntries("wardrobe"))

        // Normalized duplicate should be skipped when deduplicate = true
        assertFalse(wordlistRepository.appendEntry("wardrobe", "королевское одеяние.", deduplicate = true))
        assertEquals(1, wordlistRepository.readEntries("wardrobe").size)

        // New item appends cleanly
        assertTrue(wordlistRepository.appendEntry("wardrobe", "Достойная причёска", deduplicate = true))
        assertEquals(listOf("Королевское одеяние", "Достойная причёска"), wordlistRepository.readEntries("wardrobe"))

        // Export & import into a second list
        val exported = ByteArrayOutputStream()
        wordlistRepository.exportToStream("wardrobe", exported)
        val importedCount = wordlistRepository.importFromStream(
            listName = "wardrobe_copy",
            input = ByteArrayInputStream(exported.toByteArray()),
            merge = false,
        )
        assertEquals(2, importedCount)
        assertEquals(
            listOf("Королевское одеяние", "Достойная причёска"),
            wordlistRepository.readEntries("wardrobe_copy"),
        )
    }

    @Test
    fun homoglyphFolding_cyrillicEnabled_matchesExactFuzzyAndDeduplicates() {
        val (exactMatched, exactScore) = TextMatchingUtils.matchText(
            recognizedText = "Королевcкоe одеяниe",
            targetText = "Королевское одеяние",
            matchType = TextMatchType.EXACT_NORMALIZED,
            thresholdPercent = 100,
            foldCyrillicHomoglyphs = true,
        )
        assertTrue(exactMatched)
        assertEquals(100.0, exactScore, 0.01)

        val (fuzzyMatched, fuzzyScore) = TextMatchingUtils.matchText(
            recognizedText = "Королевcкую одеяниe",
            targetText = "Королевское одеяние",
            matchType = TextMatchType.FUZZY_SUBSTRING,
            thresholdPercent = 80,
            foldCyrillicHomoglyphs = true,
        )
        assertTrue("Expected homoglyph + Russian case variant to match at >=80%, got $fuzzyScore%", fuzzyMatched)
        assertTrue(fuzzyScore >= 80.0)

        val (safetyMatched, safetyScore) = TextMatchingUtils.matchText(
            recognizedText = "бe3опаcHOсTи.",
            targetText = "безопасности",
            matchType = TextMatchType.EXACT_NORMALIZED,
            thresholdPercent = 100,
            foldCyrillicHomoglyphs = true,
        )
        assertTrue(safetyMatched)
        assertEquals(100.0, safetyScore, 0.01)

        // Pure numeric tokens must not be corrupted by 3->з / 6->б
        assertEquals("18", TextMatchingUtils.normalize("18", foldCyrillicHomoglyphs = true))
        assertEquals("577", TextMatchingUtils.normalize("577", foldCyrillicHomoglyphs = true))
        assertEquals("наряд 3", TextMatchingUtils.normalize("Наряд 3", foldCyrillicHomoglyphs = true))
        assertEquals("глава 6", TextMatchingUtils.normalize("Глава 6", foldCyrillicHomoglyphs = true))

        // Wordlist deduplication with foldCyrillicHomoglyphs = true skips homoglyph duplicates
        wordlistRepository.saveEntries("wardrobe", listOf("Королевское одеяние"))
        assertFalse(
            wordlistRepository.appendEntry(
                listName = "wardrobe",
                rawEntry = "Королевcкоe одеяниe",
                deduplicate = true,
                foldCyrillicHomoglyphs = true,
            )
        )
        assertEquals(1, wordlistRepository.readEntries("wardrobe").size)
    }

    @Test
    fun homoglyphFolding_disabled_preservesV2Behavior() {
        assertEquals("abcehkmoptxy", TextMatchingUtils.normalize("ABCEHKMOPTXY", foldCyrillicHomoglyphs = false))

        val (matched, _) = TextMatchingUtils.matchText(
            recognizedText = "Королевcкоe одеяниe",
            targetText = "Королевское одеяние",
            matchType = TextMatchType.EXACT_NORMALIZED,
            thresholdPercent = 100,
            foldCyrillicHomoglyphs = false,
        )
        assertFalse(matched)
    }
}
