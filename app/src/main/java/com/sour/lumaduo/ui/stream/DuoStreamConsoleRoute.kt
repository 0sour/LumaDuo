// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.sour.lumaduo.duo.DuoRemoteController
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.duo.stream.DuoStreamBridge
import com.sour.lumaduo.ui.luma.DuoFocusMemory
import com.sour.lumaduo.ui.luma.LocalDuoFocusMemory

/**
 * Compose route hosted by [com.sour.lumaduo.duo.SecondScreenPresentation].
 *
 * Passthrough mode deliberately places a transparent tap catcher above the console:
 * the window is not focusable, so a single touch is the documented way into local mode,
 * and that touch must not activate the dimmed controls underneath.
 */
@Composable
fun DuoStreamConsoleRoute(
    bridge: DuoStreamBridge,
    actions: DuoStreamActions,
    settingsRepository: DuoSettingsRepository,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val state by bridge.state.collectAsState()
    val mode by bridge.mode.collectAsState()
    val focusMemory = remember { DuoFocusMemory() }
    val remoteRuntime = remember { DuoRemoteController.State() }
    val scene = remember { settingsRepository.read() }
    val local = mode == DuoStreamMode.LOCAL

    BackHandler(enabled = local) {
        actions.setMode(DuoStreamMode.PASSTHROUGH)
    }

    CompositionLocalProvider(LocalDuoFocusMemory provides focusMemory) {
        Box(modifier.fillMaxSize()) {
            DuoStreamConsole(
                state = state,
                mode = mode,
                actions = actions,
                settingsRepository = settingsRepository,
                scene = scene,
                remoteRuntime = remoteRuntime,
                modifier = Modifier.fillMaxSize(),
                compact = compact,
            )
            if (!local) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    actions.setMode(DuoStreamMode.LOCAL)
                                },
                            )
                        },
                )
            }
        }
    }
}
