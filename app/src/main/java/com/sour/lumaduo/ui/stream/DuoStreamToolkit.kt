// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.R
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType
import com.sour.lumaduo.ui.shell.DuoTextButton

private data class KeyDef(
    val label: String,
    val key: String,
    val weight: Float = 1f,
    val kind: KeyKind = KeyKind.NORMAL,
    val shift: Boolean = false,
)

private enum class KeyKind { NORMAL, SPECIAL, MODIFIER, SPACE }

private val keyboardRows = listOf(
    listOf(
        KeyDef("Esc", "Escape", 1.25f, KeyKind.SPECIAL),
        KeyDef("1", "1"), KeyDef("2", "2"), KeyDef("3", "3"), KeyDef("4", "4"),
        KeyDef("5", "5"), KeyDef("6", "6"), KeyDef("7", "7"), KeyDef("8", "8"),
        KeyDef("9", "9"), KeyDef("0", "0"), KeyDef("-", "Minus"), KeyDef("=", "Equal"),
        KeyDef("⌫", "Backspace", 1.25f, KeyKind.SPECIAL),
    ),
    listOf(
        KeyDef("Tab", "Tab", 1.25f, KeyKind.SPECIAL),
        KeyDef("Q", "Q"), KeyDef("W", "W"), KeyDef("E", "E"), KeyDef("R", "R"),
        KeyDef("T", "T"), KeyDef("Y", "Y"), KeyDef("U", "U"), KeyDef("I", "I"),
        KeyDef("O", "O"), KeyDef("P", "P"), KeyDef("[", "BracketLeft"),
        KeyDef("]", "BracketRight"), KeyDef("\\", "Backslash", 1.15f),
    ),
    listOf(
        KeyDef("Caps", "CapsLock", 1.25f, KeyKind.SPECIAL),
        KeyDef("A", "A"), KeyDef("S", "S"), KeyDef("D", "D"), KeyDef("F", "F"),
        KeyDef("G", "G"), KeyDef("H", "H"), KeyDef("J", "J"), KeyDef("K", "K"),
        KeyDef("L", "L"), KeyDef(";", "Semicolon"), KeyDef("'", "Quote"),
        KeyDef("Enter", "Enter", 1.7f, KeyKind.SPECIAL),
    ),
    listOf(
        KeyDef("Shift", "Shift", 1.7f, KeyKind.MODIFIER),
        KeyDef("Z", "Z"), KeyDef("X", "X"), KeyDef("C", "C"), KeyDef("V", "V"),
        KeyDef("B", "B"), KeyDef("N", "N"), KeyDef("M", "M"), KeyDef(",", "Comma"),
        KeyDef(".", "Period"), KeyDef("/", "Slash"), KeyDef("Shift", "Shift", 1.7f, KeyKind.MODIFIER),
    ),
    listOf(
        KeyDef("Ctrl", "Ctrl", 1.2f, KeyKind.MODIFIER),
        KeyDef("Win", "Win", 1f, KeyKind.MODIFIER),
        KeyDef("Alt", "Alt", 1.2f, KeyKind.MODIFIER),
        KeyDef("?123", "symbols", 1.2f, KeyKind.MODIFIER),
        KeyDef("Space", "Space", 4.2f, KeyKind.SPACE),
        KeyDef("←", "ArrowLeft"), KeyDef("↑", "ArrowUp"),
        KeyDef("↓", "ArrowDown"), KeyDef("→", "ArrowRight"),
    ),
)

