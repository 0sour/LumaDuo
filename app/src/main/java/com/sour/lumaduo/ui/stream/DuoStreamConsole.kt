// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.stream

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.BuildConfig
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoRemoteController
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.preferences.PreferenceConfiguration
import com.sour.lumaduo.preferences.TouchModePreset
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaMetrics
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType
import com.sour.lumaduo.ui.shell.DuoConfirmPanel
import com.sour.lumaduo.ui.shell.DuoChip
import com.sour.lumaduo.ui.shell.DuoOverlay
import com.sour.lumaduo.ui.shell.DuoSliderRow
import com.sour.lumaduo.ui.shell.DuoTextButton
import com.sour.lumaduo.ui.shell.DuoSettingRow
import com.sour.lumaduo.ui.shell.DuoSectionHeader
import kotlinx.coroutines.delay

interface DuoStreamActions {
    fun setMode(mode: DuoStreamMode)
    fun exitStream()
    fun reconnect()
    fun disconnect()
    fun openRemote()
    fun networkTest()
    fun localKeyboard()
    fun hostKeyboard()
    fun sleepHost()
    fun unlockHost()
    fun setControllerMouse(enabled: Boolean)
    fun mouseMove(dx: Float, dy: Float)
    fun mouseButton(button: Int)
    fun mouseButtonDown(button: Int)
    fun mouseButtonUp(button: Int)

    /** Positive scrolls up, negative scrolls down. */
    fun mouseScroll(delta: Int)
    fun sendKey(key: String, modifiers: Set<String> = emptySet())
    fun sendShortcut(label: String)
    fun clearNotice()
}

