package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistProfileDao {
    @Query("SELECT * FROM playlist_profiles ORDER BY id DESC")
    fun observeAll(): Flow<List<PlaylistProfileEntity>>

    @Query("SELECT * FROM playlist_profiles ORDER BY id DESC")
    suspend fun getAll(): List<PlaylistProfileEntity>

    @Query("SELECT * FROM playlist_profiles WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<PlaylistProfileEntity?>

    @Query("SELECT * FROM playlist_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlaylistProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PlaylistProfileEntity): Long

    @Update
    suspend fun update(profile: PlaylistProfileEntity)

    @Query("DELETE FROM playlist_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories 
        WHERE profileId = :profileId AND mediaType = :mediaType 
        ORDER BY isPinned DESC, sortOrder ASC, name ASC
        """
    )
    fun observeByType(profileId: Long, mediaType: MediaCategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE profileId = :profileId ORDER BY mediaType ASC, isPinned DESC, sortOrder ASC")
    fun observeAllForProfile(profileId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Long): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE categories SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE categories SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE categories SET isLocked = :locked WHERE id IN (:ids)")
    suspend fun setLockedForIds(ids: List<Long>, locked: Boolean)

    @Query("UPDATE categories SET isHidden = :hidden WHERE id IN (:ids)")
    suspend fun setHiddenForIds(ids: List<Long>, hidden: Boolean)

    @Query("DELETE FROM categories WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)
}

@Dao
interface StreamItemDao {
    @Query(
        """
        SELECT * FROM stream_items 
        WHERE profileId = :profileId AND mediaType = :mediaType 
        ORDER BY channelNumber ASC, id ASC
        """
    )
    fun observeByType(profileId: Long, mediaType: MediaCategoryType): Flow<List<StreamItemEntity>>

    @Query("SELECT * FROM stream_items WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<StreamItemEntity?>

    @Query("SELECT * FROM stream_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): StreamItemEntity?

    @Query(
        """
        SELECT * FROM stream_items 
        WHERE profileId = :profileId AND isFavorite = 1
        ORDER BY mediaType ASC, title ASC
        """
    )
    fun observeFavorites(profileId: Long): Flow<List<StreamItemEntity>>

    @Query("SELECT * FROM stream_items WHERE profileId = :profileId AND isFavorite = 1")
    suspend fun getFavorites(profileId: Long): List<StreamItemEntity>

    @Query("SELECT * FROM stream_items WHERE profileId = :profileId AND isLocked = 1")
    suspend fun getLockedItems(profileId: Long): List<StreamItemEntity>

    @Query("SELECT * FROM stream_items WHERE profileId = :profileId AND preferredEngine != ''")
    suspend fun getEngineOverrides(profileId: Long): List<StreamItemEntity>

    @Query(
        """
        SELECT mediaType, COUNT(*) as total FROM stream_items 
        WHERE profileId = :profileId 
        GROUP BY mediaType
        """
    )
    fun observeCountsByType(profileId: Long): Flow<List<MediaTypeCount>>

    @Query(
        """
        SELECT * FROM stream_items 
        WHERE profileId = :profileId AND title LIKE '%' || :query || '%'
        ORDER BY mediaType ASC, title ASC
        LIMIT 120
        """
    )
    suspend fun searchByTitle(profileId: Long, query: String): List<StreamItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StreamItemEntity>)

    @Query("UPDATE stream_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE stream_items SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE stream_items SET preferredEngine = :engine WHERE id = :id")
    suspend fun setPreferredEngine(id: Long, engine: String)

    @Query("UPDATE stream_items SET preferredEngine = :engine WHERE profileId = :profileId AND remoteStreamId = :remoteStreamId")
    suspend fun setPreferredEngineByRemoteId(profileId: Long, remoteStreamId: String, engine: String)

    @Query("UPDATE stream_items SET plot = :plot, cast = :cast, director = :director, genre = :genre, backdropUrl = :backdrop, rating = :rating WHERE id = :id")
    suspend fun updateVodMetadata(
        id: Long,
        plot: String,
        cast: String,
        director: String,
        genre: String,
        backdrop: String,
        rating: String
    )

    @Query("SELECT COUNT(*) FROM stream_items WHERE profileId = :profileId AND isFavorite = 1")
    fun observeFavoriteCount(profileId: Long): Flow<Int>

    @Query("UPDATE stream_items SET isFavorite = 0 WHERE profileId = :profileId")
    suspend fun clearFavoritesForProfile(profileId: Long)

    @Query("UPDATE stream_items SET preferredEngine = '' WHERE profileId = :profileId")
    suspend fun clearEngineOverridesForProfile(profileId: Long)

    @Query("DELETE FROM stream_items WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)
}

@Dao
interface SeriesEpisodeDao {
    @Query(
        """
        SELECT * FROM series_episodes 
        WHERE profileId = :profileId AND seriesRemoteId = :seriesRemoteId 
        ORDER BY seasonNumber ASC, episodeNumber ASC
        """
    )
    fun observeEpisodes(profileId: Long, seriesRemoteId: String): Flow<List<SeriesEpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<SeriesEpisodeEntity>)

    @Query("DELETE FROM series_episodes WHERE profileId = :profileId AND seriesRemoteId = :seriesRemoteId")
    suspend fun deleteForSeries(profileId: Long, seriesRemoteId: String)

    @Query("DELETE FROM series_episodes WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)
}

@Dao
interface EpgProgramDao {
    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE profileId = :profileId AND endEpochMs >= :windowStartMs AND startEpochMs <= :windowEndMs 
        ORDER BY startEpochMs ASC
        """
    )
    fun observeWindow(profileId: Long, windowStartMs: Long, windowEndMs: Long): Flow<List<EpgProgramEntity>>

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE profileId = :profileId AND epgChannelId = :channelId AND endEpochMs >= :fromEpochMs 
        ORDER BY startEpochMs ASC
        """
    )
    fun observeChannelSchedule(profileId: Long, channelId: String, fromEpochMs: Long): Flow<List<EpgProgramEntity>>

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE profileId = :profileId AND title LIKE '%' || :query || '%' AND endEpochMs >= :nowMs
        ORDER BY startEpochMs ASC
        LIMIT 80
        """
    )
    suspend fun searchPrograms(profileId: Long, query: String, nowMs: Long): List<EpgProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE profileId = :profileId AND hasReminder = 1 ORDER BY startEpochMs ASC")
    fun observeReminders(profileId: Long): Flow<List<EpgProgramEntity>>

    @Query("UPDATE epg_programs SET hasReminder = :hasReminder WHERE id = :id")
    suspend fun setReminder(id: Long, hasReminder: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query(
        """
        DELETE FROM epg_programs 
        WHERE profileId = :profileId AND epgChannelId = :channelId 
        AND startEpochMs >= :minStartMs AND endEpochMs <= :maxEndMs 
        AND hasReminder = 0
        """
    )
    suspend fun deleteChannelRangeWithoutReminders(
        profileId: Long,
        channelId: String,
        minStartMs: Long,
        maxEndMs: Long
    )

    @Query("SELECT COUNT(*) FROM epg_programs WHERE profileId = :profileId")
    fun observeProgramCount(profileId: Long): Flow<Int>

    @Query("DELETE FROM epg_programs WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)
}

@Dao
interface WatchProgressDao {
    @Query(
        """
        SELECT * FROM watch_progress 
        WHERE profileId = :profileId 
        ORDER BY lastWatchedAt DESC 
        LIMIT 40
        """
    )
    fun observeRecent(profileId: Long): Flow<List<WatchProgressEntity>>

    @Query("SELECT COUNT(*) FROM watch_progress WHERE profileId = :profileId")
    fun observeHistoryCount(profileId: Long): Flow<Int>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Long): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentKey = :contentKey LIMIT 1")
    suspend fun getByContentKey(profileId: Long, contentKey: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun deleteByKey(profileId: Long, contentKey: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)
}
