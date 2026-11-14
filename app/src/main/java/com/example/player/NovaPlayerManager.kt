package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.local.BufferPreset
import com.example.data.local.StreamItemEntity
import kotlinx.serialization.json.Json
import java.util.UUID

data class TrackChoice(
    val id: String,
    val label: String,
    val subLabel: String,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int,
    val trackType: Int
)

data class StreamTelemetry(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val bitrateKbps: Int = 0,
    val isBuffering: Boolean = false
)

@OptIn(UnstableApi::class)
class NovaPlayerManager(private val context: Context) {

    var primaryPlayer: ExoPlayer? = null
        private set

    val vlcController: VlcPlayerController = VlcPlayerController(context)
    val mpvController: MpvPlayerController = MpvPlayerController(context)

    private val multiScreenPlayers = arrayOfNulls<ExoPlayer>(4)

    fun buildOrGetPrimaryPlayer(
        bufferPreset: BufferPreset,
        httpHeaders: Map<String, String> = emptyMap(),
        drmScheme: String = "",
        drmLicenseKey: String = ""
    ): ExoPlayer {
        primaryPlayer?.let { return it }
        val created = createExoPlayer(
            bufferPreset = bufferPreset,
            httpHeaders = httpHeaders,
            drmScheme = drmScheme,
            drmLicenseKey = drmLicenseKey,
            capResolutionForMultiScreen = false
        )
        primaryPlayer = created
        return created
    }

