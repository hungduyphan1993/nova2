package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.config.WhitelabelConfig
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

@Composable
fun WhitelabelBrandEmblem(
    size: Dp = 42.dp,
    cornerRadius: Dp = 12.dp,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    var logoLoadFailed by remember(WhitelabelConfig.logoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (WhitelabelConfig.hasCustomLogo && !logoLoadFailed) {
            AsyncImage(
                model = WhitelabelConfig.logoUrl,
                contentDescription = "${WhitelabelConfig.appName} logo",
                contentScale = ContentScale.Fit,
                onError = { logoLoadFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}

@Composable
fun WhitelabelSupportCard(
    modifier: Modifier = Modifier
) {
    if (!WhitelabelConfig.hasSupportInfo) return

    val context = LocalContext.current
    val hasLink = WhitelabelConfig.supportUrl.isNotBlank()
    val displayTitle = WhitelabelConfig.supportText.ifBlank { "Operator Support & Help Desk" }
    val displaySub = if (WhitelabelConfig.supportText.isNotBlank() && hasLink) {
        WhitelabelConfig.supportUrl
    } else if (hasLink) {
        "Tap to open ${WhitelabelConfig.supportUrl}"
    } else {
        "${WhitelabelConfig.appName} customer assistance"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (hasLink) {
                    Modifier.clickable {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WhitelabelConfig.supportUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .testTag("whitelabel_support_card"),
        color = SlateContainerHigh,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, CinemaGold.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaGold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = CinemaGold,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displaySub,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasLink) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open support link",
                    tint = CinemaGold,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhitelabelBuildMetadataCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("whitelabel_build_metadata_card"),
        color = SlateContainer.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${WhitelabelConfig.appName} • v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Package: ${WhitelabelConfig.applicationId}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataPill(
                    text = "ABI: ${WhitelabelConfig.activeDeviceAbi}",
                    accent = ElectricCyan
                )
                MetadataPill(
                    text = "Default: ${WhitelabelConfig.defaultPlaybackEngine.shortBadge}",
                    accent = UltravioletPulse
                )
                MetadataPill(
                    text = if (WhitelabelConfig.hasApiServer) "Portal API Active" else "HTTPS/HTTP Auto-Detect",
                    accent = EmeraldSignal
                )
            }
        }
    }
}

@Composable
private fun MetadataPill(
    text: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
