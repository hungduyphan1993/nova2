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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CategoryEntity
import com.example.data.local.PlaybackEngine
import com.example.data.local.StreamItemEntity
import com.example.data.local.WatchProgressEntity
import com.example.ui.components.EngineBadgePill
import com.example.ui.components.EngineSwitcherModalSheet
import com.example.ui.components.NovaFormatters
import com.example.ui.components.engineAccentColor
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary

@Composable
fun MoviesScreen(
    categories: List<CategoryEntity>,
    movies: List<StreamItemEntity>,
    isPinSessionUnlocked: Boolean,
    hasPinConfigured: Boolean,
    onBack: () -> Unit,
    onRequestUnlock: (Boolean, String, () -> Unit) -> Unit,
    onOpenMovie: (StreamItemEntity) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    var sortByRating by remember { mutableStateOf(false) }

    val lockedCatKeys = remember(categories, isPinSessionUnlocked, hasPinConfigured) {
        if (!hasPinConfigured || isPinSessionUnlocked) emptySet()
        else categories.filter { it.isLocked }.map { it.categoryKey }.toSet()
    }

    val filtered = remember(movies, selectedCategory, query, sortByRating, lockedCatKeys) {
        val base = movies.filter { m ->
            val catOk = if (selectedCategory == "ALL") m.categoryKey !in lockedCatKeys else m.categoryKey == selectedCategory
            val qOk = query.isBlank() || m.title.contains(query, ignoreCase = true)
            catOk && qOk
        }
        if (sortByRating) base.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 } else base
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
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("movies_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Movies (VOD)", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search movies...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("movies_search_input")
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = sortByRating,
                    onClick = { sortByRating = !sortByRating },
                    label = { Text("Top Rated") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp), tint = CinemaGold) }
                )
            }
            item {
                FilterChip(
                    selected = selectedCategory == "ALL",
                    onClick = { selectedCategory = "ALL" },
                    label = { Text("All Movies") }
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
            items(filtered, key = { it.id }) { movie ->
                PosterCard(
                    title = movie.title,
                    posterUrl = movie.logoUrl,
                    rating = movie.rating,
                    badge = movie.containerExt.uppercase().ifBlank { "VOD" },
                    onClick = {
                        onRequestUnlock(movie.isLocked, "Unlock movie: ${movie.title}") {
                            onOpenMovie(movie)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MovieDetailScreen(
    movie: StreamItemEntity?,
    resumeProgress: WatchProgressEntity?,
    defaultEngine: PlaybackEngine,
    onBack: () -> Unit,
    onLoadRichInfo: (StreamItemEntity) -> Unit,
    onToggleFavorite: (StreamItemEntity) -> Unit,
    onSetStreamEngine: (StreamItemEntity, PlaybackEngine) -> Unit,
    onPlayMovie: (StreamItemEntity, Long, PlaybackEngine?) -> Unit
) {
    var showEngineSheet by remember { mutableStateOf(false) }
    LaunchedEffect(movie?.id) {
        if (movie != null) onLoadRichInfo(movie)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        if (movie == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Movie not found", color = TextSecondary)
            }
            return
        }

        val backdrop = movie.backdropUrl.ifBlank { movie.logoUrl }
        if (backdrop.isNotBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(Brush.verticalGradient(listOf(ObsidianBg.copy(alpha = 0.25f), ObsidianBg)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().background(ObsidianBg.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = { onToggleFavorite(movie) },
                    modifier = Modifier.minimumInteractiveComponentSize().background(ObsidianBg.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Icon(
                        if (movie.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (movie.isFavorite) BroadcastCrimson else Color.White
                    )
                }
            }

            Spacer(Modifier.height(148.dp))
            Text(movie.title, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (movie.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(movie.rating, style = MaterialTheme.typography.labelLarge, color = CinemaGold)
                    }
                }
                if (movie.releaseYear.isNotBlank()) {
                    Text(movie.releaseYear, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (movie.containerExt.isNotBlank()) {
                    Surface(color = ElectricCyan.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                        Text(movie.containerExt.uppercase(), style = MaterialTheme.typography.labelSmall, color = ElectricCyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                val savedMovieEngine = PlaybackEngine.fromStoredName(movie.preferredEngine)
                EngineBadgePill(
                    engine = savedMovieEngine.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine,
                    prefix = if (savedMovieEngine != PlaybackEngine.DEFAULT) "PINNED:" else "",
                    onClick = { showEngineSheet = true }
                )
            }
            if (movie.genre.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(movie.genre, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
            }

            Spacer(Modifier.height(18.dp))
            val resumeMs = resumeProgress?.positionMs?.takeIf { it > 5000L } ?: 0L
            val savedOverride = PlaybackEngine.fromStoredName(movie.preferredEngine)
            val effectiveMovieEngine = savedOverride.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onPlayMovie(movie, resumeMs, null) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    modifier = Modifier.weight(1f).height(52.dp).testTag("play_movie_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (resumeMs > 0L) "Resume (${NovaFormatters.formatDurationMs(resumeMs)})" else "Play Movie",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = { showEngineSheet = true },
                    modifier = Modifier.height(52.dp).testTag("movie_play_with_button")
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = "Choose player engine",
                        tint = engineAccentColor(effectiveMovieEngine),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Play With")
                }
            }
            if (resumeMs > 0L) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onPlayMovie(movie, 0L, null) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Play From Beginning")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Synopsis", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            Spacer(Modifier.height(6.dp))
            Text(
                text = movie.plot.ifBlank { "High-definition on-demand feature ready to stream." },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            if (movie.cast.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("Cast: ${movie.cast}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (movie.director.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Director: ${movie.director}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.height(84.dp))
        }

        if (showEngineSheet) {
            val savedOverride = PlaybackEngine.fromStoredName(movie.preferredEngine)
            val resumeMs = resumeProgress?.positionMs?.takeIf { it > 5000L } ?: 0L
            EngineSwitcherModalSheet(
                streamTitle = movie.title,
                activeEngine = savedOverride.takeIf { it != PlaybackEngine.DEFAULT } ?: defaultEngine,
                savedStreamEngine = savedOverride,
                hasDrm = movie.drmLicenseKey.isNotBlank(),
                onSelectEngine = { chosenEngine, rememberForStream ->
                    if (rememberForStream) {
                        onSetStreamEngine(movie, chosenEngine)
                    }
                    val updatedMovie = if (rememberForStream) {
                        movie.copy(preferredEngine = chosenEngine.name)
                    } else {
                        movie
                    }
                    onPlayMovie(updatedMovie, resumeMs, chosenEngine)
                },
                onResetStreamDefault = {
                    onSetStreamEngine(movie, PlaybackEngine.DEFAULT)
                },
                onDismiss = { showEngineSheet = false }
            )
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    posterUrl: String,
    rating: String,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = SlateContainerHigh,
        border = BorderStroke(1.dp, GlassBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(SlateContainer),
                contentAlignment = Alignment.Center
            ) {
                if (posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                }
                if (badge.isNotBlank()) {
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = ElectricCyan, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (rating.isNotBlank()) {
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = CinemaGold, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(rating, style = MaterialTheme.typography.labelSmall, color = CinemaGold)
                        }
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
