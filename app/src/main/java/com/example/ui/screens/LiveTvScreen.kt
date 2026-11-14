package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.PlaybackEngine
import com.example.data.local.StreamItemEntity
import com.example.ui.components.ChannelLogoBadge
import com.example.ui.components.EngineBadgePill
import com.example.ui.components.EngineSwitcherModalSheet
import com.example.ui.components.NovaFormatters
import com.example.ui.components.NovaGlassCard
import com.example.ui.components.engineAccentColor
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

@Composable
fun LiveTvScreen(
    widthSizeClass: WindowWidthSizeClass,
    categories: List<CategoryEntity>,
    streams: List<StreamItemEntity>,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    initialCategoryKey: String?,
    initialFavoritesOnly: Boolean,
    initialCatchupOnly: Boolean,
    isPinSessionUnlocked: Boolean,
    hasPinConfigured: Boolean,
    defaultEngine: PlaybackEngine,
    onBack: () -> Unit,
    onRequestUnlock: (isLocked: Boolean, reason: String, onUnlocked: () -> Unit) -> Unit,
    onToggleFavorite: (StreamItemEntity) -> Unit,
    onToggleStreamLock: (StreamItemEntity) -> Unit,
    onSetStreamEngine: (StreamItemEntity, PlaybackEngine) -> Unit,
    onOpenCatchUpGuide: (StreamItemEntity?) -> Unit,
    onPlayChannel: (StreamItemEntity, List<StreamItemEntity>, PlaybackEngine?) -> Unit
) {
    var selectedCategoryKey by remember(initialCategoryKey) { mutableStateOf(initialCategoryKey ?: "ALL") }
    var favoritesOnly by remember(initialFavoritesOnly) { mutableStateOf(initialFavoritesOnly) }
    var catchupOnly by remember(initialCatchupOnly) { mutableStateOf(initialCatchupOnly) }
    var query by remember { mutableStateOf("") }
    var focusedChannel by remember { mutableStateOf<StreamItemEntity?>(null) }
    var enginePickerChannel by remember { mutableStateOf<StreamItemEntity?>(null) }

    val lockedCategoryKeys = remember(categories, isPinSessionUnlocked, hasPinConfigured) {
        if (!hasPinConfigured || isPinSessionUnlocked) emptySet()
        else categories.filter { it.isLocked }.map { it.categoryKey }.toSet()
    }

    val filteredChannels = remember(streams, selectedCategoryKey, favoritesOnly, catchupOnly, query, lockedCategoryKeys) {
        streams.filter { item ->
            val catOk = when {
                selectedCategoryKey == "ALL" -> item.categoryKey !in lockedCategoryKeys
                else -> item.categoryKey == selectedCategoryKey
            }
            val favOk = !favoritesOnly || item.isFavorite
            val catchOk = !catchupOnly || item.catchupDays > 0 || item.catchupType.isNotBlank()
            val queryOk = query.isBlank() || item.title.contains(query, ignoreCase = true) || item.channelNumber.toString() == query.trim()
            catOk && favOk && catchOk && queryOk
        }
    }

    val isWide = widthSizeClass != WindowWidthSizeClass.Compact
    val nowMs = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("live_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
            }
            Text("Live TV", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter channels or #...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("live_search_input")
            )
        }

        // Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BroadcastCrimson.copy(alpha = 0.25f))
                )
            }
            item {
                FilterChip(
                    selected = catchupOnly,
                    onClick = { catchupOnly = !catchupOnly },
                    label = { Text("Catch-Up Filter") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = UltravioletPulse.copy(alpha = 0.28f))
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { onOpenCatchUpGuide(focusedChannel?.takeIf { it.supportsCatchUp }) },
                    label = { Text("Catch-Up Guide", color = UltravioletPulse) },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = UltravioletPulse, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("live_open_catchup_guide")
                )
            }
            if (!isWide) {
                item {
                    FilterChip(
                        selected = selectedCategoryKey == "ALL",
                        onClick = { selectedCategoryKey = "ALL" },
                        label = { Text("All Groups") }
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategoryKey == cat.categoryKey,
                        onClick = {
                            onRequestUnlock(cat.isLocked, "Unlock category: ${cat.name}") {
                                selectedCategoryKey = cat.categoryKey
                            }
                        },
                        label = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = when {
                            cat.isLocked -> ({ Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = CinemaGold) })
                            cat.isPinned -> ({ Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElectricCyan) })
                            else -> null
                        }
                    )
                }
            }
        }

        if (isWide) {
            // 3-Pane Desktop/Tablet Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pane 1: Categories
                LazyColumn(
                    modifier = Modifier.width(210.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        CategoryListRow(
                            name = "All Channels",
                            isSelected = selectedCategoryKey == "ALL",
                            isPinned = false,
                            isLocked = false,
                            onClick = { selectedCategoryKey = "ALL" }
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        CategoryListRow(
                            name = cat.name,
                            isSelected = selectedCategoryKey == cat.categoryKey,
                            isPinned = cat.isPinned,
                            isLocked = cat.isLocked && !isPinSessionUnlocked && hasPinConfigured,
                            onClick = {
                                onRequestUnlock(cat.isLocked, "Unlock category: ${cat.name}") {
                                    selectedCategoryKey = cat.categoryKey
                                }
                            }
                        )
                    }
                }

                // Pane 2: Channels
                LazyColumn(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        val programs = epgByChannel[ch.epgChannelId].orEmpty()
                        val nowProg = programs.firstOrNull { nowMs in it.startEpochMs..it.endEpochMs }
                        LiveChannelRow(
                            channel = ch,
                            nowProgram = nowProg,
                            nowMs = nowMs,
                            isSelected = focusedChannel?.id == ch.id,
                            isLockedForDisplay = ch.isLocked && !isPinSessionUnlocked && hasPinConfigured,
                            onClick = {
                                onRequestUnlock(ch.isLocked, "Unlock channel: ${ch.title}") {
                                    focusedChannel = ch
                                    onPlayChannel(ch, filteredChannels, null)
                                }
                            },
                            onOpenEnginePicker = {
                                onRequestUnlock(ch.isLocked, "Unlock channel: ${ch.title}") {
                                    enginePickerChannel = ch
                                }
                            },
                            onOpenCatchUp = if (ch.supportsCatchUp) {
                                {
                                    onRequestUnlock(ch.isLocked, "Unlock Catch-Up guide: ${ch.title}") {
                                        onOpenCatchUpGuide(ch)
                                    }
                                }
                            } else null,
                            onToggleFav = { onToggleFavorite(ch) },
                            onToggleLock = { onToggleStreamLock(ch) }
                        )
                    }
                }

                // Pane 3: Channel Detail & EPG Preview
                val preview = focusedChannel ?: filteredChannels.firstOrNull()
                NovaGlassCard(modifier = Modifier.weight(0.85f).fillMaxHeight()) {
                    if (preview == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a channel to inspect EPG", color = TextSecondary)
                        }
                    } else {
                        val upcoming = epgByChannel[preview.epgChannelId].orEmpty()
                            .filter { it.endEpochMs >= nowMs }
                            .take(6)
                        val savedPreviewEngine = PlaybackEngine.fromStoredName(preview.preferredEngine)
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogoBadge(preview.logoUrl, preview.title, size = 54.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preview.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "CH ${preview.channelNumber.toString().padStart(3, '0')}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ElectricCyan
                                        )
                                        if (savedPreviewEngine != PlaybackEngine.DEFAULT) {
                                            EngineBadgePill(engine = savedPreviewEngine)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        onRequestUnlock(preview.isLocked, "Unlock channel: ${preview.title}") {
                                            onPlayChannel(preview, filteredChannels, null)
                                        }
                                    },
                                color = ElectricCyan
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianBg)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Watch Fullscreen", style = MaterialTheme.typography.labelLarge, color = ObsidianBg)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    onRequestUnlock(preview.isLocked, "Unlock channel: ${preview.title}") {
                                        enginePickerChannel = preview
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("live_preview_engine_button")
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (savedPreviewEngine != PlaybackEngine.DEFAULT) {
                                        "Engine: ${savedPreviewEngine.shortBadge} (Change)"
                                    } else {
                                        "Play With / Engine…"
                                    }
                                )
                            }
                            if (preview.supportsCatchUp) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        onRequestUnlock(preview.isLocked, "Unlock Catch-Up guide: ${preview.title}") {
                                            onOpenCatchUpGuide(preview)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("live_preview_catchup_button")
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = UltravioletPulse, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Catch-Up Archive Guide", color = UltravioletPulse)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("Upcoming Schedule", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                            Spacer(Modifier.height(8.dp))
                            if (upcoming.isEmpty()) {
                                Text("No XMLTV program data linked to this channel.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            } else {
                                upcoming.forEach { prog ->
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            "${NovaFormatters.formatClock(prog.startEpochMs)} - ${NovaFormatters.formatClock(prog.endEpochMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (nowMs in prog.startEpochMs..prog.endEpochMs) ElectricCyan else TextSecondary
                                        )
                                        Text(prog.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Compact Phone List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChannels, key = { it.id }) { ch ->
                    val programs = epgByChannel[ch.epgChannelId].orEmpty()
                    val nowProg = programs.firstOrNull { nowMs in it.startEpochMs..it.endEpochMs }
                    LiveChannelRow(
                        channel = ch,
                        nowProgram = nowProg,
                        nowMs = nowMs,
                        isSelected = false,
                        isLockedForDisplay = ch.isLocked && !isPinSessionUnlocked && hasPinConfigured,
                        onClick = {
                            onRequestUnlock(ch.isLocked, "Unlock channel: ${ch.title}") {
                                onPlayChannel(ch, filteredChannels, null)
                            }
                        },
                        onOpenEnginePicker = {
                            onRequestUnlock(ch.isLocked, "Unlock channel: ${ch.title}") {
                                enginePickerChannel = ch
                            }
                        },
                        onOpenCatchUp = if (ch.supportsCatchUp) {
                            {
                                onRequestUnlock(ch.isLocked, "Unlock Catch-Up guide: ${ch.title}") {
                                    onOpenCatchUpGuide(ch)
                                }
                            }
                        } else null,
                        onToggleFav = { onToggleFavorite(ch) },
                        onToggleLock = { onToggleStreamLock(ch) }
                    )
                }
                item { Spacer(Modifier.height(84.dp)) }
            }
        }

        enginePickerChannel?.let { targetCh ->
            val savedOverride = PlaybackEngine.fromStoredName(targetCh.preferredEngine)
            EngineSwitcherModalSheet(
                streamTitle = targetCh.title,
                activeEngine = savedOverride.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine,
                savedStreamEngine = savedOverride,
                hasDrm = targetCh.drmLicenseKey.isNotBlank(),
                onSelectEngine = { chosenEngine, rememberForStream ->
                    if (rememberForStream) {
                        onSetStreamEngine(targetCh, chosenEngine)
                    }
                    val updatedCh = if (rememberForStream) {
                        targetCh.copy(preferredEngine = chosenEngine.name)
                    } else {
                        targetCh
                    }
                    onPlayChannel(updatedCh, filteredChannels, chosenEngine)
                },
                onResetStreamDefault = {
                    onSetStreamEngine(targetCh, PlaybackEngine.DEFAULT)
                },
                onDismiss = { enginePickerChannel = null }
            )
        }
    }
}

