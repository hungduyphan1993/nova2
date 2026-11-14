package com.example.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.data.local.PlaybackEngine
import kotlinx.serialization.json.Json

object ExternalPlayerHelper {

    fun isEngineInstalled(context: Context, engine: PlaybackEngine): Boolean {
        if (engine.isBuiltIn || engine == PlaybackEngine.EXTERNAL_CHOOSER) return true
        return resolveInstalledPackage(context, engine) != null
    }

    fun resolveInstalledPackage(context: Context, engine: PlaybackEngine): String? {
        val pm = context.packageManager
        return engine.packageCandidates.firstOrNull { pkg ->
            isPackagePresent(pm, pkg)
        }
    }

    private fun isPackagePresent(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun launchExternalPlayer(context: Context, url: String, title: String) {
        launchWithEngine(
            context = context,
            engine = PlaybackEngine.EXTERNAL_CHOOSER,
            url = url,
            title = title
        )
    }

    fun launchWithEngine(
        context: Context,
        engine: PlaybackEngine,
        url: String,
        title: String,
        userAgent: String = "",
        httpHeadersJson: String = "",
        positionMs: Long = 0L
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            Toast.makeText(context, "Stream URL is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val headersMap = buildHeadersMap(httpHeadersJson, userAgent)
        val headerPairs = headersMap.entries.flatMap { listOf(it.key, it.value) }.toTypedArray()
        val headerLines = headersMap.entries.map { "${it.key}: ${it.value}" }.toTypedArray()

        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(cleanUrl), "video/*")
            putExtra("title", title)
            putExtra("forcename", title)
            if (headerPairs.isNotEmpty()) {
                // MX Player & MPV compatible HTTP header extras
                putExtra("headers", if (engine == PlaybackEngine.EXTERNAL_MPV) headerLines else headerPairs)
            }
            if (positionMs > 0L) {
                putExtra("position", positionMs.toInt())
                putExtra("from_start", false)
            } else {
                putExtra("from_start", true)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (engine != PlaybackEngine.EXTERNAL_CHOOSER && !engine.isBuiltIn) {
            val targetPkg = resolveInstalledPackage(context, engine)
            if (targetPkg != null) {
                val targetedIntent = Intent(baseIntent).apply {
                    setPackage(targetPkg)
                }
                val launched = runCatching {
                    context.startActivity(targetedIntent)
                }.isSuccess
                if (launched) return
            } else {
                Toast.makeText(
                    context,
                    "${engine.label} is not installed — opening player chooser",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val chooser = Intent.createChooser(baseIntent, "Open stream with...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "No external video player installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildHeadersMap(rawJson: String, fallbackUserAgent: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        if (fallbackUserAgent.isNotBlank()) {
            map["User-Agent"] = fallbackUserAgent
        }
        if (rawJson.isNotBlank()) {
            runCatching {
                val parsed = Json.decodeFromString<Map<String, String>>(rawJson)
                map.putAll(parsed)
            }
        }
        return map
    }
}