@Composable
fun DuoStreamConsole(
    state: DuoStreamState,
    mode: DuoStreamMode,
    actions: DuoStreamActions,
    settingsRepository: DuoSettingsRepository,
    scene: DuoSettingsRepository.Snapshot,
    remoteRuntime: DuoRemoteController.State,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var tab by remember { mutableStateOf(DuoStreamTab.STATUS) }
    var pane by remember { mutableStateOf("console") }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmSleep by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var sceneState by remember { mutableStateOf(scene) }

    LaunchedEffect(scene) {
        sceneState = scene
    }
    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        notice = message
        delay(2_200L)
        actions.clearNotice()
        if (notice == message) notice = null
    }

    BackHandler(enabled = confirmExit || confirmDisconnect || confirmSleep) {
        when {
            confirmSleep -> confirmSleep = false
            confirmDisconnect -> confirmDisconnect = false
            else -> confirmExit = false
        }
    }

    fun changed() {
        sceneState = settingsRepository.read()
        refresh++
    }
    val refreshTick = refresh
    if (refreshTick < 0) return

    val local = mode == DuoStreamMode.LOCAL
    val consoleAlpha = if (local) 1f else 0.60f
    val initialFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(local) {
        if (local) {
            delay(80L)
            runCatching { initialFocus.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(LumaColors.Bg),
    ) {
        StreamHeader(
            state = state,
            mode = mode,
            onExit = {
                if (local) confirmExit = true else actions.setMode(DuoStreamMode.LOCAL)
            },
            onToggleMode = {
                actions.setMode(if (local) DuoStreamMode.PASSTHROUGH else DuoStreamMode.LOCAL)
            },
        )
        PaneSwitch(
            tools = pane == "tools",
            enabled = local,
            onConsole = { pane = "console" },
            onTools = { pane = "tools" },
        )
        if (pane == "tools" && local) {
            DuoStreamToolkit(
                modifier = Modifier.weight(1f),
                actions = actions,
            )
        } else {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!compact) {
                        StreamSummary(
                            state = state,
                            mode = mode,
                            onEnterLocal = { actions.setMode(DuoStreamMode.LOCAL) },
                            onResume = { actions.setMode(DuoStreamMode.PASSTHROUGH) },
                            modifier = Modifier.width(220.dp).fillMaxSize(),
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(LumaShapes.card)
                            .background(LumaColors.Surface1),
                    ) {
                        StreamTabRow(
                            selected = tab,
                            onSelect = { tab = it },
                            initialFocusRequester = initialFocus,
                        )
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            when (tab) {
                                DuoStreamTab.STATUS -> StatusTab(state)
                                DuoStreamTab.VIDEO -> VideoTab(
                                    state = state,
                                    scene = sceneState,
                                    settingsRepository = settingsRepository,
                                    onChanged = { changed() },
                                )
                                DuoStreamTab.AUDIO -> AudioTab(
                                    settingsRepository = settingsRepository,
                                    scene = sceneState,
                                    onChanged = { changed() },
                                    onNotice = { notice = it },
                                )
                                DuoStreamTab.INPUT -> InputTab(
                                    mode = mode,
                                    scene = sceneState,
                                    settingsRepository = settingsRepository,
                                    controllerMouse = state.controllerMouse,
                                    onChanged = { changed() },
                                    onEnterLocal = { actions.setMode(DuoStreamMode.LOCAL) },
                                    onResume = { actions.setMode(DuoStreamMode.PASSTHROUGH) },
                                    onToggleControllerMouse = {
                                        actions.setControllerMouse(!state.controllerMouse)
                                    },
                                )
                                DuoStreamTab.CONNECTION -> ConnectionTab(
                                    state = state,
                                    remoteRuntime = remoteRuntime,
                                    onReconnect = actions::reconnect,
                                    onDisconnect = { confirmDisconnect = true },
                                    onExit = { confirmExit = true },
                                    onSleep = { confirmSleep = true },
                                    onOpenRemote = actions::openRemote,
                                    onNetworkTest = actions::networkTest,
                                )
                            }
                        }
                    }
                }
                if (!local) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LumaColors.Bg.copy(alpha = 1f - consoleAlpha)),
                    )
                }
            }
        }
        StreamModeBar(
            state = state,
            mode = mode,
            notice = notice,
            onEnterLocal = { actions.setMode(DuoStreamMode.LOCAL) },
            onResume = { actions.setMode(DuoStreamMode.PASSTHROUGH) },
        )
    }

    if (confirmExit) {
        DuoOverlay(onDismiss = { confirmExit = false }) {
            DuoConfirmPanel(
                title = "退出串流？",
                body = "连接会断开，上屏回到主机预览。游戏进度由 PC 保留。",
                confirmLabel = "退出",
                onCancel = { confirmExit = false },
                onConfirm = {
                    confirmExit = false
                    actions.exitStream()
                },
            )
        }
    }
    if (confirmDisconnect) {
        DuoOverlay(onDismiss = { confirmDisconnect = false }) {
            DuoConfirmPanel(
                title = "断开连接？",
                body = "只断开本次串流，不退出 LumaDuo。",
                confirmLabel = "断开",
                onCancel = { confirmDisconnect = false },
                onConfirm = {
                    confirmDisconnect = false
                    actions.disconnect()
                },
                danger = false,
            )
        }
    }
    if (confirmSleep) {
        DuoOverlay(onDismiss = { confirmSleep = false }) {
            DuoConfirmPanel(
                title = "让主机休眠？",
                body = "将通过 Win+X → U → S 发送休眠组合键，主机随即断开。",
                confirmLabel = "休眠",
                onCancel = { confirmSleep = false },
                onConfirm = {
                    confirmSleep = false
                    actions.sleepHost()
                },
            )
        }
    }
}

@Composable
private fun StreamHeader(
    state: DuoStreamState,
    mode: DuoStreamMode,
    onExit: () -> Unit,
    onToggleMode: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(LumaColors.Surface1)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(icon = R.drawable.duo_ic_chevron_right, label = "本地菜单", rotate = 180f, onClick = onExit)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.hostName,
                style = LumaType.titleM,
                color = LumaColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${state.statusLabel} · ${state.configuredLabel} · ${state.latencyLabel}",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
                maxLines = 1,
            )
        }
        LumaFocusable(
            onClick = onToggleMode,
            modifier = Modifier.height(28.dp),
            cornerRadius = 14.dp,
        ) { focused ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(LumaShapes.chip)
                    .background(if (focused) LumaColors.Surface3 else LumaColors.Bg)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (mode == DuoStreamMode.LOCAL) LumaColors.Accent else LumaColors.Signal),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (mode == DuoStreamMode.LOCAL) "本地" else "直通",
                    style = LumaType.micro,
                    color = if (mode == DuoStreamMode.LOCAL) LumaColors.Accent else LumaColors.Signal,
                )
            }
        }
    }
}

