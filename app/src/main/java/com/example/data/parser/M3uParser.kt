package com.example.data.parser

import com.example.data.local.CategoryEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.SeriesEpisodeEntity
import com.example.data.local.StreamItemEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class M3uParseResult(
    val detectedEpgUrl: String?,
    val categories: List<CategoryEntity>,
    val streams: List<StreamItemEntity>,
    val seriesEpisodes: List<SeriesEpisodeEntity>
)

object M3uParser {
    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*"([^"]*)"""")
    private val seriesRegex = Regex("""^(.*?)\s+[Ss](\d{1,2})[Ee](\d{1,2})(?:\s*[-:]?\s*(.*))?$""")
    private val vodExtSet = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")

    suspend fun parseStream(
        profileId: Long,
        inputStream: InputStream,
        onBatchReady: suspend (
            categories: List<CategoryEntity>,
            streams: List<StreamItemEntity>,
            episodes: List<SeriesEpisodeEntity>
        ) -> Unit
    ): String? {
        var detectedEpgUrl: String? = null
        val seenCategories = linkedMapOf<String, CategoryEntity>()
        val streamBuffer = ArrayList<StreamItemEntity>(500)
        val episodeBuffer = ArrayList<SeriesEpisodeEntity>(200)
        val seenSeriesKeys = hashSetOf<String>()

        var currentExtInf: ExtInfMeta? = null
        var currentGroupOverride: String? = null
        val currentHeaders = linkedMapOf<String, String>()
        var currentDrmScheme = ""
        var currentDrmKey = ""
        var channelIndex = 1

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 64 * 1024).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    line = reader.readLine()
                    continue
                }

                when {
                    trimmed.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val attrs = parseAttributes(trimmed)
                        detectedEpgUrl = attrs["url-tvg"]
                            ?: attrs["x-tvg-url"]
                            ?: attrs["tvg-url"]
                    }

                    trimmed.startsWith("#EXTINF:", ignoreCase = true) -> {
                        currentExtInf = parseExtInf(trimmed)
                        currentGroupOverride = null
                        currentHeaders.clear()
                        currentDrmScheme = ""
                        currentDrmKey = ""
                    }

                    trimmed.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        currentGroupOverride = trimmed.substringAfter(":").trim()
                    }

                    trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val opt = trimmed.substringAfter(":")
                        when {
                            opt.startsWith("http-user-agent=", ignoreCase = true) -> {
                                currentHeaders["User-Agent"] = opt.substringAfter("=").trim()
                            }
                            opt.startsWith("http-referrer=", ignoreCase = true) -> {
                                currentHeaders["Referer"] = opt.substringAfter("=").trim()
                            }
                        }
                    }

                    trimmed.startsWith("#KODIPROP:", ignoreCase = true) -> {
                        val prop = trimmed.substringAfter(":")
                        when {
                            prop.startsWith("inputstream.adaptive.license_type=", ignoreCase = true) -> {
                                val raw = prop.substringAfter("=").trim().lowercase()
                                currentDrmScheme = when {
                                    "clearkey" in raw -> "clearkey"
                                    "widevine" in raw -> "widevine"
                                    else -> raw
                                }
                            }
                            prop.startsWith("inputstream.adaptive.license_key=", ignoreCase = true) -> {
                                currentDrmKey = prop.substringAfter("=").trim()
                            }
                            prop.startsWith("inputstream.adaptive.stream_headers=", ignoreCase = true) -> {
                                val rawHeaders = prop.substringAfter("=").trim()
                                rawHeaders.split("&").forEach { pair ->
                                    val k = pair.substringBefore("=").trim()
                                    val v = pair.substringAfter("=", "").trim()
                                    if (k.isNotEmpty() && v.isNotEmpty()) {
                                        currentHeaders[k] = v
                                    }
                                }
                            }
                        }
                    }

                    !trimmed.startsWith("#") -> {
                        val url = trimmed
                        val meta = currentExtInf ?: ExtInfMeta(title = url.substringAfterLast("/").ifBlank { "Channel $channelIndex" })
                        val groupName = (currentGroupOverride ?: meta.groupTitle).ifBlank { "General" }
                        val mediaType = classifyMediaType(url, groupName, meta.title)
                        val categoryKey = "${mediaType.name}:$groupName"

                        if (!seenCategories.containsKey(categoryKey)) {
                            seenCategories[categoryKey] = CategoryEntity(
                                profileId = profileId,
                                mediaType = mediaType,
                                categoryKey = categoryKey,
                                name = groupName,
                                sortOrder = seenCategories.size
                            )
                        }

                        val headersJson = if (currentHeaders.isNotEmpty()) {
                            Json.encodeToString(currentHeaders)
                        } else {
                            ""
                        }

                        val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase()
                        val chNum = meta.channelNumber ?: channelIndex++

                        if (mediaType == MediaCategoryType.SERIES) {
                            val match = seriesRegex.find(meta.title)
                            val showTitle = match?.groupValues?.get(1)?.trim()?.ifBlank { meta.title } ?: meta.title
                            val seasonNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1
                            val episodeNum = match?.groupValues?.get(3)?.toIntOrNull() ?: 1
                            val epTitle = match?.groupValues?.get(4)?.trim()?.ifBlank { "Episode $episodeNum" } ?: meta.title
                            val seriesRemoteId = "m3u_series_${showTitle.lowercase().hashCode()}"

                            if (seenSeriesKeys.add(seriesRemoteId)) {
                                streamBuffer.add(
                                    StreamItemEntity(
                                        profileId = profileId,
                                        mediaType = MediaCategoryType.SERIES,
                                        categoryKey = categoryKey,
                                        remoteStreamId = seriesRemoteId,
                                        title = showTitle,
                                        streamUrl = url,
                                        logoUrl = meta.tvgLogo,
                                        backdropUrl = meta.tvgLogo,
                                        channelNumber = chNum,
                                        containerExt = ext,
                                        httpHeadersJson = headersJson,
                                        addedAt = System.currentTimeMillis()
                                    )
                                )
                            }

                            episodeBuffer.add(
                                SeriesEpisodeEntity(
                                    profileId = profileId,
                                    seriesRemoteId = seriesRemoteId,
                                    seasonNumber = seasonNum,
                                    episodeNumber = episodeNum,
                                    episodeId = "${seriesRemoteId}_s${seasonNum}_e${episodeNum}",
                                    title = epTitle,
                                    streamUrl = url,
                                    thumbnailUrl = meta.tvgLogo,
                                    containerExt = ext.ifBlank { "mp4" },
                                    httpHeadersJson = headersJson
                                )
                            )
                        } else {
                            streamBuffer.add(
                                StreamItemEntity(
                                    profileId = profileId,
                                    mediaType = mediaType,
                                    categoryKey = categoryKey,
                                    remoteStreamId = "m3u_${url.hashCode()}_$chNum",
                                    title = meta.title,
                                    streamUrl = url,
                                    logoUrl = meta.tvgLogo,
                                    epgChannelId = meta.tvgId.ifBlank { meta.tvgName },
                                    channelNumber = chNum,
                                    containerExt = ext,
                                    catchupType = meta.catchupType,
                                    catchupDays = meta.catchupDays,
                                    catchupSource = meta.catchupSource,
                                    httpHeadersJson = headersJson,
                                    drmScheme = currentDrmScheme,
                                    drmLicenseKey = currentDrmKey,
                                    addedAt = System.currentTimeMillis()
                                )
                            )
                        }

                        if (streamBuffer.size >= 500) {
                            onBatchReady(seenCategories.values.toList(), streamBuffer.toList(), episodeBuffer.toList())
                            streamBuffer.clear()
                            episodeBuffer.clear()
                        }

                        currentExtInf = null
                        currentGroupOverride = null
                        currentHeaders.clear()
                        currentDrmScheme = ""
                        currentDrmKey = ""
                    }
                }
                line = reader.readLine()
            }
        }

        if (streamBuffer.isNotEmpty() || episodeBuffer.isNotEmpty() || seenCategories.isNotEmpty()) {
            onBatchReady(seenCategories.values.toList(), streamBuffer.toList(), episodeBuffer.toList())
        }

        return detectedEpgUrl
    }

    private fun classifyMediaType(url: String, group: String, title: String): MediaCategoryType {
        val lowerUrl = url.lowercase()
        val lowerGroup = group.lowercase()
        if ("/series/" in lowerUrl || seriesRegex.matches(title) || "series" in lowerGroup || "tv shows" in lowerGroup) {
            return MediaCategoryType.SERIES
        }
        val ext = lowerUrl.substringAfterLast('.', "").substringBefore('?')
        if ("/movie/" in lowerUrl || "/vod/" in lowerUrl || ext in vodExtSet || "movies" in lowerGroup || "vod" in lowerGroup || "cinema" in lowerGroup) {
            return MediaCategoryType.MOVIE
        }
        return MediaCategoryType.LIVE
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val map = hashMapOf<String, String>()
        attrRegex.findAll(line).forEach { match ->
            map[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        return map
    }

    private fun parseExtInf(line: String): ExtInfMeta {
        val lastComma = line.lastIndexOf(',')
        val headerPart = if (lastComma >= 0) line.substring(0, lastComma) else line
        val title = if (lastComma >= 0) line.substring(lastComma + 1).trim() else "Untitled Stream"
        val attrs = parseAttributes(headerPart)
        return ExtInfMeta(
            tvgId = attrs["tvg-id"].orEmpty(),
            tvgName = attrs["tvg-name"].orEmpty(),
            tvgLogo = attrs["tvg-logo"].orEmpty(),
            groupTitle = attrs["group-title"].orEmpty(),
            channelNumber = attrs["tvg-chno"]?.toIntOrNull(),
            catchupType = attrs["catchup"].orEmpty(),
            catchupDays = attrs["catchup-days"]?.toIntOrNull()
                ?: attrs["timeshift"]?.toIntOrNull()
                ?: 0,
            catchupSource = attrs["catchup-source"].orEmpty(),
            title = title.ifBlank { attrs["tvg-name"].orEmpty().ifBlank { "Untitled Channel" } }
        )
    }

    private data class ExtInfMeta(
        val tvgId: String = "",
        val tvgName: String = "",
        val tvgLogo: String = "",
        val groupTitle: String = "",
        val channelNumber: Int? = null,
        val catchupType: String = "",
        val catchupDays: Int = 0,
        val catchupSource: String = "",
        val title: String
    )
}
