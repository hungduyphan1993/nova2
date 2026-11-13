package com.example.data.parser

import android.util.Xml
import com.example.data.local.EpgProgramEntity
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

object XmltvParser {
    private val formatsWithZone = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
        SimpleDateFormat("yyyyMMddHHmm Z", Locale.US)
    )
    private val formatNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun parseStream(
        profileId: Long,
        rawStream: InputStream,
        epgOffsetMinutes: Int = 0,
        onBatchReady: suspend (List<EpgProgramEntity>) -> Unit
    ) {
        val decompressed = unwrapGzipIfNeeded(rawStream)
        val offsetMs = epgOffsetMinutes * 60_000L
        val batch = ArrayList<EpgProgramEntity>(500)

        decompressed.use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(BufferedInputStream(input, 64 * 1024), "UTF-8")

            var eventType = parser.eventType
            var channelId = ""
            var startMs = 0L
            var stopMs = 0L
            var title = ""
            var desc = ""
            var inProgramme = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "programme" -> {
                                inProgramme = true
                                channelId = parser.getAttributeValue(null, "channel").orEmpty()
                                startMs = parseXmltvDate(parser.getAttributeValue(null, "start")) + offsetMs
                                stopMs = parseXmltvDate(parser.getAttributeValue(null, "stop")) + offsetMs
                                title = ""
                                desc = ""
                            }
                            "title" -> if (inProgramme) {
                                title = safeNextText(parser).trim()
                            }
                            "desc" -> if (inProgramme) {
                                desc = safeNextText(parser).trim()
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("programme", ignoreCase = true) && inProgramme) {
                            inProgramme = false
                            if (channelId.isNotBlank() && title.isNotBlank() && stopMs > startMs) {
                                batch.add(
                                    EpgProgramEntity(
                                        profileId = profileId,
                                        epgChannelId = channelId,
                                        title = title,
                                        description = desc,
                                        startEpochMs = startMs,
                                        endEpochMs = stopMs
                                    )
                                )
                                if (batch.size >= 500) {
                                    onBatchReady(batch.toList())
                                    batch.clear()
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        if (batch.isNotEmpty()) {
            onBatchReady(batch.toList())
        }
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return runCatching {
            if (parser.next() == XmlPullParser.TEXT) parser.text ?: "" else ""
        }.getOrDefault("")
    }

    private fun unwrapGzipIfNeeded(input: InputStream): InputStream {
        val pb = PushbackInputStream(input, 2)
        val signature = ByteArray(2)
        val len = pb.read(signature)
        if (len > 0) {
            pb.unread(signature, 0, len)
        }
        return if (len == 2 && signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()) {
            GZIPInputStream(pb)
        } else {
            pb
        }
    }

    fun parseXmltvDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        for (fmt in formatsWithZone) {
            synchronized(fmt) {
                runCatching { return fmt.parse(trimmed)?.time ?: 0L }
            }
        }
        val clean = trimmed.take(14)
        synchronized(formatNoZone) {
            return runCatching { formatNoZone.parse(clean)?.time ?: 0L }.getOrDefault(0L)
        }
    }
}
