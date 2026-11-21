// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.os.Handler
import android.os.Looper
import com.sour.lumaduo.stream.StreamConsoleMode
import com.sour.lumaduo.stream.StreamSessionController
import com.sour.lumaduo.ui.stream.DuoStreamActions
import com.sour.lumaduo.ui.stream.DuoStreamMode
import kotlin.math.roundToInt

/**
 * Real implementation of the console actions, bound to one live [StreamSessionController].
 *
 * Every method runs on the UI thread (the console invokes actions from Compose). The key
 * sender talks directly to [com.sour.lumaduo.nvstream.NvConnection], so local console
 * input never depends on the engine's input-grab state or on a synthetic Android KeyEvent.
 */
class GameDuoStreamActions(
    private val controller: StreamSessionController,
    private val bridge: DuoStreamBridge,
    private val keySender: DuoStreamKeySender,
) : DuoStreamActions {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun setMode(mode: DuoStreamMode) {
        controller.setConsoleMode(
            when (mode) {
                DuoStreamMode.PASSTHROUGH -> StreamConsoleMode.PASSTHROUGH
                DuoStreamMode.LOCAL -> StreamConsoleMode.LOCAL
            }
        )
    }

    override fun exitStream() {
        controller.exitStream()
    }

    override fun reconnect() {
        controller.reconnectStream()
    }

    override fun disconnect() {
        controller.disconnectStream()
    }

    override fun openRemote() {
        bridge.postNotice("EasyTier 配置请退出串流后在远程页编辑")
    }

    override fun networkTest() {
        val state = bridge.state.value
        if (state.networkTestRunning) return
        val address = state.address
        if (address.isBlank() || address == "—") {
            bridge.postNotice("当前没有可测试的主机地址")
            return
        }
        bridge.setNetworkTestRunning()
        Thread {
            val result = NetworkProbe.probe(address)
            mainHandler.post { bridge.setNetworkTestResult(result) }
        }.apply { name = "DuoNetworkProbe" }.start()
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
        controller.unlockHost()
    }

    override fun mouseMove(dx: Float, dy: Float) {
        val x = dx.roundToInt()
        val y = dy.roundToInt()
        if (x == 0 && y == 0) return
        controller.mouseMove(x, y)
    }

    override fun mouseButton(button: Int) {
        controller.mouseButton(button)
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
