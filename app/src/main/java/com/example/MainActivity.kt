package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.MediaCategoryType
import com.example.player.ExternalPlayerHelper
import com.example.ui.components.DockedMiniPlayerBar
import com.example.ui.components.PinVerificationDialog
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.DiagnosticsRoute
import com.example.ui.navigation.EpgGuideRoute
import com.example.ui.navigation.LiveTvRoute
import com.example.ui.navigation.MovieDetailRoute
import com.example.ui.navigation.MoviesRoute
import com.example.ui.navigation.MultiScreenRoute
import com.example.ui.navigation.OnboardingRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SeriesDetailRoute
import com.example.ui.navigation.SeriesRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.EpgGuideScreen
import com.example.ui.screens.LiveTvScreen
import com.example.ui.screens.MovieDetailScreen
import com.example.ui.screens.MoviesScreen
import com.example.ui.screens.MultiScreenScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PlayerOverlayScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SeriesDetailScreen
import com.example.ui.screens.SeriesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NovaStreamTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PlayerPresentationMode
import com.example.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private var isInPipState = mutableStateOf(false)
    private var pipTriggerCondition: (() -> Boolean)? = null
    private var pipAspectRatioProvider: (() -> Rational)? = null

    @kotlin.OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as NovaStreamApp).container

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val mainVm: MainViewModel = viewModel(
                factory = MainViewModel.Factory(appContainer.repository, appContainer.diagnosticsService)
            )
            val catalogVm: CatalogViewModel = viewModel(
                factory = CatalogViewModel.Factory(appContainer.repository)
            )
            val playerVm: PlayerViewModel = viewModel(
                factory = PlayerViewModel.Factory(appContainer.repository, appContainer.playerManager)
            )

            val preferences by mainVm.preferences.collectAsStateWithLifecycle()
            val playerUiState by playerVm.uiState.collectAsStateWithLifecycle()

            DisposableEffect(preferences.autoPipOnBackground, playerUiState.mode, playerUiState.isPlaying, playerUiState.telemetry) {
                pipTriggerCondition = {
                    preferences.autoPipOnBackground &&
                        playerUiState.mode == PlayerPresentationMode.FULLSCREEN &&
                        playerUiState.activeStream != null
                }
                pipAspectRatioProvider = {
                    val w = playerUiState.telemetry.width
                    val h = playerUiState.telemetry.height
                    if (w > 0 && h > 0) {
                        val ratio = (w.toFloat() / h.toFloat()).coerceIn(0.45f, 2.35f)
                        Rational((ratio * 100).toInt(), 100)
                    } else {
                        Rational(16, 9)
                    }
                }
                onDispose { }
            }

            NovaStreamTheme {
                NovaStreamRoot(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    isInPipMode = isInPipState.value,
                    appContainer = appContainer,
                    mainVm = mainVm,
                    catalogVm = catalogVm,
                    playerVm = playerVm,
                    onRequestPip = { enterPipNow() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipTriggerCondition?.invoke() == true) {
            enterPipNow()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState.value = isInPictureInPictureMode
    }

    private fun enterPipNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspect = pipAspectRatioProvider?.invoke() ?: Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspect)
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }
}

