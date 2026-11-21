// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sour.lumaduo.duo.DuoRemoteController
import com.sour.lumaduo.duo.DuoRemoteRepository
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType

@Composable
fun DuoRemotePage(
    snapshot: DuoRemoteRepository.Snapshot,
    runtime: DuoRemoteController.State,
    onStartStop: () -> Unit,
    onEditConfig: () -> Unit,
    onAdvanced: () -> Unit,
    onCopyPcConfig: () -> Unit,
    onShowQr: () -> Unit,
    onAddManualHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = runtime.phase == DuoRemoteController.Phase.STARTING ||
        runtime.phase == DuoRemoteController.Phase.CONNECTED
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RemoteConfigCard(
                snapshot = snapshot,
                runtime = runtime,
                running = running,
                onStartStop = onStartStop,
                onEdit = onEditConfig,
                onAdvanced = onAdvanced,
                onCopyPcConfig = onCopyPcConfig,
                onShowQr = onShowQr,
                modifier = Modifier.weight(1.25f).fillMaxSize(),
            )
            RemoteStatusCard(
                runtime = runtime,
                running = running,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(LumaShapes.card)
                .background(LumaColors.Surface1)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("手动添加主机", style = LumaType.bodyM, color = LumaColors.Text)
                Text(
                    "PC 端 EasyTier 使用 .2",
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                )
            }
            LumaFocusable(
                onClick = onAddManualHost,
                modifier = Modifier.width(140.dp).height(38.dp),
                cornerRadius = 8.dp,
            ) { focused ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(LumaShapes.control)
                        .background(if (focused) LumaColors.Surface3 else LumaColors.Surface2)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("10.0.0.2", style = LumaType.bodyM, color = LumaColors.Secondary)
                }
            }
            Spacer(Modifier.width(6.dp))
            DuoTextButton(
                text = "添加",
                onClick = onAddManualHost,
                accent = true,
                modifier = Modifier.width(70.dp),
            )
        }
    }
}

@Composable
private fun RemoteConfigCard(
    snapshot: DuoRemoteRepository.Snapshot,
    runtime: DuoRemoteController.State,
    running: Boolean,
    onStartStop: () -> Unit,
    onEdit: () -> Unit,
    onAdvanced: () -> Unit,
    onCopyPcConfig: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EasyTier", style = LumaType.titleM, color = LumaColors.Text)
                Text(
                    "Android 内置客户端",
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                )
            }
            DuoChip(
                text = when (runtime.phase) {
                    DuoRemoteController.Phase.CONNECTED -> "已连接"
                    DuoRemoteController.Phase.STARTING -> "启动中"
                    DuoRemoteController.Phase.NEED_PERMISSION -> "待授权"
                    DuoRemoteController.Phase.ERROR -> "错误"
                    else -> "未启动"
                },
                tone = when (runtime.phase) {
                    DuoRemoteController.Phase.CONNECTED -> LumaColors.Success
                    DuoRemoteController.Phase.STARTING -> LumaColors.Accent
                    DuoRemoteController.Phase.NEED_PERMISSION -> LumaColors.Warning
                    DuoRemoteController.Phase.ERROR -> LumaColors.Error
                    else -> LumaColors.Secondary
                },
            )
        }
        Spacer(Modifier.height(7.dp))
        RemoteLine("网络名", snapshot.networkName)
        RemoteLine("网络密钥", if (snapshot.hasSecret) "••••••••••••" else "未设置")
        RemoteLine("本机虚拟 IP", "${runtime.virtualIp ?: snapshot.ipv4}/24")
        RemoteLine("Peer", snapshot.peers.firstOrNull()?.let {
            if (snapshot.peers.size > 1) "$it +${snapshot.peers.size - 1}" else it
        } ?: "—")
        RemoteLine("PC 虚拟 IP", "10.0.0.2/24")
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuoTextButton(
                text = when {
                    running -> "停止 EasyTier"
                    runtime.phase == DuoRemoteController.Phase.NEED_PERMISSION -> "授权 EasyTier"
                    else -> "启动 EasyTier"
                },
                onClick = onStartStop,
                accent = !running,
                modifier = Modifier.weight(1f),
            )
            DuoTextButton("编辑", onClick = onEdit, modifier = Modifier.width(64.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuoTextButton("复制 PC 配置", onClick = onCopyPcConfig, modifier = Modifier.weight(1f))
            DuoTextButton("二维码", onClick = onShowQr, modifier = Modifier.weight(1f))
            DuoTextButton("高级", onClick = onAdvanced, modifier = Modifier.width(58.dp))
        }
    }
}

