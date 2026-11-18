package com.simats.intellifile.ui.viewer.ppt

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.LruCache
import com.aspose.slides.IAutoShape
import com.aspose.slides.License
import com.aspose.slides.PlaceholderType
import com.aspose.slides.Presentation
import com.aspose.slides.SlideUtil
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.withLock
import kotlin.math.max

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

// Internal models for High-Fidelity Native OpenXML Engine
private data class NativePresentationModel(
    val slideWidthEmu: Long,
    val slideHeightEmu: Long,
    val aspectRatio: Float,
    val slides: List<NativeSlideModel>,
    val mediaMap: Map<String, ByteArray>
)

private data class NativeSlideModel(
    val slideIndex: Int,
    val slideNumber: Int,
    val title: String,
    val extractedText: String,
    val notes: String,
    val backgroundMediaKey: String?,
    val elements: List<NativeSlideElement>
)

private sealed class NativeSlideElement {
    data class Picture(
        val relX: Float,
        val relY: Float,
        val relW: Float,
        val relH: Float,
        val mediaKey: String,
        val isFullBleedBackground: Boolean = false
    ) : NativeSlideElement()

    data class Shape(
        val relX: Float,
        val relY: Float,
        val relW: Float,
        val relH: Float,
        val fillColor: Int?,
        val strokeColor: Int?,
        val strokeWidthPt: Float = 0f,
        val cornerRadiusPx: Float = 16f,
        val paragraphs: List<NativeParagraph>
    ) : NativeSlideElement()

    data class Table(
        val relX: Float,
        val relY: Float,
        val relW: Float,
        val relH: Float,
        val colWidthRels: List<Float>,
        val rows: List<NativeTableRow>
    ) : NativeSlideElement()
}

private data class NativeTableRow(
    val rowHeightRel: Float,
    val isHeader: Boolean,
    val cells: List<NativeTableCell>
)

private data class NativeTableCell(
    val text: String,
    val fillColor: Int?,
    val isBold: Boolean,
    val textColor: Int?
)

private data class NativeParagraph(
    val alignment: Layout.Alignment,
    val runs: List<NativeTextRun>
)

private data class NativeTextRun(
    val text: String,
    val fontSizePt: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val color: Int
)

// =========================================================================
// DOCUMENT HANDLER: 100% FREE, WATERMARK-FREE NATIVE POWERPOINT ENGINE
// =========================================================================

object PPTDocumentHandler {
    private const val TAG = "PPTDocumentHandler"
    private val renderLock = ReentrantLock()

    // Active presentation models
    @Volatile
    private var nativePresentation: NativePresentationModel? = null

    @Volatile
    private var activePresentation: Presentation? = null

    @Volatile
    private var activeDocument: PPTDocument? = null

    private var isUsingNativeEngine = false

    // High-resolution bitmap caches
    private val slideCache = LruCache<Int, Bitmap>(20)
    private val thumbnailCache = LruCache<Int, Bitmap>(100)

