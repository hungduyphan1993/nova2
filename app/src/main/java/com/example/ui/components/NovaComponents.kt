package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import com.example.data.local.EpgProgramEntity
import com.example.data.local.StreamItemEntity
import com.example.data.repository.SyncStage
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CyanGlowBorder
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.SlateContainerHighest
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NovaGlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accentColor: Color = ElectricCyan,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val borderColor = if (highlighted) accentColor.copy(alpha = 0.85f) else GlassBorder
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            SlateContainerHigh.copy(alpha = 0.92f),
            SlateContainer.copy(alpha = 0.85f)
        )
    )
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bgBrush)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium
    ) {
        content()
    }
}

@Composable
fun ChannelLogoBadge(
    logoUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val initials = remember(title) {
        title.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "TV" }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(SlateContainerHighest, SlateContainer)
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                error = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PinVerificationDialog(
    reason: String,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = SlateContainerHigh,
            border = BorderStroke(1.dp, CyanGlowBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan)
                }
                Spacer(Modifier.height(12.dp))
                Text("Parental Lock", style = MaterialTheme.typography.headlineSmall)
                if (reason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (i in 0 until 4) {
                        val filled = i < pin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (filled) ElectricCyan else SlateContainerHighest)
                                .border(1.dp, if (filled) ElectricCyan else GlassBorder, CircleShape)
                        )
                    }
                }
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = BroadcastCrimson)
                }
                Spacer(Modifier.height(18.dp))
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            Surface(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .testTag("pin_key_$key")
                                    .clickable {
                                        when (key) {
                                            "C" -> pin = ""
                                            "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            else -> if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) {
                                                    onSubmit(pin)
                                                    pin = ""
                                                }
                                            }
                                        }
                                    },
                                color = SlateContainer,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (key == "DEL") {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = if (key == "C") BroadcastCrimson else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun CinemaInfoBar(
    channel: StreamItemEntity,
    nowProgram: EpgProgramEntity?,
    nextProgram: EpgProgramEntity?,
    nowMs: Long,
    modifier: Modifier = Modifier,
    liveBadgeText: String = "LIVE",
    testTag: String = "cinema_info_bar",
    contentInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    onClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    bottomDeck: (@Composable () -> Unit)? = null
) {
    val barBg = Brush.verticalGradient(
        listOf(
            MidnightSurface.copy(alpha = 0.94f),
            ObsidianBg.copy(alpha = 0.97f)
        )
    )
    val progress = remember(nowProgram, nowMs) {
        if (nowProgram == null) 0f
        else {
            val span = (nowProgram.endEpochMs - nowProgram.startEpochMs).coerceAtLeast(1L)
            ((nowMs - nowProgram.startEpochMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
    }
    val remainingMin = remember(nowProgram, nowMs) {
        if (nowProgram == null) null
        else ((nowProgram.endEpochMs - nowMs) / 60_000L).coerceAtLeast(0L).toInt()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .testTag(testTag)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = ElectricCyan.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(contentInsets)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val isWideLayout = maxWidth >= 560.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                if (isWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StationIdentityBlock(
                            channel = channel,
                            liveBadgeText = liveBadgeText,
                            modifier = Modifier.width(230.dp)
                        )
                        EpgNowNextStage(
                            nowProgram = nowProgram,
                            nextProgram = nextProgram,
                            progress = progress,
                            remainingMin = remainingMin,
                            modifier = Modifier.weight(1f)
                        )
                        if (trailingAction != null) {
                            trailingAction()
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StationIdentityBlock(
                            channel = channel,
                            liveBadgeText = liveBadgeText,
                            modifier = Modifier.weight(1f)
                        )
                        if (trailingAction != null) {
                            Spacer(Modifier.width(8.dp))
                            trailingAction()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    EpgNowNextStage(
                        nowProgram = nowProgram,
                        nextProgram = nextProgram,
                        progress = progress,
                        remainingMin = remainingMin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (bottomDeck != null) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = GlassBorder)
                    Spacer(Modifier.height(10.dp))
                    bottomDeck()
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = GlassBorder,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun StationIdentityBlock(
    channel: StreamItemEntity,
    liveBadgeText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogoBadge(
            logoUrl = channel.logoUrl,
            title = channel.title,
            size = 54.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "CH ${channel.channelNumber.toString().padStart(3, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = BroadcastCrimson.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BroadcastCrimson)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = liveBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                if (channel.catchupDays > 0 || channel.catchupType.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Catch-up available",
                        tint = UltravioletPulse,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = channel.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpgNowNextStage(
    nowProgram: EpgProgramEntity?,
    nextProgram: EpgProgramEntity?,
    progress: Float,
    remainingMin: Int?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (nowProgram != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElectricCyan,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianBg,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = nowProgram.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${NovaFormatters.formatClock(nowProgram.startEpochMs)} – ${NovaFormatters.formatClock(nowProgram.endEpochMs)}" +
                        (remainingMin?.let { " • ${it}m left" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = ElectricCyan,
                trackColor = SlateContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        } else {
            Text(
                text = "Live Broadcast • No EPG schedule linked",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { 1f },
                color = ElectricCyan.copy(alpha = 0.35f),
                trackColor = SlateContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = UltravioletPulse.copy(alpha = 0.22f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(0.5.dp, UltravioletPulse.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = UltravioletPulse,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (nextProgram != null) {
                    "${NovaFormatters.formatClock(nextProgram.startEpochMs)}  ${nextProgram.title}"
                } else {
                    "Up next schedule unavailable"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DockedMiniPlayerBar(
    player: ExoPlayer?,
    title: String,
    subtitle: String,
    nowProgram: EpgProgramEntity?,
    nowMs: Long,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember(nowProgram, nowMs) {
        if (nowProgram == null) null
        else {
            val span = (nowProgram.endEpochMs - nowProgram.startEpochMs).coerceAtLeast(1L)
            ((nowMs - nowProgram.startEpochMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("docked_mini_player")
            .clickable(onClick = onExpand),
        shape = RectangleShape,
        color = MidnightSurface.copy(alpha = 0.97f),
        shadowElevation = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(thickness = 1.dp, color = ElectricCyan.copy(alpha = 0.55f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    if (player != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { view -> view.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BroadcastCrimson)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowProgram?.let { "NOW: ${it.title}" } ?: subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = ElectricCyan,
                            trackColor = SlateContainerHighest,
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause"
                    )
                }
                IconButton(
                    onClick = onExpand,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_expand")
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Expand player", tint = ElectricCyan)
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close mini player", tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun SyncBanner(
    stage: SyncStage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = stage !is SyncStage.Idle,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val borderColor = when (stage) {
            is SyncStage.Error -> BroadcastCrimson
            is SyncStage.Completed -> ElectricCyan
            else -> UltravioletPulse
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = SlateContainerHigh,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.8f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stage is SyncStage.InProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    when (stage) {
                        is SyncStage.InProgress -> {
                            Text(stage.step, style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                            if (stage.detail.isNotBlank()) {
                                Text(stage.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        is SyncStage.Completed -> {
                            Text("Sync Complete", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                            Text(stage.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        is SyncStage.Error -> {
                            Text("Sync Failed", style = MaterialTheme.typography.titleSmall, color = BroadcastCrimson)
                            Text(stage.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2)
                        }
                        SyncStage.Idle -> {}
                    }
                }
                if (stage !is SyncStage.InProgress) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted)
                    }
                }
            }
        }
    }
}

object NovaFormatters {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    fun formatClock(epochMs: Long): String = timeFmt.format(Date(epochMs))

    fun formatDateEpochSec(epochSec: Long?): String {
        if (epochSec == null || epochSec <= 0L) return "Unlimited"
        return dateFmt.format(Date(epochSec * 1000L))
    }

    fun formatDurationMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, mins, secs)
        } else {
            "%02d:%02d".format(mins, secs)
        }
    }
}
