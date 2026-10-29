package com.simats.intellifile.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.DocumentContentParser
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.manager.ParsedDocumentContent
import com.simats.intellifile.data.manager.SlideData
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.dialog.SaveConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentViewerScreen(
    fileName: String = "Document.pdf",
    filePath: String = "",
    fileUriString: String = "",
    fileFormat: String = "PDF",
    totalPages: Int = 1,
    showSaveConfirmation: Boolean = false,
    onBackClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onThumbnailClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSetPriorityClick: () -> Unit = {},
    onConfirmSave: () -> Unit = {},
    onDismissSaveConfirmation: () -> Unit = {}
) {
    val context = LocalContext.current
    val key = if (filePath.isNotBlank()) filePath else fileUriString

    val effectiveFormat = remember(fileName, fileFormat) {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        if (ext.isNotBlank()) ext else fileFormat.uppercase().substringBefore(" ")
    }

    val isPdf = remember(effectiveFormat) { effectiveFormat == "PDF" }

    // Navigation and display states
    var currentPageIndex by remember { mutableIntStateOf(1) }
    var totalUnitsCount by remember { mutableIntStateOf(totalPages.coerceAtLeast(1)) }
    var zoomPercentage by remember { mutableIntStateOf(100) }
    var isBookmarked by remember {
        mutableStateOf(
            if (key.isNotBlank()) LocalFileDatabaseHelper.getInstance(context).isBookmarked(key) else false
        )
    }
    var isSaved by remember { mutableStateOf(false) }

    // Search bar state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Jump dialog state
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

    // PDF state
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Parsed Content state for Office and Text documents
    var parsedContent by remember { mutableStateOf<ParsedDocumentContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load document content based on format
    LaunchedEffect(filePath, fileUriString, fileName, effectiveFormat) {
        isLoading = true
        errorMessage = null

        if (isPdf) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = LocalFileManager.getFileDescriptor(context, fileUriString, filePath)
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        parcelFileDescriptor = pfd
                        pdfRenderer = renderer
                        totalUnitsCount = renderer.pageCount.coerceAtLeast(1)
                        currentPageIndex = 1
                    } else {
                        errorMessage = "Cannot access PDF file. Please ensure storage permission is enabled."
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Unable to render PDF: ${e.localizedMessage ?: "File may be encrypted or corrupted."}"
                } finally {
                    isLoading = false
                }
            }
        } else {
            // Parse non-PDF documents: PPTX, PPT, DOCX, DOC, XLSX, XLS, CSV, TXT, JSON, MD
            withContext(Dispatchers.IO) {
                try {
                    val result = DocumentContentParser.parse(
                        context = context,
                        uriString = fileUriString,
                        path = filePath,
                        format = effectiveFormat,
                        fileName = fileName
                    )
                    parsedContent = result
                    when (result) {
                        is ParsedDocumentContent.Presentation -> {
                            totalUnitsCount = result.totalSlides.coerceAtLeast(1)
                            currentPageIndex = 1
                        }
                        is ParsedDocumentContent.RichDocument -> {
                            totalUnitsCount = result.totalPages.coerceAtLeast(1)
                            currentPageIndex = 1
                        }
                        is ParsedDocumentContent.Spreadsheet -> {
                            totalUnitsCount = result.rows.size.coerceAtLeast(1)
                            currentPageIndex = 1
                        }
                        is ParsedDocumentContent.PlainText -> {
                            totalUnitsCount = 1
                            currentPageIndex = 1
                        }
                        is ParsedDocumentContent.Error -> {
                            errorMessage = result.message
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Error opening document: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Render PDF page when currentPageIndex or zoomPercentage changes
    LaunchedEffect(currentPageIndex, zoomPercentage, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (totalUnitsCount <= 0) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val pageIndex = (currentPageIndex - 1).coerceIn(0, totalUnitsCount - 1)
                val page = renderer.openPage(pageIndex)
                val density = context.resources.displayMetrics.density
                val scale = (zoomPercentage / 100f) * density
                val targetWidth = (page.width * scale).toInt().coerceIn(200, 3200)
                val targetHeight = (page.height * scale).toInt().coerceIn(200, 4800)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pageBitmap = bitmap.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // PDF Resource cleanup
    DisposableEffect(Unit) {
        onDispose {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { parcelFileDescriptor?.close() } catch (_: Exception) {}
        }
    }

    if (showSaveConfirmation) {
        SaveConfirmationDialog(
            fileName = fileName,
            onDismissRequest = onDismissSaveConfirmation,
            onConfirmSave = {
                isSaved = true
                onConfirmSave()
            },
            onCancel = onDismissSaveConfirmation
        )
    }

    // Jump to Unit Dialog (Slide, Page, Row)
    if (showJumpDialog) {
        val unitLabel = when {
            isPdf || parsedContent is ParsedDocumentContent.RichDocument -> "Page"
            parsedContent is ParsedDocumentContent.Presentation -> "Slide"
            else -> "Row"
        }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to $unitLabel", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter $unitLabel number (1 to $totalUnitsCount):", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = jumpInput,
                        onValueChange = { jumpInput = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("$unitLabel #") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = jumpInput.toIntOrNull()
                        if (target != null && target in 1..totalUnitsCount) {
                            currentPageIndex = target
                            showJumpDialog = false
                            jumpInput = ""
                        } else {
                            Toast.makeText(context, "Please enter a valid $unitLabel (1 - $totalUnitsCount)", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search inside document...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column {
                            Text(
                                text = fileName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            val subtitle = when {
                                isPdf -> "PDF Document · Page $currentPageIndex of $totalUnitsCount"
                                parsedContent is ParsedDocumentContent.Presentation -> "PowerPoint Presentation · Slide $currentPageIndex of $totalUnitsCount"
                                parsedContent is ParsedDocumentContent.Spreadsheet -> "Excel Spreadsheet · ${(parsedContent as ParsedDocumentContent.Spreadsheet).rows.size} Rows"
                                parsedContent is ParsedDocumentContent.RichDocument -> "Word Document · Page $currentPageIndex of $totalUnitsCount"
                                else -> "$effectiveFormat Document"
                            }
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF111827)
                            )
                        }

                        IconButton(onClick = {
                            isBookmarked = !isBookmarked
                            if (key.isNotBlank()) {
                                LocalFileDatabaseHelper.getInstance(context).setBookmarked(key, fileName, isBookmarked)
                                Toast.makeText(
                                    context,
                                    if (isBookmarked) "Added to Bookmarks" else "Removed from Bookmarks",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color(0xFF6C5CE7) else Color(0xFF111827)
                            )
                        }

                        IconButton(onClick = {
                            onMoreClick()
                            onSetPriorityClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color(0xFF111827)
                            )
                        }
                    }
                }
            }

            // Main Viewport Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Opening $effectiveFormat in IntelliFile...",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unable to View in IntelliFile",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage ?: "Unknown error occurred.",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    val item = LocalFileItem(
                                        title = fileName,
                                        path = filePath,
                                        uri = if (fileUriString.isNotBlank()) Uri.parse(fileUriString) else Uri.EMPTY,
                                        mimeType = LocalFileManager.resolveMimeType(fileName)
                                    )
                                    LocalFileManager.openFileWithSystem(context, item)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open with External App")
                            }
                        }
                    }

                    isPdf -> {
                        if (pageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = pageBitmap!!,
                                    contentDescription = "Page $currentPageIndex of $totalUnitsCount",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                )
                            }
                        } else {
                            Text("Rendering page...", fontSize = 14.sp, color = Color(0xFF6B7280))
                        }
                    }

                    // PPT / PPTX Presentation View
                    parsedContent is ParsedDocumentContent.Presentation -> {
                        val presentation = parsedContent as ParsedDocumentContent.Presentation
                        val slideIndex = (currentPageIndex - 1).coerceIn(0, presentation.slides.size - 1)
                        val slide = presentation.slides.getOrNull(slideIndex) ?: SlideData(1, "Slide", emptyList())

                        PresentationSlideView(
                            slide = slide,
                            currentSlide = currentPageIndex,
                            totalSlides = presentation.totalSlides,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onPrevSlide = { if (currentPageIndex > 1) currentPageIndex-- },
                            onNextSlide = { if (currentPageIndex < totalUnitsCount) currentPageIndex++ }
                        )
                    }

                    // XLSX / XLS / CSV Spreadsheet View
                    parsedContent is ParsedDocumentContent.Spreadsheet -> {
                        val spreadsheet = parsedContent as ParsedDocumentContent.Spreadsheet
                        SpreadsheetTableView(
                            spreadsheet = spreadsheet,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery
                        )
                    }

                    // DOCX / DOC / RTF Document Reader View
                    parsedContent is ParsedDocumentContent.RichDocument -> {
                        val doc = parsedContent as ParsedDocumentContent.RichDocument
                        val pageIdx = (currentPageIndex - 1).coerceIn(0, doc.pages.size - 1)
                        val page = doc.pages.getOrNull(pageIdx)

                        WordDocumentPageView(
                            page = page,
                            currentPage = currentPageIndex,
                            totalPages = doc.totalPages,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onPrevPage = { if (currentPageIndex > 1) currentPageIndex-- },
                            onNextPage = { if (currentPageIndex < totalUnitsCount) currentPageIndex++ }
                        )
                    }

                    // Plain Text View (TXT, JSON, MD, LOG)
                    parsedContent is ParsedDocumentContent.PlainText -> {
                        val textDoc = parsedContent as ParsedDocumentContent.PlainText
                        PlainTextViewer(
                            textDoc = textDoc,
                            fileFormat = effectiveFormat,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unified Bottom Indicator & Zoom Bar
            val unitName = when {
                parsedContent is ParsedDocumentContent.Presentation -> "Slide"
                parsedContent is ParsedDocumentContent.Spreadsheet -> "Row"
                else -> "Page"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Unit Indicator Pill with Prev / Next arrows
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFF1F5F9), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalUnitsCount > 1) {
                        IconButton(
                            onClick = { if (currentPageIndex > 1) currentPageIndex-- },
                            enabled = currentPageIndex > 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous $unitName",
                                tint = if (currentPageIndex > 1) Color(0xFF374151) else Color(0xFFD1D5DB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = if (totalUnitsCount > 1) "$unitName $currentPageIndex / $totalUnitsCount" else "$unitName 1 / 1",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151),
                        modifier = Modifier
                            .clickable {
                                if (totalUnitsCount > 1) {
                                    showJumpDialog = true
                                }
                            }
                            .padding(horizontal = 8.dp)
                    )

                    if (totalUnitsCount > 1) {
                        IconButton(
                            onClick = { if (currentPageIndex < totalUnitsCount) currentPageIndex++ },
                            enabled = currentPageIndex < totalUnitsCount,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next $unitName",
                                tint = if (currentPageIndex < totalUnitsCount) Color(0xFF374151) else Color(0xFFD1D5DB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Zoom Control Pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFF1F5F9), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (zoomPercentage > 60) zoomPercentage -= 15 },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$zoomPercentage%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { if (zoomPercentage < 220) zoomPercentage += 15 },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Action Bar
            DocumentBottomActionBar(
                isSaved = isSaved,
                onSaveClick = {
                    isSaved = true
                    if (key.isNotBlank()) {
                        LocalFileDatabaseHelper.getInstance(context).setBookmarked(key, fileName, true)
                        Toast.makeText(context, "Saved to device collection", Toast.LENGTH_SHORT).show()
                    }
                    onConfirmSave()
                    onSetPriorityClick()
                },
                onThumbnailClick = {
                    if (totalUnitsCount > 1) {
                        showJumpDialog = true
                    } else {
                        onThumbnailClick()
                    }
                },
                onShareClick = onShareClick,
                onMoreClick = {
                    onMoreClick()
                    onSetPriorityClick()
                }
            )
        }
    }
}

// ==========================================
// 1. PRESENTATION SLIDE COMPOSABLE (PPT/PPTX)
// ==========================================
@Composable
private fun PresentationSlideView(
    slide: SlideData,
    currentSlide: Int,
    totalSlides: Int,
    zoomPercentage: Int,
    searchQuery: String,
    onPrevSlide: () -> Unit,
    onNextSlide: () -> Unit
) {
    val scaleFactor = zoomPercentage / 100f
    val titleSize = (20 * scaleFactor).sp
    val bodySize = (14 * scaleFactor).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slide Card Container (16:9 Presentation look)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Slide Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Slideshow,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SLIDE $currentSlide OF $totalSlides",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }

                    Text(
                        text = "IntelliFile Presentation View",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Slide Title
                Text(
                    text = slide.title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    lineHeight = (titleSize.value * 1.3f).sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // Bullet Points
                if (slide.points.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        slide.points.forEach { point ->
                            val isHighlighted = searchQuery.isNotBlank() && point.contains(searchQuery, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isHighlighted) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = (bodySize.value * 0.35f).dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = point,
                                    fontSize = bodySize,
                                    color = Color(0xFF334155),
                                    lineHeight = (bodySize.value * 1.5f).sp
                                )
                            }
                        }
                    }
                } else if (slide.rawContent.isNotBlank()) {
                    Text(
                        text = slide.rawContent,
                        fontSize = bodySize,
                        color = Color(0xFF334155),
                        lineHeight = (bodySize.value * 1.5f).sp
                    )
                } else {
                    Text(
                        text = "No content on this slide.",
                        fontSize = bodySize,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. SPREADSHEET TABLE COMPOSABLE (XLS/XLSX/CSV)
// ==========================================
@Composable
private fun SpreadsheetTableView(
    spreadsheet: ParsedDocumentContent.Spreadsheet,
    zoomPercentage: Int,
    searchQuery: String
) {
    val scaleFactor = zoomPercentage / 100f
    val textSize = (13 * scaleFactor).sp
    val cellPaddingH = (14 * scaleFactor).dp
    val cellPaddingV = (10 * scaleFactor).dp

    val filteredRows = remember(spreadsheet.rows, searchQuery) {
        if (searchQuery.isBlank()) {
            spreadsheet.rows
        } else {
            spreadsheet.rows.filter { row ->
                row.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        // Table Info Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${spreadsheet.headers.size} Columns · ${filteredRows.size} Rows",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1)
                )
            }

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Showing matches for \"$searchQuery\"",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        // Scrollable Matrix Grid (Both H & V)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                // Header Row
                item {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, Color(0xFFCBD5E1))
                    ) {
                        // Row Number Column Header
                        Text(
                            text = "#",
                            fontSize = textSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            modifier = Modifier
                                .width(44.dp)
                                .padding(horizontal = 6.dp, vertical = cellPaddingV),
                            textAlign = TextAlign.Center
                        )

                        spreadsheet.headers.forEach { header ->
                            Text(
                                text = header,
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .width(150.dp)
                                    .border(0.5.dp, Color(0xFFE2E8F0))
                                    .padding(horizontal = cellPaddingH, vertical = cellPaddingV),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Data Rows
                itemsIndexed(filteredRows) { index, row ->
                    val isAlt = index % 2 == 1
                    Row(
                        modifier = Modifier
                            .background(if (isAlt) Color(0xFFF8FAFC) else Color.White)
                            .border(0.5.dp, Color(0xFFF1F5F9))
                    ) {
                        // Row Number
                        Text(
                            text = "${index + 1}",
                            fontSize = (textSize.value * 0.9f).sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .width(44.dp)
                                .background(Color(0xFFF8FAFC))
                                .border(0.5.dp, Color(0xFFE2E8F0))
                                .padding(horizontal = 4.dp, vertical = cellPaddingV),
                            textAlign = TextAlign.Center
                        )

                        spreadsheet.headers.indices.forEach { colIdx ->
                            val cellVal = row.getOrNull(colIdx) ?: ""
                            val isMatch = searchQuery.isNotBlank() && cellVal.contains(searchQuery, ignoreCase = true)

                            Text(
                                text = cellVal,
                                fontSize = textSize,
                                color = Color(0xFF334155),
                                modifier = Modifier
                                    .width(150.dp)
                                    .border(0.5.dp, Color(0xFFF1F5F9))
                                    .then(if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier)
                                    .padding(horizontal = cellPaddingH, vertical = cellPaddingV),
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. WORD DOCUMENT PAGE COMPOSABLE (DOC/DOCX)
// ==========================================
@Composable
private fun WordDocumentPageView(
    page: com.simats.intellifile.data.manager.DocumentPageData?,
    currentPage: Int,
    totalPages: Int,
    zoomPercentage: Int,
    searchQuery: String,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit
) {
    val scaleFactor = zoomPercentage / 100f
    val bodySize = (15 * scaleFactor).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        // Document Page Paper Sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .shadow(3.dp, RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                // Header page indicator inside paper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page $currentPage of $totalPages",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "IntelliFile Reader",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(18.dp))

                if (page != null && page.paragraphs.isNotEmpty()) {
                    page.paragraphs.forEach { p ->
                        val isMatch = searchQuery.isNotBlank() && p.contains(searchQuery, ignoreCase = true)
                        Text(
                            text = p,
                            fontSize = bodySize,
                            color = Color(0xFF1E293B),
                            lineHeight = (bodySize.value * 1.6f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isMatch) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier)
                                .padding(vertical = 6.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Page content empty.",
                        fontSize = bodySize,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. PLAIN TEXT / CODE VIEWER COMPOSABLE
// ==========================================
@Composable
private fun PlainTextViewer(
    textDoc: ParsedDocumentContent.PlainText,
    fileFormat: String,
    zoomPercentage: Int,
    searchQuery: String
) {
    val scaleFactor = zoomPercentage / 100f
    val bodySize = (13 * scaleFactor).sp
    val isMonospace = fileFormat in setOf("JSON", "XML", "HTML", "LOG", "CSV")

    val lines = remember(textDoc.text) { textDoc.text.lines() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            lines.forEachIndexed { idx, line ->
                val isMatch = searchQuery.isNotBlank() && line.contains(searchQuery, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}".padStart(4),
                        fontSize = (bodySize.value * 0.85f).sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = line,
                        fontSize = bodySize,
                        color = Color(0xFF0F172A),
                        fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                        lineHeight = (bodySize.value * 1.4f).sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. BOTTOM ACTION BAR COMPOSABLE
// ==========================================
@Composable
fun DocumentBottomActionBar(
    isSaved: Boolean,
    onSaveClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        HorizontalDivider(
            color = Color(0xFFF1F5F9),
            thickness = 1.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onThumbnailClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Jump",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jump",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onShareClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Share",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSaved) Color(0xFF26E07F) else MaterialTheme.colorScheme.primary)
                    .clickable { onSaveClick() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Save,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSaved) "Saved" else "Save",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onMoreClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Priority",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Priority",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentViewerScreenPreview() {
    DocumentViewerScreen()
}
