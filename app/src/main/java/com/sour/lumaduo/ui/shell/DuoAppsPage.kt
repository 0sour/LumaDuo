// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.NvApp
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType

enum class DuoAppAction {
    LAUNCH,
    LAUNCH_LAST,
    QUIT_AND_LAUNCH,
    QUIT,
    HIDE,
    INFO,
    SHORTCUT,
}

@Composable
fun DuoAppsPage(
    host: ComputerDetails?,
    apps: List<NvApp>,
    loading: Boolean,
    error: String?,
    icons: Map<Int, Bitmap> = emptyMap(),
    onOpenApp: (NvApp) -> Unit,
    onAppAction: (NvApp, DuoAppAction) -> Unit,
    onSelectionChanged: (NvApp?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(apps.size) {
        if (apps.isEmpty()) {
            selectedIndex = 0
        } else {
            selectedIndex = selectedIndex.coerceIn(0, apps.lastIndex)
        }
    }
    val selected = apps.getOrNull(selectedIndex.coerceIn(0, (apps.size - 1).coerceAtLeast(0)))
    LaunchedEffect(selected?.appId) {
        onSelectionChanged(selected)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // 640dp bottom screen minus the 64dp rail and page padding still leaves
        // enough room for the prototype's 3-column grid. Only narrow single-screen
        // fallbacks (Thor/phone) drop to 2 columns.
        val columns = if (maxWidth < 480.dp) 2 else 3
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            DuoSectionHeader(
                "全部应用",
                when {
                    loading -> "读取中"
                    error != null -> "读取失败"
                    else -> "${apps.size} 个 · 实时"
                },
            )
            Spacer(Modifier.height(6.dp))
            when {
                host == null -> DuoEmpty("先在主机页选择一台主机", "应用列表来自当前主机", Modifier.weight(1f))
                loading -> DuoEmpty("正在读取主机应用列表…", modifier = Modifier.weight(1f))
                error != null -> DuoEmpty("读取失败：$error", "检查主机在线状态后重试", Modifier.weight(1f))
                apps.isEmpty() -> DuoEmpty("主机没有可启动的应用", "在 PC 上启动一个游戏或桌面", Modifier.weight(1f))
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        itemsIndexed(apps, key = { _, app -> app.appId }) { index, app ->
                            DuoAppCard(
                                app = app,
                                icon = icons[app.appId],
                                selected = index == selectedIndex,
                                onFocus = { selectedIndex = index },
                                onClick = {
                                    selectedIndex = index
                                    onOpenApp(app)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    DuoAppDetail(
                        host = host,
                        app = selected,
                        icon = selected?.let { icons[it.appId] },
                        onAction = onAppAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuoAppCard(
    app: NvApp,
    icon: Bitmap?,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val color = duoAppColors[((app.appId % duoAppColors.size) + duoAppColors.size) % duoAppColors.size]
    val initial = app.appName.trim().firstOrNull()?.uppercase() ?: "?"
    LumaFocusable(
        onClick = onClick,
        onFocused = { focused -> if (focused) onFocus() },
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(4.dp),
        cornerRadius = 10.dp,
    ) { focused ->
        Column(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.card)
                .background(
                    when {
                        selected -> LumaColors.Accent.copy(alpha = 0.10f)
                        focused -> LumaColors.Surface3
                        else -> LumaColors.Surface1
                    },
                )
                .border(
                    1.dp,
                    if (selected) LumaColors.Accent.copy(alpha = 0.45f) else Color.Transparent,
                    LumaShapes.card,
                )
                .padding(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(LumaShapes.control)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = initial,
                            style = LumaType.titleM.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF121216),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (app.isHdrSupported()) DuoChip("HDR", LumaColors.Signal)
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

@Composable
private fun DuoAppDetail(
    host: ComputerDetails,
    app: NvApp?,
    icon: Bitmap?,
    onAction: (NvApp, DuoAppAction) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app == null) {
            Text("选择一个应用", style = LumaType.bodyM, color = LumaColors.Tertiary)
            return@Row
        }
        val color = duoAppColors[((app.appId % duoAppColors.size) + duoAppColors.size) % duoAppColors.size]
        Box(
            Modifier
                .size(38.dp)
                .clip(LumaShapes.control)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = app.appName.trim().firstOrNull()?.uppercase() ?: "?",
                    style = LumaType.titleM.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF121216),
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = LumaType.bodyM.copy(fontWeight = FontWeight.Medium),
                color = LumaColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = host.name ?: "主机",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        DuoTextButton(
            text = "启动串流",
            onClick = { onAction(app, DuoAppAction.LAUNCH) },
            accent = true,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(6.dp))
        DuoTextButton(
            text = "应用操作",
            onClick = { onAction(app, DuoAppAction.INFO) },
            modifier = Modifier.width(92.dp),
        )
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
