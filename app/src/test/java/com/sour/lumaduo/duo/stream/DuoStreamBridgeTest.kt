// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import com.sour.lumaduo.ui.stream.DuoStreamPhase
import com.sour.lumaduo.ui.stream.DuoStreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuoStreamBridgeTest {

    @Test
    fun unpacksRttFromHighBits() {
        val packed = (18L shl 32) or 3L
        assertEquals(18.0, DuoStreamBridge.rttFrom(packed) ?: 0.0, 0.001)
        assertNull(DuoStreamBridge.rttFrom(3L))
    }

    @Test
    fun parsesBandwidthFromPerformanceString() {
        assertEquals(86.5, DuoStreamBridge.parseBandwidthMbps("86.5 Mbps") ?: 0.0, 0.001)
        assertEquals(12.0, DuoStreamBridge.parseBandwidthMbps("12 Mbps") ?: 0.0, 0.001)
        assertNull(DuoStreamBridge.parseBandwidthMbps("—"))
        assertNull(DuoStreamBridge.parseBandwidthMbps(null))
        assertNull(DuoStreamBridge.parseBandwidthMbps("0 Mbps"))
    }

    @Test
    fun exposesHonestStatusLabels() {
        assertEquals("连接中", DuoStreamState(phase = DuoStreamPhase.CONNECTING).statusLabel)
        assertEquals("已连接", DuoStreamState(phase = DuoStreamPhase.CONNECTED).statusLabel)
        assertEquals("连接异常", DuoStreamState(phase = DuoStreamPhase.ERROR).statusLabel)
        assertEquals("—", DuoStreamState().fpsLabel)
        assertEquals("—", DuoStreamState().latencyLabel)
        assertEquals("—", DuoStreamState().bitrateLabel)
    }
}
