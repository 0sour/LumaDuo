// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.R
import com.sour.lumaduo.nvstream.http.ComputerDetails
import com.sour.lumaduo.nvstream.http.PairingManager
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType

enum class DuoHostAction {
    CONNECT,
    APPS,
    PAIR,
    WAKE,
    NETWORK_TEST,
    DETAILS,
    SLEEP,
    QUIT,
    WEBUI,
    IPV6,
    UNLOCK,
    DELETE,
    MORE,
}

@Composable
fun DuoHostsPage(
    hosts: List<ComputerDetails>,
    selectedUuid: String?,
    onSelect: (ComputerDetails) -> Unit,
    onAction: (ComputerDetails, DuoHostAction) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = hosts.firstOrNull { it.uuid == selectedUuid } ?: hosts.firstOrNull()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val detailWidth = if (maxWidth < 560.dp) 200.dp else 232.dp
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxSize()) {
                DuoSectionHeader("最近主机", "${hosts.size} 台 · 实时")
                Spacer(Modifier.height(6.dp))
                if (hosts.isEmpty()) {
                    DuoEmpty(
                        title = "还没有主机",
                        detail = "手动添加 PC，或先在远程页连接 EasyTier",
                        modifier = Modifier.weight(1f),
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DuoTextButton("添加主机", onClick = onAdd, accent = true)
                                DuoTextButton("刷新", onClick = onRefresh)
                            }
                        },
                    )
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(hosts, key = { it.uuid.orEmpty() + (it.name ?: "") }) { host ->
                            DuoHostRow(
                                host = host,
                                selected = host.uuid == selected?.uuid,
                                onFocus = { if (host.uuid != selected?.uuid) onSelect(host) },
                                onClick = { onSelect(host) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "A 选择 · → 详情 · + 添加",
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                )
            }
            DuoHostDetail(
                host = selected,
                onAction = onAction,
                onAdd = onAdd,
                modifier = Modifier.width(detailWidth).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DuoHostRow(
    host: ComputerDetails,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val online = host.state == ComputerDetails.State.ONLINE
    val paired = host.pairState == PairingManager.PairState.PAIRED
    val address = (host.activeAddress ?: host.localAddress ?: host.remoteAddress ?: host.ipv6Address)
        ?.address ?: "—"
    val tag = when {
        host.activeAddress != null && host.activeAddress == host.localAddress -> "LAN"
        host.manualAddress != null -> "手动"
        else -> "远程"
    }
    LumaFocusable(
        onClick = onClick,
        onFocused = { focused -> if (focused) onFocus() },
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp),
        cornerRadius = 10.dp,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .clip(LumaShapes.card)
                .background(
                    when {
                        selected -> Brush.horizontalGradient(
                            listOf(LumaColors.Accent.copy(alpha = 0.13f), LumaColors.Surface1),
                        )
                        focused -> Brush.horizontalGradient(listOf(LumaColors.Surface3, LumaColors.Surface3))
                        else -> Brush.horizontalGradient(listOf(LumaColors.Surface1, LumaColors.Surface1))
                    },
                )
                .border(
                    1.dp,
                    if (selected) LumaColors.Accent.copy(alpha = 0.38f) else Color.Transparent,
                    LumaShapes.card,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.duo_ic_monitor),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (online) LumaColors.Accent else LumaColors.Disabled,
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (online) LumaColors.Success else LumaColors.Disabled),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = host.name ?: "未命名主机",
                        style = LumaType.bodyM.copy(fontWeight = FontWeight.Medium),
                        color = if (online) LumaColors.Text else LumaColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (host.runningGameId != 0) {
                        Spacer(Modifier.width(6.dp))
                        DuoChip("运行中")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$address · ${if (paired) "已配对" else "未配对"}",
                    style = LumaType.micro,
                    color = LumaColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = tag,
                style = LumaType.micro.copy(fontWeight = FontWeight.Medium),
                color = if (tag == "LAN") LumaColors.Tertiary else LumaColors.Signal,
                modifier = Modifier
                    .clip(LumaShapes.chip)
                    .background(LumaColors.Surface3)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DuoHostDetail(
    host: ComputerDetails?,
    onAction: (ComputerDetails, DuoHostAction) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(LumaShapes.card)
            .background(LumaColors.Surface1)
            .padding(12.dp),
    ) {
        if (host == null) {
            DuoEmpty(
                title = "选择一台主机",
                detail = "右侧显示详情与操作",
                modifier = Modifier.weight(1f),
                action = { DuoTextButton("添加主机", onClick = onAdd, accent = true) },
            )
            return
        }
        val online = host.state == ComputerDetails.State.ONLINE
        val paired = host.pairState == PairingManager.PairState.PAIRED
        val address = (host.activeAddress ?: host.localAddress ?: host.remoteAddress ?: host.ipv6Address)
            ?.address ?: "—"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = host.name ?: "未命名主机",
                    style = LumaType.titleM,
                    color = LumaColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = address,
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (online) LumaColors.Success else LumaColors.Disabled),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DuoChip(if (online) "在线" else "离线", if (online) LumaColors.Success else LumaColors.Disabled)
            DuoChip(if (paired) "已配对" else "未配对")
            if (host.runningGameId != 0) DuoChip("运行中", LumaColors.Signal)
        }
        Spacer(Modifier.height(10.dp))
        DetailLine("地址", address)
        DetailLine("主机名", host.name ?: "—")
        DetailLine("配对", if (paired) "证书就绪" else "需要配对")
        DetailLine("Sunshine", host.sunshineVersion ?: "—")
        Spacer(Modifier.weight(1f))

        val primary = when {
            host.runningGameId != 0 && online && paired -> "继续串流" to DuoHostAction.CONNECT
            !paired && online -> "配对" to DuoHostAction.PAIR
            !online -> "唤醒主机" to DuoHostAction.WAKE
            else -> "应用列表" to DuoHostAction.APPS
        }
        DuoTextButton(
            text = primary.first,
            onClick = { onAction(host, primary.second) },
            accent = online || primary.second == DuoHostAction.WAKE,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuoTextButton(
                "应用列表",
                onClick = { onAction(host, DuoHostAction.APPS) },
                enabled = online && paired,
                modifier = Modifier.weight(1f),
            )
            DuoTextButton(
                "网络测试",
                onClick = { onAction(host, DuoHostAction.NETWORK_TEST) },
                enabled = online,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuoTextButton(
                "详情",
                onClick = { onAction(host, DuoHostAction.DETAILS) },
                modifier = Modifier.weight(1f),
            )
            DuoTextButton(
                "更多",
                onClick = { onAction(host, DuoHostAction.MORE) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        DuoTextButton(
            "删除主机",
            onClick = { onAction(host, DuoHostAction.DELETE) },
            danger = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(label, style = LumaType.micro, color = LumaColors.Tertiary, modifier = Modifier.width(50.dp))
        Text(
            value,
            style = LumaType.micro,
            color = LumaColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
