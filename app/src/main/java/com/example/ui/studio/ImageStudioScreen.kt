package com.example.ui.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.data.model.MediaType
import com.example.data.model.StudioProject
import com.example.data.model.StylePreset
import java.io.File
import java.text.DateFormat

private val QUICK_AI_EDITS = listOf(
    "Golden Hour" to "Bath the scene in warm golden hour sunset sunlight with soft lens flare",
    "Cyber Neon" to "Transform the lighting into futuristic cyan and coral cyberpunk neon reflections",
    "Studio Rim" to "Add dramatic studio rim lighting and creamy shallow depth of field bokeh",
    "35mm Film" to "Restyle as authentic analog 35mm film photography with subtle grain",
    "Watercolor" to "Transform into expressive fine-art editorial watercolor painting",
    "Clean Up" to "Remove distracting background clutter while keeping the main subject sharp"
)

@Composable
fun ImageStudioScreen(
    state: ImageStudioUiState,
    historyState: ProjectsUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImageFromPicker(uri)
        }
    }

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

    val isBusy = state.taskState is AiTaskState.Running
    val haptic = LocalHapticFeedback.current
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var detailPayload by remember { mutableStateOf<ImageDetailPayload?>(null) }

    val historyProjects = (historyState as? ProjectsUiState.Success)?.projects.orEmpty()
    val historyIds = remember(historyProjects) { historyProjects.map { it.id } }
    val allHistorySelected = historyIds.isNotEmpty() && historyIds.all { it in state.selectedHistoryIds }
    val selectedCount = state.selectedHistoryIds.size

    BackHandler(enabled = state.isHistorySelectionMode) {
        viewModel.exitHistorySelectionMode()
    }

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
                    Column {
                        Text(
                            text = "AI Image Studio",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Generate from text or edit photos with prompts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_photo_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Photo")
                    }
                }
            }

            item {
                AiTaskStatusBanner(taskState = state.taskState)
            }

            item {
                // Canvas Preview Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val displayBitmap = if (state.compareOriginal && state.originalBitmap != null) {
                            state.originalBitmap
                        } else {
                            state.activeBitmap
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(state.aspectRatio.ratioValue.coerceIn(0.56f, 1.8f))
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .then(
                                    if (displayBitmap != null) {
                                        Modifier
                                            .clickable {
                                                detailPayload = ImageDetailPayload(
                                                    title = state.title,
                                                    prompt = state.prompt.ifBlank { state.title },
                                                    bitmap = displayBitmap,
                                                    localMediaPath = state.localMediaPath,
                                                    aspectRatioLabel = state.aspectRatio.apiValue,
                                                    styleLabel = state.stylePreset.label,
                                                    revisionCount = state.editHistory.size.coerceAtLeast(1),
                                                    colorMatrixArray = if (state.compareOriginal) null else colorMatrixArray,
                                                    overlayText = if (state.compareOriginal) "" else state.overlayText
                                                )
                                            }
                                            .testTag("canvas_preview_image")
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayBitmap != null) {
                                Image(
                                    bitmap = displayBitmap.asImageBitmap(),
                                    contentDescription = state.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (state.compareOriginal) null else ColorFilter.colorMatrix(
                                        ColorMatrix(colorMatrixArray)
                                    )
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xA60B0D12),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInFull,
                                            contentDescription = "Expand image",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Expand",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                                if (state.overlayText.isNotBlank() && !state.compareOriginal) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xAA0B0D12),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 18.dp, start = 16.dp, end = 16.dp)
                                    ) {
                                        Text(
                                            text = state.overlayText,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                if (state.compareOriginal) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xCC0B0D12),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "ORIGINAL",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_empty_canvas),
                                        contentDescription = "Image canvas ready",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Canvas Ready",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Generate from a prompt below or pick a photo to edit.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (state.activeBitmap != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.undoImageEdit() },
                                        enabled = state.undoStack.isNotEmpty() && !isBusy,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                                            contentDescription = "Undo edit"
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.toggleCompareOriginal(!state.compareOriginal) },
                                        enabled = state.originalBitmap != null,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Compare,
                                            contentDescription = "Compare original",
                                            tint = if (state.compareOriginal) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { viewModel.saveCurrentImageProject() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("save_image_cloud_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudUpload,
                                            contentDescription = "Save to cloud",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.saveCurrentImageToGallery() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("save_image_to_gallery_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = "Save to gallery",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.shareCurrentImage(context) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("share_image_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share image",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.activeBitmap != null) {
                item {
                    // AI Image-to-Image Prompt Editor
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
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudioSectionHeader(
                                title = "AI Prompt Editor",
                                subtitle = "Describe any change — Nano Banana edits the frame"
                            )
                            OutlinedTextField(
                                value = state.editInstruction,
                                onValueChange = viewModel::updateImageEditInstruction,
                                placeholder = { Text("e.g. Replace background with neon Tokyo rain...") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("image_edit_instruction_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QUICK_AI_EDITS.forEach { (label, instruction) ->
                                    StudioChip(
                                        label = label,
                                        selected = false,
                                        onClick = {
                                            if (!isBusy) viewModel.applyAiImageEdit(instruction)
                                        },
                                        accentColor = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.applyAiImageEdit() },
                                enabled = !isBusy && state.editInstruction.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("apply_ai_image_edit_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apply AI Edit")
                            }
                        }
                    }
                }
            }

            item {
                // Text-to-Image Generator Panel
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
                            title = "Generate New Image",
                            subtitle = "Create custom artwork or photography from text"
                        )
                        OutlinedTextField(
                            value = state.prompt,
                            onValueChange = viewModel::updateImagePrompt,
                            placeholder = { Text("Describe subject, lighting, mood, camera lens...") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("image_prompt_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text(
                            text = "Aspect Ratio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AspectRatioPreset.entries.forEach { preset ->
                                StudioChip(
                                    label = preset.label,
                                    selected = state.aspectRatio == preset,
                                    onClick = { viewModel.updateImageAspectRatio(preset) }
                                )
                            }
                        }

                        Text(
                            text = "Visual Style",
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
                                    onClick = { viewModel.updateImageStyle(style) }
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
                                    text = "Pro Image Engine (Nano Banana Pro)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Higher studio fidelity & crisp typography",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.useProModel,
                                onCheckedChange = viewModel::toggleImageProModel
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.enhanceImagePrompt() },
                                enabled = !isBusy && state.prompt.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("enhance_image_prompt_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enhance")
                            }
                            Button(
                                onClick = { viewModel.generateImageFromPrompt() },
                                enabled = !isBusy && state.prompt.isNotBlank(),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                                    .testTag("generate_image_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate Image")
                            }
                        }
                    }
                }
            }

            if (state.activeBitmap != null) {
                item {
                    ColorGradeAndTuningPanel(
                        selectedGrade = state.colorGrade,
                        exposure = state.exposure,
                        contrast = state.contrast,
                        saturation = state.saturation,
                        warmth = state.warmth,
                        overlayText = state.overlayText,
                        onGradeChange = { viewModel.updateImageAdjustments(colorGrade = it) },
                        onExposureChange = { viewModel.updateImageAdjustments(exposure = it) },
                        onContrastChange = { viewModel.updateImageAdjustments(contrast = it) },
                        onSaturationChange = { viewModel.updateImageAdjustments(saturation = it) },
                        onWarmthChange = { viewModel.updateImageAdjustments(warmth = it) },
                        onOverlayTextChange = { viewModel.updateImageAdjustments(overlayText = it) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (state.isHistorySelectionMode) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_selection_toolbar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.exitHistorySelectionMode() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("exit_selection_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Exit selection mode",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "$selectedCount selected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.selectAllImageHistory(historyIds) },
                                    enabled = historyIds.isNotEmpty() && !state.isBatchDeletingHistory,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("select_all_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (allHistorySelected) "Clear" else "Select All",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { showBatchDeleteConfirm = true },
                                    enabled = selectedCount > 0 && !state.isBatchDeletingHistory,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("toolbar_batch_delete_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete selected images",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                } else {
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
                            title = "Previous Prompts & Results",
                            subtitle = "Saved in Cloud Firestore — tap to reload, long-press to select"
                        )
                    }
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
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                    text = "Your generated image prompts and results will appear here automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(historyState.projects, key = { it.id }) { item ->
                            val isSelected = item.id in state.selectedHistoryIds
                            ImageHistoryItemCard(
                                project = item,
                                isActive = item.id == state.projectId,
                                isSelectionMode = state.isHistorySelectionMode,
                                isSelected = isSelected,
                                isBusy = isBusy || state.isBatchDeletingHistory,
                                onSelect = {
                                    if (state.isHistorySelectionMode) {
                                        viewModel.toggleHistorySelection(item.id)
                                    } else {
                                        viewModel.openProject(item) { _ -> }
                                    }
                                },
                                onInspect = {
                                    val ts = item.updatedAt ?: item.createdAt
                                    val formattedDate = ts?.toDate()?.let {
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it)
                                    }
                                    detailPayload = ImageDetailPayload(
                                        title = item.title,
                                        prompt = item.prompt,
                                        localMediaPath = item.localMediaPath,
                                        aspectRatioLabel = item.aspectRatio,
                                        styleLabel = StylePreset.fromId(item.stylePreset).label,
                                        timestampLabel = formattedDate,
                                        revisionCount = item.editHistory.size.coerceAtLeast(1),
                                        overlayText = item.overlayText,
                                        sourceProject = item
                                    )
                                },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (state.isHistorySelectionMode) {
                                        viewModel.toggleHistorySelection(item.id)
                                    } else {
                                        viewModel.enterHistorySelectionMode(item.id)
                                    }
                                },
                                onRerun = {
                                    viewModel.updateImageAspectRatio(
                                        AspectRatioPreset.fromApiValue(item.aspectRatio)
                                    )
                                    viewModel.updateImageStyle(StylePreset.fromId(item.stylePreset))
                                    viewModel.generateImageFromPrompt(item.prompt)
                                },
                                onDelete = {
                                    viewModel.deleteProject(item.id)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(if (state.isHistorySelectionMode) 96.dp else 16.dp))
            }
        }

        AnimatedVisibility(
            visible = state.isHistorySelectionMode && selectedCount > 0,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.exitHistorySelectionMode() },
                            enabled = !state.isBatchDeletingHistory,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel selection",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { viewModel.selectAllImageHistory(historyIds) },
                            enabled = historyIds.isNotEmpty() && !state.isBatchDeletingHistory,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("floating_select_all_button")
                        ) {
                            Text(if (allHistorySelected) "None" else "All")
                        }

                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            enabled = !state.isBatchDeletingHistory,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("batch_delete_history_button")
                        ) {
                            if (state.isBatchDeletingHistory) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete ($selectedCount)")
                        }
                    }
                }
            }
        }
    }

    if (showBatchDeleteConfirm && selectedCount > 0) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isBatchDeletingHistory) showBatchDeleteConfirm = false
            },
            title = {
                Text(
                    text = if (selectedCount == 1) {
                        "Delete 1 generated image?"
                    } else {
                        "Delete $selectedCount generated images?"
                    }
                )
            },
            text = {
                Text("Selected prompts and results will be permanently removed from your cloud history.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirm = false
                        viewModel.deleteSelectedImageHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_batch_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteConfirm = false },
                    modifier = Modifier.testTag("cancel_batch_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val activeDetail = detailPayload
    if (activeDetail != null) {
        val sourceProject = activeDetail.sourceProject
        ExpandedImageDetailDialog(
            payload = activeDetail,
            onDismiss = { detailPayload = null },
            onEditInStudio = if (sourceProject != null) {
                {
                    detailPayload = null
                    viewModel.openProject(sourceProject) { _ -> }
                }
            } else {
                null
            },
            onRerunPrompt = if (sourceProject != null && !isBusy) {
                {
                    detailPayload = null
                    viewModel.updateImageAspectRatio(
                        AspectRatioPreset.fromApiValue(sourceProject.aspectRatio)
                    )
                    viewModel.updateImageStyle(StylePreset.fromId(sourceProject.stylePreset))
                    viewModel.generateImageFromPrompt(sourceProject.prompt)
                }
            } else {
                null
            },
            onSaveToGallery = {
                if (sourceProject != null) {
                    viewModel.saveImageDetailToGallery(
                        localPath = sourceProject.localMediaPath,
                        title = sourceProject.title
                    )
                } else {
                    viewModel.saveCurrentImageToGallery()
                }
            },
            onShare = {
                if (sourceProject != null) {
                    viewModel.shareImageDetail(
                        activityContext = context,
                        localPath = sourceProject.localMediaPath,
                        title = sourceProject.title
                    )
                } else {
                    viewModel.shareCurrentImage(context)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageHistoryItemCard(
    project: StudioProject,
    isActive: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onInspect: () -> Unit,
    onLongPress: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val mediaFile = remember(project.localMediaPath) {
        project.localMediaPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }
    val dateLabel = remember(project.updatedAt, project.createdAt) {
        val ts = project.updatedAt ?: project.createdAt
        ts?.toDate()?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it) }
            ?: "Just now"
    }

    val cardContainerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val cardBorderWidth = if (isSelected) 2.dp else if (isActive) 1.5.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardContainerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = cardBorderWidth,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress,
                onLongClickLabel = if (isSelectionMode) "Toggle selection" else "Select generated image"
            )
            .testTag("image_history_card_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .padding(end = 4.dp)
                        .testTag("history_selection_checkbox_${project.id}")
                )
            }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onSelect() else onInspect()
                        },
                        onLongClick = onLongPress,
                        onLongClickLabel = if (isSelectionMode) "Toggle selection" else "Select generated image"
                    )
                    .testTag("history_thumbnail_${project.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (mediaFile != null) {
                    val req = remember(mediaFile) {
                        ImageRequest.Builder(context)
                            .data(mediaFile)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = project.prompt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xB30B0D12))
                                .padding(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = "View full-size image",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${project.aspectRatio} • ${StylePreset.fromId(project.stylePreset).label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    text = project.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (project.editHistory.size > 1) {
                    Text(
                        text = "${project.editHistory.size} revisions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            AnimatedVisibility(visible = !isSelectionMode) {
                Row {
                    IconButton(
                        onClick = onRerun,
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Re-run prompt",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete history entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
