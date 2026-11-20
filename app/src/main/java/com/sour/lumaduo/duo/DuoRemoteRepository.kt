// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.content.Context
import com.sour.lumaduo.utils.easytier.EasyTierController
import com.sour.lumaduo.utils.easytier.EasyTierTomlCodec

/**
 * Read-only view of the EasyTier configuration that the legacy controller will use.
 *
 * Runtime start/stop, PC config export and QR codes stay in M4. This repository never writes
 * the TOML and never exposes `network_secret`; the UI only learns whether a secret exists.
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

    fun read(): Snapshot {
        val toml = EasyTierController.readStoredConfig(appContext)
        val config = EasyTierTomlCodec.parseConfig(toml)
        val peers = config.peers.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val listeners = config.listeners.lineSequence()
            .flatMap { it.split(',') }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        return Snapshot(
            networkName = config.networkName.ifBlank { "easytier" },
            ipv4 = config.ipv4.ifBlank { "10.0.0.1" },
            hasSecret = config.networkSecret.isNotEmpty(),
            peers = peers,
            listeners = listeners,
            usesDefaultTemplate = config.networkSecret.isEmpty() &&
                config.networkName == "easytier" &&
                peers == listOf("tcp://public.easytier.top:11010"),
        )
    }
}
