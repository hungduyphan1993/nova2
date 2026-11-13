package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.StreamItemEntity
import com.example.ui.components.ChannelLogoBadge
import com.example.ui.theme.BroadcastCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MultiScreenLayout
import com.example.ui.viewmodel.MultiScreenSlot

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiScreenScreen(
    slots: List<MultiScreenSlot>,
    layout: MultiScreenLayout,
    focusedAudioSlot: Int,
    muteAll: Boolean,
    liveChannels: List<StreamItemEntity>,
    onBack: () -> Unit,
    onChangeLayout: (MultiScreenLayout) -> Unit,
    onAssignSlot: (Int, StreamItemEntity) -> Unit,
    onClearSlot: (Int) -> Unit,
    onFocusAudio: (Int) -> Unit,
    onToggleMuteAll: () -> Unit,
    onExpandSlot: (Int) -> Unit,
    onReleaseAll: () -> Unit
) {
    var pickingForSlot by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose { onReleaseAll() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("multiscreen_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Multi-Screen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MultiScreenLayout.entries.forEach { mode ->
                    FilterChip(
                        selected = layout == mode,
                        onClick = { onChangeLayout(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            IconButton(onClick = onToggleMuteAll, modifier = Modifier.minimumInteractiveComponentSize().testTag("multiscreen_mute_all")) {
                Icon(
                    if (muteAll) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mute all",
                    tint = if (muteAll) BroadcastCrimson else ElectricCyan
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            when (layout) {
                MultiScreenLayout.SPLIT_2 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MultiSlotPane(slots[0], focusedAudioSlot == 0 && !muteAll, Modifier.weight(1f).fillMaxWidth(),
                            onTap = { if (slots[0].stream == null) pickingForSlot = 0 else onFocusAudio(0) },
                            onSwap = { pickingForSlot = 0 }, onClear = { onClearSlot(0) }, onExpand = { onExpandSlot(0) })
                        MultiSlotPane(slots[1], focusedAudioSlot == 1 && !muteAll, Modifier.weight(1f).fillMaxWidth(),
                            onTap = { if (slots[1].stream == null) pickingForSlot = 1 else onFocusAudio(1) },
                            onSwap = { pickingForSlot = 1 }, onClear = { onClearSlot(1) }, onExpand = { onExpandSlot(1) })
                    }
                }
                MultiScreenLayout.FOCUS_3 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MultiSlotPane(slots[0], focusedAudioSlot == 0 && !muteAll, Modifier.weight(1.25f).fillMaxWidth(),
                            onTap = { if (slots[0].stream == null) pickingForSlot = 0 else onFocusAudio(0) },
                            onSwap = { pickingForSlot = 0 }, onClear = { onClearSlot(0) }, onExpand = { onExpandSlot(0) })
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultiSlotPane(slots[1], focusedAudioSlot == 1 && !muteAll, Modifier.weight(1f).fillMaxHeight(),
                                onTap = { if (slots[1].stream == null) pickingForSlot = 1 else onFocusAudio(1) },
                                onSwap = { pickingForSlot = 1 }, onClear = { onClearSlot(1) }, onExpand = { onExpandSlot(1) })
                            MultiSlotPane(slots[2], focusedAudioSlot == 2 && !muteAll, Modifier.weight(1f).fillMaxHeight(),
                                onTap = { if (slots[2].stream == null) pickingForSlot = 2 else onFocusAudio(2) },
                                onSwap = { pickingForSlot = 2 }, onClear = { onClearSlot(2) }, onExpand = { onExpandSlot(2) })
                        }
                    }
                }
                MultiScreenLayout.QUAD_4 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultiSlotPane(slots[0], focusedAudioSlot == 0 && !muteAll, Modifier.weight(1f).fillMaxHeight(),
                                onTap = { if (slots[0].stream == null) pickingForSlot = 0 else onFocusAudio(0) },
                                onSwap = { pickingForSlot = 0 }, onClear = { onClearSlot(0) }, onExpand = { onExpandSlot(0) })
                            MultiSlotPane(slots[1], focusedAudioSlot == 1 && !muteAll, Modifier.weight(1f).fillMaxHeight(),
                                onTap = { if (slots[1].stream == null) pickingForSlot = 1 else onFocusAudio(1) },
                                onSwap = { pickingForSlot = 1 }, onClear = { onClearSlot(1) }, onExpand = { onExpandSlot(1) })
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultiSlotPane(slots[2], focusedAudioSlot == 2 && !muteAll, Modifier.weight(1f).fillMaxHeight(),
                                onTap = { if (slots[2].stream == null) pickingForSlot = 2 else onFocusAudio(2) },
                                onSwap = { pickingForSlot = 2 }, onClear = { onClearSlot(2) }, onExpand = { onExpandSlot(2) })
                            MultiSlotPane(slots[3], focusedAudioSlot == 3 && !muteAll, Modifier.weight(1f).fillMaxHeight(),
                                onTap = { if (slots[3].stream == null) pickingForSlot = 3 else onFocusAudio(3) },
                                onSwap = { pickingForSlot = 3 }, onClear = { onClearSlot(3) }, onExpand = { onExpandSlot(3) })
                        }
                    }
                }
            }
        }
    }

    pickingForSlot?.let { slotIdx ->
        ModalBottomSheet(
            onDismissRequest = { pickingForSlot = null },
            containerColor = SlateContainerHigh
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Assign Channel to Screen ${slotIdx + 1}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(liveChannels, key = { it.id }) { ch ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onAssignSlot(slotIdx, ch)
                                    pickingForSlot = null
                                },
                            color = SlateContainer
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogoBadge(ch.logoUrl, ch.title, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(ch.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MultiSlotPane(
    slot: MultiScreenSlot,
    isAudioFocused: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    onExpand: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .testTag("multiscreen_slot_${slot.slotIndex}")
            .clickable(onClick = onTap),
        color = SlateContainer,
        border = BorderStroke(
            width = if (isAudioFocused && slot.stream != null) 2.dp else 1.dp,
            color = if (isAudioFocused && slot.stream != null) ElectricCyan else GlassBorder
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (slot.stream == null || slot.player == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add channel", tint = ElectricCyan, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Screen ${slot.slotIndex + 1}", style = MaterialTheme.typography.titleSmall)
                    Text("Tap to assign channel", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view -> view.player = slot.player },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )

                // Slot Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(ObsidianBg.copy(alpha = 0.68f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAudioFocused) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audio active", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        slot.stream.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSwap, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap channel", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand fullscreen", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close slot", tint = BroadcastCrimson, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
