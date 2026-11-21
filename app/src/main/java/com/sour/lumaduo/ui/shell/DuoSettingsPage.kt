// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import android.media.MediaCodecList
import android.os.Build
import android.view.InputDevice
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
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
    val description: String,
    val icon: Int,
) {
    VIDEO("画面", "画质、显示与 HUD", R.drawable.duo_ic_monitor),
    AUDIO("音频", "声道、直通与麦克风", R.drawable.duo_ic_volume),
    INPUT("输入", "手柄、触控与键盘", R.drawable.duo_ic_gamepad),
    NETWORK("网络", "EasyTier、STUN 与测速", R.drawable.duo_ic_globe),
    HOST("主机", "主机端行为与剪贴板", R.drawable.duo_ic_host),
    INTERFACE("界面", "语言、主题与控制台", R.drawable.duo_ic_sliders),
    ABOUT("关于", "版本、许可与诊断", R.drawable.duo_ic_apps),
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
    onOpenHosts: () -> Unit,
    onOpenLegacy: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenHelp: () -> Unit,
    onAddHost: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenConsolePreview: () -> Unit,
    onTopSettingsChanged: (String, String, List<String>) -> Unit = { _, _, _ -> },
    backBlocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var subpage by rememberSaveable { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<ChoiceRequest?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val category = DuoSetCat.entries[categoryIndex.coerceIn(0, DuoSetCat.entries.lastIndex)]
    val config = repository.sceneConfig()

    LaunchedEffect(category, version, scene, runtime.phase) {
        onTopSettingsChanged(
            category.label,
            category.description,
            settingsTopRows(category, scene, repository, remote, runtime),
        )
    }

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
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChanged = { changed() },
                            onChoose = ::choose,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.AUDIO -> AudioParent(
                            scene = scene,
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChanged = { changed() },
                            onChoose = ::choose,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.INPUT -> InputParent(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.NETWORK -> NetworkParent(
                            remote = remote,
                            runtime = runtime,
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onOpenRemote = onOpenRemote,
                            onOpenHosts = onOpenHosts,
                            onAddHost = onAddHost,
                            onNotice = onNotice,
                        )
                        DuoSetCat.HOST -> HostParent(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.INTERFACE -> InterfaceParent(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                            onChoose = ::choose,
                            onSubpage = { subpage = it },
                        )
                        DuoSetCat.ABOUT -> AboutParent(
                            onOpenLegacy = onOpenLegacy,
                            onOpenNotices = onOpenNotices,
                            onOpenHelp = onOpenHelp,
                            onOpenHosts = onOpenHosts,
                            onSubpage = { subpage = it },
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
                        "framePacing" -> FramePacingPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
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
                        "controllerMouse" -> ControllerMousePage(
                            repository = repository,
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
                        "console" -> ConsoleSettingsPage()
                        "advVideo" -> AdvancedPage(
                            "画面",
                            listOfNotNull(
                                AdvancedEntry(
                                    "帧节奏 / FEC",
                                    "精确同步 · FEC · 渲染缩放",
                                    "framePacing",
                                ),
                                AdvancedEntry(
                                    "显示与布局",
                                    "拉伸 · 位置 · 外接屏 · PiP",
                                    "display",
                                ),
                                if (!BuildConfig.MINIMAL_EDITION) {
                                    AdvancedEntry("性能浮层", "位置 · 方向 · 透明度", "overlay")
                                } else {
                                    null
                                },
                                if (!BuildConfig.MINIMAL_EDITION) {
                                    AdvancedEntry("实验性", "帧生成 · PyroWave · 解码", "experimental")
                                } else {
                                    null
                                },
                            ),
                            onSubpage = { subpage = it },
                        )
                        "advAudio" -> AdvancedPage(
                            "音频",
                            listOf(
                                AdvancedEntry("音频输出", "系统音效 · 设备能力", "audioOutput"),
                                AdvancedEntry("音频直通", "编码 · 缓冲 · AC3", "audioPass"),
                                AdvancedEntry("麦克风", "音质 · 增益 · 人声增强", "mic"),
                            ),
                            onSubpage = { subpage = it },
                        )
                        "advInput" -> AdvancedPage(
                            "输入",
                            listOfNotNull(
                                AdvancedEntry("手柄", "多手柄 · JoyCon · 翻键 · USB", "gamepad"),
                                AdvancedEntry("手柄鼠标", "摇杆速度 · D-pad 行为", "controllerMouse"),
                                AdvancedEntry("触控", "双击拖拽 · 触屏键盘 · 增强分区", "touch"),
                                AdvancedEntry("键盘与鼠标", "远程 IME · 中键 · 滚轮", "keyboard"),
                                if (!BuildConfig.MINIMAL_EDITION) {
                                    AdvancedEntry("虚拟手柄", "布局 · 透明度 · Guide", "osc")
                                } else {
                                    null
                                },
                                AdvancedEntry("体感", "设备传感器 · 回退", "gyro"),
                            ),
                            onSubpage = { subpage = it },
                        )
                        "advHost" -> HostAdvancedPage(
                            repository = repository,
                            version = version,
                            onRefresh = { version++ },
                        )
                        "diagnostics" -> DiagnosticsPage(
                            onOpenHelp = onOpenHelp,
                            onOpenHosts = onOpenHosts,
                        )
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
    "framePacing" -> "帧节奏 / FEC"
    "display" -> "显示与布局"
    "overlay" -> "性能浮层"
    "experimental" -> "实验性"
    "audioOutput" -> "音频输出"
    "audioPass" -> "音频直通"
    "mic" -> "麦克风"
    "gamepad" -> "手柄"
    "controllerMouse" -> "手柄鼠标"
    "touch" -> "触控"
    "keyboard" -> "键盘与鼠标"
    "osc" -> "虚拟手柄"
    "gyro" -> "体感"
    "console" -> "控制台"
    "advVideo" -> "画面 · 高级"
    "advAudio" -> "音频 · 高级"
    "advInput" -> "输入 · 高级"
    "advHost" -> "主机 · 高级"
    "diagnostics" -> "帮助 / 诊断"
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

private data class AdvancedEntry(val title: String, val value: String, val subpage: String)

/**
 * A direct settings row whose value opens the shared choice overlay. The HTML
 * prototype uses this pattern for nearly every option, so the top-level pages
 * stay flat and advanced upstream tuning lives behind one "高级" entry.
 */
@Composable
private fun DuoChoiceRow(
    title: String,
    value: String,
    options: List<ChoiceOption>,
    current: String,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    detail: String? = null,
    enabled: Boolean = true,
    requiresReconnect: Boolean = false,
    onSelected: (String) -> Unit,
) {
    DuoSettingRow(
        title = title,
        value = value,
        detail = detail,
        isSubpage = true,
        enabled = enabled,
        requiresReconnect = requiresReconnect,
        onActivate = { onChoose(title, options, current, onSelected) },
    )
}

@Composable
private fun DuoSwitchRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    detail: String? = null,
    enabled: Boolean = true,
    requiresReconnect: Boolean = false,
) {
    DuoSettingRow(
        title = title,
        value = if (checked) "开" else "关",
        detail = detail,
        isSwitch = true,
        switchOn = checked,
        enabled = enabled,
        requiresReconnect = requiresReconnect,
        onActivate = onToggle,
    )
}

@Composable
private fun VideoParent(
    scene: DuoSettingsRepository.Snapshot,
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChanged: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    onSubpage: (String) -> Unit,
) {
    val scale = repository.getInt("seekbar_resolutions_scale", 100)
    val overlay = repository.getBoolean("checkbox_enable_perf_overlay", false)
    // "version" is read by the caller so a preference write invalidates this page.

    DuoChoiceRow(
        title = "分辨率",
        value = scene.resolutionLabel,
        detail = "串流输出尺寸",
        options = listOf(
            ChoiceOption("native", "原生"),
            ChoiceOption("1280x720", "1280 × 720"),
            ChoiceOption("1920x1080", "1920 × 1080"),
            ChoiceOption("2560x1440", "2560 × 1440"),
            ChoiceOption("3840x2160", "3840 × 2160"),
        ),
        current = if (scene.isNativeResolution) "native" else scene.resolutionValue,
        requiresReconnect = true,
        onChoose = onChoose,
    ) { value ->
        if (value == "native") {
            repository.setResolution(sceneWidth(scene), sceneHeight(scene), true)
        } else {
            val parts = value.split('x')
            repository.setResolution(parts[0].toInt(), parts[1].toInt(), false)
        }
        onChanged()
    }
    DuoChoiceRow(
        title = "帧率",
        value = "${scene.fps} Hz",
        detail = "上屏刷新率",
        options = (listOf(30, 60, 90, 120) + scene.fps).distinct().sorted()
            .map { ChoiceOption(it.toString(), "$it Hz") },
        current = scene.fps.toString(),
        requiresReconnect = true,
        onChoose = onChoose,
    ) { value ->
        repository.setFps(value.toInt())
        onChanged()
    }
    DuoChoiceRow(
        title = "码率",
        value = "${scene.bitrateMbps} Mbps",
        detail = "网络带宽上限",
        options = (listOf(10, 20, 30, 50, 80, 100, 150) + scene.bitrateMbps)
            .distinct().sorted()
            .map { ChoiceOption(it.toString(), "$it Mbps") },
        current = scene.bitrateMbps.toString(),
        requiresReconnect = true,
        onChoose = onChoose,
    ) { value ->
        repository.setBitrateMbps(value.toInt())
        onChanged()
    }
    DuoSwitchRow(
        title = "智能码率",
        checked = scene.adaptiveBitrate,
        detail = "弱网自动调整",
        onToggle = {
            repository.setAdaptive(!scene.adaptiveBitrate)
            onChanged()
        },
    )
    DuoChoiceRow(
        title = "视频格式",
        value = scene.formatLabel,
        detail = "H.264 / HEVC / AV1",
        options = PreferenceConfiguration.FormatOption.entries
            .filter { !BuildConfig.MINIMAL_EDITION || it != PreferenceConfiguration.FormatOption.FORCE_PYROWAVE }
            .map { ChoiceOption(it.name, DuoSettingsRepository.formatLabel(it)) },
        current = repository.sceneConfig().videoFormat.name,
        requiresReconnect = true,
        onChoose = onChoose,
    ) { value ->
        repository.setFormat(PreferenceConfiguration.FormatOption.valueOf(value))
        onChanged()
    }
    DuoSwitchRow(
        title = "HDR",
        checked = scene.hdr,
        detail = "需要主机与上屏同时支持",
        requiresReconnect = true,
        onToggle = {
            repository.setHdr(!scene.hdr)
            onChanged()
        },
    )
    DuoChoiceRow(
        title = "显示缩放",
        value = "$scale%",
        detail = "主机渲染缩放",
        options = (listOf(50, 75, 100) + scale).distinct().sortedDescending()
            .map { ChoiceOption(it.toString(), "$it%") },
        current = scale.toString(),
        requiresReconnect = true,
        onChoose = onChoose,
    ) { value ->
        repository.putInt("seekbar_resolutions_scale", value.toInt())
        onRefresh()
    }
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoSwitchRow(
            title = "性能浮层",
            checked = overlay,
            detail = "控制台显示实时统计",
            onToggle = {
                repository.putBoolean("checkbox_enable_perf_overlay", !overlay)
                onRefresh()
            },
        )
    }
    SubpageEntry("高级", "显示与布局 · 帧节奏 · 实验性", onSubpage, "advVideo")
}

@Composable
private fun AudioParent(
    scene: DuoSettingsRepository.Snapshot,
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChanged: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    onSubpage: (String) -> Unit,
) {
    val passthrough = repository.getBoolean("checkbox_enable_audio_passthrough", false)
    val mic = repository.getBoolean("checkbox_enable_mic", false)
    val spatial = repository.getBoolean("checkbox_enable_spatializer", false)

    DuoChoiceRow(
        title = "声道",
        value = scene.audioLabel,
        detail = "设备 PCM 能力内自动降级",
        options = listOf(
            ChoiceOption("2", "立体声"),
            ChoiceOption("51", "5.1 环绕"),
            ChoiceOption("71", "7.1 环绕"),
            ChoiceOption("714", "7.1.4 环绕"),
        ),
        current = repository.getString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "2"),
        requiresReconnect = true,
        onChoose = onChoose,
    ) { value ->
        repository.setAudio(value)
        onChanged()
    }
    DuoSwitchRow(
        title = "音频直通",
        checked = passthrough,
        detail = "AC3 / EAC3 / Opus",
        requiresReconnect = true,
        onToggle = {
            repository.putBoolean("checkbox_enable_audio_passthrough", !passthrough)
            onRefresh()
        },
    )
    DuoSwitchRow(
        title = "麦克风",
        checked = mic,
        detail = "需要 Foundation 扩展",
        onToggle = {
            repository.putBoolean("checkbox_enable_mic", !mic)
            onRefresh()
        },
    )
    DuoSwitchRow(
        title = "空间音频",
        checked = spatial,
        detail = "7.1.4（如设备支持）",
        requiresReconnect = true,
        onToggle = {
            repository.putBoolean("checkbox_enable_spatializer", !spatial)
            onChanged()
        },
    )
    SubpageEntry("高级", "直通编码 · 缓冲 · 麦克风增益", onSubpage, "advAudio")
}

@Composable
private fun InputParent(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    onSubpage: (String) -> Unit,
) {
    val deadzone = repository.getInt("seekbar_deadzone", 7)
    val speed = repository.getInt("seekbar_hardware_touchpad_pointer_speed", 100)
    val gyro = repository.getBoolean("gyro_to_right_stick", false)
    val escKey = repository.getString("list_esc_menu_key", "111").toIntOrNull() ?: 111
    val preset = repository.touchPreset()
    val osc = repository.getBoolean("checkbox_show_onscreen_controls", false)

    DuoChoiceRow(
        title = "摇杆死区",
        value = "$deadzone%",
        detail = "左 / 右摇杆",
        options = listOf(0, 7, 12, 20).map { ChoiceOption(it.toString(), "$it%") },
        current = deadzone.toString(),
        onChoose = onChoose,
    ) { value ->
        repository.putInt("seekbar_deadzone", value.toInt())
        onRefresh()
    }
    DuoChoiceRow(
        title = "震动模式",
        value = repository.read().rumbleLabel,
        detail = "主机震动去向",
        options = listOf(
            ChoiceOption("smart", "智能分配"),
            ChoiceOption("device", "仅本机"),
            ChoiceOption("controller", "仅手柄"),
        ),
        current = repository.getString(PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING, "controller"),
        onChoose = onChoose,
    ) { value ->
        repository.setRumble(value)
        onRefresh()
    }
    DuoChoiceRow(
        title = "触摸模式",
        value = touchPresetLabel(preset),
        detail = "下屏触摸映射",
        options = listOf(
            ChoiceOption("enhanced", "增强"),
            ChoiceOption("classic", "经典"),
            ChoiceOption("trackpad", "触控板"),
            ChoiceOption("native", "原生"),
        ),
        current = preset.preferenceValue,
        onChoose = onChoose,
    ) { value ->
        TouchModePreset.fromPreferenceValue(value)?.let(repository::setTouchPreset)
        onRefresh()
    }
    DuoChoiceRow(
        title = "触控板速度",
        value = touchpadSpeedLabel(speed),
        detail = "相对鼠标灵敏度",
        options = listOf(80, 100, 120, 150).map { ChoiceOption(it.toString(), touchpadSpeedLabel(it)) },
        current = speed.toString(),
        onChoose = onChoose,
    ) { value ->
        repository.putInt("seekbar_hardware_touchpad_pointer_speed", value.toInt())
        onRefresh()
    }
    DuoSwitchRow(
        title = "陀螺仪",
        checked = gyro,
        detail = "映射右摇杆或鼠标",
        onToggle = {
            repository.putBoolean("gyro_to_right_stick", !gyro)
            onRefresh()
        },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoSwitchRow(
            title = "虚拟手柄",
            checked = osc,
            detail = "屏幕按键层",
            onToggle = {
                repository.putBoolean("checkbox_show_onscreen_controls", !osc)
                onRefresh()
            },
        )
    }
    DuoChoiceRow(
        title = "ESC 菜单键",
        value = if (escKey == 111) "Esc" else "F${escKey - 130}",
        detail = "双击打开本地控制台",
        options = listOf(
            ChoiceOption("111", "Esc"),
            ChoiceOption("131", "F1"),
            ChoiceOption("142", "F12"),
        ),
        current = escKey.toString(),
        onChoose = onChoose,
    ) { value ->
        repository.putString("list_esc_menu_key", value)
        onRefresh()
    }
    SubpageEntry("高级", "手柄细节 · 触控 · 键鼠 · 体感", onSubpage, "advInput")
}

@Composable
private fun NetworkParent(
    remote: DuoRemoteRepository.Snapshot,
    runtime: DuoRemoteController.State,
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenHosts: () -> Unit,
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
        onActivate = {
            onNotice("选择主机后点“网络测试”")
            onOpenHosts()
        },
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
        detail = "在主机详情的“更多”中切换",
        isSubpage = true,
        onActivate = {
            onNotice("选择主机后，在“更多”菜单切换 IPv6")
            onOpenHosts()
        },
    )
}

@Composable
private fun HostParent(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    onSubpage: (String) -> Unit,
) {
    val sops = repository.getBoolean("checkbox_enable_sops", true)
    val clipText = repository.getBoolean("checkbox_clipboard_sync_text", false)
    val clipImage = repository.getBoolean("checkbox_clipboard_sync_image", false)
    val hostAudio = repository.getBoolean("checkbox_host_audio", false)
    val controlOnly = repository.getBoolean("checkbox_control_only", false)
    val screenMode = repository.getString("list_screen_combination_mode", "-1")
    val quitMode = repository.quitMode()
    val backgroundMode = repository.backgroundMode()

    DuoSwitchRow(
        title = "主机优化",
        checked = sops,
        detail = "Sunshine SOPS",
        onToggle = {
            repository.putBoolean("checkbox_enable_sops", !sops)
            onRefresh()
        },
    )
    DuoChoiceRow(
        title = "屏幕组合",
        value = screenModeLabel(screenMode),
        detail = "主机默认 / 主屏 / 副屏 / 独占",
        options = listOf(
            ChoiceOption("-1", "主机默认"),
            ChoiceOption("0", "不操作"),
            ChoiceOption("1", "自动激活"),
            ChoiceOption("2", "主屏"),
            ChoiceOption("4", "副屏"),
            ChoiceOption("3", "独占"),
        ),
        current = screenMode,
        onChoose = onChoose,
    ) { value ->
        repository.putString("list_screen_combination_mode", value)
        onRefresh()
    }
    DuoSwitchRow(
        title = "主机音频",
        checked = hostAudio,
        detail = "声音在 PC 播放",
        onToggle = {
            repository.putBoolean("checkbox_host_audio", !hostAudio)
            onRefresh()
        },
    )
    DuoSwitchRow(
        title = "仅控制流",
        checked = controlOnly,
        detail = "不传输视频",
        requiresReconnect = true,
        onToggle = {
            repository.putBoolean("checkbox_control_only", !controlOnly)
            onRefresh()
        },
    )
    DuoChoiceRow(
        title = "退出行为",
        value = if (quitMode == QuitBehavior.DISCONNECT_ONLY) "仅断开" else "停止应用",
        detail = "断开时是否停止主机应用",
        options = listOf(
            ChoiceOption("stop_host_app", "停止应用"),
            ChoiceOption("disconnect_only", "仅断开"),
        ),
        current = quitMode.preferenceValue,
        onChoose = onChoose,
    ) { value ->
        repository.setQuitMode(QuitBehavior.fromPreferenceValue(value))
        onRefresh()
    }
    DuoChoiceRow(
        title = "后台串流",
        value = when (backgroundMode) {
            BackgroundStreamBehavior.RESUME -> "恢复"
            BackgroundStreamBehavior.KEEP_CONNECTED -> "保持"
            BackgroundStreamBehavior.KEEP_AUDIO -> "保持+音频"
            else -> "断开"
        },
        detail = "切后台后的连接策略",
        options = listOf(
            ChoiceOption("disconnect", "断开"),
            ChoiceOption("resume", "恢复"),
            ChoiceOption("keep_connected", "保持"),
            ChoiceOption("keep_audio", "保持+音频"),
        ),
        current = backgroundMode.preferenceValue,
        onChoose = onChoose,
    ) { value ->
        repository.setBackgroundMode(BackgroundStreamBehaviorPolicy.fromPreferenceValue(value))
        onRefresh()
    }
    DuoSwitchRow(
        title = "剪贴板文本",
        checked = clipText,
        detail = "双向同步；不做文件传输",
        onToggle = {
            repository.putBoolean("checkbox_clipboard_sync_text", !clipText)
            onRefresh()
        },
    )
    DuoSwitchRow(
        title = "剪贴板图片",
        checked = clipImage,
        detail = "本机到主机",
        onToggle = {
            repository.putBoolean("checkbox_clipboard_sync_image", !clipImage)
            onRefresh()
        },
    )
    SubpageEntry("高级", "断开后锁屏", onSubpage, "advHost")
}

@Composable
private fun InterfaceParent(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
    onSubpage: (String) -> Unit,
) {
    val style = repository.getString("list_perf_overlay_style", "standard")

    DuoSettingRow("语言", "简体中文", detail = "MVP 锁定；多语言不在范围", enabled = false)
    DuoSettingRow("主题", "深色", detail = "深色 + Lumen Gold 锁定", enabled = false)
    DuoSettingRow(
        title = "状态页指标",
        value = "默认 5 项",
        detail = "帧率 / 延迟 / 码率 / RTT / 丢包",
        isSubpage = true,
        onActivate = { onSubpage("console") },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoChoiceRow(
            title = "浮层样式",
            value = prefOverlayStyleLabel(style),
            detail = "迷你 / 标准 / 详细",
            options = listOf(
                ChoiceOption("mini", "迷你"),
                ChoiceOption("standard", "标准"),
                ChoiceOption("detailed", "详细"),
            ),
            current = style,
            onChoose = onChoose,
        ) { value ->
            repository.putString("list_perf_overlay_style", value)
            onRefresh()
        }
    }
    DuoSettingRow("控制台不透明度", "固定", detail = "直通 60% / 本地 100%", enabled = false)
}

@Composable
private fun AboutParent(
    onOpenLegacy: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenHosts: () -> Unit,
    onSubpage: (String) -> Unit,
    onNotice: (String) -> Unit,
    onOpenConsolePreview: () -> Unit,
) {
    DuoSettingRow("版本", BuildConfig.VERSION_NAME, detail = "LumaDuo", enabled = false)
    DuoSettingRow("上游", "Moonlight V+ 12.15.17", detail = "串流客户端基线", enabled = false)
    DuoSettingRow("许可证", "GPL-3.0", detail = "项目整体", enabled = false)
    DuoSettingRow(
        title = "开源声明",
        value = "查看",
        detail = "上游与素材许可",
        isSubpage = true,
        onActivate = onOpenNotices,
    )
    DuoSettingRow(
        title = "帮助 / 诊断",
        value = "打开",
        detail = "能力、手柄、网络诊断",
        isSubpage = true,
        onActivate = { onSubpage("diagnostics") },
    )
    DuoSettingRow(
        title = "更新",
        value = "暂不可用",
        detail = "尚未配置独立更新源",
        enabled = false,
    )
    DuoSettingRow(
        title = "隐私",
        value = "说明",
        detail = "无遥测、无 Crashlytics",
        isSubpage = true,
        onActivate = {
            onNotice("LumaDuo 不含遥测、Firebase 或 Crashlytics；诊断信息只保留在设备本地。")
        },
    )
    if (!BuildConfig.MINIMAL_EDITION) {
        DuoSettingRow(
            title = "旧版界面",
            value = "打开",
            detail = "旧版 V+：A 确认 · B 返回",
            isSubpage = true,
            onActivate = onOpenLegacy,
        )
    }
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
}

@Composable
private fun FramePacingPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val pacing = repository.getString("frame_pacing", "precise-sync")
    val pacingLabel = when (pacing) {
        "balanced" -> "均衡"
        "latency" -> "最低延迟"
        "smoothness" -> "最平滑"
        "cap-fps" -> "均衡 + 限帧"
        "experimental-low-latency" -> "实验性超低延迟"
        else -> "精确同步"
    }
    DuoChoiceRow(
        title = "帧节奏",
        value = pacingLabel,
        detail = "精确同步优先稳定，最低延迟优先响应",
        options = listOf(
            ChoiceOption("precise-sync", "精确同步（推荐）"),
            ChoiceOption("balanced", "均衡"),
            ChoiceOption("latency", "最低延迟"),
            ChoiceOption("smoothness", "最平滑"),
            ChoiceOption("cap-fps", "均衡 + 限帧"),
            ChoiceOption("experimental-low-latency", "实验性超低延迟"),
        ),
        current = pacing,
        onChoose = onChoose,
        requiresReconnect = true,
        onSelected = {
            repository.putString("frame_pacing", it)
            onRefresh()
        },
    )
    val cadence = repository.getBoolean("checkbox_enable_host_cadence_precise_sync", true)
    DuoSwitchRow(
        title = "主机节奏精确同步",
        checked = cadence,
        detail = "使用主机显示节奏校准帧调度",
        requiresReconnect = true,
        onToggle = {
            repository.putBoolean("checkbox_enable_host_cadence_precise_sync", !cadence)
            onRefresh()
        },
    )
    val fecMode = repository.getString("list_fec_mode", "host")
    val fecLabel = when (fecMode) {
        "automatic" -> "自动"
        "fixed" -> "固定百分比"
        else -> "跟随主机"
    }
    DuoChoiceRow(
        title = "FEC 模式",
        value = fecLabel,
        detail = "前向纠错；无线网络丢包时更稳",
        options = listOf(
            ChoiceOption("host", "跟随主机"),
            ChoiceOption("automatic", "自动"),
            ChoiceOption("fixed", "固定百分比"),
        ),
        current = fecMode,
        onChoose = onChoose,
        requiresReconnect = true,
        onSelected = {
            repository.putString("list_fec_mode", it)
            onRefresh()
        },
    )
    if (fecMode == "fixed") {
        val fec = repository.getInt("seekbar_fec_percentage", 20).coerceIn(0, 100)
        DuoSliderRow(
            title = "FEC 百分比",
            valueLabel = "$fec%",
            detail = "仅固定模式生效",
            value = fec.toFloat(),
            range = 0f..100f,
            steps = 19,
            onValueChange = { repository.putInt("seekbar_fec_percentage", it.toInt()) },
            onValueChangeFinished = onRefresh,
        )
    }
    val scale = repository.getInt("seekbar_resolutions_scale", 100).coerceIn(50, 400)
    DuoSliderRow(
        title = "主机渲染缩放",
        valueLabel = "$scale%",
        detail = "PC 端以缩放后的分辨率渲染",
        value = scale.toFloat(),
        range = 50f..400f,
        steps = 69,
        onValueChange = { repository.putInt("seekbar_resolutions_scale", it.toInt()) },
        onValueChangeFinished = onRefresh,
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
        value = "延期",
        detail = "按 D-043 暂不提供；代码保留",
        enabled = false,
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
    val audioFx = repository.getBoolean("checkbox_enable_audiofx", false)
    DuoSettingRow(
        title = "系统音效",
        value = if (audioFx) "开" else "关",
        detail = "设备支持时可用",
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
    val processing = repository.getBoolean("checkbox_mic_volume_processing", false)
    val gain = repository.getBoolean("checkbox_mic_gain", false)
    val gainDb = repository.getInt("seekbar_mic_gain_db", 0)
    val showButton = repository.getBoolean("checkbox_show_mic_button", true)
    val buttonPosition = repository.getString("list_mic_button_position", "center_right")
    val iconColor = repository.getString("list_mic_icon_color", "solid_white")
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
    DuoSwitchRow(
        title = "显示麦克风按钮",
        checked = showButton,
        detail = "串流画面上的悬浮按钮",
        enabled = enabled,
        requiresReconnect = true,
        onToggle = {
            repository.putBoolean("checkbox_show_mic_button", !showButton)
            onRefresh()
        },
    )
    DuoChoiceRow(
        title = "按钮位置",
        value = when (buttonPosition) {
            "top_left" -> "左上"
            "top_center" -> "上中"
            "top_right" -> "右上"
            "center_left" -> "左中"
            "bottom_left" -> "左下"
            "bottom_center" -> "下中"
            "bottom_right" -> "右下"
            else -> "右中"
        },
        options = listOf(
            ChoiceOption("top_left", "左上"),
            ChoiceOption("top_center", "上中"),
            ChoiceOption("top_right", "右上"),
            ChoiceOption("center_left", "左中"),
            ChoiceOption("center_right", "右中"),
            ChoiceOption("bottom_left", "左下"),
            ChoiceOption("bottom_center", "下中"),
            ChoiceOption("bottom_right", "右下"),
        ),
        current = buttonPosition,
        enabled = enabled,
        requiresReconnect = true,
        onChoose = onChoose,
        onSelected = {
            repository.putString("list_mic_button_position", it)
            onRefresh()
        },
    )
    DuoChoiceRow(
        title = "图标颜色",
        value = when (iconColor) {
            "gradient_blue" -> "蓝渐变"
            "gradient_purple" -> "紫渐变"
            "gradient_green" -> "绿渐变"
            "gradient_orange" -> "橙渐变"
            "gradient_red" -> "红渐变"
            else -> "纯白"
        },
        options = listOf(
            ChoiceOption("gradient_blue", "蓝渐变"),
            ChoiceOption("gradient_purple", "紫渐变"),
            ChoiceOption("gradient_green", "绿渐变"),
            ChoiceOption("gradient_orange", "橙渐变"),
            ChoiceOption("gradient_red", "红渐变"),
            ChoiceOption("solid_white", "纯白"),
        ),
        current = iconColor,
        enabled = enabled,
        requiresReconnect = true,
        onChoose = onChoose,
        onSelected = {
            repository.putString("list_mic_icon_color", it)
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
    DuoSettingRow(
        title = "音量处理",
        value = if (processing) "开" else "关",
        detail = "增益 / 自动平衡总开关",
        isSwitch = true,
        switchOn = processing,
        enabled = enabled,
        onActivate = {
            repository.putBoolean("checkbox_mic_volume_processing", !processing)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "固定增益",
        value = if (gain) "开" else "关",
        isSwitch = true,
        switchOn = gain,
        enabled = enabled && processing,
        onActivate = {
            repository.putBoolean("checkbox_mic_gain", !gain)
            onRefresh()
        },
    )
    DuoSliderRow(
        title = "增益量",
        valueLabel = "${if (gainDb >= 0) "+" else ""}$gainDb dB",
        value = gainDb.toFloat(),
        range = -20f..20f,
        steps = 39,
        onValueChange = { repository.putInt("seekbar_mic_gain_db", it.toInt()) },
        onValueChangeFinished = onRefresh,
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
    val multi = repository.getBoolean("checkbox_multi_controller", true)
    val joycon = repository.getBoolean("checkbox_combine_joycons", true)
    val flip = repository.getBoolean("checkbox_flip_face_buttons", false)
    val nativeHaptics = repository.getBoolean("checkbox_native_device_haptics", false)
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
private fun ControllerMousePage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val speed = repository.getInt("controller_mouse_speed_percent", 100).coerceIn(50, 200)
    val dpad = repository.getString("controller_mouse_dpad_behavior", "scroll")
    DuoSliderRow(
        title = "指针速度",
        valueLabel = "$speed%",
        detail = "右摇杆模拟鼠标的移动倍率",
        value = speed.toFloat(),
        range = 50f..200f,
        steps = 29,
        onValueChange = { repository.putInt("controller_mouse_speed_percent", it.toInt()) },
        onValueChangeFinished = onRefresh,
    )
    DuoChoiceRow(
        title = "十字键行为",
        value = if (dpad == "arrows") "方向键" else "滚轮",
        detail = "手柄鼠标模式下 D-pad 的作用",
        options = listOf(
            ChoiceOption("scroll", "滚轮"),
            ChoiceOption("arrows", "方向键"),
        ),
        current = dpad,
        onChoose = onChoose,
        onSelected = {
            repository.putString("controller_mouse_dpad_behavior", it)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "启用方式",
        value = "控制台 · 输入",
        detail = "串流中打开输入页，切换“手柄鼠标模拟”",
        enabled = false,
    )
}

@Composable
private fun TouchPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
    onChoose: (String, List<ChoiceOption>, String, (String) -> Unit) -> Unit,
) {
    val dblDrag = repository.getBoolean("pref_enable_double_click_drag", false)
    val touchKeyboard = repository.getBoolean("checkbox_touch_keyboard_auto_invoke", true)
    val enhanced = repository.getBoolean("checkbox_enable_enhanced_touch", true)
    val enhancedRight = repository.getBoolean("checkbox_enhanced_touch_on_which_side", false)
    val divider = repository.getInt("enhanced_touch_zone_divider", 50)
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
    DuoSwitchRow(
        title = "增强触摸",
        checked = enhanced,
        detail = "分区映射，适合横屏握持",
        onToggle = {
            repository.putBoolean("checkbox_enable_enhanced_touch", !enhanced)
            onRefresh()
        },
    )
    DuoSwitchRow(
        title = "增强区在右侧",
        checked = enhancedRight,
        detail = if (enhanced) "关闭时增强区在左侧" else "先开启增强触摸",
        enabled = enhanced,
        onToggle = {
            repository.putBoolean("checkbox_enhanced_touch_on_which_side", !enhancedRight)
            onRefresh()
        },
    )
    DuoSliderRow(
        title = "增强区边界",
        valueLabel = "$divider%",
        detail = "分区线位置",
        value = divider.toFloat(),
        range = 0f..100f,
        steps = 9,
        onValueChange = { repository.putInt("enhanced_touch_zone_divider", it.toInt()) },
        onValueChangeFinished = onRefresh,
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
    val gyroMouse = repository.getBoolean("gyro_to_mouse", false)
    val gyroSensitivity = repository.getFloat("gyro_sensitivity_multiplier", 1f)
    val invertX = repository.getBoolean("gyro_invert_x_axis", false)
    val invertY = repository.getBoolean("gyro_invert_y_axis", false)
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
        value = if (gyroMouse) "开" else "关",
        detail = "映射为相对鼠标移动",
        isSwitch = true,
        switchOn = gyroMouse,
        onActivate = {
            repository.putBoolean("gyro_to_mouse", !gyroMouse)
            onRefresh()
        },
    )
    DuoSliderRow(
        title = "陀螺仪灵敏度",
        valueLabel = String.format(java.util.Locale.US, "%.1f×", gyroSensitivity),
        value = gyroSensitivity,
        range = 0.2f..3.0f,
        steps = 27,
        onValueChange = { repository.putFloat("gyro_sensitivity_multiplier", it) },
        onValueChangeFinished = onRefresh,
    )
    DuoSwitchRow(
        title = "反转 X 轴",
        checked = invertX,
        onToggle = {
            repository.putBoolean("gyro_invert_x_axis", !invertX)
            onRefresh()
        },
    )
    DuoSwitchRow(
        title = "反转 Y 轴",
        checked = invertY,
        onToggle = {
            repository.putBoolean("gyro_invert_y_axis", !invertY)
            onRefresh()
        },
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
private fun DiagnosticsPage(
    onOpenHelp: () -> Unit,
    onOpenHosts: () -> Unit,
) {
    val context = LocalContext.current
    val config = context.resources.configuration
    val codecCount = remember {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.count { info ->
                !info.isEncoder && info.supportedTypes.any { it.startsWith("video/") }
            }
        }.getOrDefault(0)
    }
    val inputDeviceCount = remember {
        runCatching { InputDevice.getDeviceIds().size }.getOrDefault(0)
    }
    DuoSettingRow(
        title = "设备",
        value = "${Build.MANUFACTURER} ${Build.MODEL}",
        enabled = false,
    )
    DuoSettingRow(
        title = "系统",
        value = "Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}",
        detail = Build.SUPPORTED_ABIS.firstOrNull() ?: "—",
        enabled = false,
    )
    DuoSettingRow(
        title = "下屏布局",
        value = "${config.screenWidthDp} × ${config.screenHeightDp} dp",
        detail = "当前窗口",
        enabled = false,
    )
    DuoSettingRow(
        title = "视频解码器",
        value = "$codecCount 个",
        detail = "H.264 / HEVC / AV1（硬件与软件）",
        enabled = false,
    )
    DuoSettingRow(
        title = "输入设备",
        value = "$inputDeviceCount 个",
        detail = "系统报告的手柄 / 键盘 / 触控设备",
        enabled = false,
    )
    DuoSettingRow(
        title = "网络诊断",
        value = "选择主机",
        detail = "对当前主机做 TCP 端口探测",
        isSubpage = true,
        onActivate = onOpenHosts,
    )
    DuoSettingRow(
        title = "帮助文档",
        value = "打开",
        detail = "Sunshine / Moonlight 设置指南",
        isSubpage = true,
        onActivate = onOpenHelp,
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

/**
 * Secondary page for upstream options that the HTML prototype does not show.
 * It keeps the one-entry "高级" row promise: every unrepresented control stays
 * reachable without pushing extra rows into the first-level page.
 */
@Composable
private fun AdvancedPage(
    category: String,
    entries: List<AdvancedEntry>,
    onSubpage: (String) -> Unit,
) {
    Text(
        text = "$category · 上游高级项",
        style = LumaType.micro,
        color = LumaColors.Tertiary,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
    entries.forEach { entry ->
        SubpageEntry(entry.title, entry.value, onSubpage, entry.subpage)
    }
}

@Composable
private fun HostAdvancedPage(
    repository: DuoSettingsRepository,
    version: Int,
    onRefresh: () -> Unit,
) {
    val lock = repository.getBoolean("checkbox_lock_screen_after_disconnect", false)
    DuoSwitchRow(
        title = "断开后锁屏",
        checked = lock,
        detail = "串流结束后锁定 Windows 会话",
        onToggle = {
            repository.putBoolean("checkbox_lock_screen_after_disconnect", !lock)
            onRefresh()
        },
    )
    DuoSettingRow(
        title = "主机端能力",
        value = "Foundation",
        detail = "WebUI 见主机“更多”；解锁在串流工具页",
        enabled = false,
    )
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

private fun settingsTopRows(
    category: DuoSetCat,
    scene: DuoSettingsRepository.Snapshot,
    repository: DuoSettingsRepository,
    remote: DuoRemoteRepository.Snapshot,
    runtime: DuoRemoteController.State,
): List<String> = when (category) {
    DuoSetCat.VIDEO -> listOf(
        "分辨率 · ${scene.resolutionLabel}",
        "帧率 · ${scene.fps} Hz",
        "码率 · ${scene.bitrateMbps} Mbps",
        "智能码率 · ${if (scene.adaptiveBitrate) "开" else "关"}",
        "视频格式 · ${scene.formatLabel}",
    )
    DuoSetCat.AUDIO -> listOf(
        "声道 · ${scene.audioLabel}",
        "音频直通 · ${if (repository.getBoolean("checkbox_enable_audio_passthrough", false)) "开" else "关"}",
        "麦克风 · ${if (repository.getBoolean("checkbox_enable_mic", false)) "开" else "关"}",
        "空间音频 · ${if (repository.getBoolean("checkbox_enable_spatializer", false)) "开" else "关"}",
    )
    DuoSetCat.INPUT -> listOf(
        "摇杆死区 · ${repository.getInt("seekbar_deadzone", 7)}%",
        "震动 · ${scene.rumbleLabel}",
        "触摸模式 · ${touchPresetLabel(repository.touchPreset())}",
        "陀螺仪 · ${if (repository.getBoolean("gyro_to_right_stick", false)) "开" else "关"}",
    )
    DuoSetCat.NETWORK -> listOf(
        "EasyTier · " + when (runtime.phase) {
            DuoRemoteController.Phase.CONNECTED -> "已连接"
            DuoRemoteController.Phase.STARTING -> "启动中"
            DuoRemoteController.Phase.NEED_PERMISSION -> "待授权"
            DuoRemoteController.Phase.ERROR -> "错误"
            else -> "未启动"
        },
        "网络 · ${remote.networkName}",
        "本机 IP · ${runtime.virtualIp ?: remote.ipv4}/24",
        "STUN · ${if (repository.getBoolean("checkbox_enable_stun", false)) "开" else "关"}",
    )
    DuoSetCat.HOST -> listOf(
        "主机优化 · ${if (repository.getBoolean("checkbox_enable_sops", true)) "开" else "关"}",
        "屏幕组合 · ${screenModeLabel(repository.getString("list_screen_combination_mode", "-1"))}",
        "主机音频 · ${if (repository.getBoolean("checkbox_host_audio", false)) "开" else "关"}",
        "后台串流 · " + when (repository.backgroundMode()) {
            BackgroundStreamBehavior.RESUME -> "恢复"
            BackgroundStreamBehavior.KEEP_CONNECTED -> "保持"
            BackgroundStreamBehavior.KEEP_AUDIO -> "保持+音频"
            else -> "断开"
        },
    )
    DuoSetCat.INTERFACE -> listOf(
        "语言 · 简体中文",
        "主题 · 深色 · Lumen Gold",
        "状态页指标 · 默认 5 项",
        "控制台不透明度 · 固定",
    )
    DuoSetCat.ABOUT -> listOf(
        "版本 · ${BuildConfig.VERSION_NAME}",
        "上游 · Moonlight V+ 12.15.17",
        "许可证 · GPL-3.0",
        "隐私 · 无遥测",
    )
}

private fun touchPresetLabel(preset: TouchModePreset): String = when (preset) {
    TouchModePreset.ENHANCED -> "增强"
    TouchModePreset.CLASSIC -> "经典"
    TouchModePreset.TRACKPAD -> "触控板"
    TouchModePreset.NATIVE -> "原生"
}

/** 80 -> "0.8×", 100 -> "1.0×"; avoids locale-dependent decimal separators. */
private fun touchpadSpeedLabel(speed: Int): String = "${speed / 100}.${(speed % 100) / 10}×"

private fun screenModeLabel(value: String): String = when (value) {
    "0" -> "不操作"
    "1" -> "自动激活"
    "2" -> "主屏"
    "3" -> "独占"
    "4" -> "副屏"
    else -> "主机默认"
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