@Composable
private fun PaneSwitch(
    tools: Boolean,
    enabled: Boolean,
    onConsole: () -> Unit,
    onTools: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(LumaColors.Surface1)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaneTab("控制台", !tools, enabled, onConsole)
        Spacer(Modifier.width(6.dp))
        PaneTab("工具", tools, enabled, onTools)
        Spacer(Modifier.weight(1f))
        Text("Y 切换 · Return 本地", style = LumaType.micro, color = LumaColors.Tertiary)
    }
}

@Composable
private fun PaneTab(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    LumaFocusable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(26.dp).width(72.dp),
        cornerRadius = 6.dp,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.control)
                .background(
                    when {
                        active -> LumaColors.AccentSoft
                        focused -> LumaColors.Surface3
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = LumaType.bodyM,
                color = if (active) LumaColors.Accent else LumaColors.Secondary,
            )
        }
    }
}

@Composable
private fun StreamSummary(
    state: DuoStreamState,
    mode: DuoStreamMode,
    onEnterLocal: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (state.connected) LumaColors.Success else LumaColors.Disabled),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.connected) "串流中" else "未连接",
                style = LumaType.label,
                color = LumaColors.Text,
            )
            Spacer(Modifier.weight(1f))
            Text(state.durationLabel, style = LumaType.micro, color = LumaColors.Tertiary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            state.hostName,
            style = LumaType.titleM,
            color = LumaColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(state.address, style = LumaType.micro, color = LumaColors.Tertiary)
        Spacer(Modifier.height(10.dp))
        SummaryLine("分辨率", state.resolution)
        SummaryLine("帧率", state.configuredFps?.let { "$it Hz" } ?: "—")
        SummaryLine("码率", state.actualBitrateMbps?.let { "%.1f Mbps".format(it) } ?: "—")
        SummaryLine("HDR", if (state.hdr) "开" else "关")
        SummaryLine("输入", if (mode == DuoStreamMode.LOCAL) "本地" else "直通电脑")
        Spacer(Modifier.weight(1f))
        DuoTextButton(
            if (mode == DuoStreamMode.LOCAL) "返回游戏" else "本地菜单",
            onClick = if (mode == DuoStreamMode.LOCAL) onResume else onEnterLocal,
            accent = mode == DuoStreamMode.LOCAL,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(label, style = LumaType.micro, color = LumaColors.Tertiary, modifier = Modifier.width(48.dp))
        Text(value, style = LumaType.micro, color = LumaColors.Secondary, maxLines = 1)
    }
}

