// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo.stream

import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToLong

/**
 * Client-side reachability probe for the stream host.
 *
 * This only measures TCP connect time to Sunshine's known ports. It never authenticates,
 * reads host data or fabricates packet loss; ICMP is avoided because Android often blocks
 * raw sockets without root.
 */
object NetworkProbe {
    private val DEFAULT_PORTS = intArrayOf(47984, 47989, 47990)

    data class Result(val label: String, val detail: String)

    fun probe(
        address: String,
        ports: IntArray = DEFAULT_PORTS,
        timeoutMs: Int = 1_200,
    ): Result {
        val host = address.trim().removeSurrounding("[", "]")
        if (host.isBlank()) {
            return Result("不可达", "主机地址为空")
        }

        val timesMs = ArrayList<Long>(ports.size)
        var lastError: String? = null
        for (port in ports) {
            val startedAt = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                timesMs += ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            } catch (e: Exception) {
                lastError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            }
        }

        if (timesMs.isEmpty()) {
            val suffix = lastError?.let { " · $it" } ?: ""
            return Result("不可达", "TCP ${ports.joinToString("/")} 连接失败$suffix")
        }

        val min = timesMs.minOrNull() ?: 0L
        val avg = timesMs.average().roundToLong()
        return Result(
            label = "${timesMs.size}/${ports.size} 端口",
            detail = "TCP 最低 $min ms · 平均 $avg ms",
        )
    }
}
