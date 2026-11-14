package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.config.WhitelabelConfig
import com.example.data.local.AdultCategoryClassifier
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.CategoryEntity
import com.example.data.local.MpvHardwareMode
import com.example.data.local.NovaLanguageOptions
import com.example.data.local.PlaybackEngine
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.StartupBehavior
import com.example.data.local.SubtitleSizePreset
import com.example.data.local.UserPreferences
import com.example.data.local.VlcHardwareMode
import com.example.data.repository.SyncStage
import com.example.ui.components.NovaFormatters
import com.example.ui.components.NovaGlassCard
import com.example.ui.components.SyncBanner
import com.example.ui.components.WhitelabelBrandEmblem
import com.example.ui.components.WhitelabelBuildMetadataCard
import com.example.ui.components.WhitelabelSupportCard
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

enum class SettingsSection(
    val title: String,
    val eyebrow: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color
) {
    HUB(
        title = "Settings Control Deck",
        eyebrow = "${WhitelabelConfig.shortBrandUpper} PREFERENCES",
        description = "",
        icon = Icons.Default.Tune,
        accent = ElectricCyan
    ),
    PLAYLIST_EPG(
        title = "Playlist & EPG",
        eyebrow = "SOURCE & TIMING",
        description = "Profile status, User-Agent, EPG offset & manual sync",
        icon = Icons.Default.LiveTv,
        accent = UltravioletPulse
    ),
    PLAYBACK_ENGINES(
        title = "Playback & Engines",
        eyebrow = "DECODER & BUFFER",
        description = "ExoPlayer, LibVLC, libmpv, HW decoding & auto-fallback",
        icon = Icons.Default.PlayCircle,
        accent = ElectricCyan
    ),
    SUBTITLES_OSD(
        title = "Subtitles & OSD",
        eyebrow = "CAPTIONS & OVERLAY",
        description = "Caption scale, audio/subtitle languages & banner timeout",
        icon = Icons.Default.Subtitles,
        accent = CinemaGold
    ),
    GENERAL_STARTUP(
        title = "General & Startup",
        eyebrow = "LAUNCH & CLOCK",
        description = "24h / 12h time display & startup launch destination",
        icon = Icons.Default.Tune,
        accent = ElectricCyan
    ),
    PARENTAL_ADULT(
        title = "Parental & 18+ Shield",
        eyebrow = "FAMILY PROTECTION",
        description = "4-digit PIN & auto-lock Adult / 18+ / XXX categories",
        icon = Icons.Default.Shield,
        accent = BroadcastCrimson
    ),
    CATEGORY_STUDIO(
        title = "Category Studio",
        eyebrow = "GROUP VISIBILITY & LOCKS",
        description = "Search, filter Adult / 18+ / XXX, pin, hide & bulk lock",
        icon = Icons.Default.Category,
        accent = UltravioletPulse
    ),
    STORAGE_BACKUP(
        title = "Storage & Backup",
        eyebrow = "CACHE & DIAGNOSTICS",
        description = "Clear history/favorites/EPG, .novabackup & speed check",
        icon = Icons.Default.Storage,
        accent = EmeraldSignal
    );

    companion object {
        val hubTiles: List<SettingsSection>
            get() = entries.filter { it != HUB }
    }
}

private data class SettingsTileBadge(
    val section: SettingsSection,
    val badgeText: String
)

