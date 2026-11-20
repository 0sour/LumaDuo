// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.ui.luma.LocalDuoFocusMemory
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType

@Composable
fun DuoSectionHeader(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = LumaType.label,
            color = LumaColors.Text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = meta,
            style = LumaType.micro,
            color = LumaColors.Tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DuoEmpty(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = LumaType.bodyM,
            color = LumaColors.Secondary,
        )
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = LumaType.caption,
                color = LumaColors.Tertiary,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

@Composable
fun DuoInfoPanel(
    modifier: Modifier = Modifier,
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(
        modifier
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = LumaType.label,
            color = LumaColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        rows.forEach { (label, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = LumaType.caption,
                    color = LumaColors.Tertiary,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    text = value,
                    style = LumaType.caption,
                    color = LumaColors.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One row in a settings page. The whole row is a single focus target; A opens/adjusts,
 * left/right adjusts values in place, and switches toggle on A.
 */
@Composable
fun DuoSettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    isSwitch: Boolean = false,
    switchOn: Boolean = false,
    isSubpage: Boolean = false,
    enabled: Boolean = true,
    requiresReconnect: Boolean = false,
    onActivate: (() -> Unit)? = null,
    onAdjust: ((Int) -> Unit)? = null,
) {
    val clickable = enabled && onActivate != null
    LumaFocusable(
        onClick = { onActivate?.invoke() },
        enabled = clickable,
        onNavigateKey = { key ->
            if (enabled && onAdjust != null) {
                when (key) {
                    Key.DirectionLeft -> {
                        onAdjust(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        onAdjust(1)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        cornerRadius = 8.dp,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.control)
                .background(if (focused) LumaColors.Surface3 else Color.Transparent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = LumaType.bodyM,
                        color = if (enabled) LumaColors.Text else LumaColors.Disabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (requiresReconnect) {
                        Spacer(Modifier.width(6.dp))
                        DuoChip("重连")
                    }
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = LumaType.micro,
                        color = LumaColors.Tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isSwitch) {
                Switch(
                    checked = switchOn,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LumaColors.OnAccent,
                        checkedTrackColor = LumaColors.Accent,
                        uncheckedThumbColor = LumaColors.Secondary,
                        uncheckedTrackColor = LumaColors.Surface3,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.size(width = 40.dp, height = 24.dp),
                )
            } else {
                if (onAdjust != null && focused) {
                    Text("‹", style = LumaType.caption, color = LumaColors.Accent)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = value,
                    style = LumaType.bodyM.copy(fontWeight = FontWeight.Medium),
                    color = if (clickable || onAdjust != null) LumaColors.Accent else LumaColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onAdjust != null && focused) {
                    Spacer(Modifier.width(4.dp))
                    Text("›", style = LumaType.caption, color = LumaColors.Accent)
                } else if (isSubpage) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(com.sour.lumaduo.R.drawable.duo_ic_chevron_right),
                        contentDescription = null,
                        tint = LumaColors.Tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DuoSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val step = (range.endInclusive - range.start) / (steps + 1).coerceAtLeast(1)
    fun adjust(delta: Float) {
        onValueChange((value + delta).coerceIn(range.start, range.endInclusive))
        onValueChangeFinished?.invoke()
    }
    LumaFocusable(
        onClick = { adjust(step) },
        onNavigateKey = { key ->
            when (key) {
                Key.DirectionLeft -> {
                    adjust(-step)
                    true
                }
                Key.DirectionRight -> {
                    adjust(step)
                    true
                }
                else -> false
            }
        },
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 8.dp,
    ) { focused ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (focused) LumaColors.Surface3 else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = LumaType.bodyM, color = LumaColors.Text)
                    if (detail != null) {
                        Text(detail, style = LumaType.micro, color = LumaColors.Tertiary)
                    }
                }
                Text(
                    valueLabel,
                    style = LumaType.bodyM.copy(fontWeight = FontWeight.Medium),
                    color = LumaColors.Accent,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor = LumaColors.Accent,
                    activeTrackColor = LumaColors.Accent,
                    inactiveTrackColor = LumaColors.Surface3,
                ),
                modifier = Modifier
                    .height(24.dp)
                    .focusProperties { canFocus = false },
            )
        }
    }
}

@Composable
fun DuoChip(text: String, tone: Color = LumaColors.Accent) {
    Text(
        text = text,
        style = LumaType.micro.copy(fontWeight = FontWeight.Medium),
        color = tone,
        modifier = Modifier
            .clip(LumaShapes.chip)
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun DuoNumericKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    DuoTextButton(
                        text = key,
                        onClick = { onKey(if (key == "⌫") "back" else key) },
                        modifier = Modifier.weight(1f).height(38.dp),
                    )
                }
            }
        }
    }
}

/**
 * In-window modal. We intentionally do not use android.app.Dialog/Popup: the bottom
 * Presentation must keep ownership of its window on display 2.
 */
@Composable
fun DuoOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 420.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val panelFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val focusMemory = LocalDuoFocusMemory.current
    val restoreFocus = remember { focusMemory?.last }
    val view = LocalView.current

    DisposableEffect(Unit) {
        onDispose {
            val target = restoreFocus ?: return@onDispose
            view.post { runCatching { target.requestFocus() } }
        }
    }

    // Enter the overlay before the user can press A again. The panel itself is a
    // focus target so focus cannot remain on the page hidden behind the scrim.
    LaunchedEffect(Unit) {
        panelFocus.requestFocus()
        withFrameNanos { }
        focusManager.moveFocus(FocusDirection.Down)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .widthIn(max = width)
                .fillMaxWidth(0.92f)
                .heightIn(max = 372.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LumaColors.Surface2)
                .border(1.dp, LumaColors.Outline, RoundedCornerShape(16.dp))
                .focusRequester(panelFocus)
                .focusable()
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = when (event.key) {
                        // Leave Left/Right to text fields so cursor editing still works.
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        Key.Tab -> if (event.isShiftPressed) {
                            FocusDirection.Previous
                        } else {
                            FocusDirection.Next
                        }
                        else -> null
                    }
                    if (direction == null) {
                        false
                    } else {
                        // Consume even when moveFocus returns false: the overlay is a
                        // focus boundary, so the controller cannot leak to the page.
                        focusManager.moveFocus(direction)
                        true
                    }
                }
                .pointerInput(Unit) {
                    // Consume taps so the scrim underneath does not dismiss the panel.
                    detectTapGestures(onTap = { })
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun DuoConfirmPanel(
    title: String,
    body: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = true,
) {
    Column {
        Text(title, style = LumaType.titleM, color = LumaColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = LumaType.caption, color = LumaColors.Secondary)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DuoTextButton("取消", onClick = onCancel, modifier = Modifier.weight(1f))
            DuoTextButton(
                confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                accent = danger,
                danger = danger,
            )
        }
    }
}

@Composable
fun DuoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    LumaFocusable(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        cornerRadius = 10.dp,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.control)
                .background(
                    when {
                        !enabled -> LumaColors.Surface1
                        danger -> LumaColors.Error.copy(alpha = if (focused) 0.22f else 0.12f)
                        accent -> LumaColors.Accent
                        focused -> LumaColors.Surface3
                        else -> LumaColors.Surface1
                    },
                )
                .border(
                    1.dp,
                    when {
                        !enabled -> LumaColors.Outline
                        danger -> LumaColors.Error.copy(alpha = 0.6f)
                        accent -> LumaColors.Accent
                        focused -> LumaColors.FocusRing
                        else -> LumaColors.Outline
                    },
                    LumaShapes.control,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = LumaType.bodyM.copy(fontWeight = FontWeight.Medium),
                color = when {
                    !enabled -> LumaColors.Disabled
                    danger -> LumaColors.Error
                    accent -> LumaColors.OnAccent
                    else -> LumaColors.Text
                },
            )
        }
    }
}
