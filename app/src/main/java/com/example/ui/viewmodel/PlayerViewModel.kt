package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.EpgProgramEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.MpvHardwareMode
import com.example.data.local.PlaybackEngine
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import com.example.data.local.SubtitleSizePreset
import com.example.data.local.VlcHardwareMode
import com.example.data.local.WatchProgressEntity
import com.example.data.repository.NovaStreamRepository
import com.example.player.MpvPlayerController
import com.example.player.NovaPlayerManager
import com.example.player.StreamTelemetry
import com.example.player.TrackChoice
import com.example.player.VlcPlayerController
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

data class ExternalLaunchRequest(
    val id: Long = System.nanoTime(),
    val engine: PlaybackEngine,
    val url: String,
    val title: String,
    val userAgent: String,
    val httpHeadersJson: String,
    val positionMs: Long
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
    val activeEngine: PlaybackEngine = PlaybackEngine.EXOPLAYER,
    val vlcHardwareMode: VlcHardwareMode = VlcHardwareMode.AUTO,
    val mpvHardwareMode: MpvHardwareMode = MpvHardwareMode.AUTO_SAFE,
    val telemetry: StreamTelemetry = StreamTelemetry(),
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val subtitleSize: SubtitleSizePreset = SubtitleSizePreset.NORMAL,
    val osdTimeoutSec: Int = 5,
    val audioOnlyMode: Boolean = false,
    val showSignalHud: Boolean = false,
    val sleepTimerRemainingSec: Int? = null,
    val errorMessage: String? = null,
    val videoTracks: List<TrackChoice> = emptyList(),
    val audioTracks: List<TrackChoice> = emptyList(),
    val subtitleTracks: List<TrackChoice> = emptyList(),
    val zappingIndicator: String? = null,
    val engineSwitchNotice: String? = null,
    val showInfoBanner: Boolean = false,
    val externalLaunchRequest: ExternalLaunchRequest? = null
)

