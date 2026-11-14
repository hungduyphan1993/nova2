package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class PlaylistSourceType {
    XTREAM,
    M3U_URL,
    M3U_FILE,
    DEMO
}

@Serializable
enum class MediaCategoryType {
    LIVE,
    MOVIE,
    SERIES
}

@Entity(tableName = "playlist_profiles")
@Serializable
data class PlaylistProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceType: PlaylistSourceType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUri: String = "",
    val epgUrl: String = "",
    val userAgent: String = "NovaStream/1.0 (Android)",
    val epgOffsetMinutes: Int = 0,
    val accountStatus: String = "Active",
    val expirationEpochSec: Long? = null,
    val maxConnections: String = "1",
    val activeConnections: String = "0",
    val lastSyncedAt: Long = 0L
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["profileId", "mediaType", "categoryKey"], unique = true)
    ]
)
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val mediaType: MediaCategoryType,
    val categoryKey: String,
    val name: String,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "stream_items",
    indices = [
        Index(value = ["profileId", "mediaType", "categoryKey"]),
        Index(value = ["profileId", "mediaType", "title"]),
        Index(value = ["profileId", "epgChannelId"])
    ]
)
@Serializable
data class StreamItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val mediaType: MediaCategoryType,
    val categoryKey: String,
    val remoteStreamId: String,
    val title: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val backdropUrl: String = "",
    val epgChannelId: String = "",
    val channelNumber: Int = 0,
    val rating: String = "",
    val releaseYear: String = "",
    val genre: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val containerExt: String = "",
    val catchupType: String = "",
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    val httpHeadersJson: String = "",
    val drmScheme: String = "",
    val drmLicenseKey: String = "",
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val preferredEngine: String = "",
    val addedAt: Long = 0L
)

@Entity(
    tableName = "series_episodes",
    indices = [
        Index(value = ["profileId", "seriesRemoteId", "seasonNumber", "episodeNumber"])
    ]
)
@Serializable
data class SeriesEpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val seriesRemoteId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: String,
    val title: String,
    val streamUrl: String,
    val thumbnailUrl: String = "",
    val plot: String = "",
    val durationSec: Int = 0,
    val containerExt: String = "mp4",
    val httpHeadersJson: String = ""
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["profileId", "epgChannelId", "startEpochMs", "endEpochMs"])
    ]
)
@Serializable
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val epgChannelId: String,
    val title: String,
    val description: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val hasReminder: Boolean = false
)

@Entity(
    tableName = "watch_progress",
    indices = [
        Index(value = ["profileId", "contentKey"], unique = true)
    ]
)
@Serializable
data class WatchProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val contentKey: String,
    val mediaType: MediaCategoryType,
    val streamDbId: Long,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val streamUrl: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedAt: Long = 0L
)

data class MediaTypeCount(
    val mediaType: MediaCategoryType,
    val total: Int
)
