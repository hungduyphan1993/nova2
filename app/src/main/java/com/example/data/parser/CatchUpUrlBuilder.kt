package com.example.data.parser

import com.example.data.local.EpgProgramEntity
import com.example.data.local.PlaylistProfileEntity
import com.example.data.local.PlaylistSourceType
import com.example.data.local.StreamItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CatchUpUrlBuilder {
    fun buildArchiveUrl(
        profile: PlaylistProfileEntity,
        stream: StreamItemEntity,
        program: EpgProgramEntity
    ): String {
        val startSec = program.startEpochMs / 1000L
        val endSec = program.endEpochMs / 1000L
        val durationSec = (endSec - startSec).coerceAtLeast(60L)
        val durationMin = (durationSec / 60L).coerceAtLeast(1L)

        // 1. Explicit M3U catchup-source template
        if (stream.catchupSource.isNotBlank()) {
            return interpolateTemplate(stream.catchupSource, startSec, endSec, durationSec)
        }

        // 2. Xtream Codes timeshift endpoint
        if (stream.catchupType.equals("xtream", ignoreCase = true) ||
            (profile.sourceType == PlaylistSourceType.XTREAM && stream.catchupDays > 0)
        ) {
            val base = profile.serverUrl.trimEnd('/')
            val startFmt = formatXtreamStart(program.startEpochMs)
            return "$base/streaming/timeshift.php?username=${profile.username}&password=${profile.password}&stream=${stream.remoteStreamId}&start=$startFmt&duration=$durationMin"
        }

        // 3. Flussonic style
        if (stream.catchupType.equals("flussonic", ignoreCase = true)) {
            val baseUrl = stream.streamUrl.substringBeforeLast('/')
            return "$baseUrl/archive-$startSec-$durationSec.m3u8"
        }

        // 4. Append / default query parameters
        if (stream.catchupType.equals("append", ignoreCase = true) ||
            stream.catchupType.equals("shift", ignoreCase = true)
        ) {
            val sep = if ('?' in stream.streamUrl) "&" else "?"
            return "${stream.streamUrl}${sep}utc=$startSec&lutc=${System.currentTimeMillis() / 1000L}"
        }

        return stream.streamUrl
    }

    private fun interpolateTemplate(
        template: String,
        startSec: Long,
        endSec: Long,
        durationSec: Long
    ): String {
        val date = Date(startSec * 1000L)
        fun fmt(pattern: String): String = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)

        return template
            .replace("{utc}", startSec.toString())
            .replace("{start}", startSec.toString())
            .replace("{utcend}", endSec.toString())
            .replace("{end}", endSec.toString())
            .replace("{duration}", durationSec.toString())
            .replace("{Y}", fmt("yyyy"))
            .replace("{m}", fmt("MM"))
            .replace("{d}", fmt("dd"))
            .replace("{H}", fmt("HH"))
            .replace("{M}", fmt("mm"))
            .replace("{S}", fmt("ss"))
    }

    private fun formatXtreamStart(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }
}
