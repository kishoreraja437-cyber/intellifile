package com.simats.intellifile.ui.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared reusable Document Information Header Component across all file viewers in IntelliFile
 * (PDF, PPT/PPTX, and DOC/DOCX).
 *
 * Responsively renders the file name, format badge, page/slide indicator, and word count
 * without text clipping, squeezing, or vertical character wrapping.
 */
@Composable
fun DocumentInfoSection(
    fileName: String,
    fileFormat: String = "",
    currentPage: Int = 1,
    totalPages: Int = 1,
    wordCount: Int? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cleanFormat = remember(fileFormat, fileName) {
        val ext = fileName.substringAfterLast('.', "").uppercase().trim()
        if (ext.isNotBlank()) ext else fileFormat.uppercase().substringBefore(" ").trim()
    }

    val isPresentation = remember(cleanFormat, fileName) {
        cleanFormat in listOf("PPT", "PPTX", "PPS", "PPSX", "POT", "POTX") ||
                fileName.endsWith(".pptx", ignoreCase = true) ||
                fileName.endsWith(".ppt", ignoreCase = true)
    }

    val isWord = remember(cleanFormat, fileName) {
        cleanFormat in listOf("DOC", "DOCX", "ODT", "RTF", "DOT", "DOTX") ||
                fileName.endsWith(".docx", ignoreCase = true) ||
                fileName.endsWith(".doc", ignoreCase = true)
    }

    val isPdf = remember(cleanFormat, fileName) {
        cleanFormat == "PDF" || fileName.endsWith(".pdf", ignoreCase = true)
    }

    val isSpreadsheet = remember(cleanFormat) {
        cleanFormat in listOf("XLS", "XLSX", "CSV", "TSV")
    }

    val isArchive = remember(cleanFormat) {
        cleanFormat == "ZIP"
    }

    val isLog = remember(cleanFormat) {
        cleanFormat == "LOG"
    }

    val unitLabel = when {
        isPresentation -> "Slide"
        isSpreadsheet -> "Row"
        isArchive -> "File"
        isLog -> "Line"
        else -> "Page"
    }
    val safeTotal = totalPages.coerceAtLeast(1)
    val safeCurrent = currentPage.coerceIn(1, safeTotal)
    val unitText = "$unitLabel $safeCurrent of $safeTotal"

    val formatBadgeText = when {
        isPdf -> "PDF"
        isPresentation -> if (cleanFormat.contains("X") || fileName.endsWith(".pptx", ignoreCase = true)) "PPTX" else "PPT"
        isWord -> if (cleanFormat.contains("X") || fileName.endsWith(".docx", ignoreCase = true)) "DOCX" else "DOC"
        isSpreadsheet -> cleanFormat
        isArchive -> "ZIP"
        else -> cleanFormat.ifBlank { "DOC" }
    }

    val formatBadgeTextColor = when {
        isPdf -> Color(0xFFDC2626) // PDF Red
        isPresentation -> Color(0xFFD97706) // Presentation Orange/Amber
        isWord -> Color(0xFF2563EB) // Word Blue
        isSpreadsheet -> Color(0xFF059669) // Spreadsheet Green
        isArchive -> Color(0xFF4F46E5) // Archive Indigo
        cleanFormat in listOf("HTML", "HTM") -> Color(0xFFEA580C)
        cleanFormat == "JSON" -> Color(0xFF0284C7)
        cleanFormat == "XML" -> Color(0xFF7C3AED)
        else -> Color(0xFF55A9AF) // Dusky Aqua
    }

    val formatBadgeBgColor = when {
        isPdf -> Color(0xFFFEE2E2)
        isPresentation -> Color(0xFFFEF3C7)
        isWord -> Color(0xFFDBEAFE)
        isSpreadsheet -> Color(0xFFECFDF5)
        isArchive -> Color(0xFFEEF2FF)
        cleanFormat in listOf("HTML", "HTM") -> Color(0xFFFFF7ED)
        cleanFormat == "JSON" -> Color(0xFFE0F2FE)
        cleanFormat == "XML" -> Color(0xFFEDE9FE)
        else -> Color(0xFFEFF8F8)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        // File Name: clean bold font with sensible ellipsis truncation if too long
        Text(
            text = fileName,
            fontSize = if (isCompact) 14.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Metadata Row: Format Badge + Page/Slide count + Word count (where applicable)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Format Badge (pill)
            Surface(
                color = formatBadgeBgColor,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = formatBadgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = formatBadgeTextColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                )
            }

            // Page or Slide Indicator
            Text(
                text = unitText,
                fontSize = if (isCompact) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                maxLines = 1
            )

            // Word Count (only for PDF and DOC/DOCX when available)
            if (!isPresentation && wordCount != null && wordCount > 0) {
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "${String.format("%,d", wordCount)} words",
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }
        }
    }
}
