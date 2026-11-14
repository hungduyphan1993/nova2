package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AdultCategoryClassifier
import com.example.data.local.AspectRatioMode
import com.example.data.local.BufferPreset
import com.example.data.local.CategoryEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.MpvHardwareMode
import com.example.data.local.PlaybackEngine
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.StartupBehavior
import com.example.data.local.SubtitleSizePreset
import com.example.data.local.UserPreferences
import com.example.data.local.VlcHardwareMode
import com.example.data.network.DiagnosticReport
import com.example.data.network.StreamDiagnosticsService
import com.example.data.repository.NovaStreamRepository
import com.example.data.repository.SyncStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PinPromptState(
    val isVisible: Boolean = false,
    val reason: String = "",
    val onUnlocked: (() -> Unit)? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: NovaStreamRepository,
    private val diagnosticsService: StreamDiagnosticsService
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.prefs.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val profiles: StateFlow<List<PlaylistProfileEntity>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<PlaylistProfileEntity?> = preferences
        .flatMapLatest { prefs ->
            if (prefs.activeProfileId > 0) repository.observeProfile(prefs.activeProfileId) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val streamCounts: StateFlow<Map<MediaCategoryType, Int>> = preferences
        .flatMapLatest { prefs ->
            if (prefs.activeProfileId > 0) repository.observeCounts(prefs.activeProfileId) else flowOf(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allCategories: StateFlow<List<CategoryEntity>> = preferences
        .flatMapLatest { prefs ->
            if (prefs.activeProfileId > 0) repository.observeAllCategories(prefs.activeProfileId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteCount: StateFlow<Int> = preferences
        .flatMapLatest { prefs ->
            if (prefs.activeProfileId > 0) repository.observeFavoriteCount(prefs.activeProfileId) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val historyCount: StateFlow<Int> = preferences
        .flatMapLatest { prefs ->
            if (prefs.activeProfileId > 0) repository.observeHistoryCount(prefs.activeProfileId) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val epgCount: StateFlow<Int> = preferences
        .flatMapLatest { prefs ->
            if (prefs.activeProfileId > 0) repository.observeEpgCount(prefs.activeProfileId) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _syncStage = MutableStateFlow<SyncStage>(SyncStage.Idle)
    val syncStage: StateFlow<SyncStage> = _syncStage.asStateFlow()

    private val _isSessionPinUnlocked = MutableStateFlow(false)
    val isSessionPinUnlocked: StateFlow<Boolean> = _isSessionPinUnlocked.asStateFlow()

    private val _pinPromptState = MutableStateFlow(PinPromptState())
    val pinPromptState: StateFlow<PinPromptState> = _pinPromptState.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<DiagnosticReport?>(null)
    val diagnosticReport: StateFlow<DiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _isRunningDiagnostics = MutableStateFlow(false)
    val isRunningDiagnostics: StateFlow<Boolean> = _isRunningDiagnostics.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun dismissSyncStage() {
        _syncStage.value = SyncStage.Idle
    }

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            repository.prefs.setActiveProfileId(profileId)
            _isSessionPinUnlocked.value = false
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            _toastMessage.value = "Playlist removed"
        }
    }

    fun connectXtream(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.connectXtreamProfile(name, serverUrl, username, password, userAgent) { stage ->
                _syncStage.value = stage
            }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun connectM3uUrl(
        name: String,
        m3uUrl: String,
        epgUrl: String,
        userAgent: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.connectM3uUrl(name, m3uUrl, epgUrl, userAgent) { stage ->
                _syncStage.value = stage
            }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun connectM3uFile(
        name: String,
        uri: Uri,
        epgUrl: String,
        userAgent: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.connectM3uFile(name, uri, epgUrl, userAgent) { stage ->
                _syncStage.value = stage
            }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun loadDemoPlaylist(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.seedPublicDemoPlaylist { stage ->
                _syncStage.value = stage
            }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun refreshActiveProfile() {
        val id = activeProfile.value?.id ?: return
        viewModelScope.launch {
            repository.refreshActiveProfile(id) { stage ->
                _syncStage.value = stage
            }
        }
    }

    fun requestUnlockOrProceed(isLocked: Boolean, reason: String, action: () -> Unit) {
        val hasPin = preferences.value.hasPinConfigured
        if (!isLocked || !hasPin || _isSessionPinUnlocked.value) {
            action()
        } else {
            _pinError.value = null
            _pinPromptState.value = PinPromptState(isVisible = true, reason = reason, onUnlocked = action)
        }
    }

    fun submitPinCandidate(pin: String) {
        viewModelScope.launch {
            if (repository.prefs.verifyParentalPin(pin)) {
                _isSessionPinUnlocked.value = true
                val callback = _pinPromptState.value.onUnlocked
                _pinPromptState.value = PinPromptState()
                _pinError.value = null
                callback?.invoke()
            } else {
                _pinError.value = "Incorrect PIN. Try again."
            }
        }
    }

    fun dismissPinPrompt() {
        _pinPromptState.value = PinPromptState()
        _pinError.value = null
    }

    fun relockSession() {
        _isSessionPinUnlocked.value = false
        _toastMessage.value = "Parental lock re-engaged"
    }

    fun setParentalPin(pin: String) {
        viewModelScope.launch {
            repository.prefs.setParentalPin(pin)
            _isSessionPinUnlocked.value = true
            _toastMessage.value = "Parental PIN saved"
        }
    }

    fun clearParentalPin() {
        viewModelScope.launch {
            repository.prefs.clearParentalPin()
            _isSessionPinUnlocked.value = false
            _toastMessage.value = "Parental PIN removed"
        }
    }

    fun setCategoryPinned(categoryId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setCategoryPinned(categoryId, pinned) }
    }

    fun setCategoryHidden(categoryId: Long, hidden: Boolean) {
        viewModelScope.launch { repository.setCategoryHidden(categoryId, hidden) }
    }

    fun setCategoryLocked(categoryId: Long, locked: Boolean) {
        viewModelScope.launch { repository.setCategoryLocked(categoryId, locked) }
    }

    fun setCategoriesLocked(ids: List<Long>, locked: Boolean, feedbackLabel: String? = null) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.setCategoriesLocked(ids, locked)
            feedbackLabel?.let { _toastMessage.value = it }
        }
    }

    fun setCategoriesHidden(ids: List<Long>, hidden: Boolean, feedbackLabel: String? = null) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.setCategoriesHidden(ids, hidden)
            feedbackLabel?.let { _toastMessage.value = it }
        }
    }

    fun lockAllAdultCategories() {
        val adultCats = allCategories.value.filter { AdultCategoryClassifier.isAdultCategoryName(it.name) }
        if (adultCats.isEmpty()) {
            _toastMessage.value = "No Adult / 18+ / XXX categories found in playlist"
            return
        }
        setCategoriesLocked(adultCats.map { it.id }, true, "Locked ${adultCats.size} Adult / 18+ / XXX categories")
    }

    fun unlockAllAdultCategories() {
        val adultCats = allCategories.value.filter { AdultCategoryClassifier.isAdultCategoryName(it.name) }
        if (adultCats.isEmpty()) return
        setCategoriesLocked(adultCats.map { it.id }, false, "Unlocked ${adultCats.size} Adult / 18+ / XXX categories")
    }

    fun hideAllAdultCategories() {
        val adultCats = allCategories.value.filter { AdultCategoryClassifier.isAdultCategoryName(it.name) }
        if (adultCats.isEmpty()) {
            _toastMessage.value = "No Adult / 18+ / XXX categories found in playlist"
            return
        }
        setCategoriesHidden(adultCats.map { it.id }, true, "Hidden ${adultCats.size} Adult / 18+ / XXX categories")
    }

    fun setAutoLockAdultCategories(enabled: Boolean) {
        viewModelScope.launch {
            repository.prefs.setAutoLockAdultCategories(enabled)
            if (enabled) {
                val profileId = activeProfile.value?.id ?: -1L
                val newlyLocked = if (profileId > 0) repository.applyAdultAutoLockForProfile(profileId) else 0
                _toastMessage.value = if (newlyLocked > 0) {
                    "Auto-lock enabled • Locked $newlyLocked Adult/18+/XXX folder(s)"
                } else {
                    "Auto-lock enabled for Adult / 18+ / XXX categories"
                }
            } else {
                _toastMessage.value = "Auto-lock disabled for new syncs"
            }
        }
    }

    fun setBufferPreset(preset: BufferPreset) {
        viewModelScope.launch { repository.prefs.setBufferPreset(preset) }
    }

    fun setDefaultAspectRatio(mode: AspectRatioMode) {
        viewModelScope.launch { repository.prefs.setDefaultAspectRatio(mode) }
    }

    fun setAutoPip(enabled: Boolean) {
        viewModelScope.launch { repository.prefs.setAutoPip(enabled) }
    }

    fun setPreferExternalPlayer(enabled: Boolean) {
        viewModelScope.launch { repository.prefs.setPreferExternalPlayer(enabled) }
    }

    fun setDefaultEngine(engine: PlaybackEngine) {
        viewModelScope.launch {
            repository.prefs.setDefaultEngine(engine)
            _toastMessage.value = "Default player set to ${engine.label}"
        }
    }

    fun setVlcHardwareMode(mode: VlcHardwareMode) {
        viewModelScope.launch { repository.prefs.setVlcHardwareMode(mode) }
    }

    fun setMpvHardwareMode(mode: MpvHardwareMode) {
        viewModelScope.launch { repository.prefs.setMpvHardwareMode(mode) }
    }

    fun setAutoFallbackEngine(enabled: Boolean) {
        viewModelScope.launch { repository.prefs.setAutoFallbackEngine(enabled) }
    }

    fun setAutoReconnectLive(enabled: Boolean) {
        viewModelScope.launch { repository.prefs.setAutoReconnect(enabled) }
    }

    fun setSubtitleSize(size: SubtitleSizePreset) {
        viewModelScope.launch { repository.prefs.setSubtitleSize(size) }
    }

    fun setPreferredAudioLanguage(langCode: String) {
        viewModelScope.launch { repository.prefs.setPreferredAudioLanguage(langCode) }
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        viewModelScope.launch { repository.prefs.setPreferredSubtitleLanguage(langCode) }
    }

    fun setOsdTimeoutSec(seconds: Int) {
        viewModelScope.launch { repository.prefs.setOsdTimeoutSec(seconds) }
    }

    fun setUse24HourClock(use24Hour: Boolean) {
        viewModelScope.launch { repository.prefs.setUse24HourClock(use24Hour) }
    }

    fun setStartupBehavior(behavior: StartupBehavior) {
        viewModelScope.launch { repository.prefs.setStartupBehavior(behavior) }
    }

    fun updateProfileUserAgent(userAgent: String) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            repository.updateProfileUserAgent(profile.id, userAgent)
            _toastMessage.value = "User-Agent updated"
        }
    }

    fun clearWatchHistory() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            repository.clearWatchHistory(profileId)
            _toastMessage.value = "Watch history cleared"
        }
    }

    fun clearFavorites() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            repository.clearFavorites(profileId)
            _toastMessage.value = "Favorites cleared"
        }
    }

    fun clearEpgCache() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            repository.clearEpgCache(profileId)
            _toastMessage.value = "EPG cache cleared"
        }
    }

    fun clearEngineOverrides() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            repository.clearEngineOverrides(profileId)
            _toastMessage.value = "Per-channel player overrides reset"
        }
    }

    fun adjustEpgOffset(deltaMinutes: Int) {
        val profile = activeProfile.value ?: return
        val next = (profile.epgOffsetMinutes + deltaMinutes).coerceIn(-720, 720)
        viewModelScope.launch {
            repository.updateEpgOffset(profile.id, next)
            _toastMessage.value = "EPG time offset set to ${if (next >= 0) "+$next" else "$next"} min"
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val res = repository.exportBackupToUri(uri)
            _toastMessage.value = res.getOrElse { "Export failed: ${it.message}" }
        }
    }

    fun importBackup(uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {
            val res = repository.importBackupFromUri(uri) { stage -> _syncStage.value = stage }
            _toastMessage.value = res.getOrElse { "Import failed: ${it.message}" }
            if (res.isSuccess) onComplete()
        }
    }

    fun runServerDiagnostics(customUrl: String? = null) {
        val profile = activeProfile.value
        val target = customUrl?.takeIf { it.isNotBlank() }
            ?: profile?.serverUrl?.takeIf { it.isNotBlank() }
            ?: profile?.m3uUri?.takeIf { it.startsWith("http") }
            ?: ""
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            _diagnosticReport.value = diagnosticsService.runDiagnostics(
                targetUrl = target,
                userAgent = profile?.userAgent ?: "NovaStream/1.0"
            )
            _isRunningDiagnostics.value = false
        }
    }

    class Factory(
        private val repository: NovaStreamRepository,
        private val diagnosticsService: StreamDiagnosticsService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, diagnosticsService) as T
        }
    }
}
