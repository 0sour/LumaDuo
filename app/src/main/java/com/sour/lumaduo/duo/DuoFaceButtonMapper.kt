// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.view.KeyEvent

/**
 * Normalizes the RP Duo built-in controller's Retro-style face buttons to the
 * canonical shell mapping: bottom/BUTTON_A = confirm, right/BUTTON_B = back,
 * left/BUTTON_X = action, top/BUTTON_Y = unbound.
 *
 * Xbox-style built-in and external controllers already report that mapping, so
 * they are left untouched. The Retro controller identifies itself as
 * vendor 0x2022 / product 0x3001.
 */
object DuoFaceButtonMapper {
    private const val RETRO_VENDOR = 0x2022
    private const val RETRO_PRODUCT = 0x3001

    fun normalize(event: KeyEvent): KeyEvent {
        val device = event.device ?: return event
        if (device.vendorId != RETRO_VENDOR || device.productId != RETRO_PRODUCT) {
            return event
        }
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> return event
        }
        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            mapped,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source,
        )
    }
}
