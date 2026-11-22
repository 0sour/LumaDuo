// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.luma

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single source of truth for local gamepad semantics. RP Duo's physical silk is
 * B = confirm, A = back (bili-duo / UUYC-Duo both verified this on device). In Xbox
 * style the firmware reports the bottom/confirm button as BUTTON_A (96) and the
 * right/back button as BUTTON_B (97), so labels stay B/A while the key constants are
 * A/B respectively.
 */
object DuoKeys {
    val Confirm = Key.ButtonA
    val Back = Key.ButtonB
    const val ConfirmLabel = "B"
    const val BackLabel = "A"
}

/** Remembers the last focused Luma control so an overlay can restore it on close. */
class DuoFocusMemory {
    var last: FocusRequester? = null
}

val LocalDuoFocusMemory = staticCompositionLocalOf<DuoFocusMemory?> { null }

/**
 * Draws a high-contrast white ring with a black separator. It deliberately does not use
 * Lumen Gold: gold means selection, the ring means "the controller is here".
 */
fun Modifier.lumaFocusRing(cornerRadius: Dp, focused: Boolean): Modifier = drawWithContent {
    drawContent()
    if (!focused) return@drawWithContent
    val inset = 2.dp.toPx()
    val ringSize = Size(
        width = (size.width - inset * 2).coerceAtLeast(0f),
        height = (size.height - inset * 2).coerceAtLeast(0f),
    )
    val radius = CornerRadius(cornerRadius.toPx())
    drawRoundRect(
        color = LumaColors.FocusHalo,
        topLeft = Offset(inset, inset),
        size = ringSize,
        cornerRadius = radius,
        style = Stroke(5.dp.toPx()),
    )
    drawRoundRect(
        color = LumaColors.FocusRing,
        topLeft = Offset(inset, inset),
        size = ringSize,
        cornerRadius = radius,
        style = Stroke(LumaMetrics.FocusStroke.toPx()),
    )
}

/**
 * Single focus/click surface for touch and controller. Buttons are reachable by DPAD and
 * activate on A without relying on the legacy V+ key handlers.
 */
@Composable
fun LumaFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onNavigateKey: ((Key) -> Boolean)? = null,
    onFocused: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val selfFocus = focusRequester ?: remember { FocusRequester() }
    val memory = LocalDuoFocusMemory.current
    Box(
        modifier = modifier
            .focusRequester(selfFocus)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) memory?.last = selfFocus
                onFocused?.invoke(it.isFocused)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown) {
                    when {
                        event.key == DuoKeys.Confirm -> {
                            onClick()
                            true
                        }
                        onNavigateKey?.invoke(event.key) == true -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            .lumaFocusRing(cornerRadius, focused && enabled),
    ) {
        content(focused && enabled)
    }
}
