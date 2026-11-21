// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.stream

import kotlinx.coroutines.flow.StateFlow

/** Product-facing console input mode; the implementation owns the focus transition. */
enum class StreamConsoleMode {
    PASSTHROUGH,
    LOCAL,
}

/**
 * The boundary between LumaDuo's product UI and the streaming implementation.
 *
 * The product must not call Activity methods, dialogs, notifications or legacy menus
 * through this interface. Game currently implements it; a future engine can replace it
 * without touching the shell/console.
 */
interface StreamSessionController {
    val snapshot: StateFlow<StreamSnapshot>

    fun sessionSeconds(): Long

    fun setConsoleMode(mode: StreamConsoleMode)
    fun exitStream()
    fun reconnectStream()
    fun disconnectStream()
    fun unlockHost()
    fun setControllerMouseEmulation(enabled: Boolean)
    fun togglePerformanceOverlay()

    fun mouseMove(dx: Int, dy: Int)

    /** Click with 1 = left, 2 = middle, 3 = right. */
    fun mouseButton(button: Int)

    fun mouseButtonDown(button: Int)
    fun mouseButtonUp(button: Int)

    /** Vertical wheel: positive scrolls up, negative scrolls down. */
    fun mouseScroll(delta: Int)

    /** Release keyboard modifiers, controller buttons/triggers and touch pointers. */
    fun releaseAllInputs()
}
