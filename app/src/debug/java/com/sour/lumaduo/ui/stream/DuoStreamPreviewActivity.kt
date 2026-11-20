// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.stream

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import androidx.activity.ComponentActivity
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
import com.sour.lumaduo.R
import com.sour.lumaduo.duo.DuoRemoteController
import com.sour.lumaduo.duo.DuoSettingsRepository
import com.sour.lumaduo.duo.SecondScreenPresentation
import com.sour.lumaduo.duo.findPresentationDisplay
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaTheme
import com.sour.lumaduo.ui.luma.LumaType

/**
 * Debug-only console layout preview. It intentionally carries no fake metrics: the state is
 * "not connected", so status cells show dashes until M3 wires a real session snapshot.
 *
 * adb shell am start -n com.sour.lumaduo.debug/com.sour.lumaduo.ui.stream.DuoStreamPreviewActivity
 */
class DuoStreamPreviewActivity : ComponentActivity() {
    private var presentation: SecondScreenPresentation? = null
    private var display: Display? = null
    private var metrics: DisplayMetrics? = null
    private var mode by mutableStateOf(DuoStreamMode.LOCAL)
    private val settingsRepository by lazy { DuoSettingsRepository(this) }
    private val state = DuoStreamState(
        connected = false,
        hostName = "控制台预览 · 无会话",
        address = "未连接",
        networkLabel = "—",
        easyTierLabel = "未启动",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        display = findPresentationDisplay(this, windowManager.defaultDisplay.displayId)
        metrics = display?.let { DisplayMetrics().also { m -> it.getRealMetrics(m) } }
        setContent {
            LumaTheme { PreviewTop(display, metrics) }
        }
    }

    override fun onStart() {
        super.onStart()
        showPresentation()
    }

    override fun onStop() {
        presentation?.hide()
        super.onStop()
    }

    override fun onDestroy() {
        presentation?.dismiss()
        presentation = null
        super.onDestroy()
    }

    private fun showPresentation() {
        if (presentation != null) {
            presentation?.show()
            return
        }
        val target = display ?: return
        val scene = settingsRepository.read()
        val actions = object : DuoStreamActions {
            override fun setMode(mode: DuoStreamMode) {
                this@DuoStreamPreviewActivity.mode = mode
            }
            override fun exitStream() = finish()
            override fun reconnect() = Unit
            override fun disconnect() = Unit
            override fun openRemote() = Unit
            override fun networkTest() = Unit
            override fun localKeyboard() = Unit
            override fun hostKeyboard() = Unit
            override fun sleepHost() = Unit
            override fun unlockHost() = Unit
            override fun mouseMove(dx: Float, dy: Float) = Unit
            override fun mouseButton(button: Int) = Unit
            override fun sendKey(key: String) = Unit
            override fun sendShortcut(label: String) = Unit
        }
        val p = SecondScreenPresentation(
            activity = this,
            display = target,
            onBack = {
                if (mode == DuoStreamMode.LOCAL) {
                    mode = DuoStreamMode.PASSTHROUGH
                } else {
                    finish()
                }
            },
        ) {
            DuoStreamConsole(
                state = state,
                mode = mode,
                actions = actions,
                settingsRepository = settingsRepository,
                scene = scene,
                remoteRuntime = DuoRemoteController.State(),
            )
        }
        presentation = p
        p.show()
    }
}

@Composable
private fun PreviewTop(display: Display?, metrics: DisplayMetrics?) {
    Box(
        Modifier.fillMaxSize().background(LumaColors.StageBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.duo_logo_full),
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(LumaShapes.panel),
            )
            Spacer(Modifier.height(14.dp))
            Text("LumaDuo", style = LumaType.display, color = LumaColors.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                "串流控制台 UI 预览 · 无真实会话",
                style = LumaType.titleM,
                color = LumaColors.Secondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (display != null && metrics != null) {
                    "下屏 ${metrics.widthPixels}×${metrics.heightPixels} @${metrics.densityDpi}dpi"
                } else {
                    "未找到下屏"
                },
                style = LumaType.caption,
                color = LumaColors.Accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "状态页只显示 —/未连接，不伪造帧率、延迟或主机负载",
                style = LumaType.caption,
                color = LumaColors.Tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
