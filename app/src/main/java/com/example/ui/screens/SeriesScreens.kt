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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CategoryEntity
import com.example.data.local.PlaybackEngine
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import com.example.ui.components.EngineBadgePill
import com.example.ui.components.EngineSwitcherModalSheet
import com.example.ui.components.NovaFormatters
import com.example.ui.components.NovaGlassCard
import com.example.ui.components.engineAccentColor
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

@Composable
fun SeriesScreen(
    categories: List<CategoryEntity>,
    seriesList: List<StreamItemEntity>,
    isPinSessionUnlocked: Boolean,
    hasPinConfigured: Boolean,
    onBack: () -> Unit,
    onRequestUnlock: (Boolean, String, () -> Unit) -> Unit,
    onOpenSeries: (StreamItemEntity) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }

    val lockedCatKeys = remember(categories, isPinSessionUnlocked, hasPinConfigured) {
        if (!hasPinConfigured || isPinSessionUnlocked) emptySet()
        else categories.filter { it.isLocked }.map { it.categoryKey }.toSet()
    }

    val filtered = remember(seriesList, selectedCategory, query, lockedCatKeys) {
        seriesList.filter { s ->
            val catOk = if (selectedCategory == "ALL") s.categoryKey !in lockedCatKeys else s.categoryKey == selectedCategory
            val qOk = query.isBlank() || s.title.contains(query, ignoreCase = true)
            catOk && qOk
        }
    }

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
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("series_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("TV Series", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search series...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("series_search_input")
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == "ALL",
                    onClick = { selectedCategory = "ALL" },
                    label = { Text("All Series") }
                )
            }
            items(categories, key = { it.id }) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.categoryKey,
                    onClick = {
                        onRequestUnlock(cat.isLocked, "Unlock folder: ${cat.name}") {
                            selectedCategory = cat.categoryKey
                        }
                    },
                    label = { Text(cat.name) },
                    leadingIcon = if (cat.isLocked) {
                        { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = CinemaGold) }
                    } else null
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { series ->
                PosterCard(
                    title = series.title,
                    posterUrl = series.logoUrl,
                    rating = series.rating,
                    badge = "SERIES",
                    onClick = {
                        onRequestUnlock(series.isLocked, "Unlock series: ${series.title}") {
                            onOpenSeries(series)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(
    series: StreamItemEntity?,
    episodes: List<SeriesEpisodeEntity>,
    defaultEngine: PlaybackEngine,
    onBack: () -> Unit,
    onLoadEpisodes: (StreamItemEntity) -> Unit,
    onToggleFavorite: (StreamItemEntity) -> Unit,
    onSetStreamEngine: (StreamItemEntity, PlaybackEngine) -> Unit,
    onPlayEpisode: (StreamItemEntity, SeriesEpisodeEntity, List<SeriesEpisodeEntity>, PlaybackEngine?) -> Unit
) {
    LaunchedEffect(series?.id) {
        if (series != null) onLoadEpisodes(series)
    }

    val seasons = remember(episodes) { episodes.map { it.seasonNumber }.distinct().sorted() }
    var selectedSeason by remember(seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNumber == selectedSeason }
    }
    var showSeriesEngineSheet by remember { mutableStateOf(false) }
    var episodeForEngineSheet by remember { mutableStateOf<SeriesEpisodeEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (series == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Series not found", color = TextSecondary)
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(series.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { onToggleFavorite(series) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(
                    if (series.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (series.isFavorite) BroadcastCrimson else Color.White
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (series.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = series.logoUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(108.dp).height(154.dp).clip(MaterialTheme.shapes.medium)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (series.genre.isNotBlank()) {
                            Text(series.genre, style = MaterialTheme.typography.labelMedium, color = UltravioletPulse)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (series.releaseYear.isNotBlank()) {
                            Text("Released ${series.releaseYear}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            series.plot.ifBlank { "Browse seasons and stream episodes in HD." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        val savedSeriesEngine = PlaybackEngine.fromStoredName(series.preferredEngine)
                        val effectiveSeriesEngine = savedSeriesEngine.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine
                        OutlinedButton(
                            onClick = { showSeriesEngineSheet = true },
                            modifier = Modifier.testTag("series_engine_button")
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                tint = engineAccentColor(effectiveSeriesEngine),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (savedSeriesEngine != PlaybackEngine.DEFAULT) {
                                    "Pinned Engine: ${savedSeriesEngine.shortBadge}"
                                } else {
                                    "Engine: ${defaultEngine.shortBadge}"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { s ->
                            FilterChip(
                                selected = selectedSeason == s,
                                onClick = { selectedSeason = s },
                                label = { Text("Season $s") }
                            )
                        }
                    }
                }
            }

            if (seasonEpisodes.isEmpty()) {
                item {
                    Text("Loading episodes or none found for this season...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            } else {
                items(seasonEpisodes, key = { it.id }) { ep ->
                    NovaGlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("episode_card_${ep.episodeNumber}"),
                        onClick = { onPlayEpisode(series, ep, episodes, null) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(UltravioletPulse.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play episode", tint = ElectricCyan)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "E${ep.episodeNumber.toString().padStart(2, '0')} • ${ep.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ep.plot.isNotBlank()) {
                                    Text(ep.plot, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (ep.durationSec > 0) {
                                    Text(
                                        NovaFormatters.formatDurationMs(ep.durationSec * 1000L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                            }
                            IconButton(
                                onClick = { episodeForEngineSheet = ep },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("episode_engine_${ep.episodeNumber}")
                            ) {
                                val savedSeriesEngine = PlaybackEngine.fromStoredName(series.preferredEngine)
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = "Play episode with engine",
                                    tint = if (savedSeriesEngine != PlaybackEngine.DEFAULT) engineAccentColor(savedSeriesEngine) else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        if (showSeriesEngineSheet || episodeForEngineSheet != null) {
            val savedOverride = PlaybackEngine.fromStoredName(series.preferredEngine)
            val targetEpisode = episodeForEngineSheet
            EngineSwitcherModalSheet(
                streamTitle = if (targetEpisode != null) "${series.title} • E${targetEpisode.episodeNumber}" else series.title,
                activeEngine = savedOverride.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine,
                savedStreamEngine = savedOverride,
                hasDrm = series.drmLicenseKey.isNotBlank(),
                defaultRememberSelection = targetEpisode == null,
                onSelectEngine = { chosenEngine, rememberForStream ->
                    if (rememberForStream || targetEpisode == null) {
                        onSetStreamEngine(series, chosenEngine)
                    }
                    if (targetEpisode != null) {
                        val updatedSeries = if (rememberForStream) {
                            series.copy(preferredEngine = chosenEngine.name)
                        } else {
                            series
                        }
                        onPlayEpisode(updatedSeries, targetEpisode, episodes, chosenEngine)
                    }
                },
                onResetStreamDefault = {
                    onSetStreamEngine(series, PlaybackEngine.DEFAULT)
                },
                onDismiss = {
                    showSeriesEngineSheet = false
                    episodeForEngineSheet = null
                }
            )
        }
    }
}
