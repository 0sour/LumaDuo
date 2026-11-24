// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.luma

import android.content.Context
import android.provider.Settings
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
 * The single source of truth for local gamepad semantics. The physical layout is
 * fixed: bottom = confirm, right = back. RP Duo prints different letters per style:
 * Xbox style prints B on the bottom and A on the right; Retro style prints A on the
 * bottom and B on the right. Key constants stay canonical A/B; only the labels follow
 * the system style.
 */
object DuoKeys {
    data class Labels(val confirm: String, val back: String)

    val Confirm = Key.ButtonA
    val Back = Key.ButtonB
    const val ConfirmLabel = "B"
    const val BackLabel = "A"

    /**
     * Reads the Retroid controller style from the system settings that RPSettings
     * writes. Values are readable without a special permission; any failure keeps
     * the Xbox-style B/A labels.
     */
    fun labels(context: Context): Labels {
        return try {
            val resolver = context.contentResolver
            val flip = Settings.System.getInt(resolver, "flip_button_layout", 1)
            val temp = Settings.System.getInt(resolver, "temp_abxy_layout_mode", 0)
            val noGamepad = Settings.System.getInt(
                resolver,
                "no_create_gamepad_button_layout",
                0,
            )
            if (noGamepad != 0) {
                Labels(ConfirmLabel, BackLabel)
            } else if (flip == 0 || temp == 1) {
                Labels("A", "B")
            } else {
                Labels(ConfirmLabel, BackLabel)
            }
        } catch (e: RuntimeException) {
            Labels(ConfirmLabel, BackLabel)
        }
    }
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