    /**
     * Loads the presentation.
     * Uses our 100% Free, Native OpenXML Engine for .pptx presentations (ZERO WATERMARK).
     * Falls back to Aspose with automatic watermark scrubber for legacy .ppt files.
     */
    fun loadPresentation(
        context: Context,
        uriString: String,
        path: String,
        fileName: String
    ): Result<PPTDocument> = renderLock.withLock {
        close()

        val isLegacyPpt = fileName.endsWith(".ppt", ignoreCase = true)

        // Step 1: For modern .pptx presentations, use Native High-Fidelity OpenXML Engine (Zero Watermark)
        if (!isLegacyPpt) {
            try {
                val fileToOpen = resolvePresentationFile(context, uriString, path, fileName)
                if (fileToOpen != null && fileToOpen.exists() && fileToOpen.canRead()) {
                    val parsed = parseNativePptx(fileToOpen, fileName)
                    if (parsed != null && parsed.slides.isNotEmpty()) {
                        nativePresentation = parsed
                        isUsingNativeEngine = true

                        val slideInfos = parsed.slides.map { s ->
                            PPTSlideInfo(
                                slideIndex = s.slideIndex,
                                slideNumber = s.slideNumber,
                                title = s.title,
                                extractedText = s.extractedText,
                                notes = s.notes
                            )
                        }

                        val docTitle = fileName.substringBeforeLast('.').ifBlank { "Presentation" }
                        val doc = PPTDocument(
                            title = docTitle,
                            totalSlides = parsed.slides.size,
                            aspectRatio = parsed.aspectRatio,
                            slides = slideInfos,
                            sourcePath = path.ifBlank { uriString }
                        )

                        activeDocument = doc
                        Log.d(TAG, "Successfully loaded presentation with Native OpenXML Engine (Zero Watermark): ${doc.totalSlides} slides")
                        return Result.success(doc)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native OpenXML parser encountered error, falling back to Aspose: ${e.message}", e)
            }
        }

        // Step 2: Fallback to Aspose.Slides for legacy .ppt or if native engine was skipped
        try {
            try {
                val assetList = context.assets.list("") ?: emptyArray()
                val licName = assetList.firstOrNull { it.endsWith(".lic", ignoreCase = true) }
                    ?: "Aspose.Slides.Android.via.Java.lic"
                context.assets.open(licName).use { licStream ->
                    val lic = License()
                    lic.setLicense(licStream)
                    Log.d(TAG, "Applied Aspose license: $licName")
                }
            } catch (_: Exception) {}

            val fileToOpen = resolvePresentationFile(context, uriString, path, fileName)
            val pres = if (fileToOpen != null && fileToOpen.exists() && fileToOpen.canRead()) {
                Presentation(fileToOpen.absolutePath)
            } else {
                val stream = openInputStream(context, uriString, path)
                    ?: return Result.failure(Exception("Cannot open presentation stream. Please check file access permissions."))
                stream.use { Presentation(it) }
            }

            activePresentation = pres
            isUsingNativeEngine = false

            val count = pres.slides.size()
            if (count == 0) {
                return Result.failure(Exception("This presentation contains no slides."))
            }

            val size = pres.slideSize.size
            val ratio = if (size.height > 0f) size.width / size.height else (16f / 9f)

            val slideInfos = mutableListOf<PPTSlideInfo>()
            for (i in 0 until count) {
                val slide = pres.slides.get_Item(i)
                val slideNum = i + 1

                val textFrames = try {
                    SlideUtil.getAllTextBoxes(slide)
                } catch (_: Exception) {
                    emptyArray()
                }

                val allText = textFrames.joinToString("\n") { it.text.trim() }.trim()

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
            Log.e(TAG, "Failed loading presentation", e)
            Result.failure(Exception(e.message ?: "Failed to open PowerPoint presentation."))
        }
    }

    /**
     * Renders a slide into a high-resolution Bitmap with ZERO WATERMARK.
     */
    fun renderSlideBitmap(slideIndex: Int, scale: Float = 1.5f): Bitmap? = renderLock.withLock {
        slideCache.get(slideIndex)?.let { return it }

        // Path A: Native OpenXML Engine (100% Free, Zero Watermark)
        if (isUsingNativeEngine) {
            val pres = nativePresentation ?: return null
            val slide = pres.slides.getOrNull(slideIndex) ?: return null
            val bitmap = renderNativeSlideBitmap(pres, slide, scale = scale)
            if (bitmap != null) {
                slideCache.put(slideIndex, bitmap)
            }
            return bitmap
        }

        // Path B: Aspose.Slides fallback for legacy .ppt
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
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }

            val cleaned = if (bitmap != null) cleanWatermark(bitmap) else null
            if (cleaned != null) {
                slideCache.put(slideIndex, cleaned)
            }
            cleaned
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering slide $slideIndex", e)
            null
        }
    }

    /**
     * Renders a lightweight thumbnail Bitmap for slide strip navigation.
     */
    fun renderThumbnailBitmap(slideIndex: Int, scale: Float = 0.25f): Bitmap? = renderLock.withLock {
        thumbnailCache.get(slideIndex)?.let { return it }

        if (isUsingNativeEngine) {
            val pres = nativePresentation ?: return null
            val slide = pres.slides.getOrNull(slideIndex) ?: return null
            // Render thumbnail at lightweight scale
            val thumb = renderNativeSlideBitmap(pres, slide, scale = 0.3f)
            if (thumb != null) {
                thumbnailCache.put(slideIndex, thumb)
            }
            return thumb
        }

        val pres = activePresentation ?: return null
        val total = pres.slides.size()
        if (slideIndex !in 0 until total) return null

        try {
            val slide = pres.slides.get_Item(slideIndex)
            val bitmap = try {
                slide.getThumbnail(scale, scale)
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

    // =========================================================================
    // NATIVE OPENXML PRESENTATION PARSER & RENDERER (100% FREE & WATERMARK-FREE)
    // =========================================================================

    private fun parseNativePptx(file: File, fileName: String): NativePresentationModel? {
        return try {
            ZipFile(file).use { zip ->
                val mediaMap = mutableMapOf<String, ByteArray>()

                // 1. Extract all embedded media (backgrounds, photos, logos, diagrams)
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.contains("ppt/media/")) {
                        val key = entry.name.substringAfterLast('/').lowercase().trim()
                        zip.getInputStream(entry).use { input ->
                            mediaMap[key] = input.readBytes()
                        }
                    }
                }

                // 2. Parse presentation dimensions from ppt/presentation.xml
                var slideWidthEmu = 12192000L
                var slideHeightEmu = 6858000L
                val presEntry = zip.getEntry("ppt/presentation.xml")
                val slideOrderRIds = mutableListOf<String>()

                if (presEntry != null) {
                    zip.getInputStream(presEntry).use { input ->
                        val doc = parseXmlDocument(input)
                        val sldSz = doc.findFirstDescendant("sldSz")
                        if (sldSz != null) {
                            slideWidthEmu = sldSz.getAttribute("cx").toLongOrNull() ?: 12192000L
                            slideHeightEmu = sldSz.getAttribute("cy").toLongOrNull() ?: 6858000L
                        }

                        // Read slide order from sldIdLst
                        val sldIds = doc.findDescendants("sldId")
                        for (s in sldIds) {
                            val rId = s.getAttribute("r:id").ifBlank { s.getAttribute("id") }
                            if (rId.isNotBlank()) {
                                slideOrderRIds.add(rId)
                            }
                        }
                    }
                }

                val aspectRatio = if (slideHeightEmu > 0) slideWidthEmu.toFloat() / slideHeightEmu.toFloat() else (16f / 9f)

                // 3. Map presentation relations (rId -> target slide path)
                val presRelsMap = mutableMapOf<String, String>()
                val presRelsEntry = zip.getEntry("ppt/_rels/presentation.xml.rels")
                if (presRelsEntry != null) {
                    zip.getInputStream(presRelsEntry).use { input ->
                        val doc = parseXmlDocument(input)
                        val rels = doc.findDescendants("Relationship")
                        for (r in rels) {
                            val id = r.getAttribute("Id")
                            val target = r.getAttribute("Target").replace('\\', '/').trim()
                            if (id.isNotBlank() && target.isNotBlank()) {
                                presRelsMap[id] = target
                            }
                        }
                    }
                }

                // Determine ordered slide file paths
                val orderedSlidePaths = mutableListOf<String>()
                for (rId in slideOrderRIds) {
                    val target = presRelsMap[rId]
                    if (target != null) {
                        val fullPath = if (target.startsWith("ppt/")) target else "ppt/$target"
                        if (zip.getEntry(fullPath) != null) {
                            orderedSlidePaths.add(fullPath)
                        }
                    }
                }

                // Fallback: If sldIdLst not present, sort all ppt/slides/slide*.xml
                if (orderedSlidePaths.isEmpty()) {
                    val slideEntries = mutableListOf<String>()
                    val en = zip.entries()
                    while (en.hasMoreElements()) {
                        val name = en.nextElement().name
                        if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml") && !name.contains("_rels")) {
                            slideEntries.add(name)
                        }
                    }
                    slideEntries.sortBy { name ->
                        val num = name.filter { it.isDigit() }.toIntOrNull() ?: 0
                        num
                    }
                    orderedSlidePaths.addAll(slideEntries)
                }

                if (orderedSlidePaths.isEmpty()) return null

                // 4. Parse each slide
                val parsedSlides = mutableListOf<NativeSlideModel>()
                for ((idx, slidePath) in orderedSlidePaths.withIndex()) {
                    val slideNum = idx + 1

                    // Relationships for this slide (rId -> media path)
                    val slideRels = mutableMapOf<String, String>()
                    val relsPath = slidePath.replace("ppt/slides/", "ppt/slides/_rels/") + ".rels"
                    val relsEntry = zip.getEntry(relsPath)
                    if (relsEntry != null) {
                        zip.getInputStream(relsEntry).use { input ->
                            val doc = parseXmlDocument(input)
                            for (r in doc.findDescendants("Relationship")) {
                                val id = r.getAttribute("Id")
                                val target = r.getAttribute("Target").replace('\\', '/')
                                    .substringAfterLast('/').lowercase().trim()
                                if (id.isNotBlank() && target.isNotBlank()) {
                                    slideRels[id] = target
                                }
                            }
                        }
                    }

                    // Parse slide XML
                    val slideEntry = zip.getEntry(slidePath) ?: continue
                    var backgroundKey: String? = null
                    val elements = mutableListOf<NativeSlideElement>()
                    val allExtractedText = StringBuilder()
                    var detectedTitle = ""

                    zip.getInputStream(slideEntry).use { input ->
                        val doc = parseXmlDocument(input)

                        // A. Check for pictures (<p:pic>)
                        val pics = doc.findDescendants("pic")
                        for (pic in pics) {
                            val blip = pic.findFirstDescendant("blip")
                            val embedId = blip?.getAttribute("r:embed") ?: ""
                            val mediaKey = slideRels[embedId] ?: ""
                            if (mediaKey.isBlank() || !mediaMap.containsKey(mediaKey)) continue

                            val xfrm = pic.findFirstDescendant("xfrm")
                            val off = xfrm?.findFirstDescendant("off")
                            val ext = xfrm?.findFirstDescendant("ext")

                            val xEmu = off?.getAttribute("x")?.toLongOrNull() ?: 0L
                            val yEmu = off?.getAttribute("y")?.toLongOrNull() ?: 0L
                            val wEmu = ext?.getAttribute("cx")?.toLongOrNull() ?: slideWidthEmu
                            val hEmu = ext?.getAttribute("cy")?.toLongOrNull() ?: slideHeightEmu

                            val relX = xEmu.toFloat() / slideWidthEmu
                            val relY = yEmu.toFloat() / slideHeightEmu
                            val relW = wEmu.toFloat() / slideWidthEmu
                            val relH = hEmu.toFloat() / slideHeightEmu

                            val isFullBleed = (relW >= 0.85f && relH >= 0.85f)
                            if (isFullBleed && backgroundKey == null) {
                                backgroundKey = mediaKey
                            }

                            elements.add(
                                NativeSlideElement.Picture(
                                    relX = relX,
                                    relY = relY,
                                    relW = relW,
                                    relH = relH,
                                    mediaKey = mediaKey,
                                    isFullBleedBackground = isFullBleed
                                )
                            )
                        }

                        // B. Check for shapes (<p:sp>)
                        val shapes = doc.findDescendants("sp")
                        for (sp in shapes) {
                            val xfrm = sp.findFirstDescendant("xfrm") ?: continue
                            val off = xfrm.findFirstDescendant("off")
                            val ext = xfrm.findFirstDescendant("ext")

                            val xEmu = off?.getAttribute("x")?.toLongOrNull() ?: 0L
                            val yEmu = off?.getAttribute("y")?.toLongOrNull() ?: 0L
                            val wEmu = ext?.getAttribute("cx")?.toLongOrNull() ?: 0L
                            val hEmu = ext?.getAttribute("cy")?.toLongOrNull() ?: 0L

                            if (wEmu <= 0L || hEmu <= 0L) continue

                            val relX = xEmu.toFloat() / slideWidthEmu
                            val relY = yEmu.toFloat() / slideHeightEmu
                            val relW = wEmu.toFloat() / slideWidthEmu
                            val relH = hEmu.toFloat() / slideHeightEmu

                            // Check fill color
                            var fillColor: Int? = null
                            val spPr = sp.findFirstDescendant("spPr")
                            val solidFill = spPr?.findFirstDescendant("solidFill")
                            val srgbClr = solidFill?.findFirstDescendant("srgbClr")
                            if (srgbClr != null) {
                                val hex = srgbClr.getAttribute("val")
                                if (hex.isNotBlank()) {
                                    fillColor = parseHexColor(hex)
                                }
                            }

                            // Check stroke border
                            var strokeColor: Int? = null
                            var strokeWidth = 0f
                            val ln = spPr?.findFirstDescendant("ln")
                            if (ln != null) {
                                val lnFill = ln.findFirstDescendant("solidFill")?.findFirstDescendant("srgbClr")
                                if (lnFill != null) {
                                    strokeColor = parseHexColor(lnFill.getAttribute("val"))
                                }
                                val wVal = ln.getAttribute("w").toLongOrNull() ?: 0L
                                strokeWidth = if (wVal > 0) (wVal / 12700f).coerceIn(1f, 8f) else 1f
                            }

                            // Parse text body (<p:txBody>)
                            val txBody = sp.findFirstDescendant("txBody")
                            val paragraphs = mutableListOf<NativeParagraph>()

                            if (txBody != null) {
                                val pNodes = txBody.findDescendants("p")
                                for (p in pNodes) {
                                    val pPr = p.findFirstDescendant("pPr")
                                    val algnAttr = pPr?.getAttribute("algn") ?: ""
                                    val alignment = when (algnAttr) {
                                        "ctr" -> Layout.Alignment.ALIGN_CENTER
                                        "r" -> Layout.Alignment.ALIGN_OPPOSITE
                                        else -> Layout.Alignment.ALIGN_NORMAL
                                    }

                                    val runs = mutableListOf<NativeTextRun>()
                                    val rNodes = p.findDescendants("r")
                                    for (r in rNodes) {
                                        val t = r.findFirstDescendant("t")?.textContent ?: ""
                                        if (t.isEmpty()) continue

                                        allExtractedText.append(t).append(" ")

                                        val rPr = r.findFirstDescendant("rPr")
                                        val szVal = rPr?.getAttribute("sz")?.toIntOrNull() ?: 2000
                                        val fontSizePt = (szVal / 100f).coerceIn(10f, 72f)
                                        val isBold = rPr?.getAttribute("b") == "1"
                                        val isItalic = rPr?.getAttribute("i") == "1"

                                        var textColor = android.graphics.Color.rgb(30, 41, 59)
                                        val clr = rPr?.findFirstDescendant("srgbClr")?.getAttribute("val")
                                        if (!clr.isNullOrBlank()) {
                                            textColor = parseHexColor(clr) ?: textColor
                                        }

                                        runs.add(
                                            NativeTextRun(
                                                text = t,
                                                fontSizePt = fontSizePt,
                                                isBold = isBold,
                                                isItalic = isItalic,
                                                color = textColor
                                            )
                                        )

                                        if (detectedTitle.isBlank() && (fontSizePt >= 24f || isBold)) {
                                            val clean = t.trim()
                                            if (clean.length in 3..60) {
                                                detectedTitle = clean
                                            }
                                        }
                                    }

                                    if (runs.isNotEmpty()) {
                                        paragraphs.add(NativeParagraph(alignment, runs))
                                    }
                                }
                            }

                            // If shape has visual presence (fill, stroke, or text), keep it
                            if (fillColor != null || strokeColor != null || paragraphs.isNotEmpty()) {
                                elements.add(
                                    NativeSlideElement.Shape(
                                        relX = relX,
                                        relY = relY,
                                        relW = relW,
                                        relH = relH,
                                        fillColor = fillColor,
                                        strokeColor = strokeColor,
                                        strokeWidthPt = strokeWidth,
                                        cornerRadiusPx = 16f,
                                        paragraphs = paragraphs
                                    )
                                )
                            }
                        }

                        // C. Check for Tables (<p:graphicFrame> containing <a:tbl>)
                        val frames = doc.findDescendants("graphicFrame")
                        for (gf in frames) {
                            val tbl = gf.findFirstDescendant("tbl") ?: continue
                            val xfrm = gf.findFirstDescendant("xfrm") ?: continue
                            val off = xfrm.findFirstDescendant("off")
                            val ext = xfrm.findFirstDescendant("ext")

                            val xEmu = off?.getAttribute("x")?.toLongOrNull() ?: 0L
                            val yEmu = off?.getAttribute("y")?.toLongOrNull() ?: 0L
                            val wEmu = ext?.getAttribute("cx")?.toLongOrNull() ?: 0L
                            val hEmu = ext?.getAttribute("cy")?.toLongOrNull() ?: 0L

                            if (wEmu <= 0L || hEmu <= 0L) continue

                            val relX = xEmu.toFloat() / slideWidthEmu
                            val relY = yEmu.toFloat() / slideHeightEmu
                            val relW = wEmu.toFloat() / slideWidthEmu
                            val relH = hEmu.toFloat() / slideHeightEmu

                            // Column widths
                            val gridCols = tbl.findDescendants("gridCol")
                            val colWeights = gridCols.map { it.getAttribute("w").toLongOrNull() ?: 1000L }
                            val totalColWeight = max(1L, colWeights.sum())
                            val colWidthRels = colWeights.map { it.toFloat() / totalColWeight }

                            // Rows
                            val trList = tbl.findDescendants("tr")
                            val rowHeights = trList.map { it.getAttribute("h").toLongOrNull() ?: 1000L }
                            val totalRowWeight = max(1L, rowHeights.sum())

                            val tableRows = mutableListOf<NativeTableRow>()
                            for ((rIdx, tr) in trList.withIndex()) {
                                val isHeader = (rIdx == 0)
                                val rowHeightRel = (rowHeights[rIdx].toFloat() / totalRowWeight)
                                val cells = mutableListOf<NativeTableCell>()

                                val tcList = tr.findDescendants("tc")
                                for (tc in tcList) {
                                    val cellText = tc.findDescendants("t")
                                        .joinToString("") { it.textContent }
                                        .trim()

                                    allExtractedText.append(cellText).append(" ")

                                    var cellFill: Int? = null
                                    val tcPr = tc.findFirstDescendant("tcPr")
                                    val solidFill = tcPr?.findFirstDescendant("solidFill")?.findFirstDescendant("srgbClr")
                                    if (solidFill != null) {
                                        cellFill = parseHexColor(solidFill.getAttribute("val"))
                                    }

                                    cells.add(
                                        NativeTableCell(
                                            text = cellText,
                                            fillColor = cellFill,
                                            isBold = isHeader,
                                            textColor = if (isHeader) android.graphics.Color.WHITE else android.graphics.Color.rgb(30, 41, 59)
                                        )
                                    )
                                }

                                tableRows.add(NativeTableRow(rowHeightRel, isHeader, cells))
                            }

                            if (tableRows.isNotEmpty()) {
                                elements.add(
                                    NativeSlideElement.Table(
                                        relX = relX,
                                        relY = relY,
                                        relW = relW,
                                        relH = relH,
                                        colWidthRels = if (colWidthRels.isNotEmpty()) colWidthRels else listOf(1f),
                                        rows = tableRows
                                    )
                                )
                            }
                        }
                    }

                    if (detectedTitle.isBlank()) {
                        detectedTitle = "Slide $slideNum"
                    }

                    parsedSlides.add(
                        NativeSlideModel(
                            slideIndex = idx,
                            slideNumber = slideNum,
                            title = detectedTitle,
                            extractedText = allExtractedText.toString().trim(),
                            notes = "",
                            backgroundMediaKey = backgroundKey,
                            elements = elements
                        )
                    )
                }

                NativePresentationModel(
                    slideWidthEmu = slideWidthEmu,
                    slideHeightEmu = slideHeightEmu,
                    aspectRatio = aspectRatio,
                    slides = parsedSlides,
                    mediaMap = mediaMap
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing presentation with Native OpenXML Engine", e)
            null
        }
    }

    /**
     * Renders a native slide into a crisp Android Bitmap with ZERO watermark.
     */
    private fun renderNativeSlideBitmap(
        pres: NativePresentationModel,
        slide: NativeSlideModel,
        scale: Float = 1.5f
    ): Bitmap? {
        return try {
            val baseWidth = 1600f
            val targetWidth = (baseWidth * scale).toInt().coerceIn(360, 3840)
            val targetHeight = (targetWidth / pres.aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Draw Default Canvas Background (Clean White)
            canvas.drawColor(android.graphics.Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

            // 2. Draw Full-Bleed Background Template Image (1600x900)
            if (slide.backgroundMediaKey != null) {
                val bgBytes = pres.mediaMap[slide.backgroundMediaKey]
                if (bgBytes != null) {
                    val bgBmp = BitmapFactory.decodeByteArray(bgBytes, 0, bgBytes.size)
                    if (bgBmp != null) {
                        val dstRect = Rect(0, 0, targetWidth, targetHeight)
                        canvas.drawBitmap(bgBmp, null, dstRect, paint)
                    }
                }
            }

            // 3. Draw Elements (Pictures, Shapes, Tables)
            val ptToPx = (targetWidth / (pres.slideWidthEmu / 12700f)).coerceAtLeast(1f)

            for (element in slide.elements) {
                when (element) {
                    is NativeSlideElement.Picture -> {
                        // Skip if already rendered as full bleed background
                        if (element.isFullBleedBackground && element.mediaKey == slide.backgroundMediaKey) {
                            continue
                        }

                        val picBytes = pres.mediaMap[element.mediaKey] ?: continue
                        val picBmp = BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size) ?: continue

                        val pX = element.relX * targetWidth
                        val pY = element.relY * targetHeight
                        val pW = element.relW * targetWidth
                        val pH = element.relH * targetHeight

                        val dstRect = RectF(pX, pY, pX + pW, pY + pH)
                        canvas.drawBitmap(picBmp, null, dstRect, paint)
                    }

                    is NativeSlideElement.Shape -> {
                        val sX = element.relX * targetWidth
                        val sY = element.relY * targetHeight
                        val sW = element.relW * targetWidth
                        val sH = element.relH * targetHeight
                        val corner = (element.cornerRadiusPx * (targetWidth / 1600f)).coerceAtLeast(4f)
                        val rectF = RectF(sX, sY, sX + sW, sY + sH)

                        // Draw Shape Fill
                        if (element.fillColor != null) {
                            fillPaint.color = element.fillColor
                            canvas.drawRoundRect(rectF, corner, corner, fillPaint)
                        }

                        // Draw Shape Stroke / Border
                        if (element.strokeColor != null) {
                            strokePaint.color = element.strokeColor
                            strokePaint.strokeWidth = max(1f, element.strokeWidthPt * ptToPx)
                            canvas.drawRoundRect(rectF, corner, corner, strokePaint)
                        }

                        // Draw Formatted Text Blocks
                        if (element.paragraphs.isNotEmpty()) {
                            val innerPad = (16f * (targetWidth / 1600f)).coerceAtLeast(8f)
                            val availWidth = (sW - innerPad * 2).coerceAtLeast(40f).toInt()
                            var currentY = sY + innerPad

                            for (para in element.paragraphs) {
                                if (para.runs.isEmpty()) continue

                                val spannable = SpannableStringBuilder()
                                for (run in para.runs) {
                                    val start = spannable.length
                                    spannable.append(run.text)
                                    val end = spannable.length

                                    val fontPx = (run.fontSizePt * ptToPx).toInt().coerceIn(12, 120)
                                    spannable.setSpan(AbsoluteSizeSpan(fontPx), start, end, 0)
                                    spannable.setSpan(ForegroundColorSpan(run.color), start, end, 0)

                                    if (run.isBold && run.isItalic) {
                                        spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, 0)
                                    } else if (run.isBold) {
                                        spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                                    } else if (run.isItalic) {
                                        spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)
                                    }
                                }

                                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
                                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    StaticLayout.Builder.obtain(spannable, 0, spannable.length, textPaint, availWidth)
                                        .setAlignment(para.alignment)
                                        .setLineSpacing(0f, 1.15f)
                                        .setIncludePad(true)
                                        .build()
                                } else {
                                    @Suppress("DEPRECATION")
                                    StaticLayout(spannable, textPaint, availWidth, para.alignment, 1.15f, 0f, true)
                                }

                                canvas.save()
                                canvas.translate(sX + innerPad, currentY)
                                layout.draw(canvas)
                                canvas.restore()

                                currentY += layout.height + (6f * (targetWidth / 1600f))
                            }
                        }
                    }

                    is NativeSlideElement.Table -> {
                        val tX = element.relX * targetWidth
                        val tY = element.relY * targetHeight
                        val tW = element.relW * targetWidth
                        val tH = element.relH * targetHeight

                        var currentY = tY
                        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            color = android.graphics.Color.rgb(203, 213, 225) // Slate 300
                            strokeWidth = max(1f, 1.5f * (targetWidth / 1600f))
                        }

                        for ((rIdx, row) in element.rows.withIndex()) {
                            val rowH = (row.rowHeightRel * tH).coerceAtLeast(30f * (targetWidth / 1600f))
                            var currentX = tX

                            for ((cIdx, cell) in row.cells.withIndex()) {
                                val colPct = element.colWidthRels.getOrNull(cIdx) ?: (1f / row.cells.size)
                                val cellW = colPct * tW
                                val cellRect = RectF(currentX, currentY, currentX + cellW, currentY + rowH)

                                // Cell Background
                                cellPaint.style = Paint.Style.FILL
                                cellPaint.color = when {
                                    cell.fillColor != null -> cell.fillColor
                                    row.isHeader -> android.graphics.Color.rgb(85, 169, 175) // Dusky Aqua
                                    rIdx % 2 == 1 -> android.graphics.Color.rgb(248, 250, 252)
                                    else -> android.graphics.Color.WHITE
                                }
                                canvas.drawRect(cellRect, cellPaint)
                                canvas.drawRect(cellRect, borderPaint)

                                // Cell Text
                                if (cell.text.isNotBlank()) {
                                    val cellTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = cell.textColor ?: if (row.isHeader) android.graphics.Color.WHITE else android.graphics.Color.rgb(30, 41, 59)
                                        textSize = ((if (row.isHeader) 16f else 14f) * ptToPx).coerceIn(12f, 48f)
                                        typeface = if (cell.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                                    }

                                    val padding = (8f * (targetWidth / 1600f)).coerceAtLeast(4f)
                                    val availCellW = (cellW - padding * 2).coerceAtLeast(20f).toInt()
                                    val cellLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        StaticLayout.Builder.obtain(cell.text, 0, cell.text.length, cellTextPaint, availCellW)
                                            .setAlignment(if (row.isHeader) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                                            .setLineSpacing(0f, 1.1f)
                                            .build()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        StaticLayout(cell.text, cellTextPaint, availCellW, if (row.isHeader) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, true)
                                    }

                                    canvas.save()
                                    val textOffsetY = currentY + max(padding, (rowH - cellLayout.height) / 2f)
                                    canvas.translate(currentX + padding, textOffsetY)
                                    cellLayout.draw(canvas)
                                    canvas.restore()
                                }

                                currentX += cellW
                            }

                            currentY += rowH
                        }
                    }
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering native slide $slide.slideIndex", e)
            null
        }
    }

    // =========================================================================
    // XML DOM PARSING HELPERS (LIGHTWEIGHT, FAST, NAMESPACE-AGNOSTIC)
    // =========================================================================

    private fun parseXmlDocument(input: InputStream): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.isExpandEntityReferences = false
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        } catch (_: Exception) {}
        return factory.newDocumentBuilder().parse(input)
    }

