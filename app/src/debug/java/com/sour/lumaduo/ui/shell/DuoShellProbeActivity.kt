// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.PcView
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoHostRepository
import com.sour.lumaduo.duo.DuoRemoteRepository
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.duo.SecondScreenPresentation
import com.sour.lumaduo.duo.findPresentationDisplay
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.NvApp
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaTheme
import com.sour.lumaduo.ui.luma.LumaType
import com.sour.lumaduo.utils.ServerHelper

/**
 * Debug-only 640x480 probe. It now hosts the production [DuoShellScreen] against real data,
 * so layout/focus checks exercise the same composables as [com.sour.lumaduo.duo.DuoMainActivity].
 *
 * adb shell am start -n com.sour.lumaduo.debug/com.sour.lumaduo.ui.shell.DuoShellProbeActivity
 */
class DuoShellProbeActivity : ComponentActivity() {
    private var bottomPresentation: SecondScreenPresentation? = null
    private var bottomDisplay: Display? = null
    private var bottomMetrics: DisplayMetrics? = null
    private val hostRepository by lazy { DuoHostRepository(this) }
    private val settingsRepository by lazy { DuoSettingsRepository(this) }
    private val remoteRepository by lazy { DuoRemoteRepository(this) }

    private var destination by mutableStateOf(DuoDestination.HOSTS)
    private var settingsSnapshot by mutableStateOf(
        DuoSettingsRepository.Snapshot("", "", false, 0, 0, "", false, false, "", ""),
    )
    private var remoteSnapshot by mutableStateOf(
        DuoRemoteRepository.Snapshot("", "", false, emptyList(), emptyList(), true),
    )
    private var settingsNotice by mutableStateOf<String?>(null)
    private var rootBackArmed by mutableStateOf(false)
    private var lastBackAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
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
        settingsSnapshot = settingsRepository.read()
        remoteSnapshot = remoteRepository.read()
        bottomDisplay = findPresentationDisplay(this, windowManager.defaultDisplay.displayId)
        bottomMetrics = bottomDisplay?.let { display ->
            DisplayMetrics().also { display.getRealMetrics(it) }
        }
        LimeLog.info(
            "LumaDuo probe: bottom display=${bottomDisplay?.displayId} " +
                "${bottomMetrics?.widthPixels}x${bottomMetrics?.heightPixels} " +
                "@${bottomMetrics?.densityDpi}dpi",
        )
        setContent {
            LumaTheme {
                ProbeTopScreen(
                    display = bottomDisplay,
                    metrics = bottomMetrics,
                    hostCount = hostRepository.hosts.collectAsState().value.size,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hostRepository.start()
        settingsSnapshot = settingsRepository.read()
        remoteSnapshot = remoteRepository.read()
        showBottomPresentation()
    }

    override fun onStop() {
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
            onBack = ::handleBottomBack,
        ) {
            LaunchedEffect(destination) {
                when (destination) {
                    DuoDestination.REMOTE -> remoteSnapshot = remoteRepository.read()
                    DuoDestination.SETTINGS -> settingsSnapshot = settingsRepository.read()
                    else -> Unit
                }
            }
            DuoShellScreen(
                destination = destination,
                onDestinationChange = { next ->
                    destination = next
                    rootBackArmed = false
                    settingsNotice = null
                },
                hosts = hostRepository.hosts,
                apps = hostRepository.apps,
                appsLoading = hostRepository.appsLoading,
                appsError = hostRepository.appsError,
                settings = settingsSnapshot,
                remote = remoteSnapshot,
                onOpenLegacy = { startActivity(Intent(this, PcView::class.java)) },
                onRefreshHosts = { hostRepository.refresh() },
                onRequestApps = { hostRepository.loadApps(it) },
                onRefreshApps = { host -> hostRepository.refreshAppList(host) },
                onOpenApp = ::openApp,
                onSettingsAdjust = ::adjustSetting,
                settingsNotice = settingsNotice,
                onSettingsNoticeExpired = { settingsNotice = null },
                rootBackArmed = rootBackArmed,
            )
        }
        bottomPresentation = presentation
        presentation.show()
        LimeLog.info("LumaDuo probe: presentation shown on display ${display.displayId}")
    }

    private fun adjustSetting(field: DuoSettingsRepository.Field, delta: Int) {
        settingsSnapshot = settingsRepository.adjust(field, delta)
        settingsNotice = "已保存 · 下次串流生效"
    }

    private fun handleBottomBack() {
        if (destination != DuoDestination.HOSTS) {
            destination = DuoDestination.HOSTS
            rootBackArmed = false
            settingsNotice = null
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

    private fun openApp(host: ComputerDetails, app: NvApp) {
        if (host.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(this, "主机离线", Toast.LENGTH_SHORT).show()
            return
        }
        if (host.pairState != PairingManager.PairState.PAIRED) {
            Toast.makeText(this, "未配对：打开旧版主机管理", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PcView::class.java))
            return
        }
        val binder = hostRepository.currentBinder() ?: run {
            Toast.makeText(this, "主机服务尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        ServerHelper.doStart(this, app, host, binder)
    }
}

@Composable
private fun ProbeTopScreen(
    display: Display?,
    metrics: DisplayMetrics?,
    hostCount: Int,
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
                text = if (hostCount > 0) "已发现 $hostCount 台主机" else "尚未发现主机",
                style = LumaType.caption,
                color = LumaColors.Tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
