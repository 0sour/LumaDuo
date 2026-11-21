// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.BuildConfig
import com.sour.lumaduo.PcView
import com.sour.lumaduo.R
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.NvApp
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaTheme
import com.sour.lumaduo.ui.luma.LumaType
import com.sour.lumaduo.ui.shell.DuoDestination
import com.sour.lumaduo.utils.ShortcutHelper
import com.sour.lumaduo.ui.shell.DuoChip
import com.sour.lumaduo.ui.shell.DuoHostAction
import com.sour.lumaduo.ui.shell.DuoShellHost
import com.sour.lumaduo.ui.shell.DuoShellScreen
import com.sour.lumaduo.ui.shell.DuoTopContext
import com.sour.lumaduo.utils.AppSettingsManager
import com.sour.lumaduo.utils.ConfigurationSyncScheduler
import com.sour.lumaduo.utils.ServerHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Production dual-screen entry point.
 *
 * Top display: read-only brand/status Activity. Bottom display: a [SecondScreenPresentation]
 * hosting the interactive Compose shell. All controls stay on the bottom screen; the top
 * window never owns buttons, focus or overlays.
 */
class DuoMainActivity : ComponentActivity(), DuoShellHost {
    private var bottomPresentation: SecondScreenPresentation? = null
    private var bottomDisplay by mutableStateOf<Display?>(null)
    private var bottomMetrics by mutableStateOf<DisplayMetrics?>(null)
    private var topContext by mutableStateOf(DuoTopContext())
    private var started = false
    private val hostRepository by lazy { DuoHostRepository(this) }
    private val settingsRepository by lazy { DuoSettingsRepository(this) }
    private val remoteRepository by lazy { DuoRemoteRepository(this) }
    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplays()
        override fun onDisplayRemoved(displayId: Int) = refreshDisplays()
        override fun onDisplayChanged(displayId: Int) = refreshDisplays()
    }

    private val vpnPermissionLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        remoteController.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }
    private val remoteController by lazy {
        DuoRemoteController(this, remoteRepository) { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    private var destination by mutableStateOf(DuoDestination.HOSTS)
    private var settingsSnapshot by mutableStateOf(
        DuoSettingsRepository.Snapshot("", "", false, 0, 0, "", false, false, "", ""),
    )
    private var remoteSnapshot by mutableStateOf(
        DuoRemoteRepository.Snapshot("", "", false, emptyList(), emptyList(), true),
    )
    private var remoteConfig by mutableStateOf(
        DuoRemoteRepository.Config(
            "", "", "", "", "", false, false, false, false, false,
            false, false, false, false, false, false, false, false,
        ),
    )
    private var shellNotice by mutableStateOf<String?>(null)
    private var rootBackArmed by mutableStateOf(false)
    private var lastBackAt = 0L
    private var lastConfirmAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = handleBottomBack()
            },
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ConfigurationSyncScheduler.runNow(this)

        settingsSnapshot = settingsRepository.read()
        remoteSnapshot = remoteRepository.read()
        remoteConfig = remoteRepository.readConfig()

        bottomDisplay = findPresentationDisplay(this, ownDisplayId())
        bottomMetrics = bottomDisplay?.let(::metricsFor)
        LimeLog.info(
            "LumaDuo: bottom display=${bottomDisplay?.displayId} " +
                "${bottomMetrics?.widthPixels}x${bottomMetrics?.heightPixels} " +
                "@${bottomMetrics?.densityDpi}dpi",
        )
        setContent {
            LumaTheme {
                if (bottomDisplay == null) {
                    // Single-screen fallback: the same shell lives in the Activity window.
                    DuoShellContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                    )
                } else {
                    DuoTopScreen(
                        display = bottomDisplay,
                        metrics = bottomMetrics,
                        hostCount = hostRepository.hosts.collectAsState().value.size,
                        destination = destination,
                        context = topContext,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        hostRepository.start()
        settingsSnapshot = settingsRepository.read()
        remoteSnapshot = remoteRepository.read()
        remoteConfig = remoteRepository.readConfig()
        refreshDisplays()
    }

    override fun onStop() {
        started = false
        displayManager?.unregisterDisplayListener(displayListener)
        bottomPresentation?.hide()
        super.onStop()
    }

    override fun onDestroy() {
        displayManager?.unregisterDisplayListener(displayListener)
        remoteController.destroy()
        hostRepository.stop()
        bottomPresentation?.dismiss()
        bottomPresentation = null
        super.onDestroy()
    }

    /** Re-homes the bottom shell when a display is added, removed or resized. */
    private fun refreshDisplays() {
        val target = findPresentationDisplay(this, ownDisplayId())
        val changed = target?.displayId != bottomDisplay?.displayId
        if (changed) {
            bottomPresentation?.dismiss()
            bottomPresentation = null
            bottomDisplay = target
            bottomMetrics = target?.let(::metricsFor)
        } else if (target != null) {
            val metrics = metricsFor(target)
            val previous = bottomMetrics
            if (previous == null ||
                previous.widthPixels != metrics.widthPixels ||
                previous.heightPixels != metrics.heightPixels ||
                previous.densityDpi != metrics.densityDpi
            ) {
                bottomMetrics = metrics
            }
        }
        if (!started) return
        if (target == null) {
            bottomPresentation?.dismiss()
            bottomPresentation = null
            bottomMetrics = null
        } else {
            showBottomPresentation()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Single-screen fallback hosts the shell in this window, so it needs the same
        // physical B-confirm / A-back contract as the bottom Presentation.
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
        ) {
            if (event.repeatCount > 0) return true
            val now = SystemClock.uptimeMillis()
            val duplicate = now - lastConfirmAt < 40L
            lastConfirmAt = now
            if (duplicate) return true
        }
        val typing = (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.isAcceptingText == true
        val faceKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B
        if (typing && !faceKey) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_BACK ||
                event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_A)
        ) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    private fun ownDisplayId(): Int = windowManager.defaultDisplay.displayId

    private fun metricsFor(display: Display): DisplayMetrics =
        DisplayMetrics().also { display.getRealMetrics(it) }

    private fun showBottomPresentation() {
        bottomPresentation?.let { existing ->
            try {
                existing.show()
                return
            } catch (e: WindowManager.InvalidDisplayException) {
                LimeLog.warning("LumaDuo: bottom display disappeared: ${e.message}")
                runCatching { existing.dismiss() }
                bottomPresentation = null
                bottomMetrics = null
                scheduleDisplayRefresh()
                return
            } catch (e: WindowManager.BadTokenException) {
                LimeLog.warning("LumaDuo: bottom presentation token lost: ${e.message}")
                runCatching { existing.dismiss() }
                bottomPresentation = null
                scheduleDisplayRefresh()
                return
            }
        }
        val display = bottomDisplay ?: run {
            LimeLog.warning("LumaDuo: no presentation display; staying single-screen")
            return
        }
        val presentation = SecondScreenPresentation(
            activity = this,
            display = display,
            onBack = ::handleBottomBack,
        ) {
            DuoShellContent()
        }
        try {
            presentation.show()
            bottomPresentation = presentation
            LimeLog.info("LumaDuo: bottom shell shown on display ${display.displayId}")
        } catch (e: WindowManager.InvalidDisplayException) {
            LimeLog.warning("LumaDuo: bottom display disappeared before show: ${e.message}")
            runCatching { presentation.dismiss() }
            scheduleDisplayRefresh()
        } catch (e: WindowManager.BadTokenException) {
            LimeLog.warning("LumaDuo: bottom presentation token invalid: ${e.message}")
            runCatching { presentation.dismiss() }
            scheduleDisplayRefresh()
        }
    }

    @Composable
    private fun DuoShellContent(modifier: Modifier = Modifier) {
        LaunchedEffect(destination) {
            when (destination) {
                DuoDestination.REMOTE -> {
                    remoteSnapshot = remoteRepository.read()
                    remoteConfig = remoteRepository.readConfig()
                }
                DuoDestination.SETTINGS -> settingsSnapshot = settingsRepository.read()
                else -> Unit
            }
        }
        DuoShellScreen(
            destination = destination,
            onDestinationChange = { next ->
                destination = next
                rootBackArmed = false
                shellNotice = null
            },
            hosts = hostRepository.hosts,
            apps = hostRepository.apps,
            appsLoading = hostRepository.appsLoading,
            appsError = hostRepository.appsError,
            appIcons = hostRepository.appIcons,
            settings = settingsSnapshot,
            remote = remoteSnapshot,
            remoteConfig = remoteConfig,
            runtime = remoteController.state.collectAsState().value,
            pairState = hostRepository.pairState.collectAsState().value,
            settingsRepository = settingsRepository,
            host = this,
            onSettingsChanged = { settingsSnapshot = it },
            externalNotice = shellNotice,
            onExternalNoticeConsumed = { shellNotice = null },
            onTopContextChanged = { topContext = it },
            rootBackArmed = rootBackArmed,
            modifier = modifier,
        )
    }

    private fun scheduleDisplayRefresh() {
        if (!started) return
        mainHandler.postDelayed({ if (started) refreshDisplays() }, 400L)
    }

    private fun handleBottomBack() {
        if (destination != DuoDestination.HOSTS) {
            destination = DuoDestination.HOSTS
            rootBackArmed = false
            shellNotice = null
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt <= 2_000L) {
            finish()
        } else {
            lastBackAt = now
            rootBackArmed = true
        }
    }

    // ==================== DuoShellHost ====================

    override fun connect(host: ComputerDetails) {
        val running = if (host.runningGameId != 0) {
            hostRepository.apps.value.firstOrNull { it.appId == host.runningGameId }
                ?: NvApp("运行中的应用", host.runningGameId, false)
        } else {
            null
        }
        if (running != null) {
            openAppInternal(host, running, useLastSettings = false, forceResume = true)
        } else {
            openApps(host)
            notice("没有运行中的会话，已打开应用列表")
        }
    }

    override fun openApps(host: ComputerDetails) {
        hostRepository.loadApps(host)
    }

    override fun refreshApps(host: ComputerDetails) {
        hostRepository.refreshAppList(host)
    }

    override fun refreshHosts() {
        hostRepository.refresh()
        notice("主机列表已刷新")
        mainHandler.postDelayed({
            if (shellNotice == "主机列表已刷新") shellNotice = null
        }, 2_000L)
    }

    override fun pair(host: ComputerDetails) {
        hostRepository.pair(host) { paired ->
            LimeLog.info("LumaDuo paired ${paired.name}")
        }
    }

    override fun clearPair() {
        hostRepository.clearPairState()
    }

    override fun wake(host: ComputerDetails) {
        hostRepository.wakeHost(host)
        notice("已发送唤醒包")
    }

    override fun delete(host: ComputerDetails) {
        hostRepository.deleteHost(host)
        notice("已删除 ${host.name ?: "主机"}")
    }

    override fun selectAddress(host: ComputerDetails, address: ComputerDetails.AddressTuple) {
        hostRepository.selectAddress(host, address)
        notice("已切换到 ${address}")
    }

    override fun hostMore(host: ComputerDetails, action: DuoHostAction) {
        when (action) {
            DuoHostAction.SLEEP -> hostRepository.sleepHost(host) { error ->
                notice(error?.let { "休眠失败：$it" } ?: "已发送休眠命令")
            }
            DuoHostAction.QUIT -> hostRepository.quitRunningApp(host) { error ->
                notice(error?.let { "退出失败：$it" } ?: "已退出主机应用")
            }
            DuoHostAction.IPV6 -> {
                host.ipv6Disabled = !host.ipv6Disabled
                hostRepository.currentBinder()?.updateComputer(host)
                hostRepository.refresh()
                notice(if (host.ipv6Disabled) "已禁用 IPv6" else "已启用 IPv6")
            }
            DuoHostAction.RESTORE -> {
                val target = hostRepository.hosts.value.firstOrNull {
                    it.state == ComputerDetails.State.ONLINE &&
                        it.pairState == PairingManager.PairState.PAIRED &&
                        it.runningGameId != 0
                }
                if (target == null) {
                    notice("没有可恢复的会话")
                } else {
                    connect(target)
                }
            }
            DuoHostAction.WEBUI -> {
                val address = host.activeAddress?.address
                    ?: host.availableAddresses.firstOrNull()?.address
                if (address.isNullOrBlank()) {
                    notice("主机没有可用地址")
                } else {
                    val authority = if (address.contains(':')) "[$address]" else address
                    val opened = runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$authority:47990")))
                    }.isSuccess
                    if (!opened) notice("没有可用的浏览器")
                }
            }
            DuoHostAction.NETWORK_TEST -> notice("网络测试在连接页/主机详情接入（M3）")
            DuoHostAction.UNLOCK -> notice("主机自动解锁在串流工具页的“解锁”中使用")
            else -> Unit
        }
    }

    override fun addHost(host: String, port: Int, onResult: (String?) -> Unit) {
        hostRepository.addHost(host, port) { error ->
            onResult(error)
            if (error == null) notice("主机已添加")
        }
    }

    override fun openApp(host: ComputerDetails, app: NvApp, useLastSettings: Boolean) {
        openAppInternal(host, app, useLastSettings, forceResume = false)
    }

    private fun openAppInternal(
        host: ComputerDetails,
        app: NvApp,
        useLastSettings: Boolean,
        forceResume: Boolean,
    ) {
        if (host.state == ComputerDetails.State.OFFLINE) {
            notice("主机离线")
            return
        }
        if (host.pairState != PairingManager.PairState.PAIRED) {
            notice("未配对：先在主机页完成配对")
            return
        }
        val binder = hostRepository.currentBinder() ?: run {
            notice("主机服务尚未就绪")
            return
        }
        val lastSettings = if (useLastSettings) {
            AppSettingsManager(this).getAppLastSettings(host.uuid.orEmpty(), app)
        } else {
            null
        }
        if (useLastSettings && lastSettings == null) {
            notice("没有上次设置，使用当前默认值")
        }
        val intent = ServerHelper.createStartIntent(
            this,
            app,
            host,
            binder,
            lastSettings,
            forceResumeCurrentSession = forceResume,
            duoConsole = true,
        )
        startActivity(intent)
    }

    override fun quitApp(host: ComputerDetails, app: NvApp?, thenLaunch: NvApp?) {
        hostRepository.quitRunningApp(host) { error ->
            if (error != null) {
                notice("退出失败：$error")
                return@quitRunningApp
            }
            if (thenLaunch == null) {
                notice("已退出主机应用")
            } else {
                lifecycleScope.launch {
                    delay(1_200)
                    openAppInternal(host, thenLaunch, useLastSettings = false, forceResume = false)
                }
            }
        }
    }

    override fun setAppHidden(host: ComputerDetails, app: NvApp, hidden: Boolean) {
        hostRepository.setAppHidden(host, app, hidden)
    }

    override fun appHidden(host: ComputerDetails, app: NvApp): Boolean =
        hostRepository.isAppHidden(host, app)

    override fun createShortcut(host: ComputerDetails, app: NvApp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notice("桌面快捷方式需要 Android 8.0 以上")
            return
        }
        if (host.uuid.isNullOrBlank() || host.name.isNullOrBlank()) {
            notice("主机信息尚未就绪")
            return
        }
        val icon = hostRepository.appIcons.value[app.appId]
        val requested = ShortcutHelper(this).createPinnedGameShortcut(host, app, icon)
        notice(if (requested) "已请求创建桌面快捷方式" else "当前启动器不支持固定快捷方式")
    }

    override fun startStopEasyTier() {
        val state = remoteController.state.value
        if (state.phase == DuoRemoteController.Phase.STOPPED ||
            state.phase == DuoRemoteController.Phase.ERROR ||
            state.phase == DuoRemoteController.Phase.NEED_PERMISSION
        ) {
            remoteController.start()
        } else {
            remoteController.stop()
        }
    }

    override fun saveRemoteConfig(config: DuoRemoteRepository.Config) {
        remoteRepository.saveConfig(config)
        remoteConfig = remoteRepository.readConfig()
        remoteSnapshot = remoteRepository.read()
        remoteController.reloadConfig()
        notice("EasyTier 配置已保存")
    }

    override fun saveRemoteAdvanced(config: DuoRemoteRepository.Config) {
        saveRemoteConfig(config)
    }

    override fun copyPcConfig() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("LumaDuo EasyTier PC", remoteRepository.buildPcConfig()))
        notice("PC 配置已复制（包含网络密钥）")
    }

    override fun pcConfig(): String = remoteRepository.buildPcConfig()

    override fun openLegacy() {
        if (BuildConfig.MINIMAL_EDITION) {
            notice("最小版已移除旧版主机管理")
            return
        }
        startActivity(Intent(this, PcView::class.java))
    }

    override fun openNotices() {
        com.sour.lumaduo.preferences.OpenSourceNoticesActivity.open(this)
    }

    override fun openHelp() {
        startActivity(
            Intent(this, com.sour.lumaduo.HelpActivity::class.java).apply {
                data = android.net.Uri.parse(
                    "https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide",
                )
            },
        )
    }

    override fun openConsolePreview() {
        if (!BuildConfig.DEBUG) return
        startActivity(
            Intent().setClassName(
                packageName,
                "com.sour.lumaduo.ui.stream.DuoStreamPreviewActivity",
            ),
        )
    }

    override fun notice(message: String) {
        shellNotice = message
    }
}