private val symbolRows = listOf(
    listOf(
        KeyDef("Esc", "Escape", 1.25f, KeyKind.SPECIAL),
        KeyDef("!", "1", shift = true), KeyDef("@", "2", shift = true),
        KeyDef("#", "3", shift = true), KeyDef("$", "4", shift = true),
        KeyDef("%", "5", shift = true), KeyDef("^", "6", shift = true),
        KeyDef("&", "7", shift = true), KeyDef("*", "8", shift = true),
        KeyDef("(", "9", shift = true), KeyDef(")", "0", shift = true),
        KeyDef("⌫", "Backspace", 1.25f, KeyKind.SPECIAL),
    ),
    listOf(
        KeyDef("Tab", "Tab", 1.25f, KeyKind.SPECIAL),
        KeyDef("_", "Minus", shift = true), KeyDef("+", "Equal", shift = true),
        KeyDef("[", "BracketLeft"), KeyDef("]", "BracketRight"),
        KeyDef("{", "BracketLeft", shift = true), KeyDef("}", "BracketRight", shift = true),
        KeyDef("|", "Backslash", shift = true), KeyDef("\\", "Backslash", 1.15f),
    ),
    listOf(
        KeyDef("Caps", "CapsLock", 1.25f, KeyKind.SPECIAL),
        KeyDef(";", "Semicolon"), KeyDef(":", "Semicolon", shift = true),
        KeyDef("'", "Quote"), KeyDef("\"", "Quote", shift = true),
        KeyDef(",", "Comma"), KeyDef(".", "Period"),
        KeyDef("/", "Slash"), KeyDef("Enter", "Enter", 1.7f, KeyKind.SPECIAL),
    ),
    listOf(
        KeyDef("Shift", "Shift", 1.7f, KeyKind.MODIFIER),
        KeyDef("~", "Backquote", shift = true), KeyDef("<", "Comma", shift = true),
        KeyDef(">", "Period", shift = true), KeyDef("?", "Slash", shift = true),
        KeyDef("×", "Multiply"), KeyDef("÷", "Divide"),
        KeyDef("Shift", "Shift", 1.7f, KeyKind.MODIFIER),
    ),
    listOf(
        KeyDef("Ctrl", "Ctrl", 1.2f, KeyKind.MODIFIER),
        KeyDef("Win", "Win", 1f, KeyKind.MODIFIER),
        KeyDef("Alt", "Alt", 1.2f, KeyKind.MODIFIER),
        KeyDef("ABC", "symbols", 1.2f, KeyKind.MODIFIER),
        KeyDef("Space", "Space", 4.2f, KeyKind.SPACE),
        KeyDef("←", "ArrowLeft"), KeyDef("↑", "ArrowUp"),
        KeyDef("↓", "ArrowDown"), KeyDef("→", "ArrowRight"),
    ),
)

private val shortcuts = listOf(
    "Ctrl + C" to "复制",
    "Ctrl + V" to "粘贴",
    "Ctrl + X" to "剪切",
    "Ctrl + Z" to "撤销",
    "Ctrl + A" to "全选",
    "Alt + Tab" to "切换窗口",
    "Win + D" to "显示桌面",
    "Win + E" to "文件管理器",
    "Esc" to "退出 / 取消",
    "Tab" to "切换焦点",
    "Ctrl + Shift + Esc" to "任务管理器",
    "Win + L" to "锁定电脑",
)

@Composable
fun DuoStreamToolkit(
    actions: DuoStreamActions,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(DuoToolTab.TOUCHPAD) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoToolTab.entries.forEach { entry ->
                LumaFocusable(
                    onClick = { tab = entry },
                    modifier = Modifier
                        .height(30.dp)
                        .padding(end = 6.dp),
                    cornerRadius = 6.dp,
                ) { focused ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(LumaShapes.control)
                            .background(
                                when {
                                    tab == entry -> LumaColors.AccentSoft
                                    focused -> LumaColors.Surface3
                                    else -> Color.Transparent
                                },
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            entry.label,
                            style = LumaType.bodyM,
                            color = if (tab == entry) LumaColors.Accent else LumaColors.Secondary,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("A 返回控制台 · Q/E 切工具", style = LumaType.micro, color = LumaColors.Tertiary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            when (tab) {
                DuoToolTab.TOUCHPAD -> Touchpad(actions)
                DuoToolTab.KEYBOARD -> Keyboard(actions)
                DuoToolTab.SHORTCUTS -> Shortcuts(actions)
            }
        }
    }
}

@Composable
private fun Touchpad(actions: DuoStreamActions) {
    val density = LocalDensity.current
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var dragLock by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(LumaShapes.card)
                .background(LumaColors.Bg)
                .border(1.dp, LumaColors.Outline, LumaShapes.card)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            pointer = offset
                            actions.mouseButton(1)
                        },
                        onDoubleTap = { dragLock = !dragLock },
                        onLongPress = { dragLock = true },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pointer = it },
                        onDragEnd = { pointer = null },
                        onDragCancel = { pointer = null },
                    ) { change, drag ->
                        change.consume()
                        pointer = (pointer ?: Offset.Zero) + drag
                        actions.mouseMove(drag.x, drag.y)
                    }
                },
        ) {
            Text(
                if (dragLock) "拖拽锁定" else "单指移动 · 轻点左键 · 双击/长按拖拽",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
                modifier = Modifier.align(Alignment.Center),
            )
            pointer?.let {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = with(density) { it.x.toDp() },
                            top = with(density) { it.y.toDp() },
                        )
                        .size(10.dp)
                        .clip(LumaShapes.chip)
                        .background(LumaColors.Accent.copy(alpha = 0.8f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("左键", Modifier.weight(1f)) { actions.mouseButton(1) }
            PadButton("中键", Modifier.weight(1f)) { actions.mouseButton(2) }
            PadButton("右键", Modifier.weight(1f)) { actions.mouseButton(3) }
            PadButton(if (dragLock) "拖拽开" else "拖拽", Modifier.weight(1f)) { dragLock = !dragLock }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "相对鼠标增量 · 不承诺远端绝对光标；双指滚动/右键在 M3 输入 Sink 接入。",
            style = LumaType.micro,
            color = LumaColors.Tertiary,
        )
    }
}

