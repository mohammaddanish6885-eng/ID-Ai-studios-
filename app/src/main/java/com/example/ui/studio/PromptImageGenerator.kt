package com.example.ui.studio

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.media.LocalMediaStorage
import com.example.data.model.AspectRatioPreset
import com.example.data.model.MediaType
import com.example.data.model.StudioProject
import com.example.data.repository.StudioProjectRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface PromptImageUiState {
    data object Idle : PromptImageUiState
    data object Loading : PromptImageUiState
    data class Success(val bitmap: Bitmap) : PromptImageUiState
    data class Error(val message: String) : PromptImageUiState
}

class PromptImageViewModel(
    private val app: Application,
    private val repository: StudioProjectRepository
) : ViewModel() {

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val _uiState = MutableStateFlow<PromptImageUiState>(PromptImageUiState.Idle)
    val uiState: StateFlow<PromptImageUiState> = _uiState.asStateFlow()

    private var lastPrompt: String? = null

    fun generateIfNeeded(prompt: String, forceRefresh: Boolean = false) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _uiState.value = PromptImageUiState.Idle
            return
        }
        if (!forceRefresh && cleanPrompt == lastPrompt && _uiState.value is PromptImageUiState.Success) {
            return
        }
        lastPrompt = cleanPrompt

        viewModelScope.launch {
            _uiState.value = PromptImageUiState.Loading
            try {
                val bitmap = withTimeout(60_000L) {
                    val response = imageModel.generateContent(cleanPrompt)
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                        ?.image
                }
                if (bitmap != null) {
                    if (Firebase.auth.currentUser != null) {
                        val savedPath = LocalMediaStorage.saveBitmap(app, bitmap, "img")
                        val title = cleanPrompt.split(" ").take(4).joinToString(" ")
                            .replaceFirstChar { it.uppercase() }.ifBlank { "AI Frame" }
                        repository.createProject(
                            StudioProject(
                                userId = Firebase.auth.currentUser!!.uid,
                                title = title,
                                prompt = cleanPrompt,
                                mediaType = MediaType.IMAGE.name,
                                aspectRatio = AspectRatioPreset.SQUARE.apiValue,
                                localMediaPath = savedPath,
                                editHistory = listOf("Generated: $cleanPrompt")
                            )
                        )
                    }
                    _uiState.value = PromptImageUiState.Success(bitmap)
                } else {
                    _uiState.value = PromptImageUiState.Error("Model returned no image.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = PromptImageUiState.Error("Image generation timed out after 60s.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Quota exceeded during image generation", e)
                _uiState.value = PromptImageUiState.Error("Quota exceeded. Please check Firebase Blaze billing or try later.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = PromptImageUiState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    companion object {
        private const val TAG = "PromptImageViewModel"

        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PromptImageViewModel(app = app, repository = StudioProjectRepository(db))
            }
        }
    }
}

/**
 * Generates an image from [prompt] using the Firebase AI Logic SDK, persists the prompt and
 * output image to Firestore via [StudioProjectRepository] when signed in, and renders the
 * resulting [Bitmap] via Coil's [AsyncImage].
 */
@Composable
fun PromptGeneratedImage(
    prompt: String,
    modifier: Modifier = Modifier,
    viewModel: PromptImageViewModel = viewModel(
        key = prompt,
        factory = PromptImageViewModel.provideFactory()
    )
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExpandedDetail by remember(prompt) { mutableStateOf(false) }

    LaunchedEffect(prompt) {
        viewModel.generateIfNeeded(prompt)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PromptImageUiState.Idle -> {
                Text(
                    text = "Enter a text prompt to generate an image.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is PromptImageUiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Generating image (may take 30-60s)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is PromptImageUiState.Success -> {
                val generatedTitle = remember(prompt) {
                    prompt.split(" ").take(4).joinToString(" ")
                        .replaceFirstChar { it.uppercase() }.ifBlank { "AI Frame" }
                }
                val imageRequest = remember(state.bitmap) {
                    ImageRequest.Builder(context)
                        .data(state.bitmap)
                        .crossfade(true)
                        .build()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showExpandedDetail = true }
                        .testTag("coil_generated_image_container")
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "AI generated image for: $prompt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("coil_generated_image")
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val res = LocalMediaStorage.saveImageToGallery(
                                    context,
                                    state.bitmap,
                                    generatedTitle
                                )
                                val msg = if (res.isSuccess) {
                                    "Saved to Pictures/FrameCraft"
                                } else {
                                    res.exceptionOrNull()?.localizedMessage ?: "Failed to save to gallery"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xA60B0D12))
                            .testTag("prompt_image_save_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save image to gallery",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                }
                if (showExpandedDetail) {
                    ExpandedImageDetailDialog(
                        payload = ImageDetailPayload(
                            title = generatedTitle,
                            prompt = prompt,
                            bitmap = state.bitmap,
                            aspectRatioLabel = AspectRatioPreset.SQUARE.apiValue,
                            styleLabel = "Generated"
                        ),
                        onDismiss = { showExpandedDetail = false },
                        onRerunPrompt = {
                            showExpandedDetail = false
                            viewModel.generateIfNeeded(prompt, forceRefresh = true)
                        },
                        onSaveToGallery = {
                            coroutineScope.launch {
                                val res = LocalMediaStorage.saveImageToGallery(
                                    context,
                                    state.bitmap,
                                    generatedTitle
                                )
                                val msg = if (res.isSuccess) {
                                    "Saved to Pictures/FrameCraft"
                                } else {
                                    res.exceptionOrNull()?.localizedMessage ?: "Failed to save to gallery"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            coroutineScope.launch {
                                runCatching {
                                    LocalMediaStorage.shareBitmap(context, state.bitmap, generatedTitle)
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        err.localizedMessage ?: "Unable to share image",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
            is PromptImageUiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.generateIfNeeded(prompt, forceRefresh = true) },
                            modifier = Modifier.testTag("retry_prompt_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Retry", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
