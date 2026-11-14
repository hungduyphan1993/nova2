package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class DiagnosticReport(
    val targetUrl: String,
    val pingMs: Long?,
    val statusCode: Int?,
    val downloadMbps: Double?,
    val verdict: String,
    val error: String? = null
)

class StreamDiagnosticsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun runDiagnostics(targetUrl: String, userAgent: String): DiagnosticReport = withContext(Dispatchers.IO) {
        val cleanUrl = targetUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext DiagnosticReport(
                targetUrl = "",
                pingMs = null,
                statusCode = null,
                downloadMbps = null,
                verdict = "No target host configured",
                error = "Enter or select an active playlist first."
            )
        }

        val candidates = XtreamApiService.buildServerUrlCandidates(cleanUrl, preservePathAndQuery = true)
        val effectiveUa = userAgent.ifBlank { com.example.config.WhitelabelConfig.defaultUserAgent }

        try {
            var resolvedUrl = candidates.first()
            var statusCode = 0
            var pingMs = 1L
            var lastHeadErr: Exception? = null
            var headConnected = false

            for (candidate in candidates) {
                try {
                    val t0 = System.nanoTime()
                    val headReq = Request.Builder()
                        .url(candidate)
                        .header("User-Agent", effectiveUa)
                        .head()
                        .build()
                    client.newCall(headReq).execute().use { resp ->
                        statusCode = resp.code
                    }
                    pingMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
                    resolvedUrl = candidate
                    headConnected = true
                    break
                } catch (e: Exception) {
                    lastHeadErr = e
                }
            }

            if (!headConnected && lastHeadErr != null) {
                throw lastHeadErr
            }

            // Timed throughput test (read up to 1.5 MB)
            val getReq = Request.Builder()
                .url(resolvedUrl)
                .header("User-Agent", effectiveUa)
                .get()
                .build()
            var totalBytes = 0L
            val dlStart = System.nanoTime()
            client.newCall(getReq).execute().use { resp ->
                val stream = resp.body?.byteStream()
                val buf = ByteArray(16 * 1024)
                val maxRead = 1_500_000L
                if (stream != null) {
                    while (totalBytes < maxRead) {
                        val n = stream.read(buf)
                        if (n <= 0) break
                        totalBytes += n
                    }
                }
            }
            val elapsedSec = ((System.nanoTime() - dlStart) / 1_000_000_000.0).coerceAtLeast(0.05)
            val mbps = ((totalBytes * 8.0) / 1_000_000.0 / elapsedSec * 10.0).roundToInt() / 10.0

            val verdict = when {
                pingMs < 90 && mbps >= 10.0 -> "Optimal for 4K / Multi-Screen"
                pingMs < 200 && mbps >= 4.0 -> "Good for 1080p Live & VOD"
                statusCode in 200..399 -> "Reachable (Moderate bandwidth)"
                else -> "Server returned HTTP $statusCode"
            }

            DiagnosticReport(
                targetUrl = cleanUrl,
                pingMs = pingMs,
                statusCode = statusCode,
                downloadMbps = mbps,
                verdict = verdict
            )
        } catch (e: Exception) {
            DiagnosticReport(
                targetUrl = cleanUrl,
                pingMs = null,
                statusCode = null,
                downloadMbps = null,
                verdict = "Connection Failed",
                error = e.message ?: "Unable to reach host"
            )
        }
    }
}