@Composable
private fun CategoryListRow(
    name: String,
    isSelected: Boolean,
    isPinned: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        color = if (isSelected) ElectricCyan.copy(alpha = 0.16f) else SlateContainer,
        border = BorderStroke(1.dp, if (isSelected) ElectricCyan else GlassBorder),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isPinned) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
            }
            if (isLocked) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Lock, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: StreamItemEntity,
    nowProgram: EpgProgramEntity?,
    nowMs: Long,
    isSelected: Boolean,
    isLockedForDisplay: Boolean,
    onClick: () -> Unit,
    onOpenEnginePicker: () -> Unit,
    onOpenCatchUp: (() -> Unit)? = null,
    onToggleFav: () -> Unit,
    onToggleLock: () -> Unit
) {
    val savedEngine = PlaybackEngine.fromStoredName(channel.preferredEngine)
    NovaGlassCard(
        highlighted = isSelected,
        modifier = Modifier.fillMaxWidth().testTag("live_channel_${channel.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.channelNumber.toString().padStart(3, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.width(34.dp)
            )
            ChannelLogoBadge(channel.logoUrl, channel.title, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (savedEngine != PlaybackEngine.DEFAULT) {
                        Spacer(Modifier.width(6.dp))
                        EngineBadgePill(engine = savedEngine)
                    }
                    if (channel.catchupDays > 0 || channel.catchupType.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = UltravioletPulse.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Catch-Up available",
                                tint = UltravioletPulse,
                                modifier = Modifier.padding(3.dp).size(12.dp)
                            )
                        }
                    }
                    if (isLockedForDisplay) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Locked channel", tint = CinemaGold, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (nowProgram != null) {
                    Text(
                        text = "${NovaFormatters.formatClock(nowProgram.startEpochMs)} • ${nowProgram.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val span = (nowProgram.endEpochMs - nowProgram.startEpochMs).coerceAtLeast(1L)
                    val progress = ((nowMs - nowProgram.startEpochMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ElectricCyan,
                        trackColor = SlateContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                    )
                } else {
                    Text(
                        text = "Live Stream • Tap to tune",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            if (onOpenCatchUp != null) {
                IconButton(
                    onClick = onOpenCatchUp,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("live_catchup_${channel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Open Catch-Up Guide",
                        tint = UltravioletPulse,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            IconButton(
                onClick = onOpenEnginePicker,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("live_engine_${channel.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Choose player engine",
                    tint = if (savedEngine != PlaybackEngine.DEFAULT) engineAccentColor(savedEngine) else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (channel.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock channel",
                    tint = if (channel.isLocked) CinemaGold else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onToggleFav,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle favorite",
                    tint = if (channel.isFavorite) BroadcastCrimson else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
