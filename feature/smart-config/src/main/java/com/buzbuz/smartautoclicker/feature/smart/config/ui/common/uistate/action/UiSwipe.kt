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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.action

import android.content.Context
import androidx.annotation.DrawableRes
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.action.Gesture
import com.buzbuz.smartautoclicker.core.domain.model.action.gesture.GestureFingerMotion
import com.buzbuz.smartautoclicker.core.domain.model.action.position.CoordinatesValue
import com.buzbuz.smartautoclicker.core.domain.model.action.position.Position
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.ui.utils.formatDuration
import com.buzbuz.smartautoclicker.feature.smart.config.R

@DrawableRes
internal fun getSwipeIconRes(): Int =
    R.drawable.ic_swipe

internal fun Gesture.getSwipeDescription(context: Context, parent: Event?, inError: Boolean): String {
    if (inError) return context.getString(R.string.item_error_action_invalid_generic)

    val finger = fingers.firstOrNull()
    val press = finger?.motions?.getOrNull(0) as? GestureFingerMotion.Press
    val move = finger?.motions?.getOrNull(1) as? GestureFingerMotion.Move
    val durationText = formatDuration(getDurationMs())

    if (press == null || move == null) {
        return context.getString(R.string.item_swipe_details_at_position, durationText)
    }

    val usesConditions = press.position is Position.Condition || move.position is Position.Condition
    return if (usesConditions) {
        context.getString(
            R.string.item_swipe_details_on_conditions,
            durationText,
            press.position.formatEndpoint(context, parent),
            move.position.formatEndpoint(context, parent),
        )
    } else {
        context.getString(R.string.item_swipe_details_at_position, durationText)
    }
}

private fun Position.formatEndpoint(context: Context, parent: Event?): String = when (this) {
    is Position.Coordinates -> context.getString(
        R.string.item_swipe_endpoint_coords,
        x.formatCoord(),
        y.formatCoord(),
    )

    is Position.Condition -> when {
        parent?.conditionOperator == OR -> context.getString(R.string.item_swipe_endpoint_condition_unknown)
        conditionId == null -> context.getString(R.string.item_swipe_endpoint_condition_error)
        else -> parent?.conditions?.find { it.id == conditionId }?.name
            ?: context.getString(R.string.item_swipe_endpoint_condition_error)
    }
}

private fun CoordinatesValue.formatCoord(): String = when (this) {
    is CoordinatesValue.Literal -> value.toString()
    is CoordinatesValue.Counter -> "{$counterName}"
}
