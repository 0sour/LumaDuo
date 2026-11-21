package com.sour.lumaduo.duo.stream

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkProbeTest {
    @Test
    fun reachablePortReportsTcpLatency() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val result = NetworkProbe.probe(
                address = "127.0.0.1",
                ports = intArrayOf(server.localPort),
                timeoutMs = 1_000,
            )
            assertEquals("1/1 端口", result.label)
            assertTrue(result.detail.startsWith("TCP 最低"))
        }
    }

    @Test
    fun closedPortReportsUnreachable() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val result = NetworkProbe.probe(
            address = "127.0.0.1",
            ports = intArrayOf(port),
            timeoutMs = 200,
        )
        assertEquals("不可达", result.label)
        assertTrue(result.detail.contains("连接失败"))
    }
}
