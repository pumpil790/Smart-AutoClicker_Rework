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
package com.buzbuz.smartautoclicker.core.database.migrations

import android.content.Context
import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import com.buzbuz.smartautoclicker.core.database.entity.ActionType
import com.buzbuz.smartautoclicker.core.database.utils.assertColumnEquals
import com.buzbuz.smartautoclicker.core.database.utils.assertColumnNull
import com.buzbuz.smartautoclicker.core.database.utils.assertCountEquals
import com.buzbuz.smartautoclicker.core.database.utils.getInsertV23EmptyAction
import com.buzbuz.smartautoclicker.core.database.utils.getV25Actions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tests the [Migration25to26]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration25to26Tests {

    private companion object {
        private const val OLD_DB_VERSION = 25
        private const val NEW_DB_VERSION = 26
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ClickDatabase::class.java,
    )

    private lateinit var dbPath: String

    @Before
    fun setUp() {
        dbPath = ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath("migration-25-26-test").path
    }

    @Test
    fun migrate_existingConditionsAndActions_keptWithNewColumnsNull() {
        helper.createDatabase(dbPath, OLD_DB_VERSION).apply {
            execSQL(
                """
                    INSERT INTO condition_table (id, eventId, name, type, priority, shouldBeDetected,
                        threshold, detection_area_left, detection_area_top, detection_area_right, detection_area_bottom,
                        text_to_detect, text_alphabet)
                    VALUES (1, 1, "TextCond", "ON_TEXT_DETECTED", 0, 1, 4, 10, 20, 110, 80, "Привет", "CYRILLIC")
                """.trimIndent()
            )
            execSQL(getInsertV23EmptyAction(1L, 1L, "pause", ActionType.PAUSE.name, 0))
            close()
        }

        val dbV26 = helper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration25to26)

        dbV26.query("SELECT * FROM condition_table ORDER BY id").use { cursor ->
            cursor.assertCountEquals(1)
            cursor.moveToFirst()
            cursor.assertColumnEquals("ON_TEXT_DETECTED", "type")
            cursor.assertColumnEquals("Привет", "text_to_detect")
            cursor.assertColumnNull("text_list_mode")
            cursor.assertColumnNull("text_list_match_type")
            cursor.assertColumnNull("text_target_name")
        }

        dbV26.query(getV25Actions()).use { cursor ->
            cursor.assertCountEquals(1)
            cursor.moveToFirst()
            cursor.assertColumnEquals(ActionType.PAUSE.name, "type")
            cursor.assertColumnNull("detection_area_left")
            cursor.assertColumnNull("detection_area_top")
            cursor.assertColumnNull("detection_area_right")
            cursor.assertColumnNull("detection_area_bottom")
            cursor.assertColumnNull("text_alphabet")
            cursor.assertColumnNull("capture_text_target_type")
            cursor.assertColumnNull("capture_text_target_name")
            cursor.assertColumnNull("capture_deduplicate")
        }

        dbV26.close()
    }
}
