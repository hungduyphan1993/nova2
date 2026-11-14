package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.PlaybackEngine
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.parser.CatchUpUrlBuilder
import com.example.data.repository.NovaStreamRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NowNextEpg(
    val now: EpgProgramEntity? = null,
    val next: EpgProgramEntity? = null
)

data class OmniSearchResult(
    val liveMatches: List<StreamItemEntity> = emptyList(),
    val movieMatches: List<StreamItemEntity> = emptyList(),
    val seriesMatches: List<StreamItemEntity> = emptyList(),
    val epgMatches: List<EpgProgramEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val repository: NovaStreamRepository
) : ViewModel() {

    private val activeProfileId: Flow<Long> = repository.prefs.preferencesFlow.map { it.activeProfileId }

    val liveCategories: StateFlow<List<CategoryEntity>> = activeProfileId
        .flatMapLatest { id -> if (id > 0) repository.observeCategories(id, MediaCategoryType.LIVE) else flowOf(emptyList()) }
        .map { list -> list.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieCategories: StateFlow<List<CategoryEntity>> = activeProfileId
        .flatMapLatest { id -> if (id > 0) repository.observeCategories(id, MediaCategoryType.MOVIE) else flowOf(emptyList()) }
        .map { list -> list.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesCategories: StateFlow<List<CategoryEntity>> = activeProfileId
        .flatMapLatest { id -> if (id > 0) repository.observeCategories(id, MediaCategoryType.SERIES) else flowOf(emptyList()) }
        .map { list -> list.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveStreams: StateFlow<List<StreamItemEntity>> = combine(
        activeProfileId.flatMapLatest { id ->
            if (id > 0) repository.observeStreams(id, MediaCategoryType.LIVE) else flowOf(emptyList())
        },
        liveCategories
    ) { streams, visibleCats ->
        val visibleKeys = visibleCats.map { it.categoryKey }.toSet()
        if (visibleKeys.isEmpty()) streams else streams.filter { it.categoryKey in visibleKeys }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieStreams: StateFlow<List<StreamItemEntity>> = combine(
        activeProfileId.flatMapLatest { id ->
            if (id > 0) repository.observeStreams(id, MediaCategoryType.MOVIE) else flowOf(emptyList())
        },
        movieCategories
    ) { streams, visibleCats ->
        val visibleKeys = visibleCats.map { it.categoryKey }.toSet()
        if (visibleKeys.isEmpty()) streams else streams.filter { it.categoryKey in visibleKeys }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesStreams: StateFlow<List<StreamItemEntity>> = combine(
        activeProfileId.flatMapLatest { id ->
            if (id > 0) repository.observeStreams(id, MediaCategoryType.SERIES) else flowOf(emptyList())
        },
        seriesCategories
    ) { streams, visibleCats ->
        val visibleKeys = visibleCats.map { it.categoryKey }.toSet()
        if (visibleKeys.isEmpty()) streams else streams.filter { it.categoryKey in visibleKeys }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<StreamItemEntity>> = activeProfileId
        .flatMapLatest { id -> if (id > 0) repository.observeFavorites(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchProgress: StateFlow<List<WatchProgressEntity>> = activeProfileId
        .flatMapLatest { id -> if (id > 0) repository.observeWatchProgress(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val epgWindowPrograms: StateFlow<List<EpgProgramEntity>> = activeProfileId
        .flatMapLatest { id ->
            if (id > 0) {
                val now = System.currentTimeMillis()
                repository.observeEpgWindow(id, now - 7L * 24L * 3600_000L, now + 36L * 3600_000L)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val epgByChannel: StateFlow<Map<String, List<EpgProgramEntity>>> = epgWindowPrograms
        .map { list -> list.groupBy { it.epgChannelId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val reminders: StateFlow<List<EpgProgramEntity>> = activeProfileId
        .flatMapLatest { id -> if (id > 0) repository.observeReminders(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResult = MutableStateFlow(OmniSearchResult())
    val searchResult: StateFlow<OmniSearchResult> = _searchResult.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun getNowNext(epgChannelId: String, nowMs: Long = System.currentTimeMillis()): NowNextEpg {
        if (epgChannelId.isBlank()) return NowNextEpg()
        val list = epgByChannel.value[epgChannelId].orEmpty()
        val nowProg = list.firstOrNull { nowMs in it.startEpochMs..it.endEpochMs }
        val nextProg = list.firstOrNull { it.startEpochMs > (nowProg?.endEpochMs ?: nowMs) }
        return NowNextEpg(now = nowProg, next = nextProg)
    }

    fun observeStreamById(streamDbId: Long): Flow<StreamItemEntity?> = repository.observeStream(streamDbId)

    fun observeEpisodes(profileId: Long, seriesRemoteId: String): Flow<List<SeriesEpisodeEntity>> =
        repository.observeEpisodes(profileId, seriesRemoteId)

    fun loadVodRichInfo(profileId: Long, stream: StreamItemEntity) {
        viewModelScope.launch {
            repository.loadMovieRichInfoIfNeeded(profileId, stream)
        }
    }

    private val _isSyncingCatchUp = MutableStateFlow(false)
    val isSyncingCatchUp: StateFlow<Boolean> = _isSyncingCatchUp.asStateFlow()

    fun loadSeriesEpisodes(profileId: Long, seriesItem: StreamItemEntity) {
        viewModelScope.launch {
            repository.ensureSeriesEpisodesLoaded(profileId, seriesItem)
        }
    }

    fun loadChannelCatchUpArchive(
        profileId: Long,
        stream: StreamItemEntity,
        forceRefresh: Boolean = false
    ) {
        if (profileId <= 0L) return
        viewModelScope.launch {
            _isSyncingCatchUp.value = true
            runCatching {
                repository.ensureChannelCatchUpLoaded(profileId, stream, forceRefresh)
            }
            _isSyncingCatchUp.value = false
        }
    }

    fun toggleFavorite(stream: StreamItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(stream.id, !stream.isFavorite)
        }
    }

    fun toggleStreamLock(stream: StreamItemEntity) {
        viewModelScope.launch {
            repository.setStreamLocked(stream.id, !stream.isLocked)
        }
    }

    fun setStreamPreferredEngine(stream: StreamItemEntity, engine: PlaybackEngine) {
        viewModelScope.launch {
            repository.setStreamPreferredEngine(stream.id, engine)
        }
    }

    fun toggleProgramReminder(program: EpgProgramEntity) {
        viewModelScope.launch {
            repository.toggleEpgReminder(program.id, !program.hasReminder)
        }
    }

    fun resolveCatchUpUrl(
        profile: PlaylistProfileEntity,
        stream: StreamItemEntity,
        program: EpgProgramEntity
    ): String = CatchUpUrlBuilder.buildArchiveUrl(profile, stream, program)

    fun runOmniSearch(profileId: Long, query: String, isSessionPinUnlocked: Boolean) {
        _searchQuery.value = query
        if (query.trim().length < 2 || profileId <= 0) {
            _searchResult.value = OmniSearchResult()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val hiddenOrLockedKeys = (liveCategories.value + movieCategories.value + seriesCategories.value)
                .filter { it.isHidden || (it.isLocked && !isSessionPinUnlocked) }
                .map { it.categoryKey }
                .toSet()

            val streams = repository.searchStreams(profileId, query.trim())
                .filter { it.categoryKey !in hiddenOrLockedKeys && (!it.isLocked || isSessionPinUnlocked) }
            val programs = repository.searchPrograms(profileId, query.trim())

            _searchResult.value = OmniSearchResult(
                liveMatches = streams.filter { it.mediaType == MediaCategoryType.LIVE },
                movieMatches = streams.filter { it.mediaType == MediaCategoryType.MOVIE },
                seriesMatches = streams.filter { it.mediaType == MediaCategoryType.SERIES },
                epgMatches = programs
            )
            _isSearching.value = false
        }
    }

    class Factory(
        private val repository: NovaStreamRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatalogViewModel(repository) as T
        }
    }
}
