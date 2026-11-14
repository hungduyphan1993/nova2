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
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse
import kotlinx.coroutines.delay
import java.util.Calendar

private enum class DayPartFilter(val label: String, val startHour: Int, val endHour: Int) {
    ALL("All Day", 0, 24),
    MORNING("Morning 06–12", 6, 12),
    AFTERNOON("Afternoon 12–18", 12, 18),
    PRIME("Prime 18–24", 18, 24),
    LATE("Late 00–06", 0, 6)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatchUpGuideScreen(
    widthSizeClass: WindowWidthSizeClass,
    categories: List<CategoryEntity>,
    liveStreams: List<StreamItemEntity>,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    initialChannelDbId: Long?,
    isSyncingArchive: Boolean,
    isPinSessionUnlocked: Boolean,
    hasPinConfigured: Boolean,
    defaultEngine: PlaybackEngine,
    onBack: () -> Unit,
    onRequestUnlock: (isLocked: Boolean, reason: String, onUnlocked: () -> Unit) -> Unit,
    onLoadChannelArchive: (StreamItemEntity, Boolean) -> Unit,
    onSetStreamEngine: (StreamItemEntity, PlaybackEngine) -> Unit,
    onPlayCatchUp: (StreamItemEntity, EpgProgramEntity, PlaybackEngine?) -> Unit,
    onTuneLive: (StreamItemEntity, PlaybackEngine?) -> Unit,
    onOpenEpgGrid: () -> Unit,
    onOpenLiveTv: () -> Unit
) {
    val isWide = widthSizeClass != WindowWidthSizeClass.Compact
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val lockedCategoryKeys = remember(categories, isPinSessionUnlocked, hasPinConfigured) {
        if (!hasPinConfigured || isPinSessionUnlocked) emptySet()
        else categories.filter { it.isLocked }.map { it.categoryKey }.toSet()
    }

    val allCatchUpChannels = remember(liveStreams, lockedCategoryKeys) {
        liveStreams.filter { it.supportsCatchUp && it.categoryKey !in lockedCategoryKeys }
    }

    var selectedCategoryKey by remember { mutableStateOf("ALL") }
    var channelQuery by remember { mutableStateOf("") }
    var showChannelPickerSheet by remember { mutableStateOf(false) }

    val availableCategories = remember(categories, allCatchUpChannels) {
        val keysWithCatchUp = allCatchUpChannels.map { it.categoryKey }.toSet()
        categories.filter { it.categoryKey in keysWithCatchUp }
    }

    val filteredChannels = remember(allCatchUpChannels, selectedCategoryKey, channelQuery) {
        allCatchUpChannels.filter { ch ->
            val catOk = when (selectedCategoryKey) {
                "ALL" -> true
                "FAV" -> ch.isFavorite
                else -> ch.categoryKey == selectedCategoryKey
            }
            val qOk = channelQuery.isBlank() ||
                ch.title.contains(channelQuery, ignoreCase = true) ||
                ch.channelNumber.toString() == channelQuery.trim()
            catOk && qOk
        }
    }

    var selectedChannelId by remember(initialChannelDbId) {
        mutableStateOf(initialChannelDbId)
    }

    val activeChannel = remember(allCatchUpChannels, filteredChannels, selectedChannelId) {
        allCatchUpChannels.firstOrNull { it.id == selectedChannelId && (!it.isLocked || isPinSessionUnlocked || !hasPinConfigured) }
            ?: filteredChannels.firstOrNull { !it.isLocked || isPinSessionUnlocked || !hasPinConfigured }
            ?: filteredChannels.firstOrNull()
    }

    var selectedDayOffset by remember(activeChannel?.id) { mutableIntStateOf(0) }
    var dayPartFilter by remember { mutableStateOf(DayPartFilter.ALL) }
    var programQuery by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    var engineSheetTarget by remember { mutableStateOf<Pair<StreamItemEntity, EpgProgramEntity?>?>(null) }

    LaunchedEffect(activeChannel?.id) {
        activeChannel?.let { ch ->
            onLoadChannelArchive(ch, false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("catchup_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Catch-Up TV Guide", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = UltravioletPulse.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, UltravioletPulse.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "${allCatchUpChannels.size} ARCHIVE CH",
                            style = MaterialTheme.typography.labelSmall,
                            color = UltravioletPulse,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Multi-day broadcast archive & instant start-over replay",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isWide && allCatchUpChannels.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showChannelPickerSheet = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("catchup_open_channels_sheet")
                ) {
                    Text("Channels", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(
                onClick = onOpenEpgGrid,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("catchup_open_2d_epg")
            ) {
                Icon(Icons.Default.CalendarViewDay, contentDescription = "Open 2D EPG Grid", tint = ElectricCyan)
            }
            if (activeChannel != null) {
                IconButton(
                    onClick = { onLoadChannelArchive(activeChannel, true) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("catchup_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh channel archive")
                }
            }
        }

        if (allCatchUpChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                NovaGlassCard(modifier = Modifier.fillMaxWidth(), accentColor = UltravioletPulse) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = UltravioletPulse,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No Catch-Up Channels Found", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "None of the channels in the active playlist advertise Catch-Up / TV Archive support (tv_archive=1 on Xtream or catchup-days in M3U).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onOpenEpgGrid,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
                            ) {
                                Text("Open 2D EPG Guide")
                            }
                            OutlinedButton(onClick = onOpenLiveTv) {
                                Text("Browse Live TV")
                            }
                        }
                    }
                }
            }
            return@Column
        }

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Pane: Searchable Catch-Up Channel Sidebar
                Column(modifier = Modifier.width(276.dp).fillMaxHeight()) {
                    OutlinedTextField(
                        value = channelQuery,
                        onValueChange = { channelQuery = it },
                        placeholder = { Text("Search archive channels...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("catchup_channel_search")
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategoryKey == "ALL",
                                onClick = { selectedCategoryKey = "ALL" },
                                label = { Text("All") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedCategoryKey == "FAV",
                                onClick = { selectedCategoryKey = "FAV" },
                                label = { Text("Favorites") }
                            )
                        }
                        items(availableCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategoryKey == cat.categoryKey,
                                onClick = { selectedCategoryKey = cat.categoryKey },
                                label = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredChannels, key = { it.id }) { ch ->
                            val lockedForDisplay = ch.isLocked && !isPinSessionUnlocked && hasPinConfigured
                            CatchUpChannelItem(
                                channel = ch,
                                isSelected = activeChannel?.id == ch.id,
                                isLockedForDisplay = lockedForDisplay,
                                onClick = {
                                    onRequestUnlock(ch.isLocked, "Unlock Catch-Up channel: ${ch.title}") {
                                        selectedChannelId = ch.id
                                    }
                                }
                            )
                        }
                    }
                }

                // Right Pane: Selected Channel Multi-Day Archive Guide
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeChannel != null) {
                        CatchUpArchiveDetailPane(
                            channel = activeChannel,
                            epgByChannel = epgByChannel,
                            nowMs = nowMs,
                            isSyncingArchive = isSyncingArchive,
                            selectedDayOffset = selectedDayOffset,
                            dayPartFilter = dayPartFilter,
                            programQuery = programQuery,
                            newestFirst = newestFirst,
                            onSelectDayOffset = { selectedDayOffset = it },
                            onSelectDayPart = { dayPartFilter = it },
                            onProgramQueryChange = { programQuery = it },
                            onToggleSortOrder = { newestFirst = !newestFirst },
                            onReplayProgram = { prog ->
                                onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                    onPlayCatchUp(activeChannel, prog, null)
                                }
                            },
                            onPickEngineForProgram = { prog ->
                                onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                    engineSheetTarget = activeChannel to prog
                                }
                            },
                            onTuneLive = {
                                onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                    onTuneLive(activeChannel, null)
                                }
                            },
                            onPickEngineForChannel = {
                                onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                    engineSheetTarget = activeChannel to null
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Compact Phone Layout: Quick Horizontal Channel Strip + Full Multi-Day Archive Schedule
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allCatchUpChannels, key = { it.id }) { ch ->
                    val selected = activeChannel?.id == ch.id
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("catchup_chip_ch_${ch.id}")
                            .clickable {
                                onRequestUnlock(ch.isLocked, "Unlock Catch-Up channel: ${ch.title}") {
                                    selectedChannelId = ch.id
                                }
                            }
                            .minimumInteractiveComponentSize(),
                        color = if (selected) UltravioletPulse.copy(alpha = 0.22f) else SlateContainer,
                        border = BorderStroke(1.dp, if (selected) UltravioletPulse else GlassBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelLogoBadge(ch.logoUrl, ch.title, size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = ch.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (ch.catchupDays > 0) "${ch.catchupDays}d Archive" else "Archive",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UltravioletPulse
                                )
                            }
                        }
                    }
                }
            }

            if (activeChannel != null) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    CatchUpArchiveDetailPane(
                        channel = activeChannel,
                        epgByChannel = epgByChannel,
                        nowMs = nowMs,
                        isSyncingArchive = isSyncingArchive,
                        selectedDayOffset = selectedDayOffset,
                        dayPartFilter = dayPartFilter,
                        programQuery = programQuery,
                        newestFirst = newestFirst,
                        onSelectDayOffset = { selectedDayOffset = it },
                        onSelectDayPart = { dayPartFilter = it },
                        onProgramQueryChange = { programQuery = it },
                        onToggleSortOrder = { newestFirst = !newestFirst },
                        onReplayProgram = { prog ->
                            onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                onPlayCatchUp(activeChannel, prog, null)
                            }
                        },
                        onPickEngineForProgram = { prog ->
                            onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                engineSheetTarget = activeChannel to prog
                            }
                        },
                        onTuneLive = {
                            onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                onTuneLive(activeChannel, null)
                            }
                        },
                        onPickEngineForChannel = {
                            onRequestUnlock(activeChannel.isLocked, "Unlock channel: ${activeChannel.title}") {
                                engineSheetTarget = activeChannel to null
                            }
                        }
                    )
                }
            }
        }

        // Compact Channel Picker Bottom Sheet
        if (showChannelPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showChannelPickerSheet = false },
                containerColor = SlateContainerHigh
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Select Catch-Up Channel", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelQuery,
                        onValueChange = { channelQuery = it },
                        placeholder = { Text("Search channel name or #...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredChannels, key = { it.id }) { ch ->
                            val lockedForDisplay = ch.isLocked && !isPinSessionUnlocked && hasPinConfigured
                            CatchUpChannelItem(
                                channel = ch,
                                isSelected = activeChannel?.id == ch.id,
                                isLockedForDisplay = lockedForDisplay,
                                onClick = {
                                    onRequestUnlock(ch.isLocked, "Unlock Catch-Up channel: ${ch.title}") {
                                        selectedChannelId = ch.id
                                        showChannelPickerSheet = false
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        // Engine Picker Sheet for Channel or Individual Archived Program
        engineSheetTarget?.let { (targetChannel, targetProgram) ->
            val savedOverride = PlaybackEngine.fromStoredName(targetChannel.preferredEngine)
            EngineSwitcherModalSheet(
                streamTitle = if (targetProgram != null) "${targetChannel.title} • ${targetProgram.title}" else targetChannel.title,
                activeEngine = savedOverride.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine,
                savedStreamEngine = savedOverride,
                hasDrm = targetChannel.drmLicenseKey.isNotBlank(),
                onSelectEngine = { chosenEngine, rememberForStream ->
                    if (rememberForStream) {
                        onSetStreamEngine(targetChannel, chosenEngine)
                    }
                    val updatedCh = if (rememberForStream) {
                        targetChannel.copy(preferredEngine = chosenEngine.name)
                    } else {
                        targetChannel
                    }
                    if (targetProgram != null) {
                        onPlayCatchUp(updatedCh, targetProgram, chosenEngine)
                    } else {
                        onTuneLive(updatedCh, chosenEngine)
                    }
                },
                onResetStreamDefault = {
                    onSetStreamEngine(targetChannel, PlaybackEngine.DEFAULT)
                },
                onDismiss = { engineSheetTarget = null }
            )
        }
    }
}

@Composable
private fun CatchUpChannelItem(
    channel: StreamItemEntity,
    isSelected: Boolean,
    isLockedForDisplay: Boolean,
    onClick: () -> Unit
) {
    val savedEngine = PlaybackEngine.fromStoredName(channel.preferredEngine)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .testTag("catchup_channel_${channel.id}")
            .clickable(onClick = onClick),
        color = if (isSelected) UltravioletPulse.copy(alpha = 0.2f) else SlateContainer,
        border = BorderStroke(1.dp, if (isSelected) UltravioletPulse else GlassBorder),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.channelNumber.toString().padStart(3, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.width(30.dp)
            )
            ChannelLogoBadge(channel.logoUrl, channel.title, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (channel.catchupDays > 0) "${channel.catchupDays}d Archive" else "Timeshift Archive",
                        style = MaterialTheme.typography.labelSmall,
                        color = UltravioletPulse
                    )
                    if (savedEngine != PlaybackEngine.DEFAULT) {
                        EngineBadgePill(engine = savedEngine)
                    }
                }
            }
            if (isLockedForDisplay) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = CinemaGold, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatchUpArchiveDetailPane(
    channel: StreamItemEntity,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    nowMs: Long,
    isSyncingArchive: Boolean,
    selectedDayOffset: Int,
    dayPartFilter: DayPartFilter,
    programQuery: String,
    newestFirst: Boolean,
    onSelectDayOffset: (Int) -> Unit,
    onSelectDayPart: (DayPartFilter) -> Unit,
    onProgramQueryChange: (String) -> Unit,
    onToggleSortOrder: () -> Unit,
    onReplayProgram: (EpgProgramEntity) -> Unit,
    onPickEngineForProgram: (EpgProgramEntity) -> Unit,
    onTuneLive: () -> Unit,
    onPickEngineForChannel: () -> Unit
) {
    val maxArchiveDays = remember(channel.catchupDays) {
        channel.catchupDays.takeIf { it > 0 }?.coerceAtMost(7) ?: 3
    }
    val todayStartMs = remember(nowMs) { NovaFormatters.startOfLocalDayMs(nowMs) }

    val channelPrograms = remember(channel, epgByChannel) {
        val byPrimary = channel.epgChannelId.takeIf { it.isNotBlank() }?.let { epgByChannel[it] }.orEmpty()
        if (byPrimary.isNotEmpty()) byPrimary else epgByChannel[channel.effectiveEpgKey].orEmpty()
    }

    val dayBuckets = remember(todayStartMs, maxArchiveDays, channelPrograms, nowMs) {
        (0..maxArchiveDays).map { backDays ->
            val dayStart = NovaFormatters.addLocalDays(todayStartMs, -backDays)
            val dayEnd = NovaFormatters.addLocalDays(dayStart, 1)
            val airedInDay = channelPrograms.filter { prog ->
                prog.startEpochMs in dayStart until dayEnd && prog.startEpochMs <= nowMs
            }
            Triple(backDays, dayStart, airedInDay)
        }
    }

    val clampedOffset = selectedDayOffset.coerceIn(0, maxArchiveDays)
    val selectedDayStart = NovaFormatters.addLocalDays(todayStartMs, -clampedOffset)
    val selectedDayEnd = NovaFormatters.addLocalDays(selectedDayStart, 1)

    val rawDayPrograms = remember(channelPrograms, selectedDayStart, selectedDayEnd, nowMs) {
        channelPrograms.filter { prog ->
            prog.startEpochMs in selectedDayStart until selectedDayEnd && prog.startEpochMs <= nowMs
        }
    }

    val usingSyntheticTimeshift = rawDayPrograms.isEmpty() && channel.supportsCatchUp
    val effectiveDayPrograms = remember(rawDayPrograms, usingSyntheticTimeshift, selectedDayStart, selectedDayEnd, nowMs, channel) {
        if (!usingSyntheticTimeshift) {
            rawDayPrograms
        } else {
            buildHourlyTimeshiftBlocks(
                profileId = channel.profileId,
                epgKey = channel.effectiveEpgKey,
                dayStartMs = selectedDayStart,
                dayEndMs = selectedDayEnd,
                nowMs = nowMs
            )
        }
    }

    val visiblePrograms = remember(effectiveDayPrograms, dayPartFilter, programQuery, newestFirst) {
        val cal = Calendar.getInstance()
        val filtered = effectiveDayPrograms.filter { prog ->
            val partOk = if (dayPartFilter == DayPartFilter.ALL) {
                true
            } else {
                cal.timeInMillis = prog.startEpochMs
                val hr = cal.get(Calendar.HOUR_OF_DAY)
                hr in dayPartFilter.startHour until dayPartFilter.endHour
            }
            val qOk = programQuery.isBlank() ||
                prog.title.contains(programQuery, ignoreCase = true) ||
                prog.description.contains(programQuery, ignoreCase = true)
            partOk && qOk
        }
        if (newestFirst) filtered.sortedByDescending { it.startEpochMs } else filtered.sortedBy { it.startEpochMs }
    }

    val savedEngine = PlaybackEngine.fromStoredName(channel.preferredEngine)

    Column(modifier = Modifier.fillMaxSize()) {
        // Channel Spotlight Header Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = SlateContainerHigh,
            border = BorderStroke(1.dp, UltravioletPulse.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(UltravioletPulse.copy(alpha = 0.18f), SlateContainer.copy(alpha = 0.94f))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelLogoBadge(channel.logoUrl, channel.title, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSyncingArchive) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = UltravioletPulse
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CH ${channel.channelNumber.toString().padStart(3, '0')} • ${maxArchiveDays}d Replay Window",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (savedEngine != PlaybackEngine.DEFAULT) {
                            EngineBadgePill(engine = savedEngine)
                        }
                    }
                }
                IconButton(
                    onClick = onPickEngineForChannel,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("catchup_channel_engine_btn")
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = "Choose playback engine",
                        tint = if (savedEngine != PlaybackEngine.DEFAULT) engineAccentColor(savedEngine) else CinemaGold
                    )
                }
                Button(
                    onClick = onTuneLive,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("catchup_watch_live_btn")
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("LIVE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Multi-Day Date Selector Strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(dayBuckets, key = { it.first }) { (backDays, dayStart, progs) ->
                val isSelected = backDays == clampedOffset
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("catchup_day_$backDays")
                        .clickable { onSelectDayOffset(backDays) }
                        .minimumInteractiveComponentSize(),
                    color = if (isSelected) UltravioletPulse.copy(alpha = 0.26f) else SlateContainer,
                    border = BorderStroke(1.dp, if (isSelected) UltravioletPulse else GlassBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = NovaFormatters.formatDayTabTitle(dayStart, nowMs),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                        Text(
                            text = NovaFormatters.formatDayTabSubtitle(dayStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) UltravioletPulse else TextMuted
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (progs.isNotEmpty()) "${progs.size} shows" else "Timeshift",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (progs.isNotEmpty()) ElectricCyan else TextMuted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Time-of-Day Filter Chips + Sort Order + Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = programQuery,
                onValueChange = onProgramQueryChange,
                placeholder = { Text("Search aired shows on ${NovaFormatters.formatDayTabTitle(selectedDayStart, nowMs)}...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("catchup_program_search")
            )
            FilterChip(
                selected = newestFirst,
                onClick = onToggleSortOrder,
                label = { Text(if (newestFirst) "Latest First" else "00:00 → 24:00") },
                leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("catchup_sort_toggle")
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(DayPartFilter.entries, key = { it.name }) { part ->
                FilterChip(
                    selected = dayPartFilter == part,
                    onClick = { onSelectDayPart(part) },
                    label = { Text(part.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f)
                    )
                )
            }
        }

        if (usingSyntheticTimeshift) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                color = SlateContainerHigh,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CinemaGold.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "No XMLTV program titles linked for this date — showing 1-hour direct archive timeshift blocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Chronological / Latest-First Archive Program Cards
        if (visiblePrograms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No archived broadcasts match this filter on ${NovaFormatters.formatFullDayHeader(selectedDayStart)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visiblePrograms, key = { "${it.id}_${it.startEpochMs}" }) { prog ->
                    val isCurrentlyLive = nowMs in prog.startEpochMs until prog.endEpochMs
                    CatchUpProgramCard(
                        program = prog,
                        isCurrentlyLive = isCurrentlyLive,
                        nowMs = nowMs,
                        onReplay = { onReplayProgram(prog) },
                        onPickEngine = { onPickEngineForProgram(prog) },
                        onTuneLive = onTuneLive
                    )
                }
            }
        }
    }
}

@Composable
private fun CatchUpProgramCard(
    program: EpgProgramEntity,
    isCurrentlyLive: Boolean,
    nowMs: Long,
    onReplay: () -> Unit,
    onPickEngine: () -> Unit,
    onTuneLive: () -> Unit
) {
    val accent = if (isCurrentlyLive) ElectricCyan else UltravioletPulse
    NovaGlassCard(
        modifier = Modifier.fillMaxWidth().testTag("catchup_prog_${program.startEpochMs}"),
        highlighted = isCurrentlyLive,
        accentColor = accent,
        onClick = onReplay
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Time Window Pill
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "${NovaFormatters.formatClock(program.startEpochMs)} – ${NovaFormatters.formatClock(program.endEpochMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = NovaFormatters.formatMinutesSpan(program.startEpochMs, program.endEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (isCurrentlyLive) ElectricCyan.copy(alpha = 0.2f) else SlateContainerHigh,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isCurrentlyLive) "ON AIR • START OVER" else "ARCHIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentlyLive) ElectricCyan else UltravioletPulse,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onPickEngine,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = "Play Catch-Up with engine",
                        tint = CinemaGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isCurrentlyLive) {
                    OutlinedButton(
                        onClick = onTuneLive,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Live", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Button(
                    onClick = onReplay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (isCurrentlyLive) ObsidianBg else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isCurrentlyLive) Icons.Default.History else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isCurrentlyLive) "Start Over" else "Replay",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (program.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = program.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrentlyLive) {
                val span = (program.endEpochMs - program.startEpochMs).coerceAtLeast(1L)
                val progress = ((nowMs - program.startEpochMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = ElectricCyan,
                    trackColor = SlateContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

internal fun buildHourlyTimeshiftBlocks(
    profileId: Long,
    epgKey: String,
    dayStartMs: Long,
    dayEndMs: Long,
    nowMs: Long
): List<EpgProgramEntity> {
    val ceilingMs = minOf(dayEndMs, nowMs)
    val blocks = mutableListOf<EpgProgramEntity>()
    var cursor = dayStartMs
    var idx = 0
    while (cursor < ceilingMs) {
        val next = (cursor + 3_600_000L).coerceAtMost(dayEndMs)
        blocks.add(
            EpgProgramEntity(
                id = -(idx + 1L + (dayStartMs / 86_400_000L) * 100L),
                profileId = profileId,
                epgChannelId = epgKey,
                title = "Timeshift Slot ${NovaFormatters.formatClock(cursor)} – ${NovaFormatters.formatClock(next)}",
                description = "Direct 1-hour Catch-Up archive stream starting at ${NovaFormatters.formatClock(cursor)}.",
                startEpochMs = cursor,
                endEpochMs = next
            )
        )
        cursor = next
        idx++
    }
    return blocks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InPlayerCatchUpModalSheet(
    channel: StreamItemEntity,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    nowMs: Long,
    onPlayProgram: (EpgProgramEntity) -> Unit,
    onReturnToLive: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxArchiveDays = remember(channel.catchupDays) {
        channel.catchupDays.takeIf { it > 0 }?.coerceAtMost(7) ?: 3
    }
    val todayStartMs = remember(nowMs) { NovaFormatters.startOfLocalDayMs(nowMs) }
    var selectedDayOffset by remember(channel.id) { mutableIntStateOf(0) }

    val channelPrograms = remember(channel, epgByChannel) {
        val byPrimary = channel.epgChannelId.takeIf { it.isNotBlank() }?.let { epgByChannel[it] }.orEmpty()
        if (byPrimary.isNotEmpty()) byPrimary else epgByChannel[channel.effectiveEpgKey].orEmpty()
    }

    val dayStart = NovaFormatters.addLocalDays(todayStartMs, -selectedDayOffset)
    val dayEnd = NovaFormatters.addLocalDays(dayStart, 1)

    val dayPrograms = remember(channelPrograms, dayStart, dayEnd, nowMs, channel) {
        val matching = channelPrograms.filter {
            it.startEpochMs in dayStart until dayEnd && it.startEpochMs <= nowMs
        }
        val list = if (matching.isNotEmpty()) {
            matching
        } else {
            buildHourlyTimeshiftBlocks(
                profileId = channel.profileId,
                epgKey = channel.effectiveEpgKey,
                dayStartMs = dayStart,
                dayEndMs = dayEnd,
                nowMs = nowMs
            )
        }
        list.sortedByDescending { it.startEpochMs }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateContainerHigh
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelLogoBadge(channel.logoUrl, channel.title, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Catch-Up Guide • ${channel.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${maxArchiveDays}d Broadcast Archive", style = MaterialTheme.typography.bodySmall, color = UltravioletPulse)
                }
                OutlinedButton(
                    onClick = {
                        onReturnToLive()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watch Live", color = ElectricCyan, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0..maxArchiveDays).toList(), key = { it }) { backDays ->
                    val tabDayStart = NovaFormatters.addLocalDays(todayStartMs, -backDays)
                    FilterChip(
                        selected = selectedDayOffset == backDays,
                        onClick = { selectedDayOffset = backDays },
                        label = {
                            Text(
                                "${NovaFormatters.formatDayTabTitle(tabDayStart, nowMs)} (${NovaFormatters.formatDayTabSubtitle(tabDayStart)})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltravioletPulse.copy(alpha = 0.28f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.height(320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(dayPrograms, key = { "${it.id}_${it.startEpochMs}" }) { prog ->
                    val isLive = nowMs in prog.startEpochMs until prog.endEpochMs
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                onPlayProgram(prog)
                                onDismiss()
                            },
                        color = if (isLive) ElectricCyan.copy(alpha = 0.14f) else SlateContainer,
                        border = BorderStroke(1.dp, if (isLive) ElectricCyan else GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${NovaFormatters.formatClock(prog.startEpochMs)} – ${NovaFormatters.formatClock(prog.endEpochMs)}" +
                                        if (isLive) " • ON AIR (Start Over)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLive) ElectricCyan else UltravioletPulse
                                )
                                Text(prog.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = if (isLive) Icons.Default.History else Icons.Default.PlayArrow,
                                contentDescription = "Replay",
                                tint = if (isLive) ElectricCyan else UltravioletPulse
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