@Composable
private fun PadButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    DuoTextButton(label, onClick = onClick, modifier = modifier)
}

@Composable
private fun Keyboard(actions: DuoStreamActions) {
    var symbols by remember { mutableStateOf(false) }
    var mods by remember { mutableStateOf(setOf<String>()) }
    val rows = if (symbols) symbolRows else keyboardRows
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ASCII 直发 · 无本地拼音", style = LumaType.micro, color = LumaColors.Tertiary)
            Spacer(Modifier.weight(1f))
            Text(
                if (mods.isEmpty()) "无修饰键" else mods.joinToString(" + "),
                style = LumaType.micro,
                color = if (mods.isEmpty()) LumaColors.Tertiary else LumaColors.Accent,
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { key ->
                        Key(
                            key = key,
                            active = key.kind == KeyKind.MODIFIER && mods.contains(key.key),
                            modifier = Modifier.weight(key.weight),
                            onClick = {
                                when (key.key) {
                                    "symbols" -> symbols = !symbols
                                    "Shift", "Ctrl", "Win", "Alt" -> {
                                        mods = if (mods.contains(key.key)) mods - key.key else mods + key.key
                                    }
                                    else -> {
                                        val effectiveMods = if (key.shift) mods + "Shift" else mods
                                        actions.sendKey(key.key, effectiveMods)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuoTextButton("系统输入法", onClick = { actions.localKeyboard() }, modifier = Modifier.weight(1f))
            DuoTextButton("清空修饰键", onClick = { mods = emptySet() }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Key(
    key: KeyDef,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LumaFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        cornerRadius = 5.dp,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.control)
                .background(
                    when {
                        active -> LumaColors.Accent.copy(alpha = 0.18f)
                        focused -> LumaColors.Surface3
                        key.kind == KeyKind.NORMAL -> LumaColors.Surface1
                        else -> LumaColors.Surface3
                    },
                )
                .border(
                    1.dp,
                    when {
                        active -> LumaColors.Accent.copy(alpha = 0.55f)
                        focused -> LumaColors.FocusRing
                        else -> LumaColors.Outline
                    },
                    LumaShapes.control,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                key.label,
                style = LumaType.micro.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                color = if (active) LumaColors.Accent else LumaColors.Text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Shortcuts(actions: DuoStreamActions) {
    val actionStrip = listOf(
        "本地键盘" to { actions.localKeyboard() },
        "主机键盘" to { actions.hostKeyboard() },
        "Windows HDR" to { actions.sendShortcut("Win + Alt + B") },
        "休眠" to { actions.sleepHost() },
        "退出" to { actions.exitStream() },
        "解锁" to { actions.unlockHost() },
    )
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            actionStrip.forEach { (label, action) ->
                LumaFocusable(
                    onClick = action,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    cornerRadius = 14.dp,
                ) { focused ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(LumaShapes.chip)
                            .background(if (focused) LumaColors.Surface3 else LumaColors.Surface1)
                            .border(1.dp, LumaColors.Outline, LumaShapes.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = LumaType.micro, color = LumaColors.Secondary, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            shortcuts.chunked(4).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    row.forEach { (label, name) ->
                        val danger = label == "Win + L"
                        LumaFocusable(
                            onClick = { actions.sendShortcut(label) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            cornerRadius = 8.dp,
                        ) { focused ->
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(LumaShapes.control)
                                    .background(if (focused) LumaColors.Surface3 else LumaColors.Surface1)
                                    .border(
                                        1.dp,
                                        if (danger) LumaColors.Error.copy(alpha = 0.55f) else LumaColors.Outline,
                                        LumaShapes.control,
                                    )
                                    .padding(horizontal = 7.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    label,
                                    style = LumaType.micro.copy(fontFamily = FontFamily.Monospace),
                                    color = if (danger) LumaColors.Error else LumaColors.Accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    name,
                                    style = LumaType.micro,
                                    color = LumaColors.Tertiary,
                                    maxLines = 1,
                                )
                                if (danger) {
                                    Text("长按确认", style = LumaType.micro, color = LumaColors.Error)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "组合键按顺序发送并反向释放；离开工具/断流释放全部按键。",
            style = LumaType.micro,
            color = LumaColors.Tertiary,
        )
    }
}