@Composable
private fun NovaStreamRoot(
    widthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass,
    isInPipMode: Boolean,
    appContainer: AppContainer,
    mainVm: MainViewModel,
    catalogVm: CatalogViewModel,
    playerVm: PlayerViewModel,
    onRequestPip: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val preferences by mainVm.preferences.collectAsStateWithLifecycle()
    val profiles by mainVm.profiles.collectAsStateWithLifecycle()
    val activeProfile by mainVm.activeProfile.collectAsStateWithLifecycle()
    val streamCounts by mainVm.streamCounts.collectAsStateWithLifecycle()
    val allCategories by mainVm.allCategories.collectAsStateWithLifecycle()
    val syncStage by mainVm.syncStage.collectAsStateWithLifecycle()
    val isPinSessionUnlocked by mainVm.isSessionPinUnlocked.collectAsStateWithLifecycle()
    val pinPromptState by mainVm.pinPromptState.collectAsStateWithLifecycle()
    val pinError by mainVm.pinError.collectAsStateWithLifecycle()
    val diagnosticReport by mainVm.diagnosticReport.collectAsStateWithLifecycle()
    val isRunningDiagnostics by mainVm.isRunningDiagnostics.collectAsStateWithLifecycle()
    val toastMessage by mainVm.toastMessage.collectAsStateWithLifecycle()

    val liveCategories by catalogVm.liveCategories.collectAsStateWithLifecycle()
    val movieCategories by catalogVm.movieCategories.collectAsStateWithLifecycle()
    val seriesCategories by catalogVm.seriesCategories.collectAsStateWithLifecycle()
    val liveStreams by catalogVm.liveStreams.collectAsStateWithLifecycle()
    val movieStreams by catalogVm.movieStreams.collectAsStateWithLifecycle()
    val seriesStreams by catalogVm.seriesStreams.collectAsStateWithLifecycle()
    val favorites by catalogVm.favorites.collectAsStateWithLifecycle()
    val watchProgress by catalogVm.watchProgress.collectAsStateWithLifecycle()
    val epgByChannel by catalogVm.epgByChannel.collectAsStateWithLifecycle()
    val searchQuery by catalogVm.searchQuery.collectAsStateWithLifecycle()
    val searchResult by catalogVm.searchResult.collectAsStateWithLifecycle()
    val isSearching by catalogVm.isSearching.collectAsStateWithLifecycle()

    val playerUiState by playerVm.uiState.collectAsStateWithLifecycle()
    val activePlayer by playerVm.activePlayer.collectAsStateWithLifecycle()
    val multiSlots by playerVm.multiSlots.collectAsStateWithLifecycle()
    val multiLayout by playerVm.multiLayout.collectAsStateWithLifecycle()
    val focusedAudioSlot by playerVm.focusedAudioSlot.collectAsStateWithLifecycle()
    val muteAllMulti by playerVm.muteAllMulti.collectAsStateWithLifecycle()

    var initialRouteResolved by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(Unit) {
        val prefsSnapshot = appContainer.preferences.preferencesFlow.first()
        initialRouteResolved = if (prefsSnapshot.activeProfileId > 0L) DashboardRoute else OnboardingRoute
    }

    // Immersive system bars in Fullscreen Player
    LaunchedEffect(playerUiState.mode) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (playerUiState.mode == PlayerPresentationMode.FULLSCREEN) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainVm.consumeToast()
        }
    }

    val startDest = initialRouteResolved
    if (startDest == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ElectricCyan)
        }
        return
    }

    val navController = rememberNavController()
    val userAgent = activeProfile?.userAgent ?: "NovaStream/1.0 (Android)"

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDest
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(
                        profiles = profiles,
                        activeProfileId = preferences.activeProfileId,
                        syncStage = syncStage,
                        canNavigateBack = profiles.isNotEmpty() && navController.previousBackStackEntry != null,
                        onBack = { navController.popBackStack() },
                        onDismissSync = { mainVm.dismissSyncStage() },
                        onConnectXtream = { name, srv, usr, pwd, ua ->
                            mainVm.connectXtream(name, srv, usr, pwd, ua) {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            }
                        },
                        onConnectM3uUrl = { name, url, epg, ua ->
                            mainVm.connectM3uUrl(name, url, epg, ua) {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            }
                        },
                        onConnectM3uFile = { name, uri, epg, ua ->
                            mainVm.connectM3uFile(name, uri, epg, ua) {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            }
                        },
                        onLoadDemo = {
                            mainVm.loadDemoPlaylist {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            }
                        },
                        onImportBackup = { uri ->
                            mainVm.importBackup(uri) {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            }
                        },
                        onSelectProfile = { id ->
                            mainVm.selectProfile(id)
                            navController.navigate(DashboardRoute) {
                                popUpTo<OnboardingRoute> { inclusive = true }
                            }
                        },
                        onDeleteProfile = { id -> mainVm.deleteProfile(id) }
                    )
                }

                composable<DashboardRoute> {
                    DashboardScreen(
                        widthSizeClass = widthSizeClass,
                        profile = activeProfile,
                        counts = streamCounts,
                        liveStreams = liveStreams,
                        epgByChannel = epgByChannel,
                        activeLiveChannel = playerUiState.activeStream?.takeIf { playerUiState.isLive },
                        favorites = favorites,
                        watchProgress = watchProgress,
                        hasPinConfigured = preferences.hasPinConfigured,
                        isPinSessionUnlocked = isPinSessionUnlocked,
                        syncStage = syncStage,
                        onDismissSync = { mainVm.dismissSyncStage() },
                        onOpenLiveTv = { navController.navigate(LiveTvRoute()) },
                        onOpenMovies = { navController.navigate(MoviesRoute) },
                        onOpenSeries = { navController.navigate(SeriesRoute) },
                        onOpenEpg = { navController.navigate(EpgGuideRoute) },
                        onOpenMultiScreen = { navController.navigate(MultiScreenRoute) },
                        onOpenCatchUp = { navController.navigate(LiveTvRoute(catchupOnly = true)) },
                        onOpenFavorites = { navController.navigate(LiveTvRoute(favoritesOnly = true)) },
                        onOpenSearch = { navController.navigate(SearchRoute) },
                        onOpenDiagnostics = { navController.navigate(DiagnosticsRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenProfiles = { navController.navigate(OnboardingRoute) },
                        onRefreshSync = { mainVm.refreshActiveProfile() },
                        onToggleSessionRelock = {
                            if (isPinSessionUnlocked) {
                                mainVm.relockSession()
                            } else {
                                mainVm.requestUnlockOrProceed(true, "Unlock parental session") {}
                            }
                        },
                        onTuneSpotlightChannel = { ch ->
                            mainVm.requestUnlockOrProceed(ch.isLocked, "Unlock channel: ${ch.title}") {
                                playerVm.playLiveChannel(ch, liveStreams, userAgent, openFullscreen = true)
                            }
                        },
                        onResumeItem = { prog ->
                            when (prog.mediaType) {
                                MediaCategoryType.LIVE -> {
                                    val ch = liveStreams.firstOrNull { it.id == prog.streamDbId }
                                    if (ch != null) {
                                        mainVm.requestUnlockOrProceed(ch.isLocked, "Unlock channel: ${ch.title}") {
                                            playerVm.playLiveChannel(ch, liveStreams, userAgent)
                                        }
                                    }
                                }
                                MediaCategoryType.MOVIE -> navController.navigate(MovieDetailRoute(prog.streamDbId))
                                MediaCategoryType.SERIES -> navController.navigate(SeriesDetailRoute(prog.streamDbId))
                            }
                        }
                    )
                }

                composable<LiveTvRoute> { entry ->
                    val args = entry.toRoute<LiveTvRoute>()
                    LiveTvScreen(
                        widthSizeClass = widthSizeClass,
                        categories = liveCategories,
                        streams = liveStreams,
                        epgByChannel = epgByChannel,
                        initialCategoryKey = args.initialCategoryKey,
                        initialFavoritesOnly = args.favoritesOnly,
                        initialCatchupOnly = args.catchupOnly,
                        isPinSessionUnlocked = isPinSessionUnlocked,
                        hasPinConfigured = preferences.hasPinConfigured,
                        onBack = { navController.popBackStack() },
                        onRequestUnlock = { locked, reason, action ->
                            mainVm.requestUnlockOrProceed(locked, reason, action)
                        },
                        onToggleFavorite = { catalogVm.toggleFavorite(it) },
                        onToggleStreamLock = { item ->
                            mainVm.requestUnlockOrProceed(preferences.hasPinConfigured, "Change lock on ${item.title}") {
                                catalogVm.toggleStreamLock(item)
                            }
                        },
                        onPlayChannel = { ch, queue ->
                            if (preferences.preferExternalPlayer) {
                                ExternalPlayerHelper.launchExternalPlayer(context, ch.streamUrl, ch.title)
                            } else {
                                playerVm.playLiveChannel(ch, queue, userAgent, openFullscreen = true)
                            }
                        }
                    )
                }

                composable<MoviesRoute> {
                    MoviesScreen(
                        categories = movieCategories,
                        movies = movieStreams,
                        isPinSessionUnlocked = isPinSessionUnlocked,
                        hasPinConfigured = preferences.hasPinConfigured,
                        onBack = { navController.popBackStack() },
                        onRequestUnlock = { locked, reason, action ->
                            mainVm.requestUnlockOrProceed(locked, reason, action)
                        },
                        onOpenMovie = { movie -> navController.navigate(MovieDetailRoute(movie.id)) }
                    )
                }

                composable<MovieDetailRoute> { entry ->
                    val args = entry.toRoute<MovieDetailRoute>()
                    val movie by catalogVm.observeStreamById(args.streamDbId).collectAsStateWithLifecycle(initialValue = null)
                    val resume = watchProgress.firstOrNull { it.streamDbId == args.streamDbId && it.mediaType == MediaCategoryType.MOVIE }
                    MovieDetailScreen(
                        movie = movie,
                        resumeProgress = resume,
                        onBack = { navController.popBackStack() },
                        onLoadRichInfo = { m ->
                            activeProfile?.id?.let { pid -> catalogVm.loadVodRichInfo(pid, m) }
                        },
                        onToggleFavorite = { catalogVm.toggleFavorite(it) },
                        onPlayMovie = { m, resumeMs ->
                            if (preferences.preferExternalPlayer) {
                                ExternalPlayerHelper.launchExternalPlayer(context, m.streamUrl, m.title)
                            } else {
                                playerVm.playMovie(m, userAgent, resumeMs)
                            }
                        }
                    )
                }

                composable<SeriesRoute> {
                    SeriesScreen(
                        categories = seriesCategories,
                        seriesList = seriesStreams,
                        isPinSessionUnlocked = isPinSessionUnlocked,
                        hasPinConfigured = preferences.hasPinConfigured,
                        onBack = { navController.popBackStack() },
                        onRequestUnlock = { locked, reason, action ->
                            mainVm.requestUnlockOrProceed(locked, reason, action)
                        },
                        onOpenSeries = { series -> navController.navigate(SeriesDetailRoute(series.id)) }
                    )
                }

                composable<SeriesDetailRoute> { entry ->
                    val args = entry.toRoute<SeriesDetailRoute>()
                    val series by catalogVm.observeStreamById(args.streamDbId).collectAsStateWithLifecycle(initialValue = null)
                    val profileId = activeProfile?.id ?: -1L
                    val episodesFlow = remember(profileId, series?.remoteStreamId) {
                        val remoteId = series?.remoteStreamId
                        if (profileId > 0 && !remoteId.isNullOrBlank()) {
                            catalogVm.observeEpisodes(profileId, remoteId)
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                    }
                    val episodes by episodesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    SeriesDetailScreen(
                        series = series,
                        episodes = episodes,
                        onBack = { navController.popBackStack() },
                        onLoadEpisodes = { s ->
                            if (profileId > 0) catalogVm.loadSeriesEpisodes(profileId, s)
                        },
                        onToggleFavorite = { catalogVm.toggleFavorite(it) },
                        onPlayEpisode = { s, ep, allEps ->
                            if (preferences.preferExternalPlayer) {
                                ExternalPlayerHelper.launchExternalPlayer(context, ep.streamUrl, "${s.title} - ${ep.title}")
                            } else {
                                playerVm.playSeriesEpisode(s, ep, allEps, userAgent)
                            }
                        }
                    )
                }

                composable<EpgGuideRoute> {
                    EpgGuideScreen(
                        channels = liveStreams,
                        epgByChannel = epgByChannel,
                        epgOffsetMinutes = activeProfile?.epgOffsetMinutes ?: 0,
                        onBack = { navController.popBackStack() },
                        onAdjustOffset = { delta -> mainVm.adjustEpgOffset(delta) },
                        onTuneLive = { ch ->
                            mainVm.requestUnlockOrProceed(ch.isLocked, "Unlock channel: ${ch.title}") {
                                playerVm.playLiveChannel(ch, liveStreams, userAgent, openFullscreen = true)
                            }
                        },
                        onPlayCatchUp = { ch, prog ->
                            val profile = activeProfile ?: return@EpgGuideScreen
                            val archiveUrl = catalogVm.resolveCatchUpUrl(profile, ch, prog)
                            playerVm.playCatchUpProgram(ch, prog, archiveUrl, userAgent)
                        },
                        onToggleReminder = { prog -> catalogVm.toggleProgramReminder(prog) }
                    )
                }

                composable<MultiScreenRoute> {
                    MultiScreenScreen(
                        slots = multiSlots,
                        layout = multiLayout,
                        focusedAudioSlot = focusedAudioSlot,
                        muteAll = muteAllMulti,
                        liveChannels = liveStreams,
                        onBack = { navController.popBackStack() },
                        onChangeLayout = { playerVm.setMultiScreenLayout(it) },
                        onAssignSlot = { idx, ch -> playerVm.assignMultiScreenSlot(idx, ch, userAgent) },
                        onClearSlot = { playerVm.clearMultiScreenSlot(it) },
                        onFocusAudio = { playerVm.focusMultiScreenAudio(it) },
                        onToggleMuteAll = { playerVm.toggleMuteAllMultiScreen() },
                        onExpandSlot = { idx -> playerVm.expandMultiSlotToFullscreen(idx, liveStreams) },
                        onReleaseAll = { playerVm.releaseAllMultiScreen() }
                    )
                }

                composable<SearchRoute> {
                    SearchScreen(
                        query = searchQuery,
                        isSearching = isSearching,
                        result = searchResult,
                        onBack = { navController.popBackStack() },
                        onQueryChange = { q ->
                            activeProfile?.id?.let { pid ->
                                catalogVm.runOmniSearch(pid, q, isPinSessionUnlocked)
                            }
                        },
                        onSelectLive = { ch -> playerVm.playLiveChannel(ch, liveStreams, userAgent) },
                        onSelectMovie = { m -> navController.navigate(MovieDetailRoute(m.id)) },
                        onSelectSeries = { s -> navController.navigate(SeriesDetailRoute(s.id)) }
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        preferences = preferences,
                        categories = allCategories,
                        onBack = { navController.popBackStack() },
                        onSetPin = { mainVm.setParentalPin(it) },
                        onClearPin = { mainVm.clearParentalPin() },
                        onSetBufferPreset = { mainVm.setBufferPreset(it) },
                        onSetDefaultAspect = { mainVm.setDefaultAspectRatio(it) },
                        onSetAutoPip = { mainVm.setAutoPip(it) },
                        onSetPreferExternal = { mainVm.setPreferExternalPlayer(it) },
                        onSetAutoReconnect = { mainVm.setAutoReconnectLive(it) },
                        onToggleCatPinned = { id, p -> mainVm.setCategoryPinned(id, p) },
                        onToggleCatHidden = { id, h -> mainVm.setCategoryHidden(id, h) },
                        onToggleCatLocked = { id, l ->
                            mainVm.requestUnlockOrProceed(preferences.hasPinConfigured, "Change folder lock") {
                                mainVm.setCategoryLocked(id, l)
                            }
                        },
                        onExportBackup = { mainVm.exportBackup(it) },
                        onImportBackup = { uri ->
                            mainVm.importBackup(uri) {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<DashboardRoute> { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable<DiagnosticsRoute> {
                    val defaultUrl = activeProfile?.serverUrl?.takeIf { it.isNotBlank() }
                        ?: activeProfile?.m3uUri?.takeIf { it.startsWith("http") }
                        ?: ""
                    DiagnosticsScreen(
                        defaultTargetUrl = defaultUrl,
                        report = diagnosticReport,
                        isRunning = isRunningDiagnostics,
                        onBack = { navController.popBackStack() },
                        onRunTest = { url -> mainVm.runServerDiagnostics(url) }
                    )
                }
            }

            // Docked Live Mini-Player Bar
            if (playerUiState.mode == PlayerPresentationMode.MINI_DOCKED && !isInPipMode) {
                var miniNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        miniNowMs = System.currentTimeMillis()
                        delay(15_000L)
                    }
                }
                val miniNowProgram = remember(playerUiState.activeStream, epgByChannel, miniNowMs) {
                    val epgId = playerUiState.activeStream?.epgChannelId
                    if (epgId.isNullOrBlank()) null
                    else epgByChannel[epgId]?.firstOrNull { miniNowMs in it.startEpochMs until it.endEpochMs }
                }

                DockedMiniPlayerBar(
                    player = activePlayer,
                    title = playerUiState.title,
                    subtitle = playerUiState.subtitle,
                    nowProgram = miniNowProgram,
                    nowMs = miniNowMs,
                    isPlaying = playerUiState.isPlaying,
                    onTogglePlayPause = { playerVm.togglePlayPause() },
                    onExpand = { playerVm.expandToFullscreen() },
                    onClose = { playerVm.closePrimaryPlayer() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Fullscreen Media3 Player Overlay (or PiP Mode)
            if (playerUiState.mode == PlayerPresentationMode.FULLSCREEN || isInPipMode) {
                PlayerOverlayScreen(
                    uiState = playerUiState,
                    player = activePlayer,
                    epgByChannel = epgByChannel,
                    isInPipMode = isInPipMode,
                    onBack = {
                        if (playerUiState.isLive) {
                            playerVm.dockMiniPlayer()
                        } else {
                            playerVm.closePrimaryPlayer()
                        }
                    },
                    onTogglePlayPause = { playerVm.togglePlayPause() },
                    onSeekBy = { playerVm.seekBy(it) },
                    onSeekTo = { playerVm.seekTo(it) },
                    onZapChannel = { playerVm.zapChannel(it) },
                    onSelectChannel = { ch ->
                        playerVm.playLiveChannel(ch, playerUiState.activePlaylistQueue, userAgent, openFullscreen = true)
                    },
                    onDirectJumpChannel = { num -> playerVm.jumpToChannelNumber(num) },
                    onPlayNextEpisode = { playerVm.playNextEpisode() },
                    onCycleAspectRatio = { playerVm.cycleAspectRatio() },
                    onToggleAudioOnly = { playerVm.toggleAudioOnly() },
                    onToggleSignalHud = { playerVm.toggleSignalHud() },
                    onSelectTrack = { playerVm.selectTrack(it) },
                    onResetTrackAuto = { type, disable -> playerVm.resetTrackAuto(type, disable) },
                    onSetSleepTimer = { playerVm.setSleepTimer(it) },
                    onRetryStream = { playerVm.retryActiveStream() },
                    onEnterPip = onRequestPip
                )
            }

            // Parental Lock PIN Gate Modal
            if (pinPromptState.isVisible) {
                PinVerificationDialog(
                    reason = pinPromptState.reason,
                    errorMessage = pinError,
                    onSubmit = { mainVm.submitPinCandidate(it) },
                    onDismiss = { mainVm.dismissPinPrompt() }
                )
            }
        }
    }
}
