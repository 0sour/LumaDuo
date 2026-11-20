// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.stream

enum class StreamPhase {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR,
    STOPPED,
}

/** Engine-facing snapshot. Product UI maps this to its own display model. */
data class StreamSnapshot(
    val phase: StreamPhase = StreamPhase.IDLE,
    val hostName: String = "—",
    val appName: String = "—",
    val address: String = "—",
    val resolution: String = "—",
    val configuredFps: Int? = null,
    val configuredBitrateMbps: Int? = null,
    val codec: String = "—",
    val hdr: Boolean = false,
    val actualFps: Double? = null,
    val rttMs: Double? = null,
    val hostLatencyMs: Double? = null,
    val decodeLatencyMs: Double? = null,
    val renderLatencyMs: Double? = null,
    val actualBitrateMbps: Double? = null,
    val sessionSeconds: Long = 0L,
    val notice: String? = null,
)

/** Shared metric decoding for engine snapshots and product display models. */
object StreamMetrics {
    private val BANDWIDTH_PATTERN = Regex("([0-9]+(?:\\.[0-9]+)?)")

    /** rttInfo packs average RTT in the high 32 bits and variance in the low 32 bits. */
    fun rttFrom(rttInfo: Long): Double? {
        val high = (rttInfo ushr 32) and 0xFFFF_FFFFL
        return high.takeIf { it > 0L }?.toDouble()
    }

    fun parseBandwidthMbps(text: String?): Double? {
        val value = text?.let { BANDWIDTH_PATTERN.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
        return value?.takeIf { it > 0.0 }
    }
}
