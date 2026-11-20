package com.sour.lumaduo.ui

import android.view.KeyEvent
import com.sour.lumaduo.binding.input.GameInputDevice
import com.sour.lumaduo.binding.input.StartWheelAction

interface GameGestures {
    fun toggleKeyboard()
    fun showGameMenu(device: GameInputDevice?): Boolean
    fun showGameMenuFromUsb(device: GameInputDevice): Boolean
    fun dispatchUsbControllerMenuKey(event: KeyEvent): Boolean
    fun dispatchUsbControllerMenuAxes(
        controllerId: Int,
        leftStickX: Float,
        leftStickY: Float,
        rightStickX: Float,
        rightStickY: Float
    ): Boolean
    fun showUsbControllerShortcutHint()
    fun hideUsbControllerShortcutHint()
    fun showStartHoldWheel()
    fun updateStartHoldWheelSelection(action: StartWheelAction)
    fun hideStartHoldWheel()
}

interface GameMenuAxisSourceLifecycle {
    fun releaseControllerMenuAxisSource(sourceId: Int)
}
