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
package com.buzbuz.smartautoclicker.core.domain.model.condition

/** Comparison source for [ScreenCondition.TextListCheck]. */
enum class TextListCompareMode {
    /** Compare against entries in a `.txt` wordlist file. */
    IN_FILE_LIST,
    /** Compare against a runtime string variable. */
    EQUALS_STRING_VAR,
}

/** Matching algorithm for [ScreenCondition.TextListCheck] and wordlist deduplication. */
enum class TextMatchType {
    /** Exact match after UTF-8 normalization (lowercase, ё->е, whitespace collapse, edge punctuation stripped). */
    EXACT_NORMALIZED,
    /** Fuzzy codepoint Damerau-Levenshtein similarity / substring matching using threshold %. */
    FUZZY_SUBSTRING,
}
