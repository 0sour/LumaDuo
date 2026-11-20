// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.os.Handler
import android.os.Looper
import com.sour.lumaduo.Game
import com.sour.lumaduo.binding.input.evdev.EvdevListener
import com.sour.lumaduo.ui.stream.DuoStreamActions
import com.sour.lumaduo.ui.stream.DuoStreamMode
import kotlin.math.roundToInt

/**
 * Real implementation of the console actions, bound to one live [Game] session.
 *
 * Every method runs on the UI thread (the console invokes actions from Compose). The key
 * sender talks directly to [com.sour.lumaduo.nvstream.NvConnection], so local console
 * input never depends on Game's input-grab state or on a synthetic Android KeyEvent.
 */
class GameDuoStreamActions(
    private val game: Game,
    private val bridge: DuoStreamBridge,
    private val keySender: DuoStreamKeySender,
) : DuoStreamActions {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun setMode(mode: DuoStreamMode) {
        game.setDuoConsoleMode(mode)
    }

    override fun exitStream() {
        game.disconnect()
    }

    override fun reconnect() {
        game.duoReconnect()
    }

    override fun disconnect() {
        game.duoDisconnect()
    }

    override fun openRemote() {
        bridge.postNotice("EasyTier 配置请退出串流后在远程页编辑")
    }

    override fun networkTest() {
        bridge.postNotice("网络测试将在连接页接入")
    }

    override fun localKeyboard() {
        bridge.postNotice("请使用工具页的 ASCII 键盘")
    }

    override fun hostKeyboard() {
        // Windows on-screen keyboard. Using Game.toggleKeyboard() here would move focus
        // back to the video window and break local console navigation.
        keySender.sendChord(listOf("Win", "Ctrl", "O"))
    }

    override fun sleepHost() {
        // Win+X opens the power menu; U then S selects sleep. Give Windows time to draw
        // each menu before the next key arrives.
        keySender.sendChord(listOf("Win", "X"))
        mainHandler.postDelayed({ keySender.sendKey("U") }, 650L)
        mainHandler.postDelayed({ keySender.sendKey("S") }, 1_050L)
    }

    override fun unlockHost() {
        game.startHostUnlock()
    }

    override fun mouseMove(dx: Float, dy: Float) {
        val x = dx.roundToInt()
        val y = dy.roundToInt()
        if (x == 0 && y == 0) return
        game.mouseMove(x, y)
    }

    override fun mouseButton(button: Int) {
        val evdevButton = when (button) {
            1 -> EvdevListener.BUTTON_LEFT
            2 -> EvdevListener.BUTTON_MIDDLE
            3 -> EvdevListener.BUTTON_RIGHT
            else -> return
        }
        game.mouseButtonEvent(evdevButton, true)
        game.mouseButtonEvent(evdevButton, false)
    }

    override fun sendKey(key: String, modifiers: Set<String>) {
        keySender.sendKey(key, modifiers)
    }

    override fun sendShortcut(label: String) {
        keySender.sendShortcut(label)
    }

    override fun clearNotice() {
        bridge.clearNotice()
    }
}
