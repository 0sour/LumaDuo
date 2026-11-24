// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.sour.lumaduo.utils.AppIconCache
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
    private companion object {
        const val MAX_CACHED_HEROES = 8
    }

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

    private val _appIcons = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val appIcons: StateFlow<Map<Int, Bitmap>> = _appIcons.asStateFlow()

    /** Foundation hero art, fetched lazily for the selected app and keyed by app ID. */
    private val _appHeroes = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val appHeroes: StateFlow<Map<Int, Bitmap>> = _appHeroes.asStateFlow()
    private val heroVersions = HashMap<Int, String?>()
    private var heroJob: Job? = null

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
    private var iconsJob: Job? = null
    private var bound = false
    private val addressOverrides = HashMap<String, ComputerDetails.AddressTuple>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val manager = service as? ComputerManagerService.ComputerManagerBinder ?: return
            binder = manager
            manager.startPolling()
            _hosts.value = sortHosts(manager.getComputers().map(::withAddressOverride))
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
        _hosts.value = sortHosts(manager.getComputers().map(::withAddressOverride))
        manager.startPolling()
    }

    /**
     * Remembers a user-selected address for this host. The manager may keep polling
     * other addresses, so the override is re-applied to every snapshot until the app
     * process ends. Connection and app-list requests use the resulting activeAddress.
     */
    fun selectAddress(host: ComputerDetails, address: ComputerDetails.AddressTuple) {
        val uuid = host.uuid ?: return
        addressOverrides[uuid] = address
        _hosts.value = sortHosts(
            _hosts.value.map { current ->
                if (current.uuid == uuid) {
                    ComputerDetails(current).apply { activeAddress = address }
                } else {
                    current
                }
            },
        )
        if (appsHostUuid == uuid) {
            _hosts.value.firstOrNull { it.uuid == uuid }?.let { loadApps(it, force = true) }
        }
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
        iconsJob?.cancel()
        heroJob?.cancel()
        if (hostChanged) {
            _appIcons.value = emptyMap()
            _appHeroes.value = emptyMap()
            heroVersions.clear()
        }
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
                loadAppIcons(host, manager, list)
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

    /**
     * Loads box art lazily after the app list is visible. Icons are an enhancement, so a
     * failed fetch leaves the letter tile in place and never blocks launching.
     */
    private fun loadAppIcons(
        host: ComputerDetails,
        manager: ComputerManagerService.ComputerManagerBinder,
        apps: List<NvApp>,
    ) {
        val uuid = host.uuid ?: return
        val cached = HashMap<Int, Bitmap>()
        for (app in apps) {
            AppIconCache.instance.getIcon(host, app)?.let { cached[app.appId] = it }
        }
        if (cached.isNotEmpty()) {
            _appIcons.value = cached
        }

        iconsJob?.cancel()
        iconsJob = scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val result = HashMap<Int, Bitmap>()
                val address = host.activeAddress ?: return@withContext result
                val http = NvHTTP(
                    address,
                    host.httpsPort,
                    manager.getUniqueId(),
                    "",
                    host.serverCert,
                    PlatformBinding.getCryptoProvider(appContext),
                )
                for (app in apps) {
                    if (cached.containsKey(app.appId)) continue
                    try {
                        val bitmap = http.getBoxArt(app).use(BitmapFactory::decodeStream)
                        if (bitmap != null) {
                            AppIconCache.instance.putIcon(host, app, bitmap)
                            result[app.appId] = bitmap
                        }
                    } catch (e: Exception) {
                        LimeLog.info("App icon fetch failed for ${app.appName}: ${e.message}")
                    }
                }
                result
            }
            if (appsHostUuid == uuid) {
                _appIcons.value = HashMap(cached).apply { putAll(loaded) }
            }
        }
    }

    /**
     * Fetches Foundation's 16:9 hero art for the current selection. Only hosts that
     * advertise "hero" in FsArt get a request; every other app keeps the poster.
     */
    fun loadAppHero(host: ComputerDetails, app: NvApp?) {
        if (app == null || !app.hasFsArt("hero")) {
            heroJob?.cancel()
            heroJob = null
            if (app != null && _appHeroes.value.containsKey(app.appId)) {
                _appHeroes.value = _appHeroes.value - app.appId
                heroVersions.remove(app.appId)
            }
            return
        }
        val version = app.fsArtVersion
        if (_appHeroes.value.containsKey(app.appId) && heroVersions[app.appId] == version) {
            return
        }
        val manager = binder ?: return
        val address = host.activeAddress ?: return
        heroJob?.cancel()
        heroJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val http = NvHTTP(
                        address,
                        host.httpsPort,
                        manager.getUniqueId(),
                        "",
                        host.serverCert,
                        PlatformBinding.getCryptoProvider(appContext),
                    )
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    http.getAppArt(app, "hero").use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                } catch (e: Exception) {
                    LimeLog.info("App hero fetch failed for ${app.appName}: ${e.message}")
                    null
                }
            }
            if (bitmap != null) {
                heroVersions[app.appId] = version
                val updated = _appHeroes.value + (app.appId to bitmap)
                // Selected-app art only; keep a small LRU-ish window so browsing many
                // 1920x1080 heroes cannot grow the bitmap cache without bound.
                _appHeroes.value = if (updated.size <= MAX_CACHED_HEROES) {
                    updated
                } else {
                    updated.entries
                        .drop(updated.size - MAX_CACHED_HEROES)
                        .associate { it.key to it.value }
                }
            }
        }
    }

    fun stopAppList() {
        appsJob?.cancel()
        appsJob = null
        iconsJob?.cancel()
        iconsJob = null
        heroJob?.cancel()
        heroJob = null
        appsHostUuid = null
        _apps.value = emptyList()
        _appIcons.value = emptyMap()
        _appHeroes.value = emptyMap()
        heroVersions.clear()
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
            merged[index] = withAddressOverride(update)
        } else {
            merged += withAddressOverride(update)
        }
        _hosts.value = sortHosts(merged)
    }

    private fun withAddressOverride(details: ComputerDetails): ComputerDetails {
        val copy = ComputerDetails(details)
        val uuid = copy.uuid ?: return copy
        val override = addressOverrides[uuid] ?: return copy
        if (copy.availableAddresses.contains(override) || copy.activeAddress == override) {
            copy.activeAddress = override
        }
        return copy
    }

    private fun sortHosts(hosts: List<ComputerDetails>): List<ComputerDetails> {
        return hosts.sortedWith(
            compareBy(
                { it.state != ComputerDetails.State.ONLINE },
                { it.pairState != PairingManager.PairState.PAIRED },
                { it.name.orEmpty().lowercase() },
            ),
        )
    }
}