@Composable
fun SettingsScreen(
    widthSizeClass: WindowWidthSizeClass,
    preferences: UserPreferences,
    activeProfile: PlaylistProfileEntity?,
    categories: List<CategoryEntity>,
    syncStage: SyncStage,
    favoriteCount: Int,
    historyCount: Int,
    epgCount: Int,
    onBack: () -> Unit,
    onDismissSync: () -> Unit,
    onRefreshSync: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onUpdateUserAgent: (String) -> Unit,
    onAdjustEpgOffset: (Int) -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onRelockSession: () -> Unit,
    onRequestUnlock: (Boolean, String, () -> Unit) -> Unit,
    onSetAutoLockAdultCategories: (Boolean) -> Unit,
    onLockAllAdultCategories: () -> Unit,
    onUnlockAllAdultCategories: () -> Unit,
    onHideAllAdultCategories: () -> Unit,
    onSetDefaultEngine: (PlaybackEngine) -> Unit,
    onSetVlcHwMode: (VlcHardwareMode) -> Unit,
    onSetMpvHwMode: (MpvHardwareMode) -> Unit,
    onSetAutoFallbackEngine: (Boolean) -> Unit,
    onSetBufferPreset: (BufferPreset) -> Unit,
    onSetDefaultAspect: (AspectRatioMode) -> Unit,
    onSetAutoPip: (Boolean) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit,
    onSetSubtitleSize: (SubtitleSizePreset) -> Unit,
    onSetPreferredAudioLanguage: (String) -> Unit,
    onSetPreferredSubtitleLanguage: (String) -> Unit,
    onSetOsdTimeoutSec: (Int) -> Unit,
    onSetUse24HourClock: (Boolean) -> Unit,
    onSetStartupBehavior: (StartupBehavior) -> Unit,
    onToggleCatPinned: (Long, Boolean) -> Unit,
    onToggleCatHidden: (Long, Boolean) -> Unit,
    onToggleCatLocked: (Long, Boolean) -> Unit,
    onBulkSetCatLocked: (List<Long>, Boolean) -> Unit,
    onBulkSetCatHidden: (List<Long>, Boolean) -> Unit,
    onClearWatchHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    onClearEpgCache: () -> Unit,
    onClearEngineOverrides: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit
) {
    var activeSection by rememberSaveable { mutableStateOf(SettingsSection.HUB) }
    var categoryStudioInitialAdultOnly by rememberSaveable { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    BackHandler(enabled = activeSection != SettingsSection.HUB) {
        activeSection = SettingsSection.HUB
    }

    val adultCategories = remember(categories) {
        categories.filter { AdultCategoryClassifier.isAdultCategoryName(it.name) }
    }
    val lockedAdultCount = remember(adultCategories) { adultCategories.count { it.isLocked } }
    val hiddenCount = remember(categories) { categories.count { it.isHidden } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (activeSection == SettingsSection.HUB) {
                        onBack()
                    } else {
                        activeSection = SettingsSection.HUB
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeSection.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = activeSection.accent
                )
                Text(
                    text = activeSection.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (activeSection == SettingsSection.HUB) {
                Surface(
                    color = if (preferences.hasPinConfigured) EmeraldSignal.copy(alpha = 0.14f) else SlateContainerHigh,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (preferences.hasPinConfigured) EmeraldSignal.copy(alpha = 0.45f) else GlassBorder
                    )
                ) {
                    Text(
                        text = if (preferences.hasPinConfigured) "PIN Guard Active" else "No PIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (preferences.hasPinConfigured) EmeraldSignal else TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        SyncBanner(stage = syncStage, onDismiss = onDismissSync)

        AnimatedContent(
            targetState = activeSection,
            transitionSpec = {
                if (targetState != SettingsSection.HUB) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "settings_section_transition",
            modifier = Modifier.fillMaxSize()
        ) { section ->
            if (section == SettingsSection.HUB) {
                val epgOffsetText = activeProfile?.epgOffsetMinutes?.let { off ->
                    if (off == 0) "0m offset" else "${if (off > 0) "+" else ""}${off}m offset"
                } ?: "No profile"
                val osdLabel = if (preferences.osdTimeoutSec <= 0) "Manual OSD" else "${preferences.osdTimeoutSec}s OSD"
                val tiles = listOf(
                    SettingsTileBadge(SettingsSection.PLAYLIST_EPG, epgOffsetText),
                    SettingsTileBadge(
                        SettingsSection.PLAYBACK_ENGINES,
                        "${preferences.defaultEngine.shortBadge} • ${preferences.bufferPreset.label.substringBefore(" ")}"
                    ),
                    SettingsTileBadge(
                        SettingsSection.SUBTITLES_OSD,
                        "${NovaLanguageOptions.displayLabel(preferences.preferredAudioLanguage)} • $osdLabel"
                    ),
                    SettingsTileBadge(
                        SettingsSection.GENERAL_STARTUP,
                        "${if (preferences.use24HourClock) "24h" else "12h"} • ${preferences.startupBehavior.label.substringBefore(" ")}"
                    ),
                    SettingsTileBadge(
                        SettingsSection.PARENTAL_ADULT,
                        "${adultCategories.size} Adult (${lockedAdultCount} Locked)"
                    ),
                    SettingsTileBadge(
                        SettingsSection.CATEGORY_STUDIO,
                        "${categories.size} Groups • $hiddenCount Hidden"
                    ),
                    SettingsTileBadge(
                        SettingsSection.STORAGE_BACKUP,
                        "$epgCount EPG • $historyCount Hist"
                    )
                )

                val gridCols = if (widthSizeClass == WindowWidthSizeClass.Compact) 2 else 3
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridCols),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 980.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SettingsProfileHeroBanner(
                                profile = activeProfile,
                                onSyncNow = onRefreshSync,
                                onOpenPlaylistPage = { activeSection = SettingsSection.PLAYLIST_EPG }
                            )
                        }

                        items(tiles, key = { it.section.name }) { item ->
                            SettingsCategoryTile(
                                section = item.section,
                                badgeText = item.badgeText,
                                onClick = {
                                    if (item.section == SettingsSection.CATEGORY_STUDIO) {
                                        categoryStudioInitialAdultOnly = false
                                    }
                                    activeSection = item.section
                                }
                            )
                        }

                        if (WhitelabelConfig.hasSupportInfo) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                WhitelabelSupportCard()
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            WhitelabelBuildMetadataCard()
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(72.dp))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 820.dp)) {
                        when (section) {
                            SettingsSection.PLAYLIST_EPG -> PlaylistEpgSubPage(
                                profile = activeProfile,
                                onRefreshSync = onRefreshSync,
                                onOpenProfiles = onOpenProfiles,
                                onUpdateUserAgent = onUpdateUserAgent,
                                onAdjustEpgOffset = onAdjustEpgOffset
                            )
                            SettingsSection.PLAYBACK_ENGINES -> PlaybackEnginesSubPage(
                                preferences = preferences,
                                onSetDefaultEngine = onSetDefaultEngine,
                                onSetVlcHwMode = onSetVlcHwMode,
                                onSetMpvHwMode = onSetMpvHwMode,
                                onSetAutoFallbackEngine = onSetAutoFallbackEngine,
                                onSetBufferPreset = onSetBufferPreset,
                                onSetDefaultAspect = onSetDefaultAspect,
                                onSetAutoPip = onSetAutoPip,
                                onSetAutoReconnect = onSetAutoReconnect
                            )
                            SettingsSection.SUBTITLES_OSD -> SubtitlesOsdSubPage(
                                preferences = preferences,
                                onSetSubtitleSize = onSetSubtitleSize,
                                onSetPreferredAudioLanguage = onSetPreferredAudioLanguage,
                                onSetPreferredSubtitleLanguage = onSetPreferredSubtitleLanguage,
                                onSetOsdTimeoutSec = onSetOsdTimeoutSec
                            )
                            SettingsSection.GENERAL_STARTUP -> GeneralStartupSubPage(
                                preferences = preferences,
                                onSetUse24HourClock = onSetUse24HourClock,
                                onSetStartupBehavior = onSetStartupBehavior
                            )
                            SettingsSection.PARENTAL_ADULT -> ParentalAdultShieldSubPage(
                                preferences = preferences,
                                adultCategoryCount = adultCategories.size,
                                lockedAdultCount = lockedAdultCount,
                                hiddenAdultCount = adultCategories.count { it.isHidden },
                                onOpenPinDialog = {
                                    if (preferences.hasPinConfigured) {
                                        onRequestUnlock(true, "Verify current PIN before changing PIN") {
                                            newPin = ""
                                            showSetPinDialog = true
                                        }
                                    } else {
                                        newPin = ""
                                        showSetPinDialog = true
                                    }
                                },
                                onClearPin = {
                                    onRequestUnlock(preferences.hasPinConfigured, "Verify PIN to remove parental lock") {
                                        onClearPin()
                                    }
                                },
                                onRelockSession = onRelockSession,
                                onToggleAutoLockAdult = { next ->
                                    if (!next && preferences.hasPinConfigured) {
                                        onRequestUnlock(true, "Verify PIN to disable Auto-Lock Adult Shield") {
                                            onSetAutoLockAdultCategories(false)
                                        }
                                    } else {
                                        onSetAutoLockAdultCategories(next)
                                    }
                                },
                                onLockAllAdult = onLockAllAdultCategories,
                                onHideAllAdult = onHideAllAdultCategories,
                                onUnlockAllAdult = {
                                    onRequestUnlock(preferences.hasPinConfigured, "Verify PIN to unlock Adult / 18+ / XXX categories") {
                                        onUnlockAllAdultCategories()
                                    }
                                },
                                onInspectAdultInStudio = {
                                    categoryStudioInitialAdultOnly = true
                                    activeSection = SettingsSection.CATEGORY_STUDIO
                                }
                            )
                            SettingsSection.CATEGORY_STUDIO -> CategoryStudioSubPage(
                                categories = categories,
                                startWithAdultFilter = categoryStudioInitialAdultOnly,
                                hasPinConfigured = preferences.hasPinConfigured,
                                onTogglePinned = onToggleCatPinned,
                                onToggleHidden = { cat ->
                                    if (cat.isHidden && (cat.isLocked || AdultCategoryClassifier.isAdultCategoryName(cat.name)) && preferences.hasPinConfigured) {
                                        onRequestUnlock(true, "Verify PIN to unhide ${cat.name}") {
                                            onToggleCatHidden(cat.id, false)
                                        }
                                    } else {
                                        onToggleCatHidden(cat.id, !cat.isHidden)
                                    }
                                },
                                onToggleLocked = { cat ->
                                    if (cat.isLocked && preferences.hasPinConfigured) {
                                        onRequestUnlock(true, "Verify PIN to unlock ${cat.name}") {
                                            onToggleCatLocked(cat.id, false)
                                        }
                                    } else {
                                        onToggleCatLocked(cat.id, !cat.isLocked)
                                    }
                                },
                                onBulkLock = { ids, locked ->
                                    if (!locked && preferences.hasPinConfigured) {
                                        onRequestUnlock(true, "Verify PIN to unlock ${ids.size} categories") {
                                            onBulkSetCatLocked(ids, false)
                                        }
                                    } else {
                                        onBulkSetCatLocked(ids, locked)
                                    }
                                },
                                onBulkHide = { ids, hidden ->
                                    if (!hidden && preferences.hasPinConfigured) {
                                        onRequestUnlock(true, "Verify PIN to unhide ${ids.size} categories") {
                                            onBulkSetCatHidden(ids, false)
                                        }
                                    } else {
                                        onBulkSetCatHidden(ids, hidden)
                                    }
                                }
                            )
                            SettingsSection.STORAGE_BACKUP -> StorageBackupSubPage(
                                favoriteCount = favoriteCount,
                                historyCount = historyCount,
                                epgCount = epgCount,
                                onClearWatchHistory = onClearWatchHistory,
                                onClearFavorites = onClearFavorites,
                                onClearEpgCache = onClearEpgCache,
                                onClearEngineOverrides = onClearEngineOverrides,
                                onExportBackup = onExportBackup,
                                onImportBackup = onImportBackup,
                                onOpenDiagnostics = onOpenDiagnostics
                            )
                            SettingsSection.HUB -> Unit
                        }
                    }
                }
            }
        }
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            containerColor = SlateContainerHigh,
            title = { Text(if (preferences.hasPinConfigured) "Change 4-Digit Parental PIN" else "Set 4-Digit Parental PIN") },
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
                    enabled = newPin.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
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
private fun SettingsProfileHeroBanner(
    profile: PlaylistProfileEntity?,
    onSyncNow: () -> Unit,
    onOpenPlaylistPage: () -> Unit
) {
    NovaGlassCard(
        highlighted = true,
        accentColor = UltravioletPulse,
        onClick = onOpenPlaylistPage,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WhitelabelBrandEmblem(
                size = 48.dp,
                cornerRadius = 14.dp,
                accentColor = UltravioletPulse
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.name ?: "No Active Playlist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (profile != null) {
                        "${profile.sourceType.name} • UA: ${profile.userAgent}"
                    } else {
                        WhitelabelConfig.tagline
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSyncNow,
                enabled = profile != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = WhitelabelConfig.onPrimaryColor
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_hero_sync_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync playlist and EPG", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sync", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SettingsCategoryTile(
    section: SettingsSection,
    badgeText: String,
    onClick: () -> Unit
) {
    val bgBrush = Brush.linearGradient(
        listOf(
            SlateContainerHigh.copy(alpha = 0.94f),
            SlateContainer.copy(alpha = 0.88f)
        )
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bgBrush)
            .clickable(onClick = onClick)
            .testTag("settings_tile_${section.name.lowercase()}"),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, section.accent.copy(alpha = 0.35f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val isTight = maxWidth < 165.dp
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(section.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = section.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = if (isTight) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = section.accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, section.accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = section.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