@Composable
private fun RemoteStatusCard(
    runtime: DuoRemoteController.State,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val tone = when (runtime.phase) {
        DuoRemoteController.Phase.CONNECTED -> LumaColors.Success
        DuoRemoteController.Phase.STARTING -> LumaColors.Accent
        DuoRemoteController.Phase.NEED_PERMISSION -> LumaColors.Warning
        DuoRemoteController.Phase.ERROR -> LumaColors.Error
        else -> LumaColors.Disabled
    }
    val title = when (runtime.phase) {
        DuoRemoteController.Phase.STOPPED -> "未启动"
        DuoRemoteController.Phase.NEED_PERMISSION -> "待授权"
        DuoRemoteController.Phase.STARTING -> "启动中"
        DuoRemoteController.Phase.CONNECTED -> "已连接"
        DuoRemoteController.Phase.ERROR -> "错误"
    }
    val detail = when {
        runtime.error != null -> runtime.error
        runtime.phase == DuoRemoteController.Phase.CONNECTED ->
            runtime.latencyMs?.let { "P2P · $it ms" } ?: "virtual LAN"
        runtime.phase == DuoRemoteController.Phase.STARTING -> "正在加入网络"
        runtime.phase == DuoRemoteController.Phase.NEED_PERMISSION -> "需要 VPN 权限"
        runtime.phase == DuoRemoteController.Phase.ERROR -> "检查配置后重试"
        else -> "等待启动"
    }
    Column(
        modifier
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(10.dp),
    ) {
        Text("连接状态", style = LumaType.micro, color = LumaColors.Tertiary)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = LumaType.titleM, color = LumaColors.Text)
                Text(
                    text = detail,
                    style = LumaType.micro,
                    color = if (runtime.error != null) LumaColors.Error else LumaColors.Tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(91.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LumaColors.Surface2)
                .drawBehind {
                    drawLine(
                        color = LumaColors.Accent.copy(alpha = 0.35f),
                        start = Offset(size.width * 0.2f, size.height / 2f),
                        end = Offset(size.width * 0.8f, size.height / 2f),
                        strokeWidth = 1.5f,
                    )
                },
        ) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(com.sour.lumaduo.R.drawable.duo_ic_gamepad),
                    contentDescription = null,
                    tint = LumaColors.Secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text("本机", style = LumaType.micro, color = LumaColors.Tertiary)
            }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LumaColors.Surface2)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("EasyTier", style = LumaType.label, color = LumaColors.Accent)
                Text(
                    if (running) "virtual LAN" else "offline",
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                )
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(com.sour.lumaduo.R.drawable.duo_ic_monitor),
                    contentDescription = null,
                    tint = LumaColors.Secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text("PC", style = LumaType.micro, color = LumaColors.Tertiary)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            RemoteStat("节点", if (running) runtime.peers.toString() else "0", Modifier.weight(1f))
            RemoteStat("延迟", runtime.latencyMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
            // EasyTier's network info has no packet-loss field; never invent 0.0%.
            RemoteStat("丢包", "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RemoteStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = LumaType.label, color = LumaColors.Text)
        Text(label, style = LumaType.micro, color = LumaColors.Tertiary)
    }
}

