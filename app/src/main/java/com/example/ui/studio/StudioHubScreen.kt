package com.example.ui.studio

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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.StudioProject
import com.example.data.model.StylePreset
import java.io.File

private enum class HubFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    IMAGES("Images")
}

private data class PromptStarter(
    val label: String,
    val prompt: String,
    val mediaType: MediaType
)

private val PROMPT_STARTERS = listOf(
    PromptStarter(
        label = "Cyberpunk Rain Walk",
        prompt = "A lone wanderer walking through rain-slicked neon Tokyo alleyways with glowing coral and cyan reflections",
        mediaType = MediaType.VIDEO
    ),
    PromptStarter(
        label = "Alpine Dawn Drone",
        prompt = "Sweeping aerial drone sequence over mist-covered jagged alpine peaks at golden sunrise",
        mediaType = MediaType.VIDEO
    ),
    PromptStarter(
        label = "Editorial Prism Portrait",
        prompt = "High-fashion studio portrait illuminated by warm coral and cyan prism light streaks on obsidian backdrop",
        mediaType = MediaType.IMAGE
    ),
    PromptStarter(
        label = "Retro 35mm Cafe",
        prompt = "Sunlit Parisian corner cafe at golden hour shot on Kodak Portra 400 with warm film grain",
        mediaType = MediaType.IMAGE
    )
)

@Composable
fun StudioHubScreen(
    userDisplayName: String,
    projectsState: ProjectsUiState,
    onQuickCreate: (String, MediaType) -> Unit,
    onOpenProject: (StudioProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quickPrompt by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(MediaType.VIDEO) }
    var selectedFilter by remember { mutableStateOf(HubFilter.ALL) }
    var projectPendingDelete by remember { mutableStateOf<StudioProject?>(null) }
    var detailPayload by remember { mutableStateOf<ImageDetailPayload?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_framecraft_logo),
                            contentDescription = "FrameCraft",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FrameCraft",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = userDisplayName.ifBlank { "Creator Studio" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                // Hero Showcase + Unified Quick Create Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = "AI Video & Image Studio",
                        modifier = Modifier
                            .matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x990B0D12),
                                        Color(0xE8131720),
                                        Color(0xFA131720)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Generate Video & Images from One Prompt",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioChip(
                                label = "AI Video Reel",
                                selected = selectedMode == MediaType.VIDEO,
                                onClick = { selectedMode = MediaType.VIDEO },
                                accentColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            StudioChip(
                                label = "AI Image",
                                selected = selectedMode == MediaType.IMAGE,
                                onClick = { selectedMode = MediaType.IMAGE },
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = quickPrompt,
                            onValueChange = { quickPrompt = it },
                            placeholder = {
                                Text(
                                    if (selectedMode == MediaType.VIDEO) {
                                        "Describe a multi-scene video reel..."
                                    } else {
                                        "Describe an image or visual concept..."
                                    }
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hub_quick_prompt_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Button(
                            onClick = { onQuickCreate(quickPrompt, selectedMode) },
                            enabled = quickPrompt.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("hub_quick_create_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMode == MediaType.VIDEO) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (selectedMode == MediaType.VIDEO) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            )
                        ) {
                            Icon(
                                imageVector = if (selectedMode == MediaType.VIDEO) {
                                    Icons.Rounded.MovieCreation
                                } else {
                                    Icons.Rounded.AutoAwesome
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedMode == MediaType.VIDEO) {
                                    "Direct AI Video Reel"
                                } else {
                                    "Generate AI Image"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioSectionHeader(
                        title = "1-Tap Prompt Starters",
                        subtitle = "Launch straight into the AI director or image canvas"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PROMPT_STARTERS.forEach { starter ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .width(210.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        onQuickCreate(starter.prompt, starter.mediaType)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (starter.mediaType == MediaType.VIDEO) {
                                                Icons.Rounded.MovieCreation
                                            } else {
                                                Icons.Rounded.Image
                                            },
                                            contentDescription = null,
                                            tint = if (starter.mediaType == MediaType.VIDEO) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = starter.mediaType.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = starter.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = starter.prompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioSectionHeader(
                        title = "Your Cloud Studio Library",
                        subtitle = "Synced projects ready to re-edit or export"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubFilter.entries.forEach { filter ->
                            StudioChip(
                                label = filter.label,
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                }
            }

            when (projectsState) {
                is ProjectsUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is ProjectsUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = projectsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is ProjectsUiState.Success -> {
                    val filtered = projectsState.projects.filter { project ->
                        when (selectedFilter) {
                            HubFilter.ALL -> true
                            HubFilter.VIDEOS -> project.mediaType == MediaType.VIDEO.name
                            HubFilter.IMAGES -> project.mediaType == MediaType.IMAGE.name
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
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
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_empty_canvas),
                                        contentDescription = "Empty studio library",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "No projects yet",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Use the prompt bar above or pick a starter card to generate your first AI video reel or image.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { project ->
                            ProjectLibraryCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onInspectImage = {
                                    detailPayload = ImageDetailPayload(
                                        title = project.title,
                                        prompt = project.prompt,
                                        localMediaPath = project.localMediaPath,
                                        aspectRatioLabel = project.aspectRatio,
                                        styleLabel = StylePreset.fromId(project.stylePreset).label,
                                        revisionCount = project.editHistory.size.coerceAtLeast(1),
                                        overlayText = project.overlayText,
                                        sourceProject = project
                                    )
                                },
                                onDelete = { projectPendingDelete = project }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (projectPendingDelete != null) {
        val target = projectPendingDelete!!
        AlertDialog(
            onDismissRequest = { projectPendingDelete = null },
            title = { Text("Delete Project?") },
            text = { Text("Remove \"${target.title}\" from your cloud studio library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProject(target.id)
                        projectPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDelete = null }) {
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
                    onOpenProject(sourceProject)
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun ProjectLibraryCard(
    project: StudioProject,
    onOpen: () -> Unit,
    onInspectImage: () -> Unit,
    onDelete: () -> Unit
) {
    val isVideo = project.mediaType == MediaType.VIDEO.name
    val thumbPath = if (isVideo) {
        project.scenes.firstOrNull()?.localFramePath?.takeIf { File(it).exists() }
    } else {
        project.localMediaPath.takeIf { File(it).exists() }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (!isVideo && thumbPath != null) {
                            Modifier
                                .clickable(onClick = onInspectImage)
                                .testTag("hub_image_thumbnail_${project.id}")
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbPath != null) {
                    AsyncImage(
                        model = File(thumbPath),
                        contentDescription = project.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (!isVideo) {
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
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = if (isVideo) Icons.Rounded.MovieCreation else Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA0B0D12)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isVideo) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = if (isVideo) "VIDEO • ${project.scenes.size} SCENES" else "IMAGE • ${project.aspectRatio}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isVideo) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
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
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete project",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
