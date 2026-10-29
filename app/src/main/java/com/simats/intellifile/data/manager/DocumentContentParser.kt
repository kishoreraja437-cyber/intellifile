package com.simats.intellifile.data.manager

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

sealed class ParsedDocumentContent {
    data class Presentation(
        val totalSlides: Int,
        val slides: List<SlideData>
    ) : ParsedDocumentContent()

    data class Spreadsheet(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>
    ) : ParsedDocumentContent()

    data class RichDocument(
        val title: String,
        val totalPages: Int,
        val pages: List<DocumentPageData>,
        val fullText: String
    ) : ParsedDocumentContent()

    data class PlainText(
        val title: String,
        val text: String,
        val linesCount: Int
    ) : ParsedDocumentContent()

    data class Pdf(
        val totalPages: Int
    ) : ParsedDocumentContent()

    data class Error(
        val message: String
    ) : ParsedDocumentContent()
}

data class SlideData(
    val slideNumber: Int,
    val title: String,
    val points: List<String>,
    val rawContent: String = "",
    val notes: String = "",
    val imageBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SlideData
        if (slideNumber != other.slideNumber) return false
        if (title != other.title) return false
        if (points != other.points) return false
        if (rawContent != other.rawContent) return false
        if (notes != other.notes) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = slideNumber
        result = 31 * result + title.hashCode()
        result = 31 * result + points.hashCode()
        result = 31 * result + rawContent.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class DocumentPageData(
    val pageNumber: Int,
    val paragraphs: List<String>
)

object DocumentContentParser {

    /**
     * Parses any supported document (PDF, DOCX, DOC, PPTX, PPT, XLSX, XLS, CSV, TXT, JSON, MD)
     * into a structured in-app viewable representation.
     */
    fun parse(
        context: Context,
        uriString: String,
        path: String,
        format: String,
        fileName: String
    ): ParsedDocumentContent {
        val rawExt = fileName.substringAfterLast('.', "").lowercase().trim()
        val rawFmt = format.lowercase().trim()

        if (rawExt == "pdf" || rawFmt == "pdf") {
            return ParsedDocumentContent.Pdf(totalPages = 1)
        }

        val bytes = readFileBytes(context, uriString, path, fileName)
            ?: return ParsedDocumentContent.Error("Unable to open file. Please ensure storage permission is granted.")

        if (bytes.isEmpty()) {
            return ParsedDocumentContent.PlainText(
                title = fileName,
                text = "This file is empty (0 bytes).",
                linesCount = 1
            )
        }

        // Magic byte checks
        val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

        val isOle = bytes.size >= 8 &&
                bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

        val isPdfMagic = bytes.size >= 4 &&
                bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

        if (isPdfMagic) {
            return ParsedDocumentContent.Pdf(totalPages = 1)
        }

        // Format and extension heuristics
        val isPptHint = rawExt in listOf("pptx", "ppt", "pps", "ppsx", "pot", "potx", "odp") ||
                rawFmt.contains("ppt") || rawFmt.contains("powerpoint") || rawFmt.contains("presentation")

        val isWordHint = rawExt in listOf("docx", "doc", "dot", "dotx", "odt", "rtf") ||
                rawFmt.contains("doc") || rawFmt.contains("word")

        val isExcelHint = rawExt in listOf("xlsx", "xls", "xlsm", "xlsb", "csv", "tsv", "ods") ||
                rawFmt.contains("xls") || rawFmt.contains("excel") || rawFmt.contains("sheet") || rawFmt.contains("spreadsheet")

        val isCsvHint = rawExt == "csv" || rawFmt.contains("csv")

        // Inspect zip entry structure if it's a zip archive
        var zipHasPpt = false
        var zipHasWord = false
        var zipHasExcel = false

        if (isZip) {
            try {
                val zis = ZipInputStream(ByteArrayInputStream(bytes))
                var ze = zis.nextEntry
                while (ze != null) {
                    val n = ze.name.replace('\\', '/').lowercase()
                    if (n.contains("ppt/") || n.contains("slides/")) zipHasPpt = true
                    if (n.contains("word/") || n.contains("document.xml")) zipHasWord = true
                    if (n.contains("xl/") || n.contains("worksheets/")) zipHasExcel = true
                    ze = zis.nextEntry
                }
            } catch (_: Exception) {}
        }

        return try {
            when {
                // Priority 1: PPT/PPTX presentations
                zipHasPpt || isPptHint -> parsePresentation(bytes, fileName)

                // Priority 2: Word documents
                zipHasWord || isWordHint -> {
                    if (isZip || rawExt == "docx") {
                        try {
                            parseDocx(bytes, fileName)
                        } catch (_: Exception) {
                            parseDoc(bytes, fileName)
                        }
                    } else {
                        parseDoc(bytes, fileName)
                    }
                }

                // Priority 3: Excel spreadsheets
                zipHasExcel || (isExcelHint && !isCsvHint) -> {
                    if (isZip || rawExt == "xlsx") {
                        try {
                            parseXlsx(bytes, fileName)
                        } catch (_: Exception) {
                            parseXls(bytes, fileName)
                        }
                    } else {
                        parseXls(bytes, fileName)
                    }
                }

                // Priority 4: CSV files
                isCsvHint -> parseCsv(bytes, fileName)

                // Priority 5: Plain text & code
                rawExt in listOf("txt", "json", "md", "xml", "html", "htm", "log", "rtf", "yaml", "yml", "kt", "java", "py", "sql") -> {
                    parsePlainText(bytes, fileName)
                }

                // Fallbacks
                isZip -> {
                    val pptx = parsePptx(bytes, fileName)
                    if (pptx != null && pptx.slides.isNotEmpty()) pptx
                    else {
                        try { parseDocx(bytes, fileName) }
                        catch (_: Exception) {
                            try { parseXlsx(bytes, fileName) }
                            catch (_: Exception) { parseFallback(bytes, fileName) }
                        }
                    }
                }

                isOle -> {
                    val ppt = parsePpt(bytes, fileName)
                    if (ppt != null && ppt.slides.isNotEmpty()) ppt
                    else {
                        val doc = parseDoc(bytes, fileName)
                        if (doc.pages.isNotEmpty()) doc else parseFallback(bytes, fileName)
                    }
                }

                else -> parseFallback(bytes, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isPptHint || zipHasPpt) {
                parsePresentationFallback(bytes, fileName)
            } else {
                parseFallback(bytes, fileName)
            }
        }
    }

    private fun readFileBytes(context: Context, uriString: String, path: String, fileName: String = ""): ByteArray? {
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "content") {
                    context.contentResolver.openInputStream(uri)?.use { return it.readBytes() }
                } else if (uri.scheme == "file") {
                    val f = File(uri.path ?: "")
                    if (f.exists() && f.canRead()) {
                        return f.readBytes()
                    }
                }
            } catch (_: Exception) {}
        }

        if (path.isNotBlank()) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    return f.readBytes()
                }
            } catch (_: Exception) {}

            if (path.startsWith("content://")) {
                try {
                    val uri = Uri.parse(path)
                    context.contentResolver.openInputStream(uri)?.use { return it.readBytes() }
                } catch (_: Exception) {}
            }
        }

        try {
            LocalFileManager.getFileDescriptor(context, uriString, path)?.use { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { return it.readBytes() }
            }
        } catch (_: Exception) {}

        // Fallback: If uriString and path were empty, resolve by fileName from cache or local device files
        if (fileName.isNotBlank()) {
            try {
                val cacheFile = File(context.cacheDir, fileName)
                if (cacheFile.exists() && cacheFile.canRead()) return cacheFile.readBytes()
                val filesFile = File(context.filesDir, fileName)
                if (filesFile.exists() && filesFile.canRead()) return filesFile.readBytes()

                val found = LocalFileManager.getDeviceFiles(context).firstOrNull {
                    it.title.equals(fileName, ignoreCase = true) ||
                            it.title.equals(fileName.substringBeforeLast('.'), ignoreCase = true)
                }
                if (found != null) {
                    val bytes = readFileBytes(context, found.uri.toString(), found.path)
                    if (bytes != null && bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {}
        }

        return null
    }

    // ==========================================
    // 1. PRESENTATION PARSERS (PPTX & PPT)
    // ==========================================

    private fun parsePresentation(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

        val isOle = bytes.size >= 8 &&
                bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

        val ext = fileName.substringAfterLast('.', "").lowercase().trim()

        // 1. If it's a ZIP or ext is pptx, try PPTX parser first
        if (isZip || ext == "pptx" || ext == "ppsx" || ext == "potx") {
            try {
                val pptxResult = parsePptx(bytes, fileName)
                if (pptxResult != null && pptxResult.slides.isNotEmpty()) {
                    return pptxResult
                }
            } catch (_: Exception) {}

            // Fallback to binary PPT
            try {
                val pptResult = parsePpt(bytes, fileName)
                if (pptResult != null && pptResult.slides.isNotEmpty()) {
                    return pptResult
                }
            } catch (_: Exception) {}
        }

        // 2. If it's OLE or ext is ppt, try binary PPT parser first
        if (isOle || ext == "ppt" || ext == "pps" || ext == "pot") {
            try {
                val pptResult = parsePpt(bytes, fileName)
                if (pptResult != null && pptResult.slides.isNotEmpty()) {
                    return pptResult
                }
            } catch (_: Exception) {}

            // Fallback to PPTX (in case of renamed file)
            try {
                val pptxResult = parsePptx(bytes, fileName)
                if (pptxResult != null && pptxResult.slides.isNotEmpty()) {
                    return pptxResult
                }
            } catch (_: Exception) {}
        }

        // 3. Try PPTX then PPT regardless of header
        try {
            val pptxResult = parsePptx(bytes, fileName)
            if (pptxResult != null && pptxResult.slides.isNotEmpty()) {
                return pptxResult
            }
        } catch (_: Exception) {}

        try {
            val pptResult = parsePpt(bytes, fileName)
            if (pptResult != null && pptResult.slides.isNotEmpty()) {
                return pptResult
            }
        } catch (_: Exception) {}

        return parsePresentationFallback(bytes, fileName)
    }

    fun parsePptx(bytes: ByteArray, fileName: String): ParsedDocumentContent.Presentation? {
        val slideNumRegex = Regex("(?i)slide([0-9]+)\\.xml$")
        val notesNumRegex = Regex("(?i)notesSlide([0-9]+)\\.xml$")
        val relsNumRegex = Regex("(?i)slide([0-9]+)\\.xml\\.rels$")
        val relsTargetRegex = Regex("(?i)Target=\"(?:\\.\\./)?media/([^\"]+)\"")

        val slideXmlMap = mutableMapOf<Int, String>()
        val slideNotesMap = mutableMapOf<Int, String>()
        val slideRelsMap = mutableMapOf<Int, String>()
        val mediaMap = mutableMapOf<String, ByteArray>()
        val unnumberedSlides = mutableListOf<String>()

        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zip.nextEntry

        while (entry != null) {
            val entryName = entry.name.replace('\\', '/')
            val lowerName = entryName.lowercase()

            val isSlide = lowerName.contains("slides/") && lowerName.endsWith(".xml") && !lowerName.contains("_rels")
            val isNotes = lowerName.contains("notesslides/") && lowerName.endsWith(".xml") && !lowerName.contains("_rels")
            val isRels = lowerName.contains("slides/_rels/") && lowerName.endsWith(".xml.rels")
            val isMedia = lowerName.contains("ppt/media/") || lowerName.contains("media/")

            if (isSlide || isNotes || isRels || isMedia) {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var len: Int
                while (zip.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                val entryBytes = out.toByteArray()

                if (isSlide) {
                    val content = String(entryBytes, StandardCharsets.UTF_8)
                    val numMatch = slideNumRegex.find(entryName)
                    val slideNum = numMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (slideNum != null) {
                        slideXmlMap[slideNum] = content
                    } else {
                        unnumberedSlides.add(content)
                    }
                } else if (isNotes) {
                    val content = String(entryBytes, StandardCharsets.UTF_8)
                    val numMatch = notesNumRegex.find(entryName)
                    val notesNum = numMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (notesNum != null) {
                        slideNotesMap[notesNum] = content
                    }
                } else if (isRels) {
                    val content = String(entryBytes, StandardCharsets.UTF_8)
                    val numMatch = relsNumRegex.find(entryName)
                    val relsNum = numMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (relsNum != null) {
                        slideRelsMap[relsNum] = content
                    }
                } else if (isMedia) {
                    val simpleName = entryName.substringAfterLast('/').lowercase()
                    mediaMap[simpleName] = entryBytes
                }
            }
            entry = zip.nextEntry
        }

        val orderedXmls = mutableListOf<String>()
        if (slideXmlMap.isNotEmpty()) {
            val sortedKeys = slideXmlMap.keys.sorted()
            for (k in sortedKeys) {
                orderedXmls.add(slideXmlMap[k]!!)
            }
        }
        orderedXmls.addAll(unnumberedSlides)

        if (orderedXmls.isEmpty()) {
            return null
        }

        val slides = mutableListOf<SlideData>()
        for ((index, xml) in orderedXmls.withIndex()) {
            val slideNumber = index + 1
            val relsXml = slideRelsMap[slideNumber]
            var slideImageBytes: ByteArray? = null
            if (relsXml != null) {
                val targetMatch = relsTargetRegex.find(relsXml)
                if (targetMatch != null) {
                    val imgFileName = targetMatch.groupValues[1].substringAfterLast('/').lowercase()
                    slideImageBytes = mediaMap[imgFileName]
                }
            }
            val slideData = parseSlideXml(xml, slideNumber, fileName, slideNotesMap[slideNumber], slideImageBytes)
            slides.add(slideData)
        }

        return ParsedDocumentContent.Presentation(
            totalSlides = slides.size,
            slides = slides
        )
    }

    private fun parseSlideXml(
        xml: String,
        slideNumber: Int,
        defaultTitle: String,
        notesXml: String?,
        imageBytes: ByteArray? = null
    ): SlideData {
        val pRegex = Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<a:t[^>]*>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
        val brRegex = Regex("<a:br[^>]*/?>")
        val titlePhRegex = Regex("<p:ph[^>]*type=\"(?:ctrTitle|title)\"|<p:ph[^>]*idx=\"0\"")
        val spRegex = Regex("<p:sp[ >](.*?)</p:sp>", RegexOption.DOT_MATCHES_ALL)

        val titleParagraphs = mutableListOf<String>()
        val allParagraphs = mutableListOf<String>()

        // 1. Scan for explicit title in title shape
        for (shapeMatch in spRegex.findAll(xml)) {
            val shapeXml = shapeMatch.value
            val isTitle = titlePhRegex.containsMatchIn(shapeXml)
            if (isTitle) {
                for (pMatch in pRegex.findAll(shapeXml)) {
                    val withBreaks = brRegex.replace(pMatch.value, "\n")
                    val text = tRegex.findAll(withBreaks).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
                    if (text.isNotBlank()) {
                        text.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                            titleParagraphs.add(it)
                        }
                    }
                }
            }
        }

        // 2. Scan ALL DrawingML paragraphs across the entire slide XML
        // This captures text in shapes, text boxes, graphic frames, tables, group shapes, SmartArt
        for (pMatch in pRegex.findAll(xml)) {
            val withBreaks = brRegex.replace(pMatch.value, "\n")
            val text = tRegex.findAll(withBreaks).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
            if (text.isNotBlank()) {
                text.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    allParagraphs.add(it)
                }
            }
        }

        // 3. Scan for table rows if present
        val trRegex = Regex("<a:tr[ >](.*?)</a:tr>", RegexOption.DOT_MATCHES_ALL)
        val tcRegex = Regex("<a:tc[ >](.*?)</a:tc>", RegexOption.DOT_MATCHES_ALL)
        val tableRows = mutableListOf<String>()
        for (tr in trRegex.findAll(xml)) {
            val cellTexts = tcRegex.findAll(tr.value).map { tc ->
                tRegex.findAll(tc.value).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
            }.filter { it.isNotBlank() }.toList()

            if (cellTexts.isNotEmpty()) {
                tableRows.add(cellTexts.joinToString("  |  "))
            }
        }

        // 4. Determine Title and Content Points
        val title: String
        val points = mutableListOf<String>()

        if (titleParagraphs.isNotEmpty()) {
            title = titleParagraphs.first()
            if (titleParagraphs.size > 1) {
                points.addAll(titleParagraphs.drop(1))
            }
            var skippedTitle = false
            for (p in allParagraphs) {
                if (!skippedTitle && p == title) {
                    skippedTitle = true
                    continue
                }
                if (!points.contains(p)) {
                    points.add(p)
                }
            }
        } else if (allParagraphs.isNotEmpty()) {
            title = allParagraphs.first()
            if (allParagraphs.size > 1) {
                points.addAll(allParagraphs.drop(1))
            } else {
                points.add(allParagraphs.first())
            }
        } else {
            title = "Slide $slideNumber"
        }

        for (tRow in tableRows) {
            if (!points.contains(tRow)) {
                points.add(tRow)
            }
        }

        // 5. Notes extraction
        var extractedNotes = ""
        if (notesXml != null) {
            val notesParagraphs = mutableListOf<String>()
            for (pMatch in pRegex.findAll(notesXml)) {
                val withBreaks = brRegex.replace(pMatch.value, "\n")
                val text = tRegex.findAll(withBreaks).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
                if (text.isNotBlank() && !text.all { it.isDigit() }) {
                    notesParagraphs.add(text)
                }
            }
            extractedNotes = notesParagraphs.joinToString("\n")
        }

        val rawFullContent = (listOf(title) + points).distinct().joinToString("\n\n")

        return SlideData(
            slideNumber = slideNumber,
            title = title,
            points = points,
            rawContent = rawFullContent,
            notes = extractedNotes,
            imageBytes = imageBytes
        )
    }

    fun parsePpt(bytes: ByteArray, fileName: String): ParsedDocumentContent.Presentation? {
        if (bytes.size < 32) return null

        val slideChunks = mutableListOf<MutableList<String>>()
        var currentSlide = mutableListOf<String>()

        var i = 0
        while (i < bytes.size - 8) {
            val recVerInst = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            val recType = (bytes[i + 2].toInt() and 0xFF) or ((bytes[i + 3].toInt() and 0xFF) shl 8)
            val recLen = (bytes[i + 4].toInt() and 0xFF) or
                    ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 7].toInt() and 0xFF) shl 24)

            val isContainer = (recVerInst and 0x0F) == 0x0F

            if (recType == 1006) { // RT_Slide
                if (currentSlide.isNotEmpty()) {
                    slideChunks.add(currentSlide)
                    currentSlide = mutableListOf()
                }
                i += 8
                continue
            }

            if (isContainer) {
                // If it's a container record, step inside to inspect child records
                i += 8
                continue
            }

            if (recLen in 1..200000 && i + 8 + recLen <= bytes.size) {
                when (recType) {
                    4000, 4026 -> { // RT_TextCharsAtom, RT_CString (UTF-16LE)
                        try {
                            val text = String(bytes, i + 8, recLen, StandardCharsets.UTF_16LE).trim()
                            val cleaned = cleanPptString(text)
                            if (cleaned.isNotBlank()) {
                                val lines = cleaned.split("\r\n", "\r", "\n")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && it.length >= 2 }
                                currentSlide.addAll(lines)
                            }
                        } catch (_: Exception) {}
                        i += 8 + recLen
                    }
                    4008 -> { // RT_TextBytesAtom (Latin-1 / ANSI)
                        try {
                            val text = String(bytes, i + 8, recLen, StandardCharsets.ISO_8859_1).trim()
                            val cleaned = cleanPptString(text)
                            if (cleaned.isNotBlank()) {
                                val lines = cleaned.split("\r\n", "\r", "\n")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && it.length >= 2 }
                                currentSlide.addAll(lines)
                            }
                        } catch (_: Exception) {}
                        i += 8 + recLen
                    }
                    else -> i += 8 + recLen
                }
            } else {
                i++
            }
        }

        if (currentSlide.isNotEmpty()) {
            slideChunks.add(currentSlide)
        }

        val allScanned = scanTextFromBinary(bytes)
        val finalSlides = mutableListOf<SlideData>()

        if (slideChunks.isNotEmpty() && slideChunks.any { it.isNotEmpty() }) {
            slideChunks.filter { it.isNotEmpty() }.forEachIndexed { idx, chunk ->
                val slideNum = idx + 1
                val title = chunk.first()
                val points = if (chunk.size > 1) chunk.drop(1) else listOf(chunk.first())
                finalSlides.add(
                    SlideData(
                        slideNumber = slideNum,
                        title = if (title.length > 80) title.take(80) + "..." else title,
                        points = points,
                        rawContent = chunk.joinToString("\n\n")
                    )
                )
            }
        } else if (allScanned.isNotEmpty()) {
            val chunks = allScanned.chunked(4)
            chunks.forEachIndexed { idx, chunk ->
                val slideNum = idx + 1
                val title = chunk.first()
                val points = if (chunk.size > 1) chunk.drop(1) else listOf(chunk.first())
                finalSlides.add(
                    SlideData(
                        slideNumber = slideNum,
                        title = if (title.length > 80) title.take(80) + "..." else title,
                        points = points,
                        rawContent = chunk.joinToString("\n\n")
                    )
                )
            }
        } else {
            return null
        }

        return ParsedDocumentContent.Presentation(
            totalSlides = finalSlides.size,
            slides = finalSlides
        )
    }

    private fun cleanPptString(text: String): String {
        val ignoredStrings = setOf(
            "PowerPoint Document", "Current User", "PresProps", "SummaryInformation",
            "DocumentSummaryInformation", "WordDocument", "Workbook", "Root Entry"
        )
        val trimmed = text.trim()
        if (trimmed in ignoredStrings) return ""

        return trimmed.filter { ch ->
            ch.isLetterOrDigit() || ch.isWhitespace() || ch in ".,;:!?-()[]{}'\"/\\@#%&*+=<>–—•"
        }.trim()
    }

    private fun parsePresentationFallback(bytes: ByteArray, fileName: String): ParsedDocumentContent.Presentation {
        val textScan = scanTextFromBinary(bytes)
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { "Presentation" }

        if (textScan.isNotEmpty()) {
            val slides = textScan.chunked(4).mapIndexed { idx, chunk ->
                SlideData(
                    slideNumber = idx + 1,
                    title = if (idx == 0) defaultTitle else chunk.first(),
                    points = if (idx == 0) chunk else chunk.drop(1).ifEmpty { listOf(chunk.first()) },
                    rawContent = chunk.joinToString("\n\n")
                )
            }
            return ParsedDocumentContent.Presentation(
                totalSlides = slides.size,
                slides = slides
            )
        }

        return ParsedDocumentContent.Presentation(
            totalSlides = 1,
            slides = listOf(
                SlideData(
                    slideNumber = 1,
                    title = defaultTitle,
                    points = listOf("PowerPoint presentation loaded. Content ready to present."),
                    rawContent = "Presentation loaded: $fileName"
                )
            )
        )
    }

    // ==========================================
    // 2. DOCUMENT PARSERS (DOCX & DOC)
    // ==========================================

    private fun parseDocx(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zip.nextEntry
        var documentXml: String? = null

        while (entry != null) {
            val entryName = entry.name.replace('\\', '/')
            if (entryName.endsWith("word/document.xml", ignoreCase = true)) {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var len: Int
                while (zip.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                documentXml = out.toString("UTF-8")
                break
            }
            entry = zip.nextEntry
        }

        if (documentXml.isNullOrBlank()) {
            return ParsedDocumentContent.Error("Invalid Word document (missing word/document.xml).")
        }

        val pRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<w:t[^>]*>(.*?)</w:t>")
        val pageBreakRegex = Regex("<w:br[^>]*w:type=\"page\"|<w:lastRenderedPageBreak")

        val rawParagraphs = mutableListOf<String>()
        val pageBreakIndices = mutableSetOf<Int>()

        var pIndex = 0
        for (match in pRegex.findAll(documentXml)) {
            val pContent = match.value
            val text = tRegex.findAll(pContent).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
            if (text.isNotBlank()) {
                rawParagraphs.add(text)
                if (pageBreakRegex.containsMatchIn(pContent)) {
                    pageBreakIndices.add(pIndex)
                }
                pIndex++
            }
        }

        if (rawParagraphs.isEmpty()) {
            rawParagraphs.add("This Word document contains no readable text.")
        }

        val pages = mutableListOf<DocumentPageData>()
        var currentPageParagraphs = mutableListOf<String>()
        var currentWordsCount = 0

        rawParagraphs.forEachIndexed { idx, p ->
            currentPageParagraphs.add(p)
            currentWordsCount += p.split("\\s+".toRegex()).size

            val shouldBreak = pageBreakIndices.contains(idx) || currentWordsCount >= 350 || currentPageParagraphs.size >= 7
            if (shouldBreak) {
                pages.add(DocumentPageData(pages.size + 1, currentPageParagraphs))
                currentPageParagraphs = mutableListOf()
                currentWordsCount = 0
            }
        }

        if (currentPageParagraphs.isNotEmpty()) {
            pages.add(DocumentPageData(pages.size + 1, currentPageParagraphs))
        }

        val fullText = rawParagraphs.joinToString("\n\n")

        return ParsedDocumentContent.RichDocument(
            title = fileName,
            totalPages = pages.size,
            pages = pages,
            fullText = fullText
        )
    }

    private fun parseDoc(bytes: ByteArray, fileName: String): ParsedDocumentContent.RichDocument {
        val textLines = scanTextFromBinary(bytes)

        if (textLines.isEmpty()) {
            return ParsedDocumentContent.RichDocument(
                title = fileName,
                totalPages = 1,
                pages = listOf(DocumentPageData(1, listOf("Word document loaded. No textual content detected."))),
                fullText = ""
            )
        }

        val pages = mutableListOf<DocumentPageData>()
        val chunks = textLines.chunked(6)

        chunks.forEachIndexed { index, chunk ->
            pages.add(DocumentPageData(index + 1, chunk))
        }

        return ParsedDocumentContent.RichDocument(
            title = fileName,
            totalPages = pages.size,
            pages = pages,
            fullText = textLines.joinToString("\n\n")
        )
    }

    // ==========================================
    // 3. SPREADSHEET PARSERS (XLSX, XLS, CSV)
    // ==========================================

    private fun parseXlsx(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zip.nextEntry
        val sharedStrings = mutableListOf<String>()
        var sheetXml: String? = null

        val byteEntries = mutableMapOf<String, ByteArray>()
        while (entry != null) {
            val name = entry.name.replace('\\', '/')
            if (name.contains("sharedStrings.xml", ignoreCase = true) || name.contains("worksheets/sheet", ignoreCase = true)) {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var len: Int
                while (zip.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                byteEntries[name.lowercase()] = out.toByteArray()
            }
            entry = zip.nextEntry
        }

        val sstEntryKey = byteEntries.keys.firstOrNull { it.contains("sharedstrings.xml") }
        if (sstEntryKey != null) {
            val sstXml = String(byteEntries[sstEntryKey]!!, StandardCharsets.UTF_8)
            val tRegex = Regex("<t[^>]*>(.*?)</t>")
            for (m in tRegex.findAll(sstXml)) {
                sharedStrings.add(unescapeXml(m.groupValues[1]))
            }
        }

        val firstSheetKey = byteEntries.keys.firstOrNull { it.contains("worksheets/sheet") }
        if (firstSheetKey != null) {
            sheetXml = String(byteEntries[firstSheetKey]!!, StandardCharsets.UTF_8)
        }

        if (sheetXml.isNullOrBlank()) {
            return ParsedDocumentContent.Error("Unable to locate worksheet in Excel workbook.")
        }

        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<row[ >](.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c[^>]*r=\"([A-Z]+[0-9]+)\"[^>]*(?:t=\"([^\"]*)\")?[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        val vRegex = Regex("<v>(.*?)</v>")
        val inlineStrRegex = Regex("<is><t[^>]*>(.*?)</t></is>")

        for (rowMatch in rowRegex.findAll(sheetXml)) {
            val rowContent = rowMatch.value
            val cellList = mutableListOf<String>()

            for (cellMatch in cellRegex.findAll(rowContent)) {
                val cellType = cellMatch.groupValues[2]
                val cellBody = cellMatch.groupValues[3]

                var cellVal = ""
                if (cellType == "s") {
                    val idx = vRegex.find(cellBody)?.groupValues?.get(1)?.toIntOrNull()
                    if (idx != null && idx in 0 until sharedStrings.size) {
                        cellVal = sharedStrings[idx]
                    }
                } else if (cellType == "inlineStr") {
                    cellVal = inlineStrRegex.find(cellBody)?.groupValues?.get(1) ?: ""
                } else {
                    cellVal = vRegex.find(cellBody)?.groupValues?.get(1) ?: ""
                }
                cellList.add(unescapeXml(cellVal))
            }

            if (cellList.any { it.isNotBlank() }) {
                rows.add(cellList)
            }
        }

        if (rows.isEmpty()) {
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = listOf("A", "B", "C"),
                rows = listOf(listOf("Empty spreadsheet"))
            )
        }

        val firstRow = rows.first()
        val maxCols = rows.maxOf { it.size }
        val headers = firstRow.mapIndexed { idx, col ->
            if (col.isNotBlank()) col else "Column ${('A'.code + idx).toChar()}"
        }.toMutableList()

        while (headers.size < maxCols) {
            headers.add("Column ${('A'.code + headers.size).toChar()}")
        }

        val dataRows = if (rows.size > 1) rows.drop(1) else rows

        return ParsedDocumentContent.Spreadsheet(
            title = fileName,
            headers = headers,
            rows = dataRows
        )
    }

    private fun parseXls(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val textLines = scanTextFromBinary(bytes)

        if (textLines.isEmpty()) {
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = listOf("Column A", "Column B"),
                rows = listOf(listOf("Excel file opened", "No cell data detected"))
            )
        }

        val rows = textLines.chunked(4).map { chunk ->
            chunk.map { it.trim() }
        }

        val headers = listOf("A", "B", "C", "D").take(rows.firstOrNull()?.size ?: 2)

        return ParsedDocumentContent.Spreadsheet(
            title = fileName,
            headers = headers,
            rows = rows
        )
    }

    private fun parseCsv(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val text = String(bytes, StandardCharsets.UTF_8)
        val allLines = text.lines().filter { it.isNotBlank() }

        if (allLines.isEmpty()) {
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = listOf("Column 1"),
                rows = listOf(listOf("Empty CSV file"))
            )
        }

        val firstLine = allLines.first()
        val sep = when {
            firstLine.count { it == ',' } >= firstLine.count { it == ';' } &&
                    firstLine.count { it == ',' } >= firstLine.count { it == '\t' } -> ','
            firstLine.count { it == ';' } >= firstLine.count { it == '\t' } -> ';'
            else -> '\t'
        }

        val rows = allLines.map { line -> parseCsvLine(line, sep) }
        val headers = rows.first()
        val dataRows = if (rows.size > 1) rows.drop(1) else rows

        return ParsedDocumentContent.Spreadsheet(
            title = fileName,
            headers = headers,
            rows = dataRows
        )
    }

    private fun parseCsvLine(line: String, sep: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    // ==========================================
    // 4. PLAIN TEXT & FALLBACK PARSERS
    // ==========================================

    private fun parsePlainText(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val text = String(bytes, StandardCharsets.UTF_8)
        val lines = text.lines()

        return ParsedDocumentContent.PlainText(
            title = fileName,
            text = text.ifBlank { "This text document is empty." },
            linesCount = lines.size
        )
    }

    private fun parseFallback(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        return try {
            val text = String(bytes, StandardCharsets.UTF_8)
            if (text.isNotBlank() && text.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } < text.length * 0.1) {
                ParsedDocumentContent.PlainText(
                    title = fileName,
                    text = text,
                    linesCount = text.lines().size
                )
            } else {
                val scanned = scanTextFromBinary(bytes)
                if (scanned.isNotEmpty()) {
                    ParsedDocumentContent.PlainText(
                        title = fileName,
                        text = scanned.joinToString("\n\n"),
                        linesCount = scanned.size
                    )
                } else {
                    ParsedDocumentContent.PlainText(
                        title = fileName,
                        text = "Document opened ($fileName). Binary content could not be displayed as plain text.",
                        linesCount = 1
                    )
                }
            }
        } catch (_: Exception) {
            ParsedDocumentContent.PlainText(
                title = fileName,
                text = "Document opened: $fileName",
                linesCount = 1
            )
        }
    }

    // ==========================================
    // UTILITY HELPERS
    // ==========================================

    /**
     * Extracts readable strings from arbitrary binary streams (e.g. legacy .doc, .ppt, .xls).
     */
    fun scanTextFromBinary(bytes: ByteArray): List<String> {
        val results = mutableListOf<String>()

        // 1. Scan UTF-16LE strings (common in OLE2 Office files)
        val sbUtf16 = java.lang.StringBuilder()
        var idx = 0
        while (idx < bytes.size - 1) {
            val b1 = bytes[idx].toInt() and 0xFF
            val b2 = bytes[idx + 1].toInt() and 0xFF
            val codePoint = b1 or (b2 shl 8)

            if (codePoint in 32..126 || codePoint == 10 || codePoint == 13 || (codePoint in 160..0x04FF)) {
                sbUtf16.append(codePoint.toChar())
            } else {
                if (sbUtf16.length >= 4) {
                    val s = sbUtf16.toString().trim()
                    if (s.isNotBlank() && s.length >= 4 && s.any { it.isLetter() }) {
                        results.add(s)
                    }
                }
                sbUtf16.setLength(0)
            }
            idx += 2
        }
        if (sbUtf16.length >= 4) {
            val s = sbUtf16.toString().trim()
            if (s.isNotBlank() && s.length >= 4 && s.any { it.isLetter() }) {
                results.add(s)
            }
        }

        // 2. Scan ASCII strings
        val sbAscii = java.lang.StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126 || c == 10 || c == 13) {
                sbAscii.append(c.toChar())
            } else {
                if (sbAscii.length >= 4) {
                    val s = sbAscii.toString().trim()
                    if (s.isNotBlank() && s.length >= 4 && s.any { it.isLetter() }) {
                        results.add(s)
                    }
                }
                sbAscii.setLength(0)
            }
        }
        if (sbAscii.length >= 4) {
            val s = sbAscii.toString().trim()
            if (s.isNotBlank() && s.length >= 4 && s.any { it.isLetter() }) {
                results.add(s)
            }
        }

        return results.distinct().filter { it.length in 4..500 }.take(200)
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
