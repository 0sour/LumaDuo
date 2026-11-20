// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.stream

enum class DuoStreamMode {
    PASSTHROUGH,
    LOCAL,
}

enum class DuoStreamPhase {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR,
}

enum class DuoStreamTab(val label: String) {
    STATUS("状态"),
    VIDEO("画面"),
    AUDIO("音频"),
    INPUT("输入"),
    CONNECTION("连接"),
}

enum class DuoToolTab(val label: String) {
    TOUCHPAD("触摸板"),
    KEYBOARD("键盘"),
    SHORTCUTS("快捷键"),
}

/**
 * Session snapshot consumed by the console. Fields are nullable on purpose: the UI shows
 * "—" instead of inventing values when the stream/decode/render layer has no sample yet.
 */
data class DuoStreamState(
    val connected: Boolean = false,
    val phase: DuoStreamPhase = DuoStreamPhase.IDLE,
    val hostName: String = "未连接",
    val appName: String = "—",
    val address: String = "—",
    val resolution: String = "—",
    val configuredFps: Int? = null,
    val configuredBitrateMbps: Int? = null,
    val codec: String = "—",
    val hdr: Boolean = false,
    val actualFps: Double? = null,
    val networkLatencyMs: Double? = null,
    val rttMs: Double? = null,
    val packetLossPercent: Double? = null,
    val actualBitrateMbps: Double? = null,
    val decodeLatencyMs: Double? = null,
    val renderLatencyMs: Double? = null,
    val hostLatencyMs: Double? = null,
    val networkLabel: String = "—",
    val batteryPercent: Int? = null,
    val temperatureC: Int? = null,
    val thermalLabel: String = "—",
    val easyTierLabel: String = "未启动",
    val usbDeviceCount: Int = 0,
    val sessionSeconds: Long = 0L,
    val notice: String? = null,
    val latencyTrend: List<Float> = emptyList(),
    val bitrateTrend: List<Float> = emptyList(),
    val hostCpu: String? = null,
    val hostGpu: String? = null,
    val hostRam: String? = null,
) {
    val statusLabel: String
        get() = when (phase) {
            DuoStreamPhase.IDLE -> "未连接"
            DuoStreamPhase.CONNECTING -> "连接中"
            DuoStreamPhase.CONNECTED -> "已连接"
            DuoStreamPhase.ERROR -> "连接异常"
        }
    val fpsLabel: String get() = actualFps?.let { "%.0f".format(it) } ?: "—"
    val latencyLabel: String get() = networkLatencyMs?.let { "%.0f ms".format(it) } ?: "—"
    val bitrateLabel: String get() = actualBitrateMbps?.let { "%.1f Mbps".format(it) } ?: "—"
    val configuredLabel: String
        get() = buildString {
            append(resolution)
            configuredFps?.let { append(" · ${it} Hz") }
        }
    val durationLabel: String
        get() {
            val total = sessionSeconds.coerceAtLeast(0)
            return "%02d:%02d".format(total / 60, total % 60)
        }
}
