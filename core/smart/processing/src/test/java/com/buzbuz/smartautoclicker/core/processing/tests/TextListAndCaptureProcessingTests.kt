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
package com.buzbuz.smartautoclicker.core.processing.tests

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRAlphabet
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.detection.DetectionResult
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.detection.TextReadResult
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfig
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.EXACT
import com.buzbuz.smartautoclicker.core.domain.model.action.CaptureText
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.EventToggle
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextListCompareMode
import com.buzbuz.smartautoclicker.core.domain.model.condition.TextMatchType
import com.buzbuz.smartautoclicker.core.domain.model.counter.Counter
import com.buzbuz.smartautoclicker.core.domain.model.counter.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.wordlists.WordlistRepository
import com.buzbuz.smartautoclicker.core.processing.data.processor.ScenarioProcessor
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end tests of string variables, `.txt` wordlists, live learning, full-loop detection,
 * and per-frame OCR caching in [ScenarioProcessor].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class TextListAndCaptureProcessingTests {

    @RelaxedMockK private lateinit var mockImageDetector: ImageDetector
    @RelaxedMockK private lateinit var mockAndroidExecutor: AndroidActionExecutor
    @RelaxedMockK private lateinit var mockDisplayConfigManager: DisplayConfigManager
    @RelaxedMockK private lateinit var mockScreenBitmap: Bitmap
    @RelaxedMockK private lateinit var mockArrowBitmap: Bitmap

    private lateinit var context: Context
    private lateinit var wordlistRepository: WordlistRepository
    private lateinit var scalingManager: ScalingManager

    private val titleArea = Rect(100, 100, 600, 220)
    private val arrowArea = Rect(20, 400, 80, 460)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = ApplicationProvider.getApplicationContext()
        wordlistRepository = WordlistRepository(context)
        wordlistRepository.deleteWordlist("wardrobe")

        every { mockDisplayConfigManager.displayConfig } returns DisplayConfig(
            sizePx = Point(1080, 2400),
            orientation = 1,
            safeInsetTopPx = 0,
            roundedCorners = emptyMap(),
        )
        scalingManager = ScalingManager(mockDisplayConfigManager)

        every { mockImageDetector.detectImage(any(), any(), any(), any(), any()) } returns
            DetectionResult(isDetected = true, confidenceRate = 99.0, position = Point(50, 430), size = Point(60, 60))
    }

    @Test
    fun wardrobeWorkflow_liveLearningAndPerFrameCache() = runTest {
        wordlistRepository.saveEntries("wardrobe", listOf("Достойная причёска", "Королевское одеяние"))

        val ev1Id = Identifier(databaseId = 1L)
        val ev2Id = Identifier(databaseId = 2L)
        val ev3Id = Identifier(databaseId = 3L)
        val ev4Id = Identifier(databaseId = 4L)
        val scenarioId = Identifier(databaseId = 100L)

        // Event 1: Start -> capture first_item, reset counter, enable ev2/ev3/ev4, disable ev1
        val event1 = ScreenEvent(
            id = ev1Id,
            scenarioId = scenarioId,
            name = "Start",
            conditionOperator = AND,
            priority = 0,
            enabledOnStart = true,
            keepDetecting = false,
            cooldownMs = 0L,
            conditions = listOf(
                ScreenCondition.Image(
                    id = Identifier(databaseId = 11L),
                    eventId = ev1Id,
                    name = "Arrow",
                    threshold = 4,
                    shouldBeDetected = true,
                    priority = 0,
                    path = "arrow.png",
                    area = arrowArea,
                    detectionType = EXACT,
                )
            ),
            actions = listOf(
                CaptureText(
                    id = Identifier(databaseId = 101L),
                    eventId = ev1Id,
                    name = "Capture first_item",
                    detectionArea = titleArea,
                    alphabet = OCRAlphabet.CYRILLIC,
                    targetType = CaptureText.TargetType.STRING_VAR,
                    targetName = "first_item",
                ),
                ChangeCounter(
                    id = Identifier(databaseId = 102L),
                    eventId = ev1Id,
                    name = "Reset steps",
                    counterName = "wardrobe_steps",
                    operation = ChangeCounter.OperationType.SET,
                    operationValue = CounterOperationValue.Number(0.0),
                ),
                ToggleEvent(
                    id = Identifier(databaseId = 103L),
                    eventId = ev1Id,
                    name = "Switch to loop",
                    toggleAll = false,
                    eventToggles = listOf(
                        EventToggle(Identifier(databaseId = 1L), Identifier(databaseId = 103L), ToggleEvent.ToggleType.DISABLE, ev1Id),
                        EventToggle(Identifier(databaseId = 2L), Identifier(databaseId = 103L), ToggleEvent.ToggleType.ENABLE, ev2Id),
                        EventToggle(Identifier(databaseId = 3L), Identifier(databaseId = 103L), ToggleEvent.ToggleType.ENABLE, ev3Id),
                        EventToggle(Identifier(databaseId = 4L), Identifier(databaseId = 103L), ToggleEvent.ToggleType.ENABLE, ev4Id),
                    ),
                ),
            ),
        )

        // Event 2: Full loop -> matches first_item (disabled initially, highest priority after ev1)
        val event2 = ScreenEvent(
            id = ev2Id,
            scenarioId = scenarioId,
            name = "Full loop",
            conditionOperator = AND,
            priority = 1,
            enabledOnStart = false,
            keepDetecting = false,
            cooldownMs = 0L,
            conditions = listOf(
                ScreenCondition.TextListCheck(
                    id = Identifier(databaseId = 21L),
                    eventId = ev2Id,
                    name = "Matches first_item",
                    threshold = 90,
                    shouldBeDetected = true,
                    priority = 0,
                    detectionArea = titleArea,
                    alphabet = OCRAlphabet.CYRILLIC,
                    compareMode = TextListCompareMode.EQUALS_STRING_VAR,
                    matchType = TextMatchType.EXACT_NORMALIZED,
                    targetName = "first_item",
                )
            ),
            actions = listOf(
                ChangeCounter(
                    id = Identifier(databaseId = 201L),
                    eventId = ev2Id,
                    name = "Mark loop done",
                    counterName = "loop_done",
                    operation = ChangeCounter.OperationType.SET,
                    operationValue = CounterOperationValue.Number(1.0),
                )
            ),
        )

        // Event 3: Already in wardrobe.txt -> increment wardrobe_steps
        val event3 = ScreenEvent(
            id = ev3Id,
            scenarioId = scenarioId,
            name = "Already owned",
            conditionOperator = AND,
            priority = 2,
            enabledOnStart = false,
            keepDetecting = false,
            cooldownMs = 0L,
            conditions = listOf(
                ScreenCondition.TextListCheck(
                    id = Identifier(databaseId = 31L),
                    eventId = ev3Id,
                    name = "In wardrobe",
                    threshold = 80,
                    shouldBeDetected = true,
                    priority = 0,
                    detectionArea = titleArea,
                    alphabet = OCRAlphabet.CYRILLIC,
                    compareMode = TextListCompareMode.IN_FILE_LIST,
                    matchType = TextMatchType.FUZZY_SUBSTRING,
                    targetName = "wardrobe",
                )
            ),
            actions = listOf(
                ChangeCounter(
                    id = Identifier(databaseId = 301L),
                    eventId = ev3Id,
                    name = "Step +1",
                    counterName = "wardrobe_steps",
                    operation = ChangeCounter.OperationType.ADD,
                    operationValue = CounterOperationValue.Number(1.0),
                )
            ),
        )

        // Event 4: New item -> append to wardrobe.txt
        val event4 = ScreenEvent(
            id = ev4Id,
            scenarioId = scenarioId,
            name = "New item",
            conditionOperator = AND,
            priority = 3,
            enabledOnStart = false,
            keepDetecting = false,
            cooldownMs = 0L,
            conditions = listOf(
                ScreenCondition.Image(
                    id = Identifier(databaseId = 41L),
                    eventId = ev4Id,
                    name = "Arrow",
                    threshold = 4,
                    shouldBeDetected = true,
                    priority = 0,
                    path = "arrow.png",
                    area = arrowArea,
                    detectionType = EXACT,
                )
            ),
            actions = listOf(
                CaptureText(
                    id = Identifier(databaseId = 401L),
                    eventId = ev4Id,
                    name = "Learn item",
                    detectionArea = titleArea,
                    alphabet = OCRAlphabet.CYRILLIC,
                    targetType = CaptureText.TargetType.FILE_LIST,
                    targetName = "wardrobe",
                    deduplicate = true,
                )
            ),
        )

        val screenEvents = listOf(event1, event2, event3, event4)
        scalingManager.startScaling(quality = 10000.0, screenEvents = screenEvents)

        val processor = ScenarioProcessor(
            processingTag = "test",
            imageDetector = mockImageDetector,
            scalingManager = scalingManager,
            randomize = false,
            screenEvents = screenEvents,
            triggerEvents = emptyList(),
            counters = listOf(
                Counter(name = "wardrobe_steps", startingValue = 0.0),
                Counter(name = "loop_done", startingValue = 0.0),
            ),
            androidExecutor = mockAndroidExecutor,
            bitmapSupplier = { _, _, _ -> mockArrowBitmap },
            onStopRequested = {},
            progressListener = null,
            wordlistRepository = wordlistRepository,
        )

        processor.onScenarioStart(context)

        // Frame 1: Start event sees "Наряд 1" -> saves first_item = "Наряд 1", switches events
        every { mockImageDetector.readText("CYRILLIC", any()) } returns TextReadResult("Наряд 1", 0.95, titleArea)
        processor.process(mockScreenBitmap)
        assertEquals("Наряд 1", processor.processingState.getStringVariable("first_item"))
        assertFalse(processor.processingState.isEventEnabled(ev1Id.databaseId))
        assertTrue(processor.processingState.isEventEnabled(ev2Id.databaseId))

        // Frame 2: Screen shows "Достойную причёску" (Russian case variation of "Достойная причёска")
        // Event 2 checks first_item (miss), Event 3 checks wardrobe.txt (HIT!)
        // Verify readText is called ONLY ONCE for this frame despite both Event 2 and Event 3 reading titleArea!
        every { mockImageDetector.readText("CYRILLIC", any()) } returns TextReadResult("Достойную причёску", 0.92, titleArea)
        processor.process(mockScreenBitmap)
        assertEquals(1.0, processor.processingState.getCounterValue("wardrobe_steps")!!, 0.001)
        verify(exactly = 2) { mockImageDetector.readText("CYRILLIC", any()) } // 1 from Frame 1 + 1 from Frame 2

        // Frame 3: Screen shows a brand-new outfit "Звёздный плащ"
        // Event 2 misses, Event 3 misses, Event 4 captures & appends to wardrobe.txt (still only 1 readText for Frame 3!)
        every { mockImageDetector.readText("CYRILLIC", any()) } returns TextReadResult("Звёздный плащ", 0.90, titleArea)
        processor.process(mockScreenBitmap)
        assertTrue(wordlistRepository.readEntries("wardrobe").contains("Звёздный плащ"))
        verify(exactly = 3) { mockImageDetector.readText("CYRILLIC", any()) } // 1 extra call for Frame 3

        // Frame 4: Seeing "Звёздный плащ" again -> Event 3 now recognizes it as owned!
        every { mockImageDetector.readText("CYRILLIC", any()) } returns TextReadResult("Звездный плащ", 0.91, titleArea)
        processor.process(mockScreenBitmap)
        assertEquals(2.0, processor.processingState.getCounterValue("wardrobe_steps")!!, 0.001)

        // Frame 5: Back to "Наряд 1" -> Event 2 matches first_item and marks full loop done!
        every { mockImageDetector.readText("CYRILLIC", any()) } returns TextReadResult("Наряд 1", 0.96, titleArea)
        processor.process(mockScreenBitmap)
        assertEquals(1.0, processor.processingState.getCounterValue("loop_done")!!, 0.001)

        processor.onScenarioEnd()
    }
}