@Composable
private fun StreamTabRow(
    selected: DuoStreamTab,
    onSelect: (DuoStreamTab) -> Unit,
    initialFocusRequester: FocusRequester? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuoStreamTab.entries.forEach { tab ->
            LumaFocusable(
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp),
                cornerRadius = 6.dp,
                focusRequester = if (tab == DuoStreamTab.STATUS) initialFocusRequester else null,
            ) { focused ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(if (focused) LumaColors.Surface3 else Color.Transparent),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        tab.label,
                        style = LumaType.caption,
                        color = if (selected == tab) LumaColors.Accent else LumaColors.Secondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(if (selected == tab) LumaColors.Accent else Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTab(state: DuoStreamState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("STREAM HEALTH", style = LumaType.micro, color = LumaColors.Tertiary)
            Text("串流状态", style = LumaType.label, color = LumaColors.Text)
        }
        DuoChip(
            if (state.connected) "良好" else "未连接",
            if (state.connected) LumaColors.Success else LumaColors.Disabled,
        )
        Spacer(Modifier.width(6.dp))
        Text(state.durationLabel, style = LumaType.micro, color = LumaColors.Tertiary)
        Spacer(Modifier.width(6.dp))
        DuoChip("只读")
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Kpi("帧率", state.fpsLabel, "接收", Modifier.weight(1f))
        Kpi("网络延迟", state.latencyLabel, "RTT ${state.rttMs?.let { "%.0f".format(it) } ?: "—"} ms", Modifier.weight(1f))
        Kpi("实测码率", state.bitrateLabel, "设置 ${state.configuredBitrateMbps ?: "—"} Mbps", Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    TrendCard(state)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        InfoCell(
            R.drawable.duo_ic_monitor,
            "画面",
            state.configuredLabel,
            "${state.codec} · HDR ${if (state.hdr) "开" else "关"}",
            Modifier.weight(1f),
        )
        InfoCell(
            R.drawable.duo_ic_wifi,
            "链路",
            state.networkLabel,
            "丢包 ${state.packetLossPercent?.let { "%.1f%%".format(it) } ?: "—"}",
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        InfoCell(
            R.drawable.duo_ic_gamepad,
            "本机",
            state.temperatureC?.let { "$it°C" } ?: state.thermalLabel,
            "电量 ${state.batteryPercent?.let { "$it%" } ?: "—"}",
            Modifier.weight(1f),
        )
        InfoCell(
            R.drawable.duo_ic_host,
            "主机",
            state.hostCpu ?: "未提供",
            state.hostGpu ?: "宿主负载未接入",
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "只读 · 客户端实测；主机负载未接入时显示“未提供”，不伪造。",
        style = LumaType.micro,
        color = LumaColors.Tertiary,
    )
}

@Composable
private fun Kpi(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(52.dp)
            .clip(LumaShapes.control)
            .background(LumaColors.Bg)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(label, style = LumaType.micro, color = LumaColors.Tertiary)
        Text(
            value,
            style = LumaType.titleM.copy(fontFamily = FontFamily.Monospace),
            color = LumaColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(sub, style = LumaType.micro, color = LumaColors.Tertiary, maxLines = 1)
    }
}

@Composable
private fun TrendCard(state: DuoStreamState) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(LumaShapes.control)
            .background(LumaColors.Bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row {
            Text("最近 60 秒", style = LumaType.micro, color = LumaColors.Tertiary, modifier = Modifier.weight(1f))
            Text("延迟 / 码率", style = LumaType.micro, color = LumaColors.Tertiary)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val latency = state.latencyTrend
            val bitrate = state.bitrateTrend
            if (latency.size < 2 && bitrate.size < 2) return@Canvas
            fun drawTrend(values: List<Float>, color: Color) {
                if (values.size < 2) return
                val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
                val step = size.width / (values.size - 1)
                var prev = Offset(0f, size.height - (values[0] / max) * size.height)
                values.drop(1).forEachIndexed { index, value ->
                    val next = Offset((index + 1) * step, size.height - (value / max) * size.height)
                    drawLine(color, prev, next, strokeWidth = 2f, cap = StrokeCap.Round)
                    prev = next
                }
            }
            drawTrend(latency, LumaColors.Accent)
            drawTrend(bitrate, LumaColors.Signal)
        }
    }
}

@Composable
private fun InfoCell(
    icon: Int,
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(44.dp)
            .clip(LumaShapes.control)
            .background(LumaColors.Bg)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LumaColors.Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, style = LumaType.micro, color = LumaColors.Tertiary)
            Text(value, style = LumaType.caption, color = LumaColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = LumaType.micro, color = LumaColors.Tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VideoTab(
    state: DuoStreamState,
    scene: DuoSettingsRepository.Snapshot,
    settingsRepository: DuoSettingsRepository,
    onChanged: () -> Unit,
) {
    DuoSettingRow(
        "分辨率",
        scene.resolutionLabel,
        requiresReconnect = true,
        onActivate = {
            val options = listOf(
                Triple("1280x720", 1280, 720),
                Triple("1920x1080", 1920, 1080),
                Triple("2560x1440", 2560, 1440),
                Triple("native", 1920, 1080),
            )
            val index = if (scene.isNativeResolution) {
                options.lastIndex
            } else {
                options.indexOfFirst { it.first == scene.resolutionValue }.takeIf { it >= 0 } ?: 0
            }
            val next = options[(index + 1) % options.size]
            settingsRepository.setResolution(next.second, next.third, next.first == "native")
            onChanged()
        },
    )
    DuoSettingRow(
        "帧率",
        "${scene.fps} Hz",
        requiresReconnect = true,
        onActivate = {
            val options = listOf(30, 60, 90, 120)
            val index = options.indexOf(scene.fps).coerceAtLeast(0)
            settingsRepository.setFps(options[(index + 1) % options.size])
            onChanged()
        },
    )
    val screenMode = settingsRepository.getString("list_screen_combination_mode", "-1")
    DuoSettingRow(
        "屏幕模式",
        screenModeLabel(screenMode),
        detail = "主机默认 / 主屏 / 副屏 / 独占",
        requiresReconnect = true,
        onActivate = {
            val options = listOf("-1", "0", "1", "2", "4", "3")
            val index = options.indexOf(screenMode).coerceAtLeast(0)
            settingsRepository.putString(
                "list_screen_combination_mode",
                options[(index + 1) % options.size],
            )
            onChanged()
        },
    )
    DuoSliderRow(
        title = "码率",
        valueLabel = "${scene.bitrateMbps} Mbps",
        value = scene.bitrateMbps.toFloat(),
        range = 5f..150f,
        steps = 28,
        onValueChange = { settingsRepository.setBitrateMbps(it.toInt()) },
        onValueChangeFinished = onChanged,
    )
    DuoSettingRow(
        "智能码率",
        if (scene.adaptiveBitrate) "开" else "关",
        detail = "弱网自动调整",
        isSwitch = true,
        switchOn = scene.adaptiveBitrate,
        onActivate = {
            settingsRepository.setAdaptive(!scene.adaptiveBitrate)
            onChanged()
        },
    )
    val scale = settingsRepository.getInt("seekbar_resolutions_scale", 100)
    DuoSliderRow(
        title = "显示缩放",
        valueLabel = "$scale%",
        detail = "主机渲染缩放",
        value = scale.toFloat(),
        range = 50f..200f,
        steps = 5,
        onValueChange = { settingsRepository.putInt("seekbar_resolutions_scale", it.toInt()) },
        onValueChangeFinished = onChanged,
    )
    DuoSettingRow(
        "HDR",
        if (scene.hdr) "开" else "关",
        detail = "真实 HDR 需重连；工具内 HDR 只切 Windows",
        isSwitch = true,
        switchOn = scene.hdr,
        onActivate = {
            settingsRepository.setHdr(!scene.hdr)
            onChanged()
        },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        val overlay = settingsRepository.getBoolean("checkbox_enable_perf_overlay", false)
        DuoSettingRow(
            "性能浮层",
            if (overlay) "开" else "隐藏",
            detail = "控制台显示实时统计",
            isSwitch = true,
            switchOn = overlay,
            onActivate = {
                settingsRepository.putBoolean("checkbox_enable_perf_overlay", !overlay)
                onChanged()
            },
        )
    }
}

@Composable
private fun AudioTab(
    settingsRepository: DuoSettingsRepository,
    scene: DuoSettingsRepository.Snapshot,
    onChanged: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var volume by remember { mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    var muted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false
            } else {
                false
            }
        )
    }
    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    DuoSliderRow(
        title = "本机音量",
        valueLabel = "${(volume * 100 / maxVolume.coerceAtLeast(1))}%",
        value = volume.toFloat(),
        range = 0f..maxVolume.toFloat(),
        steps = 0,
        onValueChange = {
            volume = it.toInt()
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        },
    )
    DuoSettingRow(
        "静音",
        if (muted) "开" else "关",
        detail = "保留视频，关闭本机音频",
        isSwitch = true,
        switchOn = muted,
        onActivate = {
            val next = !muted
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (next) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0,
            )
            muted = next
        },
    )
    DuoSettingRow(
        "声道",
        scene.audioLabel,
        detail = "切换提示“应用并重连”",
        requiresReconnect = true,
        onActivate = {
            val options = listOf("2", "51", "71", "714")
            val current = settingsRepository.getString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "2")
            val index = options.indexOf(current).coerceAtLeast(0)
            settingsRepository.setAudio(options[(index + 1) % options.size])
            onChanged()
        },
    )
    DuoSettingRow(
        "音频直通",
        if (settingsRepository.getBoolean("checkbox_enable_audio_passthrough", false)) "开" else "关",
        detail = "AC3 / EAC3 / Opus",
        isSwitch = true,
        switchOn = settingsRepository.getBoolean("checkbox_enable_audio_passthrough", false),
        onActivate = {
            val current = settingsRepository.getBoolean("checkbox_enable_audio_passthrough", false)
            settingsRepository.putBoolean("checkbox_enable_audio_passthrough", !current)
            onChanged()
        },
    )
    DuoSettingRow(
        "麦克风",
        if (settingsRepository.getBoolean("checkbox_enable_mic", false)) "开" else "关",
        detail = "设备与码率",
        isSwitch = true,
        switchOn = settingsRepository.getBoolean("checkbox_enable_mic", false),
        onActivate = {
            val current = settingsRepository.getBoolean("checkbox_enable_mic", false)
            settingsRepository.putBoolean("checkbox_enable_mic", !current)
            onChanged()
        },
    )
}