class PlayerViewModel(
    private val repository: NovaStreamRepository,
    val playerManager: NovaPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _activePlayer = MutableStateFlow<ExoPlayer?>(null)
    val activePlayer: StateFlow<ExoPlayer?> = _activePlayer.asStateFlow()

    val vlcController: VlcPlayerController
        get() = playerManager.vlcController

    val mpvController: MpvPlayerController
        get() = playerManager.mpvController

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
    private var engineNoticeJob: Job? = null
    private var currentProfileUserAgent = com.example.config.WhitelabelConfig.defaultUserAgent
    private var currentBufferPreset = BufferPreset.BALANCED
    private var currentAutoReconnect = true
    private var currentDefaultEngine = com.example.config.WhitelabelConfig.defaultPlaybackEngine
    private var currentVlcHardwareMode = VlcHardwareMode.AUTO
    private var currentMpvHardwareMode = MpvHardwareMode.AUTO_SAFE
    private var currentAutoFallbackEngine = true
    private var currentSubtitleSize = SubtitleSizePreset.NORMAL
    private var currentPreferredAudioLang = ""
    private var currentPreferredSubLang = ""
    private var currentOsdTimeoutSec = 5
    private val attemptedBuiltInEngines = mutableSetOf<PlaybackEngine>()

    init {
        viewModelScope.launch {
            repository.prefs.preferencesFlow.collect { prefs ->
                currentBufferPreset = prefs.bufferPreset
                currentAutoReconnect = prefs.autoReconnectLive
                currentDefaultEngine = prefs.defaultEngine
                currentVlcHardwareMode = prefs.vlcHardwareMode
                currentMpvHardwareMode = prefs.mpvHardwareMode
                currentAutoFallbackEngine = prefs.autoFallbackEngine
                currentSubtitleSize = prefs.subtitleSize
                currentPreferredAudioLang = prefs.preferredAudioLanguage
                currentPreferredSubLang = prefs.preferredSubtitleLanguage
                currentOsdTimeoutSec = prefs.osdTimeoutSec
                _uiState.update { state ->
                    if (state.mode == PlayerPresentationMode.HIDDEN) {
                        state.copy(
                            aspectRatioMode = prefs.defaultAspectRatio,
                            vlcHardwareMode = prefs.vlcHardwareMode,
                            mpvHardwareMode = prefs.mpvHardwareMode,
                            subtitleSize = prefs.subtitleSize,
                            osdTimeoutSec = prefs.osdTimeoutSec
                        )
                    } else {
                        state.copy(
                            vlcHardwareMode = prefs.vlcHardwareMode,
                            mpvHardwareMode = prefs.mpvHardwareMode,
                            subtitleSize = prefs.subtitleSize,
                            osdTimeoutSec = prefs.osdTimeoutSec
                        )
                    }
                }
            }
        }
    }

    fun consumeExternalLaunchRequest() {
        _uiState.update { it.copy(externalLaunchRequest = null) }
    }

    private fun pulseInfoBanner() {
        infoBannerJob?.cancel()
        _uiState.update { it.copy(showInfoBanner = true) }
        val durationMs = (currentOsdTimeoutSec.takeIf { it > 0 } ?: 5) * 1000L
        infoBannerJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { it.copy(showInfoBanner = false) }
        }
    }

    private fun pulseEngineNotice(message: String) {
        engineNoticeJob?.cancel()
        _uiState.update { it.copy(engineSwitchNotice = message) }
        engineNoticeJob = viewModelScope.launch {
            delay(3200)
            _uiState.update { it.copy(engineSwitchNotice = null) }
        }
    }

    private fun resolveEffectiveEngine(
        stream: StreamItemEntity,
        explicitEngine: PlaybackEngine?
    ): Pair<PlaybackEngine, String?> {
        val streamOverride = PlaybackEngine.fromStoredName(stream.preferredEngine)
            .takeIf { it != PlaybackEngine.DEFAULT }
        val candidate = explicitEngine?.takeIf { it != PlaybackEngine.DEFAULT }
            ?: streamOverride
            ?: currentDefaultEngine

        if ((candidate == PlaybackEngine.LIBVLC || candidate == PlaybackEngine.LIBMPV) && stream.drmLicenseKey.isNotBlank()) {
            return PlaybackEngine.EXOPLAYER to "DRM stream requires ExoPlayer"
        }
        return candidate to null
    }

    fun playLiveChannel(
        stream: StreamItemEntity,
        channelQueue: List<StreamItemEntity>,
        userAgent: String,
        openFullscreen: Boolean = true,
        explicitEngine: PlaybackEngine? = null
    ) {
        currentProfileUserAgent = userAgent
        viewModelScope.launch { repository.prefs.setLastLiveStreamId(stream.id) }
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
            mode = if (openFullscreen) PlayerPresentationMode.FULLSCREEN else PlayerPresentationMode.MINI_DOCKED,
            explicitEngine = explicitEngine,
            allowAutoFallback = true
        )
        pulseInfoBanner()
    }

    fun playCatchUpProgram(
        stream: StreamItemEntity,
        program: EpgProgramEntity,
        archiveUrl: String,
        userAgent: String,
        explicitEngine: PlaybackEngine? = null
    ) {
        currentProfileUserAgent = userAgent
        val dateLabel = com.example.ui.components.NovaFormatters.formatDayTabSubtitle(program.startEpochMs)
        val startClock = com.example.ui.components.NovaFormatters.formatClock(program.startEpochMs)
        val endClock = com.example.ui.components.NovaFormatters.formatClock(program.endEpochMs)
        launchPlayback(
            stream = stream,
            overrideUrl = archiveUrl,
            title = "${stream.title} • ${program.title}",
            subtitle = "CATCH-UP • $dateLabel • $startClock–$endClock",
            isLive = false,
            isCatchUp = true,
            startPositionMs = 0L,
            channelQueue = emptyList(),
            episodesQueue = emptyList(),
            episodeIndex = -1,
            mode = PlayerPresentationMode.FULLSCREEN,
            explicitEngine = explicitEngine,
            allowAutoFallback = true
        )
    }

    fun playMovie(
        movie: StreamItemEntity,
        userAgent: String,
        resumeMs: Long = 0L,
        explicitEngine: PlaybackEngine? = null
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
            mode = PlayerPresentationMode.FULLSCREEN,
            explicitEngine = explicitEngine,
            allowAutoFallback = true
        )
    }

    fun playSeriesEpisode(
        series: StreamItemEntity,
        episode: SeriesEpisodeEntity,
        allEpisodes: List<SeriesEpisodeEntity>,
        userAgent: String,
        resumeMs: Long = 0L,
        explicitEngine: PlaybackEngine? = null
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
            mode = PlayerPresentationMode.FULLSCREEN,
            explicitEngine = explicitEngine,
            allowAutoFallback = true
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
        mode: PlayerPresentationMode,
        explicitEngine: PlaybackEngine?,
        allowAutoFallback: Boolean
    ) {
        val (effectiveEngine, drmNotice) = resolveEffectiveEngine(stream, explicitEngine)
        if (drmNotice != null) {
            pulseEngineNotice(drmNotice)
        }

        // External player handoff (MPV App, VLC App, MX Player, Just Player, Kodi, System Chooser)
        if (!effectiveEngine.isBuiltIn) {
            val targetUrl = (overrideUrl ?: stream.streamUrl).trim()
            recordWatchHistory(stream, title, subtitle, startPositionMs, 0L)
            _activePlayer.value?.pause()
            playerManager.vlcController.pause()
            playerManager.mpvController.pause()
            _uiState.update {
                it.copy(
                    externalLaunchRequest = ExternalLaunchRequest(
                        engine = effectiveEngine,
                        url = targetUrl,
                        title = title,
                        userAgent = currentProfileUserAgent,
                        httpHeadersJson = stream.httpHeadersJson,
                        positionMs = startPositionMs
                    )
                )
            }
            return
        }

        if (allowAutoFallback) {
            attemptedBuiltInEngines.clear()
        }
        attemptedBuiltInEngines.add(effectiveEngine)

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
                activeEngine = effectiveEngine,
                vlcHardwareMode = currentVlcHardwareMode,
                mpvHardwareMode = currentMpvHardwareMode,
                errorMessage = null,
                audioOnlyMode = false,
                videoTracks = emptyList(),
                audioTracks = emptyList(),
                subtitleTracks = emptyList()
            )
        }

        val handleEngineError: (String) -> Unit = { err ->
            val fallbackOrder = listOf(
                PlaybackEngine.LIBMPV,
                PlaybackEngine.LIBVLC,
                PlaybackEngine.EXOPLAYER
            )
            val nextFallbackEngine = if (currentAutoFallbackEngine && stream.drmLicenseKey.isBlank()) {
                fallbackOrder.firstOrNull { it !in attemptedBuiltInEngines }
            } else {
                null
            }

            if (nextFallbackEngine != null) {
                val resumeAt = _uiState.value.positionMs.takeIf { !isLive && it > 1500L } ?: startPositionMs
                pulseEngineNotice("Auto-Switched to ${nextFallbackEngine.label}")
                launchPlayback(
                    stream = stream,
                    overrideUrl = overrideUrl,
                    title = title,
                    subtitle = subtitle,
                    isLive = isLive,
                    isCatchUp = isCatchUp,
                    startPositionMs = resumeAt,
                    channelQueue = channelQueue,
                    episodesQueue = episodesQueue,
                    episodeIndex = episodeIndex,
                    mode = _uiState.value.mode.takeIf { it != PlayerPresentationMode.HIDDEN } ?: mode,
                    explicitEngine = nextFallbackEngine,
                    allowAutoFallback = false
                )
            } else {
                _uiState.update { it.copy(errorMessage = err) }
                if (isLive && currentAutoReconnect) {
                    viewModelScope.launch {
                        delay(2500)
                        if (_uiState.value.activeStream?.id == stream.id && _uiState.value.errorMessage != null) {
                            retryActiveStream()
                        }
                    }
                }
            }
        }

        when (effectiveEngine) {
            PlaybackEngine.LIBVLC -> {
                playerManager.prepareForLibVlc()
                _activePlayer.value = null
                playerManager.vlcController.prepareStream(
                    stream = stream,
                    overrideUrl = overrideUrl,
                    profileUserAgent = currentProfileUserAgent,
                    bufferPreset = currentBufferPreset,
                    hwMode = currentVlcHardwareMode,
                    aspectRatioMode = _uiState.value.aspectRatioMode,
                    startPositionMs = startPositionMs,
                    onError = handleEngineError,
                    onTelemetryChanged = { telemetry ->
                        _uiState.update { it.copy(telemetry = telemetry) }
                    },
                    onTracksChanged = {
                        refreshTracks()
                    }
                )
            }
            PlaybackEngine.LIBMPV -> {
                playerManager.prepareForLibMpv()
                _activePlayer.value = null
                playerManager.mpvController.prepareStream(
                    stream = stream,
                    overrideUrl = overrideUrl,
                    profileUserAgent = currentProfileUserAgent,
                    bufferPreset = currentBufferPreset,
                    hwMode = currentMpvHardwareMode,
                    aspectRatioMode = _uiState.value.aspectRatioMode,
                    subtitleScale = currentSubtitleSize.scaleFactor,
                    preferredAudioLanguage = currentPreferredAudioLang,
                    preferredSubtitleLanguage = currentPreferredSubLang,
                    startPositionMs = startPositionMs,
                    onError = handleEngineError,
                    onTelemetryChanged = { telemetry ->
                        _uiState.update { it.copy(telemetry = telemetry) }
                    },
                    onTracksChanged = {
                        refreshTracks()
                    }
                )
            }
            else -> {
                val player = playerManager.prepareStream(
                    stream = stream,
                    overrideUrl = overrideUrl,
                    profileUserAgent = currentProfileUserAgent,
                    bufferPreset = currentBufferPreset,
                    preferredAudioLanguage = currentPreferredAudioLang,
                    preferredSubtitleLanguage = currentPreferredSubLang,
                    startPositionMs = startPositionMs,
                    onError = handleEngineError,
                    onTelemetryChanged = { telemetry ->
                        _uiState.update { it.copy(telemetry = telemetry) }
                    },
                    onTracksChanged = {
                        refreshTracks()
                    }
                )
                _activePlayer.value = player
            }
        }

        startHeartbeat()
        recordWatchHistory(stream, title, subtitle, startPositionMs, 0L)
    }

    fun switchEngine(targetEngine: PlaybackEngine, saveForStream: Boolean) {
        val state = _uiState.value
        val currentStream = state.activeStream ?: return

        val updatedStream = if (saveForStream) {
            val storedValue = if (targetEngine == PlaybackEngine.DEFAULT) "" else targetEngine.name
            viewModelScope.launch {
                repository.setStreamPreferredEngine(currentStream.id, targetEngine)
            }
            currentStream.copy(preferredEngine = storedValue)
        } else {
            currentStream
        }

        val updatedQueue = if (saveForStream && state.activePlaylistQueue.isNotEmpty()) {
            state.activePlaylistQueue.map { if (it.id == updatedStream.id) updatedStream else it }
        } else {
            state.activePlaylistQueue
        }

        val effectiveTarget = if (targetEngine == PlaybackEngine.DEFAULT) {
            currentDefaultEngine
        } else {
            targetEngine
        }

        val resumePos = state.positionMs.takeIf { !state.isLive && it > 1000L } ?: 0L

        if (!effectiveTarget.isBuiltIn) {
            _activePlayer.value?.pause()
            playerManager.vlcController.pause()
            playerManager.mpvController.pause()
            if (saveForStream) {
                _uiState.update { it.copy(activeStream = updatedStream, activePlaylistQueue = updatedQueue) }
            }
            val url = (state.overrideUrl ?: updatedStream.streamUrl).trim()
            _uiState.update {
                it.copy(
                    externalLaunchRequest = ExternalLaunchRequest(
                        engine = effectiveTarget,
                        url = url,
                        title = state.title,
                        userAgent = currentProfileUserAgent,
                        httpHeadersJson = updatedStream.httpHeadersJson,
                        positionMs = resumePos
                    )
                )
            }
            return
        }

        pulseEngineNotice("Engine: ${effectiveTarget.label}")
        launchPlayback(
            stream = updatedStream,
            overrideUrl = state.overrideUrl,
            title = state.title,
            subtitle = state.subtitle,
            isLive = state.isLive,
            isCatchUp = state.isCatchUp,
            startPositionMs = resumePos,
            channelQueue = updatedQueue,
            episodesQueue = state.activeEpisodesQueue,
            episodeIndex = state.activeEpisodeIndex,
            mode = state.mode,
            explicitEngine = effectiveTarget,
            allowAutoFallback = false
        )
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
            mode = state.mode,
            explicitEngine = state.activeEngine,
            allowAutoFallback = true
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
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> {
                playerManager.vlcController.togglePlayPause()
                _uiState.update { it.copy(isPlaying = playerManager.vlcController.isPlaying) }
            }
            PlaybackEngine.LIBMPV -> {
                playerManager.mpvController.togglePlayPause()
                _uiState.update { it.copy(isPlaying = playerManager.mpvController.isPlaying) }
            }
            else -> {
                val player = _activePlayer.value ?: return
                if (player.isPlaying) player.pause() else player.play()
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> playerManager.vlcController.seekBy(deltaMs)
            PlaybackEngine.LIBMPV -> playerManager.mpvController.seekBy(deltaMs)
            else -> {
                val player = _activePlayer.value ?: return
                val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
                player.seekTo(target)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> playerManager.vlcController.seekTo(positionMs)
            PlaybackEngine.LIBMPV -> playerManager.mpvController.seekTo(positionMs)
            else -> _activePlayer.value?.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.entries
        val next = modes[(modes.indexOf(_uiState.value.aspectRatioMode) + 1) % modes.size]
        _uiState.update { it.copy(aspectRatioMode = next) }
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> playerManager.vlcController.applyAspectRatio(next)
            PlaybackEngine.LIBMPV -> playerManager.mpvController.applyAspectRatio(next)
            else -> {}
        }
    }

    fun toggleAudioOnly() {
        val next = !_uiState.value.audioOnlyMode
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> playerManager.vlcController.setAudioOnly(next)
            PlaybackEngine.LIBMPV -> playerManager.mpvController.setAudioOnly(next)
            else -> {
                val player = _activePlayer.value ?: return
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, next)
                    .build()
            }
        }
        _uiState.update { it.copy(audioOnlyMode = next) }
    }

    fun toggleSignalHud() {
        _uiState.update { it.copy(showSignalHud = !it.showSignalHud) }
    }

    fun selectTrack(choice: TrackChoice) {
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> playerManager.vlcController.selectTrack(choice)
            PlaybackEngine.LIBMPV -> playerManager.mpvController.selectTrack(choice)
            else -> playerManager.selectTrack(_activePlayer.value, choice)
        }
        refreshTracks()
    }

    fun resetTrackAuto(trackType: Int, disable: Boolean = false) {
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> playerManager.vlcController.clearTrackOverride(trackType, disable)
            PlaybackEngine.LIBMPV -> playerManager.mpvController.clearTrackOverride(trackType, disable)
            else -> playerManager.clearTrackOverride(_activePlayer.value, trackType, disable)
        }
        refreshTracks()
    }

    fun refreshTracks() {
        when (_uiState.value.activeEngine) {
            PlaybackEngine.LIBVLC -> {
                _uiState.update {
                    it.copy(
                        videoTracks = playerManager.vlcController.getTrackChoices(C.TRACK_TYPE_VIDEO),
                        audioTracks = playerManager.vlcController.getTrackChoices(C.TRACK_TYPE_AUDIO),
                        subtitleTracks = playerManager.vlcController.getTrackChoices(C.TRACK_TYPE_TEXT)
                    )
                }
            }
            PlaybackEngine.LIBMPV -> {
                _uiState.update {
                    it.copy(
                        videoTracks = playerManager.mpvController.getTrackChoices(C.TRACK_TYPE_VIDEO),
                        audioTracks = playerManager.mpvController.getTrackChoices(C.TRACK_TYPE_AUDIO),
                        subtitleTracks = playerManager.mpvController.getTrackChoices(C.TRACK_TYPE_TEXT)
                    )
                }
            }
            else -> {
                val player = _activePlayer.value
                _uiState.update {
                    it.copy(
                        videoTracks = playerManager.getTrackChoices(player, C.TRACK_TYPE_VIDEO),
                        audioTracks = playerManager.getTrackChoices(player, C.TRACK_TYPE_AUDIO),
                        subtitleTracks = playerManager.getTrackChoices(player, C.TRACK_TYPE_TEXT)
                    )
                }
            }
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
            playerManager.vlcController.pause()
            playerManager.mpvController.pause()
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
        _uiState.update {
            PlayerUiState(
                aspectRatioMode = it.aspectRatioMode,
                vlcHardwareMode = it.vlcHardwareMode,
                mpvHardwareMode = it.mpvHardwareMode
            )
        }
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
                val state = _uiState.value
                when (state.activeEngine) {
                    PlaybackEngine.LIBVLC -> {
                        val vlc = playerManager.vlcController
                        val playing = vlc.isPlaying
                        val pos = vlc.currentPositionMs
                        val dur = vlc.durationMs
                        _uiState.update {
                            it.copy(
                                isPlaying = playing,
                                positionMs = pos,
                                durationMs = dur
                            )
                        }
                        if (tick % 5 == 0 && playing) {
                            state.activeStream?.let { s ->
                                recordWatchHistory(s, state.title, state.subtitle, pos, dur)
                            }
                        }
                    }
                    PlaybackEngine.LIBMPV -> {
                        val mpv = playerManager.mpvController
                        val playing = mpv.isPlaying
                        val pos = mpv.currentPositionMs
                        val dur = mpv.durationMs
                        val telemetry = mpv.refreshTelemetrySnapshot()
                        _uiState.update {
                            it.copy(
                                isPlaying = playing,
                                positionMs = pos,
                                durationMs = dur,
                                telemetry = telemetry
                            )
                        }
                        if (tick == 1 || tick % 4 == 0) {
                            refreshTracks()
                        }
                        if (tick % 5 == 0 && playing) {
                            state.activeStream?.let { s ->
                                recordWatchHistory(s, state.title, state.subtitle, pos, dur)
                            }
                        }
                    }
                    else -> {
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
                                state.activeStream?.let { s ->
                                    recordWatchHistory(s, state.title, state.subtitle, pos, dur)
                                }
                            }
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
