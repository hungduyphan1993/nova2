package com.example.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.MpvHardwareMode
import com.example.data.local.StreamItemEntity
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.serialization.json.Json

class MpvPlayerController(private val context: Context) {

    private var mpv: MPV? = null
    private var attachedSurface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var currentAspectRatio: AspectRatioMode = AspectRatioMode.FIT
    private var currentHwMode: MpvHardwareMode = MpvHardwareMode.AUTO_SAFE
    private var isAudioOnly: Boolean = false
    private var savedVideoTrackId: String = "auto"
    private var hasLoadedCurrentFile: Boolean = false
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onTracksChangedCallback: (() -> Unit)? = null
    private var onTelemetryChangedCallback: ((StreamTelemetry) -> Unit)? = null

    private var telemetryState = StreamTelemetry(videoCodec = "libmpv")

    private val mpvObserver = object : MPV.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {}
        override fun eventProperty(property: String, value: Boolean) {}
        override fun eventProperty(property: String, value: String) {}
        override fun eventProperty(property: String, value: Double) {}
        override fun eventProperty(property: String, value: MPVNode) {}

        override fun event(eventId: Int, data: MPVNode) {
            when (eventId) {
                MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                    hasLoadedCurrentFile = true
                    refreshTelemetrySnapshot()
                    onTracksChangedCallback?.invoke()
                }
                MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG,
                MPV.mpvEvent.MPV_EVENT_AUDIO_RECONFIG -> {
                    refreshTelemetrySnapshot()
                    onTracksChangedCallback?.invoke()
                }
                MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                    val errCode = runCatching { data["error"]?.asInt() ?: 0L }.getOrDefault(0L)
                    if (errCode < 0L || !hasLoadedCurrentFile) {
                        telemetryState = telemetryState.copy(isBuffering = false)
                        onTelemetryChangedCallback?.invoke(telemetryState)
                        onErrorCallback?.invoke("libmpv could not open or decode this stream")
                    }
                }
            }
        }
    }

    val isPlaying: Boolean
        get() {
            val engine = mpv ?: return false
            if (!engine.isInitialized) return false
            return runCatching {
                val paused = engine.getPropertyBoolean("pause") ?: true
                !paused
            }.getOrDefault(false)
        }

    val currentPositionMs: Long
        get() {
            val engine = mpv ?: return 0L
            if (!engine.isInitialized) return 0L
            return runCatching {
                val sec = engine.getPropertyDouble("time-pos") ?: 0.0
                (sec * 1000.0).toLong().coerceAtLeast(0L)
            }.getOrDefault(0L)
        }

    val durationMs: Long
        get() {
            val engine = mpv ?: return 0L
            if (!engine.isInitialized) return 0L
            return runCatching {
                val sec = engine.getPropertyDouble("duration") ?: 0.0
                if (sec > 0.0) (sec * 1000.0).toLong() else 0L
            }.getOrDefault(0L)
        }

    fun prepareStream(
        stream: StreamItemEntity,
        overrideUrl: String?,
        profileUserAgent: String,
        bufferPreset: BufferPreset,
        hwMode: MpvHardwareMode,
        aspectRatioMode: AspectRatioMode,
        subtitleScale: Float = 1.0f,
        preferredAudioLanguage: String = "",
        preferredSubtitleLanguage: String = "",
        startPositionMs: Long = 0L,
        onError: (String) -> Unit = {},
        onTelemetryChanged: (StreamTelemetry) -> Unit = {},
        onTracksChanged: () -> Unit = {}
    ): Boolean {
        currentHwMode = hwMode
        currentAspectRatio = aspectRatioMode
        isAudioOnly = false
        hasLoadedCurrentFile = false
        onErrorCallback = onError
        onTelemetryChangedCallback = onTelemetryChanged
        onTracksChangedCallback = onTracksChanged

        return runCatching {
            val headers = parseHeaders(stream.httpHeadersJson, profileUserAgent)
            val userAgent = headers["User-Agent"] ?: com.example.config.WhitelabelConfig.defaultUserAgent
            val headerFields = headers
                .filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                .entries
                .joinToString(",") { (k, v) -> "$k: ${v.replace(",", "\\,")}" }

            val maxCacheBytes = when (bufferPreset) {
                BufferPreset.FAST_ZAP -> "16MiB"
                BufferPreset.BALANCED -> "48MiB"
                BufferPreset.HIGH_STABILITY -> "96MiB"
            }
            val cacheSecs = (bufferPreset.maxBufferMs / 1000.0).coerceAtLeast(5.0)

            val engine = ensureInitialized(hwMode, userAgent, maxCacheBytes, cacheSecs)

            // Update runtime stream options for the new item
            engine.setPropertyString("hwdec", hwMode.mpvValue)
            engine.setPropertyString("user-agent", userAgent)
            engine.setPropertyString("http-header-fields", headerFields)
            engine.setPropertyString("demuxer-max-bytes", maxCacheBytes)
            engine.setPropertyDouble("cache-secs", cacheSecs)
            engine.setPropertyDouble("sub-scale", subtitleScale.toDouble())
            engine.setPropertyString("alang", preferredAudioLanguage)
            when {
                preferredSubtitleLanguage.equals("off", ignoreCase = true) -> {
                    engine.setPropertyString("slang", "")
                    engine.setPropertyString("sid", "no")
                }
                preferredSubtitleLanguage.isNotBlank() -> {
                    engine.setPropertyString("slang", preferredSubtitleLanguage)
                    engine.setPropertyString("sid", "auto")
                }
                else -> {
                    engine.setPropertyString("slang", "")
                    engine.setPropertyString("sid", "auto")
                }
            }

            // Re-bind surface if already mounted in Compose
            attachedSurface?.takeIf { it.isValid }?.let { surface ->
                engine.attachSurface(surface)
                if (surfaceWidth > 0 && surfaceHeight > 0) {
                    engine.setPropertyString("android-surface-size", "${surfaceWidth}x${surfaceHeight}")
                }
                engine.setPropertyString("vo", "gpu")
                engine.setPropertyString("vid", "auto")
            }

            applyAspectRatio(aspectRatioMode)

            telemetryState = StreamTelemetry(
                videoCodec = "libmpv (${hwMode.label})",
                audioCodec = "FFmpeg",
                isBuffering = true
            )
            onTelemetryChanged(telemetryState)

            if (startPositionMs > 0L) {
                engine.setOptionString("start", "+${startPositionMs / 1000.0}")
            } else {
                engine.setOptionString("start", "none")
            }

            val targetUrl = (overrideUrl ?: stream.streamUrl).trim()
            engine.command("loadfile", targetUrl, "replace")
            engine.setPropertyBoolean("pause", false)
            true
        }.getOrElse { err ->
            onError("libmpv initialization failed: ${err.localizedMessage ?: "Native libmpv unavailable"}")
            false
        }
    }

    private fun ensureInitialized(
        hwMode: MpvHardwareMode,
        userAgent: String,
        maxCacheBytes: String,
        cacheSecs: Double
    ): MPV {
        val existing = mpv
        if (existing != null && existing.isInitialized) return existing

        val appCtx = context.applicationContext
        val engine = MPV()
        engine.create(appCtx)
        engine.setOptionString("config", "yes")
        engine.setOptionString("config-dir", appCtx.filesDir.path)
        engine.setOptionString("gpu-shader-cache-dir", appCtx.cacheDir.path)
        engine.setOptionString("icc-cache-dir", appCtx.cacheDir.path)
        engine.setOptionString("profile", "fast")
        engine.setOptionString("vo", "gpu")
        engine.setOptionString("gpu-context", "android")
        engine.setOptionString("opengl-es", "yes")
        engine.setOptionString("hwdec", hwMode.mpvValue)
        engine.setOptionString("hwdec-codecs", "all")
        engine.setOptionString("ao", "audiotrack,opensles")
        engine.setOptionString("tls-verify", "no")
        engine.setOptionString("network-timeout", "20")
        engine.setOptionString("cache", "yes")
        engine.setOptionString("demuxer-max-bytes", maxCacheBytes)
        engine.setOptionString("demuxer-max-back-bytes", "16MiB")
        engine.setOptionString("cache-secs", cacheSecs.toString())
        engine.setOptionString("user-agent", userAgent)
        engine.setOptionString("force-window", "yes")
        engine.setOptionString("keep-open", "yes")
        engine.setOptionString("sub-auto", "fuzzy")
        engine.setOptionString("ytdl", "no")
        engine.init()
        engine.addObserver(mpvObserver)
        mpv = engine
        return engine
    }

    fun attachSurface(surface: Surface, width: Int = 0, height: Int = 0) {
        attachedSurface = surface
        if (width > 0 && height > 0) {
            surfaceWidth = width
            surfaceHeight = height
        }
        val engine = mpv ?: return
        if (!engine.isInitialized || !surface.isValid) return
        runCatching {
            engine.attachSurface(surface)
            if (surfaceWidth > 0 && surfaceHeight > 0) {
                engine.setPropertyString("android-surface-size", "${surfaceWidth}x${surfaceHeight}")
            }
            engine.setPropertyString("vo", "gpu")
            if (!isAudioOnly) {
                engine.setPropertyString("vid", savedVideoTrackId.ifBlank { "auto" })
            }
            applyAspectRatio(currentAspectRatio)
        }
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        surfaceWidth = width
        surfaceHeight = height
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            engine.setPropertyString("android-surface-size", "${width}x${height}")
        }
    }

    fun detachSurface(surface: Surface? = null) {
        if (surface != null && attachedSurface !== surface) return
        val engine = mpv
        if (engine != null && engine.isInitialized) {
            runCatching {
                engine.setPropertyString("vo", "null")
                engine.detachSurface()
            }
        }
        attachedSurface = null
    }

    fun applyAspectRatio(mode: AspectRatioMode) {
        currentAspectRatio = mode
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            when (mode) {
                AspectRatioMode.FIT -> {
                    engine.setPropertyString("keepaspect", "yes")
                    engine.setPropertyDouble("panscan", 0.0)
                    engine.setPropertyString("video-aspect-override", "-1")
                }
                AspectRatioMode.FILL -> {
                    engine.setPropertyString("keepaspect", "no")
                    engine.setPropertyDouble("panscan", 0.0)
                    engine.setPropertyString("video-aspect-override", "-1")
                }
                AspectRatioMode.ZOOM -> {
                    engine.setPropertyString("keepaspect", "yes")
                    engine.setPropertyDouble("panscan", 1.0)
                    engine.setPropertyString("video-aspect-override", "-1")
                }
                AspectRatioMode.RATIO_16_9 -> {
                    engine.setPropertyString("keepaspect", "yes")
                    engine.setPropertyDouble("panscan", 0.0)
                    engine.setPropertyString("video-aspect-override", "16:9")
                }
                AspectRatioMode.RATIO_4_3 -> {
                    engine.setPropertyString("keepaspect", "yes")
                    engine.setPropertyDouble("panscan", 0.0)
                    engine.setPropertyString("video-aspect-override", "4:3")
                }
            }
        }
    }

    fun refreshTelemetrySnapshot(): StreamTelemetry {
        val engine = mpv ?: return telemetryState
        if (!engine.isInitialized) return telemetryState
        runCatching {
            val w = engine.getPropertyInt("video-params/w")
                ?: engine.getPropertyInt("width")
                ?: telemetryState.width
            val h = engine.getPropertyInt("video-params/h")
                ?: engine.getPropertyInt("height")
                ?: telemetryState.height
            val fps = (engine.getPropertyDouble("container-fps")
                ?: engine.getPropertyDouble("estimated-vf-fps")
                ?: 0.0).toFloat()
            val vCodec = engine.getPropertyString("video-format")?.takeIf { it.isNotBlank() }
                ?: engine.getPropertyString("video-codec")?.takeIf { it.isNotBlank() }
                ?: telemetryState.videoCodec
            val aCodec = engine.getPropertyString("audio-codec-name")?.takeIf { it.isNotBlank() }
                ?: telemetryState.audioCodec
            val vBitrate = engine.getPropertyInt("video-bitrate") ?: 0
            val aBitrate = engine.getPropertyInt("audio-bitrate") ?: 0
            val kbps = if (vBitrate + aBitrate > 0) (vBitrate + aBitrate) / 1000 else telemetryState.bitrateKbps
            val buffering = (engine.getPropertyBoolean("paused-for-cache") == true) ||
                (!hasLoadedCurrentFile && (engine.getPropertyBoolean("core-idle") == true))

            val next = StreamTelemetry(
                width = w,
                height = h,
                fps = if (fps > 0f) fps else telemetryState.fps,
                videoCodec = vCodec,
                audioCodec = aCodec,
                bitrateKbps = kbps,
                isBuffering = buffering
            )
            if (next != telemetryState) {
                telemetryState = next
                onTelemetryChangedCallback?.invoke(next)
            }
        }
        return telemetryState
    }

    fun togglePlayPause() {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            val paused = engine.getPropertyBoolean("pause") ?: false
            engine.setPropertyBoolean("pause", !paused)
        }
    }

    fun pause() {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            engine.setPropertyBoolean("pause", true)
        }
    }

    fun seekTo(positionMs: Long) {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            val sec = (positionMs.coerceAtLeast(0L) / 1000.0).toString()
            engine.command("seek", sec, "absolute")
        }
    }

    fun seekBy(deltaMs: Long) {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            val sec = (deltaMs / 1000.0).toString()
            engine.command("seek", sec, "relative")
        }
    }

    fun setAudioOnly(enabled: Boolean) {
        isAudioOnly = enabled
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            if (enabled) {
                val cur = engine.getPropertyString("vid")
                if (!cur.isNullOrBlank() && cur != "no") {
                    savedVideoTrackId = cur
                }
                engine.setPropertyString("vid", "no")
            } else {
                engine.setPropertyString("vid", savedVideoTrackId.ifBlank { "auto" })
            }
        }
    }

    fun getTrackChoices(trackType: Int): List<TrackChoice> {
        val engine = mpv ?: return emptyList()
        if (!engine.isInitialized) return emptyList()
        return runCatching {
            val targetMpvType = when (trackType) {
                C.TRACK_TYPE_AUDIO -> "audio"
                C.TRACK_TYPE_TEXT -> "sub"
                C.TRACK_TYPE_VIDEO -> "video"
                else -> return emptyList()
            }
            val count = engine.getPropertyInt("track-list/count") ?: 0
            val out = mutableListOf<TrackChoice>()
            for (i in 0 until count) {
                val type = engine.getPropertyString("track-list/$i/type") ?: continue
                if (type != targetMpvType) continue
                val id = engine.getPropertyInt("track-list/$i/id") ?: continue
                val title = engine.getPropertyString("track-list/$i/title").orEmpty()
                val lang = engine.getPropertyString("track-list/$i/lang").orEmpty()
                val codec = engine.getPropertyString("track-list/$i/codec").orEmpty()
                val selected = engine.getPropertyBoolean("track-list/$i/selected") == true

                val label = when (trackType) {
                    C.TRACK_TYPE_VIDEO -> {
                        val h = engine.getPropertyInt("track-list/$i/demux-h") ?: 0
                        if (h > 0) "${h}p" else title.ifBlank { "Video #$id" }
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        val ch = engine.getPropertyInt("track-list/$i/demux-channel-count") ?: 0
                        val chText = if (ch > 2) "${ch}ch" else if (ch > 0) "Stereo" else ""
                        val base = title.ifBlank { lang.uppercase().ifBlank { "Audio #$id" } }
                        listOf(base, chText).filter { it.isNotBlank() }.joinToString(" • ")
                    }
                    else -> {
                        title.ifBlank { lang.uppercase().ifBlank { "Subtitle #$id" } }
                    }
                }

                val subLabel = listOf("libmpv #$id", codec.uppercase(), lang.uppercase())
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" • ")

                out.add(
                    TrackChoice(
                        id = "mpv_${targetMpvType}_$id",
                        label = label,
                        subLabel = subLabel,
                        isSelected = selected,
                        groupIndex = 0,
                        trackIndex = id,
                        trackType = trackType
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    fun selectTrack(choice: TrackChoice) {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            val prop = when (choice.trackType) {
                C.TRACK_TYPE_AUDIO -> "aid"
                C.TRACK_TYPE_TEXT -> "sid"
                C.TRACK_TYPE_VIDEO -> "vid"
                else -> return
            }
            engine.setPropertyString(prop, choice.trackIndex.toString())
            if (choice.trackType == C.TRACK_TYPE_VIDEO) {
                savedVideoTrackId = choice.trackIndex.toString()
            }
        }
    }

    fun clearTrackOverride(trackType: Int, disableTrack: Boolean) {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        runCatching {
            val prop = when (trackType) {
                C.TRACK_TYPE_AUDIO -> "aid"
                C.TRACK_TYPE_TEXT -> "sid"
                C.TRACK_TYPE_VIDEO -> "vid"
                else -> return
            }
            engine.setPropertyString(prop, if (disableTrack) "no" else "auto")
        }
    }

    fun stopStream() {
        val engine = mpv ?: return
        if (!engine.isInitialized) return
        hasLoadedCurrentFile = false
        runCatching {
            engine.command("stop")
        }
    }

    fun release() {
        onErrorCallback = null
        onTracksChangedCallback = null
        onTelemetryChangedCallback = null
        hasLoadedCurrentFile = false
        val engine = mpv
        if (engine != null && engine.isInitialized) {
            runCatching {
                engine.removeObserver(mpvObserver)
                engine.detachSurface()
                engine.destroy()
            }
        }
        mpv = null
        attachedSurface = null
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
