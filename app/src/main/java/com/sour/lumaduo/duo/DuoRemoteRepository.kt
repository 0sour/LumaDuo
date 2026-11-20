// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.content.Context
import com.sour.lumaduo.utils.easytier.EasyTierConfigUiState
import com.sour.lumaduo.utils.easytier.EasyTierController
import com.sour.lumaduo.utils.easytier.EasyTierTomlCodec

/**
 * EasyTier configuration bridge for the bottom-screen remote page.
 *
 * Secrets stay in SharedPreferences and are only exposed as a boolean to the summary.
 * The edit sheet intentionally receives the full config because the user is editing it.
 */
class DuoRemoteRepository(context: Context) {
    private val appContext = context.applicationContext

    data class Snapshot(
        val networkName: String,
        val ipv4: String,
        val hasSecret: Boolean,
        val peers: List<String>,
        val listeners: List<String>,
        val usesDefaultTemplate: Boolean,
    )

    data class Config(
        val networkName: String,
        val secret: String,
        val ipv4: String,
        val peers: String,
        val listeners: String,
        val useSmoltcp: Boolean,
        val latencyFirst: Boolean,
        val disableP2p: Boolean,
        val privateMode: Boolean,
        val disableIpv6: Boolean,
        val enableKcpProxy: Boolean,
        val disableKcpInput: Boolean,
        val enableQuicProxy: Boolean,
        val disableQuicInput: Boolean,
        val proxyForwardBySystem: Boolean,
        val disableEncryption: Boolean,
        val disableUdpHolePunching: Boolean,
        val disableSymHolePunching: Boolean,
    )

    fun read(): Snapshot {
        val config = readConfig()
        return Snapshot(
            networkName = config.networkName.ifBlank { "easytier" },
            ipv4 = config.ipv4.ifBlank { "10.0.0.1" },
            hasSecret = config.secret.isNotEmpty(),
            peers = lines(config.peers),
            listeners = lines(config.listeners.replace(',', '\n')),
            usesDefaultTemplate = config.secret.isEmpty() &&
                config.networkName == "easytier" &&
                lines(config.peers) == listOf("tcp://public.easytier.top:11010"),
        )
    }

    fun readConfig(): Config {
        val state = EasyTierTomlCodec.parseConfig(readToml())
        return Config(
            networkName = state.networkName,
            secret = state.networkSecret,
            ipv4 = state.ipv4,
            peers = state.peers,
            listeners = state.listeners,
            useSmoltcp = state.useSmoltcp,
            latencyFirst = state.latencyFirst,
            disableP2p = state.disableP2p,
            privateMode = state.privateMode,
            disableIpv6 = state.disableIpv6,
            enableKcpProxy = state.enableKcpProxy,
            disableKcpInput = state.disableKcpInput,
            enableQuicProxy = state.enableQuicProxy,
            disableQuicInput = state.disableQuicInput,
            proxyForwardBySystem = state.proxyForwardBySystem,
            disableEncryption = state.disableEncryption,
            disableUdpHolePunching = state.disableUdpHolePunching,
            disableSymHolePunching = state.disableSymHolePunching,
        )
    }

    fun readToml(): String = EasyTierController.readStoredConfig(appContext)

    fun saveConfig(config: Config) {
        EasyTierController.saveStoredConfig(appContext, toToml(config))
    }

    fun buildPcConfig(): String {
        val config = readConfig()
        return toToml(config.copy(ipv4 = "10.0.0.2"))
    }

    private fun toToml(config: Config): String = EasyTierTomlCodec.build(
        EasyTierConfigUiState(
            networkName = config.networkName,
            networkSecret = config.secret,
            ipv4 = config.ipv4,
            listeners = config.listeners,
            peers = config.peers,
            useSmoltcp = config.useSmoltcp,
            latencyFirst = config.latencyFirst,
            disableP2p = config.disableP2p,
            privateMode = config.privateMode,
            disableIpv6 = config.disableIpv6,
            enableKcpProxy = config.enableKcpProxy,
            disableKcpInput = config.disableKcpInput,
            enableQuicProxy = config.enableQuicProxy,
            disableQuicInput = config.disableQuicInput,
            proxyForwardBySystem = config.proxyForwardBySystem,
            disableEncryption = config.disableEncryption,
            disableUdpHolePunching = config.disableUdpHolePunching,
            disableSymHolePunching = config.disableSymHolePunching,
        ),
    )

    private fun lines(value: String): List<String> =
        value.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
}