    private fun Node.isMatchingTag(name: String): Boolean {
        val nn = nodeName.lowercase()
        val target = name.lowercase()
        return nn == target || nn.endsWith(":$target")
    }

    private fun Node.findFirstDescendant(name: String): Element? {
        val queue = ArrayDeque<Node>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr is Element && curr != this && curr.isMatchingTag(name)) {
                return curr
            }
            val children = curr.childNodes
            for (i in 0 until children.length) {
                queue.add(children.item(i))
            }
        }
        return null
    }

    private fun Node.findDescendants(name: String): List<Element> {
        val result = mutableListOf<Element>()
        val queue = ArrayDeque<Node>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr is Element && curr != this && curr.isMatchingTag(name)) {
                result.add(curr)
            }
            val children = curr.childNodes
            for (i in 0 until children.length) {
                queue.add(children.item(i))
            }
        }
        return result
    }

    private fun parseHexColor(hex: String): Int? {
        return try {
            val clean = hex.removePrefix("#").trim()
            if (clean.length == 6) {
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                android.graphics.Color.rgb(r, g, b)
            } else if (clean.length == 8) {
                val a = clean.substring(0, 2).toInt(16)
                val r = clean.substring(2, 4).toInt(16)
                val g = clean.substring(4, 6).toInt(16)
                val b = clean.substring(6, 8).toInt(16)
                android.graphics.Color.argb(a, r, g, b)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // WATERMARK SCRUBBER (FOR ASPOSE FALLBACK ON LEGACY .PPT)
    // =========================================================================

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

        for (y in yStart..yEnd) {
            val rowOffset = y * width
            for (x in xStart..xEnd) {
                val color = pixels[rowOffset + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                if (r > 135 && (r - g >= 7) && (r - b >= 7)) {
                    isWatermark[rowOffset + x] = true
                    detectedCount++
                }
            }
        }

        if (detectedCount == 0) {
            return bitmap
        }

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
     * Frees memory and cleans up presentation engines.
     */
    fun close() = renderLock.withLock {
        try {
            activePresentation?.dispose()
        } catch (_: Exception) {}
        activePresentation = null
        nativePresentation = null
        activeDocument = null
        isUsingNativeEngine = false
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
