// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.BuildConfig
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoRemoteController
import com.sour.lumaduo.duo.DuoRemoteRepository
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.preferences.BackgroundStreamBehavior
import com.sour.lumaduo.preferences.BackgroundStreamBehaviorPolicy
import com.sour.lumaduo.preferences.PreferenceConfiguration
import com.sour.lumaduo.preferences.QuitBehavior
import com.sour.lumaduo.preferences.TouchModePreset
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType

private enum class DuoSetCat(
    val label: String,
    val icon: Int,
) {
    VIDEO("画面", R.drawable.duo_ic_monitor),
    AUDIO("音频", R.drawable.duo_ic_volume),
    INPUT("输入", R.drawable.duo_ic_gamepad),
    NETWORK("网络", R.drawable.duo_ic_globe),
    HOST("主机", R.drawable.duo_ic_host),
    INTERFACE("界面", R.drawable.duo_ic_sliders),
    ABOUT("关于", R.drawable.duo_ic_apps),
}

private data class ChoiceOption(val value: String, val label: String)
private data class ChoiceRequest(
    val title: String,
    val options: List<ChoiceOption>,
    val current: String,
    val onSelect: (String) -> Unit,
)

@Composable
fun DuoSettingsPage(
    repository: DuoSettingsRepository,
    scene: DuoSettingsRepository.Snapshot,
    remote: DuoRemoteRepository.Snapshot,
    runtime: DuoRemoteController.State,
    onSceneChanged: (DuoSettingsRepository.Snapshot) -> Unit,
    onOpenRemote: () -> Unit,
    onOpenLegacy: () -> Unit,
    onOpenNotices: () -> Unit,
    onAddHost: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenConsolePreview: () -> Unit,
    backBlocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var subpage by rememberSaveable { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<ChoiceRequest?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val category = DuoSetCat.entries[categoryIndex.coerceIn(0, DuoSetCat.entries.lastIndex)]
    val config = repository.sceneConfig()

    BackHandler(enabled = !backBlocked && (choice != null || subpage != null)) {
        if (choice != null) choice = null else subpage = null
    }

    fun changed() {
        version++
        onSceneChanged(repository.read())
    }

    fun choose(
        title: String,
        options: List<ChoiceOption>,
        current: String,
        onSelect: (String) -> Unit,
    ) {
        choice = ChoiceRequest(title, options, current, onSelect)
    }

    Row(
        modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(104.dp).fillMaxSize()) {
            DuoSetCat.entries.forEachIndexed { index, cat ->
                LumaFocusable(
                    onClick = {
                        categoryIndex = index
                        subpage = null
                    },
                    onFocused = { focused ->
                        if (focused && categoryIndex != index) {
                            categoryIndex = index
                            subpage = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(vertical = 2.dp),
                    cornerRadius = 8.dp,
                ) { focused ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clip(LumaShapes.control)
                            .background(
                                when {
                                    index == categoryIndex -> LumaColors.AccentSoft
                                    focused -> LumaColors.Surface3
                                    else -> Color.Transparent
                                },
                            )
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(cat.icon),
                            contentDescription = null,
                            tint = if (index == categoryIndex) LumaColors.Accent else LumaColors.Secondary,
                            modifier = Modifier.width(18.dp).height(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cat.label,
                            style = LumaType.bodyM,
                            color = if (index == categoryIndex) LumaColors.Accent else LumaColors.Secondary,
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
            if (subpage == null) {
                DuoSectionHeader(
                    title = category.label,
                    meta = "即时保存",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    when (category) {
                        DuoSetCat.VIDEO -> VideoParent(
                            scene = scene,
                            config = config,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.AUDIO -> AudioParent(
                            scene = scene,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.INPUT -> InputParent(onSubpage = { subpage = it })
                        DuoSetCat.NETWORK -> NetworkParent(
                            remote = remote,
                            runtime = runtime,
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onOpenRemote = onOpenRemote,
                            onAddHost = onAddHost,
                            onNotice = onNotice,
                        )
                        DuoSetCat.HOST -> HostParent(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.INTERFACE -> InterfaceParent(onSubpage = { subpage = it })
                        DuoSetCat.ABOUT -> AboutParent(
                            onOpenLegacy = onOpenLegacy,
                            onOpenNotices = onOpenNotices,
                            onNotice = onNotice,
                            onOpenConsolePreview = onOpenConsolePreview,
                        )
                    }
                }
            } else {
                SubpageHeader(
                    title = subpageTitle(subpage),
                    onBack = { subpage = null },
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    when (subpage) {
                        "quality" -> QualityPage(
                            scene = scene,
                            repository = repository,
                            onChanged = { changed() },
                            onChoose = ::choose,
                        )
                        "display" -> DisplayPage(
                            repository = repository,
                            version = version,
                            config = config,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "overlay" -> OverlayPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "experimental" -> ExperimentalPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                            onOpenLegacy = onOpenLegacy,
                        )
                        "audioOutput" -> AudioOutputPage(
                            scene = scene,
                            repository = repository,
                            onChanged = { changed() },
                            onChoose = ::choose,
                        )
                        "audioPass" -> AudioPassPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "mic" -> MicPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "gamepad" -> GamepadPage(
                            scene = scene,
                            repository = repository,
                            onChanged = { changed() },
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "touch" -> TouchPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "keyboard" -> KeyboardPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "osc" -> OscPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "gyro" -> GyroPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                        )
                        "host" -> HostBehaviorPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                        )
                        "console" -> ConsoleSettingsPage()
                        else -> Unit
                    }
                }
            }
        }
    }

    choice?.let { request ->
        DuoChoiceOverlay(
            request = request,
            onDismiss = { choice = null },
            onSelect = {
                request.onSelect(it)
                choice = null
            },
        )
    }
}

private fun subpageTitle(id: String?): String = when (id) {
    "quality" -> "串流画质"
    "display" -> "显示与布局"
    "overlay" -> "性能浮层"
    "experimental" -> "实验性"
    "audioOutput" -> "音频输出"
    "audioPass" -> "音频直通"
    "mic" -> "麦克风"
    "gamepad" -> "手柄"
    "touch" -> "触控"
    "keyboard" -> "键盘与鼠标"
    "osc" -> "虚拟手柄"
    "gyro" -> "体感"
    "host" -> "主机行为"
    "console" -> "控制台"
    else -> "设置"
}

@Composable
private fun SubpageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LumaFocusable(
            onClick = onBack,
            modifier = Modifier.width(30.dp).height(26.dp),
            cornerRadius = 6.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.duo_ic_chevron_right),
                    contentDescription = "返回",
                    tint = LumaColors.Secondary,
                    modifier = Modifier
                        .width(16.dp)
                        .height(16.dp)
                        .rotate(180f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "设置 / $title",
            style = LumaType.label,
            color = LumaColors.Text,
        )
    }
}

@Composable
private fun VideoParent(
    scene: DuoSettingsRepository.Snapshot,
    config: PreferenceConfiguration,
    onSubpage: (String) -> Unit,
) {
    SubpageEntry("串流画质", "${scene.resolutionLabel} · ${scene.fps} Hz", onSubpage, "quality")
    SubpageEntry(
        "显示与布局",
        if (config.stretchVideo) "拉伸 · ${prefPositionLabel(config.screenPosition.name)}" else "适应 · 居中",
        onSubpage,
        "display",
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        SubpageEntry(
            "性能浮层",
            if (config.enablePerfOverlay) "开 · ${config.perfOverlayStyle.name.lowercase()}" else "关",
            onSubpage,
            "overlay",
        )
        SubpageEntry("实验性", "帧生成 · PyroWave · 高级", onSubpage, "experimental")
    }
}

@Composable
private fun AudioParent(
    scene: DuoSettingsRepository.Snapshot,
    onSubpage: (String) -> Unit,
) {
    SubpageEntry("输出", scene.audioLabel, onSubpage, "audioOutput")
    SubpageEntry("直通", "AC3 / EAC3 / Opus", onSubpage, "audioPass")
    SubpageEntry("麦克风", "设备 / 码率 / 增益", onSubpage, "mic")
}

@Composable
private fun InputParent(onSubpage: (String) -> Unit) {
    SubpageEntry("手柄", "死区 · 震动 · 多手柄", onSubpage, "gamepad")
    SubpageEntry("触控", "触摸模式 · 速度 · 预设", onSubpage, "touch")
    SubpageEntry("键盘与鼠标", "ESC 菜单 · IME · 鼠标修复", onSubpage, "keyboard")
    if (!BuildConfig.MINIMAL_EDITION) {
        SubpageEntry("虚拟手柄", "布局 · 透明度 · 重置", onSubpage, "osc")
    }
    SubpageEntry("体感", "陀螺仪 · 回退 · 灵敏度", onSubpage, "gyro")
}

@Composable
private fun NetworkParent(
    remote: DuoRemoteRepository.Snapshot,
    runtime: DuoRemoteController.State,
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onOpenRemote: () -> Unit,
    onAddHost: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val stun = repository.getBoolean("checkbox_enable_stun", false)
    DuoSettingRow(
        title = "EasyTier",
        value = when (runtime.phase) {
            DuoRemoteController.Phase.CONNECTED -> "已连接"
            DuoRemoteController.Phase.STARTING -> "启动中"
            DuoRemoteController.Phase.NEED_PERMISSION -> "待授权"
            DuoRemoteController.Phase.ERROR -> "错误"
            else -> "未启动"
        },
        detail = "${remote.networkName} · ${runtime.virtualIp ?: "${remote.ipv4}/24"}",
        isSubpage = true,
        onActivate = onOpenRemote,
    )
    DuoSettingRow(
        title = "STUN",
        value = if (stun) "开" else "关",
        detail = "解析主机公网地址",
        isSwitch = true,
        switchOn = stun,
        onActivate = {
            repository.putBoolean("checkbox_enable_stun", !stun)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "网络测试",
        value = "选择主机",
        detail = "延迟 / 抖动 / 丢包 / 建议码率",
        isSubpage = true,
        onActivate = { onNotice("在主机详情或串流连接页发起网络测试") },
    )
    DuoSettingRow(
        title = "手动添加主机",
        value = "10.0.0.2",
        detail = "局域网或 EasyTier 虚拟 IP",
        isSubpage = true,
        onActivate = onAddHost,
    )
    DuoSettingRow(
        title = "IPv6",
        value = "按主机启用",
        detail = "在主机详情的高级项中切换",
        enabled = false,
    )
}

@Composable
private fun HostParent(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onSubpage: (String) -> Unit,
) {
    val sops = repository.getBoolean("checkbox_enable_sops", true)
    val clipText = repository.getBoolean("checkbox_clipboard_sync_text", false)
    val clipImage = repository.getBoolean("checkbox_clipboard_sync_image", false)
    DuoSettingRow(
        title = "主机行为",
        value = "退出 / 后台 / 屏幕",
        isSubpage = true,
        onActivate = { onSubpage("host") },
    )
    DuoSettingRow(
        title = "主机优化 SOPS",
        value = if (sops) "开" else "关",
        isSwitch = true,
        switchOn = sops,
        onActivate = {
            repository.putBoolean("checkbox_enable_sops", !sops)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "剪贴板文本",
        value = if (clipText) "开" else "关",
        detail = "保留 V+ 剪贴板同步，不做文件传输",
        isSwitch = true,
        switchOn = clipText,
        onActivate = {
            repository.putBoolean("checkbox_clipboard_sync_text", !clipText)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "剪贴板图片",
        value = if (clipImage) "开" else "关",
        isSwitch = true,
        switchOn = clipImage,
        onActivate = {
            repository.putBoolean("checkbox_clipboard_sync_image", !clipImage)
            onRefresh()
        },
    )
}

@Composable
private fun InterfaceParent(onSubpage: (String) -> Unit) {
    DuoSettingRow("语言", "简体中文", detail = "MVP 锁定；多语言不在范围", enabled = false)
    DuoSettingRow("主题", "深色 · Lumen Gold", detail = "品牌主题锁定", enabled = false)
    SubpageEntry("控制台", "状态页指标 · 不透明度说明", onSubpage, "console")
    DuoSettingRow("动画", "标准", detail = "焦点 120ms · 页面 180ms", enabled = false)
}

@Composable
private fun AboutParent(
    onOpenLegacy: () -> Unit,
    onOpenNotices: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenConsolePreview: () -> Unit,
) {
    DuoSettingRow("版本", BuildConfig.VERSION_NAME, detail = "versionCode ${BuildConfig.VERSION_CODE}", enabled = false)
    DuoSettingRow("上游", "Moonlight V+ 12.15.17", enabled = false)
    DuoSettingRow("主机端", "Foundation Sunshine", enabled = false)
    DuoSettingRow("许可证", "GPL-3.0", enabled = false)
    DuoSettingRow(
        title = "开源声明",
        value = "查看",
        isSubpage = true,
        onActivate = onOpenNotices,
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoSettingRow(
            title = "帮助 / 诊断",
            value = "旧版",
            detail = "旧版 V+：A 确认 · B 返回",
            isSubpage = true,
            onActivate = onOpenLegacy,
        )
    }
    DuoSettingRow(
        title = "更新",
        value = "暂不可用",
        detail = "尚未配置独立更新源",
        enabled = false,
    )
    DuoSettingRow(
        title = "隐私",
        value = "无遥测",
        detail = "不含 Firebase / Crashlytics",
        enabled = false,
    )
    if (BuildConfig.DEBUG) {
        DuoSettingRow(
            title = "串流控制台预览",
            value = "Debug",
            detail = "无真实会话；状态页只显示 —/未连接",
            isSubpage = true,
            onActivate = onOpenConsolePreview,
        )
    }
}

@Composable
private fun QualityPage(
    scene: DuoSettingsRepository.Snapshot,
    repository: DuoSettingsRepository,
    onChanged: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    DuoSettingRow(
        title = "分辨率",
        value = scene.resolutionLabel,
        isSubpage = true,
        onActivate = {
            onChoose(
                "分辨率",
                listOf(
                    ChoiceOption("native", "原生"),
                    ChoiceOption("1280x720", "1280 × 720"),
                    ChoiceOption("1920x1080", "1920 × 1080"),
                    ChoiceOption("2560x1440", "2560 × 1440"),
                    ChoiceOption("3840x2160", "3840 × 2160"),
                ),
                if (scene.isNativeResolution) "native" else scene.resolutionValue,
            ) { value ->
                if (value == "native") {
                    repository.setResolution(sceneWidth(scene), sceneHeight(scene), true)
                } else {
                    val parts = value.split('x')
                    repository.setResolution(parts[0].toInt(), parts[1].toInt(), false)
                }
                onChanged()
            }
        },
    )
    DuoSettingRow(
        title = "帧率",
        value = "${scene.fps} Hz",
        isSubpage = true,
        onActivate = {
            onChoose(
                "帧率",
                listOf(30, 60, 90, 120).map { ChoiceOption(it.toString(), "$it Hz") },
                scene.fps.toString(),
            ) { value ->
                repository.setFps(value.toInt())
                onChanged()
            }
        },
    )
    DuoSliderRow(
        title = "码率",
        detail = "当前会话带宽上限",
        valueLabel = "${scene.bitrateMbps} Mbps",
        value = scene.bitrateMbps.toFloat(),
        range = 5f..150f,
        steps = 28,
        onValueChange = { repository.setBitrateMbps(it.toInt()) },
        onValueChangeFinished = onChanged,
    )
    DuoSettingRow(
        title = "智能码率",
        value = if (scene.adaptiveBitrate) "开" else "关",
        isSwitch = true,
        switchOn = scene.adaptiveBitrate,
        onActivate = {
            repository.setAdaptive(!scene.adaptiveBitrate)
            onChanged()
        },
    )
    DuoSettingRow(
        title = "视频格式",
        value = scene.formatLabel,
        isSubpage = true,
        onActivate = {
            onChoose(
                "视频格式",
                PreferenceConfiguration.FormatOption.entries.map {
                    ChoiceOption(it.name, DuoSettingsRepository.formatLabel(it))
                },
                repository.sceneConfig().videoFormat.name,
            ) { value ->
                repository.setFormat(PreferenceConfiguration.FormatOption.valueOf(value))
                onChanged()
            }
        },
    )
    DuoSettingRow(
        title = "HDR",
        value = if (scene.hdr) "开" else "关",
        detail = "需要主机与上屏同时支持；下次串流生效",
        isSwitch = true,
        switchOn = scene.hdr,
        onActivate = {
            repository.setHdr(!scene.hdr)
            onChanged()
        },
    )
    DuoSettingRow(
        title = "帧节奏 / FEC",
        value = "高级",
        detail = "精确同步、FEC 模式、渲染缩放",
        isSubpage = true,
        onActivate = { },
        enabled = false,
    )
}

@Composable
private fun DisplayPage(
    repository: DuoSettingsRepository,
    version: Int,
    config: PreferenceConfiguration,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val stretch = repository.getBoolean("checkbox_stretch_video", false)
    val reverse = repository.getBoolean("checkbox_reverse_resolution", false)
    val external = repository.getBoolean("use_external_display", false)
    val pip = repository.getBoolean("checkbox_enable_pip", false)
    DuoSettingRow(
        title = "拉伸画面",
        value = if (stretch) "开" else "关",
        isSwitch = true,
        switchOn = stretch,
        onActivate = {
            repository.putBoolean("checkbox_stretch_video", !stretch)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "画面位置",
        value = prefPositionLabel(config.screenPosition.name),
        isSubpage = true,
        enabled = !stretch,
        onActivate = {
            onChoose(
                "画面位置",
                listOf(
                    "TOP_LEFT" to "左上", "TOP_CENTER" to "上中", "TOP_RIGHT" to "右上",
                    "CENTER_LEFT" to "左中", "CENTER" to "居中", "CENTER_RIGHT" to "右中",
                    "BOTTOM_LEFT" to "左下", "BOTTOM_CENTER" to "下中", "BOTTOM_RIGHT" to "右下",
                ).map { ChoiceOption(it.first, it.second) },
                config.screenPosition.name,
            ) { value ->
                repository.putString("list_screen_position", value.lowercase())
                onRefresh()
            }
        },
    )
    DuoSliderRow(
        title = "水平偏移",
        valueLabel = "${config.screenOffsetX}%",
        value = config.screenOffsetX.toFloat(),
        range = 0f..100f,
        steps = 9,
        onValueChange = { repository.putInt("seekbar_screen_offset_x", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoSliderRow(
        title = "垂直偏移",
        valueLabel = "${config.screenOffsetY}%",
        value = config.screenOffsetY.toFloat(),
        range = 0f..100f,
        steps = 9,
        onValueChange = { repository.putInt("seekbar_screen_offset_y", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoSettingRow(
        title = "反向分辨率",
        value = if (reverse) "开" else "关",
        isSwitch = true,
        switchOn = reverse,
        onActivate = {
            repository.putBoolean("checkbox_reverse_resolution", !reverse)
            onRefresh()
        },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoSettingRow(
            title = "外接显示器",
            value = if (external) "开" else "关",
            detail = "HDMI / DP 输出；需要设备能力",
            isSwitch = true,
            switchOn = external,
            onActivate = {
                repository.putBoolean("use_external_display", !external)
                onRefresh()
            },
        )
    }
    DuoSettingRow(
        title = "画中画",
        value = if (pip) "开" else "关",
        detail = "需要系统 PiP 支持",
        isSwitch = true,
        switchOn = pip,
        onActivate = {
            repository.putBoolean("checkbox_enable_pip", !pip)
            onRefresh()
        },
    )
    DuoSliderRow(
        title = "主机渲染缩放",
        valueLabel = "${config.resolutionScale}%",
        value = config.resolutionScale.toFloat(),
        range = 50f..400f,
        steps = 6,
        onValueChange = { repository.putInt("seekbar_resolutions_scale", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
}

@Composable
private fun OverlayPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val enabled = repository.getBoolean("checkbox_enable_perf_overlay", false)
    val style = repository.getString("list_perf_overlay_style", "standard")
    val orientation = repository.getString("list_perf_overlay_orientation", "horizontal")
    val opacity = repository.getInt("seekbar_perf_overlay_bg_opacity", 40)
    val toast = repository.getBoolean("checkbox_enable_post_stream_toast", false)
    DuoSettingRow(
        title = "显示性能浮层",
        value = if (enabled) "开" else "关",
        isSwitch = true,
        switchOn = enabled,
        onActivate = {
            repository.putBoolean("checkbox_enable_perf_overlay", !enabled)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "样式",
        value = prefOverlayStyleLabel(style),
        isSubpage = true,
        enabled = enabled,
        onActivate = {
            onChoose(
                "性能浮层样式",
                listOf(
                    ChoiceOption("mini", "迷你"),
                    ChoiceOption("standard", "标准"),
                    ChoiceOption("detailed", "详细"),
                    ChoiceOption("classic", "经典"),
                ),
                style,
            ) {
                repository.putString("list_perf_overlay_style", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "方向",
        value = if (orientation == "vertical") "竖排" else "横排",
        isSubpage = true,
        enabled = enabled,
        onActivate = {
            onChoose(
                "浮层方向",
                listOf(ChoiceOption("horizontal", "横排"), ChoiceOption("vertical", "竖排")),
                orientation,
            ) {
                repository.putString("list_perf_overlay_orientation", it)
                onRefresh()
            }
        },
    )
    DuoSliderRow(
        title = "背景不透明度",
        valueLabel = "$opacity%",
        value = opacity.toFloat(),
        range = 0f..100f,
        steps = 9,
        onValueChange = { repository.putInt("seekbar_perf_overlay_bg_opacity", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoSettingRow(
        title = "串流后延迟提示",
        value = if (toast) "开" else "关",
        isSwitch = true,
        switchOn = toast,
        onActivate = {
            repository.putBoolean("checkbox_enable_post_stream_toast", !toast)
            onRefresh()
        },
    )
}

@Composable
private fun ExperimentalPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    onOpenLegacy: () -> Unit,
) {
    val framegen = repository.getBoolean("checkbox_framegen_enabled", false)
    val adaptive = repository.getBoolean("checkbox_framegen_adaptive_enabled", false)
    val quality = repository.getString("list_framegen_quality_preset", "performance")
    DuoSettingRow(
        title = "帧生成",
        value = if (framegen) "开" else "关",
        detail = "需要开发者模式与 Lossless.dll",
        isSwitch = true,
        switchOn = framegen,
        onActivate = {
            repository.putBoolean("checkbox_framegen_enabled", !framegen)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "自适应补帧",
        value = if (adaptive) "开" else "关",
        isSwitch = true,
        switchOn = adaptive,
        onActivate = {
            repository.putBoolean("checkbox_framegen_adaptive_enabled", !adaptive)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "补帧质量",
        value = when (quality) {
            "balanced" -> "均衡"
            "clarity" -> "清晰"
            "custom" -> "自定义"
            else -> "性能"
        },
        isSubpage = true,
        onActivate = {
            onChoose(
                "补帧质量",
                listOf(
                    ChoiceOption("performance", "性能"),
                    ChoiceOption("balanced", "均衡"),
                    ChoiceOption("clarity", "清晰"),
                    ChoiceOption("custom", "自定义"),
                ),
                quality,
            ) {
                repository.putString("list_framegen_quality_preset", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "PyroWave / 高级设置",
        value = "旧版",
        detail = "DLL 导入、解码缓冲、输出队列",
        isSubpage = true,
        onActivate = onOpenLegacy,
    )
}

@Composable
private fun AudioOutputPage(
    scene: DuoSettingsRepository.Snapshot,
    repository: DuoSettingsRepository,
    onChanged: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val spatial = repository.getBoolean("checkbox_enable_spatializer", false)
    val audioFx = repository.getBoolean("checkbox_enable_audiofx", false)
    DuoSettingRow(
        title = "声道",
        value = scene.audioLabel,
        isSubpage = true,
        onActivate = {
            onChoose(
                "声道",
                listOf(
                    ChoiceOption("2", "立体声"),
                    ChoiceOption("51", "5.1 环绕"),
                    ChoiceOption("71", "7.1 环绕"),
                    ChoiceOption("714", "7.1.4 环绕"),
                ),
                repository.getString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "2"),
            ) {
                repository.setAudio(it)
                onChanged()
            }
        },
    )
    DuoSettingRow(
        title = "空间音频",
        value = if (spatial) "开" else "关",
        detail = "设备支持时可用",
        isSwitch = true,
        switchOn = spatial,
        onActivate = {
            repository.putBoolean("checkbox_enable_spatializer", !spatial)
            onChanged()
        },
    )
    DuoSettingRow(
        title = "系统音效",
        value = if (audioFx) "开" else "关",
        isSwitch = true,
        switchOn = audioFx,
        onActivate = {
            repository.putBoolean("checkbox_enable_audiofx", !audioFx)
            onChanged()
        },
    )
}

@Composable
private fun AudioPassPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val enabled = repository.getBoolean("checkbox_enable_audio_passthrough", false)
    val codec = repository.getString("list_audio_codec", "auto")
    val buffer = repository.getString("list_audio_passthrough_buffer", "normal")
    val ac3 = repository.getBoolean("checkbox_ac3_iec61937", false)
    DuoSettingRow(
        title = "音频直通",
        value = if (enabled) "开" else "关",
        isSwitch = true,
        switchOn = enabled,
        onActivate = {
            repository.putBoolean("checkbox_enable_audio_passthrough", !enabled)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "编码",
        value = codec.uppercase(),
        isSubpage = true,
        enabled = enabled,
        onActivate = {
            onChoose(
                "音频直通编码",
                listOf(
                    ChoiceOption("auto", "自动"),
                    ChoiceOption("opus", "Opus"),
                    ChoiceOption("ac3", "AC3"),
                    ChoiceOption("eac3", "EAC3"),
                    ChoiceOption("pcm", "PCM"),
                ),
                codec,
            ) {
                repository.putString("list_audio_codec", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "缓冲",
        value = when (buffer) {
            "low" -> "低"
            "high" -> "高"
            else -> "标准"
        },
        isSubpage = true,
        enabled = enabled,
        onActivate = {
            onChoose(
                "直通缓冲",
                listOf(ChoiceOption("low", "低"), ChoiceOption("normal", "标准"), ChoiceOption("high", "高")),
                buffer,
            ) {
                repository.putString("list_audio_passthrough_buffer", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "AC3 IEC61937",
        value = if (ac3) "开" else "关",
        isSwitch = true,
        switchOn = ac3,
        enabled = enabled,
        onActivate = {
            repository.putBoolean("checkbox_ac3_iec61937", !ac3)
            onRefresh()
        },
    )
}

@Composable
private fun MicPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val enabled = repository.getBoolean("checkbox_enable_mic", false)
    val initial = repository.getString("list_mic_initial_state", "off")
    val bitrate = repository.getInt("seekbar_mic_bitrate_kbps", 64)
    val enhance = repository.getBoolean("checkbox_mic_voice_enhancement", true)
    DuoSettingRow(
        title = "启用麦克风",
        value = if (enabled) "开" else "关",
        isSwitch = true,
        switchOn = enabled,
        onActivate = {
            repository.putBoolean("checkbox_enable_mic", !enabled)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "初始状态",
        value = when (initial) {
            "on" -> "开"
            "follow_last" -> "跟随上次"
            else -> "关"
        },
        isSubpage = true,
        enabled = enabled,
        onActivate = {
            onChoose(
                "麦克风初始状态",
                listOf(
                    ChoiceOption("off", "关"),
                    ChoiceOption("on", "开"),
                    ChoiceOption("follow_last", "跟随上次"),
                ),
                initial,
            ) {
                repository.putString("list_mic_initial_state", it)
                onRefresh()
            }
        },
    )
    DuoSliderRow(
        title = "码率",
        valueLabel = "$bitrate kbps",
        value = bitrate.toFloat(),
        range = 32f..256f,
        steps = 27,
        onValueChange = { repository.putInt("seekbar_mic_bitrate_kbps", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoSettingRow(
        title = "人声增强",
        value = if (enhance) "开" else "关",
        isSwitch = true,
        switchOn = enhance,
        onActivate = {
            repository.putBoolean("checkbox_mic_voice_enhancement", !enhance)
            onRefresh()
        },
    )
}

@Composable
private fun GamepadPage(
    scene: DuoSettingsRepository.Snapshot,
    repository: DuoSettingsRepository,
    onChanged: () -> Unit,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val deadzone = repository.getInt("seekbar_deadzone", 7)
    val multi = repository.getBoolean("checkbox_multi_controller", true)
    val joycon = repository.getBoolean("checkbox_combine_joycons", true)
    val flip = repository.getBoolean("checkbox_flip_face_buttons", false)
    val nativeHaptics = repository.getBoolean("checkbox_native_device_haptics", false)
    DuoSliderRow(
        title = "摇杆死区",
        valueLabel = "$deadzone%",
        value = deadzone.toFloat(),
        range = 0f..20f,
        steps = 19,
        onValueChange = { repository.putInt("seekbar_deadzone", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoSettingRow(
        title = "震动模式",
        value = scene.rumbleLabel,
        isSubpage = true,
        onActivate = {
            onChoose(
                "震动模式",
                listOf(
                    ChoiceOption("smart", "智能分配"),
                    ChoiceOption("device", "仅本机"),
                    ChoiceOption("controller", "仅手柄"),
                ),
                repository.getString(PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING, "controller"),
            ) {
                repository.setRumble(it)
                onChanged()
            }
        },
    )
    DuoSettingRow(
        title = "多手柄",
        value = if (multi) "开" else "关",
        isSwitch = true,
        switchOn = multi,
        onActivate = {
            repository.putBoolean("checkbox_multi_controller", !multi)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "合并 JoyCon",
        value = if (joycon) "开" else "关",
        isSwitch = true,
        switchOn = joycon,
        onActivate = {
            repository.putBoolean("checkbox_combine_joycons", !joycon)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "翻转 AB / XY",
        value = if (flip) "开" else "关",
        isSwitch = true,
        switchOn = flip,
        onActivate = {
            repository.putBoolean("checkbox_flip_face_buttons", !flip)
            onRefresh()
        },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoSettingRow(
            title = "原生设备触觉",
            value = if (nativeHaptics) "开" else "关",
            detail = "Android 12+ / 设备支持时",
            isSwitch = true,
            switchOn = nativeHaptics,
            onActivate = {
                repository.putBoolean("checkbox_native_device_haptics", !nativeHaptics)
                onRefresh()
            },
        )
        DuoSettingRow(
            title = "USB 手柄驱动",
            value = if (repository.getBoolean("checkbox_usb_driver", true)) "开" else "关",
            detail = "DS4 / DS5 / Xbox / Switch Pro",
            isSwitch = true,
            switchOn = repository.getBoolean("checkbox_usb_driver", true),
            onActivate = {
                val current = repository.getBoolean("checkbox_usb_driver", true)
                repository.putBoolean("checkbox_usb_driver", !current)
                onRefresh()
            },
        )
    }
}

@Composable
private fun TouchPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val preset = repository.touchPreset()
    val speed = repository.getInt("seekbar_hardware_touchpad_pointer_speed", 100)
    val dblDrag = repository.getBoolean("pref_enable_double_click_drag", false)
    val touchKeyboard = repository.getBoolean("checkbox_touch_keyboard_auto_invoke", true)
    DuoSettingRow(
        title = "触摸模式",
        value = when (preset) {
            TouchModePreset.ENHANCED -> "增强"
            TouchModePreset.CLASSIC -> "经典"
            TouchModePreset.TRACKPAD -> "触控板"
            TouchModePreset.NATIVE -> "原生"
        },
        isSubpage = true,
        onActivate = {
            onChoose(
                "触摸模式",
                listOf(
                    ChoiceOption("enhanced", "增强"),
                    ChoiceOption("classic", "经典"),
                    ChoiceOption("trackpad", "触控板"),
                    ChoiceOption("native", "原生"),
                ),
                preset.preferenceValue,
            ) {
                TouchModePreset.fromPreferenceValue(it)?.let(repository::setTouchPreset)
                onRefresh()
            }
        },
    )
    DuoSliderRow(
        title = "触控板指针速度",
        valueLabel = "$speed%",
        value = speed.toFloat(),
        range = 50f..300f,
        steps = 49,
        onValueChange = { repository.putInt("seekbar_hardware_touchpad_pointer_speed", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoSettingRow(
        title = "双击拖拽",
        value = if (dblDrag) "开" else "关",
        isSwitch = true,
        switchOn = dblDrag,
        onActivate = {
            repository.putBoolean("pref_enable_double_click_drag", !dblDrag)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "触屏键盘自动唤起",
        value = if (touchKeyboard) "开" else "关",
        isSwitch = true,
        switchOn = touchKeyboard,
        onActivate = {
            repository.putBoolean("checkbox_touch_keyboard_auto_invoke", !touchKeyboard)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "增强触摸分区",
        value = "高级",
        detail = "分区侧、分隔线、指针速度",
        enabled = false,
    )
}

@Composable
private fun KeyboardPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val escMenu = repository.getBoolean("checkbox_enable_esc_menu", true)
    val escKey = repository.getString("list_esc_menu_key", "111").toIntOrNull() ?: 111
    val remoteIme = repository.getBoolean("checkbox_remote_ime_auto_show", true)
    val middle = repository.getBoolean("checkbox_mouse_middle", true)
    val wheel = repository.getBoolean("checkbox_mouse_wheel", true)
    val hwTouchpad = repository.getBoolean("checkbox_optimize_hardware_touchpad", false)
    DuoSettingRow(
        title = "ESC 双击菜单",
        value = if (escMenu) "开" else "关",
        isSwitch = true,
        switchOn = escMenu,
        onActivate = {
            repository.putBoolean("checkbox_enable_esc_menu", !escMenu)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "菜单键",
        value = if (escKey == 111) "Esc" else "F${escKey - 130}",
        isSubpage = true,
        onActivate = {
            onChoose(
                "ESC 菜单键",
                listOf(ChoiceOption("111", "Esc")) + (1..12).map { ChoiceOption((130 + it).toString(), "F$it") },
                escKey.toString(),
            ) {
                repository.putString("list_esc_menu_key", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "远程 IME 自动弹出",
        value = if (remoteIme) "开" else "关",
        isSwitch = true,
        switchOn = remoteIme,
        onActivate = {
            repository.putBoolean("checkbox_remote_ime_auto_show", !remoteIme)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "修复鼠标中键",
        value = if (middle) "开" else "关",
        isSwitch = true,
        switchOn = middle,
        onActivate = {
            repository.putBoolean("checkbox_mouse_middle", !middle)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "修复鼠标滚轮",
        value = if (wheel) "开" else "关",
        isSwitch = true,
        switchOn = wheel,
        onActivate = {
            repository.putBoolean("checkbox_mouse_wheel", !wheel)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "硬件触控板优化",
        value = if (hwTouchpad) "开" else "关",
        isSwitch = true,
        switchOn = hwTouchpad,
        onActivate = {
            repository.putBoolean("checkbox_optimize_hardware_touchpad", !hwTouchpad)
            onRefresh()
        },
    )
}

@Composable
private fun OscPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val show = repository.getBoolean("checkbox_show_onscreen_controls", false)
    val layout = repository.getString("list_osc_layout", "xbox")
    val onlyL3R3 = repository.getBoolean("checkbox_only_show_L3R3", false)
    val guide = repository.getBoolean("checkbox_show_guide_button", true)
    val half = repository.getBoolean("checkbox_half_height_osc_portrait", true)
    val opacity = repository.getInt("seekbar_osc_opacity", 90)
    DuoSettingRow(
        title = "显示虚拟手柄",
        value = if (show) "开" else "关",
        isSwitch = true,
        switchOn = show,
        onActivate = {
            repository.putBoolean("checkbox_show_onscreen_controls", !show)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "布局",
        value = layout.uppercase(),
        isSubpage = true,
        onActivate = {
            onChoose(
                "虚拟手柄布局",
                listOf(
                    ChoiceOption("xbox", "Xbox"),
                    ChoiceOption("ds", "DualSense"),
                    ChoiceOption("ns", "Switch"),
                    ChoiceOption("classic", "经典"),
                ),
                layout,
            ) {
                repository.putString("list_osc_layout", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "仅显示 L3/R3",
        value = if (onlyL3R3) "开" else "关",
        isSwitch = true,
        switchOn = onlyL3R3,
        onActivate = {
            repository.putBoolean("checkbox_only_show_L3R3", !onlyL3R3)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "显示 Guide",
        value = if (guide) "开" else "关",
        isSwitch = true,
        switchOn = guide,
        onActivate = {
            repository.putBoolean("checkbox_show_guide_button", !guide)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "竖屏半高",
        value = if (half) "开" else "关",
        isSwitch = true,
        switchOn = half,
        onActivate = {
            repository.putBoolean("checkbox_half_height_osc_portrait", !half)
            onRefresh()
        },
    )
    DuoSliderRow(
        title = "透明度",
        valueLabel = "$opacity%",
        value = opacity.toFloat(),
        range = 0f..100f,
        steps = 9,
        onValueChange = { repository.putInt("seekbar_osc_opacity", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
}

@Composable
private fun GyroPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
) {
    val sensors = repository.getBoolean("checkbox_gamepad_motion_sensors", true)
    val fallback = repository.getBoolean("checkbox_gamepad_motion_fallback", false)
    DuoSettingRow(
        title = "手柄体感",
        value = if (sensors) "开" else "关",
        detail = "Android 12+ 需要设备传感器",
        isSwitch = true,
        switchOn = sensors,
        onActivate = {
            repository.putBoolean("checkbox_gamepad_motion_sensors", !sensors)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "设备传感器回退",
        value = if (fallback) "开" else "关",
        isSwitch = true,
        switchOn = fallback,
        onActivate = {
            repository.putBoolean("checkbox_gamepad_motion_fallback", !fallback)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "陀螺仪鼠标",
        value = "串流内调整",
        detail = "开关、激活键、灵敏度、X/Y 反转",
        enabled = false,
    )
}

@Composable
private fun HostBehaviorPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val hostAudio = repository.getBoolean("checkbox_host_audio", false)
    val controlOnly = repository.getBoolean("checkbox_control_only", false)
    val lock = repository.getBoolean("checkbox_lock_screen_after_disconnect", false)
    val screenMode = repository.getString("list_screen_combination_mode", "-1")
    DuoSettingRow(
        title = "主机音频",
        value = if (hostAudio) "开" else "关",
        isSwitch = true,
        switchOn = hostAudio,
        onActivate = {
            repository.putBoolean("checkbox_host_audio", !hostAudio)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "仅控制流",
        value = if (controlOnly) "开" else "关",
        isSwitch = true,
        switchOn = controlOnly,
        onActivate = {
            repository.putBoolean("checkbox_control_only", !controlOnly)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "屏幕组合",
        value = when (screenMode) {
            "0" -> "不操作"
            "1" -> "自动激活"
            "2" -> "主屏"
            "3" -> "独占"
            "4" -> "副屏"
            else -> "主机默认"
        },
        isSubpage = true,
        onActivate = {
            onChoose(
                "屏幕组合",
                listOf(
                    ChoiceOption("-1", "主机默认"),
                    ChoiceOption("0", "不操作"),
                    ChoiceOption("1", "自动激活"),
                    ChoiceOption("2", "主屏"),
                    ChoiceOption("4", "副屏"),
                    ChoiceOption("3", "独占"),
                ),
                screenMode,
            ) {
                repository.putString("list_screen_combination_mode", it)
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "退出行为",
        value = if (repository.quitMode() == QuitBehavior.DISCONNECT_ONLY) "仅断开" else "停止应用",
        isSubpage = true,
        onActivate = {
            onChoose(
                "退出行为",
                listOf(
                    ChoiceOption("stop_host_app", "停止主机应用"),
                    ChoiceOption("disconnect_only", "仅断开"),
                ),
                repository.quitMode().preferenceValue,
            ) {
                repository.setQuitMode(QuitBehavior.fromPreferenceValue(it))
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "后台串流",
        value = when (repository.backgroundMode()) {
            BackgroundStreamBehavior.RESUME -> "恢复"
            BackgroundStreamBehavior.KEEP_CONNECTED -> "保持连接"
            BackgroundStreamBehavior.KEEP_AUDIO -> "保持+音频"
            else -> "断开"
        },
        isSubpage = true,
        onActivate = {
            onChoose(
                "后台串流",
                listOf(
                    ChoiceOption("disconnect", "断开"),
                    ChoiceOption("resume", "恢复"),
                    ChoiceOption("keep_connected", "保持连接"),
                    ChoiceOption("keep_audio", "保持连接 + 音频"),
                ),
                repository.backgroundMode().preferenceValue,
            ) {
                repository.setBackgroundMode(BackgroundStreamBehaviorPolicy.fromPreferenceValue(it))
                onRefresh()
            }
        },
    )
    DuoSettingRow(
        title = "断开后锁屏",
        value = if (lock) "开" else "关",
        isSwitch = true,
        switchOn = lock,
        onActivate = {
            repository.putBoolean("checkbox_lock_screen_after_disconnect", !lock)
            onRefresh()
        },
    )
}

@Composable
private fun ConsoleSettingsPage() {
    DuoSettingRow("默认页签", "状态", detail = "只读，置首并默认", enabled = false)
    DuoSettingRow("直通不透明度", "60%", detail = "固定；本地 100%", enabled = false)
    DuoSettingRow("状态页指标", "5 项", detail = "帧率 / 延迟 / 码率 / RTT / 丢包", enabled = false)
    DuoSettingRow("趋势", "60 秒", detail = "延迟金线 · 码率青线", enabled = false)
    DuoSettingRow("主机负载", "未提供", detail = "不伪造 CPU/GPU/内存", enabled = false)
}

@Composable
private fun SubpageEntry(
    title: String,
    value: String,
    onSubpage: (String) -> Unit,
    id: String,
) {
    DuoSettingRow(
        title = title,
        value = value,
        isSubpage = true,
        onActivate = { onSubpage(id) },
    )
}

@Composable
private fun DuoChoiceOverlay(
    request: ChoiceRequest,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DuoOverlay(onDismiss = onDismiss, width = 380.dp) {
        Column {
            Text(request.title, style = LumaType.titleM, color = LumaColors.Text)
            Spacer(Modifier.height(8.dp))
            request.options.forEach { option ->
                DuoSettingRow(
                    title = option.label,
                    value = if (option.value == request.current) "当前" else "",
                    onActivate = { onSelect(option.value) },
                )
            }
            Spacer(Modifier.height(8.dp))
            DuoTextButton("取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun prefPositionLabel(name: String): String = when (name) {
    "TOP_LEFT" -> "左上"
    "TOP_CENTER" -> "上中"
    "TOP_RIGHT" -> "右上"
    "CENTER_LEFT" -> "左中"
    "CENTER" -> "居中"
    "CENTER_RIGHT" -> "右中"
    "BOTTOM_LEFT" -> "左下"
    "BOTTOM_CENTER" -> "下中"
    "BOTTOM_RIGHT" -> "右下"
    else -> name
}

private fun prefOverlayStyleLabel(value: String): String = when (value) {
    "mini" -> "迷你"
    "detailed" -> "详细"
    "classic" -> "经典"
    else -> "标准"
}

private fun sceneWidth(scene: DuoSettingsRepository.Snapshot): Int =
    scene.resolutionValue.substringBefore('x').toIntOrNull() ?: 1920

private fun sceneHeight(scene: DuoSettingsRepository.Snapshot): Int =
    scene.resolutionValue.substringAfter('x').toIntOrNull() ?: 1080
