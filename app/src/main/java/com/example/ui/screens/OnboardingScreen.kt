package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PlaylistProfileEntity
import com.example.data.repository.SyncStage
import com.example.ui.components.NovaGlassCard
import com.example.ui.components.SyncBanner
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

@Composable
fun OnboardingScreen(
    profiles: List<PlaylistProfileEntity>,
    activeProfileId: Long,
    syncStage: SyncStage,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onDismissSync: () -> Unit,
    onConnectXtream: (name: String, server: String, user: String, pass: String, ua: String) -> Unit,
    onConnectM3uUrl: (name: String, m3uUrl: String, epgUrl: String, ua: String) -> Unit,
    onConnectM3uFile: (name: String, uri: Uri, epgUrl: String, ua: String) -> Unit,
    onLoadDemo: () -> Unit,
    onImportBackup: (Uri) -> Unit,
    onSelectProfile: (Long) -> Unit,
    onDeleteProfile: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var xtreamName by remember { mutableStateOf("") }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var customUserAgent by remember { mutableStateOf("NovaStream/1.0 (Android)") }

    var m3uName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var m3uEpgUrl by remember { mutableStateOf("") }
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }

    val m3uFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedFileUri = uri
            if (m3uName.isBlank()) {
                m3uName = uri.lastPathSegment?.substringAfterLast('/') ?: "Local M3U"
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportBackup(uri)
    }

    val isSyncing = syncStage is SyncStage.InProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_broadcast),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.32f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianBg.copy(alpha = 0.55f), ObsidianBg.copy(alpha = 0.96f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canNavigateBack) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElectricCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = ElectricCyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("NovaStream Player", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Xtream Codes • M3U/M3U8 • XMLTV EPG",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                SyncBanner(stage = syncStage, onDismiss = onDismissSync)

                if (profiles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Saved Playlists", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Spacer(Modifier.height(8.dp))
                    profiles.forEach { profile ->
                        NovaGlassCard(
                            highlighted = profile.id == activeProfileId,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("saved_profile_${profile.id}"),
                            onClick = { onSelectProfile(profile.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Router, contentDescription = null, tint = ElectricCyan)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${profile.sourceType.name} • ${profile.accountStatus}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteProfile(profile.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_profile_${profile.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete playlist", tint = BroadcastCrimson)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                NovaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = SlateContainer,
                            contentColor = ElectricCyan
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.testTag("tab_xtream"),
                                text = { Text("Xtream API") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.testTag("tab_m3u"),
                                text = { Text("M3U / File") }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (selectedTab == 0) {
                            OutlinedTextField(
                                value = xtreamName,
                                onValueChange = { xtreamName = it },
                                label = { Text("Playlist Name (e.g. Living Room)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("xtream_name_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = xtreamServer,
                                onValueChange = { xtreamServer = it },
                                label = { Text("Server URL (http://host:port)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("xtream_server_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = xtreamUser,
                                onValueChange = { xtreamUser = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("xtream_user_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = xtreamPass,
                                onValueChange = { xtreamPass = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("xtream_pass_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customUserAgent,
                                onValueChange = { customUserAgent = it },
                                label = { Text("HTTP User-Agent (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("xtream_ua_input")
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    onConnectXtream(xtreamName, xtreamServer, xtreamUser, xtreamPass, customUserAgent)
                                },
                                enabled = !isSyncing && xtreamServer.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("connect_xtream_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Connect & Sync Xtream", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            OutlinedTextField(
                                value = m3uName,
                                onValueChange = { m3uName = it },
                                label = { Text("Playlist Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("m3u_name_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = m3uUrl,
                                onValueChange = {
                                    m3uUrl = it
                                    if (it.isNotBlank()) pickedFileUri = null
                                },
                                label = { Text("M3U / M3U8 URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("m3u_url_input")
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    m3uFileLauncher.launch(arrayOf("*/*"))
                                },
                                border = BorderStroke(1.dp, GlassBorder),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_m3u_file_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ElectricCyan)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = pickedFileUri?.lastPathSegment?.let { "Selected: $it" }
                                        ?: "Or Pick Local .m3u / .m3u8 File",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = m3uEpgUrl,
                                onValueChange = { m3uEpgUrl = it },
                                label = { Text("XMLTV EPG URL (.xml or .xml.gz, Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("m3u_epg_input")
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val fileUri = pickedFileUri
                                    if (fileUri != null) {
                                        onConnectM3uFile(m3uName, fileUri, m3uEpgUrl, customUserAgent)
                                    } else {
                                        onConnectM3uUrl(m3uName, m3uUrl, m3uEpgUrl, customUserAgent)
                                    }
                                },
                                enabled = !isSyncing && (m3uUrl.isNotBlank() || pickedFileUri != null),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("connect_m3u_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Load & Parse Playlist", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .testTag("load_demo_playlist_button")
                            .clickable(enabled = !isSyncing, onClick = onLoadDemo),
                        color = SlateContainerHigh,
                        border = BorderStroke(1.dp, UltravioletPulse.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = UltravioletPulse)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Public FTA Demo", style = MaterialTheme.typography.titleSmall)
                                Text("Instant HLS & VOD", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .testTag("restore_backup_button")
                            .clickable(enabled = !isSyncing) {
                                backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                        color = SlateContainerHigh,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Restore Backup", style = MaterialTheme.typography.titleSmall)
                                Text("Import .novabackup", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
