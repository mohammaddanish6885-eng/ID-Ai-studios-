package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AspectRatioPreset
import com.example.data.model.CameraMotion
import com.example.data.model.ColorGradePreset
import com.example.data.model.MediaType
import com.example.data.model.SceneTransition
import com.example.data.model.StylePreset
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class DirectedSceneDraft(
    val visualPrompt: String,
    val caption: String,
    val cameraMotion: CameraMotion,
    val transition: SceneTransition,
    val durationMs: Long
)

data class StoryboardDraft(
    val title: String,
    val colorGrade: ColorGradePreset,
    val scenes: List<DirectedSceneDraft>
)

class FrameCraftAiService {

    private val flashTextModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val storyboardSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short cinematic title (2-5 words)"),
                "colorGrade" to Schema.enumeration(
                    values = ColorGradePreset.entries.map { it.id },
                    description = "Recommended color grade preset matching the mood"
                ),
                "scenes" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "visualPrompt" to Schema.string(
                                description = "Detailed visual generation prompt for this single video shot, including subject, lighting, camera angle, and mood"
                            ),
                            "caption" to Schema.string(
                                description = "Concise 2-6 word on-screen subtitle or narrative beat"
                            ),
                            "cameraMotion" to Schema.enumeration(
                                values = CameraMotion.entries.map { it.id },
                                description = "Camera motion choreography for this shot"
                            ),
                            "transition" to Schema.enumeration(
                                values = SceneTransition.entries.map { it.id },
                                description = "Transition entering this shot"
                            ),
                            "durationMs" to Schema.integer(
                                description = "Shot duration in milliseconds between 2500 and 4500"
                            )
                        )
                    ),
                    description = "Ordered list of scenes forming the video story"
                )
            )
        )
    }

    private val directorModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
    }

    private fun imageModel(
        aspectRatio: AspectRatioPreset,
        useProModel: Boolean = false
    ): GenerativeModel {
        val sdkAspect = when (aspectRatio) {
            AspectRatioPreset.SQUARE -> AspectRatio.SQUARE_1x1
            AspectRatioPreset.LANDSCAPE -> AspectRatio.LANDSCAPE_16x9
            AspectRatioPreset.PORTRAIT -> AspectRatio.PORTRAIT_9x16
            AspectRatioPreset.CLASSIC -> AspectRatio.LANDSCAPE_4x3
            AspectRatioPreset.EDITORIAL -> AspectRatio.PORTRAIT_3x4
        }
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(sdkAspect)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        val modelName = if (useProModel) "gemini-3-pro-image" else "gemini-nano-banana-2.1"
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config
        )
    }

    suspend fun enhancePrompt(
        rawPrompt: String,
        mediaType: MediaType,
        style: StylePreset
    ): String = withTimeout(45_000L) {
        val kind = if (mediaType == MediaType.VIDEO) "short cinematic video reel" else "high-impact photograph or artwork"
        val instruction = """
            You are an expert creative director. Rewrite the user's simple idea into a vivid, concise, production-ready visual prompt for a $kind in the "${style.label}" style (${style.promptSuffix}).
            Keep it under 65 words, concrete and visual, with no markdown, no quotes, and no preamble.
            User idea: "$rawPrompt"
        """.trimIndent()

        val response = flashTextModel.generateContent(instruction)
        val enhanced = response.text?.trim()?.removeSurrounding("\"")
        if (enhanced.isNullOrBlank()) {
            throw IllegalStateException("Prompt enhancement returned an empty response.")
        }
        enhanced
    }

    suspend fun directVideoStoryboard(
        userPrompt: String,
        style: StylePreset,
        sceneCount: Int
    ): StoryboardDraft = withTimeout(50_000L) {
        val safeCount = sceneCount.coerceIn(2, 6)
        val directorPrompt = """
            You are an award-winning film director and editor.
            Turn this user prompt into a cohesive $safeCount-scene video storyboard in the "${style.label}" style (${style.promptSuffix}).
            Maintain visual continuity across scenes (same world, lighting palette, and character/subject continuity), with escalating visual progression from opening shot to finale.
            Each scene's visualPrompt should describe a rich still keyframe that works beautifully with gentle camera motion.
            User prompt: "$userPrompt"
        """.trimIndent()

        val response = directorModel.generateContent(directorPrompt)
        val jsonText = response.text
            ?: throw IllegalStateException("Director model returned an empty storyboard.")
        parseStoryboardJson(jsonText)
    }

    suspend fun analyzeFramesToStoryboard(
        frames: List<Bitmap>,
        userDirection: String,
        style: StylePreset
    ): StoryboardDraft = withTimeout(55_000L) {
        val response = directorModel.generateContent(
            content {
                frames.take(6).forEach { image(it) }
                text(
                    """
                    Analyze these ${frames.size} sequential frames from a user's clip/photos.
                    Create a matching ${frames.size}-scene storyboard JSON where each scene corresponds in order to one input frame.
                    Write concise, engaging captions, smooth camera motions, transitions, and an edit-ready visualPrompt describing what's in each frame with "${style.label}" polish.
                    User creative direction: "${userDirection.ifBlank { "Elevate into a cohesive cinematic montage" }}"
                    """.trimIndent()
                )
            }
        )
        val jsonText = response.text
            ?: throw IllegalStateException("Frame analysis returned an empty storyboard.")
        parseStoryboardJson(jsonText)
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatio: AspectRatioPreset,
        style: StylePreset,
        useProModel: Boolean = false
    ): Bitmap = withTimeout(60_000L) {
        val fullPrompt = "$prompt, ${style.promptSuffix}"
        val model = imageModel(aspectRatio, useProModel)
        val response = model.generateContent(fullPrompt)
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        imagePart?.image
            ?: throw IllegalStateException("No image was returned by the model. Try refining your prompt.")
    }

    suspend fun editImage(
        sourceBitmap: Bitmap,
        editInstruction: String,
        aspectRatio: AspectRatioPreset,
        useProModel: Boolean = false
    ): Bitmap = withTimeout(60_000L) {
        val model = imageModel(aspectRatio, useProModel)
        val response = model.generateContent(
            content {
                image(sourceBitmap)
                text(
                    "Edit this image according to the following instruction while preserving subject coherence and high visual fidelity: $editInstruction"
                )
            }
        )
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        imagePart?.image
            ?: throw IllegalStateException("The model did not return an edited image. Try a clearer instruction.")
    }

    private fun parseStoryboardJson(jsonString: String): StoryboardDraft {
        Log.d(TAG, "Storyboard JSON received (${jsonString.length} chars)")
        val root = JSONObject(jsonString)
        val title = root.optString("title", "AI Video Reel").ifBlank { "AI Video Reel" }
        val grade = ColorGradePreset.fromId(root.optString("colorGrade", ColorGradePreset.CINEMATIC_TEAL.id))
        val scenesJson = root.optJSONArray("scenes")
            ?: throw IllegalStateException("Storyboard JSON did not contain scenes.")

        val scenes = (0 until scenesJson.length()).map { idx ->
            val obj = scenesJson.getJSONObject(idx)
            DirectedSceneDraft(
                visualPrompt = obj.optString("visualPrompt", "Cinematic establishing shot"),
                caption = obj.optString("caption", "Scene ${idx + 1}"),
                cameraMotion = CameraMotion.fromId(obj.optString("cameraMotion", CameraMotion.ZOOM_IN.id)),
                transition = SceneTransition.fromId(obj.optString("transition", SceneTransition.CROSSFADE.id)),
                durationMs = obj.optLong("durationMs", 3000L).coerceIn(2000L, 6000L)
            )
        }
        if (scenes.isEmpty()) {
            throw IllegalStateException("Storyboard contained zero scenes.")
        }
        return StoryboardDraft(title = title, colorGrade = grade, scenes = scenes)
    }

    private companion object {
        const val TAG = "FrameCraftAiService"
    }
}