@Composable
private fun RemoteLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(label, style = LumaType.micro, color = LumaColors.Tertiary, modifier = Modifier.width(62.dp))
        Text(
            value,
            style = LumaType.micro,
            color = LumaColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DuoRemoteConfigEditor(
    config: DuoRemoteRepository.Config,
    onSave: (DuoRemoteRepository.Config) -> Unit,
    onDismiss: () -> Unit,
) {
    var networkName by rememberSaveable { mutableStateOf(config.networkName) }
    var secret by rememberSaveable { mutableStateOf(config.secret) }
    var ipv4 by rememberSaveable { mutableStateOf(config.ipv4) }
    var peers by rememberSaveable { mutableStateOf(config.peers) }
    var listeners by rememberSaveable { mutableStateOf(config.listeners) }
    var revealSecret by rememberSaveable { mutableStateOf(false) }
    DuoOverlay(onDismiss = onDismiss, width = 460.dp) {
        Column {
            Text("编辑 EasyTier 配置", style = LumaType.titleM, color = LumaColors.Text)
            Text(
                "保存后若 EasyTier 正在运行会重启实例。密钥不会写入日志。",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            Spacer(Modifier.height(10.dp))
            ConfigField("网络名", networkName, onValueChange = { networkName = it })
            Spacer(Modifier.height(6.dp))
            ConfigField(
                label = "网络密钥${if (revealSecret) "" else " · 已遮罩"}",
                value = if (revealSecret) secret else "••••••••",
                onValueChange = { secret = it },
                enabled = revealSecret,
                trailing = {
                    DuoTextButton(
                        if (revealSecret) "隐藏" else "显示",
                        onClick = { revealSecret = !revealSecret },
                        modifier = Modifier.width(64.dp).height(32.dp),
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
            ConfigField("虚拟 IPv4", ipv4, onValueChange = { ipv4 = it })
            Spacer(Modifier.height(6.dp))
            ConfigField("Peer（每行一个）", peers, singleLine = false, onValueChange = { peers = it })
            Spacer(Modifier.height(6.dp))
            ConfigField("监听（逗号分隔）", listeners, singleLine = false, onValueChange = { listeners = it })
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuoTextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                DuoTextButton(
                    "保存",
                    onClick = {
                        onSave(
                            config.copy(
                                networkName = networkName.trim(),
                                secret = secret,
                                ipv4 = ipv4.trim(),
                                peers = peers.trim(),
                                listeners = listeners.trim(),
                            ),
                        )
                    },
                    accent = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Text(label, style = LumaType.micro, color = LumaColors.Tertiary)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(LumaShapes.control)
                    .background(LumaColors.Bg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = if (enabled) LumaColors.Text else LumaColors.Secondary,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

@Composable
fun DuoQrOverlay(
    content: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(content) { runCatching { qrBitmap(content, 640) }.getOrNull() }
    DuoOverlay(onDismiss = onDismiss, width = 420.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("PC 配置二维码", style = LumaType.titleM, color = LumaColors.Text)
            Text(
                "在 PC 端 EasyTier 导入；二维码包含网络密钥。",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .size(224.dp)
                    .clip(LumaShapes.control)
                    .background(Color.White)
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "PC 配置二维码")
                } else {
                    Text("无法生成二维码", style = LumaType.caption, color = Color.Black)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuoTextButton(
                    "复制配置",
                    onClick = { copyToClipboard(context, "LumaDuo EasyTier PC", content) },
                    modifier = Modifier.weight(1f),
                )
                DuoTextButton("关闭", onClick = onDismiss, accent = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DuoRemoteAdvanced(
    config: DuoRemoteRepository.Config,
    onSave: (DuoRemoteRepository.Config) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(config) { mutableStateOf(config) }
    DuoOverlay(onDismiss = onDismiss, width = 460.dp) {
        Column {
            Text("EasyTier 高级 flags", style = LumaType.titleM, color = LumaColors.Text)
            Text("默认关闭；不确定时保持默认。", style = LumaType.micro, color = LumaColors.Tertiary)
            Spacer(Modifier.height(8.dp))
            AdvancedToggle("smoltcp", value.useSmoltcp) { value = value.copy(useSmoltcp = it) }
            AdvancedToggle("延迟优先", value.latencyFirst) { value = value.copy(latencyFirst = it) }
            AdvancedToggle("禁用 P2P", value.disableP2p) { value = value.copy(disableP2p = it) }
            AdvancedToggle("私有模式", value.privateMode) { value = value.copy(privateMode = it) }
            AdvancedToggle("禁用 IPv6", value.disableIpv6) { value = value.copy(disableIpv6 = it) }
            AdvancedToggle("KCP 代理", value.enableKcpProxy) { value = value.copy(enableKcpProxy = it) }
            AdvancedToggle("QUIC 代理", value.enableQuicProxy) { value = value.copy(enableQuicProxy = it) }
            AdvancedToggle("系统代理转发", value.proxyForwardBySystem) {
                value = value.copy(proxyForwardBySystem = it)
            }
            AdvancedToggle("禁用加密", value.disableEncryption) {
                value = value.copy(disableEncryption = it)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuoTextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                DuoTextButton(
                    "保存",
                    onClick = { onSave(value) },
                    accent = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AdvancedToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    DuoSettingRow(
        title = label,
        value = if (checked) "开" else "关",
        isSwitch = true,
        switchOn = checked,
        onActivate = { onToggle(!checked) },
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun qrBitmap(content: String, size: Int): android.graphics.Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
