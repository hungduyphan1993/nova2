package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.EpgProgramEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.repository.NovaStreamRepository
import com.example.player.NovaPlayerManager
import com.example.player.StreamTelemetry
import com.example.player.TrackChoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerPresentationMode {
    HIDDEN,
    MINI_DOCKED,
    FULLSCREEN
}

enum class MultiScreenLayout(val label: String, val slotCount: Int) {
    SPLIT_2("2-Split", 2),
    FOCUS_3("1 + 2", 3),
    QUAD_4("2x2 Quad", 4)
}

data class MultiScreenSlot(
    val slotIndex: Int,
    val stream: StreamItemEntity? = null,
    val player: ExoPlayer? = null
)

data class PlayerUiState(
    val mode: PlayerPresentationMode = PlayerPresentationMode.HIDDEN,
    val activeStream: StreamItemEntity? = null,
    val activePlaylistQueue: List<StreamItemEntity> = emptyList(),
    val activeEpisodesQueue: List<SeriesEpisodeEntity> = emptyList(),
    val activeEpisodeIndex: Int = -1,
    val title: String = "",
    val subtitle: String = "",
    val overrideUrl: String? = null,
    val isLive: Boolean = true,
    val isCatchUp: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val telemetry: StreamTelemetry = StreamTelemetry(),
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val audioOnlyMode: Boolean = false,
    val showSignalHud: Boolean = false,
    val sleepTimerRemainingSec: Int? = null,
    val errorMessage: String? = null,
    val videoTracks: List<TrackChoice> = emptyList(),
    val audioTracks: List<TrackChoice> = emptyList(),
    val subtitleTracks: List<TrackChoice> = emptyList(),
    val zappingIndicator: String? = null,
    val showInfoBanner: Boolean = false
)

