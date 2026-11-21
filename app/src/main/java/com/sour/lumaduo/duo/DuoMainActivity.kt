// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            DuoHostAction.NETWORK_TEST -> notice("网络测试在连接页/主机详情接入（M3）")
            DuoHostAction.WEBUI -> notice("Sunshine WebUI 在 M4 接入")
            DuoHostAction.UNLOCK -> notice("主机自动解锁在 P2 接入")
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
        notice("桌面快捷方式在 P1 接入")
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
                    .size(64.dp)
                    .clip(LumaShapes.panel),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "LumaDuo",
                style = LumaType.titleL,
                color = LumaColors.Text,
            )
            Spacer(Modifier.height(12.dp))

            if (display == null) {
                Text(
                    text = "未检测到下屏",
                    style = LumaType.titleM,
                    color = LumaColors.Warning,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "单屏回退尚未接入",
                    style = LumaType.bodyM,
                    color = LumaColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            } else if (context.hostName != null) {
                Text(
                    text = context.hostName,
                    style = LumaType.titleM,
                    color = LumaColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = context.appName ?: "在下屏选择应用",
                    style = LumaType.bodyM,
                    color = if (context.appName != null) LumaColors.Accent else LumaColors.Secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = if (hostCount > 0) "已发现 $hostCount 台主机" else "等待主机",
                    style = LumaType.titleM,
                    color = LumaColors.Secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "在下屏选择",
                    style = LumaType.bodyM,
                    color = LumaColors.Accent,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .width(200.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LumaColors.Accent.copy(alpha = 0.45f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "下屏：$location · 上屏只读",
                style = LumaType.caption,
                color = LumaColors.Tertiary,
                textAlign = TextAlign.Center,
            )
            if (BuildConfig.DEBUG && metrics != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${metrics.widthPixels}×${metrics.heightPixels} @${metrics.densityDpi}dpi",
                    style = LumaType.micro,
                    color = LumaColors.Disabled,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
