package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "novastream_prefs")

@Serializable
enum class BufferPreset(val label: String, val minBufferMs: Int, val maxBufferMs: Int, val playBufferMs: Int) {
    FAST_ZAP("Fast Zap (1.5s)", 1500, 15000, 500),
    BALANCED("Balanced (4s)", 4000, 30000, 1200),
    HIGH_STABILITY("High Stability (10s)", 10000, 60000, 2500)
}

@Serializable
enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3")
}

@Serializable
enum class PlaybackEngine(
    val label: String,
    val shortBadge: String,
    val subtitle: String,
    val isBuiltIn: Boolean,
    val packageCandidates: List<String> = emptyList()
) {
    DEFAULT(
        label = "Follow Global Default",
        shortBadge = "AUTO",
        subtitle = "Uses your global default playback engine",
        isBuiltIn = true
    ),
    EXOPLAYER(
        label = "ExoPlayer (Media3)",
        shortBadge = "EXO",
        subtitle = "Hardware-accelerated HLS, DASH, RTSP & Widevine/ClearKey DRM",
        isBuiltIn = true
    ),
    LIBVLC(
        label = "LibVLC (Built-In)",
        shortBadge = "VLC",
        subtitle = "Embedded VideoLAN FFmpeg engine for MPEG-TS, MKV, AC3/EAC3/DTS",
        isBuiltIn = true
    ),
    LIBMPV(
        label = "libmpv (Built-In)",
        shortBadge = "MPV",
        subtitle = "Embedded libmpv + FFmpeg/libass GPU engine for MKV, ASS/SSA, HEVC & AV1",
        isBuiltIn = true
    ),
    EXTERNAL_MPV(
        label = "MPV App (External)",
        shortBadge = "MPV APP",
        subtitle = "External mpv-android handoff with custom HTTP headers & resume",
        isBuiltIn = false,
        packageCandidates = listOf("is.xyz.mpv", "is.xyz.mpv.ytdl")
    ),
    EXTERNAL_VLC(
        label = "VLC for Android",
        shortBadge = "VLC APP",
        subtitle = "Standalone VLC app handoff with resume timestamp",
        isBuiltIn = false,
        packageCandidates = listOf("org.videolan.vlc", "org.videolan.vlc.debug")
    ),
    EXTERNAL_MX(
        label = "MX Player",
        shortBadge = "MX",
        subtitle = "External MX Player Pro/Ad with HTTP headers & resume",
        isBuiltIn = false,
        packageCandidates = listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")
    ),
    EXTERNAL_JUST(
        label = "Just (Video) Player",
        shortBadge = "JUST",
        subtitle = "Lightweight external Media3/FFmpeg player handoff",
        isBuiltIn = false,
        packageCandidates = listOf("com.brouken.player")
    ),
    EXTERNAL_KODI(
        label = "Kodi Media Center",
        shortBadge = "KODI",
        subtitle = "External Kodi media center stream handoff",
        isBuiltIn = false,
        packageCandidates = listOf("org.xbmc.kodi")
    ),
    EXTERNAL_CHOOSER(
        label = "System App Chooser",
        shortBadge = "EXT",
        subtitle = "Prompt Android Open With sheet on launch",
        isBuiltIn = false
    );

    companion object {
        val selectableGlobalEngines: List<PlaybackEngine>
            get() = entries.filter { it != DEFAULT }

        val builtInEngines: List<PlaybackEngine>
            get() = listOf(EXOPLAYER, LIBVLC, LIBMPV)

        val externalEngines: List<PlaybackEngine>
            get() = entries.filter { !it.isBuiltIn }

        fun fromStoredName(raw: String?): PlaybackEngine {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

@Serializable
enum class VlcHardwareMode(val label: String, val subtitle: String) {
    AUTO("Auto (HW+)", "Hardware decoding with automatic FFmpeg software fallback"),
    HARDWARE("Full Hardware", "Force MediaCodec hardware acceleration"),
    SOFTWARE("Software (FFmpeg)", "Force CPU software decoding for non-standard streams")
}

@Serializable
enum class MpvHardwareMode(val label: String, val mpvValue: String, val subtitle: String) {
    AUTO_SAFE("Auto-Safe (HW+)", "mediacodec-copy", "MediaCodec copy mode with automatic FFmpeg fallback"),
    ZERO_COPY("Zero-Copy HW", "mediacodec", "Direct MediaCodec surface rendering for lowest power"),
    SOFTWARE("Software (CPU)", "no", "Full FFmpeg software decoding for non-standard streams")
}

@Serializable
data class UserPreferences(
    val activeProfileId: Long = -1L,
    val hasPinConfigured: Boolean = false,
    val bufferPreset: BufferPreset = BufferPreset.BALANCED,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val autoPipOnBackground: Boolean = true,
    val preferExternalPlayer: Boolean = false,
    val autoReconnectLive: Boolean = true,
    val defaultEngine: PlaybackEngine = PlaybackEngine.EXOPLAYER,
    val vlcHardwareMode: VlcHardwareMode = VlcHardwareMode.AUTO,
    val mpvHardwareMode: MpvHardwareMode = MpvHardwareMode.AUTO_SAFE,
    val autoFallbackEngine: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val PIN_HASH = stringPreferencesKey("parental_pin_hash")
        val PIN_SALT = stringPreferencesKey("parental_pin_salt")
        val BUFFER_PRESET = stringPreferencesKey("buffer_preset")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val PREFER_EXTERNAL = booleanPreferencesKey("prefer_external")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val DEFAULT_ENGINE = stringPreferencesKey("default_engine")
        val VLC_HW_MODE = stringPreferencesKey("vlc_hw_mode")
        val MPV_HW_MODE = stringPreferencesKey("mpv_hw_mode")
        val AUTO_FALLBACK_ENGINE = booleanPreferencesKey("auto_fallback_engine")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val legacyExternal = prefs[Keys.PREFER_EXTERNAL] ?: false
        val rawEngine = prefs[Keys.DEFAULT_ENGINE]
        val resolvedDefaultEngine = if (rawEngine != null) {
            PlaybackEngine.fromStoredName(rawEngine).takeIf { it != PlaybackEngine.DEFAULT } ?: PlaybackEngine.EXOPLAYER
        } else if (legacyExternal) {
            PlaybackEngine.EXTERNAL_CHOOSER
        } else {
            PlaybackEngine.EXOPLAYER
        }
        UserPreferences(
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: -1L,
            hasPinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            bufferPreset = runCatching {
                BufferPreset.valueOf(prefs[Keys.BUFFER_PRESET] ?: BufferPreset.BALANCED.name)
            }.getOrDefault(BufferPreset.BALANCED),
            defaultAspectRatio = runCatching {
                AspectRatioMode.valueOf(prefs[Keys.ASPECT_RATIO] ?: AspectRatioMode.FIT.name)
            }.getOrDefault(AspectRatioMode.FIT),
            autoPipOnBackground = prefs[Keys.AUTO_PIP] ?: true,
            preferExternalPlayer = !resolvedDefaultEngine.isBuiltIn,
            autoReconnectLive = prefs[Keys.AUTO_RECONNECT] ?: true,
            defaultEngine = resolvedDefaultEngine,
            vlcHardwareMode = runCatching {
                VlcHardwareMode.valueOf(prefs[Keys.VLC_HW_MODE] ?: VlcHardwareMode.AUTO.name)
            }.getOrDefault(VlcHardwareMode.AUTO),
            mpvHardwareMode = runCatching {
                MpvHardwareMode.valueOf(prefs[Keys.MPV_HW_MODE] ?: MpvHardwareMode.AUTO_SAFE.name)
            }.getOrDefault(MpvHardwareMode.AUTO_SAFE),
            autoFallbackEngine = prefs[Keys.AUTO_FALLBACK_ENGINE] ?: true
        )
    }

    suspend fun setActiveProfileId(profileId: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = profileId }
    }

    suspend fun setBufferPreset(preset: BufferPreset) {
        context.dataStore.edit { it[Keys.BUFFER_PRESET] = preset.name }
    }

    suspend fun setDefaultAspectRatio(mode: AspectRatioMode) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = mode.name }
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PIP] = enabled }
    }

    suspend fun setPreferExternalPlayer(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.PREFER_EXTERNAL] = enabled
            it[Keys.DEFAULT_ENGINE] = if (enabled) PlaybackEngine.EXTERNAL_CHOOSER.name else PlaybackEngine.EXOPLAYER.name
        }
    }

    suspend fun setDefaultEngine(engine: PlaybackEngine) {
        val normalized = if (engine == PlaybackEngine.DEFAULT) PlaybackEngine.EXOPLAYER else engine
        context.dataStore.edit {
            it[Keys.DEFAULT_ENGINE] = normalized.name
            it[Keys.PREFER_EXTERNAL] = !normalized.isBuiltIn
        }
    }

    suspend fun setVlcHardwareMode(mode: VlcHardwareMode) {
        context.dataStore.edit { it[Keys.VLC_HW_MODE] = mode.name }
    }

    suspend fun setMpvHardwareMode(mode: MpvHardwareMode) {
        context.dataStore.edit { it[Keys.MPV_HW_MODE] = mode.name }
    }

    suspend fun setAutoFallbackEngine(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FALLBACK_ENGINE] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setParentalPin(pin: String) {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }.joinToString("") { "%02x".format(it) }
        val hash = sha256("$salt:$pin")
        context.dataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    suspend fun clearParentalPin() {
        context.dataStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
        }
    }

    suspend fun verifyParentalPin(candidatePin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val salt = prefs[Keys.PIN_SALT] ?: return false
        val expectedHash = prefs[Keys.PIN_HASH] ?: return false
        return sha256("$salt:$candidatePin") == expectedHash
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
