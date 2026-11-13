package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.CategoryEntity
import com.example.data.local.StreamItemEntity
import com.example.data.local.UserPreferences
import com.example.data.network.DiagnosticReport
import com.example.ui.components.ChannelLogoBadge
import com.example.ui.components.NovaFormatters
import com.example.ui.components.NovaGlassCard
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.OmniSearchResult

@Composable
fun SearchScreen(
    query: String,
    isSearching: Boolean,
    result: OmniSearchResult,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectLive: (StreamItemEntity) -> Unit,
    onSelectMovie: (StreamItemEntity) -> Unit,
    onSelectSeries: (StreamItemEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search Live TV, Movies, Series & EPG...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("omni_search_input")
            )
        }

        if (isSearching) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricCyan)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (result.liveMatches.isNotEmpty()) {
                item { Text("Live Channels (${result.liveMatches.size})", style = MaterialTheme.typography.titleSmall, color = ElectricCyan) }
                items(result.liveMatches, key = { "live_${it.id}" }) { ch ->
                    NovaGlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onSelectLive(ch) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogoBadge(ch.logoUrl, ch.title, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(ch.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (result.movieMatches.isNotEmpty()) {
                item { Text("Movies (${result.movieMatches.size})", style = MaterialTheme.typography.titleSmall, color = CinemaGold) }
                items(result.movieMatches, key = { "movie_${it.id}" }) { m ->
                    NovaGlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onSelectMovie(m) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogoBadge(m.logoUrl, m.title, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (result.seriesMatches.isNotEmpty()) {
                item { Text("Series (${result.seriesMatches.size})", style = MaterialTheme.typography.titleSmall, color = ElectricCyan) }
                items(result.seriesMatches, key = { "series_${it.id}" }) { s ->
                    NovaGlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onSelectSeries(s) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogoBadge(s.logoUrl, s.title, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (result.epgMatches.isNotEmpty()) {
                item { Text("TV Guide Programs (${result.epgMatches.size})", style = MaterialTheme.typography.titleSmall, color = TextSecondary) }
                items(result.epgMatches, key = { "epg_${it.id}" }) { prog ->
                    NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(prog.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${prog.epgChannelId} • ${NovaFormatters.formatClock(prog.startEpochMs)} - ${NovaFormatters.formatClock(prog.endEpochMs)}",
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

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onSetBufferPreset: (BufferPreset) -> Unit,
    onSetDefaultAspect: (AspectRatioMode) -> Unit,
    onSetAutoPip: (Boolean) -> Unit,
    onSetPreferExternal: (Boolean) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit,
    onToggleCatPinned: (Long, Boolean) -> Unit,
    onToggleCatHidden: (Long, Boolean) -> Unit,
    onToggleCatLocked: (Long, Boolean) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit
) {
    var showSetPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportBackup(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings & Parental Control", style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Parental Control PIN", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (preferences.hasPinConfigured) "PIN active. Locked folders and channels require verification." else "No PIN configured yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    newPin = ""
                                    showSetPinDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.testTag("settings_set_pin")
                            ) {
                                Text(if (preferences.hasPinConfigured) "Change PIN" else "Set 4-Digit PIN")
                            }
                            if (preferences.hasPinConfigured) {
                                OutlinedButton(onClick = onClearPin) {
                                    Text("Remove PIN", color = BroadcastCrimson)
                                }
                            }
                        }
                    }
                }
            }

            item {
                NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Stream Buffer & Playback Engine", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BufferPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = preferences.bufferPreset == preset,
                                    onClick = { onSetBufferPreset(preset) },
                                    label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Default Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AspectRatioMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = preferences.defaultAspectRatio == mode,
                                    onClick = { onSetDefaultAspect(mode) },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        SettingSwitchRow("Auto Picture-in-Picture on Home", preferences.autoPipOnBackground, onSetAutoPip)
                        SettingSwitchRow("Auto-Reconnect Dropped Live Streams", preferences.autoReconnectLive, onSetAutoReconnect)
                        SettingSwitchRow("Prefer External Video Player", preferences.preferExternalPlayer, onSetPreferExternal)
                    }
                }
            }

            item {
                NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Portable Local Backup (.novabackup)", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Export or restore playlists, favorites, category locks, and watch progress offline.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { exportLauncher.launch("novastream_backup.novabackup") },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.weight(1f).testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f).testTag("import_backup_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import")
                            }
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item {
                    Text("Category Visibility, Pin & Lock Studio", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                }
                items(categories, key = { it.id }) { cat ->
                    NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cat.mediaType.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            IconButton(onClick = { onToggleCatPinned(cat.id, !cat.isPinned) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Default.PushPin, contentDescription = "Pin category", tint = if (cat.isPinned) ElectricCyan else TextMuted)
                            }
                            IconButton(onClick = { onToggleCatHidden(cat.id, !cat.isHidden) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(if (cat.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Hide category", tint = if (cat.isHidden) BroadcastCrimson else TextSecondary)
                            }
                            IconButton(onClick = { onToggleCatLocked(cat.id, !cat.isLocked) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(if (cat.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock category", tint = if (cat.isLocked) CinemaGold else TextMuted)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            containerColor = SlateContainerHigh,
            title = { Text("Set 4-Digit Parental PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("4-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_pin_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            onSetPin(newPin)
                            showSetPinDialog = false
                        }
                    },
                    enabled = newPin.length == 4
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DiagnosticsScreen(
    defaultTargetUrl: String,
    report: DiagnosticReport?,
    isRunning: Boolean,
    onBack: () -> Unit,
    onRunTest: (String) -> Unit
) {
    var targetUrl by remember(defaultTargetUrl) { mutableStateOf(defaultTargetUrl.ifBlank { "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Server Ping & Speed Diagnostics", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(14.dp))
        NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("Server or Stream Test URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onRunTest(targetUrl) },
                    enabled = !isRunning && targetUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_diagnostics_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ObsidianBg, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning) "Measuring Latency & Throughput..." else "Run Connection Test")
                }
            }
        }

        report?.let { r ->
            Spacer(Modifier.height(16.dp))
            NovaGlassCard(highlighted = r.error == null, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = r.verdict,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (r.error == null) EmeraldSignal else BroadcastCrimson
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            color = SlateContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("LATENCY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    r.pingMs?.let { "${it} ms" } ?: "--",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = ElectricCyan
                                )
                            }
                        }
                        Surface(
                            color = SlateContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("THROUGHPUT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    r.downloadMbps?.let { "$it Mbps" } ?: "--",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CinemaGold
                                )
                            }
                        }
                    }
                    r.error?.let { err ->
                        Spacer(Modifier.height(10.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = BroadcastCrimson)
                    }
                }
            }
        }
    }
}
