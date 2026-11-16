package com.example.ui.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.media.LocalMediaStorage
import com.example.data.model.AspectRatioPreset
import com.example.data.model.CameraMotion
import com.example.data.model.SceneTransition
import com.example.data.model.StudioProject
import com.example.data.model.StylePreset
import java.io.File
import java.text.DateFormat
import java.util.Locale

private val VIDEO_PROMPT_STARTERS = listOf(
    "Neon Rain" to "A lone cybernetic traveler walking through rain-soaked neon Tokyo streets with glowing reflections",
    "Crystal Cave" to "An astronaut holding a warm lantern exploring towering bioluminescent crystal caverns",
    "Alpine Dawn" to "Sweeping aerial drone sequence gliding over misty jagged mountain peaks at golden sunrise",
    "Ocean Glow" to "Slow underwater camera tracking glowing jellyfish drifting through deep midnight bioluminescent currents"
)

@Composable
fun VideoStudioScreen(
    state: VideoStudioUiState,
    historyState: ProjectsUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importVideoClip(uri)
        }
    }

    var showAddSceneDialog by remember { mutableStateOf(false) }
    var newScenePrompt by remember { mutableStateOf("") }

    val colorMatrixArray = remember(
        state.colorGrade,
        state.exposure,
        state.contrast,
        state.saturation,
        state.warmth
    ) {
        LocalMediaStorage.buildColorMatrix(
            grade = state.colorGrade,
            exposure = state.exposure,
            contrast = state.contrast,
            saturation = state.saturation,
            warmth = state.warmth
        ).array
    }

    val isBusy = state.taskState is AiTaskState.Running || state.exportState is AiTaskState.Running

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Video Studio",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Prompt-to-reel director, scene remix & MP4 export",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("import_video_clip_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Clip")
                    }
                }
            }

            item {
                AiTaskStatusBanner(taskState = state.taskState)
            }

            if (state.exportState !is AiTaskState.Idle) {
                item {
                    AiTaskStatusBanner(taskState = state.exportState)
                }
            }

            if (state.scenes.isNotEmpty()) {
                item {
                    // Live Animated Video Viewport + Scrubbing Controls
                    VideoTimelinePlayerCard(
                        state = state,
                        colorMatrixArray = colorMatrixArray,
                        isBusy = isBusy,
                        onTogglePlay = viewModel::toggleVideoPlayback,
                        onSeek = viewModel::seekVideo,
                        onSaveProject = viewModel::saveCurrentVideoProject,
                        onExportMp4 = viewModel::exportVideoMp4,
                        onSaveToGallery = viewModel::saveCurrentVideoToGallery,
                        onShareMp4 = { viewModel.shareExportedVideo(context) }
                    )
                }

                item {
                    // Horizontal Scene Track
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StudioSectionHeader(
                                title = "Storyboard Timeline (${state.scenes.size} Scenes)",
                                subtitle = "Tap any shot to AI restyle, edit caption, or tune motion",
                                trailing = {
                                    TextButton(
                                        onClick = { showAddSceneDialog = true },
                                        enabled = !isBusy && state.scenes.size < 8
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Shot")
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                state.scenes.forEachIndexed { idx, sceneRender ->
                                    val selected = idx == state.selectedSceneIndex
                                    Column(
                                        modifier = Modifier
                                            .width(126.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { viewModel.selectVideoScene(idx) }
                                            .padding(6.dp)
                                            .testTag("timeline_scene_$idx")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(74.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.background),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (sceneRender.bitmap != null) {
                                                Image(
                                                    bitmap = sceneRender.bitmap.asImageBitmap(),
                                                    contentDescription = "Scene ${idx + 1}",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xBA0B0D12),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = String.format(
                                                        Locale.US,
                                                        "S%d • %.1fs",
                                                        idx + 1,
                                                        sceneRender.item.durationMs / 1000f
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = sceneRender.item.caption.ifBlank { "Scene ${idx + 1}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val selectedScene = state.scenes.getOrNull(state.selectedSceneIndex)
                if (selectedScene != null) {
                    item {
                        SelectedSceneInspector(
                            sceneIndex = state.selectedSceneIndex,
                            scene = selectedScene,
                            editInstruction = state.sceneEditInstruction,
                            isBusy = isBusy,
                            onEditInstructionChange = viewModel::updateSceneEditInstruction,
                            onApplySceneAiEdit = { viewModel.aiEditSelectedVideoScene() },
                            onCaptionChange = { viewModel.updateSelectedSceneProps(caption = it) },
                            onMotionChange = { viewModel.updateSelectedSceneProps(cameraMotion = it) },
                            onTransitionChange = { viewModel.updateSelectedSceneProps(transition = it) },
                            onDurationChange = { viewModel.updateSelectedSceneProps(durationMs = it) }
                        )
                    }
                }
            }

            if (state.scenes.isEmpty()) {
                item {
                    TextToVideoPromptCard(
                        state = state,
                        isBusy = isBusy,
                        viewModel = viewModel
                    )
                }

                item {
                    // Empty preview placeholder
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_canvas),
                                contentDescription = "Video storyboard canvas",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(155.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Direct a Multi-Scene AI Video",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gemini directs your storyboard scenes, generates keyframes, animates smooth camera motion, and exports real MP4 video.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    TextToVideoPromptCard(
                        state = state,
                        isBusy = isBusy,
                        viewModel = viewModel
                    )
                }

                item {
                    ColorGradeAndTuningPanel(
                        selectedGrade = state.colorGrade,
                        exposure = state.exposure,
                        contrast = state.contrast,
                        saturation = state.saturation,
                        warmth = state.warmth,
                        overlayText = state.overlayText,
                        onGradeChange = { viewModel.updateVideoAdjustments(colorGrade = it) },
                        onExposureChange = { viewModel.updateVideoAdjustments(exposure = it) },
                        onContrastChange = { viewModel.updateVideoAdjustments(contrast = it) },
                        onSaturationChange = { viewModel.updateVideoAdjustments(saturation = it) },
                        onWarmthChange = { viewModel.updateVideoAdjustments(warmth = it) },
                        onOverlayTextChange = { viewModel.updateVideoAdjustments(overlayText = it) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    StudioSectionHeader(
                        title = "Previous Video Prompts & Reels",
                        subtitle = "Saved in Cloud Firestore — tap to play, edit, or export MP4"
                    )
                }
            }

            when (historyState) {
                is ProjectsUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                is ProjectsUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = historyState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is ProjectsUiState.Success -> {
                    if (historyState.projects.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Text(
                                    text = "Your prompt-generated video reels will appear here automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(historyState.projects, key = { it.id }) { item ->
                            VideoHistoryItemCard(
                                project = item,
                                isActive = item.id == state.projectId,
                                isBusy = isBusy,
                                onSelect = {
                                    viewModel.openProject(item) { _ -> }
                                },
                                onSaveToGallery = {
                                    viewModel.saveVideoFileToGallery(item.localMediaPath, item.title)
                                },
                                onRerun = {
                                    viewModel.updateVideoAspectRatio(
                                        AspectRatioPreset.fromApiValue(item.aspectRatio)
                                    )
                                    viewModel.updateVideoStyle(StylePreset.fromId(item.stylePreset))
                                    viewModel.generateVideoReel(item.prompt)
                                },
                                onDelete = {
                                    viewModel.deleteProject(item.id)
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddSceneDialog) {
        AlertDialog(
            onDismissRequest = { showAddSceneDialog = false },
            title = { Text("Add AI Shot to Timeline") },
            text = {
                OutlinedTextField(
                    value = newScenePrompt,
                    onValueChange = { newScenePrompt = it },
                    placeholder = { Text("Describe the new shot to generate...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addAiSceneToVideo(newScenePrompt)
                        newScenePrompt = ""
                        showAddSceneDialog = false
                    },
                    enabled = newScenePrompt.isNotBlank()
                ) {
                    Text("Generate Shot")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSceneDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VideoTimelinePlayerCard(
    state: VideoStudioUiState,
    colorMatrixArray: FloatArray,
    isBusy: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSaveProject: () -> Unit,
    onExportMp4: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShareMp4: () -> Unit
) {
    // Compute active scene & local normalized progress at state.currentTimeMs
    val totalMs = state.totalDurationMs
    var cursor = 0L
    var activeIdx = state.scenes.lastIndex
    var localProgress = 1f
    for (i in state.scenes.indices) {
        val dur = state.scenes[i].item.durationMs.coerceIn(1500L, 8000L)
        if (state.currentTimeMs < cursor + dur || i == state.scenes.lastIndex) {
            activeIdx = i
            localProgress = ((state.currentTimeMs - cursor).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            break
        }
        cursor += dur
    }

    val activeRender = state.scenes[activeIdx]
    val prevRender = if (activeIdx > 0) state.scenes[activeIdx - 1] else null
    val transitionProgress = (localProgress / 0.18f).coerceIn(0f, 1f)
    val transition = SceneTransition.fromId(activeRender.item.transition)
    val motion = CameraMotion.fromId(activeRender.item.cameraMotion)
    val filter = remember(colorMatrixArray) { ColorFilter.colorMatrix(ColorMatrix(colorMatrixArray)) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${formatTimecode(state.currentTimeMs)} / ${formatTimecode(totalMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(state.aspectRatio.ratioValue.coerceIn(0.56f, 1.8f))
                    .clip(RoundedCornerShape(14.dp))
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (prevRender?.bitmap != null && transitionProgress < 1f) {
                    val prevMotion = CameraMotion.fromId(prevRender.item.cameraMotion)
                    AnimatedMotionFrame(
                        bitmap = prevRender.bitmap,
                        motion = prevMotion,
                        progress = 1f,
                        alpha = if (transition == SceneTransition.CROSSFADE) 1f - transitionProgress else 1f,
                        slideFraction = if (transition == SceneTransition.SLIDE_LEFT) -transitionProgress else 0f,
                        colorFilter = filter
                    )
                }

                if (activeRender.bitmap != null) {
                    AnimatedMotionFrame(
                        bitmap = activeRender.bitmap,
                        motion = motion,
                        progress = localProgress,
                        alpha = if (prevRender != null && transition == SceneTransition.CROSSFADE && transitionProgress < 1f) {
                            transitionProgress
                        } else {
                            1f
                        },
                        slideFraction = if (prevRender != null && transition == SceneTransition.SLIDE_LEFT && transitionProgress < 1f) {
                            1f - transitionProgress
                        } else {
                            0f
                        },
                        colorFilter = filter
                    )
                }

                if (prevRender != null && transition == SceneTransition.FLASH_CUT && transitionProgress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = (1f - transitionProgress) * 0.7f))
                    )
                }

                val caption = activeRender.item.caption.ifBlank { state.overlayText }
                if (caption.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xB80B0D12),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                        .testTag("video_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Slider(
                    value = state.currentTimeMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..totalMs.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSaveProject,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(0.95f)
                        .height(48.dp)
                        .testTag("save_video_cloud_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cloud", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onSaveToGallery,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.05f)
                        .height(48.dp)
                        .testTag("save_video_to_gallery_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onExportMp4,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("export_mp4_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Export MP4", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.exportedMp4Path.isNotBlank()) {
                    IconButton(
                        onClick = onShareMp4,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("share_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share MP4",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedMotionFrame(
    bitmap: android.graphics.Bitmap,
    motion: CameraMotion,
    progress: Float,
    alpha: Float,
    slideFraction: Float,
    colorFilter: ColorFilter
) {
    val (scale, txFactor, tyFactor) = when (motion) {
        CameraMotion.ZOOM_IN -> Triple(1f + 0.14f * progress, 0f, 0f)
        CameraMotion.ZOOM_OUT -> Triple(1.14f - 0.14f * progress, 0f, 0f)
        CameraMotion.PAN_LEFT -> Triple(1.12f, 0.045f * (1f - 2f * progress), 0f)
        CameraMotion.PAN_RIGHT -> Triple(1.12f, 0.045f * (-1f + 2f * progress), 0f)
        CameraMotion.DRIFT_UP -> Triple(1.12f, 0f, 0.04f * (1f - 2f * progress))
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = (txFactor * size.width) + (slideFraction * size.width)
                translationY = tyFactor * size.height
                this.alpha = alpha
            }
    )
}

@Composable
private fun SelectedSceneInspector(
    sceneIndex: Int,
    scene: VideoSceneRender,
    editInstruction: String,
    isBusy: Boolean,
    onEditInstructionChange: (String) -> Unit,
    onApplySceneAiEdit: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onMotionChange: (CameraMotion) -> Unit,
    onTransitionChange: (SceneTransition) -> Unit,
    onDurationChange: (Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioSectionHeader(
                title = "Edit Scene ${sceneIndex + 1}",
                subtitle = "AI restyle this shot or adjust motion & subtitle"
            )

            OutlinedTextField(
                value = editInstruction,
                onValueChange = onEditInstructionChange,
                label = { Text("AI Scene Edit Prompt") },
                placeholder = { Text("e.g. Add falling neon rain & dramatic reflections") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_ai_edit_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onApplySceneAiEdit,
                enabled = !isBusy && editInstruction.isNotBlank() && scene.bitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_scene_ai_edit_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Edit Scene ${sceneIndex + 1}")
            }

            OutlinedTextField(
                value = scene.item.caption,
                onValueChange = onCaptionChange,
                label = { Text("Scene Subtitle / Caption") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "Camera Motion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeMotion = CameraMotion.fromId(scene.item.cameraMotion)
                CameraMotion.entries.forEach { motion ->
                    StudioChip(
                        label = motion.label,
                        selected = activeMotion == motion,
                        onClick = { onMotionChange(motion) },
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = "Transition",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeTrans = SceneTransition.fromId(scene.item.transition)
                SceneTransition.entries.forEach { trans ->
                    StudioChip(
                        label = trans.label,
                        selected = activeTrans == trans,
                        onClick = { onTransitionChange(trans) },
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Shot Duration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fs", scene.item.durationMs / 1000f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Slider(
                    value = scene.item.durationMs.toFloat(),
                    onValueChange = { onDurationChange(it.toLong()) },
                    valueRange = 1500f..6000f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }
    }
}

@Composable
private fun TextToVideoPromptCard(
    state: VideoStudioUiState,
    isBusy: Boolean,
    viewModel: StudioViewModel
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StudioSectionHeader(
                title = "Text to Video by Prompt",
                subtitle = "Generate a multi-scene animated video reel (~${state.sceneCount * 3}s)"
            )
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::updateVideoPrompt,
                placeholder = { Text("Describe your video story, setting, lighting, and mood...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_prompt_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VIDEO_PROMPT_STARTERS.forEach { (label, starterPrompt) ->
                    StudioChip(
                        label = label,
                        selected = state.prompt.trim() == starterPrompt,
                        onClick = { viewModel.updateVideoPrompt(starterPrompt) },
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = "Format",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AspectRatioPreset.PORTRAIT,
                    AspectRatioPreset.LANDSCAPE,
                    AspectRatioPreset.SQUARE
                ).forEach { preset ->
                    StudioChip(
                        label = preset.label,
                        selected = state.aspectRatio == preset,
                        onClick = { viewModel.updateVideoAspectRatio(preset) },
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = "Scenes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { count ->
                    StudioChip(
                        label = "$count Scenes",
                        selected = state.sceneCount == count,
                        onClick = { viewModel.updateVideoSceneCount(count) },
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = "Cinematography Style",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StylePreset.entries.forEach { style ->
                    StudioChip(
                        label = style.label,
                        selected = state.stylePreset == style,
                        onClick = { viewModel.updateVideoStyle(style) },
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Export MP4 File",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Render & save MP4 to Movies/FrameCraft right after generation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.autoExportMp4,
                    onCheckedChange = viewModel::toggleAutoExportMp4,
                    modifier = Modifier.testTag("auto_export_mp4_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.enhanceVideoPrompt() },
                    enabled = !isBusy && state.prompt.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("enhance_video_prompt_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enhance")
                }
                Button(
                    onClick = { viewModel.generateVideoReel() },
                    enabled = !isBusy && state.prompt.isNotBlank(),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("generate_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate Video")
                }
            }
        }
    }
}

@Composable
private fun VideoHistoryItemCard(
    project: StudioProject,
    isActive: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onSaveToGallery: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val exportedMp4File = remember(project.localMediaPath) {
        project.localMediaPath.takeIf { it.endsWith(".mp4") }?.let(::File)?.takeIf { it.exists() }
    }
    val thumbFile = remember(project.scenes, project.localMediaPath) {
        val path = project.scenes.firstOrNull()?.localFramePath?.takeIf { it.isNotBlank() }
            ?: project.localMediaPath.takeIf { it.isNotBlank() && !it.endsWith(".mp4") }
        path?.let(::File)?.takeIf { it.exists() }
    }
    val dateLabel = remember(project.updatedAt, project.createdAt) {
        val ts = project.updatedAt ?: project.createdAt
        ts?.toDate()?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it) }
            ?: "Just now"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
            .testTag("video_history_card_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbFile != null) {
                    val req = remember(thumbFile) {
                        ImageRequest.Builder(context)
                            .data(thumbFile)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = project.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0B0D12)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${project.aspectRatio} • ${project.scenes.size} scenes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = project.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (exportedMp4File != null) {
                    TextButton(
                        onClick = onSaveToGallery,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("save_video_history_gallery_${project.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save video to device gallery",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save to Gallery",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            IconButton(
                onClick = onRerun,
                enabled = !isBusy,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Re-run video prompt",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete video entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
