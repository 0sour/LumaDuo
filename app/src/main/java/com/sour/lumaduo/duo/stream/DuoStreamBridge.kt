// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.sour.lumaduo.binding.video.PerformanceInfo
import com.sour.lumaduo.preferences.PreferenceConfiguration
import com.sour.lumaduo.ui.stream.DuoStreamMode
import com.sour.lumaduo.ui.stream.DuoStreamPhase
import com.sour.lumaduo.ui.stream.DuoStreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Adapts the live [com.sour.lumaduo.Game] session to the bottom-screen console.
 *
 * The bridge is intentionally read-mostly: it samples existing callbacks and never invents
 * host CPU/GPU/RAM or packet-loss values. Missing samples stay null so the UI can show "—".
 */
class DuoStreamBridge(
    context: Context,
    private val sessionSecondsProvider: () -> Long,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(DuoStreamState())
    val state: StateFlow<DuoStreamState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(DuoStreamMode.PASSTHROUGH)
    val mode: StateFlow<DuoStreamMode> = _mode.asStateFlow()

    private val latencyTrend = ArrayDeque<Float>()
    private val bitrateTrend = ArrayDeque<Float>()

    private val ticker = object : Runnable {
        override fun run() {
            val current = _state.value
            if (current.phase != DuoStreamPhase.CONNECTED) return
            _state.update {
                it.copy(
                    sessionSeconds = sessionSecondsProvider().coerceAtLeast(0L),
                    batteryPercent = readBatteryPercent(),
                    temperatureC = readBatteryTemperature(),
                    thermalLabel = readThermalLabel(),
                    networkLabel = readNetworkLabel(),
                    easyTierLabel = if (isVpnActive()) "系统 VPN" else "未启动",
                )
            }
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    fun startSession(
        hostName: String,
        appName: String,
        address: String,
        config: PreferenceConfiguration,
        codec: String,
        hdr: Boolean,
    ) {
        latencyTrend.clear()
        bitrateTrend.clear()
        _state.value = DuoStreamState(
            connected = false,
            phase = DuoStreamPhase.CONNECTING,
            hostName = hostName,
            appName = appName,
            address = address,
            resolution = if (config.isNativeResolution) "原生" else "${config.width}×${config.height}",
            configuredFps = config.fps.takeIf { it > 0 },
            configuredBitrateMbps = (config.bitrate / 1000).takeIf { it > 0 },
            codec = codec,
            hdr = hdr,
            networkLabel = readNetworkLabel(),
            batteryPercent = readBatteryPercent(),
            temperatureC = readBatteryTemperature(),
            thermalLabel = readThermalLabel(),
            easyTierLabel = if (isVpnActive()) "系统 VPN" else "未启动",
        )
        startTicker()
    }

    fun onConnecting() {
        _state.update {
            it.copy(connected = false, phase = DuoStreamPhase.CONNECTING, notice = null)
        }
    }

    fun onConnected() {
        _state.update {
            it.copy(connected = true, phase = DuoStreamPhase.CONNECTED, notice = null)
        }
        startTicker()
    }

    fun onTerminated(message: String?) {
        _state.update {
            it.copy(
                connected = false,
                phase = DuoStreamPhase.ERROR,
                notice = message?.takeIf { text -> text.isNotBlank() },
            )
        }
        stopTicker()
    }

    fun onStopped() {
        _state.update { it.copy(connected = false, phase = DuoStreamPhase.IDLE) }
        stopTicker()
    }

    fun onPerf(info: PerformanceInfo) {
        val rtt = rttFrom(info.rttInfo)
        val bitrate = parseBandwidthMbps(info.bandWidth)
        val fps = if (info.totalFps > 0f || info.receivedFps > 0f) {
            info.renderedFps.toDouble()
        } else {
            null
        }
        if (rtt != null) appendSample(latencyTrend, rtt.toFloat())
        if (bitrate != null) appendSample(bitrateTrend, bitrate.toFloat())

        _state.update {
            it.copy(
                codec = info.decoder?.takeIf { label -> label.isNotBlank() } ?: it.codec,
                hdr = info.hdrFormat.isHdr,
                actualFps = fps,
                networkLatencyMs = rtt,
                rttMs = rtt,
                actualBitrateMbps = bitrate,
                decodeLatencyMs = info.decodeTimeMs.takeIf { value -> value > 0f }?.toDouble(),
                renderLatencyMs = info.renderingLatencyMs.takeIf { value -> value > 0f }?.toDouble(),
                hostLatencyMs = info.aveHostProcessingLatency.takeIf { value -> value > 0f }?.toDouble(),
                latencyTrend = latencyTrend.toList(),
                bitrateTrend = bitrateTrend.toList(),
            )
        }
    }

    fun setMode(mode: DuoStreamMode) {
        _mode.value = mode
    }

    fun postNotice(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun startTicker() {
        mainHandler.removeCallbacks(ticker)
        mainHandler.post(ticker)
    }

    private fun stopTicker() {
        mainHandler.removeCallbacks(ticker)
    }

    private fun appendSample(target: ArrayDeque<Float>, value: Float) {
        target.addLast(value)
        while (target.size > TREND_SAMPLES) {
            target.removeFirst()
        }
    }

    private fun readBatteryPercent(): Int? {
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return percent.takeIf { it in 0..100 }
    }

    private fun readBatteryTemperature(): Int? {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return null
        val celsius = tenths / 10
        return celsius.takeIf { it in -20..90 }
    }

    private fun readThermalLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "—"
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return "—"
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "正常"
            PowerManager.THERMAL_STATUS_LIGHT -> "轻度"
            PowerManager.THERMAL_STATUS_MODERATE -> "中度"
            PowerManager.THERMAL_STATUS_SEVERE -> "严重"
            PowerManager.THERMAL_STATUS_CRITICAL -> "临界"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "紧急"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "过热"
            else -> "—"
        }
    }

    private fun readNetworkLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "—"
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "—"
        val network = manager.activeNetwork ?: return "—"
        val caps = manager.getNetworkCapabilities(network) ?: return "—"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝"
            else -> "—"
        }
    }

    private fun isVpnActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L
        private const val TREND_SAMPLES = 60
        private val BANDWIDTH_PATTERN = Regex("([0-9]+(?:\\.[0-9]+)?)")

        /** rttInfo packs average RTT in the high 32 bits and variance in the low 32 bits. */
        internal fun rttFrom(rttInfo: Long): Double? {
            val high = (rttInfo ushr 32) and 0xFFFF_FFFFL
            return high.takeIf { it > 0L }?.toDouble()
        }

        internal fun parseBandwidthMbps(text: String?): Double? {
            val value = text?.let { BANDWIDTH_PATTERN.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            return value?.takeIf { it > 0.0 }
        }
    }
}
