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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.mainmenu

import android.content.Context
import android.os.Build

import com.buzbuz.smartautoclicker.core.common.tutorial.domain.TutorialRepository
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingRepository
import com.buzbuz.smartautoclicker.core.processing.domain.model.DetectionState
import com.buzbuz.smartautoclicker.core.smart.debugging.domain.DebuggingRepository
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class MainMenuModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockContext: Context = mockk()
    private val mockSmartProcessingRepository: SmartProcessingRepository = mockk(relaxed = true) {
        every { scenarioId } returns MutableStateFlow(null)
        every { detectionState } returns flowOf(DetectionState.RECORDING)
        every { canStartDetection } returns flowOf(true)
    }
    private val mockEditionRepository: EditionRepository = mockk(relaxed = true) {
        every { isEditionSynchronized } returns flowOf(true)
    }
    private val mockTutorialRepository: TutorialRepository = mockk(relaxed = true) {
        every { shouldShowTip(any()) } returns flowOf(false)
        every { isTutorialStarted() } returns false
    }
    private val mockDebuggingRepository: DebuggingRepository = mockk(relaxed = true) {
        every { isLiveDebugging } returns flowOf(false)
    }

    private lateinit var viewModel: MainMenuModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        viewModel = MainMenuModel(
            mockSmartProcessingRepository,
            mockEditionRepository,
            mockTutorialRepository,
            mockk(relaxed = true),
            mockDebuggingRepository,
            mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleDetection_startsDetection() {
        viewModel.toggleDetection(mockContext)

        coVerify(exactly = 1) { mockSmartProcessingRepository.startDetection(mockContext, false, false, null) }
    }
}
