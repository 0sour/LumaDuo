// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.binding.PlatformBinding
import com.sour.lumaduo.computers.ComputerManagerService
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.NvApp
import com.sour.lumaduo.nvstream.http.NvHTTP
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.nvstream.wol.WakeOnLanSender
import java.io.IOException
import java.io.StringReader
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Binds the existing ComputerManagerService and exposes a stable, copy-only host list
 * to the bottom-screen Compose shell. The service stays the owner of discovery/polling;
 * this repository is a thin adapter so the shell never touches polling tuples directly.
 *
 * App lists are fetched on demand from the selected host (read-only GET), because the
 * shell needs a simple loading/error state while the legacy AppView owns its own poller.
 */
class DuoHostRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _hosts = MutableStateFlow<List<ComputerDetails>>(emptyList())
    val hosts: StateFlow<List<ComputerDetails>> = _hosts.asStateFlow()

    private val _apps = MutableStateFlow<List<NvApp>>(emptyList())
    val apps: StateFlow<List<NvApp>> = _apps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _appsError = MutableStateFlow<String?>(null)
    val appsError: StateFlow<String?> = _appsError.asStateFlow()

    data class PairUiState(
        val hostUuid: String? = null,
        val pin: String? = null,
        val pairing: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
    )

    private val _pairState = MutableStateFlow(PairUiState())
    val pairState: StateFlow<PairUiState> = _pairState.asStateFlow()

    private var binder: ComputerManagerService.ComputerManagerBinder? = null
    private var updatesJob: Job? = null
    private var appsJob: Job? = null
    private var appsHostUuid: String? = null
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
            stopAppList()
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
        stopAppList()
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

    /** Loads the selected host's app list. A different host cancels the old request. */
    fun loadApps(host: ComputerDetails, force: Boolean = false) {
        val manager = binder ?: return
        val uuid = host.uuid ?: return
        if (!force && appsHostUuid == uuid && (appsJob?.isActive == true || _apps.value.isNotEmpty())) {
            return
        }

        val hostChanged = appsHostUuid != uuid
        appsJob?.cancel()
        appsHostUuid = uuid
        _appsLoading.value = true
        _appsError.value = null
        if (force || hostChanged) {
            _apps.value = emptyList()
        }

        appsJob = scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val address = host.activeAddress
                        ?: throw IOException("主机离线")
                    val http = NvHTTP(
                        address,
                        host.httpsPort,
                        manager.getUniqueId(),
                        "",
                        host.serverCert,
                        PlatformBinding.getCryptoProvider(appContext),
                    )
                    NvHTTP.getAppListByReader(StringReader(http.getAppListRaw()))
                }
                _apps.value = list
            } catch (e: Exception) {
                _appsError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: e.javaClass.simpleName
            } finally {
                _appsLoading.value = false
            }
        }
    }

    fun refreshAppList(host: ComputerDetails) {
        loadApps(host, force = true)
    }

    fun stopAppList() {
        appsJob?.cancel()
        appsJob = null
        appsHostUuid = null
        _apps.value = emptyList()
        _appsError.value = null
        _appsLoading.value = false
    }

    // ==================== Host management ====================

    fun addHost(host: String, port: Int, onResult: (String?) -> Unit) {
        val manager = binder
        if (manager == null) {
            onResult("主机服务尚未就绪")
            return
        }
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val details = ComputerDetails()
                    details.manualAddress = ComputerDetails.AddressTuple(host.trim(), port)
                    if (!manager.addComputerBlocking(details)) {
                        "无法连接主机，请检查地址/端口"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            refresh()
            onResult(error)
        }
    }

    /**
     * Generates a PIN, publishes it for the bottom-screen pairing panel, then runs the
     * normal certificate pairing with the user typing that PIN on the PC.
     */
    fun pair(host: ComputerDetails, onSuccess: (ComputerDetails) -> Unit) {
        val manager = binder ?: run {
            _pairState.value = PairUiState(host.uuid, error = "主机服务尚未就绪")
            return
        }
        val uuid = host.uuid ?: return
        val pin = PairingManager.generatePinString()
        _pairState.value = PairUiState(hostUuid = uuid, pin = pin, pairing = true)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val address = host.activeAddress ?: throw IOException("主机离线")
                    val http = NvHTTP(
                        address,
                        host.httpsPort,
                        manager.getUniqueId(),
                        "",
                        host.serverCert,
                        PlatformBinding.getCryptoProvider(appContext),
                    )
                    if (http.getPairState() == PairingManager.PairState.PAIRED) {
                        return@withContext PairResult(null, true)
                    }
                    val pairingManager = http.pairingManager
                    val pairResult = pairingManager.pair(http.getServerInfo(true), pin)
                    when (pairResult.state) {
                        PairingManager.PairState.PIN_WRONG -> PairResult("PIN 错误，请在 PC 上重新输入", false)
                        PairingManager.PairState.FAILED -> PairResult("配对失败，主机可能正在游戏", false)
                        PairingManager.PairState.ALREADY_IN_PROGRESS -> PairResult("另一台设备正在配对", false)
                        PairingManager.PairState.PAIRED -> {
                            manager.getComputer(uuid)?.serverCert = pairingManager.pairedCert
                            appContext.getSharedPreferences("pair_name_map", Context.MODE_PRIVATE)
                                .edit()
                                .putString(uuid, pairResult.pairName)
                                .apply()
                            manager.invalidateStateForComputer(uuid)
                            PairResult(null, true)
                        }
                        else -> PairResult("配对未完成", false)
                    }
                } catch (e: UnknownHostException) {
                    PairResult("找不到主机", false)
                } catch (e: Exception) {
                    PairResult(e.message ?: e.javaClass.simpleName, false)
                }
            }
            _pairState.value = PairUiState(
                hostUuid = uuid,
                pin = pin,
                pairing = false,
                error = result.error,
                success = result.success,
            )
            if (result.success) {
                refresh()
                manager.getComputer(uuid)?.let(onSuccess)
            }
        }
    }

    fun clearPairState() {
        _pairState.value = PairUiState()
    }

    fun deleteHost(host: ComputerDetails) {
        binder?.removeComputer(host)
        refresh()
    }

    fun wakeHost(host: ComputerDetails) {
        scope.launch(Dispatchers.IO) {
            runCatching { WakeOnLanSender.sendWolPacket(host) }
                .onFailure { LimeLog.warning("WOL failed: ${it.message}") }
        }
    }

    fun quitRunningApp(host: ComputerDetails, onResult: (String?) -> Unit) {
        val manager = binder ?: run {
            onResult("主机服务尚未就绪")
            return
        }
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val address = host.activeAddress ?: throw IOException("主机离线")
                    val http = NvHTTP(
                        address,
                        host.httpsPort,
                        manager.getUniqueId(),
                        "",
                        host.serverCert,
                        PlatformBinding.getCryptoProvider(appContext),
                    )
                    if (!http.quitApp()) "主机拒绝了退出请求" else null
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            onResult(error)
        }
    }

    fun sleepHost(host: ComputerDetails, onResult: (String?) -> Unit) {
        val manager = binder ?: run {
            onResult("主机服务尚未就绪")
            return
        }
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val address = host.activeAddress ?: throw IOException("主机离线")
                    val http = NvHTTP(
                        address,
                        host.httpsPort,
                        manager.getUniqueId(),
                        "",
                        host.serverCert,
                        PlatformBinding.getCryptoProvider(appContext),
                    )
                    if (!http.pcSleep()) "主机拒绝了休眠请求" else null
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            onResult(error)
        }
    }

    fun isAppHidden(host: ComputerDetails, app: NvApp): Boolean {
        val uuid = host.uuid ?: return false
        val prefs = appContext.getSharedPreferences("HiddenApps", Context.MODE_PRIVATE)
        return prefs.getStringSet(uuid, emptySet())?.contains(app.appId.toString()) == true
    }

    fun setAppHidden(host: ComputerDetails, app: NvApp, hidden: Boolean) {
        val uuid = host.uuid ?: return
        val prefs = appContext.getSharedPreferences("HiddenApps", Context.MODE_PRIVATE)
        val ids = (prefs.getStringSet(uuid, emptySet()) ?: emptySet()).toMutableSet()
        if (hidden) ids += app.appId.toString() else ids -= app.appId.toString()
        prefs.edit().putStringSet(uuid, ids).apply()
    }

    private data class PairResult(val error: String?, val success: Boolean)

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
