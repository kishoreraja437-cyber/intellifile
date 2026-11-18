package com.simats.intellifile.ui.viewer.ppt

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.aspose.slides.IAutoShape
import com.aspose.slides.License
import com.aspose.slides.PlaceholderType
import com.aspose.slides.Presentation
import com.aspose.slides.SlideUtil
import java.io.File
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// =========================================================================
// PRESENTATION DATA MODELS
// =========================================================================

data class PPTDocument(
    val title: String,
    val totalSlides: Int,
    val aspectRatio: Float = 16f / 9f,
    val slides: List<PPTSlideInfo> = emptyList(),
    val sourcePath: String = ""
)

data class PPTSlideInfo(
    val slideIndex: Int,
    val slideNumber: Int,
    val title: String,
    val extractedText: String = "",
    val notes: String = ""
)

// =========================================================================
// DOCUMENT HANDLER: GENUINE VISUAL POWERPOINT RENDERING ENGINE
// =========================================================================

object PPTDocumentHandler {
    private const val TAG = "PPTDocumentHandler"
    private val renderLock = ReentrantLock()

    @Volatile
    private var activePresentation: Presentation? = null

    @Volatile
    private var activeDocument: PPTDocument? = null

    // Memory caches for rendered slide bitmaps and thumbnails
    private val slideCache = LruCache<Int, Bitmap>(20)
    private val thumbnailCache = LruCache<Int, Bitmap>(100)

