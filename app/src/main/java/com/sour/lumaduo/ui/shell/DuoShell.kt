// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sour.lumaduo.R
import com.sour.lumaduo.ui.luma.LumaColors
import com.sour.lumaduo.ui.luma.LumaFocusable
import com.sour.lumaduo.ui.luma.LumaMetrics
import com.sour.lumaduo.ui.luma.LumaShapes
import com.sour.lumaduo.ui.luma.LumaType

enum class DuoDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    HOSTS(R.string.duo_rail_hosts, R.drawable.duo_ic_host),
    APPS(R.string.duo_rail_apps, R.drawable.duo_ic_apps),
    REMOTE(R.string.duo_rail_remote, R.drawable.duo_ic_globe),
    SETTINGS(R.string.duo_rail_settings, R.drawable.duo_ic_sliders),
}

data class DuoAction(
    @DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
)

data class DuoKeyHint(val key: String, val label: String)

enum class DuoModeTone(val color: Color) {
    LOCAL(LumaColors.Accent),
    PASSTHROUGH(LumaColors.Signal),
    MENU(LumaColors.Warning),
}

/**
 * The fixed LumaDuo browse shell: 64dp rail, 44dp app bar, flexible content, 28dp mode bar.
 * Content pages own their focus tree; the shell only guarantees the first rail item is focused.
 */
@Composable
fun LumaDuoShell(
    destination: DuoDestination,
    onDestinationChange: (DuoDestination) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: List<DuoAction> = emptyList(),
    modeLabel: String = stringResource(R.string.duo_mode_local),
    modeHint: String? = null,
    modeTone: DuoModeTone = DuoModeTone.LOCAL,
    keyHints: List<DuoKeyHint> = listOf(
        DuoKeyHint("A", stringResource(R.string.duo_key_confirm)),
        DuoKeyHint("B", stringResource(R.string.duo_key_back)),
    ),
    content: @Composable () -> Unit,
) {
    val requesters = remember {
        DuoDestination.entries.associateWith { FocusRequester() }
    }
    LaunchedEffect(Unit) {
        requesters[destination]?.requestFocus()
    }

    Row(
        modifier
            .fillMaxSize()
            .background(LumaColors.Bg),
    ) {
        DuoRail(
            destination = destination,
            onDestinationChange = onDestinationChange,
            requesters = requesters,
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DuoAppBar(title = title, subtitle = subtitle, actions = actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
            DuoModeBar(
                label = modeLabel,
                hint = modeHint,
                tone = modeTone,
                keyHints = keyHints,
            )
        }
    }
}

@Composable
private fun DuoRail(
    destination: DuoDestination,
    onDestinationChange: (DuoDestination) -> Unit,
    requesters: Map<DuoDestination, FocusRequester>,
) {
    Column(
        Modifier
            .width(LumaMetrics.RailWidth)
            .fillMaxHeight()
            .background(LumaColors.Surface2)
            .drawBehind {
                drawLine(
                    color = LumaColors.Outline,
                    start = Offset(size.width - 1f, 0f),
                    end = Offset(size.width - 1f, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.duo_logo_full),
            contentDescription = stringResource(R.string.duo_brand_description),
            modifier = Modifier
                .padding(top = 2.dp, bottom = 10.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        DuoDestination.entries.forEach { item ->
            val selected = item == destination
            LumaFocusable(
                onClick = { onDestinationChange(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 2.dp)
                    .focusRequester(requesters.getValue(item)),
                cornerRadius = 8.dp,
            ) { focused ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(LumaShapes.control)
                        .background(
                            when {
                                selected -> LumaColors.AccentSoft
                                focused -> LumaColors.Surface3
                                else -> Color.Transparent
                            },
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (selected) LumaColors.Accent else LumaColors.Secondary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(item.labelRes),
                        style = LumaType.micro,
                        color = if (selected) LumaColors.Accent else LumaColors.Secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuoAppBar(
    title: String,
    subtitle: String?,
    actions: List<DuoAction>,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(LumaMetrics.AppBarHeight)
            .background(LumaColors.Surface1)
            .drawBehind {
                drawLine(
                    color = LumaColors.Outline,
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
            }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LumaType.titleM,
                color = LumaColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LumaType.micro,
                    color = LumaColors.Tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.forEach { action ->
                LumaFocusable(
                    onClick = action.onClick,
                    modifier = Modifier.size(LumaMetrics.TouchTarget),
                    cornerRadius = 8.dp,
                ) { focused ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(LumaShapes.control)
                            .background(if (focused) LumaColors.Surface3 else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = action.contentDescription,
                            modifier = Modifier.size(18.dp),
                            tint = LumaColors.Secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuoModeBar(
    label: String,
    hint: String?,
    tone: DuoModeTone,
    keyHints: List<DuoKeyHint>,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(LumaMetrics.ModeBarHeight)
            .background(LumaColors.Surface1)
            .drawBehind {
                drawLine(
                    color = LumaColors.Outline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(tone.color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = LumaType.micro,
            color = LumaColors.Secondary,
            maxLines = 1,
        )
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "· $hint",
                style = LumaType.micro,
                color = LumaColors.Tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            keyHints.forEach { keyHint ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = keyHint.key,
                        style = LumaType.micro.copy(fontWeight = FontWeight.SemiBold),
                        color = LumaColors.Accent,
                        modifier = Modifier
                            .clip(LumaShapes.chip)
                            .background(LumaColors.Surface3)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Text(
                        text = keyHint.label,
                        style = LumaType.micro,
                        color = LumaColors.Tertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Shared content inset for pages inside the browse shell. */
@Composable
fun DuoPagePadding(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = LumaMetrics.PagePadding, vertical = 10.dp)
            .widthIn(max = 960.dp),
    ) {
        content()
    }
}
