package com.example.data.network

import com.example.data.local.CategoryEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class XtreamAccountInfo(
    val status: String,
    val expDateEpochSec: Long?,
    val maxConnections: String,
    val activeCons: String
)

data class VodRichMetadata(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val backdropUrl: String,
    val rating: String
)

class XtreamApiService(
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }
    }

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String
    ): XtreamAccountInfo = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password"
        val body = executeGetString(url, userAgent)
        val root = JSONObject(body)
        val userInfo = root.optJSONObject("user_info")
            ?: throw IOException("Invalid Xtream response: missing user_info")
        val auth = userInfo.optInt("auth", 0)
        val status = userInfo.optString("status", "Active")
        if (auth != 1 && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Xtream authentication failed (status: $status)")
        }
        val expRaw = userInfo.optString("exp_date", "").toLongOrNull()
        XtreamAccountInfo(
            status = status,
            expDateEpochSec = expRaw,
            maxConnections = userInfo.optString("max_connections", "1"),
            activeCons = userInfo.optString("active_cons", "0")
        )
    }

    suspend fun fetchCategories(
        profileId: Long,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        mediaType: MediaCategoryType
    ): List<CategoryEntity> = withContext(Dispatchers.IO) {
        val action = when (mediaType) {
            MediaCategoryType.LIVE -> "get_live_categories"
            MediaCategoryType.MOVIE -> "get_vod_categories"
            MediaCategoryType.SERIES -> "get_series_categories"
        }
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password&action=$action"
        val raw = executeGetString(url, userAgent)
        if (!raw.trimStart().startsWith("[")) return@withContext emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            val catId = obj.optString("category_id", idx.toString())
            CategoryEntity(
                profileId = profileId,
                mediaType = mediaType,
                categoryKey = "${mediaType.name}:$catId",
                name = obj.optString("category_name", "Category $catId").ifBlank { "Uncategorized" },
                sortOrder = idx
            )
        }
    }

    suspend fun fetchLiveStreams(
        profileId: Long,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String
    ): List<StreamItemEntity> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password&action=get_live_streams"
        val raw = executeGetString(url, userAgent)
        if (!raw.trimStart().startsWith("[")) return@withContext emptyList()
        val arr = JSONArray(raw)
        val now = System.currentTimeMillis()
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            val streamId = obj.optString("stream_id", "")
            val catId = obj.optString("category_id", "0")
            val tvArchive = obj.optInt("tv_archive", 0)
            val archiveDuration = obj.optInt("tv_archive_duration", 0)
            val ext = "m3u8"
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.LIVE,
                categoryKey = "${MediaCategoryType.LIVE.name}:$catId",
                remoteStreamId = streamId,
                title = obj.optString("name", "Channel $streamId"),
                streamUrl = "$base/live/$username/$password/$streamId.$ext",
                logoUrl = obj.optString("stream_icon", ""),
                epgChannelId = obj.optString("epg_channel_id", ""),
                channelNumber = obj.optInt("num", idx + 1),
                containerExt = ext,
                catchupType = if (tvArchive == 1) "xtream" else "",
                catchupDays = if (tvArchive == 1) archiveDuration.coerceAtLeast(1) else 0,
                addedAt = now
            )
        }
    }

    suspend fun fetchVodStreams(
        profileId: Long,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String
    ): List<StreamItemEntity> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password&action=get_vod_streams"
        val raw = executeGetString(url, userAgent)
        if (!raw.trimStart().startsWith("[")) return@withContext emptyList()
        val arr = JSONArray(raw)
        val now = System.currentTimeMillis()
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            val streamId = obj.optString("stream_id", "")
            val catId = obj.optString("category_id", "0")
            val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.MOVIE,
                categoryKey = "${MediaCategoryType.MOVIE.name}:$catId",
                remoteStreamId = streamId,
                title = obj.optString("name", "Movie $streamId"),
                streamUrl = "$base/movie/$username/$password/$streamId.$ext",
                logoUrl = obj.optString("stream_icon", ""),
                rating = obj.optString("rating", ""),
                containerExt = ext,
                addedAt = obj.optString("added", "").toLongOrNull()?.times(1000L) ?: now
            )
        }
    }

    suspend fun fetchSeriesCatalog(
        profileId: Long,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String
    ): List<StreamItemEntity> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password&action=get_series"
        val raw = executeGetString(url, userAgent)
        if (!raw.trimStart().startsWith("[")) return@withContext emptyList()
        val arr = JSONArray(raw)
        val now = System.currentTimeMillis()
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            val seriesId = obj.optString("series_id", "")
            val catId = obj.optString("category_id", "0")
            val backdropArr = obj.optJSONArray("backdrop_path")
            val backdrop = if (backdropArr != null && backdropArr.length() > 0) backdropArr.optString(0, "") else ""
            StreamItemEntity(
                profileId = profileId,
                mediaType = MediaCategoryType.SERIES,
                categoryKey = "${MediaCategoryType.SERIES.name}:$catId",
                remoteStreamId = seriesId,
                title = obj.optString("name", "Series $seriesId"),
                streamUrl = "",
                logoUrl = obj.optString("cover", ""),
                backdropUrl = backdrop,
                rating = obj.optString("rating", ""),
                releaseYear = obj.optString("releaseDate", "").take(4),
                genre = obj.optString("genre", ""),
                plot = obj.optString("plot", ""),
                cast = obj.optString("cast", ""),
                director = obj.optString("director", ""),
                addedAt = now
            )
        }
    }

    suspend fun fetchVodInfo(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        vodId: String
    ): VodRichMetadata? = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password&action=get_vod_info&vod_id=$vodId"
        val raw = runCatching { executeGetString(url, userAgent) }.getOrNull() ?: return@withContext null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
        val info = root.optJSONObject("info") ?: return@withContext null
        val backdropArr = info.optJSONArray("backdrop_path")
        val backdrop = if (backdropArr != null && backdropArr.length() > 0) backdropArr.optString(0, "") else ""
        VodRichMetadata(
            plot = info.optString("plot", ""),
            cast = info.optString("cast", ""),
            director = info.optString("director", ""),
            genre = info.optString("genre", ""),
            backdropUrl = backdrop,
            rating = info.optString("rating", "")
        )
    }

    suspend fun fetchSeriesEpisodes(
        profileId: Long,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        seriesId: String
    ): List<SeriesEpisodeEntity> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
        val url = "$base/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"
        val raw = executeGetString(url, userAgent)
        val root = JSONObject(raw)
        val episodesObj = root.optJSONObject("episodes") ?: return@withContext emptyList()
        val out = mutableListOf<SeriesEpisodeEntity>()
        val seasonKeys = episodesObj.keys()
        while (seasonKeys.hasNext()) {
            val sKey = seasonKeys.next()
            val seasonNum = sKey.toIntOrNull() ?: 1
            val epArr = episodesObj.optJSONArray(sKey) ?: continue
            for (i in 0 until epArr.length()) {
                val ep = epArr.getJSONObject(i)
                val epId = ep.optString("id", "")
                val epNum = ep.optInt("episode_num", i + 1)
                val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                val info = ep.optJSONObject("info")
                out.add(
                    SeriesEpisodeEntity(
                        profileId = profileId,
                        seriesRemoteId = seriesId,
                        seasonNumber = seasonNum,
                        episodeNumber = epNum,
                        episodeId = epId,
                        title = ep.optString("title", "Episode $epNum"),
                        streamUrl = "$base/series/$username/$password/$epId.$ext",
                        thumbnailUrl = info?.optString("movie_image", "").orEmpty(),
                        plot = info?.optString("plot", "").orEmpty(),
                        durationSec = info?.optInt("duration_secs", 0) ?: 0,
                        containerExt = ext
                    )
                )
            }
        }
        out
    }

    fun buildXmltvUrl(serverUrl: String, username: String, password: String): String {
        val base = normalizeServerUrl(serverUrl)
        return "$base/xmltv.php?username=$username&password=$password"
    }

    private fun executeGetString(url: String, userAgent: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent.ifBlank { "NovaStream/1.0 (Android)" })
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }
}
