// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.BuildConfig
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoRemoteRepository
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.NvApp
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType
import kotlinx.coroutines.flow.StateFlow

/**
 * Production LumaDuo bottom-screen shell. It is data-driven: hosts/apps come from
 * [com.sour.lumaduo.duo.DuoHostRepository], settings from [DuoSettingsRepository] and the
 * EasyTier summary from [DuoRemoteRepository]. No demo values are rendered here.
 */
@Composable
fun DuoShellScreen(
    destination: DuoDestination,
    onDestinationChange: (DuoDestination) -> Unit,
    hosts: StateFlow<List<ComputerDetails>>,
    apps: StateFlow<List<NvApp>>,
    appsLoading: StateFlow<Boolean>,
    appsError: StateFlow<String?>,
    settings: DuoSettingsRepository.Snapshot,
    remote: DuoRemoteRepository.Snapshot,
    onOpenLegacy: () -> Unit,
    onRefreshHosts: () -> Unit,
    onRequestApps: (ComputerDetails) -> Unit,
    onRefreshApps: (ComputerDetails) -> Unit,
    onOpenApp: (ComputerDetails, NvApp) -> Unit,
    onSettingsAdjust: (DuoSettingsRepository.Field, Int) -> Unit,
    settingsNotice: String?,
    onSettingsNoticeExpired: () -> Unit,
    rootBackArmed: Boolean = false,
) {
    var selectedUuid by rememberSaveable { mutableStateOf("") }
    val hostList by hosts.collectAsState()
    val appList by apps.collectAsState()
    val appLoading by appsLoading.collectAsState()
    val appError by appsError.collectAsState()
    val effectiveSelectedUuid = selectedUuid.ifEmpty { hostList.firstOrNull()?.uuid.orEmpty() }
    val selectedHost = hostList.firstOrNull { it.uuid == effectiveSelectedUuid }

    LaunchedEffect(destination, selectedHost?.uuid) {
        if (destination == DuoDestination.APPS) {
            selectedHost?.let(onRequestApps)
        }
    }

    val title = when (destination) {
        DuoDestination.HOSTS -> "主机"
        DuoDestination.APPS -> selectedHost?.name?.let { "$it · 应用" } ?: "应用"
        DuoDestination.REMOTE -> "远程"
        DuoDestination.SETTINGS -> "设置"
    }
    val subtitle = when (destination) {
        DuoDestination.HOSTS -> "ComputerManagerService · 实时"
        DuoDestination.APPS -> "真实应用列表 · ${appList.size} 个"
        DuoDestination.REMOTE -> "EasyTier 配置 · 密钥遮罩"
        DuoDestination.SETTINGS -> "A 调整 · ←/→ 微调"
    }
    val actions = when (destination) {
        DuoDestination.HOSTS -> buildList {
            add(DuoAction(R.drawable.duo_ic_plus, "添加主机") { onOpenLegacy() })
            if (selectedHost?.pairState == PairingManager.PairState.PAIRED) {
                add(DuoAction(R.drawable.duo_ic_play, "进入应用列表") {
                    selectedHost?.let(onRequestApps)
                    onDestinationChange(DuoDestination.APPS)
                })
            }
            add(DuoAction(R.drawable.duo_ic_refresh, "刷新主机") { onRefreshHosts() })
        }
        DuoDestination.APPS -> listOf(
            DuoAction(R.drawable.duo_ic_refresh, "刷新应用") {
                selectedHost?.let(onRefreshApps)
            },
        )
        DuoDestination.REMOTE -> listOf(
            DuoAction(R.drawable.duo_ic_host, "打开旧版主机管理") { onOpenLegacy() },
        )
        DuoDestination.SETTINGS -> emptyList()
    }

    LumaDuoShell(
        destination = destination,
        onDestinationChange = onDestinationChange,
        title = title,
        subtitle = subtitle,
        actions = actions,
        modeLabel = "手柄：下屏",
        modeHint = when {
            rootBackArmed -> "再按 B 退出 LumaDuo"
            destination == DuoDestination.HOSTS -> "选择主机 · 右上角进入应用"
            destination == DuoDestination.APPS -> "选择应用 · 启动串流"
            destination == DuoDestination.REMOTE -> "只读配置 · 下屏启停在 M4"
            else -> settingsNotice ?: "选择分类 · A 调整数值"
        },
        keyHints = listOf(
            DuoKeyHint("A", if (destination == DuoDestination.SETTINGS) "调整" else "选择"),
            DuoKeyHint("B", if (destination == DuoDestination.HOSTS) "退出" else "返回"),
        ),
    ) {
        when (destination) {
            DuoDestination.HOSTS -> DuoHostsPage(
                hosts = hostList,
                selectedUuid = effectiveSelectedUuid,
                onSelectHost = { host -> selectedUuid = host.uuid.orEmpty() },
            )
            DuoDestination.APPS -> DuoAppsPage(
                host = selectedHost,
                apps = appList,
                loading = appLoading,
                error = appError,
                onOpenApp = { app -> selectedHost?.let { onOpenApp(it, app) } },
            )
            DuoDestination.REMOTE -> DuoRemotePage(
                remote = remote,
                onOpenLegacy = onOpenLegacy,
            )
            DuoDestination.SETTINGS -> DuoSettingsPage(
                settings = settings,
                remote = remote,
                notice = settingsNotice,
                onAdjust = onSettingsAdjust,
                onNoticeExpired = onSettingsNoticeExpired,
            )
        }
    }
}

