package com.example

import com.example.data.local.EpgProgramEntity
import com.example.data.local.MediaCategoryType
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.PlaylistSourceType
import com.example.data.local.StreamItemEntity
import com.example.data.parser.CatchUpUrlBuilder
import com.example.data.parser.M3uParser
import com.example.data.parser.XmltvParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class NovaParsersTest {

    @Test
    fun m3uParserExtractsHeadersDrmCatchupAndClassifiesTypes() = runTest {
        val sampleM3u = """
            #EXTM3U url-tvg="https://epg.example.com/guide.xml.gz"
            #EXTINF:-1 tvg-id="news.1" tvg-chno="101" group-title="World News" catchup="xtream" catchup-days="7",News One HD
            #EXTVLCOPT:http-user-agent=CustomAgent/2.0
            #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
            #KODIPROP:inputstream.adaptive.license_key=00112233445566778899aabbccddeeff:ffeeddccbbaa99887766554433221100
            http://stream.example.com/live/101.m3u8
            #EXTINF:-1 group-title="Movies",Action Film (2025)
            http://stream.example.com/movie/user/pass/555.mp4
            #EXTINF:-1 group-title="Drama",Cosmic Odyssey S01E02 The Launch
            http://stream.example.com/series/user/pass/902.mkv
        """.trimIndent()

        val allStreams = mutableListOf<StreamItemEntity>()
        var episodeCount = 0
        val epgUrl = M3uParser.parseStream(
            profileId = 1L,
            inputStream = ByteArrayInputStream(sampleM3u.toByteArray())
        ) { _, streams, episodes ->
            allStreams.addAll(streams)
            episodeCount += episodes.size
        }

        assertEquals("https://epg.example.com/guide.xml.gz", epgUrl)
        assertEquals(3, allStreams.size)
        assertEquals(1, episodeCount)

        val live = allStreams[0]
        assertEquals(MediaCategoryType.LIVE, live.mediaType)
        assertEquals("news.1", live.epgChannelId)
        assertEquals(101, live.channelNumber)
        assertEquals("clearkey", live.drmScheme)
        assertTrue(live.httpHeadersJson.contains("CustomAgent/2.0"))
        assertEquals(7, live.catchupDays)

        val movie = allStreams[1]
        assertEquals(MediaCategoryType.MOVIE, movie.mediaType)

        val series = allStreams[2]
        assertEquals(MediaCategoryType.SERIES, series.mediaType)
        assertEquals("Cosmic Odyssey", series.title)
    }

    @Test
    fun catchUpBuilderFormatsXtreamAndTemplateUrls() {
        val profile = PlaylistProfileEntity(
            id = 1L,
            name = "Test",
            sourceType = PlaylistSourceType.XTREAM,
            serverUrl = "http://panel.example.com:8080",
            username = "alice",
            password = "secret"
        )
        val streamXtream = StreamItemEntity(
            profileId = 1L,
            mediaType = MediaCategoryType.LIVE,
            categoryKey = "LIVE:1",
            remoteStreamId = "442",
            title = "Sport 1",
            streamUrl = "http://panel.example.com:8080/live/alice/secret/442.m3u8",
            catchupType = "xtream",
            catchupDays = 5
        )
        val program = EpgProgramEntity(
            profileId = 1L,
            epgChannelId = "sport.1",
            title = "Finals",
            startEpochMs = 1_700_000_000_000L,
            endEpochMs = 1_700_003_600_000L
        )
        val xtreamArchive = CatchUpUrlBuilder.buildArchiveUrl(profile, streamXtream, program)
        assertTrue(xtreamArchive.contains("/streaming/timeshift.php"))
        assertTrue(xtreamArchive.contains("stream=442"))
        assertTrue(xtreamArchive.contains("duration=60"))

        val streamTemplate = streamXtream.copy(
            catchupType = "default",
            catchupSource = "http://cdn.example.com/archive/{utc}/{duration}.m3u8"
        )
        val tplArchive = CatchUpUrlBuilder.buildArchiveUrl(profile, streamTemplate, program)
        assertEquals("http://cdn.example.com/archive/1700000000/3600.m3u8", tplArchive)
    }

    @Test
    fun xmltvDateParserAppliesOffsetCorrectly() {
        val baseMs = XmltvParser.parseXmltvDate("20261113120000 +0000")
        val shiftedMs = XmltvParser.parseXmltvDate("20261113140000 +0200")
        assertEquals(baseMs, shiftedMs)
        assertTrue(baseMs > 0L)
    }

    @Test
    fun playbackEngineResolvesStoredNamesAndCatalogCategories() {
        assertEquals(
            com.example.data.local.PlaybackEngine.DEFAULT,
            com.example.data.local.PlaybackEngine.fromStoredName(null)
        )
        assertEquals(
            com.example.data.local.PlaybackEngine.DEFAULT,
            com.example.data.local.PlaybackEngine.fromStoredName("")
        )
        assertEquals(
            com.example.data.local.PlaybackEngine.LIBVLC,
            com.example.data.local.PlaybackEngine.fromStoredName("LIBVLC")
        )
        assertEquals(
            com.example.data.local.PlaybackEngine.LIBMPV,
            com.example.data.local.PlaybackEngine.fromStoredName("LIBMPV")
        )
        assertEquals(
            com.example.data.local.PlaybackEngine.EXTERNAL_MPV,
            com.example.data.local.PlaybackEngine.fromStoredName("EXTERNAL_MPV")
        )
        assertEquals(
            listOf(
                com.example.data.local.PlaybackEngine.EXOPLAYER,
                com.example.data.local.PlaybackEngine.LIBVLC,
                com.example.data.local.PlaybackEngine.LIBMPV
            ),
            com.example.data.local.PlaybackEngine.builtInEngines
        )
        assertEquals("mediacodec-copy", com.example.data.local.MpvHardwareMode.AUTO_SAFE.mpvValue)
        assertEquals("mediacodec", com.example.data.local.MpvHardwareMode.ZERO_COPY.mpvValue)
        assertEquals("no", com.example.data.local.MpvHardwareMode.SOFTWARE.mpvValue)
        assertTrue(com.example.data.local.PlaybackEngine.externalEngines.contains(com.example.data.local.PlaybackEngine.EXTERNAL_MPV))
        assertTrue(com.example.data.local.PlaybackEngine.externalEngines.contains(com.example.data.local.PlaybackEngine.EXTERNAL_VLC))
        assertTrue(com.example.data.local.PlaybackEngine.selectableGlobalEngines.none { it == com.example.data.local.PlaybackEngine.DEFAULT })
    }

    @Test
    fun xtreamSimpleDataTableDecodesBase64AndFallbackTimestamps() {
        // "Evening News" = RXZlbmluZyBOZXdz, "Live recap" = TGl2ZSByZWNhcA==
        val sampleJson = """
            {
              "epg_listings": [
                {
                  "id": "10",
                  "title": "RXZlbmluZyBOZXdz",
                  "description": "TGl2ZSByZWNhcA==",
                  "start_timestamp": "1700000000",
                  "stop_timestamp": "1700003600",
                  "has_archive": 1
                },
                {
                  "id": "11",
                  "title": "Plain Text Show",
                  "description": "Direct description",
                  "start": "2023-11-14 23:13:20",
                  "end": "2023-11-15 00:13:20",
                  "has_archive": 1
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.data.network.XtreamApiService.parseSimpleDataTableJson(
            rawJson = sampleJson,
            profileId = 5L,
            targetChannelKey = "news.1",
            offsetMinutes = 0
        )

        assertEquals(2, parsed.size)
        assertEquals("Evening News", parsed[0].title)
        assertEquals("Live recap", parsed[0].description)
        assertEquals(1_700_000_000_000L, parsed[0].startEpochMs)
        assertEquals(1_700_003_600_000L, parsed[0].endEpochMs)
        assertEquals("Plain Text Show", parsed[1].title)
    }

    @Test
    fun catchUpHourlyTimeshiftBlocksAndEffectiveEpgKeyWork() {
        val channelNoXmltvId = com.example.data.local.StreamItemEntity(
            id = 42L,
            profileId = 1L,
            mediaType = com.example.data.local.MediaCategoryType.LIVE,
            categoryKey = "sports",
            remoteStreamId = "8899",
            title = "Archive Sport HD",
            streamUrl = "http://srv/live/u/p/8899.ts",
            epgChannelId = "",
            catchupDays = 5
        )
        assertTrue(channelNoXmltvId.supportsCatchUp)
        assertEquals("stream_8899", channelNoXmltvId.effectiveEpgKey)

        val dayStart = 1_700_006_400_000L // midnight UTC
        val dayEnd = dayStart + 86_400_000L
        val nowMs = dayStart + 4L * 3_600_000L + 15L * 60_000L // 04:15
        val blocks = com.example.ui.screens.buildHourlyTimeshiftBlocks(
            profileId = 1L,
            epgKey = channelNoXmltvId.effectiveEpgKey,
            dayStartMs = dayStart,
            dayEndMs = dayEnd,
            nowMs = nowMs
        )
        // Slots covering 00:00-01:00, 01:00-02:00, 02:00-03:00, 03:00-04:00, and current 04:00-05:00
        assertEquals(5, blocks.size)
        assertEquals(dayStart, blocks.first().startEpochMs)
        assertEquals(dayStart + 5L * 3_600_000L, blocks.last().endEpochMs)
    }

    @Test
    fun adultCategoryClassifierAndFormattingSettingsWork() {
        val adultNames = listOf(
            "Adult HD",
            "VIP | 18+ Channels",
            "[+18] Late Night",
            "XXX Movies",
            "UK - For Adults Only",
            "NSFW VOD",
            "Erotic Cinema"
        )
        adultNames.forEach { name ->
            assertTrue("Expected adult match for: $name", com.example.data.local.AdultCategoryClassifier.isAdultCategoryName(name))
        }

        val regularNames = listOf(
            "World News & Live",
            "Sports & Action",
            "Kids & Family",
            "Sci-Fi 4K UHD",
            "Documentary History"
        )
        regularNames.forEach { name ->
            org.junit.Assert.assertFalse("Unexpected adult match for: $name", com.example.data.local.AdultCategoryClassifier.isAdultCategoryName(name))
        }

        val sampleEpoch = 1_700_050_000_000L
        com.example.ui.components.NovaFormatters.use24HourClock = true
        val c24 = com.example.ui.components.NovaFormatters.formatClock(sampleEpoch)
        com.example.ui.components.NovaFormatters.use24HourClock = false
        val c12 = com.example.ui.components.NovaFormatters.formatClock(sampleEpoch)
        com.example.ui.components.NovaFormatters.use24HourClock = true
        assertTrue(c24.isNotBlank())
        assertTrue(c12.contains("AM", ignoreCase = true) || c12.contains("PM", ignoreCase = true))

        assertEquals(1.25f, com.example.data.local.SubtitleSizePreset.LARGE.scaleFactor, 0.001f)
        assertEquals("Off by Default", com.example.data.local.NovaLanguageOptions.displayLabel("off", isSubtitle = true))
    }
}
