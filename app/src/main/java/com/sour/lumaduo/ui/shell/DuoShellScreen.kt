// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.lumaduo.BuildConfig
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoHostRepository
import com.sour.lumaduo.duo.DuoRemoteController
import com.sour.lumaduo.duo.DuoRemoteRepository
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.duo.stream.NetworkProbe
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.NvApp
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.ui.luma.DuoFocusMemory
import com.sour.lumaduo.ui.luma.DuoKeys
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LocalDuoFocusMemory
import com.sour.lumaduo.ui.luma.LumaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only context mirrored to the top display. Never editable from the top. */
data class DuoTopContext(
    val hostName: String? = null,
    val hostAddress: String? = null,
    val hostOnline: Boolean = false,
    val hostPaired: Boolean = false,
    val hostTag: String? = null,
    val appName: String? = null,
    val appInitial: String? = null,
    val appColor: Color? = null,
    val appHdr: Boolean = false,
    val settingsCategory: String? = null,
    val settingsDescription: String? = null,
    val settingsRows: List<String> = emptyList(),
    val remoteName: String? = null,
    val remoteIp: String? = null,
    val remoteConnected: Boolean = false,
    val remotePeers: Int = 0,
)

private data class SettingsTop(
    val label: String,
    val description: String,
    val rows: List<String>,
)

private val topAppColors = listOf(
    Color(0xFF7BA7C7),
    Color(0xFF9B9BA8),
    Color(0xFFF2C14E),
    Color(0xFF4CC38A),
    Color(0xFF5ED0FF),
    Color(0xFFA78BFA),
)

/**
 * Everything the shell needs from the Activity. Keeping this as one interface avoids
 * threading a dozen unrelated lambdas through every page.
 */
interface DuoShellHost {
    fun connect(host: ComputerDetails)
    fun openApps(host: ComputerDetails)
    fun refreshApps(host: ComputerDetails)
    fun refreshHosts()
    fun pair(host: ComputerDetails)
    fun clearPair()
    fun wake(host: ComputerDetails)
    fun delete(host: ComputerDetails)
    fun selectAddress(host: ComputerDetails, address: ComputerDetails.AddressTuple)
    fun hostMore(host: ComputerDetails, action: DuoHostAction)
    fun addHost(host: String, port: Int, onResult: (String?) -> Unit)
    fun openApp(host: ComputerDetails, app: NvApp, useLastSettings: Boolean)
    fun quitApp(host: ComputerDetails, app: NvApp?, thenLaunch: NvApp?)
    fun setAppHidden(host: ComputerDetails, app: NvApp, hidden: Boolean)
    fun appHidden(host: ComputerDetails, app: NvApp): Boolean
    fun createShortcut(host: ComputerDetails, app: NvApp)
    fun startStopEasyTier()
    fun saveRemoteConfig(config: DuoRemoteRepository.Config)
    fun saveRemoteAdvanced(config: DuoRemoteRepository.Config)
    fun copyPcConfig()
    fun pcConfig(): String
    fun openLegacy()
    fun openNotices()
    fun openHelp()
    fun openConsolePreview()
    fun notice(message: String)
}

