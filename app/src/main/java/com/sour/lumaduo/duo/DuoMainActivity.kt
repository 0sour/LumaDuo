// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
    private var bottomDisplay: Display? = null
    private var bottomMetrics: DisplayMetrics? = null
    private val hostRepository by lazy { DuoHostRepository(this) }
    private val settingsRepository by lazy { DuoSettingsRepository(this) }
    private val remoteRepository by lazy { DuoRemoteRepository(this) }

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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ConfigurationSyncScheduler.runNow(this)

        settingsSnapshot = settingsRepository.read()
        remoteSnapshot = remoteRepository.read()
        remoteConfig = remoteRepository.readConfig()

        bottomDisplay = findPresentationDisplay(this, windowManager.defaultDisplay.displayId)
        bottomMetrics = bottomDisplay?.let { display ->
            DisplayMetrics().also { display.getRealMetrics(it) }
        }
        LimeLog.info(
            "LumaDuo: bottom display=${bottomDisplay?.displayId} " +
                "${bottomMetrics?.widthPixels}x${bottomMetrics?.heightPixels} " +
                "@${bottomMetrics?.densityDpi}dpi",
        )
        setContent {
            LumaTheme {
                DuoTopScreen(
                    display = bottomDisplay,
                    metrics = bottomMetrics,
                    hostCount = hostRepository.hosts.collectAsState().value.size,
                    destination = destination,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hostRepository.start()
        settingsSnapshot = settingsRepository.read()
        remoteSnapshot = remoteRepository.read()
        remoteConfig = remoteRepository.readConfig()
        showBottomPresentation()
    }

    override fun onStop() {
        bottomPresentation?.hide()
        super.onStop()
    }

    override fun onDestroy() {
        remoteController.destroy()
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
            LimeLog.warning("LumaDuo: no presentation display; staying single-screen")
            return
        }
        val presentation = SecondScreenPresentation(
            activity = this,
            display = display,
            onBack = ::handleBottomBack,
        ) {
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
                rootBackArmed = rootBackArmed,
            )
        }
        bottomPresentation = presentation
        presentation.show()
        LimeLog.info("LumaDuo: bottom shell shown on display ${display.displayId}")
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
        startActivity(Intent(this, PcView::class.java))
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
) {
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
                text = "上屏只读 · 所有操作在下屏",
                style = LumaType.titleM,
                color = LumaColors.Secondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            val location = when (destination) {
                DuoDestination.HOSTS -> "主机"
                DuoDestination.APPS -> "应用"
                DuoDestination.REMOTE -> "远程"
                DuoDestination.SETTINGS -> "设置"
            }
            Text(
                text = if (display != null && metrics != null) {
                    "下屏 ${metrics.widthPixels}×${metrics.heightPixels} @${metrics.densityDpi}dpi · $location"
                } else {
                    "未找到下屏 · 单屏回退尚未接入"
                },
                style = LumaType.caption,
                color = if (display != null) LumaColors.Accent else LumaColors.Warning,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hostCount > 0) "已发现 $hostCount 台主机" else "尚未发现主机",
                style = LumaType.caption,
                color = LumaColors.Tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
