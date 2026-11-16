package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.ColorGradePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object LocalMediaStorage {

    private const val MEDIA_DIR = "framecraft_media"

    private fun ensureMediaDir(context: Context): File {
        val dir = File(context.filesDir, MEDIA_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String = "fc"): String =
        withContext(Dispatchers.IO) {
            val file = File(ensureMediaDir(context), "${prefix}_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            file.absolutePath
        }

    suspend fun loadBitmap(path: String, maxDimension: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            if (path.isBlank()) return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDimension || opts.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOpts)
        }

    suspend fun decodeUriBitmap(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDimension ||
                boundsOpts.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }

    suspend fun extractVideoFrames(
        context: Context,
        videoUri: Uri,
        frameCount: Int = 4
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 4000L
            val safeCount = frameCount.coerceIn(1, 6)
            for (i in 0 until safeCount) {
                val timeUs = ((durationMs * (i + 0.5)) / safeCount * 1000.0).toLong()
                    .coerceAtLeast(0L)
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val scaled = scaleDownBitmap(raw, 1024)
                    frames.add(scaled)
                }
            }
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    fun scaleDownBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimension && h <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxOf(w, h).toFloat()
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    fun buildColorMatrix(
        grade: ColorGradePreset,
        exposure: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float
    ): ColorMatrix {
        val result = ColorMatrix()

        // Grade LUT matrix
        when (grade) {
            ColorGradePreset.ORIGINAL -> Unit
            ColorGradePreset.CINEMATIC_TEAL -> {
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.08f, 0f, -0.04f, 0f, 8f,
                            0f, 1.02f, 0.04f, 0f, 2f,
                            -0.06f, 0.06f, 1.12f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ColorGradePreset.WARM_KODAK -> {
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.14f, 0.04f, 0f, 0f, 12f,
                            0f, 1.04f, 0f, 0f, 6f,
                            0f, 0f, 0.88f, 0f, -4f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ColorGradePreset.NOIR_BW -> {
                val bw = ColorMatrix().apply { setSaturation(0f) }
                result.postConcat(bw)
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.22f, 0f, 0f, 0f, -20f,
                            0f, 1.22f, 0f, 0f, -20f,
                            0f, 0f, 1.22f, 0f, -20f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ColorGradePreset.CYBER_VIOLET -> {
                result.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.12f, 0f, 0.12f, 0f, 8f,
                            0f, 0.94f, 0.08f, 0f, 0f,
                            0.08f, 0.04f, 1.22f, 0f, 14f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ColorGradePreset.VIVID_POP -> {
                val sat = ColorMatrix().apply { setSaturation(1.35f) }
                result.postConcat(sat)
            }
        }

        // Saturation
        if (grade != ColorGradePreset.NOIR_BW) {
            val satMatrix = ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 2f)) }
            result.postConcat(satMatrix)
        }

        // Contrast & Exposure
        val c = contrast.coerceIn(0.5f, 2.0f)
        val translate = (-0.5f * c + 0.5f) * 255f + (exposure.coerceIn(-1f, 1f) * 85f)
        result.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        // Warmth
        val w = warmth.coerceIn(-1f, 1f) * 28f
        if (w != 0f) {
            result.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, w,
                        0f, 1f, 0f, 0f, w * 0.25f,
                        0f, 0f, 1f, 0f, -w,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return result
    }

    fun renderFilteredImage(
        source: Bitmap,
        grade: ColorGradePreset,
        exposure: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        overlayText: String
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                buildColorMatrix(grade, exposure, contrast, saturation, warmth)
            )
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (overlayText.isNotBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = (source.width * 0.055f).coerceIn(28f, 96f)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(10f, 0f, 4f, android.graphics.Color.argb(190, 0, 0, 0))
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(135, 11, 13, 18)
            }
            val y = source.height * 0.88f
            val pad = textPaint.textSize * 0.6f
            val textWidth = textPaint.measureText(overlayText)
            val left = (source.width - textWidth) / 2f - pad
            val right = (source.width + textWidth) / 2f + pad
            canvas.drawRoundRect(
                left.coerceAtLeast(24f),
                y - textPaint.textSize - pad * 0.4f,
                right.coerceAtMost(source.width - 24f),
                y + pad * 0.6f,
                20f,
                20f,
                bgPaint
            )
            canvas.drawText(overlayText, source.width / 2f, y, textPaint)
        }
        return output
    }

    suspend fun saveImageToGallery(context: Context, bitmap: Bitmap, title: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "FrameCraft" }
                val fileName = "${safeName}_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/FrameCraft"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)) {
                                error("Bitmap PNG compression failed")
                            }
                        } ?: error("Could not open MediaStore output stream")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                        return@withContext Result.success(uri)
                    } catch (t: Throwable) {
                        runCatching { resolver.delete(uri, null, null) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) throw t
                    }
                }
                val fallbackDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "FrameCraft"
                ).apply { mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                FileOutputStream(fallbackFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(fallbackFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                Result.success(Uri.fromFile(fallbackFile))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveImageFileToGallery(context: Context, imagePath: String, title: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val src = File(imagePath)
                if (!src.exists()) error("Generated image file not found on device.")
                val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "FrameCraft" }
                val fileName = "${safeName}_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/FrameCraft"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            src.inputStream().use { input -> input.copyTo(out) }
                        } ?: error("Could not open MediaStore output stream")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                        return@withContext Result.success(uri)
                    } catch (t: Throwable) {
                        runCatching { resolver.delete(uri, null, null) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) throw t
                    }
                }
                val fallbackDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "FrameCraft"
                ).apply { mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                src.copyTo(fallbackFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(fallbackFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                Result.success(Uri.fromFile(fallbackFile))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val file = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, "share_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            outFile
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share AI Creation"))
    }

    fun shareVideoFile(context: Context, videoPath: String, title: String) {
        val file = File(videoPath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share AI Video Reel"))
    }
}
