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
}
