package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.EpgProgramEntity
import com.example.data.local.StreamItemEntity
import com.example.ui.components.ChannelLogoBadge
import com.example.ui.components.NovaFormatters
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPulse

@Composable
fun EpgGuideScreen(
    channels: List<StreamItemEntity>,
    epgByChannel: Map<String, List<EpgProgramEntity>>,
    epgOffsetMinutes: Int,
    onBack: () -> Unit,
    onAdjustOffset: (Int) -> Unit,
    onTuneLive: (StreamItemEntity) -> Unit,
    onPlayCatchUp: (StreamItemEntity, EpgProgramEntity) -> Unit,
    onToggleReminder: (EpgProgramEntity) -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val windowStartMs = remember(nowMs) { ((nowMs - 2 * 3600_000L) / 1_800_000L) * 1_800_000L }
    val totalHours = 10
    val dpPerMinute = 3.2f
    val totalGridWidthDp = (totalHours * 60 * dpPerMinute).dp
    val horizontalScroll = rememberScrollState(initial = ((nowMs - windowStartMs) / 60_000f * dpPerMinute).toInt().coerceAtLeast(0))

    var selectedCell by remember { mutableStateOf<Pair<StreamItemEntity, EpgProgramEntity>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("epg_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("2D TV Guide (EPG)", style = MaterialTheme.typography.headlineSmall)
                Text("Offset: ${if (epgOffsetMinutes >= 0) "+$epgOffsetMinutes" else "$epgOffsetMinutes"} min", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            TextButton(onClick = { onAdjustOffset(-60) }) { Text("-1h") }
            TextButton(onClick = { onAdjustOffset(60) }) { Text("+1h") }
        }

        // Time Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateContainer)
                .border(1.dp, GlassBorder)
        ) {
            Box(
                modifier = Modifier.width(136.dp).height(40.dp).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("CHANNEL", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .horizontalScroll(horizontalScroll)
            ) {
                Row(modifier = Modifier.width(totalGridWidthDp)) {
                    for (slot in 0 until totalHours * 2) {
                        val slotTime = windowStartMs + slot * 1_800_000L
                        Box(
                            modifier = Modifier
                                .width((30 * dpPerMinute).dp)
                                .fillMaxHeight()
                                .border(0.5.dp, GlassBorder)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                NovaFormatters.formatClock(slotTime),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No live channels available for guide.", color = TextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(channels, key = { it.id }) { ch ->
                    val programs = epgByChannel[ch.epgChannelId].orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .border(0.5.dp, GlassBorder)
                    ) {
                        // Sticky Channel Column
                        Row(
                            modifier = Modifier
                                .width(136.dp)
                                .fillMaxHeight()
                                .background(SlateContainer)
                                .clickable { onTuneLive(ch) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelLogoBadge(ch.logoUrl, ch.title, size = 34.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(ch.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ch.catchupDays > 0) {
                                    Text("Catch-Up", style = MaterialTheme.typography.labelSmall, color = UltravioletPulse)
                                }
                            }
                        }

                        // Timeline Row
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScroll)
                        ) {
                            Box(modifier = Modifier.width(totalGridWidthDp).fillMaxHeight()) {
                                val windowEndMs = windowStartMs + totalHours * 3600_000L
                                val visiblePrograms = programs.filter { it.endEpochMs > windowStartMs && it.startEpochMs < windowEndMs }
                                if (visiblePrograms.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { onTuneLive(ch) }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text("No EPG data • Tap to tune live", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }
                                } else {
                                    visiblePrograms.forEach { prog ->
                                        val clampedStart = prog.startEpochMs.coerceAtLeast(windowStartMs)
                                        val clampedEnd = prog.endEpochMs.coerceAtMost(windowEndMs)
                                        val offsetMin = ((clampedStart - windowStartMs) / 60_000f)
                                        val durMin = ((clampedEnd - clampedStart) / 60_000f).coerceAtLeast(8f)
                                        val isNow = nowMs in prog.startEpochMs..prog.endEpochMs
                                        val isPast = prog.endEpochMs < nowMs

                                        Surface(
                                            modifier = Modifier
                                                .offset(x = (offsetMin * dpPerMinute).dp)
                                                .width((durMin * dpPerMinute).dp)
                                                .fillMaxHeight()
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { selectedCell = ch to prog },
                                            color = when {
                                                isNow -> ElectricCyan.copy(alpha = 0.18f)
                                                isPast -> SlateContainer
                                                else -> SlateContainerHigh
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                if (isNow) ElectricCyan else GlassBorder
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        prog.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (isNow) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (prog.hasReminder) {
                                                        Icon(Icons.Default.NotificationsActive, contentDescription = "Reminder set", tint = CinemaGold, modifier = Modifier.size(13.dp))
                                                    } else if (isPast && ch.catchupDays > 0) {
                                                        Icon(Icons.Default.History, contentDescription = "Catch-up available", tint = UltravioletPulse, modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                                Text(
                                                    "${NovaFormatters.formatClock(prog.startEpochMs)} – ${NovaFormatters.formatClock(prog.endEpochMs)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                // Live Time Needle
                                val nowOffsetMin = (nowMs - windowStartMs) / 60_000f
                                if (nowOffsetMin in 0f..(totalHours * 60f)) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (nowOffsetMin * dpPerMinute).dp)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(ElectricCyan)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Program Inspector Dialog
    selectedCell?.let { (channel, program) ->
        val isNow = nowMs in program.startEpochMs..program.endEpochMs
        val isPast = program.endEpochMs < nowMs
        val hasCatchUp = channel.catchupDays > 0 || channel.catchupType.isNotBlank()

        AlertDialog(
            onDismissRequest = { selectedCell = null },
            containerColor = SlateContainerHigh,
            title = { Text(program.title) },
            text = {
                Column {
                    Text(
                        "${channel.title} • ${NovaFormatters.formatClock(program.startEpochMs)} - ${NovaFormatters.formatClock(program.endEpochMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        program.description.ifBlank { "No program synopsis provided in XMLTV feed." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                when {
                    isNow -> {
                        Button(
                            onClick = {
                                selectedCell = null
                                onTuneLive(channel)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Watch Live")
                        }
                    }
                    isPast && hasCatchUp -> {
                        Button(
                            onClick = {
                                selectedCell = null
                                onPlayCatchUp(channel, program)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = UltravioletPulse, contentColor = MaterialTheme.colorScheme.onSecondary)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Replay Catch-Up")
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = {
                                onToggleReminder(program)
                                selectedCell = null
                            }
                        ) {
                            Icon(
                                if (program.hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = CinemaGold
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (program.hasReminder) "Cancel Reminder" else "Set Reminder")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCell = null }) {
                    Text("Close")
                }
            }
        )
    }
}