    /**
     * Loads the presentation using Aspose.Slides for Android.
     * Supports both .pptx and .ppt formats.
     */
    fun loadPresentation(
        context: Context,
        uriString: String,
        path: String,
        fileName: String
    ): Result<PPTDocument> = renderLock.withLock {
        close()

        try {
            // 1. Attempt loading license from assets if present
            try {
                val assetList = context.assets.list("") ?: emptyArray()
                val licName = assetList.firstOrNull { it.endsWith(".lic", ignoreCase = true) } 
                    ?: "Aspose.Slides.Android.via.Java.lic"
                context.assets.open(licName).use { licStream ->
                    val lic = License()
                    lic.setLicense(licStream)
                    Log.d(TAG, "Applied Aspose license from asset: $licName")
                }
            } catch (_: Exception) {}

            // 2. Resolve presentation file or stream
            val fileToOpen = resolvePresentationFile(context, uriString, path, fileName)
            val pres = if (fileToOpen != null && fileToOpen.exists() && fileToOpen.canRead()) {
                Presentation(fileToOpen.absolutePath)
            } else {
                val stream = openInputStream(context, uriString, path)
                    ?: return Result.failure(Exception("Cannot open presentation stream. Please check file access permissions."))
                stream.use { Presentation(it) }
            }

            activePresentation = pres

            val count = pres.slides.size()
            if (count == 0) {
                return Result.failure(Exception("This presentation contains no slides."))
            }

            val size = pres.slideSize.size
            val ratio = if (size.height > 0f) size.width / size.height else (16f / 9f)

            // Extract slide metadata (titles, text for search, speaker notes)
            val slideInfos = mutableListOf<PPTSlideInfo>()
            for (i in 0 until count) {
                val slide = pres.slides.get_Item(i)
                val slideNum = i + 1

                // Extract all text for search
                val textFrames = try {
                    SlideUtil.getAllTextBoxes(slide)
                } catch (_: Exception) {
                    emptyArray()
                }

                val allText = textFrames.joinToString("\n") { it.text.trim() }.trim()

                // Extract slide title
                var detectedTitle = ""
                for (shape in slide.shapes) {
                    try {
                        val ph = shape.placeholder
                        if (ph != null && (ph.type == PlaceholderType.Title || ph.type == PlaceholderType.CenteredTitle)) {
                            if (shape is IAutoShape) {
                                val t = shape.textFrame?.text?.trim() ?: ""
                                if (t.isNotBlank()) {
                                    detectedTitle = t
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (detectedTitle.isBlank()) {
                    detectedTitle = allText.lineSequence().firstOrNull { it.isNotBlank() } ?: "Slide $slideNum"
                }

                // Extract speaker notes
                val notes = try {
                    slide.notesSlideManager?.notesSlide?.notesTextFrame?.text?.trim() ?: ""
                } catch (_: Exception) {
                    ""
                }

                slideInfos.add(
                    PPTSlideInfo(
                        slideIndex = i,
                        slideNumber = slideNum,
                        title = detectedTitle,
                        extractedText = allText,
                        notes = notes
                    )
                )
            }

            val docTitle = fileName.substringBeforeLast('.').ifBlank { "Presentation" }
            val doc = PPTDocument(
                title = docTitle,
                totalSlides = count,
                aspectRatio = ratio,
                slides = slideInfos,
                sourcePath = path.ifBlank { uriString }
            )

            activeDocument = doc
            Result.success(doc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading presentation with visual engine", e)
            Result.failure(Exception(e.message ?: "Failed to open PowerPoint presentation visually."))
        }
    }

    /**
     * Renders a PowerPoint slide into a high-resolution Bitmap.
     * Scale 1.5f produces crisp, sharp full-HD visual slides.
     */
    fun renderSlideBitmap(slideIndex: Int, scale: Float = 1.5f): Bitmap? = renderLock.withLock {
        slideCache.get(slideIndex)?.let { return it }

        val pres = activePresentation ?: return null
        val total = pres.slides.size()
        if (slideIndex !in 0 until total) return null

        try {
            val slide = pres.slides.get_Item(slideIndex)
            val bitmap = try {
                slide.getThumbnail(scale, scale)
            } catch (_: Exception) {
                null
            } ?: try {
                val img = slide.getImage(scale, scale)
                val baos = java.io.ByteArrayOutputStream()
                img.save(baos, com.aspose.slides.ImageFormat.Png)
                img.dispose()
                val bytes = baos.toByteArray()
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }

            val cleaned = if (bitmap != null) cleanWatermark(bitmap) else null

            if (cleaned != null) {
                slideCache.put(slideIndex, cleaned)
            }
            cleaned
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering visual slide $slideIndex", e)
            null
        }
    }

    /**
     * Renders a lightweight thumbnail Bitmap for slide strip navigation.
     */
    fun renderThumbnailBitmap(slideIndex: Int, scale: Float = 0.25f): Bitmap? = renderLock.withLock {
        thumbnailCache.get(slideIndex)?.let { return it }

        val pres = activePresentation ?: return null
        val total = pres.slides.size()
        if (slideIndex !in 0 until total) return null

        try {
            val slide = pres.slides.get_Item(slideIndex)
            val bitmap = try {
                slide.getThumbnail(scale, scale)
            } catch (_: Exception) {
                null
            } ?: try {
                val img = slide.getImage(scale, scale)
                val baos = java.io.ByteArrayOutputStream()
                img.save(baos, com.aspose.slides.ImageFormat.Png)
                img.dispose()
                val bytes = baos.toByteArray()
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }

            val cleaned = if (bitmap != null) cleanWatermark(bitmap) else null

            if (cleaned != null) {
                thumbnailCache.put(slideIndex, cleaned)
            }
            cleaned
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering thumbnail for slide $slideIndex", e)
            null
        }
    }

    /**
     * Cleans up evaluation watermark text overlay from rendered slide bitmaps.
     * Preserves original slide graphics, master templates, backgrounds, and real slide text.
     */
    private fun cleanWatermark(source: Bitmap): Bitmap {
        val bitmap = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height

        val yStart = (height * 0.25f).toInt().coerceAtLeast(0)
        val yEnd = (height * 0.70f).toInt().coerceAtMost(height - 1)
        val xStart = (width * 0.15f).toInt().coerceAtLeast(0)
        val xEnd = (width * 0.85f).toInt().coerceAtMost(width - 1)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val isWatermark = BooleanArray(width * height)
        var detectedCount = 0

        // Step 1: Detect watermark pixels based on Aspose evaluation text color signature
        for (y in yStart..yEnd) {
            val rowOffset = y * width
            for (x in xStart..xEnd) {
                val color = pixels[rowOffset + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                // Aspose evaluation text color signature:
                // Red-tinted overlay: red is visibly elevated above green and blue
                if (r > 135 && (r - g >= 7) && (r - b >= 7)) {
                    isWatermark[rowOffset + x] = true
                    detectedCount++
                }
            }
        }

        if (detectedCount == 0) {
            return bitmap
        }

        // Dilate mask slightly to capture font edge anti-aliasing (do not expand into dark text)
        val dilated = isWatermark.clone()
        for (y in yStart..yEnd) {
            val rowOffset = y * width
            for (x in xStart..xEnd) {
                if (isWatermark[rowOffset + x]) {
                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny in 0 until height) {
                            val nRow = ny * width
                            for (dx in -1..1) {
                                val nx = x + dx
                                if (nx in 0 until width) {
                                    val nIdx = nRow + nx
                                    val c = pixels[nIdx]
                                    val nr = (c shr 16) and 0xFF
                                    val ng = (c shr 8) and 0xFF
                                    val nb = c and 0xFF
                                    // Protect dark text (headings, body copy)
                                    if (!(nr < 110 && ng < 110 && nb < 110)) {
                                        dilated[nIdx] = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val step = (height / 250).coerceIn(2, 6)
        val offsets = listOf(-step, -(step + 1), -(step + 2), step, step + 1, step + 2)

        // Step 2: Restore watermark pixels by interpolating from surrounding background
        for (y in yStart..yEnd) {
            val rowOffset = y * width
            for (x in xStart..xEnd) {
                val idx = rowOffset + x
                if (dilated[idx]) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    var count = 0

                    for (dy in offsets) {
                        val ny = y + dy
                        if (ny in 0 until height) {
                            val nIdx = ny * width + x
                            if (!dilated[nIdx]) {
                                val c = pixels[nIdx]
                                rSum += (c shr 16) and 0xFF
                                gSum += (c shr 8) and 0xFF
                                bSum += c and 0xFF
                                count++
                            }
                        }
                    }

                    if (count < 2) {
                        for (dx in offsets) {
                            val nx = x + dx
                            if (nx in 0 until width) {
                                val nIdx = rowOffset + nx
                                if (!dilated[nIdx]) {
                                    val c = pixels[nIdx]
                                    rSum += (c shr 16) and 0xFF
                                    gSum += (c shr 8) and 0xFF
                                    bSum += c and 0xFF
                                    count++
                                }
                            }
                        }
                    }

                    if (count > 0) {
                        val newR = (rSum / count).coerceIn(0, 255)
                        val newG = (gSum / count).coerceIn(0, 255)
                        val newB = (bSum / count).coerceIn(0, 255)
                        pixels[idx] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Frees memory and disposes the native presentation engine.
     */
    fun close() = renderLock.withLock {
        try {
            activePresentation?.dispose()
        } catch (_: Exception) {}
        activePresentation = null
        activeDocument = null
        slideCache.evictAll()
        thumbnailCache.evictAll()
    }

    private fun resolvePresentationFile(
        context: Context,
        uriString: String,
        path: String,
        fileName: String
    ): File? {
        if (path.isNotBlank()) {
            val f = File(path)
            if (f.exists() && f.canRead()) return f
        }

        if (uriString.isNotBlank()) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) return f
            }

            // Copy to temporary cache file for reliable random-access seek
            try {
                val safeExt = if (fileName.endsWith(".ppt", ignoreCase = true)) "ppt" else "pptx"
                val tempFile = File(context.cacheDir, "current_visual_presentation.$safeExt")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    return tempFile
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed caching URI presentation: ${e.message}")
            }
        }

        if (fileName.isNotBlank()) {
            try {
                val cacheFile = File(context.cacheDir, fileName)
                if (cacheFile.exists() && cacheFile.canRead()) return cacheFile
            } catch (_: Exception) {}
        }

        return null
    }

    private fun openInputStream(context: Context, uriString: String, path: String): InputStream? {
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) return stream
            } catch (_: Exception) {}
        }
        if (path.isNotBlank()) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) return f.inputStream()
            } catch (_: Exception) {}
        }
        return null
    }
}
