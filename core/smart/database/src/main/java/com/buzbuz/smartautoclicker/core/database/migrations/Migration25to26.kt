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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.buzbuz.smartautoclicker.core.base.migrations.SQLiteColumn
import com.buzbuz.smartautoclicker.core.base.migrations.getSQLiteTableReference
import com.buzbuz.smartautoclicker.core.database.ACTION_TABLE
import com.buzbuz.smartautoclicker.core.database.CONDITION_TABLE

/**
 * Migration from database v25 to v26.
 *
 * Adds support for [com.buzbuz.smartautoclicker.core.database.entity.ConditionType.ON_TEXT_LIST_CHECK]
 * and [com.buzbuz.smartautoclicker.core.database.entity.ActionType.CAPTURE_TEXT].
 */
object Migration25to26 : Migration(25, 26) {

    private val conditionTextListModeColumn = SQLiteColumn.Text("text_list_mode", isNotNull = false)
    private val conditionTextListMatchTypeColumn = SQLiteColumn.Text("text_list_match_type", isNotNull = false)
    private val conditionTextTargetNameColumn = SQLiteColumn.Text("text_target_name", isNotNull = false)

    private val actionDetectionAreaLeftColumn = SQLiteColumn.Int("detection_area_left", isNotNull = false)
    private val actionDetectionAreaTopColumn = SQLiteColumn.Int("detection_area_top", isNotNull = false)
    private val actionDetectionAreaRightColumn = SQLiteColumn.Int("detection_area_right", isNotNull = false)
    private val actionDetectionAreaBottomColumn = SQLiteColumn.Int("detection_area_bottom", isNotNull = false)
    private val actionTextAlphabetColumn = SQLiteColumn.Text("text_alphabet", isNotNull = false)
    private val actionCaptureTextTargetTypeColumn = SQLiteColumn.Text("capture_text_target_type", isNotNull = false)
    private val actionCaptureTextTargetNameColumn = SQLiteColumn.Text("capture_text_target_name", isNotNull = false)
    private val actionCaptureDeduplicateColumn = SQLiteColumn.Boolean("capture_deduplicate", isNotNull = false)

    override fun migrate(db: SupportSQLiteDatabase) {
        db.getSQLiteTableReference(CONDITION_TABLE).alterTableAddColumns(
            setOf(
                conditionTextListModeColumn,
                conditionTextListMatchTypeColumn,
                conditionTextTargetNameColumn,
            )
        )

        db.getSQLiteTableReference(ACTION_TABLE).alterTableAddColumns(
            setOf(
                actionDetectionAreaLeftColumn,
                actionDetectionAreaTopColumn,
                actionDetectionAreaRightColumn,
                actionDetectionAreaBottomColumn,
                actionTextAlphabetColumn,
                actionCaptureTextTargetTypeColumn,
                actionCaptureTextTargetNameColumn,
                actionCaptureDeduplicateColumn,
            )
        )
    }
}
