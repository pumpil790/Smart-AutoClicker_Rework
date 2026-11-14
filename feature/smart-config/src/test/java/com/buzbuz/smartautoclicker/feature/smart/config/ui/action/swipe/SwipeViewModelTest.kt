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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.swipe

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.bitmaps.BitmapRepository
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfig
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.action.Gesture
import com.buzbuz.smartautoclicker.core.domain.model.action.gesture.GestureFingerMotion
import com.buzbuz.smartautoclicker.core.domain.model.action.position.Position
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.condition.GetImageConditionBitmapJobUseCase

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class SwipeViewModelTest {

    private lateinit var context: Context
    private lateinit var displayConfigManager: DisplayConfigManager
    private lateinit var repository: IRepository
    private lateinit var editionRepository: EditionRepository
    private lateinit var viewModel: SwipeViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        displayConfigManager = mockk(relaxed = true)
        every { displayConfigManager.displayConfig } returns mockk<DisplayConfig>(relaxed = true).apply {
            every { sizePx } returns Point(1080, 2400)
        }
        repository = mockk(relaxed = true)
        editionRepository = EditionRepository(
            repository = repository,
            bitmapRepository = mockk<BitmapRepository>(relaxed = true),
            displayConfigManager = displayConfigManager,
        )
        viewModel = SwipeViewModel(
            context = context,
            getImageConditionBitmapJobUseCase = mockk<GetImageConditionBitmapJobUseCase>(relaxed = true),
            displayConfigManager = displayConfigManager,
            editionRepository = editionRepository,
        )
    }

    @Test
    fun `createNewSwipe builds a simple swipe gesture`() = runTest {
        startEditingEvent(conditions = emptyList())
        val swipe = editionRepository.editedItemsBuilder.createNewSwipe(context)

        assertTrue(swipe.isSimpleSwipe())
        assertTrue(swipe.isComplete())
    }

    @Test
    fun `switching to on condition mode defaults From to condition 2 and To to condition 1`() = runTest {
        val cond1 = colorCondition(1L, "Cond 1")
        val cond2 = colorCondition(2L, "Cond 2")
        startEditingEvent(conditions = listOf(cond1, cond2), conditionOperator = AND)

        val swipe = editionRepository.editedItemsBuilder.createNewSwipe(context)
        editionRepository.startActionEdition(swipe)

        viewModel.setSwipeMode(SwipeMode.ON_CONDITION.stateIndex)

        val updated = viewModel.getEditedSwipe()
        assertNotNull(updated)
        val motions = updated!!.fingers.first().motions
        assertEquals(Position.Condition(cond2.id), (motions[0] as GestureFingerMotion.Press).position)
        assertEquals(Position.Condition(cond1.id), (motions[1] as GestureFingerMotion.Move).position)
    }

    @Test
    fun `switching to on condition mode in OR event clears condition ids`() = runTest {
        val cond1 = colorCondition(1L, "Cond 1")
        val cond2 = colorCondition(2L, "Cond 2")
        startEditingEvent(conditions = listOf(cond1, cond2), conditionOperator = OR)

        val swipe = editionRepository.editedItemsBuilder.createNewSwipe(context)
        editionRepository.startActionEdition(swipe)

        viewModel.setSwipeMode(SwipeMode.ON_CONDITION.stateIndex)

        val updated = viewModel.getEditedSwipe()!!
        val motions = updated.fingers.first().motions
        assertEquals(Position.Condition(null), (motions[0] as GestureFingerMotion.Press).position)
        assertEquals(Position.Condition(null), (motions[1] as GestureFingerMotion.Move).position)
    }

    @Test
    fun `switching back to position mode restores literal coordinates`() = runTest {
        val cond1 = colorCondition(1L, "Cond 1")
        val cond2 = colorCondition(2L, "Cond 2")
        startEditingEvent(conditions = listOf(cond1, cond2), conditionOperator = AND)

        val swipe = editionRepository.editedItemsBuilder.createNewSwipe(context)
        editionRepository.startActionEdition(swipe)

        viewModel.setSwipeMode(SwipeMode.ON_CONDITION.stateIndex)
        viewModel.setSwipeMode(SwipeMode.POSITION.stateIndex)

        val updated = viewModel.getEditedSwipe()!!
        assertFalse(updated.hasConditionPositions())
        val motions = updated.fingers.first().motions
        assertTrue((motions[0] as GestureFingerMotion.Press).position is Position.Coordinates)
        assertTrue((motions[1] as GestureFingerMotion.Move).position is Position.Coordinates)
    }

    private suspend fun startEditingEvent(
        conditions: List<ScreenCondition>,
        conditionOperator: Int = AND,
    ) {
        val scenario = Scenario(id = Identifier(databaseId = 1L), name = "Scenario", detectionQuality = 600)
        val event = ScreenEvent(
            id = Identifier(databaseId = 10L),
            scenarioId = scenario.id,
            name = "Event",
            conditionOperator = conditionOperator,
            conditions = conditions,
            actions = emptyList(),
            priority = 0,
            keepDetecting = false,
            cooldownMs = 0L,
        )
        coEvery { repository.getScenario(1L) } returns scenario
        coEvery { repository.getScreenEvents(1L) } returns listOf(event)
        coEvery { repository.getTriggerEvents(1L) } returns emptyList()
        coEvery { repository.getCounters(1L) } returns emptyList()

        assertTrue(editionRepository.startEdition(1L))
        editionRepository.startEventEdition(event)
    }

    private fun colorCondition(id: Long, name: String) = ScreenCondition.Color(
        id = Identifier(databaseId = id),
        eventId = Identifier(databaseId = 10L),
        name = name,
        threshold = 10,
        shouldBeDetected = true,
        priority = 0,
        color = 0xFF0000,
        detectionArea = Rect(0, 0, 10, 10),
    )
}
