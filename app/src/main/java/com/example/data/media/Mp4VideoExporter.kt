package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.AspectRatioPreset
import com.example.data.model.CameraMotion
import com.example.data.model.ColorGradePreset
import com.example.data.model.SceneTransition
import com.example.data.model.StudioProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object Mp4VideoExporter {

    private const val MIME_TYPE = "video/avc"
    private const val FPS = 24
    private const val BIT_RATE = 4_000_000

    suspend fun exportProjectToMp4(
        context: Context,
        project: StudioProject,
        sceneBitmaps: List<Bitmap?>,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val aspect = AspectRatioPreset.fromApiValue(project.aspectRatio)
        val (width, height) = when (aspect) {
            AspectRatioPreset.SQUARE -> 720 to 720
            AspectRatioPreset.LANDSCAPE -> 1280 to 720
            AspectRatioPreset.PORTRAIT -> 720 to 1280
            AspectRatioPreset.CLASSIC -> 960 to 720
            AspectRatioPreset.EDITORIAL -> 720 to 960
        }

        val validScenes = project.scenes.mapIndexedNotNull { idx, scene ->
            val bmp = sceneBitmaps.getOrNull(idx)
            if (bmp != null) scene to bmp else null
        }
        if (validScenes.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No rendered scenes available to export."))
        }

        val mediaDir = File(context.filesDir, "framecraft_media").apply { mkdirs() }
        val outFile = File(mediaDir, "reel_${UUID.randomUUID()}.mp4")

        val colorMatrix = LocalMediaStorage.buildColorMatrix(
            grade = ColorGradePreset.fromId(project.colorGrade),
            exposure = project.exposure.toFloat(),
            contrast = project.contrast.toFloat(),
            saturation = project.saturation.toFloat(),
            warmth = project.warmth.toFloat()
        )
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.044f).coerceIn(26f, 48f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 3f, Color.argb(200, 0, 0, 0))
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 11, 13, 18)
        }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            val totalDurationMs = validScenes.sumOf { it.first.durationMs.coerceIn(1500L, 8000L) }
            val totalFrames = ((totalDurationMs / 1000.0) * FPS).toInt().coerceAtLeast(FPS)
            val frameDurationUs = 1_000_000L / FPS
            var encodedFrameIndex = 0L

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    codec.signalEndOfInputStream()
                }
                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw IllegalStateException("Format changed twice")
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputIndex >= 0 -> {
                            val encodedData = codec.getOutputBuffer(outputIndex)
                                ?: throw IllegalStateException("Encoder output buffer null")
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0 && muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = encodedFrameIndex * frameDurationUs
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                encodedFrameIndex++
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            var elapsedMs = 0f
            val msPerFrame = 1000f / FPS
            for (frameIdx in 0 until totalFrames) {
                val canvas = inputSurface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.rgb(11, 13, 18))
                    drawTimelineFrame(
                        canvas = canvas,
                        width = width.toFloat(),
                        height = height.toFloat(),
                        timeMs = elapsedMs,
                        validScenes = validScenes,
                        overlayTitle = project.overlayText,
                        framePaint = framePaint,
                        captionPaint = captionPaint,
                        pillPaint = pillPaint
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(endOfStream = false)
                elapsedMs += msPerFrame
                onProgress((frameIdx + 1).toFloat() / totalFrames.toFloat())
            }

            drainEncoder(endOfStream = true)
            // Note: Surface-based timestamps are driven by presentation time; copy to MediaStore if desired
            inputSurface.release()
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            outFile.delete()
            Result.failure(e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun drawTimelineFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        timeMs: Float,
        validScenes: List<Pair<com.example.data.model.VideoSceneItem, Bitmap>>,
        overlayTitle: String,
        framePaint: Paint,
        captionPaint: Paint,
        pillPaint: Paint
    ) {
        var cursor = 0f
        var activeIdx = validScenes.lastIndex
        var localProgress = 1f
        for (i in validScenes.indices) {
            val dur = validScenes[i].first.durationMs.coerceIn(1500L, 8000L).toFloat()
            if (timeMs < cursor + dur || i == validScenes.lastIndex) {
                activeIdx = i
                localProgress = ((timeMs - cursor) / dur).coerceIn(0f, 1f)
                break
            }
            cursor += dur
        }

        val (scene, bitmap) = validScenes[activeIdx]
        val transitionProgress = (localProgress / 0.18f).coerceIn(0f, 1f)
        val prevPair = if (activeIdx > 0) validScenes[activeIdx - 1] else null

        if (prevPair != null && transitionProgress < 1f) {
            val transition = SceneTransition.fromId(scene.transition)
            when (transition) {
                SceneTransition.CROSSFADE -> {
                    framePaint.alpha = 255
                    drawSceneWithMotion(canvas, width, height, prevPair.second, CameraMotion.fromId(prevPair.first.cameraMotion), 1f, 0f, framePaint)
                    framePaint.alpha = (transitionProgress * 255).toInt()
                    drawSceneWithMotion(canvas, width, height, bitmap, CameraMotion.fromId(scene.cameraMotion), localProgress, 0f, framePaint)
                    framePaint.alpha = 255
                }
                SceneTransition.SLIDE_LEFT -> {
                    framePaint.alpha = 255
                    drawSceneWithMotion(canvas, width, height, prevPair.second, CameraMotion.fromId(prevPair.first.cameraMotion), 1f, -width * transitionProgress, framePaint)
                    drawSceneWithMotion(canvas, width, height, bitmap, CameraMotion.fromId(scene.cameraMotion), localProgress, width * (1f - transitionProgress), framePaint)
                }
                SceneTransition.FLASH_CUT -> {
                    framePaint.alpha = 255
                    drawSceneWithMotion(canvas, width, height, bitmap, CameraMotion.fromId(scene.cameraMotion), localProgress, 0f, framePaint)
                    val flashAlpha = ((1f - transitionProgress) * 200).toInt().coerceIn(0, 200)
                    if (flashAlpha > 0) {
                        canvas.drawColor(Color.argb(flashAlpha, 255, 255, 255))
                    }
                }
            }
        } else {
            framePaint.alpha = 255
            drawSceneWithMotion(canvas, width, height, bitmap, CameraMotion.fromId(scene.cameraMotion), localProgress, 0f, framePaint)
        }

        val caption = scene.caption.ifBlank { overlayTitle }
        if (caption.isNotBlank()) {
            val y = height * 0.87f
            val padH = 28f
            val padV = 16f
            val textWidth = captionPaint.measureText(caption).coerceAtMost(width - 80f)
            val rect = RectF(
                (width - textWidth) / 2f - padH,
                y - captionPaint.textSize - padV * 0.4f,
                (width + textWidth) / 2f + padH,
                y + padV
            )
            canvas.drawRoundRect(rect, 22f, 22f, pillPaint)
            canvas.drawText(caption, width / 2f, y, captionPaint)
        }
    }

    private fun drawSceneWithMotion(
        canvas: Canvas,
        canvasW: Float,
        canvasH: Float,
        bitmap: Bitmap,
        motion: CameraMotion,
        progress: Float,
        offsetX: Float,
        paint: Paint
    ) {
        val baseScale = maxOf(canvasW / bitmap.width, canvasH / bitmap.height)
        val (zoom, tx, ty) = when (motion) {
            CameraMotion.ZOOM_IN -> Triple(1f + 0.14f * progress, 0f, 0f)
            CameraMotion.ZOOM_OUT -> Triple(1.14f - 0.14f * progress, 0f, 0f)
            CameraMotion.PAN_LEFT -> Triple(1.12f, canvasW * 0.05f * (1f - 2f * progress), 0f)
            CameraMotion.PAN_RIGHT -> Triple(1.12f, canvasW * 0.05f * (-1f + 2f * progress), 0f)
            CameraMotion.DRIFT_UP -> Triple(1.12f, 0f, canvasH * 0.04f * (1f - 2f * progress))
        }
        val finalScale = baseScale * zoom
        val drawW = bitmap.width * finalScale
        val drawH = bitmap.height * finalScale
        val left = (canvasW - drawW) / 2f + tx + offsetX
        val top = (canvasH - drawH) / 2f + ty
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(finalScale, finalScale)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()
    }

    suspend fun saveVideoToGallery(context: Context, mp4Path: String, title: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val src = File(mp4Path)
                if (!src.exists()) error("Exported MP4 not found.")
                val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "FrameCraft_Reel" }
                val fileName = "${safeName}_${System.currentTimeMillis()}.mp4"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_MOVIES}/FrameCraft"
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(src).use { input -> input.copyTo(out) }
                        } ?: error("Could not open MediaStore video stream")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                        return@withContext Result.success(uri)
                    } catch (t: Throwable) {
                        runCatching { resolver.delete(uri, null, null) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) throw t
                    }
                }
                val fallbackDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "FrameCraft"
                ).apply { mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                src.copyTo(fallbackFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(fallbackFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                Result.success(Uri.fromFile(fallbackFile))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