class PlayerViewModel(
    private val repository: NovaStreamRepository,
    val playerManager: NovaPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _activePlayer = MutableStateFlow<ExoPlayer?>(null)
    val activePlayer: StateFlow<ExoPlayer?> = _activePlayer.asStateFlow()

    private val _multiSlots = MutableStateFlow(List(4) { MultiScreenSlot(it) })
    val multiSlots: StateFlow<List<MultiScreenSlot>> = _multiSlots.asStateFlow()

    private val _multiLayout = MutableStateFlow(MultiScreenLayout.QUAD_4)
    val multiLayout: StateFlow<MultiScreenLayout> = _multiLayout.asStateFlow()

    private val _focusedAudioSlot = MutableStateFlow(0)
    val focusedAudioSlot: StateFlow<Int> = _focusedAudioSlot.asStateFlow()

    private val _muteAllMulti = MutableStateFlow(false)
    val muteAllMulti: StateFlow<Boolean> = _muteAllMulti.asStateFlow()

    private var heartbeatJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var infoBannerJob: Job? = null
    private var currentProfileUserAgent = "NovaStream/1.0 (Android)"
    private var currentBufferPreset = BufferPreset.BALANCED
    private var currentAutoReconnect = true

    init {
        viewModelScope.launch {
            repository.prefs.preferencesFlow.collect { prefs ->
                currentBufferPreset = prefs.bufferPreset
                currentAutoReconnect = prefs.autoReconnectLive
                if (_uiState.value.mode == PlayerPresentationMode.HIDDEN) {
                    _uiState.update { it.copy(aspectRatioMode = prefs.defaultAspectRatio) }
                }
            }
        }
    }

    fun playLiveChannel(
        stream: StreamItemEntity,
        channelQueue: List<StreamItemEntity>,
        userAgent: String,
        openFullscreen: Boolean = true
    ) {
        currentProfileUserAgent = userAgent
        launchPlayback(
            stream = stream,
            overrideUrl = null,
            title = stream.title,
            subtitle = "CH ${stream.channelNumber.toString().padStart(3, '0')} • LIVE",
            isLive = true,
            isCatchUp = false,
            startPositionMs = 0L,
            channelQueue = channelQueue,
            episodesQueue = emptyList(),
            episodeIndex = -1,
            mode = if (openFullscreen) PlayerPresentationMode.FULLSCREEN else PlayerPresentationMode.MINI_DOCKED
        )
        pulseInfoBanner()
    }

    private fun pulseInfoBanner() {
        infoBannerJob?.cancel()
        _uiState.update { it.copy(showInfoBanner = true) }
        infoBannerJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(showInfoBanner = false) }
        }
    }

    fun playCatchUpProgram(
        stream: StreamItemEntity,
        program: EpgProgramEntity,
        archiveUrl: String,
        userAgent: String
    ) {
        currentProfileUserAgent = userAgent
        launchPlayback(
            stream = stream,
            overrideUrl = archiveUrl,
            title = "${stream.title} • ${program.title}",
            subtitle = "CATCH-UP ARCHIVE",
            isLive = false,
            isCatchUp = true,
            startPositionMs = 0L,
            channelQueue = emptyList(),
            episodesQueue = emptyList(),
            episodeIndex = -1,
            mode = PlayerPresentationMode.FULLSCREEN
        )
    }

    fun playMovie(
        movie: StreamItemEntity,
        userAgent: String,
        resumeMs: Long = 0L
    ) {
        currentProfileUserAgent = userAgent
        launchPlayback(
            stream = movie,
            overrideUrl = null,
            title = movie.title,
            subtitle = listOf(movie.releaseYear, movie.genre).filter { it.isNotBlank() }.joinToString(" • "),
            isLive = false,
            isCatchUp = false,
            startPositionMs = resumeMs,
            channelQueue = emptyList(),
            episodesQueue = emptyList(),
            episodeIndex = -1,
            mode = PlayerPresentationMode.FULLSCREEN
        )
    }

    fun playSeriesEpisode(
        series: StreamItemEntity,
        episode: SeriesEpisodeEntity,
        allEpisodes: List<SeriesEpisodeEntity>,
        userAgent: String,
        resumeMs: Long = 0L
    ) {
        currentProfileUserAgent = userAgent
        val epIdx = allEpisodes.indexOfFirst { it.episodeId == episode.episodeId }
        val syntheticStream = series.copy(
            streamUrl = episode.streamUrl,
            containerExt = episode.containerExt,
            httpHeadersJson = episode.httpHeadersJson.ifBlank { series.httpHeadersJson }
        )
        launchPlayback(
            stream = syntheticStream,
            overrideUrl = episode.streamUrl,
            title = series.title,
            subtitle = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')} • ${episode.title}",
            isLive = false,
            isCatchUp = false,
            startPositionMs = resumeMs,
            channelQueue = emptyList(),
            episodesQueue = allEpisodes,
            episodeIndex = epIdx,
            mode = PlayerPresentationMode.FULLSCREEN
        )
    }

    private fun launchPlayback(
        stream: StreamItemEntity,
        overrideUrl: String?,
        title: String,
        subtitle: String,
        isLive: Boolean,
        isCatchUp: Boolean,
        startPositionMs: Long,
        channelQueue: List<StreamItemEntity>,
        episodesQueue: List<SeriesEpisodeEntity>,
        episodeIndex: Int,
        mode: PlayerPresentationMode
    ) {
        _uiState.update {
            it.copy(
                mode = mode,
                activeStream = stream,
                activePlaylistQueue = channelQueue,
                activeEpisodesQueue = episodesQueue,
                activeEpisodeIndex = episodeIndex,
                title = title,
                subtitle = subtitle,
                overrideUrl = overrideUrl,
                isLive = isLive,
                isCatchUp = isCatchUp,
                errorMessage = null,
                audioOnlyMode = false
            )
        }

        val player = playerManager.prepareStream(
            stream = stream,
            overrideUrl = overrideUrl,
            profileUserAgent = currentProfileUserAgent,
            bufferPreset = currentBufferPreset,
            startPositionMs = startPositionMs,
            onError = { err ->
                _uiState.update { it.copy(errorMessage = err) }
                if (isLive && currentAutoReconnect) {
                    viewModelScope.launch {
                        delay(2500)
                        if (_uiState.value.activeStream?.id == stream.id && _uiState.value.errorMessage != null) {
                            retryActiveStream()
                        }
                    }
                }
            },
            onTelemetryChanged = { telemetry ->
                _uiState.update { it.copy(telemetry = telemetry) }
            },
            onTracksChanged = {
                refreshTracks()
            }
        )
        _activePlayer.value = player
        startHeartbeat()
        recordWatchHistory(stream, title, subtitle, 0L, 0L)
    }

    fun retryActiveStream() {
        val state = _uiState.value
        val stream = state.activeStream ?: return
        launchPlayback(
            stream = stream,
            overrideUrl = state.overrideUrl,
            title = state.title,
            subtitle = state.subtitle,
            isLive = state.isLive,
            isCatchUp = state.isCatchUp,
            startPositionMs = state.positionMs.takeIf { !state.isLive && it > 2000L } ?: 0L,
            channelQueue = state.activePlaylistQueue,
            episodesQueue = state.activeEpisodesQueue,
            episodeIndex = state.activeEpisodeIndex,
            mode = state.mode
        )
    }

    fun zapChannel(offset: Int) {
        val state = _uiState.value
        val queue = state.activePlaylistQueue
        val current = state.activeStream ?: return
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx < 0) return
        val nextIdx = (idx + offset + queue.size) % queue.size
        val target = queue[nextIdx]
        _uiState.update { it.copy(zappingIndicator = "CH ${target.channelNumber.toString().padStart(3, '0')} • ${target.title}") }
        playLiveChannel(target, queue, currentProfileUserAgent, openFullscreen = state.mode == PlayerPresentationMode.FULLSCREEN)
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(zappingIndicator = null) }
        }
    }

    fun jumpToChannelNumber(channelNumber: Int): Boolean {
        val queue = _uiState.value.activePlaylistQueue
        val match = queue.firstOrNull { it.channelNumber == channelNumber } ?: return false
        playLiveChannel(match, queue, currentProfileUserAgent, openFullscreen = _uiState.value.mode == PlayerPresentationMode.FULLSCREEN)
        return true
    }

    fun playNextEpisode(): Boolean {
        val state = _uiState.value
        val nextIdx = state.activeEpisodeIndex + 1
        if (nextIdx !in state.activeEpisodesQueue.indices) return false
        val stream = state.activeStream ?: return false
        playSeriesEpisode(stream, state.activeEpisodesQueue[nextIdx], state.activeEpisodesQueue, currentProfileUserAgent)
        return true
    }

    fun togglePlayPause() {
        val player = _activePlayer.value ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val player = _activePlayer.value ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        _activePlayer.value?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.entries
        val next = modes[(modes.indexOf(_uiState.value.aspectRatioMode) + 1) % modes.size]
        _uiState.update { it.copy(aspectRatioMode = next) }
    }

    fun toggleAudioOnly() {
        val player = _activePlayer.value ?: return
        val next = !_uiState.value.audioOnlyMode
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, next)
            .build()
        _uiState.update { it.copy(audioOnlyMode = next) }
    }

    fun toggleSignalHud() {
        _uiState.update { it.copy(showSignalHud = !it.showSignalHud) }
    }

    fun selectTrack(choice: TrackChoice) {
        playerManager.selectTrack(_activePlayer.value, choice)
        refreshTracks()
    }

    fun resetTrackAuto(trackType: Int, disable: Boolean = false) {
        playerManager.clearTrackOverride(_activePlayer.value, trackType, disable)
        refreshTracks()
    }

    fun refreshTracks() {
        val player = _activePlayer.value
        _uiState.update {
            it.copy(
                videoTracks = playerManager.getTrackChoices(player, C.TRACK_TYPE_VIDEO),
                audioTracks = playerManager.getTrackChoices(player, C.TRACK_TYPE_AUDIO),
                subtitleTracks = playerManager.getTrackChoices(player, C.TRACK_TYPE_TEXT)
            )
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingSec = null) }
            return
        }
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
                delay(1000)
                remaining--
            }
            _uiState.update { it.copy(sleepTimerRemainingSec = null) }
            _activePlayer.value?.pause()
        }
    }

    fun dockMiniPlayer() {
        if (_uiState.value.activeStream != null) {
            _uiState.update { it.copy(mode = PlayerPresentationMode.MINI_DOCKED) }
        }
    }

    fun expandToFullscreen() {
        if (_uiState.value.activeStream != null) {
            _uiState.update { it.copy(mode = PlayerPresentationMode.FULLSCREEN) }
        }
    }

    fun closePrimaryPlayer() {
        heartbeatJob?.cancel()
        sleepTimerJob?.cancel()
        playerManager.releasePrimary()
        _activePlayer.value = null
        _uiState.update { PlayerUiState(aspectRatioMode = it.aspectRatioMode) }
    }

    // Multi-Screen Controls
    fun setMultiScreenLayout(layout: MultiScreenLayout) {
        _multiLayout.value = layout
    }

    fun assignMultiScreenSlot(slotIndex: Int, stream: StreamItemEntity, userAgent: String) {
        currentProfileUserAgent = userAgent
        val isFocused = !_muteAllMulti.value && _focusedAudioSlot.value == slotIndex
        val player = playerManager.getOrCreateMultiScreenPlayer(slotIndex, stream, userAgent, isFocused)
        _multiSlots.update { list ->
            list.map { slot ->
                if (slot.slotIndex == slotIndex) slot.copy(stream = stream, player = player) else slot
            }
        }
    }

    fun clearMultiScreenSlot(slotIndex: Int) {
        playerManager.releaseMultiScreenSlot(slotIndex)
        _multiSlots.update { list ->
            list.map { slot ->
                if (slot.slotIndex == slotIndex) slot.copy(stream = null, player = null) else slot
            }
        }
    }

    fun focusMultiScreenAudio(slotIndex: Int) {
        _focusedAudioSlot.value = slotIndex
        _muteAllMulti.value = false
        playerManager.setMultiScreenAudioFocus(slotIndex, false)
    }

    fun toggleMuteAllMultiScreen() {
        val next = !_muteAllMulti.value
        _muteAllMulti.value = next
        playerManager.setMultiScreenAudioFocus(_focusedAudioSlot.value, next)
    }

    fun expandMultiSlotToFullscreen(slotIndex: Int, allLiveChannels: List<StreamItemEntity>) {
        val stream = _multiSlots.value.getOrNull(slotIndex)?.stream ?: return
        releaseAllMultiScreen()
        playLiveChannel(stream, allLiveChannels, currentProfileUserAgent, openFullscreen = true)
    }

    fun releaseAllMultiScreen() {
        playerManager.releaseAllMultiScreen()
        _multiSlots.value = List(4) { MultiScreenSlot(it) }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val p = _activePlayer.value
                if (p != null) {
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                    _uiState.update {
                        it.copy(
                            isPlaying = p.isPlaying,
                            positionMs = pos,
                            durationMs = dur
                        )
                    }
                    if (tick % 5 == 0 && p.isPlaying) {
                        val s = _uiState.value.activeStream
                        if (s != null) {
                            recordWatchHistory(s, _uiState.value.title, _uiState.value.subtitle, pos, dur)
                        }
                    }
                }
                tick++
                delay(1000)
            }
        }
    }

    private fun recordWatchHistory(
        stream: StreamItemEntity,
        title: String,
        subtitle: String,
        posMs: Long,
        durMs: Long
    ) {
        viewModelScope.launch {
            val contentKey = if (stream.mediaType == MediaCategoryType.SERIES) {
                "${stream.remoteStreamId}_$subtitle"
            } else {
                "${stream.mediaType.name}_${stream.remoteStreamId}"
            }
            repository.updateWatchProgress(
                WatchProgressEntity(
                    profileId = stream.profileId,
                    contentKey = contentKey,
                    mediaType = stream.mediaType,
                    streamDbId = stream.id,
                    title = title,
                    subtitle = subtitle,
                    artworkUrl = stream.backdropUrl.ifBlank { stream.logoUrl },
                    streamUrl = stream.streamUrl,
                    positionMs = posMs,
                    durationMs = durMs,
                    lastWatchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.releasePrimary()
        playerManager.releaseAllMultiScreen()
    }

    class Factory(
        private val repository: NovaStreamRepository,
        private val playerManager: NovaPlayerManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(repository, playerManager) as T
        }
    }
}