private data class DuoHost(
    val uuid: String,
    val name: String,
    val address: String,
    val tag: String,
    val online: Boolean,
    val paired: Boolean,
)

private fun ComputerDetails.toDuoHost(): DuoHost {
    val address = (activeAddress ?: localAddress ?: remoteAddress ?: ipv6Address)?.address ?: "—"
    val tag = when {
        activeAddress != null && activeAddress == localAddress -> "LAN"
        manualAddress != null -> "手动"
        else -> "远程"
    }
    return DuoHost(
        uuid = uuid.orEmpty(),
        name = name ?: "未命名主机",
        address = address,
        tag = tag,
        online = state == ComputerDetails.State.ONLINE,
        paired = pairState == PairingManager.PairState.PAIRED,
    )
}

@Composable
private fun DuoHostsPage(
    hosts: List<ComputerDetails>,
    selectedUuid: String?,
    onSelectHost: (ComputerDetails) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DuoSectionHeader("最近主机", "${hosts.size} 台 · 实时")
        Spacer(Modifier.height(8.dp))
        if (hosts.isEmpty()) {
            DuoEmpty("还没有主机", "用右上角 + 打开旧版主机管理添加/配对", Modifier.weight(1f))
            return@Column
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(hosts, key = { it.uuid.orEmpty() + it.name }) { computer ->
                val host = computer.toDuoHost()
                val selected = host.uuid == selectedUuid
                LumaFocusable(
                    onClick = { onSelectHost(computer) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .padding(vertical = 4.dp),
                    cornerRadius = 10.dp,
                ) { focused ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clip(LumaShapes.card)
                            .background(
                                when {
                                    selected -> Brush.horizontalGradient(
                                        listOf(
                                            LumaColors.Accent.copy(alpha = 0.13f),
                                            LumaColors.Surface1,
                                        ),
                                    )
                                    focused -> Brush.horizontalGradient(
                                        listOf(LumaColors.Surface3, LumaColors.Surface3),
                                    )
                                    else -> Brush.horizontalGradient(
                                        listOf(LumaColors.Surface1, LumaColors.Surface1),
                                    )
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) {
                                    LumaColors.Accent.copy(alpha = 0.38f)
                                } else {
                                    Color.Transparent
                                },
                                shape = LumaShapes.card,
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.duo_ic_monitor),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (host.online) LumaColors.Accent else LumaColors.Disabled,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (host.online) LumaColors.Success else LumaColors.Disabled,
                                        ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = host.name,
                                    style = LumaType.titleM,
                                    color = LumaColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${host.address} · ${if (host.paired) "已配对" else "未配对"}",
                                style = LumaType.caption,
                                color = LumaColors.Secondary,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = host.tag,
                            style = LumaType.micro.copy(fontWeight = FontWeight.Medium),
                            color = if (host.tag == "LAN") LumaColors.Tertiary else LumaColors.Signal,
                            modifier = Modifier
                                .clip(LumaShapes.chip)
                                .background(LumaColors.Surface3)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "右上角 ▶ 进入应用列表；+ 打开旧版主机管理添加/配对。",
            style = LumaType.caption,
            color = LumaColors.Tertiary,
        )
    }
}

@Composable
private fun DuoAppsPage(
    host: ComputerDetails?,
    apps: List<NvApp>,
    loading: Boolean,
    error: String?,
    onOpenApp: (NvApp) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DuoSectionHeader("全部应用", if (loading) "读取中" else "${apps.size} 个 · 实时")
        Spacer(Modifier.height(8.dp))
        when {
            host == null -> DuoEmpty("先在主机页选择一台主机", modifier = Modifier.weight(1f))
            loading -> DuoEmpty("正在读取主机应用列表…", modifier = Modifier.weight(1f))
            error != null -> DuoEmpty("读取失败：$error", modifier = Modifier.weight(1f))
            apps.isEmpty() -> DuoEmpty("主机没有可启动的应用", modifier = Modifier.weight(1f))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = { it.appId }) { app ->
                    val color = duoAppColors[
                        ((app.appId % duoAppColors.size) + duoAppColors.size) % duoAppColors.size
                    ]
                    val initial = app.appName.trim().firstOrNull()?.uppercase() ?: "?"
                    LumaFocusable(
                        onClick = { onOpenApp(app) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp),
                        cornerRadius = 10.dp,
                    ) { focused ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .clip(LumaShapes.card)
                                .background(if (focused) LumaColors.Surface3 else LumaColors.Surface1)
                                .padding(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(LumaShapes.control)
                                    .background(color),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = initial,
                                    style = LumaType.titleM.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF121216),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = app.appName,
                                style = LumaType.bodyM,
                                color = LumaColors.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuoRemotePage(
    remote: DuoRemoteRepository.Snapshot,
    onOpenLegacy: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DuoSectionHeader(
            "EasyTier",
            if (remote.usesDefaultTemplate) "内置模板" else "已配置",
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuoInfoPanel(
                modifier = Modifier.weight(1.35f),
                title = "网络",
                rows = listOf(
                    "网络名" to remote.networkName,
                    "密钥" to if (remote.hasSecret) "已设置 · 已遮罩" else "未设置",
                    "虚拟 IP" to "${remote.ipv4}/24",
                ),
            )
            DuoInfoPanel(
                modifier = Modifier.weight(1f),
                title = "链路",
                rows = listOf(
                    "Peer" to (remote.peers.firstOrNull() ?: "—"),
                    "Peer 数" to "${remote.peers.size}",
                    "监听" to "${remote.listeners.size} 个",
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LumaColors.Disabled),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "运行状态：未启动",
                style = LumaType.label,
                color = LumaColors.Secondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "M4 接入下屏启停",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "本页只读真实 TOML 配置；密钥不会显示或写入日志。PC 配置复制、二维码和手动添加虚拟 IP 在 M4 完成。",
            style = LumaType.caption,
            color = LumaColors.Tertiary,
        )
        Spacer(Modifier.weight(1f))
        LumaFocusable(
            onClick = onOpenLegacy,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            cornerRadius = 8.dp,
        ) { focused ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(LumaShapes.control)
                    .background(if (focused) LumaColors.Surface3 else LumaColors.Surface1)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.duo_ic_host),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LumaColors.Accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "打开旧版主机管理 / EasyTier",
                    style = LumaType.bodyM,
                    color = LumaColors.Text,
                )
            }
        }
    }
}

private data class DuoSettingRow(
    val label: String,
    val value: String,
    val field: DuoSettingsRepository.Field? = null,
    val detail: String? = null,
)

private data class DuoSettingsCategory(
    val name: String,
    val meta: String,
    val rows: List<DuoSettingRow>,
)

private fun settingsCategories(
    settings: DuoSettingsRepository.Snapshot,
    remote: DuoRemoteRepository.Snapshot,
    versionName: String,
    versionCode: Long,
): List<DuoSettingsCategory> = listOf(
    DuoSettingsCategory(
        name = "画面",
        meta = "下次串流生效",
        rows = listOf(
            DuoSettingRow("分辨率", settings.resolutionLabel, DuoSettingsRepository.Field.RESOLUTION),
            DuoSettingRow("帧率", "${settings.fps} Hz", DuoSettingsRepository.Field.FPS),
            DuoSettingRow("码率", "${settings.bitrateMbps} Mbps", DuoSettingsRepository.Field.BITRATE),
            DuoSettingRow("视频格式", settings.formatLabel, DuoSettingsRepository.Field.FORMAT),
            DuoSettingRow("HDR", if (settings.hdr) "开" else "关", DuoSettingsRepository.Field.HDR),
            DuoSettingRow(
                "动态码率",
                if (settings.adaptiveBitrate) "开" else "关",
                DuoSettingsRepository.Field.ADAPTIVE_BITRATE,
            ),
        ),
    ),
    DuoSettingsCategory(
        name = "音频",
        meta = "下次串流生效",
        rows = listOf(
            DuoSettingRow("声道", settings.audioLabel, DuoSettingsRepository.Field.AUDIO),
            DuoSettingRow("主机音频", "跟随 V+ 设置", detail = "M3 迁入控制台"),
        ),
    ),
    DuoSettingsCategory(
        name = "输入",
        meta = "直通优先",
        rows = listOf(
            DuoSettingRow("串流输入", "直通 PC", detail = "Return/Back 进入本地"),
            DuoSettingRow("震动", settings.rumbleLabel, DuoSettingsRepository.Field.RUMBLE),
            DuoSettingRow("本地键盘", "数字 + ASCII", detail = "系统 IME 触摸补充"),
        ),
    ),
    DuoSettingsCategory(
        name = "网络",
        meta = "EasyTier",
        rows = listOf(
            DuoSettingRow("网络名", remote.networkName, detail = "远程页只读"),
            DuoSettingRow("虚拟 IP", "${remote.ipv4}/24"),
            DuoSettingRow("运行状态", "未启动", detail = "M4 接入"),
        ),
    ),
    DuoSettingsCategory(
        name = "外观",
        meta = "设计基线",
        rows = listOf(
            DuoSettingRow("主题", "深色 · 锁定"),
            DuoSettingRow("强调色", "Lumen Gold", detail = "#F2C14E"),
            DuoSettingRow("焦点环", "白色 2px", detail = "金色表示选中"),
            DuoSettingRow("品牌标", "UUYC A1 全金", detail = "矢量直接缩放"),
        ),
    ),
    DuoSettingsCategory(
        name = "关于",
        meta = "LumaDuo",
        rows = listOf(
            DuoSettingRow("版本", versionName, detail = "versionCode $versionCode"),
            DuoSettingRow("许可证", "GPL-3.0"),
            DuoSettingRow("上游", "Moonlight V+ 12.15.17"),
            DuoSettingRow("主机端", "Foundation Sunshine"),
        ),
    ),
)

@Composable
private fun DuoSettingsPage(
    settings: DuoSettingsRepository.Snapshot,
    remote: DuoRemoteRepository.Snapshot,
    notice: String?,
    onAdjust: (DuoSettingsRepository.Field, Int) -> Unit,
    onNoticeExpired: () -> Unit,
) {
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(1_800)
            onNoticeExpired()
        }
    }
    var selected by rememberSaveable { mutableStateOf(0) }
    val categories = settingsCategories(
        settings,
        remote,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE.toLong(),
    )
    val selectedCategory = categories[selected.coerceIn(0, categories.lastIndex)]

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(108.dp)) {
            categories.forEachIndexed { index, category ->
                LumaFocusable(
                    onClick = { selected = index },
                    onFocused = { isFocused -> if (isFocused) selected = index },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(vertical = 2.dp),
                    cornerRadius = 8.dp,
                ) { focused ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(LumaShapes.control)
                            .background(
                                when {
                                    index == selected -> LumaColors.AccentSoft
                                    focused -> LumaColors.Surface3
                                    else -> Color.Transparent
                                },
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = category.name,
                            style = LumaType.bodyM,
                            color = if (index == selected) LumaColors.Accent else LumaColors.Secondary,
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(LumaShapes.card)
                .background(LumaColors.Surface1),
        ) {
            DuoSectionHeader(
                selectedCategory.name,
                notice ?: selectedCategory.meta,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            selectedCategory.rows.forEach { row ->
                DuoSettingRowView(row = row, onAdjust = onAdjust)
            }
        }
    }
}

@Composable
private fun DuoSettingRowView(
    row: DuoSettingRow,
    onAdjust: (DuoSettingsRepository.Field, Int) -> Unit,
) {
    val field = row.field
    LumaFocusable(
        onClick = { field?.let { onAdjust(it, 1) } },
        enabled = field != null,
        onNavigateKey = { key ->
            if (field == null) {
                false
            } else {
                when (key) {
                    Key.DirectionLeft -> {
                        onAdjust(field, -1)
                        true
                    }
                    Key.DirectionRight -> {
                        onAdjust(field, 1)
                        true
                    }
                    else -> false
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        cornerRadius = 8.dp,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.control)
                .background(if (focused) LumaColors.Surface3 else Color.Transparent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.label,
                    style = LumaType.bodyM,
                    color = if (field != null) LumaColors.Text else LumaColors.Secondary,
                )
                if (row.detail != null) {
                    Text(
                        text = row.detail,
                        style = LumaType.micro,
                        color = LumaColors.Tertiary,
                        maxLines = 1,
                    )
                }
            }
            if (field != null) {
                Text(
                    text = "‹",
                    style = LumaType.caption,
                    color = if (focused) LumaColors.Accent else LumaColors.Tertiary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = row.value,
                style = LumaType.bodyM.copy(fontWeight = FontWeight.Medium),
                color = if (field != null) LumaColors.Accent else LumaColors.Secondary,
                maxLines = 1,
            )
            if (field != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "›",
                    style = LumaType.caption,
                    color = if (focused) LumaColors.Accent else LumaColors.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun DuoSectionHeader(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = LumaType.label,
            color = LumaColors.Text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = meta,
            style = LumaType.micro,
            color = LumaColors.Tertiary,
        )
    }
}

@Composable
private fun DuoEmpty(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = LumaType.bodyM,
            color = LumaColors.Secondary,
        )
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = LumaType.caption,
                color = LumaColors.Tertiary,
            )
        }
    }
}

@Composable
private fun DuoInfoPanel(
    modifier: Modifier = Modifier,
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(
        modifier
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = LumaType.label,
            color = LumaColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        rows.forEach { (label, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = LumaType.caption,
                    color = LumaColors.Tertiary,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    text = value,
                    style = LumaType.caption,
                    color = LumaColors.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val duoAppColors = listOf(
    Color(0xFF7BA7C7),
    Color(0xFF9B9BA8),
    Color(0xFFF2C14E),
    Color(0xFF4CC38A),
    Color(0xFF5ED0FF),
    Color(0xFFA78BFA),
)