@Composable
private fun DuoTopScreen(
    display: Display?,
    metrics: DisplayMetrics?,
    hostCount: Int,
    destination: DuoDestination,
    context: DuoTopContext,
) {
    val location = when (destination) {
        DuoDestination.HOSTS -> "主机"
        DuoDestination.APPS -> "应用"
        DuoDestination.REMOTE -> "远程"
        DuoDestination.SETTINGS -> "设置"
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(LumaColors.StageBg),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LumaColors.Accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.76f, size.height * 0.46f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.76f, size.height * 0.46f),
            )
            drawCircle(
                color = LumaColors.Signal.copy(alpha = 0.05f),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.12f, size.height * 0.9f),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 30.dp),
        ) {
            TopBrandLine(location)
            Spacer(Modifier.weight(1f))
            if (display == null) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "未检测到下屏",
                        style = TopTitleStyle,
                        color = LumaColors.Warning,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "单屏回退尚未接入",
                        style = LumaType.bodyL,
                        color = LumaColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopCopy(
                        destination = destination,
                        context = context,
                        hostCount = hostCount,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(28.dp))
                    TopVisual(
                        destination = destination,
                        context = context,
                        modifier = Modifier.width(300.dp).height(230.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "上屏只读 · 下屏操作",
                    style = LumaType.caption,
                    color = LumaColors.Tertiary,
                )
                Spacer(Modifier.weight(1f))
                if (BuildConfig.DEBUG && metrics != null) {
                    Text(
                        text = "下屏 ${metrics.widthPixels}×${metrics.heightPixels} @${metrics.densityDpi}dpi",
                        style = LumaType.micro,
                        color = LumaColors.Disabled,
                    )
                }
            }
        }
    }
}

