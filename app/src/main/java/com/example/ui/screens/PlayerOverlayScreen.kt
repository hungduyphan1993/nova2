package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.AspectRatioMode
import com.example.data.local.EpgProgramEntity
import com.example.data.local.PlaybackEngine
import com.example.data.local.StreamItemEntity
import com.example.player.ExternalPlayerHelper
import com.example.player.MpvPlayerController
import com.example.player.TrackChoice
import com.example.player.VlcPlayerController
import com.example.ui.components.ChannelLogoBadge
import com.example.ui.components.CinemaInfoBar
import com.example.ui.components.EngineBadgePill
import com.example.ui.components.EngineSwitcherModalSheet
import com.example.ui.components.MpvPlayerSurface
import com.example.ui.components.NovaFormatters
import com.example.ui.components.VlcPlayerSurface
import com.example.ui.components.engineAccentColor
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.CyanGlowBorder
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse
import com.example.ui.viewmodel.PlayerUiState
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@OptIn(UnstableApi::class)
@Composable
fun PlayerOverlayScreen(
    uiState: PlayerUiState,
    player: ExoPlayer?,
    vlcController: VlcPlayerController,
    mpvController: MpvPlayerController,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    isInPipMode: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onZapChannel: (Int) -> Unit,
    onSelectChannel: (StreamItemEntity) -> Unit,
    onDirectJumpChannel: (Int) -> Boolean,
    onPlayNextEpisode: () -> Boolean,
    onCycleAspectRatio: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onToggleSignalHud: () -> Unit,
    onSelectTrack: (TrackChoice) -> Unit,
    onResetTrackAuto: (Int, Boolean) -> Unit,
    onSwitchEngine: (PlaybackEngine, Boolean) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onRetryStream: () -> Unit,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    var showOsd by remember { mutableStateOf(true) }
    var showZapperDrawer by remember { mutableStateOf(false) }
    var showTracksSheet by remember { mutableStateOf(false) }
    var showEngineSheet by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var keypadBuffer by remember { mutableStateOf("") }
    var gestureHudLabel by remember { mutableStateOf<String?>(null) }
    var containerWidthPx by remember { mutableIntStateOf(1) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var brightnessLevel by remember { mutableFloatStateOf(0.6f) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val activeChannel = uiState.activeStream
    val channelEpg = remember(activeChannel, epgByChannel) {
        activeChannel?.epgChannelId?.takeIf { it.isNotBlank() }?.let { epgByChannel[it] }.orEmpty()
    }
    val nowProgram = remember(channelEpg, nowMs) {
        channelEpg.firstOrNull { nowMs in it.startEpochMs until it.endEpochMs }
    }
    val nextProgram = remember(channelEpg, nowProgram, nowMs) {
        val threshold = nowProgram?.endEpochMs ?: nowMs
        channelEpg.firstOrNull { it.startEpochMs >= threshold }
    }

    BackHandler {
        when {
            showZapperDrawer -> showZapperDrawer = false
            showKeypad -> showKeypad = false
            else -> onBack()
        }
    }

    LaunchedEffect(showOsd, uiState.isPlaying, showZapperDrawer, showTracksSheet, showEngineSheet) {
        if (showOsd && uiState.isPlaying && !showZapperDrawer && !showTracksSheet && !showEngineSheet) {
            delay(4500)
            showOsd = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerWidthPx = it.width.coerceAtLeast(1) }
            .pointerInput(uiState.isLive) {
                detectTapGestures(
                    onTap = { showOsd = !showOsd },
                    onDoubleTap = { offset ->
                        if (!uiState.isLive) {
                            if (offset.x < containerWidthPx / 2f) {
                                onSeekBy(-10_000L)
                                gestureHudLabel = "-10s"
                            } else {
                                onSeekBy(10_000L)
                                gestureHudLabel = "+10s"
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { gestureHudLabel = null },
                    onVerticalDrag = { change, dragAmount ->
                        val isLeft = change.position.x < containerWidthPx / 2f
                        if (isLeft) {
                            brightnessLevel = (brightnessLevel - dragAmount / 600f).coerceIn(0.05f, 1f)
                            (context as? Activity)?.window?.let { win ->
                                val lp = win.attributes
                                lp.screenBrightness = brightnessLevel
                                win.attributes = lp
                            }
                            gestureHudLabel = "Brightness ${(brightnessLevel * 100).toInt()}%"
                        } else {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (dragAmount < -18f) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (curVol + 1).coerceAtMost(maxVol), 0)
                            } else if (dragAmount > 18f) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (curVol - 1).coerceAtLeast(0), 0)
                            }
                            val pct = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxVol
                            gestureHudLabel = "Volume $pct%"
                        }
                    }
                )
            }
    ) {
        if (!uiState.audioOnlyMode) {
            when (uiState.activeEngine) {
                PlaybackEngine.LIBVLC -> {
                    VlcPlayerSurface(
                        vlcController = vlcController,
                        aspectRatioMode = uiState.aspectRatioMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PlaybackEngine.LIBMPV -> {
                    MpvPlayerSurface(
                        mpvController = mpvController,
                        aspectRatioMode = uiState.aspectRatioMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    if (player != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { view ->
                                view.player = player
                                view.resizeMode = when (uiState.aspectRatioMode) {
                                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                    AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(10.dp))
                Text("Audio-Only Broadcast Mode (${uiState.activeEngine.shortBadge})", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                Text(uiState.title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        if (!isInPipMode) {
            // Buffering indicator
            if (uiState.telemetry.isBuffering && uiState.errorMessage == null) {
                CircularProgressIndicator(
                    color = engineAccentColor(uiState.activeEngine),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Gesture / Zapping / Engine Switch Pill
            val activePill = gestureHudLabel ?: uiState.engineSwitchNotice ?: uiState.zappingIndicator
            if (!activePill.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                    color = SlateContainerHigh.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, CyanGlowBorder)
                ) {
                    Text(
                        text = activePill,
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }

            // Error Recovery Banner with Built-In Engine Switch (ExoPlayer / libmpv / LibVLC) & External Handoff
            if (!uiState.errorMessage.isNullOrBlank()) {
                val alternateBuiltIns = remember(uiState.activeEngine) {
                    PlaybackEngine.builtInEngines.filter { it != uiState.activeEngine }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = SlateContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, BroadcastCrimson)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Stream Interrupted (${uiState.activeEngine.shortBadge})",
                            style = MaterialTheme.typography.titleMedium,
                            color = BroadcastCrimson
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(uiState.errorMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRetryStream,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.testTag("error_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                            if (activeChannel?.drmLicenseKey.isNullOrBlank()) {
                                alternateBuiltIns.forEach { alt ->
                                    val accent = engineAccentColor(alt)
                                    OutlinedButton(
                                        onClick = { onSwitchEngine(alt, false) },
                                        modifier = Modifier.testTag("error_try_${alt.name.lowercase()}")
                                    ) {
                                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp), tint = accent)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Try ${alt.shortBadge}", color = accent)
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { onSwitchEngine(PlaybackEngine.EXTERNAL_MPV, false) },
                                modifier = Modifier.testTag("error_external_mpv")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = UltravioletPulse)
                                Spacer(Modifier.width(6.dp))
                                Text("External MPV", color = UltravioletPulse)
                            }
                            OutlinedButton(
                                onClick = { showEngineSheet = true },
                                modifier = Modifier.testTag("error_more_players")
                            ) {
                                Text("More Players")
                            }
                        }
                    }
                }
            }

            // Signal Diagnostics Telemetry HUD
            if (uiState.showSignalHud) {
                val t = uiState.telemetry
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 64.dp, end = 16.dp),
                    color = ObsidianBg.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("SIGNAL TELEMETRY", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        Text(
                            "ENG: ${uiState.activeEngine.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = engineAccentColor(uiState.activeEngine)
                        )
                        Text(
                            "${t.width}x${t.height} • ${if (t.fps > 0f) "${t.fps.toInt()}fps" else "--"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (t.bitrateKbps > 0) {
                            Text("${t.bitrateKbps} kbps", style = MaterialTheme.typography.labelSmall, color = CinemaGold)
                        }
                        if (t.videoCodec.isNotBlank()) {
                            Text("V: ${t.videoCodec}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        if (t.audioCodec.isNotBlank()) {
                            Text("A: ${t.audioCodec}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Main OSD Controls
            AnimatedVisibility(
                visible = showOsd,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.84f)
                                )
                            )
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    // Top OSD Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("player_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(uiState.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.isLive) {
                                    Box(
                                        modifier = Modifier.size(7.dp).clip(CircleShape).background(BroadcastCrimson)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = uiState.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onToggleSignalHud, modifier = Modifier.minimumInteractiveComponentSize().testTag("player_hud_button")) {
                            Icon(Icons.Default.Info, contentDescription = "Signal telemetry HUD", tint = if (uiState.showSignalHud) ElectricCyan else Color.White)
                        }
                        IconButton(onClick = { showSleepDialog = true }, modifier = Modifier.minimumInteractiveComponentSize().testTag("player_sleep_button")) {
                            Icon(Icons.Default.Bedtime, contentDescription = "Sleep timer", tint = if (uiState.sleepTimerRemainingSec != null) CinemaGold else Color.White)
                        }
                        IconButton(onClick = onToggleAudioOnly, modifier = Modifier.minimumInteractiveComponentSize().testTag("player_audio_only_button")) {
                            Icon(Icons.Default.Headphones, contentDescription = "Audio only mode", tint = if (uiState.audioOnlyMode) ElectricCyan else Color.White)
                        }
                        IconButton(
                            onClick = { showEngineSheet = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("player_top_engine_button")
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = "Switch player engine",
                                tint = engineAccentColor(uiState.activeEngine)
                            )
                        }
                        IconButton(onClick = onEnterPip, modifier = Modifier.minimumInteractiveComponentSize().testTag("player_pip_button")) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Enter Picture in Picture", tint = Color.White)
                        }
                    }

                    // Center Transport Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isLive) {
                            IconButton(
                                onClick = { onZapChannel(-1) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SlateContainerHigh.copy(alpha = 0.72f))
                                    .testTag("player_zap_prev")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Previous channel", tint = Color.White)
                            }
                        } else {
                            IconButton(
                                onClick = { onSeekBy(-10_000L) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SlateContainerHigh.copy(alpha = 0.72f))
                                    .testTag("player_rew_10")
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White)
                            }
                        }

                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                                .testTag("player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play or pause",
                                tint = ObsidianBg,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        if (uiState.isLive) {
                            IconButton(
                                onClick = { onZapChannel(1) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SlateContainerHigh.copy(alpha = 0.72f))
                                    .testTag("player_zap_next")
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Next channel", tint = Color.White)
                            }
                        } else {
                            IconButton(
                                onClick = { onSeekBy(10_000L) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SlateContainerHigh.copy(alpha = 0.72f))
                                    .testTag("player_fwd_10")
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                            }
                        }
                    }

                    // Bottom VOD / Series / Catch-Up OSD Bar (Live channels use full-width CinemaInfoBar below)
                    if (!uiState.isLive) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (uiState.durationMs > 0L) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        NovaFormatters.formatDurationMs(uiState.positionMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                    Slider(
                                        value = uiState.positionMs.toFloat(),
                                        onValueChange = { onSeekTo(it.toLong()) },
                                        valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = ElectricCyan,
                                            activeTrackColor = ElectricCyan
                                        ),
                                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                                    )
                                    Text(
                                        NovaFormatters.formatDurationMs(uiState.durationMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (uiState.activeEpisodeIndex >= 0 && uiState.activeEpisodeIndex + 1 < uiState.activeEpisodesQueue.size) {
                                        OsdPillButton(
                                            icon = Icons.Default.SkipNext,
                                            label = "Next Ep",
                                            testTag = "player_next_episode",
                                            onClick = { onPlayNextEpisode() }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OsdPillButton(
                                        icon = Icons.Default.Memory,
                                        label = uiState.activeEngine.shortBadge,
                                        testTag = "player_engine_button",
                                        accentTint = engineAccentColor(uiState.activeEngine),
                                        onClick = { showEngineSheet = true }
                                    )
                                    OsdPillButton(
                                        icon = Icons.Default.Subtitles,
                                        label = "Tracks",
                                        testTag = "player_tracks_button",
                                        onClick = { showTracksSheet = true }
                                    )
                                    OsdPillButton(
                                        icon = Icons.Default.AspectRatio,
                                        label = uiState.aspectRatioMode.label,
                                        testTag = "player_aspect_button",
                                        onClick = onCycleAspectRatio
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Full-Width Edge-to-Edge IBO-Style Live Cinema Info Bar (auto-appears on zap/tune + expands with OSD)
            AnimatedVisibility(
                visible = uiState.isLive && activeChannel != null && (showOsd || uiState.showInfoBanner),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                if (activeChannel != null) {
                    CinemaInfoBar(
                        channel = activeChannel,
                        nowProgram = nowProgram,
                        nextProgram = nextProgram,
                        nowMs = nowMs,
                        liveBadgeText = "LIVE",
                        testTag = "player_cinema_info_bar",
                        contentInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        trailingAction = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EngineBadgePill(
                                    engine = uiState.activeEngine,
                                    onClick = { showEngineSheet = true }
                                )
                                Surface(
                                    color = SlateContainerHigh.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Text(
                                        text = NovaFormatters.formatClock(nowMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        },
                        bottomDeck = if (showOsd) {
                            {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (uiState.activePlaylistQueue.isNotEmpty()) {
                                        OsdPillButton(
                                            icon = Icons.Default.List,
                                            label = "Channels",
                                            testTag = "player_zapper_button",
                                            onClick = { showZapperDrawer = true }
                                        )
                                        OsdPillButton(
                                            icon = Icons.Default.Dialpad,
                                            label = "CH #",
                                            testTag = "player_keypad_button",
                                            onClick = {
                                                keypadBuffer = ""
                                                showKeypad = true
                                            }
                                        )
                                    }
                                    OsdPillButton(
                                        icon = Icons.Default.Memory,
                                        label = "Engine: ${uiState.activeEngine.shortBadge}",
                                        testTag = "player_engine_button",
                                        accentTint = engineAccentColor(uiState.activeEngine),
                                        onClick = { showEngineSheet = true }
                                    )
                                    OsdPillButton(
                                        icon = Icons.Default.Subtitles,
                                        label = "Tracks",
                                        testTag = "player_tracks_button",
                                        onClick = { showTracksSheet = true }
                                    )
                                    OsdPillButton(
                                        icon = Icons.Default.AspectRatio,
                                        label = uiState.aspectRatioMode.label,
                                        testTag = "player_aspect_button",
                                        onClick = onCycleAspectRatio
                                    )
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            // Zapper Side Drawer
            AnimatedVisibility(
                visible = showZapperDrawer,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showZapperDrawer = false }
                ) {
                    Surface(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .clickable(enabled = false) {},
                        color = SlateContainer.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Channel Zapper", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { showZapperDrawer = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close zapper")
                                }
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(uiState.activePlaylistQueue, key = { it.id }) { ch ->
                                    val selected = ch.id == uiState.activeStream?.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                onSelectChannel(ch)
                                                showZapperDrawer = false
                                            },
                                        color = if (selected) ElectricCyan.copy(alpha = 0.18f) else SlateContainerHigh,
                                        border = BorderStroke(1.dp, if (selected) ElectricCyan else GlassBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                ch.channelNumber.toString().padStart(3, '0'),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ElectricCyan,
                                                modifier = Modifier.width(34.dp)
                                            )
                                            ChannelLogoBadge(ch.logoUrl, ch.title, size = 34.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Text(ch.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Direct Channel Number Jump Pad
            if (showKeypad) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).width(240.dp),
                    color = SlateContainerHigh,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, CyanGlowBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Jump to Channel #", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = keypadBuffer.ifBlank { "---" },
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.height(10.dp))
                        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "GO")).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { k ->
                                    TextButton(
                                        onClick = {
                                            when (k) {
                                                "C" -> keypadBuffer = ""
                                                "GO" -> {
                                                    val num = keypadBuffer.toIntOrNull()
                                                    if (num != null) onDirectJumpChannel(num)
                                                    showKeypad = false
                                                }
                                                else -> if (keypadBuffer.length < 4) keypadBuffer += k
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(k, color = if (k == "GO") ElectricCyan else Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sleep Timer Sheet
            if (showSleepDialog) {
                ModalBottomSheet(
                    onDismissRequest = { showSleepDialog = false },
                    containerColor = SlateContainerHigh
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        listOf(null to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "60 minutes", 90 to "90 minutes").forEach { (mins, label) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        onSetSleepTimer(mins)
                                        showSleepDialog = false
                                    },
                                color = SlateContainer
                            ) {
                                Text(label, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }

            // Tracks Bottom Sheet (Video Quality, Audio, Subtitles)
            if (showTracksSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTracksSheet = false },
                    containerColor = SlateContainerHigh
                ) {
                    var trackTab by remember { mutableIntStateOf(0) }
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("Stream Tracks & Quality", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        TabRow(selectedTabIndex = trackTab, containerColor = SlateContainer) {
                            Tab(selected = trackTab == 0, onClick = { trackTab = 0 }, text = { Text("Audio (${uiState.audioTracks.size})") })
                            Tab(selected = trackTab == 1, onClick = { trackTab = 1 }, text = { Text("Subtitles (${uiState.subtitleTracks.size})") })
                            Tab(selected = trackTab == 2, onClick = { trackTab = 2 }, text = { Text("Video (${uiState.videoTracks.size})") })
                        }
                        Spacer(Modifier.height(10.dp))
                        val activeList = when (trackTab) {
                            0 -> uiState.audioTracks
                            1 -> uiState.subtitleTracks
                            else -> uiState.videoTracks
                        }
                        val trackType = when (trackTab) {
                            0 -> C.TRACK_TYPE_AUDIO
                            1 -> C.TRACK_TYPE_TEXT
                            else -> C.TRACK_TYPE_VIDEO
                        }
                        LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            onResetTrackAuto(trackType, trackTab == 1)
                                            showTracksSheet = false
                                        },
                                    color = SlateContainer
                                ) {
                                    Text(
                                        text = if (trackTab == 1) "Disable Subtitles" else "Automatic (Recommended)",
                                        modifier = Modifier.padding(14.dp),
                                        color = ElectricCyan
                                    )
                                }
                            }
                            items(activeList, key = { it.id }) { choice ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            onSelectTrack(choice)
                                            showTracksSheet = false
                                        },
                                    color = if (choice.isSelected) ElectricCyan.copy(alpha = 0.16f) else SlateContainer,
                                    border = if (choice.isSelected) BorderStroke(1.dp, ElectricCyan) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(choice.label, style = MaterialTheme.typography.titleSmall)
                                            if (choice.subLabel.isNotBlank()) {
                                                Text(choice.subLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                            }
                                        }
                                        if (choice.isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = ElectricCyan)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            // Playback Engine Switcher Sheet (ExoPlayer, LibVLC, MPV, VLC App, MX, Kodi)
            if (showEngineSheet) {
                EngineSwitcherModalSheet(
                    streamTitle = uiState.title,
                    activeEngine = uiState.activeEngine,
                    savedStreamEngine = PlaybackEngine.fromStoredName(activeChannel?.preferredEngine),
                    hasDrm = !activeChannel?.drmLicenseKey.isNullOrBlank(),
                    onSelectEngine = { target, rememberForStream ->
                        onSwitchEngine(target, rememberForStream)
                    },
                    onResetStreamDefault = {
                        onSwitchEngine(PlaybackEngine.DEFAULT, true)
                    },
                    onDismiss = { showEngineSheet = false }
                )
            }
        }
    }
}

@Composable
private fun OsdPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    accentTint: Color = ElectricCyan,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .testTag(testTag)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
        color = SlateContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = accentTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
