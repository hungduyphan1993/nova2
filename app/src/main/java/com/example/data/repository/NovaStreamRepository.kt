package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.config.WhitelabelConfig
import com.example.data.local.AdultCategoryClassifier
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.MediaTypeCount
import com.example.data.local.PlaybackEngine
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.PlaylistSourceType
import com.example.data.local.PreferencesRepository
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WatchProgressEntity
import com.example.data.network.PortalDiscoveryService
import com.example.data.network.XtreamApiService
import com.example.data.parser.M3uParser
import com.example.data.parser.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed class SyncStage {
    data object Idle : SyncStage()
    data class InProgress(val step: String, val detail: String = "") : SyncStage()
    data class Completed(val summary: String) : SyncStage()
    data class Error(val message: String) : SyncStage()
}

@Serializable
data class NovaBackupBundle(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val preferences: UserPreferences,
    val profiles: List<PlaylistProfileEntity>,
    val categories: List<CategoryEntity> = emptyList(),
    val favoriteStreamRemoteIds: List<String> = emptyList(),
    val lockedStreamRemoteIds: List<String> = emptyList(),
    val preferredEnginesByRemoteId: Map<String, String> = emptyMap(),
    val watchProgress: List<WatchProgressEntity> = emptyList()
)

class NovaStreamRepository(
    private val context: Context,
    private val db: AppDatabase,
    val prefs: PreferencesRepository,
    private val xtreamApi: XtreamApiService = XtreamApiService(),
    private val portalDiscovery: PortalDiscoveryService = PortalDiscoveryService(),
    private val httpClient: OkHttpClient = XtreamApiService.defaultClient()
) {
    private val profileDao = db.profileDao()
    private val categoryDao = db.categoryDao()
    private val streamDao = db.streamDao()
    private val episodeDao = db.seriesEpisodeDao()
    private val epgDao = db.epgDao()
    private val progressDao = db.watchProgressDao()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val allProfiles: Flow<List<PlaylistProfileEntity>> = profileDao.observeAll()

    fun observeProfile(profileId: Long): Flow<PlaylistProfileEntity?> = profileDao.observeById(profileId)

    fun observeCategories(profileId: Long, mediaType: MediaCategoryType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(profileId, mediaType)

    fun observeAllCategories(profileId: Long): Flow<List<CategoryEntity>> =
        categoryDao.observeAllForProfile(profileId)

    fun observeStreams(profileId: Long, mediaType: MediaCategoryType): Flow<List<StreamItemEntity>> =
        streamDao.observeByType(profileId, mediaType)

    fun observeStream(streamId: Long): Flow<StreamItemEntity?> = streamDao.observeById(streamId)

    fun observeFavorites(profileId: Long): Flow<List<StreamItemEntity>> = streamDao.observeFavorites(profileId)

    fun observeCounts(profileId: Long): Flow<Map<MediaCategoryType, Int>> =
        streamDao.observeCountsByType(profileId).map { list ->
            list.associate { it.mediaType to it.total }
        }

    fun observeEpisodes(profileId: Long, seriesRemoteId: String): Flow<List<SeriesEpisodeEntity>> =
        episodeDao.observeEpisodes(profileId, seriesRemoteId)

    fun observeEpgWindow(profileId: Long, startMs: Long, endMs: Long): Flow<List<EpgProgramEntity>> =
        epgDao.observeWindow(profileId, startMs, endMs)

    fun observeChannelEpg(profileId: Long, epgChannelId: String, fromMs: Long): Flow<List<EpgProgramEntity>> =
        epgDao.observeChannelSchedule(profileId, epgChannelId, fromMs)

    fun observeReminders(profileId: Long): Flow<List<EpgProgramEntity>> = epgDao.observeReminders(profileId)

    fun observeWatchProgress(profileId: Long): Flow<List<WatchProgressEntity>> = progressDao.observeRecent(profileId)

    fun observeFavoriteCount(profileId: Long): Flow<Int> = streamDao.observeFavoriteCount(profileId)

    fun observeHistoryCount(profileId: Long): Flow<Int> = progressDao.observeHistoryCount(profileId)

    fun observeEpgCount(profileId: Long): Flow<Int> = epgDao.observeProgramCount(profileId)

    suspend fun searchStreams(profileId: Long, query: String): List<StreamItemEntity> =
        withContext(Dispatchers.IO) { streamDao.searchByTitle(profileId, query) }

    private val syncedCatchUpChannelKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun searchPrograms(profileId: Long, query: String): List<EpgProgramEntity> =
        withContext(Dispatchers.IO) {
            epgDao.searchPrograms(profileId, query, System.currentTimeMillis() - 7L * 24L * 3600_000L)
        }

    suspend fun toggleFavorite(streamId: Long, favorite: Boolean) =
        withContext(Dispatchers.IO) { streamDao.setFavorite(streamId, favorite) }

    suspend fun setStreamLocked(streamId: Long, locked: Boolean) =
        withContext(Dispatchers.IO) { streamDao.setLocked(streamId, locked) }

    suspend fun setStreamPreferredEngine(streamId: Long, engine: PlaybackEngine) =
        withContext(Dispatchers.IO) {
            val stored = if (engine == PlaybackEngine.DEFAULT) "" else engine.name
            streamDao.setPreferredEngine(streamId, stored)
        }

    suspend fun setCategoryPinned(categoryId: Long, pinned: Boolean) =
        withContext(Dispatchers.IO) { categoryDao.setPinned(categoryId, pinned) }

    suspend fun setCategoryHidden(categoryId: Long, hidden: Boolean) =
        withContext(Dispatchers.IO) { categoryDao.setHidden(categoryId, hidden) }

    suspend fun setCategoryLocked(categoryId: Long, locked: Boolean) =
        withContext(Dispatchers.IO) { categoryDao.setLocked(categoryId, locked) }

    suspend fun setCategoriesLocked(ids: List<Long>, locked: Boolean) = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach { chunk -> categoryDao.setLockedForIds(chunk, locked) }
    }

    suspend fun setCategoriesHidden(ids: List<Long>, hidden: Boolean) = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach { chunk -> categoryDao.setHiddenForIds(chunk, hidden) }
    }

    suspend fun applyAdultAutoLockForProfile(profileId: Long): Int = withContext(Dispatchers.IO) {
        val cats = categoryDao.getAllForProfile(profileId)
        val unlockedAdultIds = cats
            .filter { AdultCategoryClassifier.isAdultCategoryName(it.name) && !it.isLocked }
            .map { it.id }
        if (unlockedAdultIds.isNotEmpty()) {
            setCategoriesLocked(unlockedAdultIds, true)
        }
        unlockedAdultIds.size
    }

    suspend fun toggleEpgReminder(programId: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) { epgDao.setReminder(programId, enabled) }

    suspend fun updateWatchProgress(progress: WatchProgressEntity) =
        withContext(Dispatchers.IO) { progressDao.upsert(progress) }

    suspend fun updateEpgOffset(profileId: Long, offsetMinutes: Int) = withContext(Dispatchers.IO) {
        val current = profileDao.getById(profileId) ?: return@withContext
        profileDao.update(current.copy(epgOffsetMinutes = offsetMinutes))
    }

    suspend fun updateProfileUserAgent(profileId: Long, userAgent: String) = withContext(Dispatchers.IO) {
        val current = profileDao.getById(profileId) ?: return@withContext
        val sanitized = userAgent.trim().ifBlank { WhitelabelConfig.defaultUserAgent }
        profileDao.update(current.copy(userAgent = sanitized))
    }

    suspend fun clearWatchHistory(profileId: Long) = withContext(Dispatchers.IO) {
        progressDao.deleteByProfile(profileId)
    }

    suspend fun clearFavorites(profileId: Long) = withContext(Dispatchers.IO) {
        streamDao.clearFavoritesForProfile(profileId)
    }

    suspend fun clearEpgCache(profileId: Long) = withContext(Dispatchers.IO) {
        epgDao.deleteByProfile(profileId)
        syncedCatchUpChannelKeys.clear()
    }

    suspend fun clearEngineOverrides(profileId: Long) = withContext(Dispatchers.IO) {
        streamDao.clearEngineOverridesForProfile(profileId)
    }

    suspend fun deleteProfile(profileId: Long) = withContext(Dispatchers.IO) {
        epgDao.deleteByProfile(profileId)
        episodeDao.deleteByProfile(profileId)
        streamDao.deleteByProfile(profileId)
        categoryDao.deleteByProfile(profileId)
        progressDao.deleteByProfile(profileId)
        profileDao.deleteById(profileId)
        val remaining = profileDao.getAll()
        prefs.setActiveProfileId(remaining.firstOrNull()?.id ?: -1L)
    }

    suspend fun connectXtreamProfile(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        onProgress: (SyncStage) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val effectiveUa = userAgent.trim().ifBlank { WhitelabelConfig.defaultUserAgent }
            val trimmedServer = serverUrl.trim()
            val rawServerCandidates = if (trimmedServer.isNotBlank()) {
                listOf(trimmedServer)
            } else if (WhitelabelConfig.hasApiServer) {
                onProgress(
                    SyncStage.InProgress(
                        "Resolving Portal Server",
                        "Fetching server URL from configured API server"
                    )
                )
                portalDiscovery.discoverServerUrls(
                    apiServerRaw = WhitelabelConfig.apiServerUrl,
                    username = username.trim(),
                    userAgent = effectiveUa
                )
            } else {
                throw IOException("Please enter an Xtream Server URL")
            }

            val resolvedAuth = xtreamApi.authenticateResolvingServer(
                rawServerInputs = rawServerCandidates,
                username = username.trim(),
                password = password.trim(),
                userAgent = effectiveUa
            ) { candidateUrl ->
                onProgress(SyncStage.InProgress("Authenticating Xtream Server", candidateUrl))
            }

            val normalizedServer = resolvedAuth.resolvedServerUrl
            val account = resolvedAuth.accountInfo

            val profileId = profileDao.insert(
                PlaylistProfileEntity(
                    name = name.ifBlank { "${WhitelabelConfig.launcherLabel} IPTV" },
                    sourceType = PlaylistSourceType.XTREAM,
                    serverUrl = normalizedServer,
                    username = username.trim(),
                    password = password.trim(),
                    epgUrl = xtreamApi.buildXmltvUrl(normalizedServer, username.trim(), password.trim()),
                    userAgent = effectiveUa,
                    accountStatus = account.status,
                    expirationEpochSec = account.expDateEpochSec,
                    maxConnections = account.maxConnections,
                    activeConnections = account.activeCons,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )

            syncXtreamCatalog(profileId, onProgress)
            prefs.setActiveProfileId(profileId)
            profileId
        }.onFailure { err ->
            onProgress(SyncStage.Error(err.message ?: "Failed to connect to Xtream server"))
        }
    }

    suspend fun syncXtreamCatalog(
        profileId: Long,
        onProgress: (SyncStage) -> Unit
    ) = withContext(Dispatchers.IO) {
        val profile = profileDao.getById(profileId) ?: throw IOException("Profile not found")
        val existingCats = categoryDao.getAllForProfile(profileId).associateBy { it.categoryKey }
        val favIds = streamDao.getFavorites(profileId).map { it.remoteStreamId }.toSet()
        val lockedIds = streamDao.getLockedItems(profileId).map { it.remoteStreamId }.toSet()
        val engineOverrides = streamDao.getEngineOverrides(profileId).associate { it.remoteStreamId to it.preferredEngine }

        onProgress(SyncStage.InProgress("Fetching Categories", "Live, VOD & Series folders"))
        val liveCats = xtreamApi.fetchCategories(profileId, profile.serverUrl, profile.username, profile.password, profile.userAgent, MediaCategoryType.LIVE)
        val vodCats = xtreamApi.fetchCategories(profileId, profile.serverUrl, profile.username, profile.password, profile.userAgent, MediaCategoryType.MOVIE)
        val seriesCats = xtreamApi.fetchCategories(profileId, profile.serverUrl, profile.username, profile.password, profile.userAgent, MediaCategoryType.SERIES)

        val autoLockAdult = prefs.preferencesFlow.firstOrNull()?.autoLockAdultCategories ?: true
        val mergedCats = (liveCats + vodCats + seriesCats).map { cat ->
            val old = existingCats[cat.categoryKey]
            val shouldAutoLock = autoLockAdult && AdultCategoryClassifier.isAdultCategoryName(cat.name)
            if (old != null) {
                cat.copy(
                    isPinned = old.isPinned,
                    isHidden = old.isHidden,
                    isLocked = old.isLocked || shouldAutoLock
                )
            } else {
                cat.copy(isLocked = shouldAutoLock)
            }
        }

        onProgress(SyncStage.InProgress("Importing Live Channels", "Downloading live stream index"))
        val liveStreams = xtreamApi.fetchLiveStreams(profileId, profile.serverUrl, profile.username, profile.password, profile.userAgent)

        onProgress(SyncStage.InProgress("Importing Movies (VOD)", "${liveStreams.size} live channels loaded"))
        val vodStreams = xtreamApi.fetchVodStreams(profileId, profile.serverUrl, profile.username, profile.password, profile.userAgent)

        onProgress(SyncStage.InProgress("Importing Series", "${vodStreams.size} movies loaded"))
        val seriesStreams = xtreamApi.fetchSeriesCatalog(profileId, profile.serverUrl, profile.username, profile.password, profile.userAgent)

        val allStreams = (liveStreams + vodStreams + seriesStreams).map { item ->
            item.copy(
                isFavorite = item.remoteStreamId in favIds,
                isLocked = item.remoteStreamId in lockedIds,
                preferredEngine = engineOverrides[item.remoteStreamId].orEmpty()
            )
        }

        categoryDao.deleteByProfile(profileId)
        streamDao.deleteByProfile(profileId)
        categoryDao.insertAll(mergedCats)
        allStreams.chunked(500).forEach { chunk -> streamDao.insertAll(chunk) }

        if (profile.epgUrl.isNotBlank()) {
            onProgress(SyncStage.InProgress("Syncing XMLTV EPG", "Downloading program guide"))
            runCatching {
                syncXmltvFromUrl(profileId, profile.epgUrl, profile.userAgent, profile.epgOffsetMinutes)
            }
        }

        profileDao.update(profile.copy(lastSyncedAt = System.currentTimeMillis()))
        onProgress(SyncStage.Completed("Synced ${liveStreams.size} channels, ${vodStreams.size} movies, ${seriesStreams.size} series"))
    }

    suspend fun connectM3uUrl(
        name: String,
        m3uUrl: String,
        epgUrl: String,
        userAgent: String,
        onProgress: (SyncStage) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val effectiveUa = userAgent.trim().ifBlank { WhitelabelConfig.defaultUserAgent }
            val resolvedM3uUrl = XtreamApiService.buildServerUrlCandidates(
                m3uUrl.trim(),
                preservePathAndQuery = true
            ).firstOrNull() ?: m3uUrl.trim()
            val resolvedEpgUrl = if (epgUrl.isNotBlank()) {
                XtreamApiService.buildServerUrlCandidates(
                    epgUrl.trim(),
                    preservePathAndQuery = true
                ).firstOrNull() ?: epgUrl.trim()
            } else {
                ""
            }
            onProgress(SyncStage.InProgress("Downloading M3U Playlist", resolvedM3uUrl))
            val profileId = profileDao.insert(
                PlaylistProfileEntity(
                    name = name.ifBlank { "M3U Playlist" },
                    sourceType = PlaylistSourceType.M3U_URL,
                    m3uUri = resolvedM3uUrl,
                    epgUrl = resolvedEpgUrl,
                    userAgent = effectiveUa,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            syncM3uProfile(profileId, onProgress)
            prefs.setActiveProfileId(profileId)
            profileId
        }.onFailure { err ->
            onProgress(SyncStage.Error(err.message ?: "Failed to load M3U playlist"))
        }
    }

    suspend fun connectM3uFile(
        name: String,
        fileUri: Uri,
        epgUrl: String,
        userAgent: String,
        onProgress: (SyncStage) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    fileUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onProgress(SyncStage.InProgress("Reading Local M3U File", fileUri.lastPathSegment.orEmpty()))
            val profileId = profileDao.insert(
                PlaylistProfileEntity(
                    name = name.ifBlank { "Local Playlist" },
                    sourceType = PlaylistSourceType.M3U_FILE,
                    m3uUri = fileUri.toString(),
                    epgUrl = epgUrl.trim(),
                    userAgent = userAgent.trim().ifBlank { WhitelabelConfig.defaultUserAgent },
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            syncM3uProfile(profileId, onProgress)
            prefs.setActiveProfileId(profileId)
            profileId
        }.onFailure { err ->
            onProgress(SyncStage.Error(err.message ?: "Failed to parse local M3U file"))
        }
    }

    suspend fun syncM3uProfile(
        profileId: Long,
        onProgress: (SyncStage) -> Unit
    ) = withContext(Dispatchers.IO) {
        val profile = profileDao.getById(profileId) ?: throw IOException("Profile not found")
        val existingCats = categoryDao.getAllForProfile(profileId).associateBy { it.categoryKey }
        val favIds = streamDao.getFavorites(profileId).map { it.remoteStreamId }.toSet()
        val lockedIds = streamDao.getLockedItems(profileId).map { it.remoteStreamId }.toSet()
        val engineOverrides = streamDao.getEngineOverrides(profileId).associate { it.remoteStreamId to it.preferredEngine }

        categoryDao.deleteByProfile(profileId)
        streamDao.deleteByProfile(profileId)
        episodeDao.deleteByProfile(profileId)

        var totalStreams = 0
        var resolvedM3uUri = profile.m3uUri
        val inputStream = when (profile.sourceType) {
            PlaylistSourceType.M3U_FILE -> {
                context.contentResolver.openInputStream(Uri.parse(profile.m3uUri))
                    ?: throw IOException("Cannot open selected M3U file")
            }
            else -> {
                val urlCandidates = listOf(profile.m3uUri).let { baseList ->
                    if (profile.m3uUri.startsWith("https://", ignoreCase = true)) {
                        listOf(profile.m3uUri, "http://" + profile.m3uUri.substring(8))
                    } else if (profile.m3uUri.startsWith("http://", ignoreCase = true)) {
                        listOf(profile.m3uUri, "https://" + profile.m3uUri.substring(7))
                    } else {
                        XtreamApiService.buildServerUrlCandidates(profile.m3uUri, preservePathAndQuery = true)
                    }
                }.distinct()

                var openedStream: java.io.InputStream? = null
                var lastErr: Throwable? = null
                for (candidateUrl in urlCandidates) {
                    try {
                        val req = Request.Builder()
                            .url(candidateUrl)
                            .header("User-Agent", profile.userAgent.ifBlank { WhitelabelConfig.defaultUserAgent })
                            .get()
                            .build()
                        val resp = httpClient.newCall(req).execute()
                        if (!resp.isSuccessful) {
                            val code = resp.code
                            resp.close()
                            throw IOException("HTTP $code downloading playlist")
                        }
                        val stream = resp.body?.byteStream() ?: throw IOException("Empty M3U response")
                        resolvedM3uUri = candidateUrl
                        openedStream = stream
                        break
                    } catch (e: Throwable) {
                        lastErr = e
                    }
                }
                openedStream ?: throw IOException(lastErr?.message ?: "Failed downloading M3U playlist", lastErr)
            }
        }

        val autoLockAdult = prefs.preferencesFlow.firstOrNull()?.autoLockAdultCategories ?: true
        val collectedCategories = linkedMapOf<String, CategoryEntity>()
        val detectedEpg = M3uParser.parseStream(profileId, inputStream) { cats, streams, episodes ->
            cats.forEach { cat ->
                val old = existingCats[cat.categoryKey]
                val shouldAutoLock = autoLockAdult && AdultCategoryClassifier.isAdultCategoryName(cat.name)
                collectedCategories[cat.categoryKey] = if (old != null) {
                    cat.copy(
                        isPinned = old.isPinned,
                        isHidden = old.isHidden,
                        isLocked = old.isLocked || shouldAutoLock
                    )
                } else {
                    cat.copy(isLocked = shouldAutoLock)
                }
            }
            if (streams.isNotEmpty()) {
                val merged = streams.map {
                    it.copy(
                        isFavorite = it.remoteStreamId in favIds,
                        isLocked = it.remoteStreamId in lockedIds,
                        preferredEngine = engineOverrides[it.remoteStreamId].orEmpty()
                    )
                }
                streamDao.insertAll(merged)
                totalStreams += merged.size
                onProgress(SyncStage.InProgress("Parsing Channels & VOD", "$totalStreams items imported"))
            }
            if (episodes.isNotEmpty()) {
                episodeDao.insertAll(episodes)
            }
        }

        if (collectedCategories.isNotEmpty()) {
            categoryDao.insertAll(collectedCategories.values.toList())
        }

        val effectiveEpgUrl = profile.epgUrl.ifBlank { detectedEpg.orEmpty() }
        val updatedProfile = profile.copy(
            m3uUri = resolvedM3uUri,
            epgUrl = effectiveEpgUrl,
            lastSyncedAt = System.currentTimeMillis()
        )
        profileDao.update(updatedProfile)

        if (effectiveEpgUrl.isNotBlank()) {
            onProgress(SyncStage.InProgress("Syncing XMLTV EPG", effectiveEpgUrl))
            runCatching {
                syncXmltvFromUrl(profileId, effectiveEpgUrl, profile.userAgent, profile.epgOffsetMinutes)
            }
        }

        onProgress(SyncStage.Completed("Imported $totalStreams streams across ${collectedCategories.size} groups"))
    }

    suspend fun syncXmltvFromUrl(
        profileId: Long,
        epgUrl: String,
        userAgent: String,
        offsetMinutes: Int
    ) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(epgUrl)
            .header("User-Agent", userAgent.ifBlank { WhitelabelConfig.defaultUserAgent })
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("EPG HTTP ${resp.code}")
            val bodyStream = resp.body?.byteStream() ?: throw IOException("Empty EPG stream")
            epgDao.deleteByProfile(profileId)
            XmltvParser.parseStream(profileId, bodyStream, offsetMinutes) { batch ->
                epgDao.insertAll(batch)
            }
        }
    }

    suspend fun refreshActiveProfile(profileId: Long, onProgress: (SyncStage) -> Unit) {
        val profile = profileDao.getById(profileId) ?: return
        when (profile.sourceType) {
            PlaylistSourceType.XTREAM -> {
                runCatching { syncXtreamCatalog(profileId, onProgress) }
                    .onFailure { onProgress(SyncStage.Error(it.message ?: "Refresh failed")) }
            }
            PlaylistSourceType.M3U_URL, PlaylistSourceType.M3U_FILE -> {
                runCatching { syncM3uProfile(profileId, onProgress) }
                    .onFailure { onProgress(SyncStage.Error(it.message ?: "Refresh failed")) }
            }
            PlaylistSourceType.DEMO -> {
                seedPublicDemoPlaylist(onProgress)
            }
        }
    }

    suspend fun loadMovieRichInfoIfNeeded(profileId: Long, stream: StreamItemEntity): StreamItemEntity =
        withContext(Dispatchers.IO) {
            if (stream.plot.isNotBlank()) return@withContext stream
            val profile = profileDao.getById(profileId) ?: return@withContext stream
            if (profile.sourceType != PlaylistSourceType.XTREAM) return@withContext stream
            val info = xtreamApi.fetchVodInfo(
                profile.serverUrl,
                profile.username,
                profile.password,
                profile.userAgent,
                stream.remoteStreamId
            ) ?: return@withContext stream
            streamDao.updateVodMetadata(
                id = stream.id,
                plot = info.plot,
                cast = info.cast,
                director = info.director,
                genre = info.genre,
                backdrop = info.backdropUrl.ifBlank { stream.backdropUrl },
                rating = info.rating.ifBlank { stream.rating }
            )
            streamDao.getById(stream.id) ?: stream
        }

    suspend fun ensureChannelCatchUpLoaded(
        profileId: Long,
        stream: StreamItemEntity,
        forceRefresh: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val profile = profileDao.getById(profileId) ?: return@withContext false
        val sessionKey = "${profileId}_${stream.id}"
        if (!forceRefresh && sessionKey in syncedCatchUpChannelKeys) return@withContext false

        if (profile.sourceType == PlaylistSourceType.DEMO) {
            syncedCatchUpChannelKeys.add(sessionKey)
            val now = System.currentTimeMillis()
            val existingOld = epgDao.searchPrograms(profileId, "", 0L)
                .any { it.startEpochMs < now - 6L * 3600_000L }
            if (!existingOld || forceRefresh) {
                seedDemoContentForProfile(profileId)
                return@withContext true
            }
            return@withContext false
        }

        if (profile.sourceType != PlaylistSourceType.XTREAM) return@withContext false

        val channelKey = stream.effectiveEpgKey
        val listings = xtreamApi.fetchCatchUpEpgTable(
            profileId = profileId,
            serverUrl = profile.serverUrl,
            username = profile.username,
            password = profile.password,
            userAgent = profile.userAgent,
            streamRemoteId = stream.remoteStreamId,
            targetChannelKey = channelKey,
            offsetMinutes = profile.epgOffsetMinutes
        )
        syncedCatchUpChannelKeys.add(sessionKey)
        if (listings.isNotEmpty()) {
            val minStart = listings.minOf { it.startEpochMs }
            val maxEnd = listings.maxOf { it.endEpochMs }
            epgDao.deleteChannelRangeWithoutReminders(profileId, channelKey, minStart, maxEnd)
            epgDao.insertAll(listings)
            true
        } else {
            false
        }
    }

    suspend fun ensureSeriesEpisodesLoaded(profileId: Long, seriesItem: StreamItemEntity) =
        withContext(Dispatchers.IO) {
            val profile = profileDao.getById(profileId) ?: return@withContext
            if (profile.sourceType != PlaylistSourceType.XTREAM) return@withContext
            val eps = xtreamApi.fetchSeriesEpisodes(
                profileId = profileId,
                serverUrl = profile.serverUrl,
                username = profile.username,
                password = profile.password,
                userAgent = profile.userAgent,
                seriesId = seriesItem.remoteStreamId
            )
            if (eps.isNotEmpty()) {
                episodeDao.deleteForSeries(profileId, seriesItem.remoteStreamId)
                episodeDao.insertAll(eps)
            }
        }

    suspend fun exportBackupToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val profiles = profileDao.getAll()
            val allCats = profiles.flatMap { categoryDao.getAllForProfile(it.id) }
            val favs = profiles.flatMap { streamDao.getFavorites(it.id) }.map { it.remoteStreamId }
            val locked = profiles.flatMap { streamDao.getLockedItems(it.id) }.map { it.remoteStreamId }
            val engineOverrides = profiles.flatMap { streamDao.getEngineOverrides(it.id) }
                .associate { it.remoteStreamId to it.preferredEngine }
            val progress = profiles.flatMap { progressDao.getAllForProfile(it.id) }
            val userPrefs = prefs.preferencesFlow.firstOrNull() ?: UserPreferences()
            val bundle = NovaBackupBundle(
                preferences = userPrefs,
                profiles = profiles,
                categories = allCats,
                favoriteStreamRemoteIds = favs,
                lockedStreamRemoteIds = locked,
                preferredEnginesByRemoteId = engineOverrides,
                watchProgress = progress
            )
            val payload = json.encodeToString(bundle)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Unable to write backup file")
            "Exported ${profiles.size} profile(s)"
        }
    }

    suspend fun importBackupFromUri(uri: Uri, onProgress: (SyncStage) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IOException("Unable to read backup file")
            val bundle = json.decodeFromString<NovaBackupBundle>(raw)
            var lastId = -1L
            bundle.profiles.forEach { profile ->
                val newId = profileDao.insert(profile.copy(id = 0))
                lastId = newId
                val matchingCats = bundle.categories.filter { it.profileId == profile.id }
                    .map { it.copy(id = 0, profileId = newId) }
                if (matchingCats.isNotEmpty()) {
                    categoryDao.insertAll(matchingCats)
                }
                if (profile.sourceType == PlaylistSourceType.DEMO) {
                    seedDemoContentForProfile(newId)
                } else {
                    runCatching { refreshActiveProfile(newId, onProgress) }
                }
                bundle.preferredEnginesByRemoteId.forEach { (remoteId, engName) ->
                    streamDao.setPreferredEngineByRemoteId(newId, remoteId, engName)
                }
            }
            if (lastId > 0) {
                prefs.setActiveProfileId(lastId)
            }
            prefs.setBufferPreset(bundle.preferences.bufferPreset)
            prefs.setDefaultAspectRatio(bundle.preferences.defaultAspectRatio)
            prefs.setAutoPip(bundle.preferences.autoPipOnBackground)
            prefs.setDefaultEngine(bundle.preferences.defaultEngine)
            prefs.setVlcHardwareMode(bundle.preferences.vlcHardwareMode)
            prefs.setMpvHardwareMode(bundle.preferences.mpvHardwareMode)
            prefs.setAutoFallbackEngine(bundle.preferences.autoFallbackEngine)
            prefs.setAutoReconnect(bundle.preferences.autoReconnectLive)
            prefs.setSubtitleSize(bundle.preferences.subtitleSize)
            prefs.setPreferredAudioLanguage(bundle.preferences.preferredAudioLanguage)
            prefs.setPreferredSubtitleLanguage(bundle.preferences.preferredSubtitleLanguage)
            prefs.setOsdTimeoutSec(bundle.preferences.osdTimeoutSec)
            prefs.setUse24HourClock(bundle.preferences.use24HourClock)
            prefs.setStartupBehavior(bundle.preferences.startupBehavior)
            prefs.setAutoLockAdultCategories(bundle.preferences.autoLockAdultCategories)
            "Restored ${bundle.profiles.size} profile(s)"
        }
    }

    /**
     * Seeds legal, open-source HLS & MP4 public test streams (Mux, Apple, Blender Open Movies)
     * plus a rich 12-hour EPG window around current local time so users can test Live Zapping,
     * 2D EPG, Catch-Up replay, Multi-Screen 4x, PiP, and VOD/Series immediately.
     */
    suspend fun seedPublicDemoPlaylist(onProgress: (SyncStage) -> Unit): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(SyncStage.InProgress("Preparing Public FTA Demo", "Loading open-source HLS & VOD test streams"))
            val existing = profileDao.getAll().firstOrNull { it.sourceType == PlaylistSourceType.DEMO }
            val profileId = if (existing != null) {
                existing.id
            } else {
                profileDao.insert(
                    PlaylistProfileEntity(
                        name = "Public FTA & Test Streams",
                        sourceType = PlaylistSourceType.DEMO,
                        serverUrl = "https://test-streams.mux.dev",
                        accountStatus = "Demo Ready",
                        maxConnections = "4",
                        activeConnections = "1",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            seedDemoContentForProfile(profileId)
            prefs.setActiveProfileId(profileId)
            onProgress(SyncStage.Completed("Loaded Public FTA Demo catalog & EPG"))
            profileId
        }
    }

    private suspend fun seedDemoContentForProfile(profileId: Long) {
        categoryDao.deleteByProfile(profileId)
        streamDao.deleteByProfile(profileId)
        episodeDao.deleteByProfile(profileId)
        epgDao.deleteByProfile(profileId)

        val now = System.currentTimeMillis()
        val categories = listOf(
            CategoryEntity(profileId = profileId, mediaType = MediaCategoryType.LIVE, categoryKey = "LIVE:NEWS", name = "World News & Live", isPinned = true, sortOrder = 0),
            CategoryEntity(profileId = profileId, mediaType = MediaCategoryType.LIVE, categoryKey = "LIVE:SPORTS", name = "Sports & Action", sortOrder = 1),
            CategoryEntity(profileId = profileId, mediaType = MediaCategoryType.LIVE, categoryKey = "LIVE:SCIENCE", name = "Science & Space", sortOrder = 2),
            CategoryEntity(profileId = profileId, mediaType = MediaCategoryType.LIVE, categoryKey = "LIVE:CINEMA", name = "24/7 Animation", sortOrder = 3),
            CategoryEntity(profileId = profileId, mediaType = MediaCategoryType.MOVIE, categoryKey = "MOVIE:OPEN", name = "Blender Open Cinema", sortOrder = 0),
            CategoryEntity(profileId = profileId, mediaType = MediaCategoryType.SERIES, categoryKey = "SERIES:OPEN", name = "Open Shorts Anthology", sortOrder = 0)
        )
        categoryDao.insertAll(categories)

        val liveStreams = listOf(
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.LIVE,
                categoryKey = "LIVE:NEWS",
                remoteStreamId = "demo_live_mux1",
                title = "Nova World 4K (Mux HLS)",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                epgChannelId = "nova.world",
                channelNumber = 101,
                catchupType = "default",
                catchupDays = 3,
                catchupSource = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                isFavorite = true,
                addedAt = now
            ),
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.LIVE,
                categoryKey = "LIVE:SPORTS",
                remoteStreamId = "demo_live_apple",
                title = "Arena Sports Multi-Audio",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                epgChannelId = "arena.sports",
                channelNumber = 102,
                catchupType = "default",
                catchupDays = 3,
                catchupSource = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                isFavorite = true,
                addedAt = now
            ),
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.LIVE,
                categoryKey = "LIVE:SCIENCE",
                remoteStreamId = "demo_live_tears",
                title = "SciFi Orbit HD (DASH/HLS)",
                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                epgChannelId = "scifi.orbit",
                channelNumber = 103,
                catchupType = "default",
                catchupDays = 2,
                catchupSource = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                addedAt = now
            ),
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.LIVE,
                categoryKey = "LIVE:CINEMA",
                remoteStreamId = "demo_live_sintel",
                title = "Cinema Reel Live",
                streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                epgChannelId = "cinema.reel",
                channelNumber = 104,
                catchupType = "default",
                catchupDays = 1,
                catchupSource = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                addedAt = now
            )
        )

        val movieStreams = listOf(
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.MOVIE,
                categoryKey = "MOVIE:OPEN",
                remoteStreamId = "demo_vod_bbb",
                title = "Big Buck Bunny",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                logoUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg?x11217",
                backdropUrl = "https://peach.blender.org/wp-content/uploads/bbb-splash.png",
                rating = "8.4",
                releaseYear = "2008",
                genre = "Animation • Comedy",
                plot = "A giant, kind-hearted rabbit confronts three mischievous rodents who disrupt his peaceful forest morning.",
                cast = "Blender Open Movie Studio",
                director = "Sacha Goedegebure",
                containerExt = "mp4",
                isFavorite = true,
                addedAt = now
            ),
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.MOVIE,
                categoryKey = "MOVIE:OPEN",
                remoteStreamId = "demo_vod_sintel",
                title = "Sintel",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                logoUrl = "https://durian.blender.org/wp-content/uploads/2010/06/05.8b_comp_000272.jpg",
                backdropUrl = "https://durian.blender.org/wp-content/uploads/2010/06/05.8b_comp_000272.jpg",
                rating = "8.8",
                releaseYear = "2010",
                genre = "Fantasy • Adventure",
                plot = "A lonely wanderer embarks on a perilous quest across frozen peaks to reunite with a young dragon she raised.",
                cast = "Halina Reijn, Thom Hoffman",
                director = "Colin Levy",
                containerExt = "mp4",
                addedAt = now
            ),
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.MOVIE,
                categoryKey = "MOVIE:OPEN",
                remoteStreamId = "demo_vod_tos",
                title = "Tears of Steel",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                logoUrl = "https://mango.blender.org/wp-content/uploads/2012/09/01_thom_celia_bridge.jpg",
                backdropUrl = "https://mango.blender.org/wp-content/uploads/2012/09/01_thom_celia_bridge.jpg",
                rating = "7.9",
                releaseYear = "2012",
                genre = "Sci-Fi • Action",
                plot = "In a dystopian future Amsterdam, a group of warriors and scientists gather at the Oude Kerk to reverse a catastrophic mistake.",
                cast = "Derek de Lint, Sergio Hasselbaink",
                director = "Ian Hubert",
                containerExt = "mp4",
                addedAt = now
            )
        )

        val seriesStreams = listOf(
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.SERIES,
                categoryKey = "SERIES:OPEN",
                remoteStreamId = "demo_series_blender",
                title = "Blender Studio Classics",
                streamUrl = "",
                logoUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg?x11217",
                backdropUrl = "https://durian.blender.org/wp-content/uploads/2010/06/05.8b_comp_000272.jpg",
                rating = "9.0",
                releaseYear = "2024",
                genre = "Anthology • Animation",
                plot = "Curated multi-season showcase of acclaimed open-source cinema shorts in crisp HD.",
                cast = "Blender Institute",
                director = "Various Directors",
                addedAt = now
            )
        )

        streamDao.insertAll(liveStreams + movieStreams + seriesStreams)

        val episodes = listOf(
            SeriesEpisodeEntity(
                profileId = profileId,
                seriesRemoteId = "demo_series_blender",
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = "ep_101",
                title = "Big Buck Bunny",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                plot = "Season premiere featuring the woodland slapstick classic.",
                durationSec = 596
            ),
            SeriesEpisodeEntity(
                profileId = profileId,
                seriesRemoteId = "demo_series_blender",
                seasonNumber = 1,
                episodeNumber = 2,
                episodeId = "ep_102",
                title = "Elephants Dream",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                plot = "Two travelers explore a surreal, endless mechanical universe.",
                durationSec = 653
            ),
            SeriesEpisodeEntity(
                profileId = profileId,
                seriesRemoteId = "demo_series_blender",
                seasonNumber = 2,
                episodeNumber = 1,
                episodeId = "ep_201",
                title = "Sintel",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                plot = "Season 2 opens with an epic journey for a lost companion.",
                durationSec = 888
            )
        )
        episodeDao.insertAll(episodes)

        // Generate realistic 60-min EPG archive blocks spanning -72h (3 full days of Catch-Up) to +12h ahead
        val hourFloor = (now / 3_600_000L) * 3_600_000L
        val epgSlots = mutableListOf<EpgProgramEntity>()
        val channelMeta = listOf(
            Triple(
                "nova.world",
                3,
                listOf(
                    "Global Morning Briefing" to "Overnight diplomatic dispatches, global weather radar, and international headlines.",
                    "Tech & Silicon Markets Live" to "Opening bell coverage, semiconductor supply analysis, and AI infrastructure reports.",
                    "Midday World Desk" to "Live correspondents reporting from London, Tokyo, Singapore, and New York.",
                    "Planet Earth Documentary Hour" to "Long-form investigative feature exploring ocean conservation and climate resilience.",
                    "Prime Time Geopolitics" to "In-depth roundtable debate with foreign policy analysts and economic historians.",
                    "Nightline Global Wrap" to "Late-evening summary of global markets, science breakthroughs, and cultural stories."
                )
            ),
            Triple(
                "arena.sports",
                3,
                listOf(
                    "Morning Warmup & Transfers" to "Daily training ground updates, injury reports, and club transfer rumors.",
                    "Tactical Breakdown Studio" to "Frame-by-frame telestrator analysis of recent championship fixtures.",
                    "Motorsport Paddock Live" to "Trackside telemetry, qualifying sector comparisons, and pit strategy.",
                    "Championship Matchday Live" to "Full high-bitrate multi-audio stadium broadcast with live commentary.",
                    "Post-Match Press & Highlights" to "Manager interviews, locker-room reactions, and extended match highlights.",
                    "Classic Finals Vault" to "Remastered full-match replay from historic tournament finals."
                )
            ),
            Triple(
                "scifi.orbit",
                2,
                listOf(
                    "Deep Space Horizons" to "James Webb infrared discoveries and deep-field galaxy spectroscopy.",
                    "Quantum Frontiers" to "Accessible breakdown of quantum error correction, fusion, and particle physics.",
                    "Exoplanet Atlas" to "Touring habitable-zone rocky worlds and atmospheric biosignature missions.",
                    "Orbital Station Telemetry" to "Live low-Earth orbit mission logs, EVA prep, and propulsion engineering.",
                    "Sci-Fi Short Film Showcase" to "Open-source VFX cinema featuring Tears of Steel and futuristic shorts."
                )
            ),
            Triple(
                "cinema.reel",
                1,
                listOf(
                    "Animation Vault Classics" to "Curated open-movie animation shorts mastered in crisp progressive HD.",
                    "Director's Storyboard Lab" to "Behind-the-scenes look at lighting, rigging, and sound design workflows.",
                    "Indie Festival Spotlight" to "Award-winning independent short films from international cinema festivals.",
                    "Midnight Fantasy Feature" to "Late-night screening of Sintel with full surround audio mix."
                )
            )
        )
        channelMeta.forEach { (chId, archiveDays, shows) ->
            val startSlot = -(archiveDays * 24)
            for (slotIdx in startSlot..12) {
                val start = hourFloor + slotIdx * 3_600_000L
                val end = start + 3_600_000L
                val (title, synopsis) = shows[((slotIdx % shows.size) + shows.size) % shows.size]
                epgSlots.add(
                    EpgProgramEntity(
                        profileId = profileId,
                        epgChannelId = chId,
                        title = title,
                        description = synopsis,
                        startEpochMs = start,
                        endEpochMs = end
                    )
                )
            }
        }
        epgDao.insertAll(epgSlots)
    }
}
