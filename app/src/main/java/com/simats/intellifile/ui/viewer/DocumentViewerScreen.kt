package com.simats.intellifile.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.DocumentContentParser
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.manager.ParsedDocumentContent
import com.simats.intellifile.data.manager.SlideData
import com.simats.intellifile.data.manager.SlideImage
import com.simats.intellifile.data.manager.SlideTable
import com.simats.intellifile.data.manager.SlideSection
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.dialog.SaveConfirmationDialog
import com.simats.intellifile.data.manager.DocumentElement
import com.simats.intellifile.data.manager.DocumentElementType
import com.simats.intellifile.data.manager.DocumentTable
import com.simats.intellifile.data.manager.DocumentImage
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ViewAgenda
import com.simats.intellifile.ui.viewer.ppt.PPTViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PresentationViewMode {
    SLIDE_CARDS, ALL_CONTENT
}

enum class WordDocViewMode {
    PAGE_VIEW, CONTINUOUS
}

@Composable
fun DocumentViewerScreen(
    fileName: String = "Document.pdf",
    filePath: String = "",
    fileUriString: String = "",
    fileFormat: String = "PDF",
    totalPages: Int = 1,
    documentId: Int? = null,
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
    val isPreview = LocalInspectionMode.current
    val key = if (filePath.isNotBlank()) filePath else fileUriString

    val effectiveFormat = remember(fileName, fileFormat) {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        if (ext.isNotBlank()) ext else fileFormat.uppercase().substringBefore(" ")
    }

    val isPdf = remember(effectiveFormat) { effectiveFormat == "PDF" }

    val isPresentation = remember(effectiveFormat, fileName) {
        effectiveFormat.uppercase() in listOf("PPT", "PPTX", "PPS", "PPSX", "POT", "POTX") ||
                fileName.endsWith(".pptx", ignoreCase = true) ||
                fileName.endsWith(".ppt", ignoreCase = true) ||
                fileName.endsWith(".ppsx", ignoreCase = true) ||
                fileName.endsWith(".pps", ignoreCase = true)
    }

    if (isPresentation) {
        PPTViewerScreen(
            fileName = fileName,
            filePath = filePath,
            fileUriString = fileUriString,
            documentId = documentId,
            onBackClick = onBackClick,
            onShareClick = onShareClick
        )
        return
    }

    // Navigation and display states
    var currentPageIndex by remember { mutableIntStateOf(1) }
    var totalUnitsCount by remember { mutableIntStateOf(totalPages.coerceAtLeast(1)) }
    var zoomPercentage by remember { mutableIntStateOf(100) }
    var docGestureScale by remember { mutableFloatStateOf(1f) }
    var docPanOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentPageIndex) {
        docGestureScale = 1f
        docPanOffset = Offset.Zero
        zoomPercentage = 100
    }

    var wordDocViewMode by remember { mutableStateOf(WordDocViewMode.PAGE_VIEW) }
    var zoomTargetDocumentImage by remember { mutableStateOf<DocumentImage?>(null) }
    var isBookmarked by remember {
        mutableStateOf(
            if (!isPreview && key.isNotBlank()) LocalFileDatabaseHelper.getInstance(context).isBookmarked(key) else false
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
    var pdfRenderer by remember { mutableStateOf<PdfRendererWrapper?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Parsed Content state for Office and Text documents
    var parsedContent by remember { mutableStateOf<ParsedDocumentContent?>(null) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var presentationViewMode by remember { mutableStateOf(PresentationViewMode.SLIDE_CARDS) }
    var zoomTargetImage by remember { mutableStateOf<SlideImage?>(null) }
    var isFullscreenSlideshow by remember { mutableStateOf(false) }

    fun openWithExternalApp() {
        try {  
            var fileToOpen: java.io.File? = null
            if (filePath.isNotBlank()) {
                val f = java.io.File(filePath)
                if (f.exists() && f.canRead()) {
                    fileToOpen = f
                }
            }

            if (fileToOpen == null) {
                val safeName = if (fileName.isNotBlank()) fileName else "presentation.$effectiveFormat"
                val cacheFile = java.io.File(context.cacheDir, "shared_$safeName")
                LocalFileManager.openInputStream(context, fileUriString, filePath)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    fileToOpen = cacheFile
                }
            }

            val uri = if (fileToOpen != null) {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    fileToOpen
                )
            } else if (fileUriString.isNotBlank()) {
                Uri.parse(fileUriString)
            } else null

            if (uri != null) {
                val mime = when {
                    fileName.endsWith(".pptx", ignoreCase = true) || effectiveFormat.equals("PPTX", ignoreCase = true) ->
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    fileName.endsWith(".ppt", ignoreCase = true) || effectiveFormat.equals("PPT", ignoreCase = true) ->
                        "application/vnd.ms-powerpoint"
                    fileName.endsWith(".docx", ignoreCase = true) ->
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    fileName.endsWith(".doc", ignoreCase = true) ->
                        "application/msword"
                    fileName.endsWith(".xlsx", ignoreCase = true) ->
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    fileName.endsWith(".xls", ignoreCase = true) ->
                        "application/vnd.ms-excel"
                    fileName.endsWith(".pdf", ignoreCase = true) ->
                        "application/pdf"
                    else -> "*/*"
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open Presentation With"))
            } else {
                Toast.makeText(context, "Cannot locate presentation file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No external presentation app found", Toast.LENGTH_SHORT).show()
        }
    }

    // Load document content based on format
    LaunchedEffect(filePath, fileUriString, fileName, effectiveFormat, isPreview) {
        isLoading = true
        errorMessage = null

        if (isPreview) {
            isLoading = false
            totalUnitsCount = totalPages.coerceAtLeast(1)
            currentPageIndex = 1
            return@LaunchedEffect
        }

        if (isPdf) {
            withContext(Dispatchers.IO) {
                try {
                    var pfd = LocalFileManager.getFileDescriptor(context, fileUriString, filePath)
                    var renderer: PdfRendererWrapper? = null
                    if (pfd != null) {
                        try {
                            renderer = PdfRendererWrapper(pfd)
                        } catch (seekEx: Exception) {
                            // Direct descriptor was not seekable (e.g. pipe from WhatsApp/Gmail/Drive).
                            // Fallback: create seekable cache copy.
                            try { pfd.close() } catch (ignored: Exception) {}
                            pfd = null

                            val tempFile = java.io.File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
                            LocalFileManager.openInputStream(context, fileUriString, filePath)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0) {
                                val fallbackPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                renderer = PdfRendererWrapper(fallbackPfd)
                                pfd = fallbackPfd
                            } else {
                                throw seekEx
                            }
                        }
                    } else {
                        // Stream copy fallback if pfd was null initially
                        val tempFile = java.io.File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
                        LocalFileManager.openInputStream(context, fileUriString, filePath)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            val fallbackPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRendererWrapper(fallbackPfd)
                            pfd = fallbackPfd
                        }
                    }

                    if (renderer != null && pfd != null) {
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
    LaunchedEffect(currentPageIndex, zoomPercentage, pdfRenderer, isPreview) {
        if (isPreview) return@LaunchedEffect
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (totalUnitsCount <= 0) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val pageIndex = (currentPageIndex - 1).coerceIn(0, totalUnitsCount - 1)
                val density = context.resources.displayMetrics.density
                val scale = (zoomPercentage / 100f) * density
                val bitmap = renderer.renderPage(pageIndex, scale)
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

    // Document Image Zoom Dialog
    if (zoomTargetDocumentImage != null) {
        val targetImg = zoomTargetDocumentImage!!
        val bmp = remember(targetImg) {
            try {
                BitmapFactory.decodeByteArray(targetImg.bytes, 0, targetImg.bytes.size)
            } catch (_: Exception) { null }
        }
        var imgScale by remember { mutableFloatStateOf(1f) }
        var imgOffset by remember { mutableStateOf(Offset.Zero) }

        Dialog(
            onDismissRequest = { zoomTargetDocumentImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                imgScale = (imgScale * zoom).coerceIn(0.5f, 5f)
                                imgOffset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = targetImg.caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = imgScale,
                                    scaleY = imgScale,
                                    translationX = imgOffset.x,
                                    translationY = imgOffset.y
                                )
                        )
                    } else {
                        Text("Image could not be rendered", color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = targetImg.caption.ifBlank { "Document Graphic" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { zoomTargetDocumentImage = null },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
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
                                parsedContent is ParsedDocumentContent.RichDocument -> {
                                    val rich = parsedContent as ParsedDocumentContent.RichDocument
                                    if (rich.wordCount > 0) "Word Document · Page $currentPageIndex of $totalUnitsCount · ${rich.wordCount} words"
                                    else "Word Document · Page $currentPageIndex of $totalUnitsCount"
                                }
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
                        if (parsedContent is ParsedDocumentContent.Presentation || effectiveFormat.uppercase() in listOf("PPT", "PPTX")) {
                            IconButton(onClick = { openWithExternalApp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in PowerPoint / Office App",
                                    tint = Color(0xFF55A9AF)
                                )
                            }
                            IconButton(onClick = { isFullscreenSlideshow = true }) {
                                Icon(
                                    imageVector = Icons.Default.Slideshow,
                                    contentDescription = "Fullscreen Slideshow",
                                    tint = Color(0xFF111827)
                                )
                            }
                        }

                        if (parsedContent is ParsedDocumentContent.RichDocument || effectiveFormat.uppercase() in listOf("DOC", "DOCX", "RTF")) {
                            IconButton(onClick = { openWithExternalApp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in Word / Office App",
                                    tint = Color(0xFF55A9AF)
                                )
                            }
                            IconButton(onClick = {
                                wordDocViewMode = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) WordDocViewMode.CONTINUOUS else WordDocViewMode.PAGE_VIEW
                            }) {
                                Icon(
                                    imageVector = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) "Switch to Continuous Reading" else "Switch to Page View",
                                    tint = Color(0xFF111827)
                                )
                            }
                        }

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
                                    .clipToBounds()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (docGestureScale > 1.1f) {
                                                    docGestureScale = 1f
                                                    docPanOffset = Offset.Zero
                                                } else {
                                                    docGestureScale = 2.2f
                                                    docPanOffset = Offset.Zero
                                                }
                                                zoomPercentage = (docGestureScale * 100).toInt()
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            var prevDist = 0f
                                            var prevCentroid = Offset.Zero
                                            do {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.filter { it.pressed }
                                                if (pressed.size >= 2) {
                                                    // Two-finger pinch to zoom in/out
                                                    val p1 = pressed[0].position
                                                    val p2 = pressed[1].position
                                                    val currDist = (p1 - p2).getDistance()
                                                    val currCentroid = (p1 + p2) / 2f
                                                    if (prevDist > 0f) {
                                                        val zoomRatio = currDist / prevDist
                                                        docGestureScale = (docGestureScale * zoomRatio).coerceIn(0.6f, 5.0f)
                                                        val maxPanX = (1200f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                                        val maxPanY = (1600f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                                        val rawPan = docPanOffset + (currCentroid - prevCentroid)
                                                        docPanOffset = Offset(
                                                            x = rawPan.x.coerceIn(-maxPanX, maxPanX),
                                                            y = rawPan.y.coerceIn(-maxPanY, maxPanY)
                                                        )
                                                        zoomPercentage = (docGestureScale * 100).toInt()
                                                    }
                                                    prevDist = currDist
                                                    prevCentroid = currCentroid
                                                    event.changes.forEach { it.consume() }
                                                } else if (pressed.size == 1 && docGestureScale > 1.05f) {
                                                    // Single-finger drag pan when zoomed into PDF
                                                    val change = pressed[0]
                                                    val panDelta = change.positionChange()
                                                    if (panDelta != Offset.Zero) {
                                                        val maxPanX = (1200f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                                        val maxPanY = (1600f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                                        val rawPan = docPanOffset + panDelta
                                                        docPanOffset = Offset(
                                                            x = rawPan.x.coerceIn(-maxPanX, maxPanX),
                                                            y = rawPan.y.coerceIn(-maxPanY, maxPanY)
                                                        )
                                                        change.consume()
                                                    }
                                                    prevDist = 0f
                                                } else {
                                                    prevDist = 0f
                                                }
                                            } while (pressed.isNotEmpty())
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = pageBitmap!!,
                                    contentDescription = "Page $currentPageIndex of $totalUnitsCount",
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = docGestureScale
                                            scaleY = docGestureScale
                                            translationX = docPanOffset.x
                                            translationY = docPanOffset.y
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                )
                            }
                        } else if (isPreview) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = fileName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "PDF Document Preview",
                                            fontSize = 13.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text("Rendering page...", fontSize = 14.sp, color = Color(0xFF6B7280))
                        }
                    }

                    // PPT / PPTX Presentation View
                    parsedContent is ParsedDocumentContent.Presentation -> {
                        val presentation = parsedContent as ParsedDocumentContent.Presentation
                        if (presentationViewMode == PresentationViewMode.ALL_CONTENT) {
                            PresentationAllContentView(
                                presentation = presentation,
                                zoomPercentage = zoomPercentage,
                                searchQuery = searchQuery,
                                onSwitchToSlide = { slideNum ->
                                    currentPageIndex = slideNum
                                    presentationViewMode = PresentationViewMode.SLIDE_CARDS
                                },
                                onZoomImage = { zoomTargetImage = it },
                                onOpenExternal = { openWithExternalApp() }
                            )
                        } else {
                            val slideIndex = (currentPageIndex - 1).coerceIn(0, presentation.slides.size - 1)
                            val slide = presentation.slides.getOrNull(slideIndex) ?: SlideData(1, "Slide")

                            PresentationSlideView(
                                slide = slide,
                                currentSlide = currentPageIndex,
                                totalSlides = presentation.totalSlides,
                                zoomPercentage = zoomPercentage,
                                searchQuery = searchQuery,
                                viewMode = presentationViewMode,
                                onViewModeChange = { presentationViewMode = it },
                                onPrevSlide = { if (currentPageIndex > 1) currentPageIndex-- },
                                onNextSlide = { if (currentPageIndex < totalUnitsCount) currentPageIndex++ },
                                onJumpClick = { showJumpDialog = true },
                                onZoomImage = { zoomTargetImage = it },
                                onStartSlideshow = { isFullscreenSlideshow = true },
                                onOpenExternal = { openWithExternalApp() }
                            )
                        }
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
                        if (wordDocViewMode == WordDocViewMode.CONTINUOUS) {
                            WordDocumentContinuousView(
                                doc = doc,
                                zoomPercentage = zoomPercentage,
                                docGestureScale = docGestureScale,
                                docPanOffset = docPanOffset,
                                onZoomPanChange = { newScale, newPan ->
                                    docGestureScale = newScale
                                    val maxPanX = (1200f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                    val maxPanY = (1600f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                    docPanOffset = Offset(
                                        x = newPan.x.coerceIn(-maxPanX, maxPanX),
                                        y = newPan.y.coerceIn(-maxPanY, maxPanY)
                                    )
                                    zoomPercentage = (docGestureScale * 100).toInt()
                                },
                                onDoubleTap = {
                                    if (docGestureScale > 1.1f) {
                                        docGestureScale = 1f
                                        docPanOffset = Offset.Zero
                                    } else {
                                        docGestureScale = 2.2f
                                        docPanOffset = Offset.Zero
                                    }
                                    zoomPercentage = (docGestureScale * 100).toInt()
                                },
                                searchQuery = searchQuery,
                                onZoomImage = { zoomTargetDocumentImage = it }
                            )
                        } else {
                            val pageIdx = if (doc.pages.isNotEmpty()) (currentPageIndex - 1).coerceIn(0, doc.pages.size - 1) else 0
                            val page = doc.pages.getOrNull(pageIdx)

                            WordDocumentPageView(
                                page = page,
                                currentPage = currentPageIndex,
                                totalPages = doc.totalPages,
                                zoomPercentage = zoomPercentage,
                                docGestureScale = docGestureScale,
                                docPanOffset = docPanOffset,
                                onZoomPanChange = { newScale, newPan ->
                                    docGestureScale = newScale
                                    val maxPanX = (1200f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                    val maxPanY = (1600f * (docGestureScale - 1f)).coerceAtLeast(0f)
                                    docPanOffset = Offset(
                                        x = newPan.x.coerceIn(-maxPanX, maxPanX),
                                        y = newPan.y.coerceIn(-maxPanY, maxPanY)
                                    )
                                    zoomPercentage = (docGestureScale * 100).toInt()
                                },
                                onDoubleTap = {
                                    if (docGestureScale > 1.1f) {
                                        docGestureScale = 1f
                                        docPanOffset = Offset.Zero
                                    } else {
                                        docGestureScale = 2.2f
                                        docPanOffset = Offset.Zero
                                    }
                                    zoomPercentage = (docGestureScale * 100).toInt()
                                },
                                searchQuery = searchQuery,
                                onPrevPage = { if (currentPageIndex > 1) currentPageIndex-- },
                                onNextPage = { if (currentPageIndex < totalUnitsCount) currentPageIndex++ },
                                onZoomImage = { zoomTargetDocumentImage = it }
                            )
                        }
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
                    if (wordDocViewMode == WordDocViewMode.CONTINUOUS && parsedContent is ParsedDocumentContent.RichDocument) {
                        Text(
                            text = "Full Document · $totalUnitsCount Pages",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    } else {
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
                        onClick = {
                            if (zoomPercentage > 60) {
                                zoomPercentage -= 20
                                docGestureScale = zoomPercentage / 100f
                                if (docGestureScale <= 1f) docPanOffset = Offset.Zero
                            }
                        },
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
                        color = Color(0xFF111827),
                        modifier = Modifier.clickable {
                            docGestureScale = 1f
                            docPanOffset = Offset.Zero
                            zoomPercentage = 100
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (zoomPercentage < 300) {
                                zoomPercentage += 20
                                docGestureScale = zoomPercentage / 100f
                            }
                        },
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

        // Interactive Full-Screen Diagram Zoom Dialog
        if (zoomTargetImage != null) {
            DiagramZoomDialog(
                image = zoomTargetImage!!,
                onDismiss = { zoomTargetImage = null }
            )
        }

        // Fullscreen Slideshow Presentation Mode
        if (isFullscreenSlideshow && parsedContent is ParsedDocumentContent.Presentation) {
            FullscreenSlideshowDialog(
                presentation = parsedContent as ParsedDocumentContent.Presentation,
                initialSlide = currentPageIndex,
                onDismiss = { isFullscreenSlideshow = false }
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
    viewMode: PresentationViewMode,
    onViewModeChange: (PresentationViewMode) -> Unit,
    onPrevSlide: () -> Unit,
    onNextSlide: () -> Unit,
    onJumpClick: () -> Unit = {},
    onZoomImage: (SlideImage) -> Unit = {},
    onStartSlideshow: () -> Unit = {},
    onOpenExternal: () -> Unit = {}
) {
    val scaleFactor = zoomPercentage / 100f
    val titleSize = (22 * scaleFactor).sp
    val bodySize = (14 * scaleFactor).sp

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // Pre-decode all bitmaps for this slide's images and diagrams (preserves vector diagrams if decode fails)
    val slideBitmaps = remember(slide.images) {
        slide.images.map { img ->
            val bmp = try {
                BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
            Pair(img, bmp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slide Card Container (16:9 Presentation look with swipe gesture)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .pointerInput(currentSlide) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragAccumulator < -60f && currentSlide < totalSlides) {
                                onNextSlide()
                            } else if (dragAccumulator > 60f && currentSlide > 1) {
                                onPrevSlide()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                }
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Slide Badge & Mode Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF8F8))
                            .clickable { onJumpClick() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Slideshow,
                            contentDescription = null,
                            tint = Color(0xFF55A9AF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SLIDE $currentSlide OF $totalSlides",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF387A80)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { onStartSlideshow() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Slideshow,
                                    contentDescription = "Slideshow",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Present", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // View Mode Toggle (Slide View vs All Content)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF55A9AF))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Slide",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onViewModeChange(PresentationViewMode.ALL_CONTENT) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "All Content",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Slide Title
                Text(
                    text = slide.title.ifBlank { "Slide $currentSlide" },
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    lineHeight = (titleSize.value * 1.3f).sp
                )

                // Optional Slide Subtitle
                if (slide.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = slide.subtitle,
                        fontSize = (titleSize.value * 0.72f).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        lineHeight = (titleSize.value * 0.95f).sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 1. ALL Visuals, Diagrams, Charts & Figures on this Slide
                if (slideBitmaps.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF55A9AF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Slide Diagrams & Visuals (${slideBitmaps.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Tap to zoom",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    slideBitmaps.forEachIndexed { imgIdx, (img, bitmap) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = img.title.ifBlank { "Diagram ${imgIdx + 1}" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )

                                    if (bitmap != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFEFF6FF),
                                            modifier = Modifier.clickable { onZoomImage(img) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "Zoom",
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Zoom",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF2563EB)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (bitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .clickable { onZoomImage(img) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = img.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 160.dp, max = 340.dp)
                                                .padding(6.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF55A9AF),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = img.title.ifBlank { "Embedded Presentation Diagram" },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Text(
                                                    text = "Vector diagram · Tap 'Open in Office' below to inspect full graphics",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (imgIdx < slideBitmaps.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. Structured Tables on this Slide
                if (slide.tables.isNotEmpty()) {
                    slide.tables.forEachIndexed { tblIdx, tbl ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                tbl.rows.forEachIndexed { rowIdx, rowCells ->
                                    val isHeader = rowIdx == 0
                                    Row(
                                        modifier = Modifier
                                            .background(if (isHeader) Color(0xFF55A9AF) else if (rowIdx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                            .padding(vertical = 8.dp, horizontal = 12.dp)
                                    ) {
                                        rowCells.forEach { cellText ->
                                            Text(
                                                text = cellText,
                                                fontSize = if (isHeader) 13.sp else 12.sp,
                                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isHeader) Color.White else Color(0xFF1E293B),
                                                modifier = Modifier
                                                    .widthIn(min = 90.dp, max = 220.dp)
                                                    .padding(horizontal = 6.dp)
                                            )
                                        }
                                    }
                                    if (rowIdx < tbl.rows.size - 1) {
                                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // 3. Structured Content Sections / Cards
                if (slide.sections.isNotEmpty()) {
                    slide.sections.forEach { sec ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (sec.isCallout) Color(0xFFFEF9C3) else Color(0xFFF8FAFC)
                            ),
                            border = BorderStroke(1.dp, if (sec.isCallout) Color(0xFFFEF08A) else Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                if (sec.heading.isNotBlank()) {
                                    Text(
                                        text = sec.heading,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sec.isCallout) Color(0xFF854D0E) else Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                if (sec.items.isNotEmpty()) {
                                    sec.items.forEach { itm ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sec.isCallout) Color(0xFFB45309) else Color(0xFF55A9AF))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = itm,
                                                fontSize = bodySize,
                                                color = if (sec.isCallout) Color(0xFF713F12) else Color(0xFF334155),
                                                lineHeight = (bodySize.value * 1.45f).sp
                                            )
                                        }
                                    }
                                } else if (sec.content.isNotBlank()) {
                                    Text(
                                        text = sec.content,
                                        fontSize = bodySize,
                                        color = if (sec.isCallout) Color(0xFF713F12) else Color(0xFF334155),
                                        lineHeight = (bodySize.value * 1.45f).sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 4. Bullet Points & Content Paragraphs
                val displayPoints = slide.points.filter { it.isNotBlank() }

                if (displayPoints.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        displayPoints.forEach { point ->
                            val isHighlighted = searchQuery.isNotBlank() && point.contains(searchQuery, ignoreCase = true)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isHighlighted) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = (bodySize.value * 0.4f).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF55A9AF))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = point,
                                    fontSize = bodySize,
                                    color = Color(0xFF1E293B),
                                    lineHeight = (bodySize.value * 1.55f).sp
                                )
                            }
                        }
                    }
                } else if (slide.rawContent.isNotBlank() && slide.rawContent != slide.title) {
                    val rawLines = slide.rawContent.split("\n\n", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it != slide.title && it != slide.subtitle }

                    if (rawLines.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rawLines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = (bodySize.value * 0.4f).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF55A9AF))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = line,
                                        fontSize = bodySize,
                                        color = Color(0xFF1E293B),
                                        lineHeight = (bodySize.value * 1.55f).sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (displayPoints.isEmpty() && slide.sections.isEmpty() && slide.tables.isEmpty() && slideBitmaps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Slide $currentSlide has no readable text content.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenExternal,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in PowerPoint", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 5. Speaker Notes section if present
                if (slide.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFEF9C3))
                            .border(1.dp, Color(0xFFFEF08A), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF854D0E),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Speaker Notes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF854D0E)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = slide.notes,
                            fontSize = (bodySize.value * 0.9f).sp,
                            color = Color(0xFF713F12),
                            lineHeight = (bodySize.value * 1.35f).sp
                        )
                    }
                }

                // Quick Action Bar: Open Original Presentation with Office
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onOpenExternal,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF55A9AF)),
                        border = BorderStroke(1.dp, Color(0xFF55A9AF).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Full PPT in Office / PowerPoint", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // On-card Slide Navigation Bar
        if (totalSlides > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrevSlide,
                    enabled = currentSlide > 1,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Slide",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev Slide", fontSize = 12.sp)
                }

                Text(
                    text = "Slide $currentSlide of $totalSlides",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280)
                )

                Button(
                    onClick = onNextSlide,
                    enabled = currentSlide < totalSlides,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Next Slide", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Slide",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Continuous Reading View showing all presentation slides and their full textual content.
 */
@Composable
private fun PresentationAllContentView(
    presentation: ParsedDocumentContent.Presentation,
    zoomPercentage: Int,
    searchQuery: String,
    onSwitchToSlide: (Int) -> Unit,
    onZoomImage: (SlideImage) -> Unit = {},
    onOpenExternal: () -> Unit = {}
) {
    val scaleFactor = zoomPercentage / 100f
    val titleSize = (18 * scaleFactor).sp
    val bodySize = (14 * scaleFactor).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFF8F8))
                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Full Presentation Content",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D4ED8)
                )
                Text(
                    text = "${presentation.totalSlides} Slides Extracted",
                    fontSize = 11.sp,
                    color = Color(0xFF60A5FA)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenExternal) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open External",
                        tint = Color(0xFF55A9AF)
                    )
                }
                Button(
                    onClick = { onSwitchToSlide(1) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Slideshow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Slide View", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(presentation.slides) { index, slide ->
                val slideNum = index + 1

                val slideBitmaps = remember(slide.images) {
                    slide.images.map { img ->
                        val bmp = try {
                            BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)?.asImageBitmap()
                        } catch (_: Exception) { null }
                        Pair(img, bmp)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSwitchToSlide(slideNum) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEFF8F8))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SLIDE $slideNum OF ${presentation.totalSlides}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF387A80)
                                )
                            }

                            Text(
                                text = "View as Slide >",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF55A9AF)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title
                        Text(
                            text = slide.title.ifBlank { "Slide $slideNum" },
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        if (slide.subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = slide.subtitle,
                                fontSize = (titleSize.value * 0.72f).sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Diagrams on this slide
                        if (slideBitmaps.isNotEmpty()) {
                            slideBitmaps.forEach { (img, bmp) ->
                                if (bmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .clickable { onZoomImage(img) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = img.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .padding(6.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF55A9AF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = img.title.ifBlank { "Embedded Diagram (${img.fileName.substringAfterLast('.').uppercase()})" },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Tables on this slide
                        if (slide.tables.isNotEmpty()) {
                            slide.tables.forEach { tbl ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    tbl.rows.forEachIndexed { rIdx, cells ->
                                        Row(
                                            modifier = Modifier
                                                .background(if (rIdx == 0) Color(0xFF55A9AF) else if (rIdx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                                .padding(6.dp)
                                        ) {
                                            cells.forEach { c ->
                                                Text(
                                                    text = c,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (rIdx == 0) Color.White else Color(0xFF1E293B),
                                                    modifier = Modifier
                                                        .widthIn(min = 80.dp, max = 180.dp)
                                                        .padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Points
                        val points = slide.points.filter { it.isNotBlank() }
                        if (points.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                points.forEach { pt ->
                                    val isHighlighted = searchQuery.isNotBlank() && pt.contains(searchQuery, ignoreCase = true)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (isHighlighted) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = (bodySize.value * 0.4f).dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF55A9AF))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = pt,
                                            fontSize = bodySize,
                                            color = Color(0xFF1E293B),
                                            lineHeight = (bodySize.value * 1.5f).sp
                                        )
                                    }
                                }
                            }
                        } else if (slide.rawContent.isNotBlank() && slide.rawContent != slide.title) {
                            val rawLines = slide.rawContent.split("\n\n", "\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && it != slide.title && it != slide.subtitle }

                            if (rawLines.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rawLines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = (bodySize.value * 0.4f).dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF55A9AF))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = line,
                                                fontSize = bodySize,
                                                color = Color(0xFF1E293B),
                                                lineHeight = (bodySize.value * 1.5f).sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Notes
                        if (slide.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Notes: ${slide.notes}",
                                fontSize = (bodySize.value * 0.85f).sp,
                                color = Color(0xFF713F12),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFEF9C3))
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Full-Screen Diagram & Image Zoom Dialog with pinch-to-zoom and pan.
 */
@Composable
private fun DiagramZoomDialog(
    image: SlideImage,
    onDismiss: () -> Unit
) {
    val bitmap = remember(image.bytes) {
        try {
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = image.title.ifBlank { "Presentation Diagram" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "Pinch to zoom (up to 5x) · Drag to pan diagram",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(5f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                    }
                    IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Interactive Zoom / Pan Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 24.dp, start = 12.dp, end = 12.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = image.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Unable to render image bytes", color = Color.White)
                }
            }
        }
    }
}

/**
 * Fullscreen Immersive Slideshow Mode
 */
@Composable
private fun FullscreenSlideshowDialog(
    presentation: ParsedDocumentContent.Presentation,
    initialSlide: Int,
    onDismiss: () -> Unit
) {
    var currentSlideNum by remember { mutableIntStateOf(initialSlide) }
    val total = presentation.totalSlides
    val slide = presentation.slides.getOrNull(currentSlideNum - 1) ?: presentation.slides.first()

    val slideBitmaps = remember(slide.images) {
        slide.images.mapNotNull { img ->
            try {
                BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)?.let { bmp ->
                    Pair(img, bmp.asImageBitmap())
                }
            } catch (_: Exception) { null }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D16))
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "SLIDE $currentSlideNum OF $total",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF55A9AF),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
                }
            }

            // Slide Canvas with scroll
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 72.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = slide.title.ifBlank { "Slide $currentSlideNum" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (slide.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = slide.subtitle,
                            fontSize = 16.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Diagrams
                    slideBitmaps.forEach { (img, bmp) ->
                        Image(
                            bitmap = bmp,
                            contentDescription = img.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Tables
                    slide.tables.forEach { tbl ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        ) {
                            tbl.rows.forEachIndexed { rIdx, cells ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (rIdx == 0) Color(0xFF55A9AF) else if (rIdx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                        .padding(8.dp)
                                ) {
                                    cells.forEach { c ->
                                        Text(
                                            text = c,
                                            fontSize = 12.sp,
                                            fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (rIdx == 0) Color.White else Color(0xFF1E293B),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Content Points
                    slide.points.forEach { pt ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF55A9AF))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = pt, fontSize = 14.sp, color = Color(0xFF1E293B), lineHeight = 20.sp)
                        }
                    }
                }
            }

            // Bottom Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (currentSlideNum > 1) currentSlideNum-- },
                    enabled = currentSlideNum > 1,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Previous")
                }

                Button(
                    onClick = { if (currentSlideNum < total) currentSlideNum++ },
                    enabled = currentSlideNum < total,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Slide")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
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
// 3. WORD DOCUMENT COMPOSABLES (DOC/DOCX)
// ==========================================

@Composable
private fun WordDocumentContinuousView(
    doc: com.simats.intellifile.data.manager.ParsedDocumentContent.RichDocument,
    zoomPercentage: Int,
    docGestureScale: Float = 1f,
    docPanOffset: Offset = Offset.Zero,
    onZoomPanChange: (Float, Offset) -> Unit = { _, _ -> },
    onDoubleTap: () -> Unit = {},
    searchQuery: String,
    onZoomImage: (com.simats.intellifile.data.manager.DocumentImage) -> Unit
) {
    val scaleFactor = zoomPercentage / 100f
    val readingTimeMin = ((doc.wordCount / 200.0) + 0.5).toInt().coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var prevDist = 0f
                    var prevCentroid = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val currDist = (p1 - p2).getDistance()
                            val currCentroid = (p1 + p2) / 2f
                            if (prevDist > 0f) {
                                val zoomRatio = currDist / prevDist
                                val newScale = (docGestureScale * zoomRatio).coerceIn(0.6f, 5.0f)
                                val panDelta = currCentroid - prevCentroid
                                onZoomPanChange(newScale, docPanOffset + panDelta)
                            }
                            prevDist = currDist
                            prevCentroid = currCentroid
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && docGestureScale > 1.05f) {
                            val change = pressed[0]
                            val panDelta = change.positionChange()
                            if (panDelta != Offset.Zero) {
                                onZoomPanChange(docGestureScale, docPanOffset + panDelta)
                                change.consume()
                            }
                            prevDist = 0f
                        } else {
                            prevDist = 0f
                        }
                    } while (pressed.isNotEmpty())
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = docGestureScale
                    scaleY = docGestureScale
                    translationX = docPanOffset.x
                    translationY = docPanOffset.y
                }
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .shadow(3.dp, RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
        Column {
            // Document Metadata Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEFF8F8))
                    .border(1.dp, Color(0xFFD5ECEE), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * scaleFactor).sp,
                        color = Color(0xFF183A3C),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${doc.totalPages} Pages · ${doc.wordCount} Words · ~$readingTimeMin min read",
                        fontSize = (11 * scaleFactor).sp,
                        color = Color(0xFF537779)
                    )
                }
                Text(
                    text = "Continuous",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF55A9AF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Render all pages sequentially
            doc.pages.forEachIndexed { pageIdx, page ->
                if (pageIdx > 0) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        Text(
                            text = "  Page ${pageIdx + 1} of ${doc.totalPages}  ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (page.elements.isNotEmpty()) {
                    page.elements.forEach { element ->
                        RenderDocumentElement(
                            element = element,
                            scaleFactor = scaleFactor,
                            searchQuery = searchQuery,
                            onZoomImage = onZoomImage
                        )
                    }
                } else if (page.paragraphs.isNotEmpty()) {
                    page.paragraphs.forEach { p ->
                        RenderPlainParagraph(
                            text = p,
                            scaleFactor = scaleFactor,
                            searchQuery = searchQuery
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "— End of Document —",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
}

@Composable
private fun WordDocumentPageView(
    page: com.simats.intellifile.data.manager.DocumentPageData?,
    currentPage: Int,
    totalPages: Int,
    zoomPercentage: Int,
    docGestureScale: Float = 1f,
    docPanOffset: Offset = Offset.Zero,
    onZoomPanChange: (Float, Offset) -> Unit = { _, _ -> },
    onDoubleTap: () -> Unit = {},
    searchQuery: String,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onZoomImage: (com.simats.intellifile.data.manager.DocumentImage) -> Unit
) {
    val scaleFactor = zoomPercentage / 100f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var prevDist = 0f
                    var prevCentroid = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val currDist = (p1 - p2).getDistance()
                            val currCentroid = (p1 + p2) / 2f
                            if (prevDist > 0f) {
                                val zoomRatio = currDist / prevDist
                                val newScale = (docGestureScale * zoomRatio).coerceIn(0.6f, 5.0f)
                                val panDelta = currCentroid - prevCentroid
                                onZoomPanChange(newScale, docPanOffset + panDelta)
                            }
                            prevDist = currDist
                            prevCentroid = currCentroid
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && docGestureScale > 1.05f) {
                            val change = pressed[0]
                            val panDelta = change.positionChange()
                            if (panDelta != Offset.Zero) {
                                onZoomPanChange(docGestureScale, docPanOffset + panDelta)
                                change.consume()
                            }
                            prevDist = 0f
                        } else {
                            prevDist = 0f
                        }
                    } while (pressed.isNotEmpty())
                }
            }
    ) {
        // Document Page Paper Sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    scaleX = docGestureScale
                    scaleY = docGestureScale
                    translationX = docPanOffset.x
                    translationY = docPanOffset.y
                }
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
                        text = "IntelliFile Document Reader",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF55A9AF)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                if (page != null && page.elements.isNotEmpty()) {
                    page.elements.forEach { element ->
                        RenderDocumentElement(
                            element = element,
                            scaleFactor = scaleFactor,
                            searchQuery = searchQuery,
                            onZoomImage = onZoomImage
                        )
                    }
                } else if (page != null && page.paragraphs.isNotEmpty()) {
                    page.paragraphs.forEach { p ->
                        RenderPlainParagraph(
                            text = p,
                            scaleFactor = scaleFactor,
                            searchQuery = searchQuery
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Page content empty.",
                            fontSize = (14 * scaleFactor).sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDocumentElement(
    element: com.simats.intellifile.data.manager.DocumentElement,
    scaleFactor: Float,
    searchQuery: String,
    onZoomImage: (com.simats.intellifile.data.manager.DocumentImage) -> Unit
) {
    when (element.type) {
        com.simats.intellifile.data.manager.DocumentElementType.TITLE -> {
            Text(
                text = element.text,
                fontSize = (22 * scaleFactor).sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF183A3C),
                lineHeight = (26 * scaleFactor).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            )
        }

        com.simats.intellifile.data.manager.DocumentElementType.SUBTITLE -> {
            Text(
                text = element.text,
                fontSize = (15 * scaleFactor).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF537779),
                lineHeight = (20 * scaleFactor).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }

        com.simats.intellifile.data.manager.DocumentElementType.HEADING_1 -> {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = element.text,
                fontSize = (18 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF55A9AF),
                lineHeight = (22 * scaleFactor).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }

        com.simats.intellifile.data.manager.DocumentElementType.HEADING_2 -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = element.text,
                fontSize = (16 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF183A3C),
                lineHeight = (20 * scaleFactor).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        com.simats.intellifile.data.manager.DocumentElementType.HEADING_3 -> {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = element.text,
                fontSize = (15 * scaleFactor).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF183A3C),
                lineHeight = (19 * scaleFactor).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        com.simats.intellifile.data.manager.DocumentElementType.LIST_ITEM -> {
            val isMatch = searchQuery.isNotBlank() && element.text.contains(searchQuery, ignoreCase = true)
            val bullet = if (element.bullet.isNotBlank()) element.bullet else "•"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isMatch) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier)
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = bullet,
                    fontSize = (14 * scaleFactor).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF55A9AF),
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                )
                Text(
                    text = element.text.removePrefix("•").removePrefix("-").trim(),
                    fontSize = (14 * scaleFactor).sp,
                    color = Color(0xFF1E293B),
                    lineHeight = (20 * scaleFactor).sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        com.simats.intellifile.data.manager.DocumentElementType.QUOTE,
        com.simats.intellifile.data.manager.DocumentElementType.CALLOUT -> {
            val isMatch = searchQuery.isNotBlank() && element.text.contains(searchQuery, ignoreCase = true)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0FDFA))
                    .border(
                        BorderStroke(1.dp, Color(0xFFCCFBF1)),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isMatch) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f)) else Modifier)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF55A9AF))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = element.text,
                        fontSize = (13.5f * scaleFactor).sp,
                        color = Color(0xFF134E4A),
                        lineHeight = (19 * scaleFactor).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        com.simats.intellifile.data.manager.DocumentElementType.TABLE -> {
            val table = element.table
            if (table != null && table.rows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                DocumentTableView(table = table, scaleFactor = scaleFactor, searchQuery = searchQuery)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        com.simats.intellifile.data.manager.DocumentElementType.IMAGE -> {
            val img = element.image
            if (img != null && img.bytes.isNotEmpty()) {
                val bmp = remember(img) {
                    try {
                        BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)
                    } catch (_: Exception) { null }
                }
                if (bmp != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onZoomImage(img) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = img.caption,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit
                            )
                            if (img.caption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = img.caption,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        com.simats.intellifile.data.manager.DocumentElementType.DIVIDER -> {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
        }

        else -> {
            RenderPlainParagraph(
                text = element.text,
                scaleFactor = scaleFactor,
                searchQuery = searchQuery
            )
        }
    }
}

@Composable
private fun RenderPlainParagraph(
    text: String,
    scaleFactor: Float,
    searchQuery: String
) {
    val isMatch = searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)
    val bodySize = (14.5f * scaleFactor).sp

    Text(
        text = text,
        fontSize = bodySize,
        color = Color(0xFF1E293B),
        lineHeight = (bodySize.value * 1.55f).sp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isMatch) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun DocumentTableView(
    table: com.simats.intellifile.data.manager.DocumentTable,
    scaleFactor: Float,
    searchQuery: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                table.rows.forEachIndexed { rowIdx, rowCells ->
                    val isHeader = rowIdx == 0 && table.hasHeader
                    val rowBg = when {
                        isHeader -> Color(0xFFF1F5F9)
                        rowIdx % 2 == 1 -> Color(0xFFF8FAFC)
                        else -> Color.White
                    }

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .border(BorderStroke(0.5.dp, Color(0xFFE2E8F0))),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowCells.forEachIndexed { colIdx, cellText ->
                            val isMatch = searchQuery.isNotBlank() && cellText.contains(searchQuery, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 90.dp, max = 220.dp)
                                    .border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)))
                                    .then(if (isMatch) Modifier.background(Color(0xFFFEF08A).copy(alpha = 0.6f)) else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = cellText,
                                    fontSize = if (isHeader) (12 * scaleFactor).sp else (11.5f * scaleFactor).sp,
                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHeader) Color(0xFF0F172A) else Color(0xFF334155),
                                    lineHeight = (15 * scaleFactor).sp
                                )
                            }
                        }
                    }
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

/**
 * Internal wrapper class around [PdfRenderer] to isolate android.graphics.pdf.PdfRenderer references.
 * This prevents ClassNotFoundException / NoClassDefFoundError during Compose Preview layoutlib reflection.
 */
private class PdfRendererWrapper(pfd: ParcelFileDescriptor) : AutoCloseable {
    private val renderer = PdfRenderer(pfd)

    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(pageIndex: Int, scale: Float): Bitmap {
        val page = renderer.openPage(pageIndex)
        val targetWidth = (page.width * scale).toInt().coerceIn(200, 3200)
        val targetHeight = (page.height * scale).toInt().coerceIn(200, 4800)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    override fun close() {
        try { renderer.close() } catch (_: Exception) {}
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentViewerScreenPreview() {
    DocumentViewerScreen()
}