@Composable
private fun InputTab(
    mode: DuoStreamMode,
    scene: DuoSettingsRepository.Snapshot,
    settingsRepository: DuoSettingsRepository,
    controllerMouse: Boolean,
    onChanged: () -> Unit,
    onEnterLocal: () -> Unit,
    onResume: () -> Unit,
    onToggleControllerMouse: () -> Unit,
) {
    DuoSettingRow(
        "输入模式",
        if (mode == DuoStreamMode.LOCAL) "本地菜单" else "直通电脑",
        detail = if (mode == DuoStreamMode.LOCAL) "焦点在下屏" else "手柄控制 PC",
        onActivate = if (mode == DuoStreamMode.LOCAL) onResume else onEnterLocal,
    )
    DuoSettingRow(
        "触摸模式",
        touchLabel(settingsRepository.touchPreset()),
        detail = "下屏触摸映射",
        onActivate = {
            val options = TouchModePreset.entries
            val current = settingsRepository.touchPreset()
            val index = options.indexOf(current).coerceAtLeast(0)
            settingsRepository.setTouchPreset(options[(index + 1) % options.size])
            onChanged()
        },
    )
    val speed = settingsRepository.getInt("seekbar_hardware_touchpad_pointer_speed", 100)
    DuoSliderRow(
        title = "触控板速度",
        valueLabel = touchpadSpeedLabel(speed),
        detail = "相对鼠标灵敏度",
        value = speed.toFloat(),
        range = 50f..300f,
        steps = 49,
        onValueChange = { settingsRepository.putInt("seekbar_hardware_touchpad_pointer_speed", it.toInt()) },
        onValueChangeFinished = onChanged,
    )
    val gyro = settingsRepository.getBoolean("gyro_to_right_stick", false)
    DuoSettingRow(
        "陀螺仪",
        if (gyro) "开" else "关",
        detail = "映射右摇杆或鼠标",
        isSwitch = true,
        switchOn = gyro,
        onActivate = {
            settingsRepository.putBoolean("gyro_to_right_stick", !gyro)
            onChanged()
        },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        val osc = settingsRepository.getBoolean("checkbox_show_onscreen_controls", false)
        DuoSettingRow("虚拟手柄", if (osc) "开" else "关", isSwitch = true, switchOn = osc, onActivate = {
            settingsRepository.putBoolean("checkbox_show_onscreen_controls", !osc)
            onChanged()
        })
    }
    DuoSettingRow(
        "手柄鼠标模拟",
        if (controllerMouse) "开" else "关",
        detail = "用摇杆控制 PC 鼠标；速度在设置中调整",
        isSwitch = true,
        switchOn = controllerMouse,
        onActivate = onToggleControllerMouse,
    )
    DuoSettingRow(
        "震动",
        scene.rumbleLabel,
        detail = "智能 / 仅本机 / 仅手柄",
        onActivate = {
            val options = listOf("smart", "device", "controller")
            val current = settingsRepository.getString(PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING, "controller")
            val index = options.indexOf(current).coerceAtLeast(0)
            settingsRepository.setRumble(options[(index + 1) % options.size])
            onChanged()
        },
    )
}

