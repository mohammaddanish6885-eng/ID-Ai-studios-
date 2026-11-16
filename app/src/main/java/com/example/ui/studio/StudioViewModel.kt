package com.example.ui.studio

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.FrameCraftAiService
import com.example.data.media.LocalMediaStorage
import com.example.data.media.Mp4VideoExporter
import com.example.data.model.AspectRatioPreset
import com.example.data.model.CameraMotion
import com.example.data.model.ColorGradePreset
import com.example.data.model.MediaType
import com.example.data.model.SceneTransition
import com.example.data.model.StudioProject
import com.example.data.model.StylePreset
import com.example.data.model.VideoSceneItem
import com.example.data.repository.StudioProjectRepository
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(val projects: List<StudioProject>) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Running(val status: String, val progress: Float? = null) : AiTaskState
    data class Success(val message: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

data class ImageStudioUiState(
    val projectId: String = "",
    val title: String = "Untitled Frame",
    val prompt: String = "",
    val editInstruction: String = "",
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.SQUARE,
    val stylePreset: StylePreset = StylePreset.CINEMATIC,
    val useProModel: Boolean = false,
    val colorGrade: ColorGradePreset = ColorGradePreset.ORIGINAL,
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val overlayText: String = "",
    val activeBitmap: Bitmap? = null,
    val originalBitmap: Bitmap? = null,
    val undoStack: List<Bitmap> = emptyList(),
    val compareOriginal: Boolean = false,
    val localMediaPath: String = "",
    val editHistory: List<String> = emptyList(),
    val taskState: AiTaskState = AiTaskState.Idle,
    val isHistorySelectionMode: Boolean = false,
    val selectedHistoryIds: Set<String> = emptySet(),
    val isBatchDeletingHistory: Boolean = false
)

data class VideoSceneRender(
    val item: VideoSceneItem,
    val bitmap: Bitmap?
)

data class VideoStudioUiState(
    val projectId: String = "",
    val title: String = "Untitled Reel",
    val prompt: String = "",
    val sceneCount: Int = 4,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.PORTRAIT,
    val stylePreset: StylePreset = StylePreset.CINEMATIC,
    val colorGrade: ColorGradePreset = ColorGradePreset.CINEMATIC_TEAL,
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val overlayText: String = "",
    val scenes: List<VideoSceneRender> = emptyList(),
    val selectedSceneIndex: Int = 0,
    val sceneEditInstruction: String = "",
    val isPlaying: Boolean = false,
    val currentTimeMs: Long = 0L,
    val exportedMp4Path: String = "",
    val autoExportMp4: Boolean = false,
    val editHistory: List<String> = emptyList(),
    val taskState: AiTaskState = AiTaskState.Idle,
    val exportState: AiTaskState = AiTaskState.Idle
) {
    val totalDurationMs: Long
        get() = scenes.sumOf { it.item.durationMs.coerceIn(1500L, 8000L) }.coerceAtLeast(1000L)
}

class StudioViewModel(
    private val app: Application,
    private val repository: StudioProjectRepository,
    private val aiService: FrameCraftAiService,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<ProjectsUiState> = repository.observeProjects(currentUserId)
        .map<List<StudioProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing studio projects", rootError)
            emit(ProjectsUiState.Error(rootError.localizedMessage ?: "Failed to sync projects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectsUiState.Loading
        )

    val imageHistoryState: StateFlow<ProjectsUiState> = repository.observeImageHistory(currentUserId)
        .map<List<StudioProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing image history", rootError)
            emit(ProjectsUiState.Error(rootError.localizedMessage ?: "Failed to load image history."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectsUiState.Loading
        )

    val videoHistoryState: StateFlow<ProjectsUiState> = repository.observeVideoHistory(currentUserId)
        .map<List<StudioProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing video history", rootError)
            emit(ProjectsUiState.Error(rootError.localizedMessage ?: "Failed to load video history."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectsUiState.Loading
        )

    private val _imageState = MutableStateFlow(ImageStudioUiState())
    val imageState: StateFlow<ImageStudioUiState> = _imageState.asStateFlow()

    private val _videoState = MutableStateFlow(VideoStudioUiState())
    val videoState: StateFlow<VideoStudioUiState> = _videoState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        startPlaybackTicker()
    }

    private fun startPlaybackTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(33L)
                val current = _videoState.value
                if (current.isPlaying && current.scenes.isNotEmpty()) {
                    val total = current.totalDurationMs
                    val nextTime = current.currentTimeMs + 33L
                    _videoState.update {
                        it.copy(currentTimeMs = if (nextTime >= total) 0L else nextTime)
                    }
                }
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // --- Image Studio Actions ---

    fun updateImagePrompt(value: String) {
        _imageState.update { it.copy(prompt = value) }
    }

    fun updateImageEditInstruction(value: String) {
        _imageState.update { it.copy(editInstruction = value) }
    }

    fun updateImageAspectRatio(aspectRatio: AspectRatioPreset) {
        _imageState.update { it.copy(aspectRatio = aspectRatio) }
    }

    fun updateImageStyle(style: StylePreset) {
        _imageState.update { it.copy(stylePreset = style) }
    }

    fun toggleImageProModel(enabled: Boolean) {
        _imageState.update { it.copy(useProModel = enabled) }
    }

    fun toggleCompareOriginal(compare: Boolean) {
        _imageState.update { it.copy(compareOriginal = compare) }
    }

    fun updateImageAdjustments(
        colorGrade: ColorGradePreset? = null,
        exposure: Float? = null,
        contrast: Float? = null,
        saturation: Float? = null,
        warmth: Float? = null,
        overlayText: String? = null
    ) {
        _imageState.update { state ->
            state.copy(
                colorGrade = colorGrade ?: state.colorGrade,
                exposure = exposure ?: state.exposure,
                contrast = contrast ?: state.contrast,
                saturation = saturation ?: state.saturation,
                warmth = warmth ?: state.warmth,
                overlayText = overlayText ?: state.overlayText
            )
        }
    }

    fun enhanceImagePrompt() {
        val raw = _imageState.value.prompt.trim()
        if (raw.isBlank()) {
            _toastMessage.value = "Enter a short idea first to enhance."
            return
        }
        viewModelScope.launch {
            _imageState.update { it.copy(taskState = AiTaskState.Running("Enhancing prompt with Gemini...")) }
            try {
                val enhanced = aiService.enhancePrompt(
                    rawPrompt = raw,
                    mediaType = MediaType.IMAGE,
                    style = _imageState.value.stylePreset
                )
                _imageState.update {
                    it.copy(
                        prompt = enhanced,
                        taskState = AiTaskState.Success("Prompt enhanced")
                    )
                }
            } catch (e: Exception) {
                handleImageTaskError(e, "Prompt enhancement")
            }
        }
    }

    fun generateImageFromPrompt(customPrompt: String? = null) {
        val state = _imageState.value
        val activePrompt = (customPrompt ?: state.prompt).trim()
        if (activePrompt.isBlank()) {
            _toastMessage.value = "Describe the image you want to create."
            return
        }
        viewModelScope.launch {
            _imageState.update {
                it.copy(
                    prompt = activePrompt,
                    taskState = AiTaskState.Running("Synthesizing image with Nano Banana...")
                )
            }
            try {
                val bitmap = aiService.generateImage(
                    prompt = activePrompt,
                    aspectRatio = state.aspectRatio,
                    style = state.stylePreset,
                    useProModel = state.useProModel
                )
                val savedPath = LocalMediaStorage.saveBitmap(app, bitmap, "img")
                val generatedTitle = activePrompt.split(" ").take(4).joinToString(" ")
                    .replaceFirstChar { it.uppercase() }.ifBlank { "AI Frame" }
                val history = listOf("Generated: $activePrompt")

                val project = StudioProject(
                    id = "",
                    userId = currentUserId,
                    title = generatedTitle,
                    prompt = activePrompt,
                    mediaType = MediaType.IMAGE.name,
                    aspectRatio = state.aspectRatio.apiValue,
                    stylePreset = state.stylePreset.id,
                    colorGrade = state.colorGrade.id,
                    exposure = state.exposure.toDouble(),
                    contrast = state.contrast.toDouble(),
                    saturation = state.saturation.toDouble(),
                    warmth = state.warmth.toDouble(),
                    overlayText = state.overlayText,
                    localMediaPath = savedPath,
                    scenes = emptyList(),
                    editHistory = history
                )
                val finalId = persistProject(project)

                _imageState.update {
                    it.copy(
                        projectId = finalId,
                        title = generatedTitle,
                        activeBitmap = bitmap,
                        originalBitmap = bitmap,
                        undoStack = if (it.activeBitmap != null) (it.undoStack + it.activeBitmap).takeLast(6) else emptyList(),
                        localMediaPath = savedPath,
                        editHistory = history,
                        compareOriginal = false,
                        taskState = AiTaskState.Success("Image generated & saved to history")
                    )
                }
            } catch (e: Exception) {
                handleImageTaskError(e, "Image generation")
            }
        }
    }

    fun applyAiImageEdit(instructionOverride: String? = null) {
        val state = _imageState.value
        val source = state.activeBitmap
        if (source == null) {
            _toastMessage.value = "Generate or pick a photo first."
            return
        }
        val instruction = (instructionOverride ?: state.editInstruction).trim()
        if (instruction.isBlank()) {
            _toastMessage.value = "Describe how you want to edit this image."
            return
        }

        viewModelScope.launch {
            _imageState.update {
                it.copy(
                    editInstruction = instruction,
                    taskState = AiTaskState.Running("Applying AI edit: \"$instruction\"...")
                )
            }
            try {
                val payloadBitmap = LocalMediaStorage.scaleDownBitmap(source, 1280)
                val edited = aiService.editImage(
                    sourceBitmap = payloadBitmap,
                    editInstruction = instruction,
                    aspectRatio = state.aspectRatio,
                    useProModel = state.useProModel
                )
                val savedPath = LocalMediaStorage.saveBitmap(app, edited, "edit")
                val history = (state.editHistory + "Edit: $instruction").takeLast(20)
                val project = StudioProject(
                    id = state.projectId,
                    userId = currentUserId,
                    title = state.title,
                    prompt = state.prompt.ifBlank { instruction },
                    mediaType = MediaType.IMAGE.name,
                    aspectRatio = state.aspectRatio.apiValue,
                    stylePreset = state.stylePreset.id,
                    colorGrade = state.colorGrade.id,
                    exposure = state.exposure.toDouble(),
                    contrast = state.contrast.toDouble(),
                    saturation = state.saturation.toDouble(),
                    warmth = state.warmth.toDouble(),
                    overlayText = state.overlayText,
                    localMediaPath = savedPath,
                    scenes = emptyList(),
                    editHistory = history
                )
                val finalId = persistProject(project)

                _imageState.update {
                    it.copy(
                        projectId = finalId,
                        activeBitmap = edited,
                        originalBitmap = it.originalBitmap ?: source,
                        undoStack = (it.undoStack + source).takeLast(6),
                        localMediaPath = savedPath,
                        editHistory = history,
                        editInstruction = "",
                        compareOriginal = false,
                        taskState = AiTaskState.Success("AI edit applied")
                    )
                }
            } catch (e: Exception) {
                handleImageTaskError(e, "AI image edit")
            }
        }
    }

    fun loadImageFromPicker(uri: Uri) {
        viewModelScope.launch {
            _imageState.update { it.copy(taskState = AiTaskState.Running("Importing photo...")) }
            try {
                val decoded = LocalMediaStorage.decodeUriBitmap(app, uri, 1280)
                    ?: throw IllegalStateException("Could not decode the selected image.")
                val savedPath = LocalMediaStorage.saveBitmap(app, decoded, "import")
                _imageState.update {
                    ImageStudioUiState(
                        title = "Imported Photo Edit",
                        prompt = "Imported photo",
                        aspectRatio = AspectRatioPreset.SQUARE,
                        stylePreset = it.stylePreset,
                        activeBitmap = decoded,
                        originalBitmap = decoded,
                        localMediaPath = savedPath,
                        editHistory = listOf("Imported photo from device"),
                        taskState = AiTaskState.Success("Photo ready to edit with AI")
                    )
                }
            } catch (e: Exception) {
                handleImageTaskError(e, "Photo import")
            }
        }
    }

    fun undoImageEdit() {
        val state = _imageState.value
        val previous = state.undoStack.lastOrNull() ?: return
        viewModelScope.launch {
            val savedPath = LocalMediaStorage.saveBitmap(app, previous, "undo")
            _imageState.update {
                it.copy(
                    activeBitmap = previous,
                    undoStack = it.undoStack.dropLast(1),
                    localMediaPath = savedPath,
                    compareOriginal = false
                )
            }
            _toastMessage.value = "Reverted last edit"
        }
    }

    fun saveCurrentImageProject() {
        val state = _imageState.value
        val bitmap = state.activeBitmap ?: return
        viewModelScope.launch {
            try {
                val rendered = LocalMediaStorage.renderFilteredImage(
                    source = bitmap,
                    grade = state.colorGrade,
                    exposure = state.exposure,
                    contrast = state.contrast,
                    saturation = state.saturation,
                    warmth = state.warmth,
                    overlayText = state.overlayText
                )
                val savedPath = LocalMediaStorage.saveBitmap(app, rendered, "saved")
                val project = StudioProject(
                    id = state.projectId,
                    userId = currentUserId,
                    title = state.title.ifBlank { "AI Image Edit" },
                    prompt = state.prompt.ifBlank { "Custom Image Edit" },
                    mediaType = MediaType.IMAGE.name,
                    aspectRatio = state.aspectRatio.apiValue,
                    stylePreset = state.stylePreset.id,
                    colorGrade = state.colorGrade.id,
                    exposure = state.exposure.toDouble(),
                    contrast = state.contrast.toDouble(),
                    saturation = state.saturation.toDouble(),
                    warmth = state.warmth.toDouble(),
                    overlayText = state.overlayText,
                    localMediaPath = savedPath,
                    scenes = emptyList(),
                    editHistory = state.editHistory
                )
                val finalId = persistProject(project)
                _imageState.update { it.copy(projectId = finalId, localMediaPath = savedPath) }
                _toastMessage.value = "Saved project to cloud"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastMessage.value = e.localizedMessage ?: "Failed to save project"
            }
        }
    }

    fun saveCurrentImageToGallery() {
        val state = _imageState.value
        val bitmap = state.activeBitmap ?: return
        viewModelScope.launch {
            val rendered = LocalMediaStorage.renderFilteredImage(
                source = bitmap,
                grade = state.colorGrade,
                exposure = state.exposure,
                contrast = state.contrast,
                saturation = state.saturation,
                warmth = state.warmth,
                overlayText = state.overlayText
            )
            val result = LocalMediaStorage.saveImageToGallery(app, rendered, state.title)
            _toastMessage.value = if (result.isSuccess) {
                "Saved to Pictures/FrameCraft"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to save to gallery"
            }
        }
    }

    fun shareCurrentImage(activityContext: android.content.Context) {
        val state = _imageState.value
        val bitmap = state.activeBitmap ?: return
        viewModelScope.launch {
            try {
                val rendered = LocalMediaStorage.renderFilteredImage(
                    source = bitmap,
                    grade = state.colorGrade,
                    exposure = state.exposure,
                    contrast = state.contrast,
                    saturation = state.saturation,
                    warmth = state.warmth,
                    overlayText = state.overlayText
                )
                LocalMediaStorage.shareBitmap(activityContext, rendered, state.title)
            } catch (e: Exception) {
                _toastMessage.value = e.localizedMessage ?: "Unable to share image"
            }
        }
    }

    fun saveImageDetailToGallery(localPath: String, title: String) {
        if (localPath.isBlank()) {
            _toastMessage.value = "Image file not found on device"
            return
        }
        viewModelScope.launch {
            val result = LocalMediaStorage.saveImageFileToGallery(app, localPath, title)
            _toastMessage.value = if (result.isSuccess) {
                "Saved to Pictures/FrameCraft"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to save to gallery"
            }
        }
    }

    fun shareImageDetail(activityContext: android.content.Context, localPath: String, title: String) {
        if (localPath.isBlank()) {
            _toastMessage.value = "Image file not found on device"
            return
        }
        viewModelScope.launch {
            try {
                val bitmap = LocalMediaStorage.loadBitmap(localPath, 2048)
                if (bitmap == null) {
                    _toastMessage.value = "Could not load full-size image"
                    return@launch
                }
                LocalMediaStorage.shareBitmap(activityContext, bitmap, title)
            } catch (e: Exception) {
                _toastMessage.value = e.localizedMessage ?: "Unable to share image"
            }
        }
    }

    // --- Video Studio Actions ---

    fun updateVideoPrompt(value: String) {
        _videoState.update { it.copy(prompt = value) }
    }

    fun updateVideoTitle(value: String) {
        _videoState.update { it.copy(title = value) }
    }

    fun updateVideoSceneCount(count: Int) {
        _videoState.update { it.copy(sceneCount = count.coerceIn(2, 6)) }
    }

    fun updateVideoAspectRatio(aspectRatio: AspectRatioPreset) {
        _videoState.update { it.copy(aspectRatio = aspectRatio) }
    }

    fun updateVideoStyle(style: StylePreset) {
        _videoState.update { it.copy(stylePreset = style) }
    }

    fun toggleAutoExportMp4(enabled: Boolean) {
        _videoState.update { it.copy(autoExportMp4 = enabled) }
    }

    fun updateVideoAdjustments(
        colorGrade: ColorGradePreset? = null,
        exposure: Float? = null,
        contrast: Float? = null,
        saturation: Float? = null,
        warmth: Float? = null,
        overlayText: String? = null
    ) {
        _videoState.update { state ->
            state.copy(
                colorGrade = colorGrade ?: state.colorGrade,
                exposure = exposure ?: state.exposure,
                contrast = contrast ?: state.contrast,
                saturation = saturation ?: state.saturation,
                warmth = warmth ?: state.warmth,
                overlayText = overlayText ?: state.overlayText
            )
        }
    }

    fun toggleVideoPlayback() {
        _videoState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun seekVideo(timeMs: Long) {
        _videoState.update { state ->
            state.copy(currentTimeMs = timeMs.coerceIn(0L, state.totalDurationMs))
        }
    }

    fun selectVideoScene(index: Int) {
        _videoState.update { state ->
            if (index !in state.scenes.indices) return@update state
            var startMs = 0L
            for (i in 0 until index) {
                startMs += state.scenes[i].item.durationMs.coerceIn(1500L, 8000L)
            }
            state.copy(
                selectedSceneIndex = index,
                currentTimeMs = startMs,
                isPlaying = false
            )
        }
    }

    fun updateSceneEditInstruction(value: String) {
        _videoState.update { it.copy(sceneEditInstruction = value) }
    }

    fun updateSelectedSceneProps(
        caption: String? = null,
        cameraMotion: CameraMotion? = null,
        transition: SceneTransition? = null,
        durationMs: Long? = null
    ) {
        _videoState.update { state ->
            val idx = state.selectedSceneIndex
            if (idx !in state.scenes.indices) return@update state
            val updatedScenes = state.scenes.mapIndexed { i, render ->
                if (i != idx) render else render.copy(
                    item = render.item.copy(
                        caption = caption ?: render.item.caption,
                        cameraMotion = cameraMotion?.id ?: render.item.cameraMotion,
                        transition = transition?.id ?: render.item.transition,
                        durationMs = durationMs?.coerceIn(1500L, 8000L) ?: render.item.durationMs
                    )
                )
            }
            state.copy(scenes = updatedScenes)
        }
    }

    fun enhanceVideoPrompt() {
        val raw = _videoState.value.prompt.trim()
        if (raw.isBlank()) {
            _toastMessage.value = "Enter a video concept first to enhance."
            return
        }
        viewModelScope.launch {
            _videoState.update { it.copy(taskState = AiTaskState.Running("Enhancing director prompt...")) }
            try {
                val enhanced = aiService.enhancePrompt(
                    rawPrompt = raw,
                    mediaType = MediaType.VIDEO,
                    style = _videoState.value.stylePreset
                )
                _videoState.update {
                    it.copy(
                        prompt = enhanced,
                        taskState = AiTaskState.Success("Director prompt enhanced")
                    )
                }
            } catch (e: Exception) {
                handleVideoTaskError(e, "Prompt enhancement")
            }
        }
    }

    fun generateVideoReel(customPrompt: String? = null) {
        val state = _videoState.value
        val activePrompt = (customPrompt ?: state.prompt).trim()
        if (activePrompt.isBlank()) {
            _toastMessage.value = "Describe the video reel you want to create."
            return
        }

        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    prompt = activePrompt,
                    isPlaying = false,
                    exportedMp4Path = "",
                    taskState = AiTaskState.Running("Directing ${state.sceneCount}-scene storyboard...", 0.08f)
                )
            }
            try {
                val storyboard = aiService.directVideoStoryboard(
                    userPrompt = activePrompt,
                    style = state.stylePreset,
                    sceneCount = state.sceneCount
                )

                val renderedScenes = mutableListOf<VideoSceneRender>()
                val total = storyboard.scenes.size
                storyboard.scenes.forEachIndexed { idx, draft ->
                    _videoState.update {
                        it.copy(
                            taskState = AiTaskState.Running(
                                status = "Synthesizing Scene ${idx + 1} of $total...",
                                progress = (idx + 0.5f) / total.toFloat()
                            )
                        )
                    }
                    val bitmap = aiService.generateImage(
                        prompt = draft.visualPrompt,
                        aspectRatio = state.aspectRatio,
                        style = state.stylePreset,
                        useProModel = false
                    )
                    val path = LocalMediaStorage.saveBitmap(app, bitmap, "scene_$idx")
                    val item = VideoSceneItem(
                        sceneIndex = idx.toLong(),
                        visualPrompt = draft.visualPrompt,
                        caption = draft.caption,
                        cameraMotion = draft.cameraMotion.id,
                        transition = draft.transition.id,
                        durationMs = draft.durationMs,
                        localFramePath = path
                    )
                    renderedScenes.add(VideoSceneRender(item = item, bitmap = bitmap))
                }

                val history = listOf("Directed $total-scene reel from prompt")
                val project = StudioProject(
                    id = "",
                    userId = currentUserId,
                    title = storyboard.title,
                    prompt = activePrompt,
                    mediaType = MediaType.VIDEO.name,
                    aspectRatio = state.aspectRatio.apiValue,
                    stylePreset = state.stylePreset.id,
                    colorGrade = storyboard.colorGrade.id,
                    exposure = 0.0,
                    contrast = 1.0,
                    saturation = 1.0,
                    warmth = 0.0,
                    overlayText = "",
                    localMediaPath = renderedScenes.firstOrNull()?.item?.localFramePath ?: "",
                    scenes = renderedScenes.map { it.item },
                    editHistory = history
                )
                val finalId = persistProject(project)

                _videoState.update {
                    it.copy(
                        projectId = finalId,
                        title = storyboard.title,
                        colorGrade = storyboard.colorGrade,
                        scenes = renderedScenes,
                        selectedSceneIndex = 0,
                        currentTimeMs = 0L,
                        isPlaying = true,
                        editHistory = history,
                        taskState = AiTaskState.Success("AI Video Reel ready to edit & export")
                    )
                }
                if (state.autoExportMp4) {
                    exportVideoMp4()
                }
            } catch (e: Exception) {
                handleVideoTaskError(e, "AI Video generation")
            }
        }
    }

    fun importVideoClip(uri: Uri) {
        val state = _videoState.value
        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    isPlaying = false,
                    exportedMp4Path = "",
                    taskState = AiTaskState.Running("Extracting frames from video clip...", 0.25f)
                )
            }
            try {
                val frames = LocalMediaStorage.extractVideoFrames(app, uri, frameCount = 4)
                if (frames.isEmpty()) {
                    throw IllegalStateException("Could not extract frames from the selected video.")
                }
                _videoState.update {
                    it.copy(taskState = AiTaskState.Running("Analyzing clip with Gemini Vision...", 0.65f))
                }
                val storyboard = aiService.analyzeFramesToStoryboard(
                    frames = frames,
                    userDirection = state.prompt,
                    style = state.stylePreset
                )
                val renders = frames.mapIndexed { idx, bmp ->
                    val draft = storyboard.scenes.getOrNull(idx)
                    val path = LocalMediaStorage.saveBitmap(app, bmp, "clip_$idx")
                    VideoSceneRender(
                        item = VideoSceneItem(
                            sceneIndex = idx.toLong(),
                            visualPrompt = draft?.visualPrompt ?: "Imported clip frame ${idx + 1}",
                            caption = draft?.caption ?: "Clip ${idx + 1}",
                            cameraMotion = (draft?.cameraMotion ?: CameraMotion.ZOOM_IN).id,
                            transition = (draft?.transition ?: SceneTransition.CROSSFADE).id,
                            durationMs = draft?.durationMs ?: 3000L,
                            localFramePath = path
                        ),
                        bitmap = bmp
                    )
                }
                val project = StudioProject(
                    id = "",
                    userId = currentUserId,
                    title = storyboard.title,
                    prompt = state.prompt.ifBlank { "Imported video clip remix" },
                    mediaType = MediaType.VIDEO.name,
                    aspectRatio = state.aspectRatio.apiValue,
                    stylePreset = state.stylePreset.id,
                    colorGrade = storyboard.colorGrade.id,
                    localMediaPath = renders.firstOrNull()?.item?.localFramePath ?: "",
                    scenes = renders.map { it.item },
                    editHistory = listOf("Imported video clip (${renders.size} scenes)")
                )
                val finalId = persistProject(project)
                _videoState.update {
                    it.copy(
                        projectId = finalId,
                        title = storyboard.title,
                        colorGrade = storyboard.colorGrade,
                        scenes = renders,
                        selectedSceneIndex = 0,
                        currentTimeMs = 0L,
                        isPlaying = true,
                        taskState = AiTaskState.Success("Video clip imported — tap any scene to AI restyle")
                    )
                }
            } catch (e: Exception) {
                handleVideoTaskError(e, "Video clip import")
            }
        }
    }

    fun aiEditSelectedVideoScene(instructionOverride: String? = null) {
        val state = _videoState.value
        val idx = state.selectedSceneIndex
        val target = state.scenes.getOrNull(idx) ?: return
        val sourceBitmap = target.bitmap ?: return
        val instruction = (instructionOverride ?: state.sceneEditInstruction).trim()
        if (instruction.isBlank()) {
            _toastMessage.value = "Describe how to restyle or edit this scene."
            return
        }

        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    isPlaying = false,
                    taskState = AiTaskState.Running("AI editing Scene ${idx + 1}...")
                )
            }
            try {
                val edited = aiService.editImage(
                    sourceBitmap = LocalMediaStorage.scaleDownBitmap(sourceBitmap, 1280),
                    editInstruction = instruction,
                    aspectRatio = state.aspectRatio,
                    useProModel = false
                )
                val path = LocalMediaStorage.saveBitmap(app, edited, "scene_edit_$idx")
                val updatedScenes = state.scenes.mapIndexed { i, render ->
                    if (i != idx) render else render.copy(
                        item = render.item.copy(
                            visualPrompt = instruction,
                            localFramePath = path
                        ),
                        bitmap = edited
                    )
                }
                val history = (state.editHistory + "Scene ${idx + 1}: $instruction").takeLast(20)
                _videoState.update {
                    it.copy(
                        scenes = updatedScenes,
                        sceneEditInstruction = "",
                        editHistory = history,
                        exportedMp4Path = "",
                        taskState = AiTaskState.Success("Scene ${idx + 1} updated")
                    )
                }
                saveCurrentVideoProject()
            } catch (e: Exception) {
                handleVideoTaskError(e, "Scene AI edit")
            }
        }
    }

    fun addAiSceneToVideo(scenePrompt: String) {
        val state = _videoState.value
        if (state.scenes.size >= 8) {
            _toastMessage.value = "Maximum 8 scenes per reel."
            return
        }
        val cleanPrompt = scenePrompt.trim()
        if (cleanPrompt.isBlank()) return

        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    isPlaying = false,
                    taskState = AiTaskState.Running("Generating new scene...")
                )
            }
            try {
                val bitmap = aiService.generateImage(
                    prompt = cleanPrompt,
                    aspectRatio = state.aspectRatio,
                    style = state.stylePreset
                )
                val idx = state.scenes.size
                val path = LocalMediaStorage.saveBitmap(app, bitmap, "scene_$idx")
                val newRender = VideoSceneRender(
                    item = VideoSceneItem(
                        sceneIndex = idx.toLong(),
                        visualPrompt = cleanPrompt,
                        caption = cleanPrompt.split(" ").take(4).joinToString(" "),
                        cameraMotion = CameraMotion.ZOOM_IN.id,
                        transition = SceneTransition.CROSSFADE.id,
                        durationMs = 3000L,
                        localFramePath = path
                    ),
                    bitmap = bitmap
                )
                _videoState.update {
                    it.copy(
                        scenes = it.scenes + newRender,
                        selectedSceneIndex = idx,
                        exportedMp4Path = "",
                        taskState = AiTaskState.Success("Added Scene ${idx + 1}")
                    )
                }
                saveCurrentVideoProject()
            } catch (e: Exception) {
                handleVideoTaskError(e, "Add AI scene")
            }
        }
    }

    fun saveCurrentVideoProject() {
        val state = _videoState.value
        if (state.scenes.isEmpty()) return
        viewModelScope.launch {
            try {
                val project = StudioProject(
                    id = state.projectId,
                    userId = currentUserId,
                    title = state.title.ifBlank { "AI Video Reel" },
                    prompt = state.prompt.ifBlank { "Custom Video Reel" },
                    mediaType = MediaType.VIDEO.name,
                    aspectRatio = state.aspectRatio.apiValue,
                    stylePreset = state.stylePreset.id,
                    colorGrade = state.colorGrade.id,
                    exposure = state.exposure.toDouble(),
                    contrast = state.contrast.toDouble(),
                    saturation = state.saturation.toDouble(),
                    warmth = state.warmth.toDouble(),
                    overlayText = state.overlayText,
                    localMediaPath = state.exportedMp4Path.ifBlank {
                        state.scenes.firstOrNull()?.item?.localFramePath ?: ""
                    },
                    scenes = state.scenes.map { it.item },
                    editHistory = state.editHistory
                )
                val finalId = persistProject(project)
                _videoState.update { it.copy(projectId = finalId) }
                _toastMessage.value = "Video project saved to cloud"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastMessage.value = e.localizedMessage ?: "Failed to save video project"
            }
        }
    }

    fun exportVideoMp4() {
        val state = _videoState.value
        if (state.scenes.isEmpty()) {
            _toastMessage.value = "Create or import scenes before exporting."
            return
        }
        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    isPlaying = false,
                    exportState = AiTaskState.Running("Rendering MP4 video...", 0f)
                )
            }
            val project = StudioProject(
                id = state.projectId,
                userId = currentUserId,
                title = state.title,
                prompt = state.prompt,
                mediaType = MediaType.VIDEO.name,
                aspectRatio = state.aspectRatio.apiValue,
                stylePreset = state.stylePreset.id,
                colorGrade = state.colorGrade.id,
                exposure = state.exposure.toDouble(),
                contrast = state.contrast.toDouble(),
                saturation = state.saturation.toDouble(),
                warmth = state.warmth.toDouble(),
                overlayText = state.overlayText,
                scenes = state.scenes.map { it.item }
            )
            val result = Mp4VideoExporter.exportProjectToMp4(
                context = app,
                project = project,
                sceneBitmaps = state.scenes.map { it.bitmap },
                onProgress = { pct ->
                    _videoState.update {
                        it.copy(exportState = AiTaskState.Running("Encoding MP4 (${(pct * 100).toInt()}%)...", pct))
                    }
                }
            )
            result.onSuccess { mp4Path ->
                val gallerySave = Mp4VideoExporter.saveVideoToGallery(app, mp4Path, state.title)
                _videoState.update {
                    it.copy(
                        exportedMp4Path = mp4Path,
                        exportState = AiTaskState.Success(
                            if (gallerySave.isSuccess) "MP4 exported & saved to Movies/FrameCraft"
                            else "MP4 exported & ready to share"
                        )
                    )
                }
                saveCurrentVideoProject()
            }.onFailure { err ->
                Log.e(TAG, "MP4 export failed", err)
                _videoState.update {
                    it.copy(exportState = AiTaskState.Error(err.localizedMessage ?: "MP4 export failed."))
                }
            }
        }
    }

    fun saveCurrentVideoToGallery() {
        val state = _videoState.value
        val mp4Path = state.exportedMp4Path
        if (mp4Path.isNotBlank() && File(mp4Path).exists()) {
            saveVideoFileToGallery(mp4Path, state.title)
        } else {
            exportVideoMp4()
        }
    }

    fun saveVideoFileToGallery(mp4Path: String, title: String) {
        if (mp4Path.isBlank() || !File(mp4Path).exists()) {
            _toastMessage.value = "Export the MP4 first before saving to gallery."
            return
        }
        viewModelScope.launch {
            val result = Mp4VideoExporter.saveVideoToGallery(app, mp4Path, title)
            _toastMessage.value = if (result.isSuccess) {
                "Saved video to Movies/FrameCraft"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to save video to gallery"
            }
        }
    }

    fun shareExportedVideo(activityContext: android.content.Context) {
        val mp4Path = _videoState.value.exportedMp4Path
        if (mp4Path.isBlank()) {
            _toastMessage.value = "Export the MP4 first before sharing."
            return
        }
        LocalMediaStorage.shareVideoFile(activityContext, mp4Path, _videoState.value.title)
    }

    // --- Shared Project Navigation & Lifecycle ---

    fun openProject(project: StudioProject, onRoute: (MediaType) -> Unit) {
        viewModelScope.launch {
            if (project.mediaType == MediaType.VIDEO.name) {
                val loadedScenes = project.scenes.map { scene ->
                    val bmp = LocalMediaStorage.loadBitmap(scene.localFramePath, 1280)
                    VideoSceneRender(item = scene, bitmap = bmp)
                }
                _videoState.value = VideoStudioUiState(
                    projectId = project.id,
                    title = project.title,
                    prompt = project.prompt,
                    sceneCount = loadedScenes.size.coerceIn(2, 6),
                    aspectRatio = AspectRatioPreset.fromApiValue(project.aspectRatio),
                    stylePreset = StylePreset.fromId(project.stylePreset),
                    colorGrade = ColorGradePreset.fromId(project.colorGrade),
                    exposure = project.exposure.toFloat(),
                    contrast = project.contrast.toFloat(),
                    saturation = project.saturation.toFloat(),
                    warmth = project.warmth.toFloat(),
                    overlayText = project.overlayText,
                    scenes = loadedScenes,
                    selectedSceneIndex = 0,
                    isPlaying = loadedScenes.isNotEmpty(),
                    exportedMp4Path = if (project.localMediaPath.endsWith(".mp4")) project.localMediaPath else "",
                    editHistory = project.editHistory
                )
                onRoute(MediaType.VIDEO)
            } else {
                val bmp = LocalMediaStorage.loadBitmap(project.localMediaPath, 1280)
                _imageState.value = ImageStudioUiState(
                    projectId = project.id,
                    title = project.title,
                    prompt = project.prompt,
                    aspectRatio = AspectRatioPreset.fromApiValue(project.aspectRatio),
                    stylePreset = StylePreset.fromId(project.stylePreset),
                    colorGrade = ColorGradePreset.fromId(project.colorGrade),
                    exposure = project.exposure.toFloat(),
                    contrast = project.contrast.toFloat(),
                    saturation = project.saturation.toFloat(),
                    warmth = project.warmth.toFloat(),
                    overlayText = project.overlayText,
                    activeBitmap = bmp,
                    originalBitmap = bmp,
                    localMediaPath = project.localMediaPath,
                    editHistory = project.editHistory
                )
                onRoute(MediaType.IMAGE)
            }
        }
    }

    fun enterHistorySelectionMode(projectId: String) {
        if (projectId.isBlank()) return
        _imageState.update { state ->
            state.copy(
                isHistorySelectionMode = true,
                selectedHistoryIds = state.selectedHistoryIds + projectId
            )
        }
    }

    fun toggleHistorySelection(projectId: String) {
        if (projectId.isBlank()) return
        _imageState.update { state ->
            val updated = if (projectId in state.selectedHistoryIds) {
                state.selectedHistoryIds - projectId
            } else {
                state.selectedHistoryIds + projectId
            }
            state.copy(
                isHistorySelectionMode = updated.isNotEmpty(),
                selectedHistoryIds = updated
            )
        }
    }

    fun selectAllImageHistory(allIds: List<String>) {
        val validIds = allIds.filter { it.isNotBlank() }.toSet()
        if (validIds.isEmpty()) return
        _imageState.update { state ->
            val allSelected = validIds.all { it in state.selectedHistoryIds }
            if (allSelected) {
                state.copy(
                    isHistorySelectionMode = false,
                    selectedHistoryIds = emptySet()
                )
            } else {
                state.copy(
                    isHistorySelectionMode = true,
                    selectedHistoryIds = validIds
                )
            }
        }
    }

    fun exitHistorySelectionMode() {
        _imageState.update {
            it.copy(
                isHistorySelectionMode = false,
                selectedHistoryIds = emptySet(),
                isBatchDeletingHistory = false
            )
        }
    }

    fun deleteSelectedImageHistory() {
        val idsToDelete = _imageState.value.selectedHistoryIds.toList()
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            _imageState.update { it.copy(isBatchDeletingHistory = true) }
            val result = repository.deleteProjects(idsToDelete)
            result.onSuccess { deletedCount ->
                _imageState.update { state ->
                    state.copy(
                        projectId = if (state.projectId in idsToDelete) "" else state.projectId,
                        isHistorySelectionMode = false,
                        selectedHistoryIds = emptySet(),
                        isBatchDeletingHistory = false
                    )
                }
                _toastMessage.value = if (deletedCount == 1) {
                    "Deleted 1 image from history"
                } else {
                    "Deleted $deletedCount images from history"
                }
            }.onFailure { err ->
                _imageState.update { it.copy(isBatchDeletingHistory = false) }
                _toastMessage.value = err.localizedMessage ?: "Could not delete selected images"
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val res = repository.deleteProject(projectId)
            if (res.isSuccess) {
                _imageState.update { state ->
                    val updatedSelection = state.selectedHistoryIds - projectId
                    state.copy(
                        projectId = if (state.projectId == projectId) "" else state.projectId,
                        selectedHistoryIds = updatedSelection,
                        isHistorySelectionMode = state.isHistorySelectionMode && updatedSelection.isNotEmpty()
                    )
                }
                _videoState.update { state ->
                    state.copy(projectId = if (state.projectId == projectId) "" else state.projectId)
                }
                _toastMessage.value = "Project deleted"
            } else {
                _toastMessage.value = "Could not delete project"
            }
        }
    }

    private suspend fun persistProject(project: StudioProject): String {
        return if (project.id.isBlank()) {
            repository.createProject(project).getOrThrow()
        } else {
            repository.updateProject(project).getOrThrow()
            project.id
        }
    }

    private fun handleImageTaskError(e: Exception, actionName: String) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        Log.e(TAG, "$actionName failed", e)
        val msg = when (e) {
            is TimeoutCancellationException -> "$actionName timed out after 60s. Please try again."
            is QuotaExceededException -> "AI quota exceeded. Please check Firebase Blaze billing or try later."
            else -> e.localizedMessage ?: "$actionName failed."
        }
        _imageState.update { it.copy(taskState = AiTaskState.Error(msg)) }
    }

    private fun handleVideoTaskError(e: Exception, actionName: String) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        Log.e(TAG, "$actionName failed", e)
        val msg = when (e) {
            is TimeoutCancellationException -> "$actionName timed out. Please try again."
            is QuotaExceededException -> "AI quota exceeded. Please check Firebase Blaze billing or try later."
            else -> e.localizedMessage ?: "$actionName failed."
        }
        _videoState.update { it.copy(taskState = AiTaskState.Error(msg)) }
    }

    companion object {
        private const val TAG = "StudioViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(
                    app = app,
                    repository = StudioProjectRepository(db),
                    aiService = FrameCraftAiService(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
