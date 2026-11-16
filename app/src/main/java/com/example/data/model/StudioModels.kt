package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MediaType(val label: String) {
    IMAGE("AI Image"),
    VIDEO("AI Video")
}

enum class AspectRatioPreset(
    val apiValue: String,
    val label: String,
    val ratioValue: Float
) {
    SQUARE("1:1", "1:1 Square", 1f),
    LANDSCAPE("16:9", "16:9 Cinema", 16f / 9f),
    PORTRAIT("9:16", "9:16 Reel", 9f / 16f),
    CLASSIC("4:3", "4:3 Frame", 4f / 3f),
    EDITORIAL("3:4", "3:4 Poster", 3f / 4f);

    companion object {
        fun fromApiValue(value: String): AspectRatioPreset =
            entries.firstOrNull { it.apiValue == value } ?: SQUARE
    }
}

enum class StylePreset(
    val id: String,
    val label: String,
    val promptSuffix: String
) {
    CINEMATIC(
        id = "CINEMATIC",
        label = "Cinematic Film",
        promptSuffix = "cinematic 35mm anamorphic film photography, dramatic volumetric lighting, rich dynamic range, shallow depth of field"
    ),
    CYBERPUNK(
        id = "CYBERPUNK",
        label = "Cyberpunk Neon",
        promptSuffix = "futuristic neon noir cyberpunk aesthetic, rain-slicked reflections, cyan and coral rim lights, atmospheric haze"
    ),
    ANALOG_35MM(
        id = "ANALOG_35MM",
        label = "Analog 35mm",
        promptSuffix = "authentic Kodak Portra 400 analog 35mm film grain, organic warmth, candid editorial framing"
    ),
    EDITORIAL_NOIR(
        id = "EDITORIAL_NOIR",
        label = "Editorial Noir",
        promptSuffix = "high-contrast fine art monochrome photography, sculptural chiaroscuro shadows, silver gelatin print texture"
    ),
    ANIME_STUDIO(
        id = "ANIME_STUDIO",
        label = "Anime Studio",
        promptSuffix = "lush hand-painted theatrical anime background art, luminous sky light, expressive detail"
    ),
    SCULPTURAL_3D(
        id = "SCULPTURAL_3D",
        label = "3D Render",
        promptSuffix = "octane 3D studio render, subsurface scattering, physically accurate glass and brushed metal shaders"
    );

    companion object {
        fun fromId(id: String): StylePreset =
            entries.firstOrNull { it.id == id } ?: CINEMATIC
    }
}

enum class ColorGradePreset(val id: String, val label: String) {
    ORIGINAL("ORIGINAL", "Original"),
    CINEMATIC_TEAL("CINEMATIC_TEAL", "Teal & Coral"),
    WARM_KODAK("WARM_KODAK", "Warm Kodak"),
    NOIR_BW("NOIR_BW", "Noir B&W"),
    CYBER_VIOLET("CYBER_VIOLET", "Cyber Neon"),
    VIVID_POP("VIVID_POP", "Vivid Pop");

    companion object {
        fun fromId(id: String): ColorGradePreset =
            entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

enum class CameraMotion(val id: String, val label: String) {
    ZOOM_IN("ZOOM_IN", "Slow Push In"),
    ZOOM_OUT("ZOOM_OUT", "Pull Back"),
    PAN_LEFT("PAN_LEFT", "Pan Left"),
    PAN_RIGHT("PAN_RIGHT", "Pan Right"),
    DRIFT_UP("DRIFT_UP", "Tilt Up");

    companion object {
        fun fromId(id: String): CameraMotion =
            entries.firstOrNull { it.id == id } ?: ZOOM_IN
    }
}

enum class SceneTransition(val id: String, val label: String) {
    CROSSFADE("CROSSFADE", "Crossfade"),
    SLIDE_LEFT("SLIDE_LEFT", "Slide Cut"),
    FLASH_CUT("FLASH_CUT", "Flash Cut");

    companion object {
        fun fromId(id: String): SceneTransition =
            entries.firstOrNull { it.id == id } ?: CROSSFADE
    }
}

@IgnoreExtraProperties
data class VideoSceneItem(
    val sceneIndex: Long = 0L,
    val visualPrompt: String = "",
    val caption: String = "",
    val cameraMotion: String = CameraMotion.ZOOM_IN.id,
    val transition: String = SceneTransition.CROSSFADE.id,
    val durationMs: Long = 3000L,
    val localFramePath: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sceneIndex" to sceneIndex,
        "visualPrompt" to visualPrompt,
        "caption" to caption,
        "cameraMotion" to cameraMotion,
        "transition" to transition,
        "durationMs" to durationMs,
        "localFramePath" to localFramePath
    )
}

@IgnoreExtraProperties
data class StudioProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val mediaType: String = MediaType.IMAGE.name,
    val aspectRatio: String = AspectRatioPreset.SQUARE.apiValue,
    val stylePreset: String = StylePreset.CINEMATIC.id,
    val colorGrade: String = ColorGradePreset.ORIGINAL.id,
    val exposure: Double = 0.0,
    val contrast: Double = 1.0,
    val saturation: Double = 1.0,
    val warmth: Double = 0.0,
    val overlayText: String = "",
    val localMediaPath: String = "",
    val scenes: List<VideoSceneItem> = emptyList(),
    val editHistory: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
