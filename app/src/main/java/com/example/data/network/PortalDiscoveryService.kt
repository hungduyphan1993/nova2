package com.example.data.network

import com.example.config.WhitelabelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PortalDiscoveryService(
    private val okHttpClient: OkHttpClient = defaultDiscoveryClient()
) {
    companion object {
        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val directUrlKeys = listOf(
            "server_url",
            "serverUrl",
            "portal_url",
            "portalUrl",
            "xtream_url",
            "xtreamUrl",
            "dns",
            "url",
            "server",
            "portal",
            "host",
            "domain"
        )

        private val nestedContainerKeys = listOf(
            "data",
            "result",
            "config",
            "response",
            "payload",
            "servers",
            "dns_list",
            "dnsList",
            "portals",
            "urls",
            "hosts"
        )

        fun defaultDiscoveryClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun buildDiscoveryEndpointCandidates(apiServerRaw: String, username: String): List<String> {
            val trimmed = apiServerRaw.trim()
            if (trimmed.isEmpty()) return emptyList()
            val encodedUser = runCatching {
                URLEncoder.encode(username.trim(), "UTF-8")
            }.getOrDefault(username.trim())
            val templated = trimmed
                .replace("{username}", encodedUser)
                .replace("{user}", encodedUser)
            return XtreamApiService.buildServerUrlCandidates(templated, preservePathAndQuery = true)
        }

        fun parsePortalUrlsFromBody(rawBody: String): List<String> {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return emptyList()

            val collected = LinkedHashSet<String>()
            extractFromTextOrJson(trimmed, collected, depth = 0)
            return collected.toList()
        }

        private fun extractFromTextOrJson(raw: String, out: MutableSet<String>, depth: Int) {
            if (depth > 4) return
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return

            // 1. Check if raw is JSON Object or JSON Array
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val parsed = runCatching { lenientJson.parseToJsonElement(trimmed) }.getOrNull()
                if (parsed != null) {
                    extractFromJsonElement(parsed, out, depth + 1)
                    if (out.isNotEmpty()) return
                }
            }

            // 2. Check if raw is Base64-encoded JSON or URL
            val decoded = XtreamApiService.decodeMaybeBase64(trimmed)
            if (decoded != trimmed && decoded.isNotBlank()) {
                extractFromTextOrJson(decoded, out, depth + 1)
                if (out.isNotEmpty()) return
            }

            // 3. Plain-text single or multi-line portal host / URL list
            if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)
            ) {
                return
            }

            trimmed.split('\n', '\r', ',', ';')
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { token ->
                    sanitizeCandidatePortal(token)?.let { out.add(it) }
                }
        }

        private fun extractFromJsonElement(element: JsonElement, out: MutableSet<String>, depth: Int) {
            if (depth > 6) return
            when (element) {
                is JsonPrimitive -> {
                    val content = element.content.trim()
                    if (content.isNotEmpty()) {
                        val maybeDecoded = XtreamApiService.decodeMaybeBase64(content)
                        if (maybeDecoded != content && (maybeDecoded.startsWith("{") || maybeDecoded.startsWith("[") || looksLikePortalHostOrUrl(maybeDecoded))) {
                            extractFromTextOrJson(maybeDecoded, out, depth + 1)
                        } else {
                            sanitizeCandidatePortal(content)?.let { out.add(it) }
                        }
                    }
                }
                is JsonArray -> {
                    element.forEach { child -> extractFromJsonElement(child, out, depth + 1) }
                }
                is JsonObject -> {
                    val prioritized = (directUrlKeys + nestedContainerKeys).toSet()
                    directUrlKeys.forEach { key ->
                        element[key]?.let { value ->
                            extractFromJsonElement(value, out, depth + 1)
                        }
                    }
                    nestedContainerKeys.forEach { key ->
                        element[key]?.let { nested ->
                            extractFromJsonElement(nested, out, depth + 1)
                        }
                    }
                    if (out.isEmpty()) {
                        element.entries.forEach { (key, value) ->
                            if (key !in prioritized) {
                                extractFromJsonElement(value, out, depth + 1)
                            }
                        }
                    }
                }
            }
        }

        private fun sanitizeCandidatePortal(rawToken: String): String? {
            val cleaned = rawToken.trim().trimEnd('/')
            if (cleaned.isEmpty() || ' ' in cleaned) return null
            return if (looksLikePortalHostOrUrl(cleaned)) cleaned else null
        }

        private fun looksLikePortalHostOrUrl(value: String): Boolean {
            val lower = value.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                val hostPart = value.substringAfter("://").substringBefore('/')
                return hostPart.contains('.') || hostPart.contains(':') || hostPart == "localhost"
            }
            if (lower.contains("://")) return false
            val hostPort = value.substringBefore('/')
            val hostOnly = hostPort.substringBefore(':')
            val hasValidChars = hostOnly.all { it.isLetterOrDigit() || it == '.' || it == '-' }
            return hostOnly.isNotBlank() && hasValidChars && (hostOnly.contains('.') || hostOnly == "localhost")
        }
    }

    suspend fun discoverServerUrls(
        apiServerRaw: String,
        username: String,
        userAgent: String
    ): List<String> = withContext(Dispatchers.IO) {
        val endpoints = buildDiscoveryEndpointCandidates(apiServerRaw, username)
        if (endpoints.isEmpty()) {
            throw IOException("Discovery API server URL is empty")
        }

        val effectiveUa = userAgent.ifBlank { WhitelabelConfig.defaultUserAgent }
        var lastError: Throwable? = null

        for (endpoint in endpoints) {
            try {
                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", effectiveUa)
                    .header("Accept", "application/json, text/plain, */*")
                if (username.isNotBlank()) {
                    requestBuilder.header("X-Xtream-Username", username.trim())
                }
                okHttpClient.newCall(requestBuilder.get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code} from discovery API ($endpoint)")
                    }
                    val body = resp.body?.string().orEmpty()
                    val urls = parsePortalUrlsFromBody(body)
                    if (urls.isNotEmpty()) {
                        return@withContext urls
                    }
                    throw IOException("Discovery API returned no valid portal server URL")
                }
            } catch (err: Throwable) {
                lastError = err
            }
        }

        throw IOException(
            lastError?.message ?: "Unable to fetch portal URL from configured API server",
            lastError
        )
    }
}
