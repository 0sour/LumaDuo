// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.view.KeyEvent
import com.sour.lumaduo.nvstream.input.KeyboardPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuoStreamKeySenderTest {

    @Test
    fun mapsSymbolicNamesToAndroidKeyCodes() {
        assertEquals(KeyEvent.KEYCODE_ESCAPE, DuoStreamKeySender.keyCodeFor("Escape"))
        assertEquals(KeyEvent.KEYCODE_LEFT_BRACKET, DuoStreamKeySender.keyCodeFor("BracketLeft"))
        assertEquals(KeyEvent.KEYCODE_APOSTROPHE, DuoStreamKeySender.keyCodeFor("Quote"))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, DuoStreamKeySender.keyCodeFor("ArrowLeft"))
        assertEquals(KeyEvent.KEYCODE_A, DuoStreamKeySender.keyCodeFor("A"))
        assertEquals(KeyEvent.KEYCODE_5, DuoStreamKeySender.keyCodeFor("5"))
        assertEquals(null, DuoStreamKeySender.keyCodeFor("NotAKey"))
    }

    @Test
    fun buildsModifierMaskFromToolkitState() {
        assertEquals(
            KeyboardPacket.MODIFIER_CTRL.toInt() or KeyboardPacket.MODIFIER_SHIFT.toInt(),
            DuoStreamKeySender.modifierMask(setOf("Ctrl", "Shift")).toInt(),
        )
        assertEquals(
            KeyboardPacket.MODIFIER_META.toInt() or KeyboardPacket.MODIFIER_ALT.toInt(),
            DuoStreamKeySender.modifierMask(setOf("Win", "Alt")).toInt(),
        )
        assertEquals(0, DuoStreamKeySender.modifierMask(emptySet()).toInt())
    }

    @Test
    fun sendsModifierWithKeyDownAndUp() {
        val events = mutableListOf<Triple<Short, Boolean, Byte>>()
        val sender = DuoStreamKeySender(sink = { keyMap, down, modifier ->
            events += Triple(keyMap, down, modifier)
        })

        sender.sendKey("C", setOf("Ctrl"))

        assertEquals(2, events.size)
        assertNotEquals(0, events[0].first.toInt())
        assertTrue(events[0].second)
        assertEquals(false, events[1].second)
        assertEquals(KeyboardPacket.MODIFIER_CTRL, events[0].third)
        assertEquals(KeyboardPacket.MODIFIER_CTRL, events[1].third)
    }

    @Test
    fun sendsChordsInOrderAndReleasesInReverse() {
        val events = mutableListOf<Triple<Short, Boolean, Byte>>()
        val sender = DuoStreamKeySender(sink = { keyMap, down, modifier ->
            events += Triple(keyMap, down, modifier)
        })

        sender.sendShortcut("Ctrl + C")

        assertEquals(4, events.size)
        assertEquals(true, events[0].second) // Ctrl down
        assertEquals(true, events[1].second) // C down
        assertEquals(false, events[2].second) // C up
        assertEquals(false, events[3].second) // Ctrl up
        assertEquals(KeyboardPacket.MODIFIER_CTRL, events[1].third)
        assertEquals(KeyboardPacket.MODIFIER_CTRL, events[2].third)
    }

    @Test
    fun recognizesEveryShortcutShownByTheToolkit() {
        val labels = listOf(
            "Ctrl + C", "Ctrl + V", "Ctrl + X", "Ctrl + Z", "Ctrl + A",
            "Alt + Tab", "Win + D", "Win + E", "Esc", "Tab",
            "Ctrl + Shift + Esc", "Win + L", "Win + Alt + B",
        )
        for (label in labels) {
            val chord = DuoStreamKeySender.shortcutChord(label)
            assertTrue("missing chord for $label", chord != null && chord.isNotEmpty())
        }
    }
}
