// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sour.lumaduo.AppView
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.PcView
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoHostRepository
import com.sour.lumaduo.duo.SecondScreenPresentation
import com.sour.lumaduo.duo.findPresentationDisplay
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaTheme
import com.sour.lumaduo.ui.luma.LumaType
import kotlinx.coroutines.flow.StateFlow

/**
 * Debug-only 640x480 shell probe. Launch it on either physical display to check rail,
 * app bar, mode bar and focus behavior without touching the production PcView flow.
 *
 * adb shell am start -n com.sour.lumaduo.debug/com.sour.lumaduo.ui.shell.DuoShellProbeActivity
 * adb shell am start --display 2 -n com.sour.lumaduo.debug/com.sour.lumaduo.ui.shell.DuoShellProbeActivity
 */
class DuoShellProbeActivity : ComponentActivity() {
    private var bottomPresentation: SecondScreenPresentation? = null
    private var bottomDisplay: Display? = null
    private val hostRepository by lazy { DuoHostRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        bottomDisplay = findPresentationDisplay(this, windowManager.defaultDisplay.displayId)
        val metrics = bottomDisplay?.let { display ->
            DisplayMetrics().also { display.getRealMetrics(it) }
        }
        LimeLog.info(
            "LumaDuo probe: bottom display=" +
                "${bottomDisplay?.displayId} ${metrics?.widthPixels}x${metrics?.heightPixels} " +
                "@${metrics?.densityDpi}dpi"
        )
        setContent {
            LumaTheme {
                ProbeTopScreen(bottomDisplay, metrics)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hostRepository.start()
        showBottomPresentation()
    }

    override fun onStop() {
        // Keep the Presentation instance (and Compose state) across a temporary stop;
        // dismiss only when the Activity is going away.
        bottomPresentation?.hide()
        super.onStop()
    }

    override fun onDestroy() {
        hostRepository.stop()
        bottomPresentation?.dismiss()
        bottomPresentation = null
        super.onDestroy()
    }

    private fun showBottomPresentation() {
        if (bottomPresentation != null) {
            bottomPresentation?.show()
            return
        }
        val display = bottomDisplay ?: run {
            LimeLog.warning("LumaDuo probe: no presentation display; staying single-screen")
            return
        }
        val presentation = SecondScreenPresentation(
            activity = this,
            display = display,
            onBack = {
                LimeLog.info("LumaDuo probe: bottom back/escape received")
                Toast.makeText(this, "下屏收到 Return/Back", Toast.LENGTH_SHORT).show()
            },
        ) {
            DuoShellProbe(
                hosts = hostRepository.hosts,
                onOpenHost = ::openHost,
                onOpenLegacy = { startActivity(Intent(this, PcView::class.java)) },
                onRefresh = { hostRepository.refresh() },
            )
        }
        bottomPresentation = presentation
        presentation.show()
        LimeLog.info("LumaDuo probe: presentation shown on display ${display.displayId}")
    }

    private fun openHost(host: ComputerDetails) {
        if (host.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(this, "主机离线", Toast.LENGTH_SHORT).show()
            return
        }
        if (host.pairState != PairingManager.PairState.PAIRED) {
            Toast.makeText(this, "未配对：打开旧版主机管理", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PcView::class.java))
            return
        }
        startActivity(
            Intent(this, AppView::class.java).apply {
                putExtra(AppView.NAME_EXTRA, host.name)
                putExtra(AppView.UUID_EXTRA, host.uuid)
            },
        )
    }
}

@Composable
private fun ProbeTopScreen(display: Display?, metrics: DisplayMetrics?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LumaColors.StageBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.duo_logo_full),
                contentDescription = "LumaDuo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(LumaShapes.panel),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "LumaDuo",
                style = LumaType.display,
                color = LumaColors.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "上屏只读 · 双屏壳层探针",
                style = LumaType.titleM,
                color = LumaColors.Secondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            val info = if (display != null && metrics != null) {
                "下屏 display ${display.displayId} · ${metrics.widthPixels}×${metrics.heightPixels} " +
                    "@${metrics.densityDpi}dpi"
            } else {
                "未找到 FLAG_PRESENTATION 下屏 · 单屏回退"
            }
            Text(
                text = info,
                style = LumaType.caption,
                color = if (display != null) LumaColors.Accent else LumaColors.Warning,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "下屏可触摸/手柄操作；Return 由 Presentation 自己接收",
                style = LumaType.caption,
                color = LumaColors.Tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class ProbeHost(
    val uuid: String,
    val name: String,
    val address: String,
    val tag: String,
    val online: Boolean,
    val paired: Boolean,
)

private data class DemoApp(
    val name: String,
    val initial: String,
    val color: Color,
)

private val demoApps = listOf(
    DemoApp("Steam 大屏", "S", Color(0xFF7BA7C7)),
    DemoApp("桌面", "D", Color(0xFF9B9BA8)),
    DemoApp("赛博朋克 2077", "C", Color(0xFFF2C14E)),
    DemoApp("极限竞速：地平线 5", "F", Color(0xFF4CC38A)),
    DemoApp("RetroArch", "R", Color(0xFF5ED0FF)),
    DemoApp("Moonlight 测试", "M", Color(0xFFA78BFA)),
)

@Composable
private fun DuoShellProbe(
    hosts: StateFlow<List<ComputerDetails>>,
    onOpenHost: (ComputerDetails) -> Unit,
    onOpenLegacy: () -> Unit,
    onRefresh: () -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(DuoDestination.HOSTS.name) }
    var selectedUuid by rememberSaveable { mutableStateOf("") }
    var selectedSettings by rememberSaveable { mutableIntStateOf(0) }
    val hostList by hosts.collectAsState()
    val destination = DuoDestination.valueOf(destinationName)
    val context = LocalContext.current
    val effectiveSelectedUuid = selectedUuid.ifEmpty { hostList.firstOrNull()?.uuid.orEmpty() }
    val selectedHost = hostList.firstOrNull { it.uuid == effectiveSelectedUuid }

    val title = when (destination) {
        DuoDestination.HOSTS -> "主机"
        DuoDestination.APPS -> selectedHost?.name?.let { "$it · 应用" } ?: "应用"
        DuoDestination.REMOTE -> "远程"
        DuoDestination.SETTINGS -> "设置"
    }
    val subtitle = when (destination) {
        DuoDestination.HOSTS -> "真实数据 · ComputerManagerService"
        DuoDestination.APPS -> "DEMO · 应用网格待接入"
        DuoDestination.REMOTE -> "DEMO · EasyTier 状态占位"
        DuoDestination.SETTINGS -> "DEMO · 分类与行布局"
    }
    val actions = when (destination) {
        DuoDestination.HOSTS -> listOf(
            DuoAction(R.drawable.duo_ic_plus, "添加主机") {
                onOpenLegacy()
            },
            DuoAction(R.drawable.duo_ic_refresh, "刷新主机") {
                onRefresh()
            },
        )
        DuoDestination.APPS -> listOf(
            DuoAction(R.drawable.duo_ic_refresh, "刷新应用") {
                Toast.makeText(context, "应用网格待接入", Toast.LENGTH_SHORT).show()
            },
        )
        else -> emptyList()
    }

    LumaDuoShell(
        destination = destination,
        onDestinationChange = { destinationName = it.name },
        title = title,
        subtitle = subtitle,
        actions = actions,
        modeLabel = "手柄：下屏",
        modeHint = "真实主机 · 点击连接",
        keyHints = listOf(
            DuoKeyHint("A", "连接"),
            DuoKeyHint("B", "返回"),
        ),
    ) {
        when (destination) {
            DuoDestination.HOSTS -> ProbeHosts(
                hosts = hostList,
                selectedUuid = effectiveSelectedUuid,
                onOpenHost = { host ->
                    selectedUuid = host.uuid.orEmpty()
                    onOpenHost(host)
                },
            )
            DuoDestination.APPS -> DemoApps()
            DuoDestination.REMOTE -> DemoRemote()
            DuoDestination.SETTINGS -> DemoSettings(
                selected = selectedSettings,
                onSelect = { selectedSettings = it },
            )
        }
    }
}

private fun ComputerDetails.toProbeHost(): ProbeHost {
    val address = (activeAddress ?: localAddress ?: remoteAddress ?: ipv6Address)?.address ?: "—"
    val tag = when {
        activeAddress != null && activeAddress == localAddress -> "LAN"
        manualAddress != null -> "手动"
        else -> "远程"
    }
    return ProbeHost(
        uuid = uuid.orEmpty(),
        name = name ?: "未命名主机",
        address = address,
        tag = tag,
        online = state == ComputerDetails.State.ONLINE,
        paired = pairState == PairingManager.PairState.PAIRED,
    )
}

@Composable
private fun ProbeHosts(
    hosts: List<ComputerDetails>,
    selectedUuid: String?,
    onOpenHost: (ComputerDetails) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DemoSectionHeader("最近主机", "${hosts.size} 台 · 实时")
        Spacer(Modifier.height(8.dp))
        if (hosts.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "还没有主机",
                    style = LumaType.titleM,
                    color = LumaColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "用右上角 + 打开旧版主机管理添加/配对",
                    style = LumaType.caption,
                    color = LumaColors.Tertiary,
                )
            }
            return@Column
        }
        hosts.forEach { computer ->
            val host = computer.toProbeHost()
            val selected = host.uuid == selectedUuid
            LumaFocusable(
                onClick = { onOpenHost(computer) },
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
                                    colors = listOf(
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
                                    .background(if (host.online) LumaColors.Success else LumaColors.Disabled),
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
        Spacer(Modifier.height(6.dp))
        Text(
            text = "已配对接现有 AppView；未配对接旧版主机管理。应用页仍为 DEMO。",
            style = LumaType.caption,
            color = LumaColors.Tertiary,
        )
    }
}

@Composable
private fun DemoApps() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DemoSectionHeader("全部应用", "6 个 · DEMO")
        Spacer(Modifier.height(8.dp))
        demoApps.chunked(3).forEach { rowApps ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowApps.forEach { app ->
                    LumaFocusable(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
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
                                    .background(app.color),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = app.initial,
                                    style = LumaType.titleM.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF121216),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = app.name,
                                style = LumaType.bodyM,
                                color = LumaColors.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat(3 - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DemoRemote() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DemoSectionHeader("EasyTier", "已连接 · DEMO")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoInfoPanel(
                modifier = Modifier.weight(1.35f),
                title = "网络",
                rows = listOf(
                    "网络名" to "family-net",
                    "密钥" to "••••••••",
                    "Peer" to "public.easytier.top:11010",
                ),
            )
            DemoInfoPanel(
                modifier = Modifier.weight(1f),
                title = "本机",
                rows = listOf(
                    "虚拟 IP" to "10.0.0.1/24",
                    "节点" to "2",
                    "延迟" to "18 ms",
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "启动/停止、复制 PC 配置、二维码和手动添加主机在 M2 页面接入。",
            style = LumaType.caption,
            color = LumaColors.Tertiary,
        )
    }
}

@Composable
private fun DemoSettings(selected: Int, onSelect: (Int) -> Unit) {
    val categories = listOf("画面", "音频", "输入", "网络", "外观", "关于")
    val rows = when (selected) {
        0 -> listOf("分辨率" to "1920 × 1080", "帧率" to "60 Hz", "码率" to "50 Mbps", "HDR" to "开")
        1 -> listOf("音量" to "80%", "静音" to "关", "输出" to "设备")
        2 -> listOf("输入模式" to "直通优先", "震动" to "开", "键盘" to "数字 + ASCII")
        3 -> listOf("EasyTier 自启" to "开", "虚拟 IP" to "10.0.0.1/24")
        4 -> listOf("主题" to "深色", "强调色" to "Lumen Gold")
        else -> listOf("版本" to "0.1.0", "许可证" to "GPL-3.0")
    }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(132.dp)) {
            categories.forEachIndexed { index, name ->
                LumaFocusable(
                    onClick = { onSelect(index) },
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
                            text = name,
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
            DemoSectionHeader(categories[selected], "DEMO")
            rows.forEach { (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = LumaType.bodyM,
                        color = LumaColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = value,
                        style = LumaType.caption,
                        color = LumaColors.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoSectionHeader(title: String, meta: String) {
    Row(
        Modifier.fillMaxWidth(),
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
private fun DemoInfoPanel(
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
                    modifier = Modifier.width(72.dp),
                )
                Text(
                    text = value,
                    style = LumaType.caption,
                    color = LumaColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