private val TopTitleStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.SemiBold,
)

private val TopEyebrowStyle = TextStyle(
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.sp,
    fontWeight = FontWeight.Medium,
)

@Composable
private fun TopBrandLine(location: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.duo_logo_full),
            contentDescription = "LumaDuo",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "LumaDuo",
            style = LumaType.label.copy(fontWeight = FontWeight.Bold),
            color = LumaColors.Text,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "· $location",
            style = LumaType.micro,
            color = LumaColors.Tertiary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "READ ONLY",
            style = TopEyebrowStyle,
            color = LumaColors.Accent.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun TopCopy(
    destination: DuoDestination,
    context: DuoTopContext,
    hostCount: Int,
    modifier: Modifier = Modifier,
) {
    val eyebrow: String
    val title: String
    val subtitle: String
    val tags: List<Pair<String, Color>>
    val hint: String?
    val rows: List<String>
    when (destination) {
        DuoDestination.HOSTS -> {
            val online = context.hostOnline
            val paired = context.hostPaired
            eyebrow = if (context.hostName != null) "当前主机" else "还没有主机"
            title = context.hostName ?: "先添加一台 PC"
            subtitle = if (context.hostName != null) {
                "${context.hostAddress ?: "—"} · ${if (online) "在线" else "离线"} · ${if (paired) "已配对" else "未配对"}"
            } else {
                "在下屏使用 + 添加主机，或连接 EasyTier。"
            }
            tags = buildList {
                if (context.hostName != null) {
                    add((if (online) "在线" else "离线") to (if (online) LumaColors.Success else LumaColors.Disabled))
                    add((if (paired) "已配对" else "未配对") to LumaColors.Accent)
                    context.hostTag?.let { add(it to LumaColors.Signal) }
                } else if (hostCount > 0) {
                    add("已发现 $hostCount 台" to LumaColors.Signal)
                }
            }
            hint = "在下屏选择或操作 · 上屏不接收输入"
            rows = emptyList()
        }
        DuoDestination.APPS -> {
            eyebrow = "${context.hostName ?: "未选择主机"} · 应用"
            title = context.appName ?: "选择一个应用"
            subtitle = if (context.appName != null) "在下屏选择并启动" else "应用列表来自当前主机"
            tags = buildList {
                if (context.appHdr) add("HDR" to LumaColors.Signal)
                if (context.appName != null) add("上屏预览" to LumaColors.Accent)
            }
            hint = if (context.appName != null) "上屏只读预览 · 下屏启动" else "等待下屏选择"
            rows = emptyList()
        }
        DuoDestination.REMOTE -> {
            eyebrow = "虚拟网络"
            title = context.remoteName ?: "EasyTier"
            subtitle = "PC 端使用 10.0.0.2/24"
            tags = buildList {
                add(
                    (if (context.remoteConnected) "已连接" else "未启动") to
                        (if (context.remoteConnected) LumaColors.Success else LumaColors.Disabled),
                )
                if (context.remoteConnected) add("${context.remotePeers} 节点" to LumaColors.Accent)
            }
            hint = "在下屏启动 / 停止 EasyTier"
            rows = emptyList()
        }
        DuoDestination.SETTINGS -> {
            eyebrow = "设置分类"
            title = context.settingsCategory ?: "设置"
            subtitle = context.settingsDescription ?: "在下屏调整"
            tags = emptyList()
            hint = null
            rows = context.settingsRows
        }
    }
    Column(modifier) {
        Text(eyebrow, style = TopEyebrowStyle, color = LumaColors.Accent)
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = TopTitleStyle,
            color = LumaColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = LumaType.bodyL,
            color = LumaColors.Secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { (label, tone) -> DuoChip(label, tone) }
            }
        }
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.take(5).forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(LumaColors.Accent.copy(alpha = 0.7f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(row, style = LumaType.caption, color = LumaColors.Secondary)
                    }
                }
            }
        } else if (hint != null) {
            Spacer(Modifier.height(14.dp))
            Text(hint, style = LumaType.micro, color = LumaColors.Tertiary)
        }
    }
}

