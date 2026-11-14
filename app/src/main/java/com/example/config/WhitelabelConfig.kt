package com.example.config

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.BuildConfig
import com.example.data.local.PlaybackEngine

object WhitelabelConfig {
    const val STOCK_APP_NAME = "NovaStream Player"
    const val STOCK_LAUNCHER_LABEL = "NovaStream"
    const val STOCK_TAGLINE = "Xtream Codes • M3U/M3U8 • XMLTV EPG"
    const val STOCK_USER_AGENT = "NovaStream/1.0 (Android)"

    private val StockObsidianBg = Color(0xFF06080D)
    private val StockMidnightSurface = Color(0xFF0D121C)
    private val StockSlateLow = Color(0xFF101624)
    private val StockSlate = Color(0xFF141C2C)
    private val StockSlateHigh = Color(0xFF171F30)
    private val StockSlateHighest = Color(0xFF202A40)
    private val StockPrimary = Color(0xFF00E5FF)
    private val StockSecondary = Color(0xFF8C52FF)
    private val StockAccent = Color(0xFFFFB800)
    private val StockTextPrimary = Color(0xFFF3F6FC)

    val appName: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_APP_NAME, "WHITELABEL_APP_NAME", "APP_NAME")
            .ifBlank { STOCK_APP_NAME }

    val launcherLabel: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_LAUNCHER_LABEL, "WHITELABEL_LAUNCHER_LABEL", "LAUNCHER_LABEL")
            .ifBlank { STOCK_LAUNCHER_LABEL }

    val shortBrandUpper: String = if (
        appName.equals(STOCK_APP_NAME, ignoreCase = true) &&
        launcherLabel.equals(STOCK_LAUNCHER_LABEL, ignoreCase = true)
    ) {
        "NOVASTREAM"
    } else {
        launcherLabel.ifBlank { appName }.uppercase()
    }

    val applicationId: String = BuildConfig.APPLICATION_ID

    val tagline: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_TAGLINE, "WHITELABEL_TAGLINE", "APP_TAGLINE")
            .ifBlank { STOCK_TAGLINE }

    val logoUrl: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_LOGO_URL, "WHITELABEL_LOGO_URL", "BRAND_LOGO_URL")
    val hasCustomLogo: Boolean = logoUrl.isNotBlank()

    val backgroundColor: Color = parseHexColor(
        sanitizeEnvValue(BuildConfig.WHITELABEL_BG_COLOR, "WHITELABEL_BG_COLOR", "BG_COLOR"),
        StockObsidianBg
    )
    val primaryColor: Color = ensureVisibleAccent(
        parseHexColor(
            sanitizeEnvValue(BuildConfig.WHITELABEL_PRIMARY_COLOR, "WHITELABEL_PRIMARY_COLOR", "PRIMARY_COLOR"),
            StockPrimary
        ),
        backgroundColor
    )
    val secondaryColor: Color = ensureVisibleAccent(
        parseHexColor(
            sanitizeEnvValue(BuildConfig.WHITELABEL_SECONDARY_COLOR, "WHITELABEL_SECONDARY_COLOR", "SECONDARY_COLOR"),
            StockSecondary
        ),
        backgroundColor
    )
    val accentColor: Color = ensureVisibleAccent(
        parseHexColor(
            sanitizeEnvValue(BuildConfig.WHITELABEL_ACCENT_COLOR, "WHITELABEL_ACCENT_COLOR", "ACCENT_COLOR"),
            StockAccent
        ),
        backgroundColor
    )

    private val isDefaultBg: Boolean = backgroundColor == StockObsidianBg

    val midnightSurface: Color =
        if (isDefaultBg) StockMidnightSurface else lerp(backgroundColor, Color.White, 0.042f)
    val slateContainerLow: Color =
        if (isDefaultBg) StockSlateLow else lerp(backgroundColor, Color.White, 0.062f)
    val slateContainer: Color =
        if (isDefaultBg) StockSlate else lerp(backgroundColor, Color.White, 0.085f)
    val slateContainerHigh: Color =
        if (isDefaultBg) StockSlateHigh else lerp(backgroundColor, Color.White, 0.105f)
    val slateContainerHighest: Color =
        if (isDefaultBg) StockSlateHighest else lerp(backgroundColor, Color.White, 0.145f)

    val primaryContainer: Color =
        if (primaryColor == StockPrimary && isDefaultBg) Color(0xFF003640)
        else lerp(backgroundColor, primaryColor, 0.22f)

    val secondaryContainer: Color =
        if (secondaryColor == StockSecondary && isDefaultBg) Color(0xFF2D1B54)
        else lerp(backgroundColor, secondaryColor, 0.25f)

    val accentContainer: Color =
        if (accentColor == StockAccent && isDefaultBg) Color(0xFF422E00)
        else lerp(backgroundColor, accentColor, 0.24f)

    val primaryGlowBorder: Color = primaryColor.copy(alpha = 0.53f)

    val onPrimaryColor: Color = contrastingContentColor(primaryColor, darkFallback = backgroundColor, lightFallback = StockTextPrimary)
    val onSecondaryColor: Color = contrastingContentColor(secondaryColor, darkFallback = backgroundColor, lightFallback = StockTextPrimary)
    val onAccentColor: Color = contrastingContentColor(accentColor, darkFallback = backgroundColor, lightFallback = StockTextPrimary)

    val supportText: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_SUPPORT_TEXT, "WHITELABEL_SUPPORT_TEXT", "SUPPORT_TEXT")
    val supportUrl: String = normalizeExternalLink(
        sanitizeEnvValue(BuildConfig.WHITELABEL_SUPPORT_URL, "WHITELABEL_SUPPORT_URL", "SUPPORT_URL")
    )
    val hasSupportInfo: Boolean = supportText.isNotBlank() || supportUrl.isNotBlank()

    val apiServerUrl: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_API_SERVER, "WHITELABEL_API_SERVER", "API_SERVER")
    val hasApiServer: Boolean = apiServerUrl.isNotBlank()

    val defaultUserAgent: String =
        sanitizeEnvValue(BuildConfig.WHITELABEL_DEFAULT_USER_AGENT, "WHITELABEL_DEFAULT_USER_AGENT", "DEFAULT_USER_AGENT")
            .ifBlank { STOCK_USER_AGENT }

    val defaultPlaybackEngine: PlaybackEngine = resolveEngineFromEnv(
        sanitizeEnvValue(BuildConfig.WHITELABEL_DEFAULT_ENGINE, "WHITELABEL_DEFAULT_ENGINE", "DEFAULT_ENGINE")
    )

    val activeDeviceAbi: String
        get() = runCatching { Build.SUPPORTED_ABIS?.firstOrNull().orEmpty() }.getOrDefault("").ifBlank { "universal" }

    fun sanitizeEnvValue(raw: String?, vararg keyAliases: String): String {
        val trimmed = raw?.trim() ?: return ""
        if (trimmed.isEmpty()) return ""
        if (keyAliases.any { alias -> trimmed.equals(alias, ignoreCase = true) }) return ""
        val upper = trimmed.uppercase()
        if (upper == "NULL" || upper == "UNDEFINED" || upper == "NONE" || upper == "N/A") return ""
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) return ""
        if (upper.startsWith("YOUR_") || upper.startsWith("MY_") || upper.endsWith("_DEFAULT_VALUE")) return ""
        return trimmed
    }

    fun parseHexColor(rawHex: String?, fallback: Color): Color {
        if (rawHex.isNullOrBlank()) return fallback
        val cleaned = rawHex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        val argbLong = when (cleaned.length) {
            6 -> cleaned.toLongOrNull(16)?.let { 0xFF000000L or it }
            8 -> cleaned.toLongOrNull(16)
            else -> null
        } ?: return fallback
        val a = ((argbLong ushr 24) and 0xFF).toInt()
        val r = ((argbLong ushr 16) and 0xFF).toInt()
        val g = ((argbLong ushr 8) and 0xFF).toInt()
        val b = (argbLong and 0xFF).toInt()
        return Color(red = r, green = g, blue = b, alpha = a)
    }

    fun ensureVisibleAccent(accent: Color, background: Color): Color {
        val lAccent = relativeLuminance(accent)
        val lBg = relativeLuminance(background)
        val contrast = (maxOf(lAccent, lBg) + 0.05f) / (minOf(lAccent, lBg) + 0.05f)
        return if (contrast < 2.2f) {
            lerp(accent, StockTextPrimary, 0.72f)
        } else {
            accent
        }
    }

    private fun relativeLuminance(color: Color): Float {
        return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    }

    fun contrastingContentColor(
        surface: Color,
        darkFallback: Color = StockObsidianBg,
        lightFallback: Color = StockTextPrimary
    ): Color {
        return if (relativeLuminance(surface) >= 0.48f) darkFallback else lightFallback
    }

    fun resolveEngineFromEnv(raw: String?): PlaybackEngine {
        val token = raw?.trim()?.uppercase().orEmpty()
        if (token.isEmpty()) return PlaybackEngine.EXOPLAYER
        return when (token) {
            "EXO", "EXOPLAYER", "MEDIA3" -> PlaybackEngine.EXOPLAYER
            "VLC", "LIBVLC" -> PlaybackEngine.LIBVLC
            "MPV", "LIBMPV" -> PlaybackEngine.LIBMPV
            "EXTERNAL_MPV", "MPV_APP" -> PlaybackEngine.EXTERNAL_MPV
            "EXTERNAL_VLC", "VLC_APP" -> PlaybackEngine.EXTERNAL_VLC
            "EXTERNAL_MX", "MX", "MXPLAYER" -> PlaybackEngine.EXTERNAL_MX
            "EXTERNAL_JUST", "JUST" -> PlaybackEngine.EXTERNAL_JUST
            "EXTERNAL_KODI", "KODI" -> PlaybackEngine.EXTERNAL_KODI
            "EXTERNAL", "EXTERNAL_CHOOSER", "CHOOSER" -> PlaybackEngine.EXTERNAL_CHOOSER
            else -> PlaybackEngine.fromStoredName(token).takeIf { it != PlaybackEngine.DEFAULT }
                ?: PlaybackEngine.EXOPLAYER
        }
    }

    fun normalizeExternalLink(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("mailto:", ignoreCase = true) ||
                trimmed.startsWith("tel:", ignoreCase = true) ||
                trimmed.startsWith("tg://", ignoreCase = true) -> trimmed
            trimmed.contains("@") && !trimmed.contains("/") -> "mailto:$trimmed"
            else -> "https://$trimmed"
        }
    }
}
