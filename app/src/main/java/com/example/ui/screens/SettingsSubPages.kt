package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.WhitelabelConfig
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.MpvHardwareMode
import com.example.data.local.NovaLanguageOptions
import com.example.data.local.PlaybackEngine
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.StartupBehavior
import com.example.data.local.SubtitleSizePreset
import com.example.data.local.UserPreferences
import com.example.data.local.VlcHardwareMode
import com.example.ui.components.NovaFormatters
import com.example.ui.components.NovaGlassCard
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

private val userAgentPresets = listOf(
    "${WhitelabelConfig.launcherLabel} Default" to WhitelabelConfig.defaultUserAgent,
    "VLC Player" to "VLC/3.0.20 LibVLC/3.0.20",
    "TiviMate" to "TiviMate/4.7.0 (Android)",
    "IBO Player" to "IBOPlayer/3.9 (AndroidTV)",
    "Chrome Android" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistEpgSubPage(
    profile: PlaylistProfileEntity?,
    onRefreshSync: () -> Unit,
    onOpenProfiles: () -> Unit,
    onUpdateUserAgent: (String) -> Unit,
    onAdjustEpgOffset: (Int) -> Unit
) {
    var userAgentDraft by remember(profile?.id, profile?.userAgent) {
        mutableStateOf(profile?.userAgent ?: WhitelabelConfig.defaultUserAgent)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            NovaGlassCard(highlighted = true, accentColor = UltravioletPulse, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Active Playlist Profile", style = MaterialTheme.typography.titleMedium, color = UltravioletPulse)
                    Spacer(Modifier.height(6.dp))
                    if (profile != null) {
                        Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Source: ${profile.sourceType.name} • Status: ${profile.accountStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            "Expires: ${NovaFormatters.formatDateEpochSec(profile.expirationEpochSec)} • Connections: ${profile.activeConnections}/${profile.maxConnections}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (profile.lastSyncedAt > 0L) {
                            Text(
                                "Last synced at ${NovaFormatters.formatClock(profile.lastSyncedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    } else {
                        Text("No playlist profile selected.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRefreshSync,
                            enabled = profile != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = WhitelabelConfig.onPrimaryColor
                            ),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_sync_playlist_epg")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Playlist & EPG")
                        }
                        OutlinedButton(
                            onClick = onOpenProfiles,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_manage_profiles")
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Profiles")
                        }
                    }
                }
            }
        }

        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("HTTP User-Agent", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sent on playlist syncs, EPG XMLTV fetches, and stream playback headers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = userAgentDraft,
                        onValueChange = { userAgentDraft = it },
                        label = { Text("Custom User-Agent String") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_user_agent_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        userAgentPresets.forEach { (name, value) ->
                            FilterChip(
                                selected = userAgentDraft == value,
                                onClick = {
                                    userAgentDraft = value
                                    if (profile != null) onUpdateUserAgent(value)
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onUpdateUserAgent(userAgentDraft) },
                        enabled = profile != null && userAgentDraft.isNotBlank() && userAgentDraft != profile.userAgent,
                        colors = ButtonDefaults.buttonColors(containerColor = UltravioletPulse, contentColor = Color.White),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_save_user_agent")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply User-Agent")
                    }
                }
            }
        }

        item {
            val offsetMin = profile?.epgOffsetMinutes ?: 0
            val offsetFormatted = if (offsetMin >= 0) "+${offsetMin}m" else "${offsetMin}m"
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("EPG Guide Time-Shift Offset", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                            Text(
                                "Adjust program guide timestamps relative to your local timezone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = offsetFormatted,
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-60 to "-1 Hour", -30 to "-30 Min", 0 to "Reset", 30 to "+30 Min", 60 to "+1 Hour").forEach { (delta, label) ->
                            OutlinedButton(
                                enabled = profile != null,
                                onClick = {
                                    if (delta == 0) {
                                        onAdjustEpgOffset(-offsetMin)
                                    } else {
                                        onAdjustEpgOffset(delta)
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackEnginesSubPage(
    preferences: UserPreferences,
    onSetDefaultEngine: (PlaybackEngine) -> Unit,
    onSetVlcHwMode: (VlcHardwareMode) -> Unit,
    onSetMpvHwMode: (MpvHardwareMode) -> Unit,
    onSetAutoFallbackEngine: (Boolean) -> Unit,
    onSetBufferPreset: (BufferPreset) -> Unit,
    onSetDefaultAspect: (AspectRatioMode) -> Unit,
    onSetAutoPip: (Boolean) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Playback Engine", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose between built-in ExoPlayer, LibVLC, native libmpv, or external players.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlaybackEngine.selectableGlobalEngines.forEach { engine ->
                            FilterChip(
                                selected = preferences.defaultEngine == engine,
                                onClick = { onSetDefaultEngine(engine) },
                                label = { Text(engine.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("settings_engine_${engine.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("libmpv Hardware Decoding (hwdec)", style = MaterialTheme.typography.labelMedium, color = EmeraldSignal)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MpvHardwareMode.entries.forEach { hw ->
                            FilterChip(
                                selected = preferences.mpvHardwareMode == hw,
                                onClick = { onSetMpvHwMode(hw) },
                                label = { Text(hw.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("settings_mpv_hw_${hw.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("LibVLC Hardware Decoding", style = MaterialTheme.typography.labelMedium, color = CinemaGold)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VlcHardwareMode.entries.forEach { hw ->
                            FilterChip(
                                selected = preferences.vlcHardwareMode == hw,
                                onClick = { onSetVlcHwMode(hw) },
                                label = { Text(hw.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("settings_vlc_hw_${hw.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Buffering, Aspect & Resilience", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Network Buffer Preset", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BufferPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preferences.bufferPreset == preset,
                                onClick = { onSetBufferPreset(preset) },
                                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Default Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AspectRatioMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.defaultAspectRatio == mode,
                                onClick = { onSetDefaultAspect(mode) },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(Modifier.height(4.dp))
                    SettingSwitchRow(
                        label = "Auto-Fallback Engine on Stream Error",
                        subtitle = "Switch automatically between ExoPlayer, libmpv & LibVLC on codec failure",
                        checked = preferences.autoFallbackEngine,
                        onCheckedChange = onSetAutoFallbackEngine
                    )
                    SettingSwitchRow(
                        label = "Auto Picture-in-Picture on Home",
                        subtitle = "Shrink active stream into PiP window when pressing Home",
                        checked = preferences.autoPipOnBackground,
                        onCheckedChange = onSetAutoPip
                    )
                    SettingSwitchRow(
                        label = "Auto-Reconnect Dropped Live Streams",
                        subtitle = "Automatically retry interrupted live broadcasts",
                        checked = preferences.autoReconnectLive,
                        onCheckedChange = onSetAutoReconnect
                    )
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitlesOsdSubPage(
    preferences: UserPreferences,
    onSetSubtitleSize: (SubtitleSizePreset) -> Unit,
    onSetPreferredAudioLanguage: (String) -> Unit,
    onSetPreferredSubtitleLanguage: (String) -> Unit,
    onSetOsdTimeoutSec: (Int) -> Unit
) {
    val osdOptions = listOf(3 to "3s", 5 to "5s", 8 to "8s", 12 to "12s", 0 to "Manual (Stay Open)")

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            NovaGlassCard(highlighted = true, accentColor = CinemaGold, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Subtitle Scale & Live Preview", style = MaterialTheme.typography.titleMedium, color = CinemaGold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 14.dp, vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sample Caption — ${WhitelabelConfig.launcherLabel} Cinema Subtitle",
                            fontSize = (15f * preferences.subtitleSize.scaleFactor).sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SubtitleSizePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preferences.subtitleSize == preset,
                                onClick = { onSetSubtitleSize(preset) },
                                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("settings_sub_size_${preset.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Preferred Track Languages", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Preferred Audio Track Language", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NovaLanguageOptions.audioLanguages.forEach { opt ->
                            FilterChip(
                                selected = preferences.preferredAudioLanguage.equals(opt.code, ignoreCase = true),
                                onClick = { onSetPreferredAudioLanguage(opt.code) },
                                label = { Text(opt.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Preferred Subtitle Track Language", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NovaLanguageOptions.subtitleLanguages.forEach { opt ->
                            FilterChip(
                                selected = preferences.preferredSubtitleLanguage.equals(opt.code, ignoreCase = true),
                                onClick = { onSetPreferredSubtitleLanguage(opt.code) },
                                label = { Text(opt.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Player OSD & Cinema Info Bar Timeout", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How long playback controls and channel banners stay on screen before auto-hiding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        osdOptions.forEach { (sec, label) ->
                            FilterChip(
                                selected = preferences.osdTimeoutSec == sec,
                                onClick = { onSetOsdTimeoutSec(sec) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("settings_osd_timeout_$sec")
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
fun GeneralStartupSubPage(
    preferences: UserPreferences,
    onSetUse24HourClock: (Boolean) -> Unit,
    onSetStartupBehavior: (StartupBehavior) -> Unit
) {
    val sampleTime = remember { System.currentTimeMillis() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clock & EPG Time Format", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    }
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        label = "Use 24-Hour Broadcast Clock",
                        subtitle = "Preview: ${NovaFormatters.formatClock(sampleTime)} across EPG, Catch-Up & Info Bar",
                        checked = preferences.use24HourClock,
                        testTag = "settings_use_24h_clock",
                        onCheckedChange = onSetUse24HourClock
                    )
                }
            }
        }

        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Launch Startup Behavior", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose what ${WhitelabelConfig.appName} opens automatically on launch when an active playlist exists.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    StartupBehavior.entries.forEach { behavior ->
                        val selected = preferences.startupBehavior == behavior
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSetStartupBehavior(behavior) }
                                .testTag("settings_startup_${behavior.name.lowercase()}"),
                            color = if (selected) ElectricCyan.copy(alpha = 0.14f) else SlateContainer,
                            border = BorderStroke(1.dp, if (selected) ElectricCyan else GlassBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSetStartupBehavior(behavior) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(behavior.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        behavior.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) ElectricCyan else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}
