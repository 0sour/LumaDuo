// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import com.sour.lumaduo.Game
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.duo.SecondScreenPresentation
import com.sour.lumaduo.duo.findPresentationDisplay
import com.sour.lumaduo.ui.stream.DuoStreamConsoleRoute
import com.sour.lumaduo.ui.stream.DuoStreamMode

/**
 * Owns the bottom-screen Presentation for a live stream.
 *
 * Unlike the shell's Presentation, this one lives inside [Game]. That is the only way to
 * keep the console visible while the top display shows the video: launching Game stops
 * DuoMainActivity and any Presentation owned by it.
 */
class GameDuoConsoleHost(
    private val game: Game,
    private val bridge: DuoStreamBridge,
    private val actions: GameDuoStreamActions,
    private val settingsRepository: DuoSettingsRepository,
    private val onShown: () -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayManager =
        game.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    private var presentation: SecondScreenPresentation? = null
    private var displayId = Display.INVALID_DISPLAY
    private var started = false
    private var retryPosted = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = scheduleRefresh()
        override fun onDisplayRemoved(displayId: Int) = scheduleRefresh()
        override fun onDisplayChanged(displayId: Int) = scheduleRefresh()
    }

    fun start() {
        if (started) return
        started = true
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        show()
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager?.unregisterDisplayListener(displayListener)
        mainHandler.removeCallbacksAndMessages(null)
        retryPosted = false
        hide()
    }

    fun dismiss() {
        mainHandler.removeCallbacksAndMessages(null)
        presentation?.dismiss()
        presentation = null
        displayId = Display.INVALID_DISPLAY
    }

    fun onPictureInPictureChanged(inPip: Boolean) {
        if (inPip) hide() else show()
    }

    fun handleBack(): Boolean {
        if (!isShowing) return false
        if (bridge.mode.value == DuoStreamMode.LOCAL) {
            actions.setMode(DuoStreamMode.PASSTHROUGH)
        } else {
            actions.setMode(DuoStreamMode.LOCAL)
        }
        return true
    }

    fun enterLocal() {
        if (isShowing) actions.setMode(DuoStreamMode.LOCAL)
    }

    fun applyMode(mode: DuoStreamMode) {
        presentation?.setFocusEnabled(mode == DuoStreamMode.LOCAL)
        presentation?.setBackInterceptionEnabled(mode == DuoStreamMode.LOCAL)
    }

    val isShowing: Boolean get() = presentation?.isShowing == true

    private fun show() {
        if (!started || game.isFinishing || game.isDestroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && game.isInPictureInPictureMode) return

        val display = findPresentationDisplay(game, ownDisplayId()) ?: run {
            if (presentation != null) {
                presentation?.dismiss()
                presentation = null
                displayId = Display.INVALID_DISPLAY
            }
            LimeLog.info("LumaDuo console: no presentation display available")
            return
        }

        val current = presentation
        if (current != null && current.isShowing && displayId == display.displayId) return

        current?.dismiss()
        presentation = null
        displayId = display.displayId

        try {
            val created = SecondScreenPresentation(
                activity = game,
                display = display,
                onBack = {
                    if (bridge.mode.value == DuoStreamMode.LOCAL) {
                        actions.setMode(DuoStreamMode.PASSTHROUGH)
                    } else {
                        actions.setMode(DuoStreamMode.LOCAL)
                    }
                },
            ) {
                DuoStreamConsoleRoute(
                    bridge = bridge,
                    actions = actions,
                    settingsRepository = settingsRepository,
                )
            }
            created.setFocusEnabled(bridge.mode.value == DuoStreamMode.LOCAL)
            created.setBackInterceptionEnabled(bridge.mode.value == DuoStreamMode.LOCAL)
            created.show()
            presentation = created
            onShown()
            LimeLog.info("LumaDuo console shown on display ${display.displayId}")
        } catch (e: WindowManager.InvalidDisplayException) {
            LimeLog.warning("LumaDuo console display became invalid: ${e.message}")
            scheduleRefresh()
        } catch (e: WindowManager.BadTokenException) {
            LimeLog.warning("LumaDuo console token rejected: ${e.message}")
            scheduleRefresh()
        } catch (e: RuntimeException) {
            LimeLog.warning("LumaDuo console failed to show: ${e.message}")
            scheduleRefresh()
        }
    }

    private fun hide() {
        presentation?.hide()
    }

    private fun scheduleRefresh() {
        if (!started || retryPosted) return
        retryPosted = true
        mainHandler.postDelayed({
            retryPosted = false
            show()
        }, RETRY_DELAY_MS)
    }

    private fun ownDisplayId(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return game.display?.displayId ?: Display.DEFAULT_DISPLAY
        }
        @Suppress("DEPRECATION")
        return game.windowManager.defaultDisplay.displayId
    }

    companion object {
        private const val RETRY_DELAY_MS = 400L
    }
}
