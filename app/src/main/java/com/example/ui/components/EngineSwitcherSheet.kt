package com.example.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.AspectRatioMode
import com.example.data.local.PlaybackEngine
import com.example.player.ExternalPlayerHelper
import com.example.player.MpvPlayerController
import com.example.player.VlcPlayerController
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse
import org.videolan.libvlc.util.VLCVideoLayout

fun engineAccentColor(engine: PlaybackEngine): Color = when (engine) {
    PlaybackEngine.EXOPLAYER -> ElectricCyan
    PlaybackEngine.LIBVLC -> CinemaGold
    PlaybackEngine.LIBMPV -> EmeraldSignal
    PlaybackEngine.DEFAULT -> TextSecondary
    else -> UltravioletPulse
}

@Composable
fun EngineBadgePill(
    engine: PlaybackEngine,
    modifier: Modifier = Modifier,
    prefix: String = "",
    onClick: (() -> Unit)? = null
) {
    val accent = engineAccentColor(engine)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Text(
            text = if (prefix.isBlank()) engine.shortBadge else "$prefix ${engine.shortBadge}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun VlcPlayerSurface(
    vlcController: VlcPlayerController,
    aspectRatioMode: AspectRatioMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vlcLayout = remember(context) {
        VLCVideoLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(vlcController, vlcLayout) {
        vlcController.attachSurface(vlcLayout)
        onDispose {
            vlcController.detachSurface(vlcLayout)
        }
    }

    AndroidView(
        factory = { vlcLayout },
        update = {
            vlcController.applyAspectRatio(aspectRatioMode)
        },
        modifier = modifier
    )
}

@Composable
fun MpvPlayerSurface(
    mpvController: MpvPlayerController,
    aspectRatioMode: AspectRatioMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surfaceView = remember(context) {
        SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(mpvController, surfaceView) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val frame = holder.surfaceFrame
                mpvController.attachSurface(
                    surface = holder.surface,
                    width = frame.width(),
                    height = frame.height()
                )
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                mpvController.attachSurface(holder.surface, width, height)
                mpvController.onSurfaceSizeChanged(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mpvController.detachSurface(holder.surface)
            }
        }
        surfaceView.holder.addCallback(callback)
        val existingSurface = surfaceView.holder.surface
        if (existingSurface != null && existingSurface.isValid) {
            val frame = surfaceView.holder.surfaceFrame
            mpvController.attachSurface(existingSurface, frame.width(), frame.height())
        }
        onDispose {
            surfaceView.holder.removeCallback(callback)
            mpvController.detachSurface(surfaceView.holder.surface)
        }
    }

    AndroidView(
        factory = { surfaceView },
        update = {
            mpvController.applyAspectRatio(aspectRatioMode)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSwitcherModalSheet(
    streamTitle: String,
    activeEngine: PlaybackEngine,
    savedStreamEngine: PlaybackEngine,
    hasDrm: Boolean = false,
    defaultRememberSelection: Boolean = false,
    actionHeadline: String = "Playback Engine & External Players",
    onSelectEngine: (engine: PlaybackEngine, rememberForStream: Boolean) -> Unit,
    onResetStreamDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rememberChoice by remember {
        mutableStateOf(defaultRememberSelection || savedStreamEngine != PlaybackEngine.DEFAULT)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(actionHeadline, style = MaterialTheme.typography.titleLarge)
            if (streamTitle.isNotBlank()) {
                Text(
                    text = streamTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                color = SlateContainer,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remember for this stream", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (savedStreamEngine != PlaybackEngine.DEFAULT) {
                                "Pinned: ${savedStreamEngine.label}"
                            } else {
                                "Currently following global default"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it },
                        modifier = Modifier.testTag("engine_remember_switch")
                    )
                }
            }

            if (savedStreamEngine != PlaybackEngine.DEFAULT && onResetStreamDefault != null) {
                TextButton(
                    onClick = {
                        onResetStreamDefault()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset stream override to Global Default", color = CinemaGold, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (hasDrm) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Note: Widevine/ClearKey DRM streams require built-in ExoPlayer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaGold
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    Text(
                        "BUILT-IN NATIVE ENGINES (EXOPLAYER • LIBVLC • LIBMPV)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                items(PlaybackEngine.builtInEngines, key = { it.name }) { engine ->
                    val isSelected = activeEngine == engine
                    val accent = engineAccentColor(engine)
                    val disabledByDrm = hasDrm && (engine == PlaybackEngine.LIBVLC || engine == PlaybackEngine.LIBMPV)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .testTag("engine_option_${engine.name.lowercase()}")
                            .clickable(enabled = !disabledByDrm) {
                                onSelectEngine(engine, rememberChoice)
                                onDismiss()
                            },
                        color = if (isSelected) accent.copy(alpha = 0.16f) else SlateContainer,
                        border = BorderStroke(1.dp, if (isSelected) accent else GlassBorder),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(engine.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    EngineBadgePill(engine = engine)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = engine.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = accent)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "EXTERNAL APP HANDOFF (MPV • VLC • MX • KODI)",
                        style = MaterialTheme.typography.labelSmall,
                        color = UltravioletPulse
                    )
                }

                items(PlaybackEngine.externalEngines, key = { it.name }) { engine ->
                    val installed = remember(context, engine) {
                        ExternalPlayerHelper.isEngineInstalled(context, engine)
                    }
                    val isPinned = savedStreamEngine == engine
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .testTag("engine_option_${engine.name.lowercase()}")
                            .clickable {
                                onSelectEngine(engine, rememberChoice)
                                onDismiss()
                            },
                        color = if (isPinned) UltravioletPulse.copy(alpha = 0.15f) else SlateContainer,
                        border = BorderStroke(1.dp, if (isPinned) UltravioletPulse else GlassBorder),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(UltravioletPulse.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = UltravioletPulse, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(engine.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (engine != PlaybackEngine.EXTERNAL_CHOOSER) {
                                        Surface(
                                            color = if (installed) EmeraldSignal.copy(alpha = 0.18f) else ObsidianBg.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (installed) "INSTALLED" else "CHOOSER FALLBACK",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (installed) EmeraldSignal else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = engine.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