@Composable
fun DuoShellScreen(
    destination: DuoDestination,
    onDestinationChange: (DuoDestination) -> Unit,
    hosts: StateFlow<List<ComputerDetails>>,
    apps: StateFlow<List<NvApp>>,
    appsLoading: StateFlow<Boolean>,
    appsError: StateFlow<String?>,
    appIcons: StateFlow<Map<Int, android.graphics.Bitmap>>,
    settings: DuoSettingsRepository.Snapshot,
    remote: DuoRemoteRepository.Snapshot,
    remoteConfig: DuoRemoteRepository.Config,
    runtime: DuoRemoteController.State,
    pairState: DuoHostRepository.PairUiState,
    settingsRepository: DuoSettingsRepository,
    host: DuoShellHost,
    onSettingsChanged: (DuoSettingsRepository.Snapshot) -> Unit,
    externalNotice: String? = null,
    onExternalNoticeConsumed: () -> Unit = {},
    onTopContextChanged: (DuoTopContext) -> Unit = {},
    rootBackArmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedUuid by rememberSaveable { mutableStateOf("") }
    var hostFilter by rememberSaveable { mutableStateOf(DuoHostFilter.ALL) }
    var appSheet by remember { mutableStateOf<NvApp?>(null) }
    var hostMore by remember { mutableStateOf<ComputerDetails?>(null) }
    var addressHost by remember { mutableStateOf<ComputerDetails?>(null) }
    var netTestHost by remember { mutableStateOf<ComputerDetails?>(null) }
    var netTestNonce by remember { mutableStateOf(0) }
    var netTestRunning by remember { mutableStateOf(false) }
    var netTestResult by remember { mutableStateOf<NetworkProbe.Result?>(null) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var addAddress by rememberSaveable { mutableStateOf("") }
    var addPort by rememberSaveable { mutableStateOf("47989") }
    var addError by remember { mutableStateOf<String?>(null) }
    var addBusy by remember { mutableStateOf(false) }
    var remoteEdit by remember { mutableStateOf(false) }
    var remoteAdvanced by remember { mutableStateOf(false) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    var pairHost by remember { mutableStateOf<ComputerDetails?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var settingsTop by remember {
        mutableStateOf(SettingsTop("画面", "画质、显示与 HUD", emptyList()))
    }
    var appSelection by remember { mutableStateOf<NvApp?>(null) }
    val focusMemory = remember { DuoFocusMemory() }

    val hostList by hosts.collectAsState()
    val appList by apps.collectAsState()
    val appLoading by appsLoading.collectAsState()
    val appError by appsError.collectAsState()
    val appIconMap by appIcons.collectAsState()
    val visibleHosts = remember(hostList, hostFilter) {
        when (hostFilter) {
            DuoHostFilter.ALL -> hostList
            DuoHostFilter.PAIRED -> hostList.filter {
                it.pairState == PairingManager.PairState.PAIRED
            }
            DuoHostFilter.UNPAIRED -> hostList.filter {
                it.pairState != PairingManager.PairState.PAIRED
            }
        }
    }
    val selected = visibleHosts.firstOrNull { it.uuid == selectedUuid } ?: visibleHosts.firstOrNull()
    val pair = pairHost ?: hostList.firstOrNull { it.uuid == pairState.hostUuid }
    val hasShellOverlay = addOpen || pair != null && (pairState.pin != null || pairState.error != null) ||
        hostMore != null || addressHost != null || appSheet != null || confirm != null || netTestHost != null ||
        remoteEdit || remoteAdvanced || qrContent != null

    // Back/Escape/Button-B reach the Presentation's dispatcher. Overlays consume the
    // event before the Activity's page-navigation fallback.
    BackHandler(enabled = hasShellOverlay) {
        when {
            confirm != null -> confirm = null
            qrContent != null -> qrContent = null
            remoteAdvanced -> remoteAdvanced = false
            remoteEdit -> remoteEdit = false
            appSheet != null -> appSheet = null
            netTestHost != null && !netTestRunning -> netTestHost = null
            addressHost != null -> addressHost = null
            hostMore != null -> hostMore = null
            pair != null && (pairState.pin != null || pairState.error != null) -> {
                pairHost = null
                host.clearPair()
            }
            addOpen && !addBusy -> addOpen = false
            else -> Unit
        }
    }

    LaunchedEffect(netTestHost, netTestNonce) {
        val target = netTestHost ?: return@LaunchedEffect
        val address = target.activeAddress?.address
            ?: target.localAddress?.address
            ?: target.manualAddress?.address
        if (address.isNullOrBlank()) {
            netTestRunning = false
            netTestResult = NetworkProbe.Result("不可达", "主机没有可用地址")
            return@LaunchedEffect
        }
        netTestRunning = true
        netTestResult = null
        val result = withContext(Dispatchers.IO) { NetworkProbe.probe(address) }
        netTestRunning = false
        netTestResult = result
    }

    LaunchedEffect(destination, selected?.uuid) {
        if (destination == DuoDestination.APPS) {
            selected?.let(host::openApps)
        }
    }

    LaunchedEffect(selected?.uuid) {
        appSelection = null
    }

    val hostAddress = selected?.activeAddress
        ?: selected?.localAddress
        ?: selected?.manualAddress
        ?: selected?.remoteAddress
        ?: selected?.ipv6Address
    val hostTag = when {
        selected?.activeAddress != null && selected.activeAddress == selected.localAddress -> "LAN"
        selected?.manualAddress != null -> "手动"
        selected?.remoteAddress != null -> "远程"
        else -> null
    }
    val topApp = appSelection?.takeIf { destination == DuoDestination.APPS }
    val topContext = DuoTopContext(
        hostName = selected?.name,
        hostAddress = hostAddress?.address,
        hostOnline = selected?.state == ComputerDetails.State.ONLINE,
        hostPaired = selected?.pairState == PairingManager.PairState.PAIRED,
        hostTag = hostTag,
        appName = topApp?.appName,
        appInitial = topApp?.appName?.trim()?.firstOrNull()?.uppercase(),
        appColor = topApp?.let { topAppColors[((it.appId % topAppColors.size) + topAppColors.size) % topAppColors.size] },
        appHdr = topApp?.isHdrSupported() == true,
        settingsCategory = settingsTop.label.takeIf { destination == DuoDestination.SETTINGS },
        settingsDescription = settingsTop.description.takeIf { destination == DuoDestination.SETTINGS },
        settingsRows = settingsTop.rows.takeIf { destination == DuoDestination.SETTINGS } ?: emptyList(),
        remoteName = remote.networkName.takeIf { destination == DuoDestination.REMOTE },
        remoteIp = (runtime.virtualIp ?: remote.ipv4).takeIf { destination == DuoDestination.REMOTE },
        remoteConnected = runtime.phase == DuoRemoteController.Phase.CONNECTED,
        remotePeers = runtime.peers,
    )
    LaunchedEffect(topContext) {
        onTopContextChanged(topContext)
    }

    LaunchedEffect(pairState.success) {
        if (pairState.success) {
            pairHost = null
            host.clearPair()
            onDestinationChange(DuoDestination.APPS)
        }
    }

    LaunchedEffect(destination) {
        if (destination != DuoDestination.APPS) showHidden = false
    }

    LaunchedEffect(externalNotice) {
        if (externalNotice != null) {
            notice = externalNotice
            delay(2_200)
            onExternalNoticeConsumed()
        }
    }

    val title = when (destination) {
        DuoDestination.HOSTS -> "主机"
        DuoDestination.APPS -> "应用"
        DuoDestination.REMOTE -> "远程"
        DuoDestination.SETTINGS -> "设置"
    }
    val subtitle = when (destination) {
        DuoDestination.HOSTS -> "ComputerManagerService · 实时"
        DuoDestination.APPS -> "真实应用列表 · ${appList.size} 个"
        DuoDestination.REMOTE -> "EasyTier 配置 · 密钥遮罩"
        DuoDestination.SETTINGS -> "7 类 · B 调整 · A 返回"
    }
    val actions = when (destination) {
        DuoDestination.HOSTS -> buildList {
            add(DuoAction(R.drawable.duo_ic_plus, "添加主机") {
                addAddress = ""
                addPort = "47989"
                addError = null
                addOpen = true
            })
            add(DuoAction(R.drawable.duo_ic_refresh, "刷新主机") { host.refreshHosts() })
        }
        DuoDestination.APPS -> buildList {
            add(DuoAction(
                R.drawable.duo_ic_sliders,
                if (showHidden) "隐藏已隐藏应用" else "显示已隐藏应用",
            ) { showHidden = !showHidden })
            add(DuoAction(R.drawable.duo_ic_refresh, "刷新应用") {
                selected?.let { host.refreshApps(it) }
            })
        }
        DuoDestination.REMOTE -> if (BuildConfig.MINIMAL_EDITION) emptyList() else listOf(
            DuoAction(R.drawable.duo_ic_host, "打开旧版主机管理") { host.openLegacy() },
        )
        DuoDestination.SETTINGS -> emptyList()
    }

    CompositionLocalProvider(LocalDuoFocusMemory provides focusMemory) {
        LumaDuoShell(
            destination = destination,
            onDestinationChange = { next ->
                onDestinationChange(next)
                notice = null
            },
            title = title,
            subtitle = subtitle,
            actions = actions,
            modeLabel = "手柄：下屏",
            modeHint = notice ?: when {
                rootBackArmed -> "再按 A 退出 LumaDuo"
                destination == DuoDestination.HOSTS -> "选择主机 · ▶ 应用 · + 添加"
                destination == DuoDestination.APPS -> "B 启动 · 操作键打开应用操作"
                destination == DuoDestination.REMOTE -> "启动/停止 · 配置 · PC 配置"
                else -> "分类 → 选项 → 调整"
            },
            keyHints = listOf(
                DuoKeyHint(DuoKeys.ConfirmLabel, if (destination == DuoDestination.SETTINGS) "打开/调整" else "选择"),
                DuoKeyHint(DuoKeys.BackLabel, if (destination == DuoDestination.HOSTS) "退出" else "返回"),
            ),
            modifier = modifier,
        ) {
            when (destination) {
                DuoDestination.HOSTS -> DuoHostsPage(
                    hosts = visibleHosts,
                    selectedUuid = selected?.uuid,
                    filter = hostFilter,
                    onFilterChange = { hostFilter = it },
                    onSelect = { selectedUuid = it.uuid.orEmpty() },
                    onAction = { target, action ->
                        when (action) {
                            DuoHostAction.CONNECT -> host.connect(target)
                            DuoHostAction.APPS -> {
                                host.openApps(target)
                                onDestinationChange(DuoDestination.APPS)
                            }
                            DuoHostAction.PAIR -> {
                                if (target.state != ComputerDetails.State.ONLINE) {
                                    host.notice("主机离线，先唤醒或检查网络")
                                } else {
                                    pairHost = target
                                    host.pair(target)
                                }
                            }
                            DuoHostAction.WAKE -> host.wake(target)
                            DuoHostAction.ADDRESS -> addressHost = target
                            DuoHostAction.RESTORE -> host.hostMore(target, action)
                            DuoHostAction.DELETE -> confirm = ConfirmRequest(
                                title = "删除主机？",
                                body = "将从列表移除 ${target.name ?: "这台主机"}，不会卸载 PC 端 Sunshine。",
                                confirmLabel = "删除",
                                onConfirm = { host.delete(target) },
                            )
                            DuoHostAction.MORE -> hostMore = target
                            DuoHostAction.NETWORK_TEST -> {
                                netTestNonce++
                                netTestHost = target
                            }
                            else -> host.hostMore(target, action)
                        }
                    },
                    onAdd = {
                        addAddress = ""
                        addPort = "47989"
                        addError = null
                        addOpen = true
                    },
                    onRefresh = { host.refreshHosts() },
                )
                DuoDestination.APPS -> DuoAppsPage(
                    host = selected,
                    hosts = hostList.filter {
                        it.state == ComputerDetails.State.ONLINE &&
                            it.pairState == PairingManager.PairState.PAIRED
                    },
                    apps = if (showHidden) appList else appList.filterNot {
                        selected?.let { s -> host.appHidden(s, it) } == true
                    },
                    loading = appLoading,
                    error = appError,
                    icons = appIconMap,
                    onSelectHost = { candidate ->
                        hostFilter = DuoHostFilter.ALL
                        selectedUuid = candidate.uuid.orEmpty()
                    },
                    onOpenApp = { app -> selected?.let { host.openApp(it, app, false) } },
                    onAppAction = { app, action ->
                        when (action) {
                            DuoAppAction.INFO -> appSheet = app
                            else -> handleAppAction(selected, app, action, host)
                        }
                    },
                    onSelectionChanged = { appSelection = it },
                )
                DuoDestination.REMOTE -> DuoRemotePage(
                    snapshot = remote,
                    runtime = runtime,
                    onStartStop = host::startStopEasyTier,
                    onEditConfig = { remoteEdit = true },
                    onAdvanced = { remoteAdvanced = true },
                    onCopyPcConfig = host::copyPcConfig,
                    onShowQr = { qrContent = host.pcConfig() },
                    onAddManualHost = {
                        addAddress = "10.0.0.2"
                        addPort = "47989"
                        addError = null
                        addOpen = true
                    },
                )
                DuoDestination.SETTINGS -> DuoSettingsPage(
                    repository = settingsRepository,
                    scene = settings,
                    remote = remote,
                    runtime = runtime,
                    onSceneChanged = onSettingsChanged,
                    onOpenRemote = { onDestinationChange(DuoDestination.REMOTE) },
                    onOpenHosts = { onDestinationChange(DuoDestination.HOSTS) },
                    onOpenLegacy = host::openLegacy,
                    onOpenNotices = host::openNotices,
                    onOpenHelp = host::openHelp,
                    onAddHost = {
                        addAddress = "10.0.0.2"
                        addPort = "47989"
                        addError = null
                        addOpen = true
                    },
                    onNotice = { notice = it },
                    onOpenConsolePreview = host::openConsolePreview,
                    onTopSettingsChanged = { label, description, rows ->
                        settingsTop = SettingsTop(label, description, rows)
                    },
                    backBlocked = hasShellOverlay,
                )
            }
        }

        // ==================== Overlays ====================
        if (addOpen) {
            AddHostOverlay(
                address = addAddress,
                port = addPort,
                error = addError,
                busy = addBusy,
                onAddress = { addAddress = it },
                onPort = { addPort = it },
                onDismiss = { if (!addBusy) addOpen = false },
                onSubmit = {
                    addBusy = true
                    addError = null
                    host.addHost(addAddress, addPort.toIntOrNull() ?: 47989) { error ->
                        addBusy = false
                        if (error == null) {
                            addOpen = false
                        } else {
                            addError = error
                        }
                    }
                },
            )
        }

        if (pair != null && (pairState.pin != null || pairState.error != null)) {
            PairOverlay(
                host = pair,
                state = pairState,
                onRetry = { pair?.let(host::pair) },
                onCancel = {
                    pairHost = null
                    host.clearPair()
                },
            )
        }

        addressHost?.let { target ->
            AddressOverlay(
                host = target,
                onDismiss = { addressHost = null },
                onSelect = { address ->
                    host.selectAddress(target, address)
                    addressHost = null
                },
            )
        }

        hostMore?.let { target ->
            HostMoreOverlay(
                host = target,
                onDismiss = { hostMore = null },
                onAction = { action ->
                    hostMore = null
                    when (action) {
                        DuoHostAction.DELETE -> confirm = ConfirmRequest(
                            title = "删除主机？",
                            body = "将从列表移除 ${target.name ?: "这台主机"}。",
                            confirmLabel = "删除",
                            onConfirm = { host.delete(target) },
                        )
                        DuoHostAction.NETWORK_TEST -> {
                            netTestNonce++
                            netTestHost = target
                        }
                        DuoHostAction.PAIR -> {
                            if (target.state != ComputerDetails.State.ONLINE) {
                                host.notice("主机离线，先唤醒或检查网络")
                            } else {
                                pairHost = target
                                host.pair(target)
                            }
                        }
                        else -> host.hostMore(target, action)
                    }
                },
            )
        }

        netTestHost?.let { target ->
            val address = target.activeAddress?.address
                ?: target.localAddress?.address
                ?: target.manualAddress?.address
                ?: "—"
            DuoOverlay(
                onDismiss = { if (!netTestRunning) netTestHost = null },
                width = 380.dp,
            ) {
                Column {
                    Text("网络测试", style = LumaType.titleM, color = LumaColors.Text)
                    Text(
                        "${target.name ?: "主机"} · $address",
                        style = LumaType.micro,
                        color = LumaColors.Tertiary,
                    )
                    Spacer(Modifier.height(12.dp))
                    when {
                        netTestRunning -> Text(
                            "正在连接 TCP 47984 / 47989 / 47990…",
                            style = LumaType.bodyM,
                            color = LumaColors.Secondary,
                        )
                        netTestResult != null -> {
                            Text(
                                netTestResult!!.label,
                                style = LumaType.titleM,
                                color = LumaColors.Accent,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                netTestResult!!.detail,
                                style = LumaType.caption,
                                color = LumaColors.Secondary,
                            )
                        }
                        else -> Text(
                            "客户端只测端口连通性，不登录、不读取主机数据。",
                            style = LumaType.caption,
                            color = LumaColors.Tertiary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "只测 TCP 连接时间；不伪造丢包或建议码率。",
                        style = LumaType.micro,
                        color = LumaColors.Tertiary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DuoTextButton(
                            if (netTestRunning) "测试中…" else "重新测试",
                            onClick = { netTestNonce++ },
                            enabled = !netTestRunning,
                            modifier = Modifier.weight(1f),
                        )
                        DuoTextButton(
                            "关闭",
                            onClick = { netTestHost = null },
                            enabled = !netTestRunning,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        appSheet?.let { app ->
            val target = selected
            AppActionsOverlay(
                app = app,
                hidden = target?.let { host.appHidden(it, app) } == true,
                onDismiss = { appSheet = null },
                onAction = { action ->
                    appSheet = null
                    target?.let { handleAppAction(it, app, action, host) }
                },
            )
        }

        confirm?.let { request ->
            DuoOverlay(onDismiss = { confirm = null }) {
                DuoConfirmPanel(
                    title = request.title,
                    body = request.body,
                    confirmLabel = request.confirmLabel,
                    onCancel = { confirm = null },
                    onConfirm = {
                        request.onConfirm()
                        confirm = null
                    },
                )
            }
        }

        if (remoteEdit) {
            DuoRemoteConfigEditor(
                config = remoteConfig,
                onSave = {
                    host.saveRemoteConfig(it)
                    remoteEdit = false
                },
                onDismiss = { remoteEdit = false },
            )
        }

        if (remoteAdvanced) {
            DuoRemoteAdvanced(
                config = remoteConfig,
                onSave = {
                    host.saveRemoteAdvanced(it)
                    remoteAdvanced = false
                },
                onDismiss = { remoteAdvanced = false },
            )
        }

        qrContent?.let { content ->
            DuoQrOverlay(content = content, onDismiss = { qrContent = null })
        }
    }
}

private data class ConfirmRequest(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

private fun handleAppAction(
    host: ComputerDetails?,
    app: NvApp,
    action: DuoAppAction,
    callbacks: DuoShellHost,
) {
    if (host == null) return
    when (action) {
        DuoAppAction.LAUNCH -> callbacks.openApp(host, app, false)
        DuoAppAction.LAUNCH_LAST -> callbacks.openApp(host, app, true)
        DuoAppAction.QUIT_AND_LAUNCH -> callbacks.quitApp(host, app, app)
        DuoAppAction.QUIT -> callbacks.quitApp(host, null, null)
        DuoAppAction.HIDE -> {
            val hidden = callbacks.appHidden(host, app)
            callbacks.setAppHidden(host, app, !hidden)
            callbacks.notice(if (hidden) "已取消隐藏 ${app.appName}" else "已隐藏 ${app.appName}")
        }
        DuoAppAction.INFO -> Unit
        DuoAppAction.SHORTCUT -> callbacks.createShortcut(host, app)
    }
}

@Composable
private fun AddHostOverlay(
    address: String,
    port: String,
    error: String?,
    busy: Boolean,
    onAddress: (String) -> Unit,
    onPort: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var target by remember { mutableStateOf("address") }
    LaunchedEffect(Unit) {
        delay(80)
        focus.requestFocus()
    }
    DuoOverlay(onDismiss = onDismiss, width = 440.dp) {
        Column {
            Text("添加主机", style = LumaType.titleM, color = LumaColors.Text)
            Text(
                "输入 PC 的局域网地址或 EasyTier 虚拟 IP。",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("IP / 主机名", style = LumaType.micro, color = LumaColors.Tertiary)
                    OverlayField(
                        value = address,
                        onValueChange = onAddress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                Column(Modifier.width(96.dp)) {
                    Text("端口", style = LumaType.micro, color = LumaColors.Tertiary)
                    OverlayField(
                        value = port,
                        onValueChange = onPort,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, style = LumaType.micro, color = LumaColors.Error)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DuoTextButton(
                    if (target == "address") "输入 IP" else "IP",
                    onClick = { target = "address" },
                    accent = target == "address",
                    modifier = Modifier.weight(1f).height(34.dp),
                )
                DuoTextButton(
                    if (target == "port") "输入端口" else "端口",
                    onClick = { target = "port" },
                    accent = target == "port",
                    modifier = Modifier.weight(1f).height(34.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            DuoNumericKeypad(
                onKey = { key ->
                    if (target == "address") {
                        if (key == "back") {
                            onAddress(address.dropLast(1))
                        } else {
                            onAddress((address + key).take(48))
                        }
                    } else {
                        if (key == "back") {
                            onPort(port.dropLast(1))
                        } else if (key.all(Char::isDigit)) {
                            onPort((port + key).take(5))
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuoTextButton(
                    "取消",
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                DuoTextButton(
                    if (busy) "连接中…" else "添加并检测",
                    onClick = onSubmit,
                    accent = true,
                    enabled = !busy && address.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OverlayField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = LumaColors.Text,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        ),
        modifier = modifier
            .padding(top = 3.dp)
            .background(LumaColors.Bg)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

@Composable
private fun PairOverlay(
    host: ComputerDetails,
    state: DuoHostRepository.PairUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    DuoOverlay(onDismiss = onCancel, width = 420.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "配对 ${host.name ?: "主机"}",
                style = LumaType.titleM,
                color = LumaColors.Text,
            )
            Text(
                "在电脑上的 Foundation Sunshine 输入下方 PIN。",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (state.pin ?: "••••").forEach { ch ->
                    Text(
                        text = ch.toString(),
                        style = LumaType.display.copy(fontFamily = FontFamily.Monospace),
                        color = LumaColors.Accent,
                        modifier = Modifier
                            .background(LumaColors.Bg, LumaShapes.control)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    state.pairing -> "等待 PC 确认…"
                    state.error != null -> state.error
                    state.success -> "配对成功"
                    else -> "准备配对"
                },
                style = LumaType.caption,
                color = if (state.error != null) LumaColors.Error else LumaColors.Secondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuoTextButton("取消", onClick = onCancel, modifier = Modifier.weight(1f))
                DuoTextButton(
                    "重试",
                    onClick = onRetry,
                    accent = true,
                    enabled = !state.pairing,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddressOverlay(
    host: ComputerDetails,
    onDismiss: () -> Unit,
    onSelect: (ComputerDetails.AddressTuple) -> Unit,
) {
    val context = LocalContext.current
    DuoOverlay(onDismiss = onDismiss, width = 420.dp) {
        Column {
            Text("选择连接地址", style = LumaType.titleM, color = LumaColors.Text)
            Text(
                "${host.name ?: "主机"} · ${host.availableAddresses.size} 个地址",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            Spacer(Modifier.height(8.dp))
            host.availableAddresses.forEach { address ->
                val current = address == host.activeAddress
                DuoSettingRow(
                    title = address.toString(),
                    value = if (current) "当前" else host.getAddressTypeDescription(context, address),
                    detail = if (current) "串流和应用列表使用此地址" else null,
                    isSubpage = true,
                    onActivate = { onSelect(address) },
                )
            }
            Spacer(Modifier.height(6.dp))
            DuoTextButton("关闭", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HostMoreOverlay(
    host: ComputerDetails,
    onDismiss: () -> Unit,
    onAction: (DuoHostAction) -> Unit,
) {
    DuoOverlay(onDismiss = onDismiss, width = 400.dp) {
        Column {
            Text(host.name ?: "主机操作", style = LumaType.titleM, color = LumaColors.Text)
            Text(
                "${host.activeAddress?.address ?: "—"} · ${if (host.pairState == PairingManager.PairState.PAIRED) "已配对" else "未配对"}",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            Spacer(Modifier.height(8.dp))
            DuoSettingRow("恢复上次会话", "运行中", detail = "继续任意在线主机的运行中应用", onActivate = {
                onAction(DuoHostAction.RESTORE)
            })
            DuoSettingRow("唤醒主机", "WOL", detail = "离线且已记录 MAC 时可用", onActivate = {
                onAction(DuoHostAction.WAKE)
            })
            if (host.pairState == PairingManager.PairState.PAIRED) {
                DuoSettingRow("重新配对", "证书", detail = "重新输入主机 PIN", onActivate = {
                    onAction(DuoHostAction.PAIR)
                })
            }
            DuoSettingRow("网络测试", "延迟 / 丢包", onActivate = {
                onAction(DuoHostAction.NETWORK_TEST)
            })
            DuoSettingRow("休眠主机", "确认", onActivate = { onAction(DuoHostAction.SLEEP) })
            DuoSettingRow("退出主机应用", "确认", onActivate = { onAction(DuoHostAction.QUIT) })
            DuoSettingRow("打开 Sunshine WebUI", "浏览器", onActivate = {
                onAction(DuoHostAction.WEBUI)
            })
            DuoSettingRow(
                "IPv6",
                if (host.ipv6Disabled) "已禁用" else "已启用",
                onActivate = { onAction(DuoHostAction.IPV6) },
            )
            Spacer(Modifier.height(6.dp))
            DuoTextButton(
                "删除主机",
                onClick = { onAction(DuoHostAction.DELETE) },
                danger = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            DuoTextButton("关闭", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AppActionsOverlay(
    app: NvApp,
    hidden: Boolean,
    onDismiss: () -> Unit,
    onAction: (DuoAppAction) -> Unit,
) {
    DuoOverlay(onDismiss = onDismiss, width = 400.dp) {
        Column {
            Text(app.appName, style = LumaType.titleM, color = LumaColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "App ID ${app.appId} · ${if (app.isHdrSupported()) "支持 HDR" else "SDR"}",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            app.cmdList?.let { commands ->
                Text(
                    "启动命令 ${commands.size()} 条 · 由主机应用列表提供",
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                )
            }
            Spacer(Modifier.height(8.dp))
            DuoSettingRow("启动", "B", onActivate = { onAction(DuoAppAction.LAUNCH) })
            DuoSettingRow(
                "使用上次设置启动",
                "上次会话",
                detail = "分辨率 / FPS / 码率 / HDR",
                onActivate = { onAction(DuoAppAction.LAUNCH_LAST) },
            )
            DuoSettingRow("退出当前应用并启动", "确认", onActivate = {
                onAction(DuoAppAction.QUIT_AND_LAUNCH)
            })
            DuoSettingRow("退出当前应用", "确认", onActivate = { onAction(DuoAppAction.QUIT) })
            DuoSettingRow(
                if (hidden) "取消隐藏" else "隐藏应用",
                if (hidden) "已隐藏" else "",
                onActivate = { onAction(DuoAppAction.HIDE) },
            )
            DuoSettingRow("创建桌面快捷方式", "桌面", onActivate = { onAction(DuoAppAction.SHORTCUT) })
            Spacer(Modifier.height(6.dp))
            DuoTextButton("关闭", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