@Composable
private fun TopVisual(
    destination: DuoDestination,
    context: DuoTopContext,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (destination) {
            DuoDestination.HOSTS -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(LumaColors.Surface1)
                        .border(1.dp, LumaColors.Outline, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.duo_ic_monitor),
                            contentDescription = null,
                            tint = LumaColors.Accent,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (context.hostOnline) "ONLINE" else "OFFLINE",
                            style = LumaType.micro.copy(letterSpacing = 1.5.sp),
                            color = if (context.hostOnline) LumaColors.Success else LumaColors.Disabled,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(90.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LumaColors.Surface3),
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(150.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LumaColors.Surface2),
                )
            }
            DuoDestination.APPS -> Box(
                Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(context.appColor ?: LumaColors.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.appInitial ?: "?",
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF121216),
                )
            }
            DuoDestination.REMOTE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.duo_ic_gamepad),
                        contentDescription = null,
                        tint = LumaColors.Secondary,
                        modifier = Modifier.size(34.dp),
                    )
                    Box(
                        Modifier
                            .width(44.dp)
                            .height(2.dp)
                            .background(LumaColors.Accent.copy(alpha = 0.35f)),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EasyTier", style = LumaType.label, color = LumaColors.Accent)
                        Text(
                            if (context.remoteConnected) "virtual LAN" else "offline",
                            style = LumaType.micro,
                            color = LumaColors.Tertiary,
                        )
                    }
                    Box(
                        Modifier
                            .width(44.dp)
                            .height(2.dp)
                            .background(LumaColors.Accent.copy(alpha = 0.35f)),
                    )
                    Icon(
                        painterResource(R.drawable.duo_ic_monitor),
                        contentDescription = null,
                        tint = LumaColors.Secondary,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "${context.remoteIp ?: "10.0.0.1"}/24 ↔ 10.0.0.2/24",
                    style = LumaType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = LumaColors.Secondary,
                )
            }
            DuoDestination.SETTINGS -> Box(
                Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(50))
                    .background(LumaColors.Accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(topSettingsIcon(context.settingsCategory)),
                    contentDescription = null,
                    tint = LumaColors.Accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(88.dp),
                )
            }
        }
    }
}

private fun topSettingsIcon(category: String?): Int = when (category) {
    "音频" -> R.drawable.duo_ic_volume
    "输入" -> R.drawable.duo_ic_gamepad
    "网络" -> R.drawable.duo_ic_globe
    "主机" -> R.drawable.duo_ic_host
    "界面" -> R.drawable.duo_ic_sliders
    "关于" -> R.drawable.duo_ic_apps
    else -> R.drawable.duo_ic_monitor
}
