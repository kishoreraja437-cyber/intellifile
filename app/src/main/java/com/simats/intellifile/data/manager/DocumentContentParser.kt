package com.simats.intellifile.data.manager

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
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
    val rawContent: String = ""
)

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
        val ext = fileName.substringAfterLast('.', "").lowercase().ifBlank {
            format.lowercase().substringBefore(" ")
        }

        if (ext == "pdf") {
            return ParsedDocumentContent.Pdf(totalPages = 1)
        }

        val inputStream = openStream(context, uriString, path)
            ?: return ParsedDocumentContent.Error("Unable to open file stream. Please ensure permission is granted.")

        return try {
            when (ext) {
                "pptx" -> parsePptx(inputStream, fileName)
                "ppt" -> parsePpt(inputStream, fileName)
                "docx" -> parseDocx(inputStream, fileName)
                "doc" -> parseDoc(inputStream, fileName)
                "xlsx" -> parseXlsx(inputStream, fileName)
                "xls" -> parseXls(inputStream, fileName)
                "csv" -> parseCsv(inputStream, fileName)
                "txt", "json", "md", "xml", "html", "htm", "log", "rtf" -> parsePlainText(inputStream, fileName)
                else -> {
                    // Try parsing as text or DOCX/PPTX zip
                    parseFallback(inputStream, fileName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedDocumentContent.Error("Error parsing document: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    private fun openStream(context: Context, uriString: String, path: String): InputStream? {
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) return stream
            } catch (_: Exception) {}
        }

        if (path.isNotBlank()) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    return file.inputStream()
                }
            } catch (_: Exception) {}
        }

        return null
    }

    // ==========================================
    // 1. PRESENTATION PARSERS (PPTX & PPT)
    // ==========================================

    private fun parsePptx(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val slideXmlMap = mutableMapOf<Int, String>()
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val slidePattern = Pattern.compile("ppt/slides/slide([0-9]+)\\.xml")

        while (entry != null) {
            val matcher = slidePattern.matcher(entry.name)
            if (matcher.matches()) {
                val slideNum = matcher.group(1)?.toIntOrNull() ?: (slideXmlMap.size + 1)
                val xmlContent = zip.bufferedReader(StandardCharsets.UTF_8).readText()
                slideXmlMap[slideNum] = xmlContent
            }
            entry = zip.nextEntry
        }

        if (slideXmlMap.isEmpty()) {
            return ParsedDocumentContent.Error("No slides found in PowerPoint presentation.")
        }

        val sortedSlideNumbers = slideXmlMap.keys.sorted()
        val slides = mutableListOf<SlideData>()
        val pRegex = Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<a:t[^>]*>(.*?)</a:t>")

        sortedSlideNumbers.forEachIndexed { index, slideNum ->
            val xml = slideXmlMap[slideNum] ?: ""
            val paragraphs = pRegex.findAll(xml).map { pMatch ->
                tRegex.findAll(pMatch.value).map { unescapeXml(it.groupValues[1]) }.joinToString("").trim()
            }.filter { it.isNotBlank() }.toList()

            val title = if (paragraphs.isNotEmpty()) {
                paragraphs.first()
            } else {
                "Slide ${index + 1}"
            }

            val points = if (paragraphs.size > 1) {
                paragraphs.drop(1)
            } else {
                emptyList()
            }

            slides.add(
                SlideData(
                    slideNumber = index + 1,
                    title = title,
                    points = points,
                    rawContent = paragraphs.joinToString("\n")
                )
            )
        }

        return ParsedDocumentContent.Presentation(
            totalSlides = slides.size,
            slides = slides
        )
    }

    private fun parsePpt(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        // Binary PPT extractor: scans record headers & text chunks
        val bytes = inputStream.readBytes()
        val extractedStrings = mutableListOf<String>()

        var i = 0
        while (i < bytes.size - 8) {
            val recType = (bytes[i + 2].toInt() and 0xFF) or ((bytes[i + 3].toInt() and 0xFF) shl 8)
            val recLen = (bytes[i + 4].toInt() and 0xFF) or
                    ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 7].toInt() and 0xFF) shl 24)

            if (recLen in 1..200000 && i + 8 + recLen <= bytes.size) {
                if (recType == 4000 || recType == 4026) {
                    // UTF-16LE text
                    try {
                        val text = String(bytes, i + 8, recLen, StandardCharsets.UTF_16LE).trim()
                        val clean = text.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?-()[]{}'\"/\\@#%&*+=<>" }
                        if (clean.isNotBlank() && clean.length > 2) {
                            extractedStrings.add(clean)
                        }
                    } catch (_: Exception) {}
                } else if (recType == 4008) {
                    // ANSI text
                    try {
                        val text = String(bytes, i + 8, recLen, StandardCharsets.ISO_8859_1).trim()
                        val clean = text.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?-()[]{}'\"/\\@#%&*+=<>" }
                        if (clean.isNotBlank() && clean.length > 2) {
                            extractedStrings.add(clean)
                        }
                    } catch (_: Exception) {}
                }
                i += 8 + recLen
            } else {
                i++
            }
        }

        // Secondary fallback if binary PPT record parsing found few strings
        if (extractedStrings.isEmpty()) {
            val textScan = scanTextFromBinary(bytes)
            extractedStrings.addAll(textScan)
        }

        if (extractedStrings.isEmpty()) {
            return ParsedDocumentContent.Presentation(
                totalSlides = 1,
                slides = listOf(
                    SlideData(
                        slideNumber = 1,
                        title = fileName.substringBeforeLast('.'),
                        points = listOf("PowerPoint presentation contents loaded. No textual notes detected.")
                    )
                )
            )
        }

        // Group into slides (every 3 to 6 text points = 1 slide)
        val slides = mutableListOf<SlideData>()
        val chunkSize = 4
        val chunks = extractedStrings.chunked(chunkSize)

        chunks.forEachIndexed { index, chunk ->
            val slideTitle = chunk.firstOrNull() ?: "Slide ${index + 1}"
            val slidePoints = if (chunk.size > 1) chunk.drop(1) else listOf(slideTitle)
            slides.add(
                SlideData(
                    slideNumber = index + 1,
                    title = if (slideTitle.length > 60) slideTitle.take(60) + "..." else slideTitle,
                    points = slidePoints,
                    rawContent = chunk.joinToString("\n")
                )
            )
        }

        return ParsedDocumentContent.Presentation(
            totalSlides = slides.size,
            slides = slides
        )
    }

    // ==========================================
    // 2. DOCUMENT PARSERS (DOCX & DOC)
    // ==========================================

    private fun parseDocx(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        var documentXml: String? = null

        while (entry != null) {
            if (entry.name == "word/document.xml") {
                documentXml = zip.bufferedReader(StandardCharsets.UTF_8).readText()
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

        // Group paragraphs into pages
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

    private fun parseDoc(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val bytes = inputStream.readBytes()
        val textLines = scanTextFromBinary(bytes)

        if (textLines.isEmpty()) {
            return ParsedDocumentContent.RichDocument(
                title = fileName,
                totalPages = 1,
                pages = listOf(DocumentPageData(1, listOf("Word document loaded. No textual content detected."))),
                fullText = ""
            )
        }

        // Group into pages of 5-8 paragraphs
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

    private fun parseXlsx(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val sharedStrings = mutableListOf<String>()
        var sheetXml: String? = null

        // Pass 1: Read sharedStrings.xml and sheet1.xml
        val byteEntries = mutableMapOf<String, ByteArray>()
        while (entry != null) {
            val name = entry.name
            if (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet")) {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var len: Int
                while (zip.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                byteEntries[name] = out.toByteArray()
            }
            entry = zip.nextEntry
        }

        // Parse sharedStrings
        val sstBytes = byteEntries["xl/sharedStrings.xml"]
        if (sstBytes != null) {
            val sstXml = String(sstBytes, StandardCharsets.UTF_8)
            val tRegex = Regex("<t[^>]*>(.*?)</t>")
            for (m in tRegex.findAll(sstXml)) {
                sharedStrings.add(unescapeXml(m.groupValues[1]))
            }
        }

        // Parse first available sheet
        val firstSheetKey = byteEntries.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") }
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

        // First row is headers
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

    private fun parseXls(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val bytes = inputStream.readBytes()
        val textLines = scanTextFromBinary(bytes)

        if (textLines.isEmpty()) {
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = listOf("Column A", "Column B"),
                rows = listOf(listOf("Excel file opened", "No cell data detected"))
            )
        }

        // Create spreadsheet rows from extracted cell strings
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

    private fun parseCsv(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val reader = inputStream.bufferedReader(StandardCharsets.UTF_8)
        val allLines = reader.readLines().filter { it.isNotBlank() }

        if (allLines.isEmpty()) {
            return ParsedDocumentContent.Spreadsheet(
                title = fileName,
                headers = listOf("Column 1"),
                rows = listOf(listOf("Empty CSV file"))
            )
        }

        // Determine separator: comma, semicolon, tab
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
        val sb = StringBuilder()
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

    private fun parsePlainText(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        val text = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val lines = text.lines()

        return ParsedDocumentContent.PlainText(
            title = fileName,
            text = text.ifBlank { "This text document is empty." },
            linesCount = lines.size
        )
    }

    private fun parseFallback(inputStream: InputStream, fileName: String): ParsedDocumentContent {
        return try {
            val text = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (text.isNotBlank() && text.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } < text.length * 0.1) {
                ParsedDocumentContent.PlainText(
                    title = fileName,
                    text = text,
                    linesCount = text.lines().size
                )
            } else {
                ParsedDocumentContent.Error("File format could not be decoded as text or document.")
            }
        } catch (_: Exception) {
            ParsedDocumentContent.Error("Unsupported document format.")
        }
    }

    // ==========================================
    // UTILITY HELPERS
    // ==========================================

    /**
     * Extracts readable strings from arbitrary binary streams (e.g. legacy .doc, .ppt, .xls).
     */
    private fun scanTextFromBinary(bytes: ByteArray): List<String> {
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

        // 2. Scan ASCII strings
        val sbAscii = java.lang.StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126 || c == 10 || c == 13) {
                sbAscii.append(c.toChar())
            } else {
                if (sbAscii.length >= 5) {
                    val s = sbAscii.toString().trim()
                    if (s.isNotBlank() && s.length >= 5 && s.any { it.isLetter() }) {
                        results.add(s)
                    }
                }
                sbAscii.setLength(0)
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

