// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.computers.ComputerManagerService
import com.sour.lumaduo.nvstream.http.ComputerDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Binds the existing ComputerManagerService and exposes a stable, copy-only host list
 * to the bottom-screen Compose shell. The service stays the owner of discovery/polling;
 * this repository is a thin adapter so the shell never touches polling tuples directly.
 */
class DuoHostRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _hosts = MutableStateFlow<List<ComputerDetails>>(emptyList())
    val hosts: StateFlow<List<ComputerDetails>> = _hosts.asStateFlow()

    private var binder: ComputerManagerService.ComputerManagerBinder? = null
    private var updatesJob: Job? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val manager = service as? ComputerManagerService.ComputerManagerBinder ?: return
            binder = manager
            manager.startPolling()
            _hosts.value = sortHosts(manager.getComputers())
            updatesJob?.cancel()
            updatesJob = scope.launch {
                manager.computerUpdates.collect { update ->
                    mergeUpdate(update)
                }
            }
            LimeLog.info("DuoHostRepository connected; ${_hosts.value.size} host(s)")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            updatesJob?.cancel()
            updatesJob = null
            binder = null
            LimeLog.info("DuoHostRepository disconnected")
        }
    }

    fun start() {
        if (bound) return
        bound = appContext.bindService(
            Intent(appContext, ComputerManagerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
        binder?.stopPolling()
        binder = null
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
    }

    fun currentBinder(): ComputerManagerService.ComputerManagerBinder? = binder

    fun refresh() {
        val manager = binder ?: return
        _hosts.value = sortHosts(manager.getComputers())
        manager.startPolling()
    }

    private fun mergeUpdate(update: ComputerDetails) {
        val uuid = update.uuid ?: return
        val merged = _hosts.value.toMutableList()
        val index = merged.indexOfFirst { it.uuid == uuid }
        if (index >= 0) {
            merged[index] = ComputerDetails(update)
        } else {
            merged += ComputerDetails(update)
        }
        _hosts.value = sortHosts(merged)
    }

    private fun sortHosts(hosts: List<ComputerDetails>): List<ComputerDetails> {
        return hosts.sortedWith(
            compareBy(
                { it.state != ComputerDetails.State.ONLINE },
                { it.name.orEmpty().lowercase() },
            ),
        )
    }
}
