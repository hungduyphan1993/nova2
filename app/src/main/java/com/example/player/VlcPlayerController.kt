package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.StreamItemEntity
import com.example.data.local.VlcHardwareMode
import kotlinx.serialization.json.Json
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlayerController(private val context: Context) {

    private var libVlc: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
        private set

    private var attachedLayout: VLCVideoLayout? = null
    private var currentScaleMode: AspectRatioMode = AspectRatioMode.FIT
    private var savedVideoTrackId: Int = -1
    private var currentHwMode: VlcHardwareMode = VlcHardwareMode.AUTO
    private var telemetryState = StreamTelemetry(videoCodec = "LibVLC")

    val isPlaying: Boolean
        get() = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    val currentPositionMs: Long
        get() = runCatching { mediaPlayer?.time?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)

    val durationMs: Long
        get() = runCatching {
            val len = mediaPlayer?.length ?: 0L
            if (len > 0L) len else 0L
        }.getOrDefault(0L)

    fun prepareStream(
        stream: StreamItemEntity,
        overrideUrl: String?,
        profileUserAgent: String,
        bufferPreset: BufferPreset,
        hwMode: VlcHardwareMode,
        aspectRatioMode: AspectRatioMode,
        startPositionMs: Long = 0L,
        onError: (String) -> Unit = {},
        onTelemetryChanged: (StreamTelemetry) -> Unit = {},
        onTracksChanged: () -> Unit = {}
    ): Boolean {
        release()
        currentHwMode = hwMode
        currentScaleMode = aspectRatioMode

        return runCatching {
            val headers = parseHeaders(stream.httpHeadersJson, profileUserAgent)
            val userAgent = headers["User-Agent"] ?: "NovaStream/1.0 (Android)"
            val networkCacheMs = bufferPreset.playBufferMs.coerceIn(500, 8000)

            val options = arrayListOf(
                "--network-caching=$networkCacheMs",
                "--live-caching=$networkCacheMs",
                "--file-caching=$networkCacheMs",
                "--http-reconnect",
                "--rtsp-tcp",
                "--http-user-agent=$userAgent"
            )

            val vlc = LibVLC(context.applicationContext, options)
            libVlc = vlc

            val player = MediaPlayer(vlc)
            mediaPlayer = player

            attachedLayout?.let { layout ->
                player.attachViews(layout, null, true, false)
                applyAspectRatio(aspectRatioMode)
            }

            telemetryState = StreamTelemetry(
                videoCodec = "LibVLC (${hwMode.label})",
                audioCodec = "FFmpeg",
                isBuffering = true
            )
            onTelemetryChanged(telemetryState)

            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Buffering -> {
                        val buffering = event.buffering < 99.5f
                        if (telemetryState.isBuffering != buffering) {
                            telemetryState = telemetryState.copy(isBuffering = buffering)
                            onTelemetryChanged(telemetryState)
                        }
                    }
                    MediaPlayer.Event.Playing -> {
                        updateVideoTrackTelemetry(player)
                        telemetryState = telemetryState.copy(isBuffering = false)
                        onTelemetryChanged(telemetryState)
                        onTracksChanged()
                    }
                    MediaPlayer.Event.ESAdded,
                    MediaPlayer.Event.ESSelected,
                    MediaPlayer.Event.Vout -> {
                        updateVideoTrackTelemetry(player)
                        onTelemetryChanged(telemetryState)
                        onTracksChanged()
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        telemetryState = telemetryState.copy(isBuffering = false)
                        onTelemetryChanged(telemetryState)
                        onError("LibVLC could not open or decode this stream")
                    }
                }
            }

            val targetUrl = (overrideUrl ?: stream.streamUrl).trim()
            val media = Media(vlc, Uri.parse(targetUrl)).apply {
                when (hwMode) {
                    VlcHardwareMode.AUTO -> setHWDecoderEnabled(true, false)
                    VlcHardwareMode.HARDWARE -> setHWDecoderEnabled(true, true)
                    VlcHardwareMode.SOFTWARE -> setHWDecoderEnabled(false, false)
                }
                addOption(":network-caching=$networkCacheMs")
                addOption(":http-user-agent=$userAgent")
                headers["Referer"]?.takeIf { it.isNotBlank() }?.let { ref ->
                    addOption(":http-referrer=$ref")
                }
            }

            player.media = media
            media.release()
            player.play()
            if (startPositionMs > 0L) {
                player.time = startPositionMs
            }
            true
        }.getOrElse { err ->
            onError("LibVLC initialization failed: ${err.localizedMessage ?: "Unsupported ABI or stream"}")
            false
        }
    }

    private fun updateVideoTrackTelemetry(player: MediaPlayer) {
        runCatching {
            val vt = player.currentVideoTrack
            if (vt != null) {
                val fps = if (vt.frameRateDen > 0) vt.frameRateNum.toFloat() / vt.frameRateDen.toFloat() else 0f
                val vCodec = vt.codec?.takeIf { it.isNotBlank() } ?: "LibVLC (${currentHwMode.label})"
                val kbps = if (vt.bitrate > 0) vt.bitrate / 1000 else telemetryState.bitrateKbps
                telemetryState = telemetryState.copy(
                    width = vt.width,
                    height = vt.height,
                    fps = fps,
                    videoCodec = vCodec,
                    bitrateKbps = kbps
                )
            }
        }
    }

    fun attachSurface(layout: VLCVideoLayout) {
        if (attachedLayout === layout) return
        detachSurface()
        attachedLayout = layout
        runCatching {
            mediaPlayer?.attachViews(layout, null, true, false)
            applyAspectRatio(currentScaleMode)
        }
    }

    fun detachSurface(layout: VLCVideoLayout? = null) {
        if (layout != null && attachedLayout !== layout) return
        runCatching {
            mediaPlayer?.detachViews()
        }
        attachedLayout = null
    }

    fun applyAspectRatio(mode: AspectRatioMode) {
        currentScaleMode = mode
        val scale = when (mode) {
            AspectRatioMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectRatioMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            AspectRatioMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectRatioMode.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
            AspectRatioMode.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
        }
        runCatching { mediaPlayer?.videoScale = scale }
    }

    fun togglePlayPause() {
        val p = mediaPlayer ?: return
        runCatching {
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun pause() {
        runCatching { mediaPlayer?.pause() }
    }

    fun seekTo(positionMs: Long) {
        val p = mediaPlayer ?: return
        runCatching {
            if (p.isSeekable) {
                p.time = positionMs.coerceAtLeast(0L)
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        val p = mediaPlayer ?: return
        val dur = durationMs
        val next = if (dur > 0L) {
            (currentPositionMs + deltaMs).coerceIn(0L, dur)
        } else {
            (currentPositionMs + deltaMs).coerceAtLeast(0L)
        }
        seekTo(next)
    }

    fun setAudioOnly(enabled: Boolean) {
        val p = mediaPlayer ?: return
        runCatching {
            if (enabled) {
                val cur = p.videoTrack
                if (cur != -1) savedVideoTrackId = cur
                p.videoTrack = -1
            } else {
                val restoreId = if (savedVideoTrackId != -1) {
                    savedVideoTrackId
                } else {
                    p.videoTracks?.firstOrNull { it.id != -1 }?.id ?: -1
                }
                if (restoreId != -1) p.videoTrack = restoreId
            }
        }
    }

    fun getTrackChoices(trackType: Int): List<TrackChoice> {
        val p = mediaPlayer ?: return emptyList()
        return runCatching {
            when (trackType) {
                C.TRACK_TYPE_AUDIO -> {
                    val activeId = p.audioTrack
                    p.audioTracks.orEmpty()
                        .filter { it.id != -1 }
                        .mapIndexed { idx, desc ->
                            TrackChoice(
                                id = "vlc_a_${desc.id}",
                                label = desc.name?.takeIf { it.isNotBlank() } ?: "Audio Track ${idx + 1}",
                                subLabel = "LibVLC Audio #${desc.id}",
                                isSelected = desc.id == activeId,
                                groupIndex = 0,
                                trackIndex = desc.id,
                                trackType = C.TRACK_TYPE_AUDIO
                            )
                        }
                }
                C.TRACK_TYPE_TEXT -> {
                    val activeId = p.spuTrack
                    p.spuTracks.orEmpty()
                        .filter { it.id != -1 }
                        .mapIndexed { idx, desc ->
                            TrackChoice(
                                id = "vlc_s_${desc.id}",
                                label = desc.name?.takeIf { it.isNotBlank() } ?: "Subtitle ${idx + 1}",
                                subLabel = "LibVLC SPU #${desc.id}",
                                isSelected = desc.id == activeId,
                                groupIndex = 0,
                                trackIndex = desc.id,
                                trackType = C.TRACK_TYPE_TEXT
                            )
                        }
                }
                C.TRACK_TYPE_VIDEO -> {
                    val activeId = p.videoTrack
                    p.videoTracks.orEmpty()
                        .filter { it.id != -1 }
                        .mapIndexed { idx, desc ->
                            TrackChoice(
                                id = "vlc_v_${desc.id}",
                                label = desc.name?.takeIf { it.isNotBlank() } ?: "Video Track ${idx + 1}",
                                subLabel = "LibVLC Video #${desc.id}",
                                isSelected = desc.id == activeId,
                                groupIndex = 0,
                                trackIndex = desc.id,
                                trackType = C.TRACK_TYPE_VIDEO
                            )
                        }
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    fun selectTrack(choice: TrackChoice) {
        val p = mediaPlayer ?: return
        runCatching {
            when (choice.trackType) {
                C.TRACK_TYPE_AUDIO -> p.audioTrack = choice.trackIndex
                C.TRACK_TYPE_TEXT -> p.spuTrack = choice.trackIndex
                C.TRACK_TYPE_VIDEO -> p.videoTrack = choice.trackIndex
            }
        }
    }

    fun clearTrackOverride(trackType: Int, disableTrack: Boolean) {
        val p = mediaPlayer ?: return
        runCatching {
            when (trackType) {
                C.TRACK_TYPE_TEXT -> if (disableTrack) p.spuTrack = -1
                C.TRACK_TYPE_VIDEO -> if (disableTrack) p.videoTrack = -1
            }
        }
    }

    fun release() {
        runCatching {
            mediaPlayer?.let { mp ->
                mp.setEventListener(null)
                mp.stop()
                mp.detachViews()
                mp.release()
            }
        }
        mediaPlayer = null
        runCatching { libVlc?.release() }
        libVlc = null
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
}
