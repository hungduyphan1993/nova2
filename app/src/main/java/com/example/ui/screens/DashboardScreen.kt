package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.config.WhitelabelConfig
import com.example.data.local.EpgProgramEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.PlaylistSourceType
import com.example.data.local.StreamItemEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.repository.SyncStage
import com.example.ui.components.ChannelLogoBadge
import com.example.ui.components.CinemaInfoBar
import com.example.ui.components.NovaFormatters
import com.example.ui.components.NovaGlassCard
import com.example.ui.components.SyncBanner
import com.example.ui.components.WhitelabelBrandEmblem
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    widthSizeClass: WindowWidthSizeClass,
    profile: PlaylistProfileEntity?,
    counts: Map<MediaCategoryType, Int>,
    liveStreams: List<StreamItemEntity>,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    activeLiveChannel: StreamItemEntity?,
    favorites: List<StreamItemEntity>,
    watchProgress: List<WatchProgressEntity>,
    hasPinConfigured: Boolean,
    isPinSessionUnlocked: Boolean,
    syncStage: SyncStage,
    onDismissSync: () -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenMultiScreen: () -> Unit,
    onOpenCatchUp: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onRefreshSync: () -> Unit,
    onToggleSessionRelock: () -> Unit,
    onTuneSpotlightChannel: (StreamItemEntity) -> Unit,
    onResumeItem: (WatchProgressEntity) -> Unit
) {
    val isWide = widthSizeClass != WindowWidthSizeClass.Compact
    val liveCount = counts[MediaCategoryType.LIVE] ?: 0
    val movieCount = counts[MediaCategoryType.MOVIE] ?: 0
    val seriesCount = counts[MediaCategoryType.SERIES] ?: 0

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val spotlightChannel = remember(activeLiveChannel, watchProgress, favorites, liveStreams) {
        activeLiveChannel
            ?: watchProgress
                .firstOrNull { it.mediaType == MediaCategoryType.LIVE }
                ?.let { prog -> liveStreams.firstOrNull { it.id == prog.streamDbId } }
            ?: favorites.firstOrNull { it.mediaType == MediaCategoryType.LIVE }
            ?: liveStreams.firstOrNull()
    }
    val spotlightEpg = remember(spotlightChannel, epgByChannel) {
        spotlightChannel?.epgChannelId?.takeIf { it.isNotBlank() }?.let { epgByChannel[it] }.orEmpty()
    }
    val spotlightNowProgram = remember(spotlightEpg, nowMs) {
        spotlightEpg.firstOrNull { nowMs in it.startEpochMs until it.endEpochMs }
    }
    val spotlightNextProgram = remember(spotlightEpg, spotlightNowProgram, nowMs) {
        val threshold = spotlightNowProgram?.endEpochMs ?: nowMs
        spotlightEpg.firstOrNull { it.startEpochMs >= threshold }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_broadcast),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .alpha(0.42f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ObsidianBg)
                    )
                )
        )

        val horizontalSafeModifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 18.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            // Top Command Header
            Row(
                modifier = horizontalSafeModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (WhitelabelConfig.hasCustomLogo) {
                    WhitelabelBrandEmblem(
                        size = 38.dp,
                        cornerRadius = 10.dp,
                        accentColor = ElectricCyan
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = WhitelabelConfig.shortBrandUpper,
                            style = MaterialTheme.typography.titleLarge,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = profile?.accountStatus?.uppercase() ?: "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = profile?.let {
                            val exp = if (it.sourceType == PlaylistSourceType.XTREAM) {
                                " • Exp: ${NovaFormatters.formatDateEpochSec(it.expirationEpochSec)}"
                            } else {
                                ""
                            }
                            "${it.name}$exp"
                        } ?: "No active playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_search_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Global search")
                }
                IconButton(
                    onClick = onRefreshSync,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh playlists and EPG")
                }
                if (hasPinConfigured) {
                    IconButton(
                        onClick = onToggleSessionRelock,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_lock_button")
                    ) {
                        Icon(
                            imageVector = if (isPinSessionUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Parental lock status",
                            tint = if (isPinSessionUnlocked) CinemaGold else ElectricCyan
                        )
                    }
                }
                IconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_diagnostics_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = "Server speed diagnostics")
                }
                IconButton(
                    onClick = onOpenProfiles,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_profiles_button")
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Switch playlist profile")
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }

            SyncBanner(
                stage = syncStage,
                onDismiss = onDismissSync,
                modifier = horizontalSafeModifier
            )

            // Full-Width Edge-to-Edge Dashboard Status Cinema Bar (IBO Player Style)
            if (spotlightChannel != null) {
                Spacer(Modifier.height(10.dp))
                CinemaInfoBar(
                    channel = spotlightChannel,
                    nowProgram = spotlightNowProgram,
                    nextProgram = spotlightNextProgram,
                    nowMs = nowMs,
                    liveBadgeText = if (activeLiveChannel != null) "ON AIR" else "LIVE NOW",
                    testTag = "dashboard_cinema_info_bar",
                    contentInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    onClick = { onTuneSpotlightChannel(spotlightChannel) },
                    trailingAction = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = NovaFormatters.formatClock(nowMs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Button(
                                onClick = { onTuneSpotlightChannel(spotlightChannel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_watch_live_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (activeLiveChannel != null) "RESUME" else "WATCH LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(modifier = horizontalSafeModifier) {

            // Primary Hero Cards (Smarters / IBO Signature Stage)
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeroHubTile(
                        title = "LIVE TV",
                        subtitle = "$liveCount Channels • Zapping & Catch-Up",
                        icon = Icons.Default.LiveTv,
                        accent = ElectricCyan,
                        badgeText = "LIVE",
                        modifier = Modifier.weight(1.25f).height(168.dp).testTag("hub_live_tv"),
                        onClick = onOpenLiveTv
                    )
                    HeroHubTile(
                        title = "MOVIES",
                        subtitle = "$movieCount VOD Titles",
                        icon = Icons.Default.Movie,
                        accent = CinemaGold,
                        badgeText = "4K VOD",
                        modifier = Modifier.weight(1f).height(168.dp).testTag("hub_movies"),
                        onClick = onOpenMovies
                    )
                    HeroHubTile(
                        title = "SERIES",
                        subtitle = "$seriesCount Boxsets & Seasons",
                        icon = Icons.Default.Tv,
                        accent = UltravioletPulse,
                        badgeText = "SHOWS",
                        modifier = Modifier.weight(1f).height(168.dp).testTag("hub_series"),
                        onClick = onOpenSeries
                    )
                }
            } else {
                HeroHubTile(
                    title = "LIVE TV",
                    subtitle = "$liveCount Live Channels • Instant Zapping & Catch-Up",
                    icon = Icons.Default.LiveTv,
                    accent = ElectricCyan,
                    badgeText = "LIVE BROADCAST",
                    modifier = Modifier.fillMaxWidth().height(132.dp).testTag("hub_live_tv"),
                    onClick = onOpenLiveTv
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroHubTile(
                        title = "MOVIES",
                        subtitle = "$movieCount Titles",
                        icon = Icons.Default.Movie,
                        accent = CinemaGold,
                        badgeText = "VOD",
                        modifier = Modifier.weight(1f).height(138.dp).testTag("hub_movies"),
                        onClick = onOpenMovies
                    )
                    HeroHubTile(
                        title = "SERIES",
                        subtitle = "$seriesCount Shows",
                        icon = Icons.Default.Tv,
                        accent = UltravioletPulse,
                        badgeText = "SEASONS",
                        modifier = Modifier.weight(1f).height(138.dp).testTag("hub_series"),
                        onClick = onOpenSeries
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Secondary Action Deck
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickDeckCard(
                    title = "2D EPG Guide",
                    subtitle = "Timeline & Catch-Up",
                    icon = Icons.Default.CalendarViewDay,
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f).testTag("deck_epg_guide"),
                    onClick = onOpenEpg
                )
                QuickDeckCard(
                    title = "Multi-Screen",
                    subtitle = "Up to 4x Split",
                    icon = Icons.Default.GridView,
                    accent = EmeraldSignal,
                    modifier = Modifier.weight(1f).testTag("deck_multi_screen"),
                    onClick = onOpenMultiScreen
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickDeckCard(
                    title = "Catch-Up Guide",
                    subtitle = "Multi-Day Replay",
                    icon = Icons.Default.History,
                    accent = UltravioletPulse,
                    modifier = Modifier.weight(1f).testTag("deck_catchup"),
                    onClick = onOpenCatchUp
                )
                QuickDeckCard(
                    title = "Favorites",
                    subtitle = "${favorites.size} Saved",
                    icon = Icons.Default.Favorite,
                    accent = BroadcastCrimson,
                    modifier = Modifier.weight(1f).testTag("deck_favorites"),
                    onClick = onOpenFavorites
                )
            }

            if (watchProgress.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Continue Watching & Recent", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 12.dp)
                ) {
                    items(watchProgress, key = { it.id }) { item ->
                        ContinueWatchingCard(
                            item = item,
                            onClick = { onResumeItem(item) }
                        )
                    }
                }
            } else if (liveCount == 0 && movieCount == 0 && seriesCount == 0) {
                Spacer(Modifier.height(24.dp))
                NovaGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenProfiles
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_channels),
                            contentDescription = "No channels loaded",
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("No Active Channels", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap to connect an Xtream server, load an M3U playlist, or try the Public FTA Demo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(92.dp))
            }
        }
    }
}

@Composable
private fun HeroHubTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    badgeText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = SlateContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.22f), SlateContainer.copy(alpha = 0.94f))
                    )
                )
                .padding(16.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }

            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickDeckCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NovaGlassCard(
        modifier = modifier.height(74.dp),
        accentColor = accent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: WatchProgressEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(196.dp)
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
                    .height(98.dp)
                    .background(SlateContainer),
                contentAlignment = Alignment.Center
            ) {
                if (item.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ChannelLogoBadge(logoUrl = "", title = item.title, size = 48.dp)
                }
                if (item.durationMs > 0L) {
                    val progress = (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ElectricCyan,
                        trackColor = ObsidianBg.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = item.subtitle.ifBlank { item.mediaType.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
