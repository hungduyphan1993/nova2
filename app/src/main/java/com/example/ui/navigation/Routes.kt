package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object DashboardRoute

@Serializable
data class LiveTvRoute(
    val initialCategoryKey: String? = null,
    val initialStreamId: Long? = null,
    val favoritesOnly: Boolean = false,
    val catchupOnly: Boolean = false
)

@Serializable
object MoviesRoute

@Serializable
data class MovieDetailRoute(val streamDbId: Long)

@Serializable
object SeriesRoute

@Serializable
data class SeriesDetailRoute(val streamDbId: Long)

@Serializable
object EpgGuideRoute

@Serializable
data class CatchUpGuideRoute(val initialChannelDbId: Long? = null)

@Serializable
object MultiScreenRoute

@Serializable
object SearchRoute

@Serializable
object SettingsRoute

@Serializable
object DiagnosticsRoute
