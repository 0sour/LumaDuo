// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import com.easytier.jni.EasyTierManager
import com.sour.lumaduo.LimeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Small bottom-screen-oriented wrapper around the existing EasyTier JNI manager.
 * It only exposes phase/virtual IP/peer count; the VPN consent dialog is still a system
 * dialog owned by the Activity.
 */
class DuoRemoteController(
    private val activity: Activity,
    private val repository: DuoRemoteRepository,
    private val onRequestVpnPermission: (Intent) -> Unit,
) {
    enum class Phase {
        STOPPED,
        NEED_PERMISSION,
        STARTING,
        CONNECTED,
        ERROR,
    }

    data class State(
        val phase: Phase = Phase.STOPPED,
        val virtualIp: String? = null,
        val peers: Int = 0,
        val latencyMs: Int? = null,
        val error: String? = null,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var manager: EasyTierManager? = null
    private var startedAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            val current = manager ?: return
            val json = current.latestNetworkInfoJson
            if (json != null) {
                _state.value = parseState(json)
            } else if (_state.value.phase == Phase.STARTING &&
                System.currentTimeMillis() - startedAt > START_TIMEOUT_MS
            ) {
                _state.value = State(
                    phase = Phase.ERROR,
                    error = "EasyTier 启动超时，请检查配置或 VPN 权限",
                )
            }
            if (_state.value.phase == Phase.STARTING || _state.value.phase == Phase.CONNECTED) {
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun start() {
        if (_state.value.phase == Phase.STARTING || _state.value.phase == Phase.CONNECTED) return
        if (!nativeLibrariesAvailable()) {
            _state.value = State(phase = Phase.ERROR, error = "EasyTier 原生库不可用")
            return
        }
        val config = repository.readToml()
        if (manager == null) {
            manager = EasyTierManager(activity, "Default", config)
        }
        val prepareIntent = VpnService.prepare(activity)
        if (prepareIntent != null) {
            _state.value = State(phase = Phase.NEED_PERMISSION)
            onRequestVpnPermission(prepareIntent)
            return
        }
        startManager()
    }

    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) {
            startManager()
        } else {
            _state.value = State(phase = Phase.NEED_PERMISSION, error = "需要 VPN 权限才能连接虚拟网络")
        }
    }

    fun stop() {
        mainHandler.removeCallbacks(pollRunnable)
        manager?.stop()
        manager = null
        _state.value = State(phase = Phase.STOPPED)
    }

    /** Apply an edited TOML. A running instance is stopped so the next start uses it. */
    fun reloadConfig() {
        val wasRunning = _state.value.phase == Phase.STARTING || _state.value.phase == Phase.CONNECTED
        mainHandler.removeCallbacks(pollRunnable)
        manager?.stop()
        manager = null
        _state.value = State(phase = Phase.STOPPED)
        if (wasRunning) start()
    }

    fun destroy() {
        stop()
    }

    private fun startManager() {
        val current = manager ?: EasyTierManager(activity, "Default", repository.readToml()).also {
            manager = it
        }
        _state.value = State(phase = Phase.STARTING)
        startedAt = System.currentTimeMillis()
        try {
            current.start()
        } catch (e: Throwable) {
            LimeLog.warning("EasyTier start failed: ${e.message}")
            _state.value = State(phase = Phase.ERROR, error = e.message ?: "EasyTier 启动失败")
            return
        }
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun parseState(json: String): State {
        return try {
            val root = JSONObject(json)
            val instances = root.optJSONObject("map")
            val instance = instances?.keys()?.asSequence()
                ?.mapNotNull { key -> instances.optJSONObject(key) }
                ?.firstOrNull { it.has("my_node_info") }
                ?: return State(phase = Phase.CONNECTED)
            val node = instance.optJSONObject("my_node_info")
            val virtualIp = node?.optJSONObject("virtual_ipv4")?.let { ipv4 ->
                val addr = ipv4.optJSONObject("address")?.optInt("addr") ?: 0
                val length = ipv4.optInt("network_length", 24)
                "${ipFromInt(addr)}/$length"
            }
            val peers = instance.optJSONArray("peers")
            val latency = peers?.optJSONObject(0)?.optLong("latency_us", 0L)
                ?.takeIf { it > 0 }?.div(1000)?.toInt()
            State(
                phase = Phase.CONNECTED,
                virtualIp = virtualIp,
                peers = peers?.length() ?: 0,
                latencyMs = latency,
            )
        } catch (e: Exception) {
            State(phase = Phase.CONNECTED, error = "网络信息解析失败")
        }
    }

    private fun ipFromInt(value: Int): String {
        return "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}." +
            "${(value ushr 8) and 0xFF}.${value and 0xFF}"
    }

    private fun nativeLibrariesAvailable(): Boolean {
        return try {
            System.loadLibrary("easytier_android_jni")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val START_TIMEOUT_MS = 8_000L
    }
}
