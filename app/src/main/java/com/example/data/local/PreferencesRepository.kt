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
data class UserPreferences(
    val activeProfileId: Long = -1L,
    val hasPinConfigured: Boolean = false,
    val bufferPreset: BufferPreset = BufferPreset.BALANCED,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val autoPipOnBackground: Boolean = true,
    val preferExternalPlayer: Boolean = false,
    val autoReconnectLive: Boolean = true
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
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
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
            preferExternalPlayer = prefs[Keys.PREFER_EXTERNAL] ?: false,
            autoReconnectLive = prefs[Keys.AUTO_RECONNECT] ?: true
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
        context.dataStore.edit { it[Keys.PREFER_EXTERNAL] = enabled }
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
