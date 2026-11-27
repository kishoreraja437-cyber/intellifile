package com.simats.intellifile.data.manager

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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

    data class SpreadsheetSheet(
        val name: String,
        val headers: List<String>,
        val rows: List<List<String>>
    )

    data class Spreadsheet(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val sheets: List<SpreadsheetSheet> = emptyList(),
        val activeSheetIndex: Int = 0
    ) : ParsedDocumentContent()

    data class RichDocument(
        val title: String,
        val totalPages: Int,
        val pages: List<DocumentPageData>,
        val fullText: String,
        val wordCount: Int = 0,
        val charCount: Int = 0,
        val allElements: List<DocumentElement> = emptyList(),
        val allImages: List<DocumentImage> = emptyList(),
        val allTables: List<DocumentTable> = emptyList()
    ) : ParsedDocumentContent()

    data class HtmlDocument(
        val title: String,
        val htmlContent: String,
        val rawSource: String
    ) : ParsedDocumentContent()

    data class MarkdownDocument(
        val title: String,
        val markdownText: String,
        val rawSource: String
    ) : ParsedDocumentContent()

    data class StructuredData(
        val title: String,
        val formattedContent: String,
        val rawContent: String,
        val dataType: String // "JSON" or "XML"
    ) : ParsedDocumentContent()

    data class LogLine(
        val lineNumber: Int,
        val timestamp: String,
        val level: String,
        val message: String,
        val rawLine: String
    )

    data class LogDocument(
        val title: String,
        val lines: List<LogLine>,
        val rawContent: String,
        val errorCount: Int = 0,
        val warnCount: Int = 0,
        val infoCount: Int = 0
    ) : ParsedDocumentContent()

    data class ArchiveEntry(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val compressedSizeBytes: Long,
        val isDirectory: Boolean,
        val lastModifiedMs: Long,
        val extension: String
    )

    data class ArchiveDocument(
        val title: String,
        val entries: List<ArchiveEntry>,
        val totalSizeBytes: Long,
        val totalCompressedBytes: Long,
        val zipBytes: ByteArray
    ) : ParsedDocumentContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ArchiveDocument
            if (title != other.title) return false
            if (entries != other.entries) return false
            if (totalSizeBytes != other.totalSizeBytes) return false
            if (totalCompressedBytes != other.totalCompressedBytes) return false
            return zipBytes.contentEquals(other.zipBytes)
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + entries.hashCode()
            result = 31 * result + totalSizeBytes.hashCode()
            result = 31 * result + totalCompressedBytes.hashCode()
            result = 31 * result + zipBytes.contentHashCode()
            return result
        }
    }

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

enum class DocumentElementType {
    TITLE,
    SUBTITLE,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    PARAGRAPH,
    LIST_ITEM,
    CALLOUT,
    QUOTE,
    TABLE,
    IMAGE,
    DIVIDER
}

data class DocumentTable(
    val rows: List<List<String>>,
    val hasHeader: Boolean = true
)

data class DocumentImage(
    val fileName: String,
    val bytes: ByteArray,
    val caption: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentImage
        if (fileName != other.fileName) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return caption == other.caption
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + caption.hashCode()
        return result
    }
}

data class DocumentElement(
    val type: DocumentElementType,
    val text: String = "",
    val bullet: String = "",
    val table: DocumentTable? = null,
    val image: DocumentImage? = null
)

data class SlideImage(
    val fileName: String,
    val bytes: ByteArray,
    val title: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SlideImage
        if (fileName != other.fileName) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return title == other.title
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + title.hashCode()
        return result
    }
}

data class SlideTable(
    val rows: List<List<String>>
)

data class SlideSection(
    val heading: String = "",
    val content: String = "",
    val items: List<String> = emptyList(),
    val isCallout: Boolean = false
)

data class SlideData(
    val slideNumber: Int,
    val title: String,
    val subtitle: String = "",
    val points: List<String> = emptyList(),
    val tables: List<SlideTable> = emptyList(),
    val images: List<SlideImage> = emptyList(),
    val sections: List<SlideSection> = emptyList(),
    val rawContent: String = "",
    val notes: String = "",
    val imageBytes: ByteArray? = images.firstOrNull()?.bytes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SlideData
        if (slideNumber != other.slideNumber) return false
        if (title != other.title) return false
        if (subtitle != other.subtitle) return false
        if (points != other.points) return false
        if (tables != other.tables) return false
        if (images != other.images) return false
        if (sections != other.sections) return false
        if (rawContent != other.rawContent) return false
        if (notes != other.notes) return false
        return true
    }

    override fun hashCode(): Int {
        var result = slideNumber
        result = 31 * result + title.hashCode()
        result = 31 * result + subtitle.hashCode()
        result = 31 * result + points.hashCode()
        result = 31 * result + tables.hashCode()
        result = 31 * result + images.hashCode()
        result = 31 * result + sections.hashCode()
        result = 31 * result + rawContent.hashCode()
        result = 31 * result + notes.hashCode()
        return result
    }
}

