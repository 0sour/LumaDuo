// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.view.KeyEvent
import com.sour.lumaduo.binding.input.KeyboardTranslator
import com.sour.lumaduo.nvstream.NvConnection
import com.sour.lumaduo.nvstream.input.KeyboardPacket

/**
 * Sends the console's symbolic key names to the host.
 *
 * The toolkit uses logical names ("Escape", "BracketLeft", "Ctrl") instead of Android
 * key codes. This class keeps that mapping in one place, applies modifier masks, and
 * guarantees that every chord is released in reverse order.
 */
class DuoStreamKeySender(
    private val sink: (keyMap: Short, down: Boolean, modifier: Byte) -> Unit,
    private val translator: KeyboardTranslator = KeyboardTranslator(),
) {
    constructor(connectionProvider: () -> NvConnection?) : this({ keyMap, down, modifier ->
        connectionProvider()?.sendKeyboardInput(
            keyMap,
            if (down) KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP,
            modifier,
            0,
        )
    })

    fun sendKey(key: String, modifiers: Set<String> = emptySet()) {
        val keyCode = keyCodeFor(key) ?: return
        val keyMap = translator.translate(keyCode, -1)
        if (keyMap.toInt() == 0) return
        val mask = modifierMask(modifiers)
        sink(keyMap, true, mask)
        sink(keyMap, false, mask)
    }

    fun sendShortcut(label: String) {
        val chord = shortcutChord(label) ?: return
        sendChord(chord)
    }

    fun sendChord(keys: List<String>) {
        val entries = keys.mapNotNull { name ->
            val code = keyCodeFor(name) ?: return@mapNotNull null
            val keyMap = translator.translate(code, -1)
            if (keyMap.toInt() == 0) null else name to keyMap
        }
        if (entries.isEmpty()) return

        val held = mutableSetOf<String>()
        for ((name, keyMap) in entries) {
            modifierName(name)?.let { held += it }
            sink(keyMap, true, modifierMask(held))
        }
        for ((name, keyMap) in entries.asReversed()) {
            sink(keyMap, false, modifierMask(held))
            modifierName(name)?.let { held -= it }
        }
    }

    companion object {
        /** Android key code for a console key name, or null when the name is unknown. */
        internal fun keyCodeFor(name: String): Int? {
            if (name.length == 1) {
                val ch = name[0]
                return when {
                    ch in 'A'..'Z' -> KeyEvent.KEYCODE_A + (ch - 'A')
                    ch in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch - 'a')
                    ch in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
                    else -> null
                }
            }
            return when (name) {
                "Escape" -> KeyEvent.KEYCODE_ESCAPE
                "Backspace" -> KeyEvent.KEYCODE_DEL
                "Tab" -> KeyEvent.KEYCODE_TAB
                "CapsLock" -> KeyEvent.KEYCODE_CAPS_LOCK
                "Enter" -> KeyEvent.KEYCODE_ENTER
                "Space" -> KeyEvent.KEYCODE_SPACE
                "Minus" -> KeyEvent.KEYCODE_MINUS
                "Equal" -> KeyEvent.KEYCODE_EQUALS
                "BracketLeft" -> KeyEvent.KEYCODE_LEFT_BRACKET
                "BracketRight" -> KeyEvent.KEYCODE_RIGHT_BRACKET
                "Backslash" -> KeyEvent.KEYCODE_BACKSLASH
                "Semicolon" -> KeyEvent.KEYCODE_SEMICOLON
                "Quote" -> KeyEvent.KEYCODE_APOSTROPHE
                "Comma" -> KeyEvent.KEYCODE_COMMA
                "Period" -> KeyEvent.KEYCODE_PERIOD
                "Slash" -> KeyEvent.KEYCODE_SLASH
                "Backquote" -> KeyEvent.KEYCODE_GRAVE
                "ArrowLeft" -> KeyEvent.KEYCODE_DPAD_LEFT
                "ArrowRight" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "ArrowUp" -> KeyEvent.KEYCODE_DPAD_UP
                "ArrowDown" -> KeyEvent.KEYCODE_DPAD_DOWN
                "Insert" -> KeyEvent.KEYCODE_INSERT
                "Delete" -> KeyEvent.KEYCODE_FORWARD_DEL
                "Home" -> KeyEvent.KEYCODE_MOVE_HOME
                "End" -> KeyEvent.KEYCODE_MOVE_END
                "PageUp" -> KeyEvent.KEYCODE_PAGE_UP
                "PageDown" -> KeyEvent.KEYCODE_PAGE_DOWN
                "Multiply" -> KeyEvent.KEYCODE_NUMPAD_MULTIPLY
                "Divide" -> KeyEvent.KEYCODE_NUMPAD_DIVIDE
                "Shift" -> KeyEvent.KEYCODE_SHIFT_LEFT
                "Ctrl" -> KeyEvent.KEYCODE_CTRL_LEFT
                "Win" -> KeyEvent.KEYCODE_META_LEFT
                "Alt" -> KeyEvent.KEYCODE_ALT_LEFT
                else -> null
            }
        }

        internal fun modifierName(name: String): String? = when (name) {
            "Shift", "Ctrl", "Win", "Alt" -> name
            else -> null
        }

        internal fun modifierMask(modifiers: Set<String>): Byte {
            var mask = 0
            if ("Shift" in modifiers) mask = mask or KeyboardPacket.MODIFIER_SHIFT.toInt()
            if ("Ctrl" in modifiers) mask = mask or KeyboardPacket.MODIFIER_CTRL.toInt()
            if ("Alt" in modifiers) mask = mask or KeyboardPacket.MODIFIER_ALT.toInt()
            if ("Win" in modifiers) mask = mask or KeyboardPacket.MODIFIER_META.toInt()
            return mask.toByte()
        }

        /** Shortcut labels shown by the toolkit; unknown labels are ignored. */
        internal fun shortcutChord(label: String): List<String>? = when (label) {
            "Ctrl + C" -> listOf("Ctrl", "C")
            "Ctrl + V" -> listOf("Ctrl", "V")
            "Ctrl + X" -> listOf("Ctrl", "X")
            "Ctrl + Z" -> listOf("Ctrl", "Z")
            "Ctrl + A" -> listOf("Ctrl", "A")
            "Alt + Tab" -> listOf("Alt", "Tab")
            "Win + D" -> listOf("Win", "D")
            "Win + E" -> listOf("Win", "E")
            "Esc" -> listOf("Escape")
            "Tab" -> listOf("Tab")
            "Ctrl + Shift + Esc" -> listOf("Ctrl", "Shift", "Escape")
            "Win + L" -> listOf("Win", "L")
            "Win + Alt + B" -> listOf("Win", "Alt", "B")
            else -> null
        }
    }
}