@Composable
private fun ConnectionTab(
    state: DuoStreamState,
    remoteRuntime: DuoRemoteController.State,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onExit: () -> Unit,
    onSleep: () -> Unit,
    onOpenRemote: () -> Unit,
    onNetworkTest: () -> Unit,
) {
    DuoSettingRow("主机", state.hostName, detail = state.statusLabel, enabled = false)
    DuoSettingRow("地址", state.address, detail = state.networkLabel, enabled = false)
    DuoSettingRow("配对", "已配对", enabled = false)
    DuoSettingRow("延迟 / RTT", "${state.latencyLabel} / ${state.rttMs?.let { "%.0f ms".format(it) } ?: "—"}", enabled = false)
    DuoSettingRow(
        "丢包 / 带宽",
        "${state.packetLossPercent?.let { "%.1f%%".format(it) } ?: "—"} / " +
            (state.actualBitrateMbps?.let { "%.1f Mbps".format(it) } ?: "—"),
        enabled = false,
    )
    DuoSettingRow("EasyTier", remoteRuntime.virtualIp ?: state.easyTierLabel, detail = "虚拟网络", isSubpage = true, onActivate = onOpenRemote)
    DuoSettingRow(
        "网络测试",
        if (state.networkTestRunning) "测试中…" else state.networkTestLabel ?: "重新测速",
        detail = state.networkTestDetail ?: "TCP 47984/47989/47990",
        isSubpage = true,
        enabled = !state.networkTestRunning,
        onActivate = onNetworkTest,
    )
    DuoSettingRow("重新连接", "热切换失败时使用", isSubpage = true, onActivate = onReconnect)
    DuoSettingRow("休眠主机", "确认", detail = "Win+X → U → S", isSubpage = true, onActivate = onSleep)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DuoTextButton("断开", onClick = onDisconnect, modifier = Modifier.weight(1f))
        DuoTextButton("退出串流", onClick = onExit, danger = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StreamModeBar(
    state: DuoStreamState,
    mode: DuoStreamMode,
    notice: String?,
    onEnterLocal: () -> Unit,
    onResume: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(LumaColors.Surface1)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (mode == DuoStreamMode.LOCAL) LumaColors.Accent else LumaColors.Signal),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "手柄：${if (mode == DuoStreamMode.LOCAL) "本地" else "直通电脑"}",
            style = LumaType.micro,
            color = LumaColors.Secondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            notice ?: if (mode == DuoStreamMode.LOCAL) "A 返回直通 · B 确认" else "Return / 触摸 进入本地",
            style = LumaType.micro,
            color = LumaColors.Tertiary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (mode == DuoStreamMode.LOCAL) "A 返回" else "RETURN 本地",
            style = LumaType.micro,
            color = LumaColors.Tertiary,
        )
    }
}

@Composable
private fun IconButton(
    icon: Int,
    label: String,
    rotate: Float = 0f,
    onClick: () -> Unit,
) {
    LumaFocusable(
        onClick = onClick,
        modifier = Modifier.size(LumaMetrics.TouchTarget),
        cornerRadius = 8.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = LumaColors.Secondary,
                modifier = Modifier
                    .size(18.dp)
                    .then(if (rotate != 0f) Modifier.rotate(rotate) else Modifier),
            )
        }
    }
}

private fun touchLabel(preset: TouchModePreset): String = when (preset) {
    TouchModePreset.ENHANCED -> "增强"
    TouchModePreset.CLASSIC -> "经典"
    TouchModePreset.TRACKPAD -> "触控板"
    TouchModePreset.NATIVE -> "原生"
}

private fun screenModeLabel(value: String): String = when (value) {
    "0" -> "不操作"
    "1" -> "自动激活"
    "2" -> "主屏"
    "3" -> "独占"
    "4" -> "副屏"
    else -> "主机默认"
}

/** 80 -> "0.8×", 100 -> "1.0×". */
private fun touchpadSpeedLabel(speed: Int): String = "${speed / 100}.${(speed % 100) / 10}×"