data class DocumentPageData(
    val pageNumber: Int,
    val paragraphs: List<String> = emptyList(),
    val elements: List<DocumentElement> = emptyList(),
    val tables: List<DocumentTable> = emptyList(),
    val images: List<DocumentImage> = emptyList()
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
                // Priority 1: PPT/PPTX presentations (Strictly preserved)
                zipHasPpt || isPptHint -> parsePresentation(bytes, fileName)

                // Priority 2: Word documents (Strictly preserved)
                zipHasWord || (isWordHint && rawExt != "rtf") -> {
                    if (isZip || rawExt == "docx") {
                        try {
                            val docx = parseDocx(bytes, fileName)
                            if (docx.pages.isNotEmpty() && docx.fullText.isNotBlank()) docx
                            else parseDoc(bytes, fileName)
                        } catch (_: Exception) {
                            parseDoc(bytes, fileName)
                        }
                    } else {
                        parseDoc(bytes, fileName)
                    }
                }

                // Priority 3: Excel spreadsheets (Enhanced multi-sheet)
                zipHasExcel || rawExt in listOf("xlsx", "xls", "xlsm", "xlsb", "ods") -> {
                    if (isZip || rawExt == "xlsx" || rawExt == "xlsm") {
                        try {
                            parseXlsx(bytes, fileName)
                        } catch (_: Exception) {
                            parseXls(bytes, fileName)
                        }
                    } else {
                        parseXls(bytes, fileName)
                    }
                }

                // Priority 4: CSV & TSV
                rawExt == "tsv" -> parseDelimited(bytes, fileName, '\t')
                rawExt == "csv" || isCsvHint -> parseDelimited(bytes, fileName, null)

                // Priority 5: HTML / HTM
                rawExt in listOf("html", "htm") -> parseHtml(bytes, fileName)

                // Priority 6: Markdown (MD)
                rawExt == "md" -> parseMarkdown(bytes, fileName)

                // Priority 7: JSON
                rawExt == "json" -> parseJson(bytes, fileName)

                // Priority 8: XML
                rawExt == "xml" -> parseXml(bytes, fileName)

                // Priority 9: LOG
                rawExt == "log" -> parseLog(bytes, fileName)

                // Priority 10: RTF
                rawExt == "rtf" -> parseRtf(bytes, fileName)

                // Priority 11: ZIP Archive
                rawExt == "zip" -> parseZip(bytes, fileName)

                // Priority 12: Plain text & code
                rawExt in listOf("txt", "yaml", "yml", "kt", "java", "py", "js", "ts", "jsx", "tsx", "css", "scss", "c", "cpp", "h", "cs", "go", "rs", "php", "sql", "sh", "bat", "ini", "conf", "properties") -> {
                    parsePlainText(bytes, fileName)
                }

                // Fallbacks
                isZip -> {
                    val pptx = parsePptx(bytes, fileName)
                    if (pptx != null && pptx.slides.isNotEmpty()) pptx
                    else {
                        try {
                            val docx = parseDocx(bytes, fileName)
                            if (docx.pages.isNotEmpty() && docx.fullText.isNotBlank()) docx
                            else parseZip(bytes, fileName)
                        } catch (_: Exception) {
                            try { parseXlsx(bytes, fileName) }
                            catch (_: Exception) { parseZip(bytes, fileName) }
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
            val isMedia = lowerName.contains("ppt/media/") || lowerName.contains("media/") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") ||
                    lowerName.endsWith(".svg")

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
                    mediaMap[lowerName] = entryBytes
                }
            }
            entry = zip.nextEntry
        }

        val orderedSlideEntries = mutableListOf<Pair<Int, String>>()
        if (slideXmlMap.isNotEmpty()) {
            val sortedKeys = slideXmlMap.keys.sorted()
            for (k in sortedKeys) {
                orderedSlideEntries.add(Pair(k, slideXmlMap[k]!!))
            }
        }
        for (un in unnumberedSlides) {
            orderedSlideEntries.add(Pair(-1, un))
        }

        if (orderedSlideEntries.isEmpty()) {
            return null
        }

        val slides = mutableListOf<SlideData>()
        for ((index, pair) in orderedSlideEntries.withIndex()) {
            val slideNumber = index + 1
            val origNum = pair.first
            val xml = pair.second
            val relsXml = if (origNum != -1) slideRelsMap[origNum] else slideRelsMap[slideNumber]

            // 1. Extract ALL slide relationships mapping rId -> media file name
            val relsTargetMap = mutableMapOf<String, String>()
            if (relsXml != null) {
                val relItemRegex = Regex("<Relationship\\s+([^>]+)/>", RegexOption.DOT_MATCHES_ALL)
                val idAttrRegex = Regex("Id=\"([^\"]+)\"")
                val targetAttrRegex = Regex("Target=\"([^\"]+)\"")
                val typeAttrRegex = Regex("Type=\"([^\"]+)\"")

                for (relItem in relItemRegex.findAll(relsXml)) {
                    val attrs = relItem.groupValues[1]
                    val rId = idAttrRegex.find(attrs)?.groupValues?.get(1)
                    val target = targetAttrRegex.find(attrs)?.groupValues?.get(1)
                    val type = typeAttrRegex.find(attrs)?.groupValues?.get(1)?.lowercase() ?: ""

                    if (rId != null && target != null) {
                        val fileName = target.substringAfterLast('/').lowercase()
                        val isImageOrDiagram = type.contains("image") ||
                                type.contains("diagram") ||
                                type.contains("chart") ||
                                target.contains("media/") ||
                                fileName.endsWith(".png") ||
                                fileName.endsWith(".jpg") ||
                                fileName.endsWith(".jpeg") ||
                                fileName.endsWith(".webp") ||
                                fileName.endsWith(".gif") ||
                                fileName.endsWith(".bmp") ||
                                fileName.endsWith(".svg")

                        if (isImageOrDiagram) {
                            relsTargetMap[rId] = fileName
                        }
                    }
                }
            }

            // 2. Extract diagram/picture titles & descriptions from slide XML
            val rIdToTitleMap = mutableMapOf<String, String>()
            val picRegex = Regex("<p:pic[ >](.*?)</p:pic>", RegexOption.DOT_MATCHES_ALL)
            val nameRegex = Regex("name=\"([^\"]+)\"")
            val descrRegex = Regex("descr=\"([^\"]+)\"")
            val blipRegex = Regex("<a:blip[^>]*r:embed=\"([^\"]+)\"")

            for (picMatch in picRegex.findAll(xml)) {
                val picXml = picMatch.value
                val rId = blipRegex.find(picXml)?.groupValues?.get(1)
                if (rId != null) {
                    val name = nameRegex.find(picXml)?.groupValues?.get(1)?.trim() ?: ""
                    val descr = descrRegex.find(picXml)?.groupValues?.get(1)?.trim() ?: ""
                    val title = when {
                        descr.isNotBlank() && name.isNotBlank() -> "$name - $descr"
                        descr.isNotBlank() -> descr
                        name.isNotBlank() && !name.startsWith("Picture ") -> name
                        else -> ""
                    }
                    if (title.isNotBlank()) rIdToTitleMap[rId] = title
                }
            }

            val gfRegex = Regex("<p:graphicFrame[ >](.*?)</p:graphicFrame>", RegexOption.DOT_MATCHES_ALL)
            for (gfMatch in gfRegex.findAll(xml)) {
                val gfXml = gfMatch.value
                val rId = blipRegex.find(gfXml)?.groupValues?.get(1)
                if (rId != null) {
                    val name = nameRegex.find(gfXml)?.groupValues?.get(1)?.trim() ?: ""
                    val descr = descrRegex.find(gfXml)?.groupValues?.get(1)?.trim() ?: ""
                    val title = when {
                        descr.isNotBlank() -> descr
                        name.isNotBlank() -> name
                        else -> "Diagram"
                    }
                    rIdToTitleMap[rId] = title
                }
            }

            // 3. Assemble ALL images and diagrams for this slide
            val slideImages = mutableListOf<SlideImage>()
            val addedFileNames = mutableSetOf<String>()

            for (blipMatch in blipRegex.findAll(xml)) {
                val rId = blipMatch.groupValues[1]
                val imgFileName = relsTargetMap[rId]
                if (imgFileName != null && mediaMap.containsKey(imgFileName) && !addedFileNames.contains(imgFileName)) {
                    addedFileNames.add(imgFileName)
                    val imgBytes = mediaMap[imgFileName]!!
                    val customTitle = rIdToTitleMap[rId] ?: ""
                    val isDiagram = imgFileName.contains("diagram") || imgFileName.contains("chart") || customTitle.contains("Diagram", ignoreCase = true)
                    val defaultTitle = if (isDiagram) "Diagram ${slideImages.size + 1}" else "Visual ${slideImages.size + 1}"
                    slideImages.add(
                        SlideImage(
                            fileName = imgFileName,
                            bytes = imgBytes,
                            title = if (customTitle.isNotBlank()) customTitle else defaultTitle
                        )
                    )
                }
            }

            for ((rId, imgFileName) in relsTargetMap) {
                if (mediaMap.containsKey(imgFileName) && !addedFileNames.contains(imgFileName)) {
                    addedFileNames.add(imgFileName)
                    val imgBytes = mediaMap[imgFileName]!!
                    val customTitle = rIdToTitleMap[rId] ?: ""
                    val isDiagram = imgFileName.contains("diagram") || imgFileName.contains("chart")
                    val defaultTitle = if (isDiagram) "Diagram ${slideImages.size + 1}" else "Image ${slideImages.size + 1}"
                    slideImages.add(
                        SlideImage(
                            fileName = imgFileName,
                            bytes = imgBytes,
                            title = if (customTitle.isNotBlank()) customTitle else defaultTitle
                        )
                    )
                }
            }

            if (slideImages.isEmpty() && orderedSlideEntries.size == 1 && mediaMap.isNotEmpty()) {
                for ((imgFileName, imgBytes) in mediaMap) {
                    val simple = imgFileName.substringAfterLast('/')
                    if (!addedFileNames.contains(simple)) {
                        addedFileNames.add(simple)
                        slideImages.add(
                            SlideImage(
                                fileName = simple,
                                bytes = imgBytes,
                                title = "Presentation Image ${slideImages.size + 1}"
                            )
                        )
                    }
                }
            }

            val slideData = parseSlideXml(xml, slideNumber, fileName, slideNotesMap[slideNumber], slideImages)
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
        slideImages: List<SlideImage> = emptyList()
    ): SlideData {
        // Broad namespace-tolerant regexes for OpenXML & DrawingML elements
        val pRegex = Regex("<(?:[a-zA-Z0-9]+:)?p(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?p>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<(?:[a-zA-Z0-9]+:)?t(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?t>", RegexOption.DOT_MATCHES_ALL)
        val brRegex = Regex("<(?:[a-zA-Z0-9]+:)?br(?:\\s[^>]*)?/?>")
        val titlePhRegex = Regex("<(?:[a-zA-Z0-9]+:)?ph[^>]*type=\"(?:ctrTitle|title)\"|<(?:[a-zA-Z0-9]+:)?ph[^>]*idx=\"0\"")
        val subtitlePhRegex = Regex("<(?:[a-zA-Z0-9]+:)?ph[^>]*type=\"subTitle\"")
        val spRegex = Regex("<(?:[a-zA-Z0-9]+:)?sp(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?sp>", RegexOption.DOT_MATCHES_ALL)

        val titleParagraphs = mutableListOf<String>()
        val subtitleParagraphs = mutableListOf<String>()
        val allParagraphs = mutableListOf<String>()
        val sections = mutableListOf<SlideSection>()

        // 1. Scan shapes for title, subtitle, and structured sections
        for (shapeMatch in spRegex.findAll(xml)) {
            val shapeXml = shapeMatch.value
            val isTitle = titlePhRegex.containsMatchIn(shapeXml)
            val isSubtitle = subtitlePhRegex.containsMatchIn(shapeXml)

            val shapeParas = mutableListOf<String>()
            for (pMatch in pRegex.findAll(shapeXml)) {
                val withBreaks = brRegex.replace(pMatch.value, "\n")
                val text = tRegex.findAll(withBreaks).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
                if (text.isNotBlank()) {
                    text.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                        shapeParas.add(it)
                    }
                }
            }

            if (isTitle) {
                titleParagraphs.addAll(shapeParas)
            } else if (isSubtitle) {
                subtitleParagraphs.addAll(shapeParas)
            } else if (shapeParas.isNotEmpty()) {
                val nameMatch = Regex("name=\"([^\"]+)\"").find(shapeXml)?.groupValues?.get(1) ?: ""
                val isCallout = nameMatch.contains("callout", ignoreCase = true) ||
                        nameMatch.contains("card", ignoreCase = true) ||
                        nameMatch.contains("quote", ignoreCase = true) ||
                        nameMatch.contains("summary", ignoreCase = true) ||
                        nameMatch.contains("highlight", ignoreCase = true)

                if (isCallout) {
                    val head = if (shapeParas.size > 1 && shapeParas.first().length <= 60) shapeParas.first() else ""
                    val bodyItems = if (head.isNotBlank()) shapeParas.drop(1) else shapeParas
                    sections.add(
                        SlideSection(
                            heading = head,
                            content = bodyItems.joinToString("\n"),
                            items = bodyItems,
                            isCallout = true
                        )
                    )
                }
            }
        }

        // 2. Scan ALL DrawingML paragraphs across the entire slide XML
        for (pMatch in pRegex.findAll(xml)) {
            val withBreaks = brRegex.replace(pMatch.value, "\n")
            val text = tRegex.findAll(withBreaks).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
            if (text.isNotBlank()) {
                text.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    allParagraphs.add(it)
                }
            }
        }

        // Fallback: If pRegex missed because of non-standard tags, scan direct <t> elements
        if (allParagraphs.isEmpty()) {
            for (tMatch in tRegex.findAll(xml)) {
                val text = unescapeXml(tMatch.groupValues[1]).trim()
                if (text.isNotBlank()) {
                    allParagraphs.add(text)
                }
            }
        }

        // 3. Scan for structured tables
        val trRegex = Regex("<(?:[a-zA-Z0-9]+:)?tr(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?tr>", RegexOption.DOT_MATCHES_ALL)
        val tcRegex = Regex("<(?:[a-zA-Z0-9]+:)?tc(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?tc>", RegexOption.DOT_MATCHES_ALL)
        val tblRegex = Regex("<(?:[a-zA-Z0-9]+:)?tbl(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?tbl>", RegexOption.DOT_MATCHES_ALL)
        val tables = mutableListOf<SlideTable>()
        for (tbl in tblRegex.findAll(xml)) {
            val tblRows = mutableListOf<List<String>>()
            for (tr in trRegex.findAll(tbl.value)) {
                val cellTexts = tcRegex.findAll(tr.value).map { tc ->
                    tRegex.findAll(tc.value).map { unescapeXml(it.groupValues[1]) }.joinToString(" ").trim()
                }.toList()
                if (cellTexts.any { it.isNotBlank() }) {
                    tblRows.add(cellTexts)
                }
            }
            if (tblRows.isNotEmpty()) {
                tables.add(SlideTable(rows = tblRows))
            }
        }

        // 4. Determine Title, Subtitle, and Content Points
        val title = when {
            titleParagraphs.isNotEmpty() -> titleParagraphs.first()
            allParagraphs.isNotEmpty() -> allParagraphs.first()
            else -> "Slide $slideNumber"
        }

        val subtitle = when {
            subtitleParagraphs.isNotEmpty() -> subtitleParagraphs.joinToString(" • ")
            titleParagraphs.size > 1 -> titleParagraphs.drop(1).first()
            else -> ""
        }

        val points = mutableListOf<String>()
        val skipItems = mutableSetOf(title)
        if (subtitle.isNotBlank()) skipItems.add(subtitle)
        sections.forEach { sec ->
            if (sec.heading.isNotBlank()) skipItems.add(sec.heading)
            skipItems.addAll(sec.items)
        }

        for (p in allParagraphs) {
            if (!skipItems.contains(p) && !points.contains(p)) {
                points.add(p)
            }
        }

        // If no points were extracted but allParagraphs has multiple items, ensure they are shown
        if (points.isEmpty() && sections.isEmpty() && allParagraphs.size > 1) {
            points.addAll(allParagraphs.drop(1))
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

        val rawFullContent = (listOf(title, subtitle) + points).filter { it.isNotBlank() }.distinct().joinToString("\n\n")

        return SlideData(
            slideNumber = slideNumber,
            title = title,
            subtitle = subtitle,
            points = points,
            tables = tables,
            images = slideImages,
            sections = sections,
            rawContent = rawFullContent,
            notes = extractedNotes
        )
    }

    fun parsePpt(bytes: ByteArray, fileName: String): ParsedDocumentContent.Presentation? {
        if (bytes.size < 32) return null

        val extractedImages = extractImagesFromPptBinary(bytes)
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

        val rawSlideSources = when {
            slideChunks.isNotEmpty() && slideChunks.any { it.isNotEmpty() } -> slideChunks.filter { it.isNotEmpty() }
            allScanned.isNotEmpty() -> allScanned.chunked(4)
            else -> emptyList()
        }

        if (rawSlideSources.isNotEmpty()) {
            val totalSlideCount = rawSlideSources.size
            rawSlideSources.forEachIndexed { idx, chunk ->
                val slideNum = idx + 1
                val title = chunk.first()
                val points = if (chunk.size > 1) chunk.drop(1) else listOf(chunk.first())
                val slideImagesForThis = if (extractedImages.isNotEmpty()) {
                    if (totalSlideCount == 1) {
                        extractedImages
                    } else {
                        val sliceSize = (extractedImages.size + totalSlideCount - 1) / totalSlideCount
                        val from = (idx * sliceSize).coerceAtMost(extractedImages.size)
                        val to = ((idx + 1) * sliceSize).coerceAtMost(extractedImages.size)
                        if (from < to) extractedImages.subList(from, to) else emptyList()
                    }
                } else emptyList()

                finalSlides.add(
                    SlideData(
                        slideNumber = slideNum,
                        title = if (title.length > 80) title.take(80) + "..." else title,
                        points = points,
                        images = slideImagesForThis,
                        rawContent = chunk.joinToString("\n\n")
                    )
                )
            }
        } else if (extractedImages.isNotEmpty()) {
            finalSlides.add(
                SlideData(
                    slideNumber = 1,
                    title = fileName.substringBeforeLast('.'),
                    points = listOf("Presentation Diagrams and Graphics"),
                    images = extractedImages,
                    rawContent = "Presentation Visuals"
                )
            )
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
        val extractedImages = extractImagesFromPptBinary(bytes)

        if (textScan.isNotEmpty()) {
            val chunks = textScan.chunked(4)
            val slides = chunks.mapIndexed { idx, chunk ->
                val slideImgs = if (idx == 0) extractedImages else emptyList()
                SlideData(
                    slideNumber = idx + 1,
                    title = if (idx == 0) defaultTitle else chunk.first(),
                    points = if (idx == 0) chunk else chunk.drop(1).ifEmpty { listOf(chunk.first()) },
                    images = slideImgs,
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
                    images = extractedImages,
                    rawContent = "Presentation loaded: $fileName"
                )
            )
        )
    }

    fun extractImagesFromPptBinary(bytes: ByteArray): List<SlideImage> {
        val images = mutableListOf<SlideImage>()
        if (bytes.size < 64) return images

        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val pngIend = byteArrayOf(0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())

        var offset = 0
        var imgIdx = 1
        while (offset < bytes.size - 16 && images.size < 40) {
            val pngStart = findBytes(bytes, pngMagic, offset)
            if (pngStart == -1) break

            val iendPos = findBytes(bytes, pngIend, pngStart + 8)
            if (iendPos != -1 && iendPos - pngStart in 64..12_000_000) {
                val pngEnd = iendPos + 8
                val imgBytes = bytes.copyOfRange(pngStart, pngEnd)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size, opts)
                if (opts.outWidth > 20 && opts.outHeight > 20) {
                    images.add(
                        SlideImage(
                            fileName = "diagram_$imgIdx.png",
                            bytes = imgBytes,
                            title = "Diagram $imgIdx (${opts.outWidth}×${opts.outHeight})"
                        )
                    )
                    imgIdx++
                }
                offset = pngEnd
            } else {
                offset = pngStart + 8
            }
        }

        offset = 0
        while (offset < bytes.size - 16 && images.size < 40) {
            val jpegStart = findJpegStart(bytes, offset)
            if (jpegStart == -1) break

            val jpegEnd = findJpegEnd(bytes, jpegStart + 3)
            if (jpegEnd != -1 && jpegEnd - jpegStart in 128..12_000_000) {
                val imgBytes = bytes.copyOfRange(jpegStart, jpegEnd)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size, opts)
                if (opts.outWidth > 20 && opts.outHeight > 20) {
                    images.add(
                        SlideImage(
                            fileName = "figure_$imgIdx.jpg",
                            bytes = imgBytes,
                            title = "Figure $imgIdx (${opts.outWidth}×${opts.outHeight})"
                        )
                    )
                    imgIdx++
                }
                offset = jpegEnd
            } else {
                offset = jpegStart + 3
            }
        }

        return images
    }

    private fun findBytes(source: ByteArray, target: ByteArray, start: Int): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        val max = source.size - target.size
        for (i in start..max) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun findJpegStart(source: ByteArray, start: Int): Int {
        val max = source.size - 3
        for (i in start..max) {
            if ((source[i].toInt() and 0xFF) == 0xFF &&
                (source[i + 1].toInt() and 0xFF) == 0xD8 &&
                (source[i + 2].toInt() and 0xFF) == 0xFF
            ) {
                return i
            }
        }
        return -1
    }

    private fun findJpegEnd(source: ByteArray, start: Int): Int {
        val max = source.size - 2
        for (i in start..max) {
            if ((source[i].toInt() and 0xFF) == 0xFF &&
                (source[i + 1].toInt() and 0xFF) == 0xD9
            ) {
                return i + 2
            }
        }
        return -1
    }

    // ==========================================
    // 2. DOCUMENT PARSERS (DOCX & DOC)
    // ==========================================

    fun parseDocx(bytes: ByteArray, fileName: String): ParsedDocumentContent.RichDocument {
        if (bytes.isEmpty()) {
            return ParsedDocumentContent.RichDocument(
                title = fileName,
                totalPages = 1,
                pages = listOf(DocumentPageData(1, listOf("This Word document is empty."))),
                fullText = ""
            )
        }

        var documentXmlBytes: ByteArray? = null
        val mediaMap = mutableMapOf<String, ByteArray>()
        val relsTargetMap = mutableMapOf<String, String>()

        try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace('\\', '/')
                val lowerName = entryName.lowercase()

                if (lowerName.endsWith("word/document.xml") || lowerName == "document.xml") {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var len: Int
                    while (zip.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                    documentXmlBytes = out.toByteArray()
                } else if (lowerName.contains("media/") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") ||
                    lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")
                ) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var len: Int
                    while (zip.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                    val simpleName = entryName.substringAfterLast('/')
                    val b = out.toByteArray()
                    mediaMap[simpleName] = b
                    mediaMap[entryName] = b
                } else if (lowerName.endsWith("document.xml.rels")) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var len: Int
                    while (zip.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                    val relsXml = out.toString("UTF-8")
                    val relRegex = Regex("""Id="([^"]+)"[^>]*Target="([^"]+)"""")
                    for (m in relRegex.findAll(relsXml)) {
                        val rId = m.groupValues[1]
                        val target = m.groupValues[2].replace('\\', '/').substringAfterLast('/')
                        relsTargetMap[rId] = target
                    }
                }
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val elements = mutableListOf<DocumentElement>()
        val hardPageBreakIndices = mutableSetOf<Int>()

        if (documentXmlBytes != null && documentXmlBytes.isNotEmpty()) {
            try {
                val factory = javax.xml.parsers.SAXParserFactory.newInstance()
                factory.isNamespaceAware = false
                val saxParser = factory.newSAXParser()

                var inParagraph = false
                var inTable = false
                var inCell = false
                val paragraphText = StringBuilder()
                var paragraphStyle = ""
                var isListItem = false
                val currentTableRows = mutableListOf<List<String>>()
                val currentRowCells = mutableListOf<String>()
                val currentCellText = StringBuilder()
                val paragraphImages = mutableListOf<DocumentImage>()

                saxParser.parse(ByteArrayInputStream(documentXmlBytes), object : org.xml.sax.helpers.DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes?) {
                        val rawName = qName ?: localName ?: ""
                        val tag = rawName.substringAfter(':').lowercase()
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                currentTableRows.clear()
                            }
                            "tr" -> {
                                currentRowCells.clear()
                            }
                            "tc" -> {
                                inCell = true
                                currentCellText.clear()
                            }
                            "p" -> {
                                inParagraph = true
                                paragraphText.clear()
                                paragraphStyle = ""
                                isListItem = false
                                paragraphImages.clear()
                            }
                            "pstyle" -> {
                                val v = attributes?.getValue("w:val")
                                    ?: attributes?.getValue("val") ?: ""
                                paragraphStyle = v
                            }
                            "numpr" -> {
                                isListItem = true
                            }
                            "tab" -> {
                                if (inCell) currentCellText.append("    ")
                                else if (inParagraph) paragraphText.append("    ")
                            }
                            "br" -> {
                                val bType = attributes?.getValue("w:type")
                                    ?: attributes?.getValue("type") ?: ""
                                if (bType.equals("page", ignoreCase = true)) {
                                    hardPageBreakIndices.add(elements.size)
                                } else {
                                    if (inCell) currentCellText.append("\n")
                                    else if (inParagraph) paragraphText.append("\n")
                                }
                            }
                            "lastrenderedpagebreak" -> {
                                hardPageBreakIndices.add(elements.size)
                            }
                            "blip" -> {
                                val embedId = attributes?.getValue("r:embed")
                                    ?: attributes?.getValue("embed") ?: ""
                                val target = relsTargetMap[embedId]
                                if (target != null && mediaMap.containsKey(target)) {
                                    val imgBytes = mediaMap[target]!!
                                    paragraphImages.add(
                                        DocumentImage(
                                            fileName = target,
                                            bytes = imgBytes,
                                            caption = "Figure ${elements.count { it.type == DocumentElementType.IMAGE } + 1}"
                                        )
                                    )
                                }
                            }
                        }
                    }

                    override fun characters(ch: CharArray?, start: Int, length: Int) {
                        if (ch != null && length > 0) {
                            val txt = String(ch, start, length)
                            if (inCell) {
                                currentCellText.append(txt)
                            } else if (inParagraph) {
                                paragraphText.append(txt)
                            }
                        }
                    }

                    override fun endElement(uri: String?, localName: String?, qName: String?) {
                        val rawName = qName ?: localName ?: ""
                        val tag = rawName.substringAfter(':').lowercase()
                        when (tag) {
                            "tc" -> {
                                inCell = false
                                currentRowCells.add(currentCellText.toString().trim())
                                currentCellText.clear()
                            }
                            "tr" -> {
                                if (currentRowCells.any { it.isNotBlank() }) {
                                    currentTableRows.add(currentRowCells.toList())
                                }
                                currentRowCells.clear()
                            }
                            "tbl" -> {
                                inTable = false
                                if (currentTableRows.isNotEmpty()) {
                                    val table = DocumentTable(
                                        rows = currentTableRows.toList(),
                                        hasHeader = currentTableRows.size > 1
                                    )
                                    elements.add(
                                        DocumentElement(
                                            type = DocumentElementType.TABLE,
                                            table = table
                                        )
                                    )
                                }
                            }
                            "p" -> {
                                inParagraph = false
                                if (inCell) {
                                    currentCellText.append(" ")
                                } else {
                                    for (img in paragraphImages) {
                                        elements.add(
                                            DocumentElement(
                                                type = DocumentElementType.IMAGE,
                                                image = img
                                            )
                                        )
                                    }

                                    val rawP = paragraphText.toString().trim()
                                    if (rawP.isNotBlank()) {
                                        val elType = resolveParagraphType(rawP, paragraphStyle, isListItem)
                                        elements.add(
                                            DocumentElement(
                                                type = elType,
                                                text = rawP,
                                                bullet = if (elType == DocumentElementType.LIST_ITEM) "•" else ""
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Secondary fallback: Tag-agnostic regex scanner if XML parser yielded no elements
        if (elements.isEmpty() && documentXmlBytes != null) {
            val xmlStr = String(documentXmlBytes, StandardCharsets.UTF_8)
            val tblRegex = Regex("<(?:[a-zA-Z0-9]+:)?tbl[ >](.*?)</(?:[a-zA-Z0-9]+:)?tbl>", RegexOption.DOT_MATCHES_ALL)
            val trRegex = Regex("<(?:[a-zA-Z0-9]+:)?tr[ >](.*?)</(?:[a-zA-Z0-9]+:)?tr>", RegexOption.DOT_MATCHES_ALL)
            val tcRegex = Regex("<(?:[a-zA-Z0-9]+:)?tc[ >](.*?)</(?:[a-zA-Z0-9]+:)?tc>", RegexOption.DOT_MATCHES_ALL)
            val tRegex = Regex("<(?:[a-zA-Z0-9]+:)?t[^>]*>(.*?)</(?:[a-zA-Z0-9]+:)?t>", RegexOption.DOT_MATCHES_ALL)
            val pRegex = Regex("<(?:[a-zA-Z0-9]+:)?p[ >](.*?)</(?:[a-zA-Z0-9]+:)?p>", RegexOption.DOT_MATCHES_ALL)

            for (tblMatch in tblRegex.findAll(xmlStr)) {
                val tableRows = mutableListOf<List<String>>()
                for (trMatch in trRegex.findAll(tblMatch.value)) {
                    val cells = mutableListOf<String>()
                    for (tcMatch in tcRegex.findAll(trMatch.value)) {
                        val cellText = tRegex.findAll(tcMatch.value).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
                        cells.add(cellText)
                    }
                    if (cells.any { it.isNotBlank() }) {
                        tableRows.add(cells)
                    }
                }
                if (tableRows.isNotEmpty()) {
                    elements.add(
                        DocumentElement(
                            type = DocumentElementType.TABLE,
                            table = DocumentTable(rows = tableRows, hasHeader = tableRows.size > 1)
                        )
                    )
                }
            }

            for (pMatch in pRegex.findAll(xmlStr)) {
                val pBody = pMatch.value
                val text = tRegex.findAll(pBody).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
                if (text.isNotBlank()) {
                    val elType = resolveParagraphType(text, "", false)
                    elements.add(DocumentElement(type = elType, text = text))
                }
            }

            if (elements.isEmpty()) {
                for (tMatch in tRegex.findAll(xmlStr)) {
                    val t = unescapeXml(tMatch.groupValues[1]).trim()
                    if (t.isNotBlank() && t.length >= 2) {
                        elements.add(DocumentElement(type = DocumentElementType.PARAGRAPH, text = t))
                    }
                }
            }
        }

        // If completely empty, try parseDoc
        if (elements.isEmpty()) {
            return parseDoc(bytes, fileName)
        }

        return buildRichDocument(fileName, elements, hardPageBreakIndices)
    }

    fun parseDoc(bytes: ByteArray, fileName: String): ParsedDocumentContent.RichDocument {
        if (bytes.isEmpty()) {
            return ParsedDocumentContent.RichDocument(
                title = fileName,
                totalPages = 1,
                pages = listOf(DocumentPageData(1, listOf("Word document is empty (0 bytes)."))),
                fullText = ""
            )
        }

        // 1. Check if RTF document
        val previewIso = try {
            String(bytes.take(64).toByteArray(), StandardCharsets.ISO_8859_1).trim()
        } catch (_: Exception) { "" }

        if (previewIso.startsWith("{\\rtf")) {
            return parseRtfDocument(bytes, fileName)
        }

        // 2. Check if HTML or MHTML saved as .doc
        val previewUtf = try {
            String(bytes.take(256).toByteArray(), StandardCharsets.UTF_8).lowercase().trim()
        } catch (_: Exception) { "" }

        if (previewUtf.startsWith("<!doctype html") || previewUtf.startsWith("<html") ||
            previewUtf.contains("<head") || previewUtf.contains("<body") || previewUtf.startsWith("mime-version:")
        ) {
            return parseHtmlDocument(bytes, fileName)
        }

        // 3. True Word 97-2003 OLE2 Binary document
        return parseOleWordDocument(bytes, fileName)
    }

    private fun resolveParagraphType(text: String, style: String, isList: Boolean): DocumentElementType {
        val s = style.lowercase()
        return when {
            s.contains("title") -> DocumentElementType.TITLE
            s.contains("subtitle") -> DocumentElementType.SUBTITLE
            s.contains("heading1") || s == "1" -> DocumentElementType.HEADING_1
            s.contains("heading2") || s == "2" -> DocumentElementType.HEADING_2
            s.contains("heading3") || s == "3" -> DocumentElementType.HEADING_3
            s.contains("quote") || s.contains("callout") -> DocumentElementType.QUOTE
            isList || s.contains("list") -> DocumentElementType.LIST_ITEM
            text.startsWith("•") || text.startsWith("- ") || text.startsWith("* ") ||
                    text.matches(Regex("^[0-9]+[.)]\\s+.*")) -> DocumentElementType.LIST_ITEM
            text.length in 3..60 && text.all { it.isUpperCase() || it.isWhitespace() || it in "—:-/&" } -> DocumentElementType.HEADING_1
            text.length in 3..70 && text.endsWith(":") && !text.contains(". ") -> DocumentElementType.HEADING_2
            else -> DocumentElementType.PARAGRAPH
        }
    }

    private fun buildRichDocument(
        fileName: String,
        elements: List<DocumentElement>,
        hardPageBreakIndices: Set<Int> = emptySet()
    ): ParsedDocumentContent.RichDocument {
        val pages = mutableListOf<DocumentPageData>()
        var currentPageElements = mutableListOf<DocumentElement>()
        var currentWordCount = 0

        elements.forEachIndexed { idx, el ->
            currentPageElements.add(el)
            val wordsInEl = when {
                el.table != null -> el.table.rows.sumOf { r -> r.sumOf { c -> c.split(Regex("\\s+")).count { it.isNotBlank() } } }
                else -> el.text.split(Regex("\\s+")).count { it.isNotBlank() }
            }
            currentWordCount += wordsInEl

            val shouldBreak = hardPageBreakIndices.contains(idx) ||
                    (currentWordCount >= 380 && currentPageElements.size >= 4) ||
                    currentPageElements.size >= 12

            if (shouldBreak) {
                val pageParas = currentPageElements.filter { it.text.isNotBlank() }.map { it.text }
                val pageTables = currentPageElements.mapNotNull { it.table }
                val pageImages = currentPageElements.mapNotNull { it.image }
                pages.add(
                    DocumentPageData(
                        pageNumber = pages.size + 1,
                        paragraphs = if (pageParas.isNotEmpty()) pageParas else listOf("Document content on this page."),
                        elements = currentPageElements.toList(),
                        tables = pageTables,
                        images = pageImages
                    )
                )
                currentPageElements = mutableListOf()
                currentWordCount = 0
            }
        }

        if (currentPageElements.isNotEmpty() || pages.isEmpty()) {
            val pageParas = currentPageElements.filter { it.text.isNotBlank() }.map { it.text }
            val pageTables = currentPageElements.mapNotNull { it.table }
            val pageImages = currentPageElements.mapNotNull { it.image }
            pages.add(
                DocumentPageData(
                    pageNumber = pages.size + 1,
                    paragraphs = if (pageParas.isNotEmpty()) pageParas else listOf("Document content."),
                    elements = currentPageElements.toList(),
                    tables = pageTables,
                    images = pageImages
                )
            )
        }

        val allImages = elements.mapNotNull { it.image }
        val allTables = elements.mapNotNull { it.table }
        val fullTextSb = StringBuilder()
        for (el in elements) {
            when {
                el.table != null -> {
                    fullTextSb.append(el.table.rows.joinToString("\n") { it.joinToString(" | ") }).append("\n\n")
                }
                el.text.isNotBlank() -> {
                    fullTextSb.append(el.text).append("\n\n")
                }
            }
        }
        val fullText = fullTextSb.toString().trim()
        val totalWords = fullText.split(Regex("\\s+")).count { it.isNotBlank() }

        return ParsedDocumentContent.RichDocument(
            title = fileName,
            totalPages = pages.size,
            pages = pages,
            fullText = fullText,
            wordCount = totalWords,
            charCount = fullText.length,
            allElements = elements,
            allImages = allImages,
            allTables = allTables
        )
    }

    private fun parseRtfDocument(bytes: ByteArray, fileName: String): ParsedDocumentContent.RichDocument {
        return try {
            val raw = String(bytes, StandardCharsets.ISO_8859_1)
            var cleaned = raw.replace(Regex("""\{\\\*?\\[a-zA-Z]+[^{}]*(?:\{[^{}]*\}[^{}]*)*\}"""), "")
            cleaned = cleaned.replace(Regex("""\\par\b|\\line\b"""), "\n")
            cleaned = cleaned.replace(Regex("""\\tab\b"""), "    ")
            cleaned = Regex("""\\'([0-9a-fA-F]{2})""").replace(cleaned) { m ->
                try {
                    val code = m.groupValues[1].toInt(16)
                    code.toChar().toString()
                } catch (_: Exception) { "" }
            }
            cleaned = Regex("""\\u([0-9]+)\??""").replace(cleaned) { m ->
                try {
                    val code = m.groupValues[1].toInt()
                    code.toChar().toString()
                } catch (_: Exception) { "" }
            }
            cleaned = cleaned.replace(Regex("""\\[a-zA-Z]+-?[0-9]* ?"""), "")
            cleaned = cleaned.replace("{", "").replace("}", "")

            val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
            val elements = mutableListOf<DocumentElement>()
            for (line in lines) {
                val elType = resolveParagraphType(line, "", false)
                elements.add(
                    DocumentElement(
                        type = elType,
                        text = line,
                        bullet = if (elType == DocumentElementType.LIST_ITEM) "•" else ""
                    )
                )
            }

            if (elements.isEmpty()) {
                elements.add(DocumentElement(type = DocumentElementType.PARAGRAPH, text = "RTF Document opened. No text content found."))
            }

            buildRichDocument(fileName, elements)
        } catch (_: Exception) {
            parseFallbackDoc(fileName, "RTF document loaded.")
        }
    }

    private fun parseHtmlDocument(bytes: ByteArray, fileName: String): ParsedDocumentContent.RichDocument {
        return try {
            val html = String(bytes, StandardCharsets.UTF_8)
            val elements = mutableListOf<DocumentElement>()

            fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").trim()

            val trRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val tdRegex = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val tblRegex = Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

            for (tbl in tblRegex.findAll(html)) {
                val rows = mutableListOf<List<String>>()
                for (tr in trRegex.findAll(tbl.value)) {
                    val cells = tdRegex.findAll(tr.value).map { stripTags(it.groupValues[1]) }.toList()
                    if (cells.any { it.isNotBlank() }) rows.add(cells)
                }
                if (rows.isNotEmpty()) {
                    elements.add(DocumentElement(type = DocumentElementType.TABLE, table = DocumentTable(rows, rows.size > 1)))
                }
            }

            val pRegex = Regex("<(?:p|h[1-6]|li)[^>]*>(.*?)</(?:p|h[1-6]|li)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (m in pRegex.findAll(html)) {
                val rawTag = m.value.substring(1, 3).lowercase()
                val text = stripTags(m.groupValues[1])
                if (text.isNotBlank()) {
                    val elType = when (rawTag) {
                        "h1" -> DocumentElementType.HEADING_1
                        "h2" -> DocumentElementType.HEADING_2
                        "h3" -> DocumentElementType.HEADING_3
                        "li" -> DocumentElementType.LIST_ITEM
                        else -> DocumentElementType.PARAGRAPH
                    }
                    elements.add(DocumentElement(type = elType, text = text, bullet = if (elType == DocumentElementType.LIST_ITEM) "•" else ""))
                }
            }

            if (elements.isEmpty()) {
                val stripped = stripTags(html)
                for (line in stripped.lines().map { it.trim() }.filter { it.length >= 3 }) {
                    elements.add(DocumentElement(type = DocumentElementType.PARAGRAPH, text = line))
                }
            }

            if (elements.isEmpty()) {
                elements.add(DocumentElement(type = DocumentElementType.PARAGRAPH, text = "HTML Document opened. No readable content found."))
            }

            buildRichDocument(fileName, elements)
        } catch (_: Exception) {
            parseFallbackDoc(fileName, "HTML document loaded.")
        }
    }

    private fun parseOleWordDocument(bytes: ByteArray, fileName: String): ParsedDocumentContent.RichDocument {
        val extractedImages = extractImagesFromPptBinary(bytes)
        val elements = mutableListOf<DocumentElement>()

        val ignoredStrings = setOf(
            "WordDocument", "SummaryInformation", "DocumentSummaryInformation", "CompObj",
            "Root Entry", "Data", "1Table", "0Table", "Normal.dot", "Normal.dotm",
            "Times New Roman", "Calibri", "Arial", "Cambria", "Symbol", "Wingdings",
            "Default Paragraph Font", "Heading 1", "Heading 2", "Heading 3", "Title",
            "Standard", "Microsoft Office Word", "Microsoft Word Document", "Table",
            "Current User", "PowerPoint Document"
        )

        val utf16TextBlocks = mutableListOf<String>()
        val sbUtf16 = java.lang.StringBuilder()
        var idx = 0
        while (idx < bytes.size - 1) {
            val b1 = bytes[idx].toInt() and 0xFF
            val b2 = bytes[idx + 1].toInt() and 0xFF
            val cp = b1 or (b2 shl 8)

            if (cp in 32..126 || cp == 10 || cp == 13 || cp in 160..0x04FF) {
                sbUtf16.append(cp.toChar())
            } else {
                if (sbUtf16.length >= 6) {
                    val s = sbUtf16.toString().trim()
                    if (s.isNotBlank() && s.length >= 6 && s.any { it.isLetter() } && s !in ignoredStrings) {
                        utf16TextBlocks.add(s)
                    }
                }
                sbUtf16.setLength(0)
            }
            idx += 2
        }
        if (sbUtf16.length >= 6) {
            val s = sbUtf16.toString().trim()
            if (s.isNotBlank() && s.length >= 6 && s.any { it.isLetter() } && s !in ignoredStrings) {
                utf16TextBlocks.add(s)
            }
        }

        val asciiTextBlocks = mutableListOf<String>()
        val sbAscii = java.lang.StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126 || c == 10 || c == 13 || c in 160..255) {
                sbAscii.append(c.toChar())
            } else {
                if (sbAscii.length >= 6) {
                    val s = sbAscii.toString().trim()
                    if (s.isNotBlank() && s.length >= 6 && s.any { it.isLetter() } && s !in ignoredStrings) {
                        asciiTextBlocks.add(s)
                    }
                }
                sbAscii.setLength(0)
            }
        }
        if (sbAscii.length >= 6) {
            val s = sbAscii.toString().trim()
            if (s.isNotBlank() && s.length >= 6 && s.any { it.isLetter() } && s !in ignoredStrings) {
                asciiTextBlocks.add(s)
            }
        }

        val chosenText = when {
            utf16TextBlocks.sumOf { it.length } > asciiTextBlocks.sumOf { it.length } * 1.5 -> utf16TextBlocks
            else -> asciiTextBlocks.ifEmpty { utf16TextBlocks }
        }

        for (chunk in chosenText) {
            val lines = chunk.split("\r\n", "\r", "\n").map { it.trim() }.filter { it.isNotBlank() }
            for (line in lines) {
                if (line.length in 3..2000 && line !in ignoredStrings && line.count { it.isLetter() } >= 2) {
                    val elType = resolveParagraphType(line, "", false)
                    elements.add(
                        DocumentElement(
                            type = elType,
                            text = line,
                            bullet = if (elType == DocumentElementType.LIST_ITEM) "•" else ""
                        )
                    )
                }
            }
        }

        for ((imgIdx, img) in extractedImages.withIndex()) {
            val docImg = DocumentImage(
                fileName = img.fileName,
                bytes = img.bytes,
                caption = "Document Image ${imgIdx + 1}"
            )
            elements.add(DocumentElement(type = DocumentElementType.IMAGE, image = docImg))
        }

        if (elements.isEmpty()) {
            return parseFallbackDoc(fileName, "Word Document loaded: $fileName")
        }

        return buildRichDocument(fileName, elements)
    }

    private fun parseFallbackDoc(fileName: String, message: String): ParsedDocumentContent.RichDocument {
        val p = listOf(message)
        return ParsedDocumentContent.RichDocument(
            title = fileName,
            totalPages = 1,
            pages = listOf(DocumentPageData(1, p)),
            fullText = message,
            wordCount = message.split(Regex("\\s+")).count { it.isNotBlank() },
            charCount = message.length,
            allElements = listOf(DocumentElement(type = DocumentElementType.PARAGRAPH, text = message))
        )
    }

    // ==========================================
    // 3. SPREADSHEET PARSERS (XLSX, XLS, CSV, TSV)
    // ==========================================

    private fun columnLetterToIndex(colStr: String): Int {
        var index = 0
        for (ch in colStr.uppercase()) {
            if (ch in 'A'..'Z') {
                index = index * 26 + (ch - 'A' + 1)
            }
        }
        return (index - 1).coerceAtLeast(0)
    }

    private fun parseXlsx(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zip.nextEntry
        val sharedStrings = mutableListOf<String>()

        val byteEntries = mutableMapOf<String, ByteArray>()
        while (entry != null) {
            val name = entry.name.replace('\\', '/')
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var len: Int
            while (zip.read(buf).also { len = it } > 0) {
                out.write(buf, 0, len)
            }
            byteEntries[name.lowercase()] = out.toByteArray()
            entry = zip.nextEntry
        }

        // 1. Parse Shared Strings
        val sstEntryKey = byteEntries.keys.firstOrNull { it.contains("sharedstrings.xml") }
        if (sstEntryKey != null) {
            val sstXml = String(byteEntries[sstEntryKey]!!, StandardCharsets.UTF_8)
            val tRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
            for (m in tRegex.findAll(sstXml)) {
                sharedStrings.add(unescapeXml(m.groupValues[1]))
            }
        }

        // 2. Discover Worksheets (from workbook.xml if present, else by path pattern)
        val discoveredSheets = mutableListOf<Pair<String, String>>() // (SheetName, EntryKey)
        val workbookKey = byteEntries.keys.firstOrNull { it.contains("workbook.xml") && !it.contains(".rels") }
        if (workbookKey != null) {
            val wbXml = String(byteEntries[workbookKey]!!, StandardCharsets.UTF_8)
            val sheetRegex = Regex("<sheet[^>]*name=\"([^\"]+)\"[^>]*(?:sheetId=\"([^\"]+)\")?[^>]*(?:r:id=\"([^\"]+)\")?", RegexOption.IGNORE_CASE)
            var idx = 1
            for (m in sheetRegex.findAll(wbXml)) {
                val sName = m.groupValues[1]
                val sId = m.groupValues[2].ifBlank { idx.toString() }
                // Look for sheet matching sId
                val candidateKey = byteEntries.keys.firstOrNull {
                    it.contains("worksheets/sheet$sId.xml") || it.contains("worksheets/sheet$idx.xml")
                }
                if (candidateKey != null) {
                    discoveredSheets.add(Pair(sName, candidateKey))
                }
                idx++
            }
        }

        // Fallback discovery if workbook.xml wasn't parsed or yielded empty
        if (discoveredSheets.isEmpty()) {
            val sheetKeys = byteEntries.keys.filter { it.contains("worksheets/sheet") && it.endsWith(".xml") }.sorted()
            sheetKeys.forEachIndexed { i, key ->
                val num = Regex("""sheet(\d+)\.xml""").find(key)?.groupValues?.get(1) ?: (i + 1).toString()
                discoveredSheets.add(Pair("Sheet $num", key))
            }
        }

        if (discoveredSheets.isEmpty()) {
            return ParsedDocumentContent.Error("Unable to locate worksheets in Excel workbook.")
        }

        val allSheets = mutableListOf<ParsedDocumentContent.SpreadsheetSheet>()
        val rowRegex = Regex("<row[^>]*r=\"(\\d+)\"[^>]*>(.*?)</row>|<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c[^>]*r=\"([A-Z]+)(\\d+)\"[^>]*(?:t=\"([^\"]*)\")?[^>]*>(.*?)</c>|<c[^>]*(?:t=\"([^\"]*)\")?[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        val vRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
        val inlineStrRegex = Regex("<is>.*?<t[^>]*>(.*?)</t>.*?</is>", RegexOption.DOT_MATCHES_ALL)

        for ((sheetName, sheetKey) in discoveredSheets) {
            val sheetXmlBytes = byteEntries[sheetKey] ?: continue
            val sheetXml = String(sheetXmlBytes, StandardCharsets.UTF_8)

            val parsedRowList = mutableListOf<List<String>>()
            var maxRowCols = 0

            for (rowMatch in rowRegex.findAll(sheetXml)) {
                val rowContent = rowMatch.value
                val cellMap = mutableMapOf<Int, String>()
                var sequentialCol = 0

                for (cellMatch in cellRegex.findAll(rowContent)) {
                    val colLetters = cellMatch.groupValues[1]
                    val cellType = cellMatch.groupValues[3].ifBlank { cellMatch.groupValues[5] }
                    val cellBody = cellMatch.groupValues[4].ifBlank { cellMatch.groupValues[6] }

                    var cellVal = ""
                    if (cellType == "s") {
                        val sIdx = vRegex.find(cellBody)?.groupValues?.get(1)?.toIntOrNull()
                        if (sIdx != null && sIdx in 0 until sharedStrings.size) {
                            cellVal = sharedStrings[sIdx]
                        }
                    } else if (cellType == "inlineStr") {
                        cellVal = inlineStrRegex.find(cellBody)?.groupValues?.get(1) ?: ""
                    } else {
                        cellVal = vRegex.find(cellBody)?.groupValues?.get(1) ?: ""
                    }
                    val cleanVal = unescapeXml(cellVal).trim()

                    val targetCol = if (colLetters.isNotBlank()) {
                        columnLetterToIndex(colLetters)
                    } else {
                        sequentialCol
                    }
                    cellMap[targetCol] = cleanVal
                    sequentialCol = targetCol + 1
                }

                if (cellMap.isNotEmpty()) {
                    val highestCol = cellMap.keys.maxOrNull() ?: 0
                    if (highestCol + 1 > maxRowCols) maxRowCols = highestCol + 1
                    val rowItems = ArrayList<String>(highestCol + 1)
                    for (c in 0..highestCol) {
                        rowItems.add(cellMap[c] ?: "")
                    }
                    if (rowItems.any { it.isNotBlank() }) {
                        parsedRowList.add(rowItems)
                    }
                }
            }

            if (parsedRowList.isEmpty()) {
                allSheets.add(
                    ParsedDocumentContent.SpreadsheetSheet(
                        name = sheetName,
                        headers = listOf("A", "B", "C"),
                        rows = listOf(listOf("Empty sheet"))
                    )
                )
                continue
            }

            val sheetMaxCols = maxRowCols.coerceAtLeast(1)
            val firstRow = parsedRowList.first()
            val headers = firstRow.mapIndexed { idx, col ->
                if (col.isNotBlank()) col else "Column ${('A'.code + (idx % 26)).toChar()}"
            }.toMutableList()

            while (headers.size < sheetMaxCols) {
                headers.add("Column ${('A'.code + (headers.size % 26)).toChar()}")
            }

            val dataRows = if (parsedRowList.size > 1) {
                parsedRowList.drop(1).map { r ->
                    val padded = r.toMutableList()
                    while (padded.size < sheetMaxCols) padded.add("")
                    padded.toList()
                }
            } else {
                parsedRowList
            }

            allSheets.add(
                ParsedDocumentContent.SpreadsheetSheet(
                    name = sheetName,
                    headers = headers,
                    rows = dataRows
                )
            )
        }

        if (allSheets.isEmpty()) {
            return ParsedDocumentContent.Error("No readable worksheet data found in Excel workbook.")
        }

        val primarySheet = allSheets.first()
        return ParsedDocumentContent.Spreadsheet(
            title = fileName,
            headers = primarySheet.headers,
            rows = primarySheet.rows,
            sheets = allSheets,
            activeSheetIndex = 0
        )
    }

    private fun parseXls(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val textLines = scanTextFromBinary(bytes)

        if (textLines.isEmpty()) {
            val emptySheet = ParsedDocumentContent.SpreadsheetSheet(
                name = "Sheet 1",
                headers = listOf("Column A", "Column B"),
                rows = listOf(listOf("Excel file opened", "No cell data detected"))
            )
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = emptySheet.headers,
                rows = emptySheet.rows,
                sheets = listOf(emptySheet)
            )
        }

        val rows = textLines.chunked(4).map { chunk ->
            chunk.map { it.trim() }
        }
        val maxCols = rows.maxOfOrNull { it.size } ?: 2
        val headers = listOf("A", "B", "C", "D").take(maxCols)

        val sheet = ParsedDocumentContent.SpreadsheetSheet(
            name = "Sheet 1",
            headers = headers,
            rows = rows
        )

        return ParsedDocumentContent.Spreadsheet(
            title = fileName,
            headers = headers,
            rows = rows,
            sheets = listOf(sheet)
        )
    }

    private fun parseDelimited(bytes: ByteArray, fileName: String, delimiter: Char? = null): ParsedDocumentContent {
        val text = String(bytes, StandardCharsets.UTF_8)
        if (text.isBlank()) {
            val emptySheet = ParsedDocumentContent.SpreadsheetSheet(
                name = "Data",
                headers = listOf("Column A"),
                rows = listOf(listOf("Empty file"))
            )
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = emptySheet.headers,
                rows = emptySheet.rows,
                sheets = listOf(emptySheet)
            )
        }

        val sep = delimiter ?: run {
            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
            when {
                firstLine.count { it == '\t' } > firstLine.count { it == ',' } &&
                        firstLine.count { it == '\t' } > firstLine.count { it == ';' } -> '\t'
                firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
                else -> ','
            }
        }

        val allRows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < len && text[i + 1] == '\"') {
                        currentField.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == sep && !inQuotes -> {
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && text[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        allRows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                allRows.add(currentRow.toList())
            }
        }

        if (allRows.isEmpty()) {
            val emptySheet = ParsedDocumentContent.SpreadsheetSheet(
                name = "Data",
                headers = listOf("Column A"),
                rows = listOf(listOf("Empty data"))
            )
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = emptySheet.headers,
                rows = emptySheet.rows,
                sheets = listOf(emptySheet)
            )
        }

        val maxCols = allRows.maxOf { it.size }
        val firstRow = allRows.first()
        val headers = firstRow.mapIndexed { idx, col ->
            if (col.isNotBlank()) col else "Column ${('A'.code + (idx % 26)).toChar()}"
        }.toMutableList()

        while (headers.size < maxCols) {
            headers.add("Column ${('A'.code + (headers.size % 26)).toChar()}")
        }

        val dataRows = if (allRows.size > 1) {
            allRows.drop(1).map { row ->
                val padded = row.toMutableList()
                while (padded.size < maxCols) padded.add("")
                padded.toList()
            }
        } else {
            allRows
        }

        val sheet = ParsedDocumentContent.SpreadsheetSheet(
            name = if (sep == '\t') "TSV Data" else "CSV Data",
            headers = headers,
            rows = dataRows
        )

        return ParsedDocumentContent.Spreadsheet(
            title = fileName,
            headers = headers,
            rows = dataRows,
            sheets = listOf(sheet)
        )
    }

    // ==========================================
    // 4. RICH WEB & MARKDOWN PARSERS
    // ==========================================

    private fun parseHtml(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val rawText = String(bytes, StandardCharsets.UTF_8)
        var html = rawText.trim()
        val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0\">"
        val modernStyle = """
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    line-height: 1.6;
                    color: #0f172a;
                    background-color: #ffffff;
                    padding: 16px;
                    margin: 0;
                    word-wrap: break-word;
                }
                img { max-width: 100%; height: auto; border-radius: 6px; }
                table { border-collapse: collapse; width: 100%; margin: 12px 0; }
                th, td { border: 1px solid #cbd5e1; padding: 8px 12px; text-align: left; }
                th { background-color: #f8fafc; font-weight: 600; }
                pre, code { font-family: monospace; background-color: #f1f5f9; border-radius: 4px; padding: 2px 6px; font-size: 90%; }
                pre { padding: 12px; overflow-x: auto; }
                blockquote { border-left: 4px solid #55A9AF; margin: 12px 0; padding-left: 14px; color: #475569; }
                a { color: #0284c7; text-decoration: none; }
            </style>
        """.trimIndent()

        if (!html.contains("<meta", ignoreCase = true) || !html.contains("viewport", ignoreCase = true)) {
            if (html.contains("<head>", ignoreCase = true)) {
                html = html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$viewportMeta\n$modernStyle\n")
            } else if (html.contains("<html>", ignoreCase = true)) {
                html = html.replaceFirst(Regex("<html>", RegexOption.IGNORE_CASE), "<html>\n<head>\n$viewportMeta\n$modernStyle\n</head>\n")
            } else {
                html = "<!DOCTYPE html>\n<html>\n<head>\n$viewportMeta\n$modernStyle\n</head>\n<body>\n$html\n</body>\n</html>"
            }
        }

        return ParsedDocumentContent.HtmlDocument(
            title = fileName,
            htmlContent = html,
            rawSource = rawText
        )
    }

    private fun parseMarkdown(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val rawText = String(bytes, StandardCharsets.UTF_8)
        return ParsedDocumentContent.MarkdownDocument(
            title = fileName,
            markdownText = rawText,
            rawSource = rawText
        )
    }

    // ==========================================
    // 5. STRUCTURED DATA PARSERS (JSON & XML)
    // ==========================================

    private fun parseJson(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val rawText = String(bytes, StandardCharsets.UTF_8)
        val formatted = try {
            val trimmed = rawText.trim()
            if (trimmed.startsWith("{")) {
                org.json.JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed).toString(2)
            } else {
                rawText
            }
        } catch (_: Exception) {
            rawText
        }

        return ParsedDocumentContent.StructuredData(
            title = fileName,
            formattedContent = formatted,
            rawContent = rawText,
            dataType = "JSON"
        )
    }

    private fun parseXml(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val rawText = String(bytes, StandardCharsets.UTF_8)
        val formatted = try {
            formatXmlString(rawText)
        } catch (_: Exception) {
            rawText
        }

        return ParsedDocumentContent.StructuredData(
            title = fileName,
            formattedContent = formatted,
            rawContent = rawText,
            dataType = "XML"
        )
    }

    private fun formatXmlString(xml: String): String {
        val sb = java.lang.StringBuilder()
        var indent = 0
        val tokens = xml.replace("><", ">\n<").lines()

        for (rawToken in tokens) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue

            if (token.startsWith("</")) {
                indent = (indent - 1).coerceAtLeast(0)
            }

            for (k in 0 until indent) sb.append("  ")
            sb.append(token).append("\n")

            if (token.startsWith("<") && !token.startsWith("</") && !token.startsWith("<?") && !token.startsWith("<!") && !token.endsWith("/>") && !token.contains("</")) {
                indent++
            }
        }
        return sb.toString().trimEnd()
    }

    // ==========================================
    // 6. LOG & RTF PARSERS
    // ==========================================

    private fun parseLog(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val rawText = String(bytes, StandardCharsets.UTF_8)
        val allLines = rawText.lines()
        val logLines = mutableListOf<ParsedDocumentContent.LogLine>()
        var errorCount = 0
        var warnCount = 0
        var infoCount = 0

        val timeRegex = Regex("""(\d{4}[-/]\d{2}[-/]\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d+)?|\[\d{2}:\d{2}:\d{2}(?:\.\d+)?\])""")
        val levelRegex = Regex("""\b(ERROR|FATAL|CRITICAL|WARN|WARNING|INFO|DEBUG|TRACE)\b""", RegexOption.IGNORE_CASE)

        allLines.forEachIndexed { idx, line ->
            val timeMatch = timeRegex.find(line)?.value ?: ""
            val levelMatch = levelRegex.find(line)?.value?.uppercase() ?: "INFO"

            when {
                levelMatch in listOf("ERROR", "FATAL", "CRITICAL") -> errorCount++
                levelMatch in listOf("WARN", "WARNING") -> warnCount++
                levelMatch == "INFO" -> infoCount++
            }

            var msg = line
            if (timeMatch.isNotBlank()) msg = msg.replace(timeMatch, "").trim()
            if (levelMatch.isNotBlank()) msg = msg.replaceFirst(Regex("""\b$levelMatch\b""", RegexOption.IGNORE_CASE), "").trim()

            logLines.add(
                ParsedDocumentContent.LogLine(
                    lineNumber = idx + 1,
                    timestamp = timeMatch,
                    level = levelMatch,
                    message = msg.ifBlank { line },
                    rawLine = line
                )
            )
        }

        return ParsedDocumentContent.LogDocument(
            title = fileName,
            lines = logLines,
            rawContent = rawText,
            errorCount = errorCount,
            warnCount = warnCount,
            infoCount = infoCount
        )
    }

    private fun parseRtf(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val text = String(bytes, StandardCharsets.ISO_8859_1)
        val sb = java.lang.StringBuilder()
        var i = 0
        val len = text.length
        var inGroup = 0
        var skipGroup = false

        while (i < len) {
            val c = text[i]
            when (c) {
                '{' -> {
                    inGroup++
                    val nextWord = text.substring(i + 1, (i + 15).coerceAtMost(len))
                    if (nextWord.startsWith("\\fonttbl") || nextWord.startsWith("\\colortbl") ||
                        nextWord.startsWith("\\stylesheet") || nextWord.startsWith("\\*\\") ||
                        nextWord.startsWith("\\info")) {
                        skipGroup = true
                    }
                    i++
                }
                '}' -> {
                    inGroup--
                    if (inGroup <= 1) skipGroup = false
                    i++
                }
                '\\' -> {
                    i++
                    if (i < len) {
                        val ctrlStart = i
                        while (i < len && text[i].isLetter()) {
                            i++
                        }
                        val ctrlWord = text.substring(ctrlStart, i)
                        while (i < len && (text[i].isDigit() || text[i] == '-')) {
                            i++
                        }
                        if (i < len && text[i] == ' ') i++

                        if (!skipGroup) {
                            when (ctrlWord) {
                                "par", "line" -> sb.append('\n')
                                "tab" -> sb.append("    ")
                                "emdash" -> sb.append("—")
                                "endash" -> sb.append("–")
                                "bullet" -> sb.append("• ")
                                "'" -> {
                                    if (i + 1 < len) {
                                        val hex = text.substring(i, i + 2)
                                        val charCode = hex.toIntOrNull(16)
                                        if (charCode != null) sb.append(charCode.toChar())
                                        i += 2
                                    }
                                }
                            }
                        }
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (!skipGroup && inGroup >= 0) {
                        sb.append(c)
                    }
                    i++
                }
            }
        }

        val clean = sb.toString().trim()
        val result = if (clean.isNotBlank()) clean else scanTextFromBinary(bytes).joinToString("\n\n")

        return ParsedDocumentContent.PlainText(
            title = fileName,
            text = result.ifBlank { "Rich Text document opened ($fileName)." },
            linesCount = result.lines().size
        )
    }

    // ==========================================
    // 7. ZIP ARCHIVE PARSER
    // ==========================================

    private fun parseZip(bytes: ByteArray, fileName: String): ParsedDocumentContent {
        val entries = mutableListOf<ParsedDocumentContent.ArchiveEntry>()
        var totalSize = 0L
        var totalCompressed = 0L

        try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val isDir = entry.isDirectory || name.endsWith("/")
                val size = if (entry.size >= 0) entry.size else 0L
                val compSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L
                val time = entry.time
                val ext = if (!isDir) name.substringAfterLast('.', "").uppercase() else "DIR"

                totalSize += size
                totalCompressed += compSize

                entries.add(
                    ParsedDocumentContent.ArchiveEntry(
                        path = name,
                        name = name.trimEnd('/').substringAfterLast('/'),
                        sizeBytes = size,
                        compressedSizeBytes = compSize,
                        isDirectory = isDir,
                        lastModifiedMs = if (time > 0) time else System.currentTimeMillis(),
                        extension = ext
                    )
                )
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (entries.isEmpty()) {
            return ParsedDocumentContent.PlainText(
                title = fileName,
                text = "Zip Archive opened ($fileName). The archive contains no files or could not be read.",
                linesCount = 1
            )
        }

        val sortedEntries = entries.sortedWith(
            compareBy<ParsedDocumentContent.ArchiveEntry> { !it.isDirectory }
                .thenBy { it.path.lowercase() }
        )

        return ParsedDocumentContent.ArchiveDocument(
            title = fileName,
            entries = sortedEntries,
            totalSizeBytes = totalSize,
            totalCompressedBytes = totalCompressed,
            zipBytes = bytes
        )
    }

    fun extractZipEntry(zipBytes: ByteArray, targetEntryPath: String, destFile: File): Boolean {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(zipBytes))
            var entry = zis.nextEntry
            var found = false
            while (entry != null) {
                if (entry.name == targetEntryPath) {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                    found = true
                    zis.closeEntry()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            found && destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==========================================
    // 8. PLAIN TEXT & FALLBACK PARSERS
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
