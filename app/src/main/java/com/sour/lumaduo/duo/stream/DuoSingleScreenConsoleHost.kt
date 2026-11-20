// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.Game
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaTheme
import com.sour.lumaduo.ui.luma.LumaType
import com.sour.lumaduo.ui.shell.DuoTextButton
import com.sour.lumaduo.ui.stream.DuoStreamConsoleRoute
import com.sour.lumaduo.ui.stream.DuoStreamMode

/**
 * Single-screen fallback for the stream console.
 *
 * There is no presentation display to own, so a ComposeView is layered over the video
 * window. Back opens a bottom panel at 60% height; the compact console drops the 220dp
 * summary column and uses the full width. Keeping the panel inside Game's window means
 * controller focus and `setInputGrabState` follow the same local/passthrough rules as the
 * dual-screen Presentation.
 */
class DuoSingleScreenConsoleHost(
    private val game: Game,
    private val bridge: DuoStreamBridge,
    private val actions: GameDuoStreamActions,
    private val settingsRepository: DuoSettingsRepository,
) {
    private var visible by mutableStateOf(false)
    private var lastConfirmAt = 0L
    private var lastHatKeyCode = 0

    private val composeView = ComposeView(game).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        visibility = View.GONE
        setContent {
            LumaTheme {
                SingleScreenOverlay(
                    visible = visible,
                    onClose = { game.setDuoConsoleMode(DuoStreamMode.PASSTHROUGH) },
                    bridge = bridge,
                    actions = actions,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }

    init {
        val root = game.streamView.parent as? FrameLayout
        root?.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    val isOverlayVisible: Boolean get() = visible

    fun open() {
        game.setDuoConsoleMode(DuoStreamMode.LOCAL)
    }

    fun handleBack(): Boolean {
        if (visible) {
            game.setDuoConsoleMode(DuoStreamMode.PASSTHROUGH)
        } else {
            game.setDuoConsoleMode(DuoStreamMode.LOCAL)
        }
        return true
    }

    fun setOverlayVisible(enabled: Boolean) {
        visible = enabled
        composeView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            composeView.isFocusableInTouchMode = true
            composeView.post { runCatching { composeView.requestFocus() } }
        } else {
            composeView.clearFocus()
        }
    }

    fun onPictureInPictureChanged(inPip: Boolean) {
        if (inPip && visible) {
            game.setDuoConsoleMode(DuoStreamMode.PASSTHROUGH)
        }
    }

    /** Hide and restore passthrough input before the Activity stops. */
    fun stop() {
        if (visible) {
            game.setDuoConsoleMode(DuoStreamMode.PASSTHROUGH)
        }
    }

    fun destroy() {
        (composeView.parent as? ViewGroup)?.removeView(composeView)
    }

    /**
     * Activity-level key bridge for single-screen mode. While the overlay is visible it
     * owns every key: A/B/Back route through the Compose back dispatcher, the rest go to
     * the ComposeView so controller focus cannot leak to the host PC.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!visible) return false
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
        ) {
            if (event.repeatCount > 0) return true
            val now = SystemClock.uptimeMillis()
            val duplicate = now - lastConfirmAt < 40L
            lastConfirmAt = now
            if (duplicate) return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_BACK ||
                event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_A)
        ) {
            game.onBackPressedDispatcher.onBackPressed()
            return true
        }
        // Local console owns the controller/keyboard while open. Dispatching directly to
        // the ComposeView also covers the case where window focus has not settled yet.
        composeView.dispatchKeyEvent(event)
        return true
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!visible) return false
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val keyCode = when {
                hatX > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                hatX < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                hatY > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                hatY < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                else -> 0
            }
            if (keyCode == 0) {
                lastHatKeyCode = 0
            } else if (keyCode != lastHatKeyCode) {
                lastHatKeyCode = keyCode
                composeView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                composeView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            return true
        }
        composeView.dispatchGenericMotionEvent(event)
        return true
    }
}

@Composable
private fun SingleScreenOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    bridge: DuoStreamBridge,
    actions: GameDuoStreamActions,
    settingsRepository: DuoSettingsRepository,
) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .background(LumaColors.Bg)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { })
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(LumaColors.Surface1)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    "单屏控制台",
                    style = LumaType.micro,
                    color = LumaColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                DuoTextButton(
                    "收起",
                    onClick = onClose,
                    modifier = Modifier.height(24.dp),
                )
            }
            DuoStreamConsoleRoute(
                bridge = bridge,
                actions = actions,
                settingsRepository = settingsRepository,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }
}
