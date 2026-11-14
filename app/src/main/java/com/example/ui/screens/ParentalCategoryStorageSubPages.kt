package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AdultCategoryClassifier
import com.example.data.local.CategoryEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.UserPreferences
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
import com.example.ui.theme.UltravioletPulse

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentalAdultShieldSubPage(
    preferences: UserPreferences,
    adultCategoryCount: Int,
    lockedAdultCount: Int,
    hiddenAdultCount: Int,
    onOpenPinDialog: () -> Unit,
    onClearPin: () -> Unit,
    onRelockSession: () -> Unit,
    onToggleAutoLockAdult: (Boolean) -> Unit,
    onLockAllAdult: () -> Unit,
    onHideAllAdult: () -> Unit,
    onUnlockAllAdult: () -> Unit,
    onInspectAdultInStudio: () -> Unit
) {
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
                        if (preferences.hasPinConfigured) {
                            "PIN active. Locked categories and protected channels require verification."
                        } else {
                            "No PIN configured yet. Set a 4-digit PIN to enforce category & 18+ locks."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onOpenPinDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_set_pin")
                        ) {
                            Text(if (preferences.hasPinConfigured) "Change PIN" else "Set 4-Digit PIN")
                        }
                        if (preferences.hasPinConfigured) {
                            OutlinedButton(
                                onClick = onRelockSession,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Re-Lock Session", color = CinemaGold)
                            }
                            OutlinedButton(
                                onClick = onClearPin,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_remove_pin")
                            ) {
                                Text("Remove PIN", color = BroadcastCrimson)
                            }
                        }
                    }
                }
            }
        }

        item {
            NovaGlassCard(highlighted = true, accentColor = BroadcastCrimson, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = BroadcastCrimson, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Adult / 18+ / XXX Category Shield", style = MaterialTheme.typography.titleMedium, color = BroadcastCrimson)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Automatically detects folders containing Adult, 18+, +18, XXX, NSFW, Porn, or Erotic in their names.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBadgeBox("DETECTED", "$adultCategoryCount", BroadcastCrimson, Modifier.weight(1f))
                        StatBadgeBox("LOCKED", "$lockedAdultCount", CinemaGold, Modifier.weight(1f))
                        StatBadgeBox("HIDDEN", "$hiddenAdultCount", UltravioletPulse, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingSwitchRow(
                        label = "Auto-Lock Adult / 18+ / XXX Categories",
                        subtitle = "Automatically locks matching categories when enabled and on playlist sync",
                        checked = preferences.autoLockAdultCategories,
                        testTag = "settings_auto_lock_adult_switch",
                        onCheckedChange = onToggleAutoLockAdult
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onLockAllAdult,
                            enabled = adultCategoryCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = BroadcastCrimson, contentColor = Color.White),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_lock_all_adult")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lock All 18+/XXX")
                        }
                        OutlinedButton(
                            onClick = onHideAllAdult,
                            enabled = adultCategoryCount > 0,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_hide_all_adult")
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = BroadcastCrimson)
                            Spacer(Modifier.width(6.dp))
                            Text("Hide All 18+/XXX")
                        }
                        if (lockedAdultCount > 0) {
                            OutlinedButton(
                                onClick = onUnlockAllAdult,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_unlock_all_adult")
                            ) {
                                Text("Unlock 18+/XXX")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onInspectAdultInStudio,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_open_adult_studio")
                    ) {
                        Text("Review & Filter Adult Categories in Studio")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

private enum class CategoryFilterTab(val label: String) {
    ALL("All Types"),
    LIVE("Live TV"),
    MOVIE("Movies"),
    SERIES("Series")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryStudioSubPage(
    categories: List<CategoryEntity>,
    startWithAdultFilter: Boolean,
    hasPinConfigured: Boolean,
    onTogglePinned: (Long, Boolean) -> Unit,
    onToggleHidden: (CategoryEntity) -> Unit,
    onToggleLocked: (CategoryEntity) -> Unit,
    onBulkLock: (List<Long>, Boolean) -> Unit,
    onBulkHide: (List<Long>, Boolean) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(CategoryFilterTab.ALL) }
    var onlyAdult18Plus by rememberSaveable(startWithAdultFilter) { mutableStateOf(startWithAdultFilter) }
    var onlyLocked by rememberSaveable { mutableStateOf(false) }
    var onlyHidden by rememberSaveable { mutableStateOf(false) }

    val adultCount = remember(categories) {
        categories.count { AdultCategoryClassifier.isAdultCategoryName(it.name) }
    }

    val filteredCategories = remember(categories, searchQuery, selectedType, onlyAdult18Plus, onlyLocked, onlyHidden) {
        val q = searchQuery.trim().lowercase()
        categories.filter { cat ->
            val typeOk = when (selectedType) {
                CategoryFilterTab.ALL -> true
                CategoryFilterTab.LIVE -> cat.mediaType == MediaCategoryType.LIVE
                CategoryFilterTab.MOVIE -> cat.mediaType == MediaCategoryType.MOVIE
                CategoryFilterTab.SERIES -> cat.mediaType == MediaCategoryType.SERIES
            }
            val adultOk = !onlyAdult18Plus || AdultCategoryClassifier.isAdultCategoryName(cat.name)
            val lockOk = !onlyLocked || cat.isLocked
            val hideOk = !onlyHidden || cat.isHidden
            val queryOk = q.isEmpty() || cat.name.lowercase().contains(q)
            typeOk && adultOk && lockOk && hideOk && queryOk
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search categories or type adult / 18+ / xxx...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("category_studio_search")
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CategoryFilterTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedType == tab,
                        onClick = { selectedType = tab },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                FilterChip(
                    selected = onlyAdult18Plus,
                    onClick = { onlyAdult18Plus = !onlyAdult18Plus },
                    label = { Text("Adult / 18+ / XXX ($adultCount)", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BroadcastCrimson.copy(alpha = 0.28f),
                        selectedLabelColor = BroadcastCrimson
                    ),
                    modifier = Modifier.testTag("category_filter_adult_18")
                )
                FilterChip(
                    selected = onlyLocked,
                    onClick = { onlyLocked = !onlyLocked },
                    label = { Text("Locked Only", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = onlyHidden,
                    onClick = { onlyHidden = !onlyHidden },
                    label = { Text("Hidden Only", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (filteredCategories.isNotEmpty()) {
            item {
                val filteredIds = filteredCategories.map { it.id }
                NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Showing ${filteredCategories.size} of ${categories.size} categories" +
                                if (hasPinConfigured) " • PIN Active" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onBulkLock(filteredIds, true) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("category_bulk_lock")
                            ) {
                                Text("Lock Filtered", style = MaterialTheme.typography.labelSmall, color = CinemaGold)
                            }
                            OutlinedButton(
                                onClick = { onBulkLock(filteredIds, false) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("category_bulk_unlock")
                            ) {
                                Text("Unlock Filtered", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onBulkHide(filteredIds, true) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("category_bulk_hide")
                            ) {
                                Text("Hide Filtered", style = MaterialTheme.typography.labelSmall, color = BroadcastCrimson)
                            }
                            OutlinedButton(
                                onClick = { onBulkHide(filteredIds, false) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("category_bulk_unhide")
                            ) {
                                Text("Unhide Filtered", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (filteredCategories.isEmpty()) {
            item {
                NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (onlyAdult18Plus) {
                                "No Adult / 18+ / XXX categories detected"
                            } else {
                                "No categories match current filters"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Try clearing the search box or switching category tabs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredCategories, key = { it.id }) { cat ->
                val isAdult = AdultCategoryClassifier.isAdultCategoryName(cat.name)
                NovaGlassCard(
                    highlighted = isAdult,
                    accentColor = BroadcastCrimson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isAdult) {
                                    Surface(
                                        color = BroadcastCrimson.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, BroadcastCrimson)
                                    ) {
                                        Text(
                                            text = "18+",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BroadcastCrimson,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(cat.mediaType.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        IconButton(
                            onClick = { onTogglePinned(cat.id, !cat.isPinned) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pin category",
                                tint = if (cat.isPinned) ElectricCyan else TextMuted
                            )
                        }
                        IconButton(
                            onClick = { onToggleHidden(cat) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                if (cat.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Hide category",
                                tint = if (cat.isHidden) BroadcastCrimson else TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { onToggleLocked(cat) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                if (cat.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock category",
                                tint = if (cat.isLocked) CinemaGold else TextMuted
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

private enum class PendingClearAction(val title: String, val message: String) {
    HISTORY("Clear Watch History?", "Remove all playback resume markers and recently watched items for the active profile."),
    FAVORITES("Clear All Favorites?", "Unstar all Live TV, Movie, and Series favorites for the active profile."),
    EPG("Clear Cached EPG Guide?", "Remove all locally cached TV guide & Catch-Up listings. You can re-sync anytime."),
    ENGINE_OVERRIDES("Reset Per-Channel Player Overrides?", "Revert all streams back to your global default playback engine.")
}

@Composable
fun StorageBackupSubPage(
    favoriteCount: Int,
    historyCount: Int,
    epgCount: Int,
    onClearWatchHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    onClearEpgCache: () -> Unit,
    onClearEngineOverrides: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    var pendingClear by remember { mutableStateOf<PendingClearAction?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportBackup(uri) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            NovaGlassCard(highlighted = true, accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Cache & Library Cleanup", style = MaterialTheme.typography.titleMedium, color = EmeraldSignal)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBadgeBox("EPG", "$epgCount", ElectricCyan, Modifier.weight(1f))
                        StatBadgeBox("HISTORY", "$historyCount", CinemaGold, Modifier.weight(1f))
                        StatBadgeBox("FAVS", "$favoriteCount", UltravioletPulse, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    CleanupActionRow(
                        label = "Clear Watch History ($historyCount)",
                        testTag = "settings_clear_history",
                        onClick = { pendingClear = PendingClearAction.HISTORY }
                    )
                    CleanupActionRow(
                        label = "Clear Favorites ($favoriteCount)",
                        testTag = "settings_clear_favorites",
                        onClick = { pendingClear = PendingClearAction.FAVORITES }
                    )
                    CleanupActionRow(
                        label = "Clear EPG Program Cache ($epgCount)",
                        testTag = "settings_clear_epg",
                        onClick = { pendingClear = PendingClearAction.EPG }
                    )
                    CleanupActionRow(
                        label = "Reset Per-Stream Player Overrides",
                        testTag = "settings_clear_engine_overrides",
                        onClick = { pendingClear = PendingClearAction.ENGINE_OVERRIDES }
                    )
                }
            }
        }

        item {
            NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Portable Local Backup (.novabackup)", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Export or restore playlists, favorites, category locks, subtitles, and playback settings offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("novastream_backup.novabackup") },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("export_backup_button")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("import_backup_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                }
            }
        }

        item {
            NovaGlassCard(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = EmeraldSignal)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Server Ping & Speed Diagnostics", style = MaterialTheme.typography.titleSmall)
                        Text("Measure latency and download throughput to your IPTV host", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }

    pendingClear?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            containerColor = SlateContainerHigh,
            title = { Text(action.title) },
            text = { Text(action.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            PendingClearAction.HISTORY -> onClearWatchHistory()
                            PendingClearAction.FAVORITES -> onClearFavorites()
                            PendingClearAction.EPG -> onClearEpgCache()
                            PendingClearAction.ENGINE_OVERRIDES -> onClearEngineOverrides()
                        }
                        pendingClear = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BroadcastCrimson, contentColor = Color.White)
                ) {
                    Text("Confirm Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatBadgeBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = SlateContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CleanupActionRow(label: String, testTag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = BroadcastCrimson, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}