    fun prepareStream(
        stream: StreamItemEntity,
        overrideUrl: String? = null,
        profileUserAgent: String,
        bufferPreset: BufferPreset,
        preferredAudioLanguage: String = "",
        preferredSubtitleLanguage: String = "",
        startPositionMs: Long = 0L,
        onError: (String) -> Unit = {},
        onTelemetryChanged: (StreamTelemetry) -> Unit = {},
        onTracksChanged: () -> Unit = {}
    ): ExoPlayer {
        val headers = parseHeaders(stream.httpHeadersJson, profileUserAgent)
        // Ensure built-in LibVLC and libmpv are stopped when preparing ExoPlayer
        vlcController.release()
        mpvController.stopStream()
        primaryPlayer?.release()
        val player = createExoPlayer(
            bufferPreset = bufferPreset,
            httpHeaders = headers,
            drmScheme = stream.drmScheme,
            drmLicenseKey = stream.drmLicenseKey,
            capResolutionForMultiScreen = false,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredSubtitleLanguage = preferredSubtitleLanguage
        )
        primaryPlayer = player

        val targetUrl = (overrideUrl ?: stream.streamUrl).trim()
        val mediaItem = buildMediaItem(targetUrl, stream.drmScheme, stream.drmLicenseKey)

        player.addListener(object : Player.Listener {
            private var telemetry = StreamTelemetry()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                telemetry = telemetry.copy(width = videoSize.width, height = videoSize.height)
                onTelemetryChanged(telemetry)
            }

            override fun onTracksChanged(tracks: Tracks) {
                var vCodec = ""
                var aCodec = ""
                var fps = 0f
                var bitrate = 0
                tracks.groups.forEach { group ->
                    if (group.isSelected) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val f = group.getTrackFormat(i)
                                if (group.type == C.TRACK_TYPE_VIDEO) {
                                    vCodec = f.codecs.orEmpty().ifBlank { f.sampleMimeType.orEmpty() }
                                    if (f.frameRate > 0f) fps = f.frameRate
                                    if (f.bitrate > 0) bitrate += f.bitrate / 1000
                                } else if (group.type == C.TRACK_TYPE_AUDIO) {
                                    aCodec = f.codecs.orEmpty().ifBlank { f.sampleMimeType.orEmpty() }
                                    if (f.bitrate > 0) bitrate += f.bitrate / 1000
                                }
                            }
                        }
                    }
                }
                telemetry = telemetry.copy(
                    videoCodec = vCodec,
                    audioCodec = aCodec,
                    fps = fps,
                    bitrateKbps = bitrate
                )
                onTelemetryChanged(telemetry)
                onTracksChanged()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                telemetry = telemetry.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                onTelemetryChanged(telemetry)
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(error.localizedMessage ?: "Stream playback error (${error.errorCodeName})")
            }
        })

        player.setMediaItem(mediaItem)
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.playWhenReady = true
        return player
    }

    fun getOrCreateMultiScreenPlayer(
        slotIndex: Int,
        stream: StreamItemEntity,
        profileUserAgent: String,
        isAudioFocused: Boolean
    ): ExoPlayer {
        multiScreenPlayers[slotIndex]?.release()
        val headers = parseHeaders(stream.httpHeadersJson, profileUserAgent)
        val player = createExoPlayer(
            bufferPreset = BufferPreset.FAST_ZAP,
            httpHeaders = headers,
            drmScheme = stream.drmScheme,
            drmLicenseKey = stream.drmLicenseKey,
            capResolutionForMultiScreen = true
        )
        player.volume = if (isAudioFocused) 1f else 0f
        player.setMediaItem(buildMediaItem(stream.streamUrl, stream.drmScheme, stream.drmLicenseKey))
        player.prepare()
        player.playWhenReady = true
        multiScreenPlayers[slotIndex] = player
        return player
    }

    fun setMultiScreenAudioFocus(focusedSlot: Int, muteAll: Boolean) {
        for (i in 0..3) {
            multiScreenPlayers[i]?.volume = if (!muteAll && i == focusedSlot) 1f else 0f
        }
    }

    fun releaseMultiScreenSlot(slotIndex: Int) {
        multiScreenPlayers[slotIndex]?.release()
        multiScreenPlayers[slotIndex] = null
    }

    fun releaseAllMultiScreen() {
        for (i in 0..3) {
            multiScreenPlayers[i]?.release()
            multiScreenPlayers[i] = null
        }
    }

    fun releaseExoPrimaryOnly() {
        primaryPlayer?.release()
        primaryPlayer = null
    }

    fun prepareForLibVlc() {
        releaseExoPrimaryOnly()
        mpvController.stopStream()
    }

    fun prepareForLibMpv() {
        releaseExoPrimaryOnly()
        vlcController.release()
    }

    fun releasePrimary() {
        primaryPlayer?.release()
        primaryPlayer = null
        vlcController.release()
        mpvController.release()
    }

    fun getTrackChoices(player: Player?, trackType: Int): List<TrackChoice> {
        if (player == null) return emptyList()
        val out = mutableListOf<TrackChoice>()
        val groups = player.currentTracks.groups
        groups.forEachIndexed { gIdx, group ->
            if (group.type == trackType) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val label = formatTrackLabel(trackType, format, tIdx)
                    val sub = formatTrackSubLabel(trackType, format)
                    out.add(
                        TrackChoice(
                            id = "${gIdx}_$tIdx",
                            label = label,
                            subLabel = sub,
                            isSelected = group.isTrackSelected(tIdx),
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            trackType = trackType
                        )
                    )
                }
            }
        }
        return out
    }

    fun selectTrack(player: Player?, choice: TrackChoice) {
        if (player == null) return
        val group: TrackGroup = player.currentTracks.groups.getOrNull(choice.groupIndex)?.mediaTrackGroup ?: return
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(choice.trackType, false)
            .setOverrideForType(TrackSelectionOverride(group, choice.trackIndex))
            .build()
        player.trackSelectionParameters = params
    }

    fun clearTrackOverride(player: Player?, trackType: Int, disableTrack: Boolean = false) {
        if (player == null) return
        val params = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, disableTrack)
            .build()
        player.trackSelectionParameters = params
    }

    private fun createExoPlayer(
        bufferPreset: BufferPreset,
        httpHeaders: Map<String, String>,
        drmScheme: String,
        drmLicenseKey: String,
        capResolutionForMultiScreen: Boolean,
        preferredAudioLanguage: String = "",
        preferredSubtitleLanguage: String = ""
    ): ExoPlayer {
        val userAgent = httpHeaders["User-Agent"] ?: "NovaStream/1.0 (Android)"
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .apply {
                if (httpHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(httpHeaders)
                }
            }

        val trackSelector = DefaultTrackSelector(context).apply {
            val builder = buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
            if (preferredAudioLanguage.isNotBlank()) {
                builder.setPreferredAudioLanguage(preferredAudioLanguage)
            }
            if (preferredSubtitleLanguage.equals("off", ignoreCase = true)) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else if (preferredSubtitleLanguage.isNotBlank()) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.setPreferredTextLanguage(preferredSubtitleLanguage)
            }
            if (capResolutionForMultiScreen) {
                builder.setMaxVideoSize(1280, 720)
            }
            setParameters(builder)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferPreset.minBufferMs,
                bufferPreset.maxBufferMs,
                bufferPreset.playBufferMs,
                bufferPreset.playBufferMs * 2
            )
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        if (drmScheme.equals("clearkey", ignoreCase = true) && ":" in drmLicenseKey && !drmLicenseKey.startsWith("http")) {
            val callback = buildInlineClearKeyCallback(drmLicenseKey)
            val drmManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(callback)
            mediaSourceFactory.setDrmSessionManagerProvider { drmManager }
        } else if (drmLicenseKey.startsWith("http")) {
            val uuid = if (drmScheme.equals("clearkey", true)) C.CLEARKEY_UUID else C.WIDEVINE_UUID
            val drmManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(HttpMediaDrmCallback(drmLicenseKey, dataSourceFactory))
            mediaSourceFactory.setDrmSessionManagerProvider { drmManager }
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }

    private fun buildMediaItem(url: String, drmScheme: String, drmLicenseKey: String): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        if (drmLicenseKey.startsWith("http")) {
            val uuid = if (drmScheme.equals("clearkey", true)) C.CLEARKEY_UUID else C.WIDEVINE_UUID
            builder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(uuid)
                    .setLicenseUri(drmLicenseKey)
                    .build()
            )
        }
        return builder.build()
    }

    private fun buildInlineClearKeyCallback(kidKeyPair: String): MediaDrmCallback {
        val kidHex = kidKeyPair.substringBefore(":").trim().replace("-", "")
        val keyHex = kidKeyPair.substringAfter(":").trim().replace("-", "")
        val kidB64 = hexToBase64Url(kidHex)
        val keyB64 = hexToBase64Url(keyHex)
        val jwkJson = """{"keys":[{"kty":"oct","k":"$keyB64","kid":"$kidB64"}],"type":"temporary"}"""
        val responseBytes = jwkJson.toByteArray(Charsets.UTF_8)

        return object : MediaDrmCallback {
            override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray = ByteArray(0)
            override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray = responseBytes
        }
    }

    private fun hexToBase64Url(hex: String): String {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        val bytes = ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun parseHeaders(rawJson: String, fallbackUserAgent: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        if (fallbackUserAgent.isNotBlank()) {
            map["User-Agent"] = fallbackUserAgent
        }
        if (rawJson.isNotBlank()) {
            runCatching {
                val parsed = Json.decodeFromString<Map<String, String>>(rawJson)
                map.putAll(parsed)
            }
        }
        return map
    }

    private fun formatTrackLabel(trackType: Int, format: Format, index: Int): String {
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> {
                val h = if (format.height > 0) "${format.height}p" else "Video #${index + 1}"
                val fps = if (format.frameRate > 0f) " ${format.frameRate.toInt()}fps" else ""
                "$h$fps"
            }
            C.TRACK_TYPE_AUDIO -> {
                val lang = format.language?.uppercase() ?: "Track ${index + 1}"
                val label = format.label?.takeIf { it.isNotBlank() } ?: lang
                val ch = if (format.channelCount > 2) "${format.channelCount}ch" else if (format.channelCount > 0) "Stereo" else ""
                listOf(label, ch).filter { it.isNotBlank() }.joinToString(" • ")
            }
            else -> {
                format.label?.takeIf { it.isNotBlank() }
                    ?: format.language?.uppercase()
                    ?: "Subtitle ${index + 1}"
            }
        }
    }

    private fun formatTrackSubLabel(trackType: Int, format: Format): String {
        val kbps = if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else ""
        val codec = format.codecs.orEmpty().ifBlank { format.sampleMimeType.orEmpty() }
        return listOf(codec, kbps).filter { it.isNotBlank() }.joinToString(" • ")
    }
}
