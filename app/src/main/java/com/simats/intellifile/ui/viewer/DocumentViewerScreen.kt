package com.simats.intellifile.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Delete
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
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.simats.intellifile.data.repository.DocumentRepository
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.ImageType
import kotlinx.coroutines.launch
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
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPreview = LocalInspectionMode.current
    val key = if (filePath.isNotBlank()) filePath else fileUriString

    LaunchedEffect(key, fileName) {
        if (key.isNotBlank()) {
            LocalFileDatabaseHelper.getInstance(context).recordFileOpened(key, fileName)
        }
    }

    // Search bar state
    var isSearchActive by remember(filePath, fileUriString) { mutableStateOf(false) }
    var searchQuery by remember(filePath, fileUriString) { mutableStateOf("") }

    val toggleOrientation: () -> Unit = {
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    DisposableEffect(filePath, fileUriString) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isSearchActive = false
            searchQuery = ""
        }
    }

    // Immersive Full-Screen controls visibility
    var areControlsVisible by remember { mutableStateOf(true) }

    BackHandler {
        if (!areControlsVisible) {
            areControlsVisible = true
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onBackClick()
        }
    }

    val effectiveFormat = remember(fileName, fileFormat) {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        if (ext.isNotBlank()) ext else fileFormat.uppercase().substringBefore(" ")
    }

    val isPdf = remember(effectiveFormat) { effectiveFormat == "PDF" }

    val isWordDoc = remember(effectiveFormat, fileName) {
        val ext = fileName.substringAfterLast('.', "").uppercase().ifBlank { effectiveFormat.uppercase() }
        ext in listOf("DOC", "DOCX", "ODT", "RTF", "DOT", "DOTX")
    }

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
    var isBookmarked by remember(key) {
        mutableStateOf(
            if (!isPreview && key.isNotBlank()) LocalFileDatabaseHelper.getInstance(context).isBookmarked(key) else false
        )
    }
    var isSaved by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Jump dialog state
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

    // PDF state
    var pdfRenderer by remember { mutableStateOf<PdfRendererWrapper?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Password Protected PDF state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isUnlocking by remember { mutableStateOf(false) }
    var currentPasswordProtectedFile by remember { mutableStateOf<File?>(null) }

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
                    try {
                        if (!PDFBoxResourceLoader.isReady()) {
                            PDFBoxResourceLoader.init(context)
                        }
                    } catch (_: Exception) {}

                    val seekableFile = LocalFileManager.prepareSeekablePdfFile(context, fileUriString, filePath)
                    var renderer: PdfRendererWrapper? = null
                    var pfd: ParcelFileDescriptor? = null
                    var requiresPassword = false

                    // Strategy 1: Fast Native Android PdfRenderer
                    if (seekableFile != null && seekableFile.exists() && seekableFile.length() > 0) {
                        try {
                            val openPfd = ParcelFileDescriptor.open(seekableFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = NativePdfRendererWrapper(openPfd)
                            pfd = openPfd
                        } catch (secEx: SecurityException) {
                            requiresPassword = true
                        } catch (e: Exception) {
                            try { pfd?.close() } catch (_: Exception) {}
                            pfd = null
                            renderer = null
                        }
                    }

                    // Strategy 2: Robust Fallback with Apache PDFBox
                    if (renderer == null && seekableFile != null && seekableFile.exists() && !requiresPassword) {
                        try {
                            val pdDoc = PDDocument.load(seekableFile)
                            if (pdDoc.isEncrypted) {
                                requiresPassword = true
                                pdDoc.close()
                            } else {
                                renderer = PdfBoxRendererWrapper(pdDoc)
                            }
                        } catch (secEx: SecurityException) {
                            requiresPassword = true
                        } catch (invEx: InvalidPasswordException) {
                            requiresPassword = true
                        } catch (e: Exception) {
                            val msg = e.localizedMessage ?: ""
                            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                                requiresPassword = true
                            }
                        }
                    }

                    // Strategy 3: Direct Stream with PDFBox if seekableFile was not created
                    if (renderer == null && !requiresPassword) {
                        try {
                            LocalFileManager.openInputStream(context, fileUriString, filePath)?.use { input ->
                                val pdDoc = PDDocument.load(input)
                                if (pdDoc.isEncrypted) {
                                    requiresPassword = true
                                    pdDoc.close()
                                } else {
                                    renderer = PdfBoxRendererWrapper(pdDoc)
                                }
                            }
                        } catch (invEx: InvalidPasswordException) {
                            requiresPassword = true
                        } catch (e: Exception) {
                            val msg = e.localizedMessage ?: ""
                            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                                requiresPassword = true
                            }
                        }
                    }

                    if (requiresPassword) {
                        currentPasswordProtectedFile = seekableFile
                        showPasswordDialog = true
                        isLoading = false
                        return@withContext
                    }

                    val activeRenderer = renderer
                    if (activeRenderer != null) {
                        parcelFileDescriptor = pfd
                        pdfRenderer = activeRenderer
                        totalUnitsCount = activeRenderer.pageCount.coerceAtLeast(1)
                        currentPageIndex = 1
                    } else {
                        errorMessage = "Cannot access PDF file. Please ensure storage permission is enabled."
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val msg = e.localizedMessage ?: ""
                    if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                        showPasswordDialog = true
                    } else {
                        errorMessage = "Unable to render PDF: ${e.localizedMessage ?: "File may be corrupted."}"
                    }
                } finally {
                    isLoading = false
                }
            }
        } else if (isWordDoc) {
            withContext(Dispatchers.IO) {
                try {
                    // Strategy 1: High-fidelity LibreOffice conversion to PDF via backend
                    val docRepo = DocumentRepository(context)
                    val conversionResult = docRepo.convertWordToPdf(
                        documentId = documentId,
                        filePath = filePath.ifBlank { null },
                        uriString = fileUriString.ifBlank { null },
                        fileName = fileName
                    )

                    var renderer: PdfRendererWrapper? = null
                    var pfd: ParcelFileDescriptor? = null

                    if (conversionResult.isSuccess) {
                        val convertedPdf = conversionResult.getOrNull()
                        if (convertedPdf != null && convertedPdf.exists() && convertedPdf.length() > 0) {
                            try {
                                pfd = ParcelFileDescriptor.open(convertedPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                                renderer = NativePdfRendererWrapper(pfd)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                try { pfd?.close() } catch (_: Exception) {}
                                pfd = null
                                renderer = null
                            }
                        }
                    }

                    val activeWordRenderer = renderer
                    if (activeWordRenderer != null && pfd != null) {
                        parcelFileDescriptor = pfd
                        pdfRenderer = activeWordRenderer
                        totalUnitsCount = activeWordRenderer.pageCount.coerceAtLeast(1)
                        currentPageIndex = 1

                        // Also parse document text in background for search metadata if available
                        try {
                            val parsed = DocumentContentParser.parse(
                                context = context,
                                uriString = fileUriString,
                                path = filePath,
                                format = effectiveFormat,
                                fileName = fileName
                            )
                            if (parsed is ParsedDocumentContent.RichDocument) {
                                parsedContent = parsed
                            }
                        } catch (_: Exception) {}
                    } else {
                        // Strategy 2: Graceful offline fallback to local DocumentContentParser
                        val result = DocumentContentParser.parse(
                            context = context,
                            uriString = fileUriString,
                            path = filePath,
                            format = effectiveFormat,
                            fileName = fileName
                        )
                        parsedContent = result
                        when (result) {
                            is ParsedDocumentContent.RichDocument -> {
                                totalUnitsCount = result.totalPages.coerceAtLeast(1)
                                currentPageIndex = 1
                            }
                            is ParsedDocumentContent.Error -> {
                                errorMessage = result.message
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        val result = DocumentContentParser.parse(
                            context = context,
                            uriString = fileUriString,
                            path = filePath,
                            format = effectiveFormat,
                            fileName = fileName
                        )
                        parsedContent = result
                        if (result is ParsedDocumentContent.RichDocument) {
                            totalUnitsCount = result.totalPages.coerceAtLeast(1)
                            currentPageIndex = 1
                        }
                    } catch (fallbackEx: Exception) {
                        errorMessage = "Error opening document: ${e.localizedMessage}"
                    }
                } finally {
                    isLoading = false
                }
            }
        } else {
            // Parse non-PDF documents: PPTX, PPT, XLSX, XLS, CSV, TXT, JSON, MD
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
                        is ParsedDocumentContent.LogDocument -> {
                            totalUnitsCount = result.lines.size.coerceAtLeast(1)
                            currentPageIndex = 1
                        }
                        is ParsedDocumentContent.ArchiveDocument -> {
                            totalUnitsCount = result.entries.size.coerceAtLeast(1)
                            currentPageIndex = 1
                        }
                        is ParsedDocumentContent.HtmlDocument,
                        is ParsedDocumentContent.MarkdownDocument,
                        is ParsedDocumentContent.StructuredData,
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

    // Password Protected PDF Dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                if (pdfRenderer == null) {
                    errorMessage = "This document is password protected. Enter password to view."
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF00A896),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Password Protected PDF",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "This PDF file is encrypted. Please enter the password to view its contents.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        singleLine = true,
                        label = { Text("Password") },
                        placeholder = { Text("Enter document password") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        },
                        isError = passwordError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00A896),
                            focusedLabelColor = Color(0xFF00A896),
                            cursorColor = Color(0xFF00A896)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isBlank()) {
                            passwordError = "Please enter the password"
                            return@Button
                        }
                        val file = currentPasswordProtectedFile ?: return@Button
                        isUnlocking = true
                        passwordError = null
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                if (!PDFBoxResourceLoader.isReady()) {
                                    PDFBoxResourceLoader.init(context)
                                }
                                val pdDoc = PDDocument.load(file, passwordInput)
                                val boxRenderer = PdfBoxRendererWrapper(pdDoc)
                                withContext(Dispatchers.Main) {
                                    try { pdfRenderer?.close() } catch (_: Exception) {}
                                    pdfRenderer = boxRenderer
                                    totalUnitsCount = boxRenderer.pageCount.coerceAtLeast(1)
                                    currentPageIndex = 1
                                    errorMessage = null
                                    showPasswordDialog = false
                                    isUnlocking = false
                                    passwordInput = ""
                                }
                            } catch (inv: InvalidPasswordException) {
                                withContext(Dispatchers.Main) {
                                    passwordError = "Incorrect password. Please try again."
                                    isUnlocking = false
                                }
                            } catch (e: Exception) {
                                val msg = e.localizedMessage ?: ""
                                withContext(Dispatchers.Main) {
                                    if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                                        passwordError = "Incorrect password. Please try again."
                                    } else {
                                        passwordError = "Failed to unlock: $msg"
                                    }
                                    isUnlocking = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A896)),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isUnlocking
                ) {
                    if (isUnlocking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlocking...")
                    } else {
                        Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        if (pdfRenderer == null) {
                            errorMessage = "Password required to view this PDF."
                        }
                    },
                    enabled = !isUnlocking
                ) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
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
                    .background(Color(0xFFF8FAFC))
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
                        Text("Image could not be rendered", color = Color(0xFF64748B))
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
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { zoomTargetDocumentImage = null },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1E293B))
                    }
                }
            }
        }
    }

    // Confirmation Dialog for deleting file
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Delete File?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"$fileName\" from your device? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        val deleted = LocalFileManager.deleteLocalFile(context, filePath, fileUriString)
                        if (deleted) {
                            Toast.makeText(context, "\"$fileName\" deleted", Toast.LENGTH_SHORT).show()
                            onBackClick()
                        } else {
                            Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
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
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(220)) + shrinkVertically(animationSpec = tween(220))
            ) {
                if (isSearchActive) {
                val matchingUnits = remember(parsedContent, searchQuery) {
                    if (searchQuery.isBlank()) emptyList<Int>()
                    else when (val content = parsedContent) {
                        is ParsedDocumentContent.Presentation -> {
                            content.slides.filter { s ->
                                s.title.contains(searchQuery, ignoreCase = true) ||
                                        s.subtitle.contains(searchQuery, ignoreCase = true) ||
                                        s.points.any { it.contains(searchQuery, ignoreCase = true) } ||
                                        s.rawContent.contains(searchQuery, ignoreCase = true) ||
                                        s.notes.contains(searchQuery, ignoreCase = true)
                            }.map { it.slideNumber }
                        }
                        is ParsedDocumentContent.RichDocument -> {
                            content.pages.filter { p ->
                                p.paragraphs.any { it.contains(searchQuery, ignoreCase = true) } ||
                                        p.elements.any { it.text.contains(searchQuery, ignoreCase = true) }
                            }.map { it.pageNumber }
                        }
                        is ParsedDocumentContent.Spreadsheet -> {
                            val matches = mutableListOf<Int>()
                            content.rows.forEachIndexed { idx, row ->
                                if (row.any { it.contains(searchQuery, ignoreCase = true) }) {
                                    matches.add(idx + 1)
                                }
                            }
                            matches
                        }
                        is ParsedDocumentContent.LogDocument -> {
                            content.lines.filter {
                                it.rawLine.contains(searchQuery, ignoreCase = true)
                            }.map { it.lineNumber }
                        }
                        else -> emptyList()
                    }
                }

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
                        placeholder = { Text("Search inside document...", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF55A9AF),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            cursorColor = Color(0xFF55A9AF)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF64748B))
                                }
                            }
                        }
                    )

                    if (searchQuery.isNotBlank() && matchingUnits.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${matchingUnits.size} match",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF55A9AF)
                        )

                        if (matchingUnits.size > 1) {
                            IconButton(onClick = {
                                val curIdx = matchingUnits.indexOf(currentPageIndex)
                                val prevIdx = if (curIdx <= 0) matchingUnits.lastIndex else curIdx - 1
                                currentPageIndex = matchingUnits[prevIdx]
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Match",
                                    tint = Color(0xFF55A9AF)
                                )
                            }
                            IconButton(onClick = {
                                val curIdx = matchingUnits.indexOf(currentPageIndex)
                                val nextIdx = if (curIdx < 0 || curIdx >= matchingUnits.lastIndex) 0 else curIdx + 1
                                currentPageIndex = matchingUnits[nextIdx]
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Match",
                                    tint = Color(0xFF55A9AF)
                                )
                            }
                        }
                    }
                }
            } else {
                val richWordCount = remember(parsedContent) {
                    when (val content = parsedContent) {
                        is ParsedDocumentContent.RichDocument -> content.wordCount.takeIf { it > 0 }
                        is ParsedDocumentContent.PlainText -> content.text.trim().split("\\s+".toRegex()).size.takeIf { it > 0 }
                        else -> null
                    }
                }

                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    onBackClick()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            DocumentInfoSection(
                                fileName = fileName,
                                fileFormat = effectiveFormat,
                                currentPage = currentPageIndex,
                                totalPages = totalUnitsCount,
                                wordCount = richWordCount,
                                isCompact = true,
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (parsedContent is ParsedDocumentContent.Presentation || effectiveFormat.uppercase() in listOf("PPT", "PPTX")) {
                                    IconButton(onClick = { openWithExternalApp() }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open in PowerPoint / Office App",
                                            tint = Color(0xFF55A9AF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(onClick = { isFullscreenSlideshow = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Slideshow,
                                            contentDescription = "Fullscreen Slideshow",
                                            tint = Color(0xFF111827),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (isWordDoc || parsedContent is ParsedDocumentContent.RichDocument || effectiveFormat.uppercase() in listOf("DOC", "DOCX", "RTF")) {
                                    IconButton(onClick = { openWithExternalApp() }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open in Word / Office App",
                                            tint = Color(0xFF55A9AF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(onClick = {
                                        wordDocViewMode = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) WordDocViewMode.CONTINUOUS else WordDocViewMode.PAGE_VIEW
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) "Switch to Continuous Reading" else "Switch to Page View",
                                            tint = Color(0xFF111827),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                IconButton(onClick = { isSearchActive = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFF111827),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = toggleOrientation, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Orientation",
                                        tint = if (isLandscape) Color(0xFF55A9AF) else Color(0xFF111827),
                                        modifier = Modifier.size(20.dp)
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
                                }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) Color(0xFF6C5CE7) else Color(0xFF111827),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color(0xFF111827),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete File",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Portrait Mode: 2-Tier Layout ensuring Document Information has full width and is never squeezed
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        onBackClick()
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (parsedContent is ParsedDocumentContent.Presentation || effectiveFormat.uppercase() in listOf("PPT", "PPTX")) {
                                        IconButton(onClick = { openWithExternalApp() }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open in PowerPoint / Office App",
                                                tint = Color(0xFF55A9AF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(onClick = { isFullscreenSlideshow = true }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.Slideshow,
                                                contentDescription = "Fullscreen Slideshow",
                                                tint = Color(0xFF111827),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    if (isWordDoc || parsedContent is ParsedDocumentContent.RichDocument || effectiveFormat.uppercase() in listOf("DOC", "DOCX", "RTF")) {
                                        IconButton(onClick = { openWithExternalApp() }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open in Word / Office App",
                                                tint = Color(0xFF55A9AF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            wordDocViewMode = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) WordDocViewMode.CONTINUOUS else WordDocViewMode.PAGE_VIEW
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                imageVector = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = if (wordDocViewMode == WordDocViewMode.PAGE_VIEW) "Switch to Continuous Reading" else "Switch to Page View",
                                                tint = Color(0xFF111827),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    IconButton(onClick = { isSearchActive = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = Color(0xFF111827),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(onClick = toggleOrientation, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.ScreenRotation,
                                            contentDescription = "Rotate Orientation",
                                            tint = if (isLandscape) Color(0xFF55A9AF) else Color(0xFF111827),
                                            modifier = Modifier.size(20.dp)
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
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) Color(0xFF6C5CE7) else Color(0xFF111827),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color(0xFF111827),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete File",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Document Information Section: Full Width, properly spaced, readable font, no clipping
                            DocumentInfoSection(
                                fileName = fileName,
                                fileFormat = effectiveFormat,
                                currentPage = currentPageIndex,
                                totalPages = totalUnitsCount,
                                wordCount = richWordCount,
                                isCompact = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

            // Main Viewport Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (!areControlsVisible) 0.dp else if (isLandscape) 4.dp else 16.dp),
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

                    isPdf || (isWordDoc && pdfRenderer != null && wordDocViewMode == WordDocViewMode.PAGE_VIEW) -> {
                        if (pageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                areControlsVisible = !areControlsVisible
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
                                            }
                                        )
                                    }
                                    .pointerInput(currentPageIndex) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            var prevDist = 0f
                                            var prevCentroid = Offset.Zero
                                            var totalDragX = 0f
                                            var totalDragY = 0f
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
                                                } else if (pressed.size == 1) {
                                                    val change = pressed[0]
                                                    val panDelta = change.positionChange()
                                                    if (docGestureScale > 1.05f) {
                                                        // Single-finger drag pan when zoomed into PDF/Word
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
                                                    } else {
                                                        // Track horizontal drag for page swipe when scale is 1.0
                                                        totalDragX += panDelta.x
                                                        totalDragY += panDelta.y
                                                    }
                                                    prevDist = 0f
                                                } else {
                                                    prevDist = 0f
                                                }
                                            } while (pressed.isNotEmpty())

                                            // Check swipe navigation on finger release
                                            if (docGestureScale <= 1.05f && kotlin.math.abs(totalDragX) > 80f && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.5f) {
                                                if (totalDragX < 0 && currentPageIndex < totalUnitsCount) {
                                                    // Swiping from right to left -> Next page
                                                    currentPageIndex++
                                                } else if (totalDragX > 0 && currentPageIndex > 1) {
                                                    // Swiping from left to right -> Previous page
                                                    currentPageIndex--
                                                }
                                            }
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

                    isWordDoc && pdfRenderer != null && wordDocViewMode == WordDocViewMode.CONTINUOUS -> {
                        WordPdfContinuousView(
                            renderer = pdfRenderer!!,
                            totalPages = totalUnitsCount,
                            currentPageIndex = currentPageIndex,
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
                            onToggleControls = { areControlsVisible = !areControlsVisible },
                            onPageChanged = { newPage ->
                                currentPageIndex = newPage
                            }
                        )
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
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
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
                                onToggleControls = { areControlsVisible = !areControlsVisible },
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
                                onToggleControls = { areControlsVisible = !areControlsVisible },
                                searchQuery = searchQuery,
                                onPrevPage = { if (currentPageIndex > 1) currentPageIndex-- },
                                onNextPage = { if (currentPageIndex < totalUnitsCount) currentPageIndex++ },
                                onZoomImage = { zoomTargetDocumentImage = it }
                            )
                        }
                    }

                    // Plain Text View (TXT, code files)
                    parsedContent is ParsedDocumentContent.PlainText -> {
                        val textDoc = parsedContent as ParsedDocumentContent.PlainText
                        PlainTextViewer(
                            textDoc = textDoc,
                            fileFormat = effectiveFormat,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
                        )
                    }

                    // HTML / HTM Web Document View
                    parsedContent is ParsedDocumentContent.HtmlDocument -> {
                        HtmlDocumentViewer(
                            htmlDoc = parsedContent as ParsedDocumentContent.HtmlDocument,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
                        )
                    }

                    // Markdown (MD) Formatted View
                    parsedContent is ParsedDocumentContent.MarkdownDocument -> {
                        MarkdownDocumentViewer(
                            markdownDoc = parsedContent as ParsedDocumentContent.MarkdownDocument,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
                        )
                    }

                    // Structured Data View (JSON & XML)
                    parsedContent is ParsedDocumentContent.StructuredData -> {
                        StructuredCodeViewer(
                            codeDoc = parsedContent as ParsedDocumentContent.StructuredData,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
                        )
                    }

                    // Log Document View (LOG)
                    parsedContent is ParsedDocumentContent.LogDocument -> {
                        LogDocumentViewer(
                            logDoc = parsedContent as ParsedDocumentContent.LogDocument,
                            zoomPercentage = zoomPercentage,
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
                        )
                    }

                    // Zip Archive Contents View (ZIP)
                    parsedContent is ParsedDocumentContent.ArchiveDocument -> {
                        ZipArchiveViewer(
                            archiveDoc = parsedContent as ParsedDocumentContent.ArchiveDocument,
                            searchQuery = searchQuery,
                            onToggleControls = { areControlsVisible = !areControlsVisible }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(220)) + shrinkVertically(animationSpec = tween(220))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Unified Bottom Indicator & Zoom Bar
                    val unitName = when {
                        parsedContent is ParsedDocumentContent.Presentation -> "Slide"
                        parsedContent is ParsedDocumentContent.Spreadsheet -> "Row"
                        parsedContent is ParsedDocumentContent.LogDocument -> "Line"
                        parsedContent is ParsedDocumentContent.ArchiveDocument -> "Item"
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
                            if (wordDocViewMode == WordDocViewMode.CONTINUOUS && (isWordDoc || parsedContent is ParsedDocumentContent.RichDocument)) {
                                Text(
                                    text = "Page $currentPageIndex of $totalUnitsCount",
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

                    Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 10.dp))

                    // Bottom Action Bar
                    DocumentBottomActionBar(
                        isLandscape = isLandscape,
                        onThumbnailClick = {
                            if (totalUnitsCount > 1) {
                                showJumpDialog = true
                            } else {
                                onThumbnailClick()
                            }
                        },
                        onShareClick = onShareClick
                    )
                }
            }
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
                .background(Color(0xFFF8FAFC))
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
                        color = Color(0xFF1E293B),
                        maxLines = 1
                    )
                    Text(
                        text = "Pinch to zoom (up to 5x) · Drag to pan diagram",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(5f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFF1E293B))
                    }
                    IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF1E293B))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1E293B))
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
// 2. SPREADSHEET TABLE COMPOSABLE (XLS/XLSX/CSV/TSV)
// ==========================================
@Composable
private fun SpreadsheetTableView(
    spreadsheet: ParsedDocumentContent.Spreadsheet,
    zoomPercentage: Int,
    searchQuery: String,
    onToggleControls: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeSheetIndex by remember(spreadsheet) { mutableIntStateOf(spreadsheet.activeSheetIndex) }
    var selectedCell by remember(activeSheetIndex) { mutableStateOf<Pair<Int, Int>?>(null) }

    val currentSheet = remember(spreadsheet, activeSheetIndex) {
        spreadsheet.sheets.getOrNull(activeSheetIndex)
    }
    val sheetHeaders = currentSheet?.headers ?: spreadsheet.headers
    val sheetRows = currentSheet?.rows ?: spreadsheet.rows

    val scaleFactor = zoomPercentage / 100f
    val textSize = (13 * scaleFactor).sp
    val cellPaddingH = (14 * scaleFactor).dp
    val cellPaddingV = (10 * scaleFactor).dp

    val filteredRows = remember(sheetRows, searchQuery) {
        if (searchQuery.isBlank()) {
            sheetRows
        } else {
            sheetRows.filter { row ->
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
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleControls() })
            }
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
                    text = "${currentSheet?.name ?: "Sheet"} · ${sheetHeaders.size} Cols · ${filteredRows.size} Rows",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1)
                )
            }

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Matches for \"$searchQuery\"",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Multi-Sheet Tabs Bar (if 2 or more sheets exist)
        if (spreadsheet.sheets.size > 1) {
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                spreadsheet.sheets.forEachIndexed { sIdx, s ->
                    val isSelected = sIdx == activeSheetIndex
                    Surface(
                        color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(6.dp),
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFFCBD5E1)) else null,
                        modifier = Modifier.clickable {
                            activeSheetIndex = sIdx
                            selectedCell = null
                        }
                    ) {
                        Text(
                            text = s.name.ifBlank { "Sheet ${sIdx + 1}" },
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Cell Inspection Bar (When a cell is selected)
        if (selectedCell != null) {
            val rIdx = selectedCell!!.first
            val cIdx = selectedCell!!.second
            val cellVal = filteredRows.getOrNull(rIdx)?.getOrNull(cIdx) ?: ""
            val colLetter = if (cIdx in 0..25) "${('A'.code + cIdx).toChar()}" else "C${cIdx + 1}"
            val cellCoord = "$colLetter${rIdx + 1}"

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF0FDF4))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = cellCoord,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cellVal.ifBlank { "(Empty)" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE2E8F0))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Cell Value", cellVal))
                            Toast.makeText(context, "Copied cell value", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Copy", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                }
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

                        sheetHeaders.forEach { header ->
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

                        sheetHeaders.indices.forEach { colIdx ->
                            val cellVal = row.getOrNull(colIdx) ?: ""
                            val isMatch = searchQuery.isNotBlank() && cellVal.contains(searchQuery, ignoreCase = true)
                            val isSelected = selectedCell?.first == index && selectedCell?.second == colIdx

                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .then(
                                        when {
                                            isSelected -> Modifier
                                                .background(Color(0xFFEFF6FF))
                                                .border(2.dp, Color(0xFF0284C7))
                                            isMatch -> Modifier
                                                .background(Color(0xFFFEF08A))
                                                .border(0.5.dp, Color(0xFFF1F5F9))
                                            else -> Modifier.border(0.5.dp, Color(0xFFF1F5F9))
                                        }
                                    )
                                    .clickable {
                                        selectedCell = Pair(index, colIdx)
                                    }
                                    .padding(horizontal = cellPaddingH, vertical = cellPaddingV)
                            ) {
                                Text(
                                    text = cellVal,
                                    fontSize = textSize,
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF334155),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 3
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
// 2B. HTML WEB DOCUMENT VIEWER
// ==========================================
@Composable
private fun HtmlDocumentViewer(
    htmlDoc: ParsedDocumentContent.HtmlDocument,
    zoomPercentage: Int,
    searchQuery: String,
    onToggleControls: () -> Unit = {}
) {
    var isSourceMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFFFF7ED),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "HTML",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSourceMode) "Source Code" else "Rendered Web Preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isSourceMode) Color.White else Color.Transparent)
                        .clickable { isSourceMode = false }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Preview",
                        fontSize = 11.sp,
                        fontWeight = if (!isSourceMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isSourceMode) Color(0xFF0F172A) else Color(0xFF64748B)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSourceMode) Color.White else Color.Transparent)
                        .clickable { isSourceMode = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Source",
                        fontSize = 11.sp,
                        fontWeight = if (isSourceMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSourceMode) Color(0xFF0F172A) else Color(0xFF64748B)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        if (!isSourceMode) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.textZoom = zoomPercentage.coerceIn(50, 300)
                        setBackgroundColor(android.graphics.Color.WHITE)
                        loadDataWithBaseURL(null, htmlDoc.htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.settings.textZoom = zoomPercentage.coerceIn(50, 300)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleControls() })
                    }
            )
        } else {
            PlainTextViewer(
                textDoc = ParsedDocumentContent.PlainText(
                    title = htmlDoc.title,
                    text = htmlDoc.rawSource,
                    linesCount = htmlDoc.rawSource.lines().size
                ),
                fileFormat = "HTML",
                zoomPercentage = zoomPercentage,
                searchQuery = searchQuery,
                onToggleControls = onToggleControls
            )
        }
    }
}

// ==========================================
// 2C. MARKDOWN DOCUMENT VIEWER
// ==========================================
private sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val index: Int, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0
    val total = lines.size

    while (i < total) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> {
                i++
            }
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < total && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < total) i++
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
                i++
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
                i++
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
                i++
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks.add(MarkdownBlock.Divider)
                i++
            }
            trimmed.startsWith("> ") -> {
                blocks.add(MarkdownBlock.Blockquote(trimmed.removePrefix("> ").trim()))
                i++
            }
            trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2).trim()))
                i++
            }
            Regex("""^\d+\.\s""").containsMatchIn(trimmed) -> {
                val num = trimmed.substringBefore('.').toIntOrNull() ?: 1
                val content = trimmed.substringAfter(". ").trim()
                blocks.add(MarkdownBlock.NumberedItem(num, content))
                i++
            }
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < total && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.isNotEmpty()) {
                    val headerLine = tableLines.first()
                    val headers = headerLine.split("|").filter { it.isNotBlank() }.map { it.trim() }
                    val dataRows = tableLines.drop(1).filter { !it.contains("---") }.map { r ->
                        r.split("|").filter { it.isNotBlank() }.map { it.trim() }
                    }
                    blocks.add(MarkdownBlock.Table(headers, dataRows))
                }
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(trimmed))
                i++
            }
        }
    }
    return blocks
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    scaleFactor: Float,
    searchQuery: String,
    context: Context
) {
    val isMatch = searchQuery.isNotBlank() && when (block) {
        is MarkdownBlock.Header -> block.text.contains(searchQuery, ignoreCase = true)
        is MarkdownBlock.Paragraph -> block.text.contains(searchQuery, ignoreCase = true)
        is MarkdownBlock.BulletItem -> block.text.contains(searchQuery, ignoreCase = true)
        is MarkdownBlock.NumberedItem -> block.text.contains(searchQuery, ignoreCase = true)
        is MarkdownBlock.Blockquote -> block.text.contains(searchQuery, ignoreCase = true)
        is MarkdownBlock.CodeBlock -> block.code.contains(searchQuery, ignoreCase = true)
        else -> false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier)
    ) {
        when (block) {
            is MarkdownBlock.Header -> {
                Column {
                    Text(
                        text = block.text,
                        fontSize = when (block.level) {
                            1 -> (22 * scaleFactor).sp
                            2 -> (18 * scaleFactor).sp
                            else -> (15 * scaleFactor).sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (block.level == 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    }
                }
            }
            is MarkdownBlock.Paragraph -> {
                Text(
                    text = block.text,
                    fontSize = (13 * scaleFactor).sp,
                    color = Color(0xFF334155),
                    lineHeight = (19 * scaleFactor).sp
                )
            }
            is MarkdownBlock.BulletItem -> {
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = "•", fontSize = (14 * scaleFactor).sp, color = Color(0xFF55A9AF), modifier = Modifier.padding(end = 8.dp))
                    Text(text = block.text, fontSize = (13 * scaleFactor).sp, color = Color(0xFF334155))
                }
            }
            is MarkdownBlock.NumberedItem -> {
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = "${block.index}.", fontSize = (13 * scaleFactor).sp, fontWeight = FontWeight.Bold, color = Color(0xFF55A9AF), modifier = Modifier.padding(end = 8.dp))
                    Text(text = block.text, fontSize = (13 * scaleFactor).sp, color = Color(0xFF334155))
                }
            }
            is MarkdownBlock.Blockquote -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(BorderStroke(3.dp, Color(0xFF55A9AF)), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = block.text,
                        fontSize = (13 * scaleFactor).sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color(0xFF475569)
                    )
                }
            }
            is MarkdownBlock.CodeBlock -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(12.dp)
                ) {
                    if (block.language.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = block.language.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Code", block.code))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = block.code,
                        fontSize = (12 * scaleFactor).sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
            is MarkdownBlock.Table -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                ) {
                    Row(modifier = Modifier.background(Color(0xFFF1F5F9))) {
                        block.headers.forEach { h ->
                            Text(
                                text = h,
                                fontSize = (12 * scaleFactor).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .width(130.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                    block.rows.forEachIndexed { rIdx, row ->
                        Row(modifier = Modifier.background(if (rIdx % 2 == 1) Color(0xFFF8FAFC) else Color.White)) {
                            block.headers.indices.forEach { colIdx ->
                                Text(
                                    text = row.getOrNull(colIdx) ?: "",
                                    fontSize = (12 * scaleFactor).sp,
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .width(130.dp)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
            is MarkdownBlock.Divider -> {
                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun MarkdownDocumentViewer(
    markdownDoc: ParsedDocumentContent.MarkdownDocument,
    zoomPercentage: Int,
    searchQuery: String,
    onToggleControls: () -> Unit = {}
) {
    var isRawMode by remember { mutableStateOf(false) }
    val scaleFactor = zoomPercentage / 100f
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "MARKDOWN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRawMode) "Raw Markdown" else "Formatted Preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isRawMode) Color.White else Color.Transparent)
                        .clickable { isRawMode = false }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Rendered",
                        fontSize = 11.sp,
                        fontWeight = if (!isRawMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isRawMode) Color(0xFF0F172A) else Color(0xFF64748B)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRawMode) Color.White else Color.Transparent)
                        .clickable { isRawMode = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Raw",
                        fontSize = 11.sp,
                        fontWeight = if (isRawMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isRawMode) Color(0xFF0F172A) else Color(0xFF64748B)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        if (!isRawMode) {
            val blocks = remember(markdownDoc.markdownText) {
                parseMarkdownBlocks(markdownDoc.markdownText)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleControls() })
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                blocks.forEach { block ->
                    RenderMarkdownBlock(block, scaleFactor, searchQuery, context)
                }
            }
        } else {
            PlainTextViewer(
                textDoc = ParsedDocumentContent.PlainText(
                    title = markdownDoc.title,
                    text = markdownDoc.rawSource,
                    linesCount = markdownDoc.rawSource.lines().size
                ),
                fileFormat = "MD",
                zoomPercentage = zoomPercentage,
                searchQuery = searchQuery,
                onToggleControls = onToggleControls
            )
        }
    }
}

// ==========================================
// 2D. STRUCTURED CODE VIEWER (JSON & XML)
// ==========================================
@Composable
private fun RenderSyntaxCodeLine(line: String, dataType: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    if (dataType == "JSON") {
        val keyRegex = Regex("""^(\s*)(".*?")(\s*:\s*)(.*)$""")
        val match = keyRegex.find(line)
        if (match != null) {
            val indent = match.groupValues[1]
            val key = match.groupValues[2]
            val colon = match.groupValues[3]
            val rest = match.groupValues[4]

            Row {
                if (indent.isNotEmpty()) {
                    Text(text = indent, fontSize = fontSize, fontFamily = FontFamily.Monospace)
                }
                Text(text = key, fontSize = fontSize, color = Color(0xFF0284C7), fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                Text(text = colon, fontSize = fontSize, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                val restColor = when {
                    rest.startsWith("\"") -> Color(0xFF16A34A)
                    rest.trim().startsWith("true") || rest.trim().startsWith("false") -> Color(0xFFD97706)
                    rest.trim().startsWith("null") -> Color(0xFF94A3B8)
                    rest.trim().firstOrNull()?.isDigit() == true || rest.trim().startsWith("-") -> Color(0xFFD97706)
                    else -> Color(0xFF0F172A)
                }
                Text(text = rest, fontSize = fontSize, color = restColor, fontFamily = FontFamily.Monospace)
            }
            return
        }
    } else if (dataType == "XML") {
        val tagRegex = Regex("""^(<\/?)([a-zA-Z0-9_\-:]+)(.*?)(\/?>)$""")
        val match = tagRegex.find(line.trim())
        if (match != null) {
            val indentLen = line.indexOf('<').coerceAtLeast(0)
            val indent = " ".repeat(indentLen)
            Row {
                if (indent.isNotEmpty()) {
                    Text(text = indent, fontSize = fontSize, fontFamily = FontFamily.Monospace)
                }
                Text(text = match.groupValues[1], fontSize = fontSize, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                Text(text = match.groupValues[2], fontSize = fontSize, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(text = match.groupValues[3], fontSize = fontSize, color = Color(0xFFD97706), fontFamily = FontFamily.Monospace)
                Text(text = match.groupValues[4], fontSize = fontSize, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
            }
            return
        }
    }
    Text(
        text = line,
        fontSize = fontSize,
        color = Color(0xFF0F172A),
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun StructuredCodeViewer(
    codeDoc: ParsedDocumentContent.StructuredData,
    zoomPercentage: Int,
    searchQuery: String,
    onToggleControls: () -> Unit = {}
) {
    val scaleFactor = zoomPercentage / 100f
    val bodySize = (13 * scaleFactor).sp
    val context = LocalContext.current
    val lines = remember(codeDoc.formattedContent) { codeDoc.formattedContent.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (codeDoc.dataType == "JSON") Color(0xFFE0F2FE) else Color(0xFFEDE9FE),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = codeDoc.dataType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (codeDoc.dataType == "JSON") Color(0xFF0369A1) else Color(0xFF6D28D9),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${lines.size} Lines · Formatted",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE2E8F0))
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Formatted ${codeDoc.dataType}", codeDoc.formattedContent))
                        Toast.makeText(context, "Copied ${codeDoc.dataType} to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Copy",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Copy",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        RenderSyntaxCodeLine(line, codeDoc.dataType, bodySize)
                    }
                }
            }
        }
    }
}

// ==========================================
// 2E. LOG DOCUMENT VIEWER (LOG)
// ==========================================
@Composable
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    badgeColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) badgeColor else bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else badgeColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LogDocumentViewer(
    logDoc: ParsedDocumentContent.LogDocument,
    zoomPercentage: Int,
    searchQuery: String,
    onToggleControls: () -> Unit = {}
) {
    var selectedLevelFilter by remember { mutableStateOf("ALL") }
    val scaleFactor = zoomPercentage / 100f
    val bodySize = (12 * scaleFactor).sp

    val filteredLines = remember(logDoc.lines, selectedLevelFilter, searchQuery) {
        logDoc.lines.filter { item ->
            val matchesFilter = when (selectedLevelFilter) {
                "ERROR" -> item.level in listOf("ERROR", "FATAL", "CRITICAL")
                "WARN" -> item.level in listOf("WARN", "WARNING")
                "INFO" -> item.level == "INFO"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true
            else item.rawLine.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipPill(
                label = "All (${logDoc.lines.size})",
                isSelected = selectedLevelFilter == "ALL",
                badgeColor = Color(0xFF475569),
                bgColor = Color(0xFFF1F5F9),
                onClick = { selectedLevelFilter = "ALL" }
            )

            if (logDoc.errorCount > 0) {
                FilterChipPill(
                    label = "Errors (${logDoc.errorCount})",
                    isSelected = selectedLevelFilter == "ERROR",
                    badgeColor = Color(0xFFDC2626),
                    bgColor = Color(0xFFFEE2E2),
                    onClick = { selectedLevelFilter = if (selectedLevelFilter == "ERROR") "ALL" else "ERROR" }
                )
            }

            if (logDoc.warnCount > 0) {
                FilterChipPill(
                    label = "Warn (${logDoc.warnCount})",
                    isSelected = selectedLevelFilter == "WARN",
                    badgeColor = Color(0xFFD97706),
                    bgColor = Color(0xFFFEF3C7),
                    onClick = { selectedLevelFilter = if (selectedLevelFilter == "WARN") "ALL" else "WARN" }
                )
            }

            if (logDoc.infoCount > 0) {
                FilterChipPill(
                    label = "Info (${logDoc.infoCount})",
                    isSelected = selectedLevelFilter == "INFO",
                    badgeColor = Color(0xFF2563EB),
                    bgColor = Color(0xFFEFF6FF),
                    onClick = { selectedLevelFilter = if (selectedLevelFilter == "INFO") "ALL" else "INFO" }
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                filteredLines.forEach { item ->
                    val isMatch = searchQuery.isNotBlank() && item.rawLine.contains(searchQuery, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.lineNumber}".padStart(4),
                            fontSize = (bodySize.value * 0.85f).sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        val levelBadgeBg = when (item.level) {
                            "ERROR", "FATAL", "CRITICAL" -> Color(0xFFFEE2E2)
                            "WARN", "WARNING" -> Color(0xFFFEF3C7)
                            "INFO" -> Color(0xFFEFF6FF)
                            else -> Color(0xFFF1F5F9)
                        }
                        val levelBadgeText = when (item.level) {
                            "ERROR", "FATAL", "CRITICAL" -> Color(0xFFDC2626)
                            "WARN", "WARNING" -> Color(0xFFD97706)
                            "INFO" -> Color(0xFF2563EB)
                            else -> Color(0xFF64748B)
                        }

                        Surface(
                            color = levelBadgeBg,
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = item.level.take(5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = levelBadgeText,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (item.timestamp.isNotBlank()) {
                            Text(
                                text = item.timestamp,
                                fontSize = (bodySize.value * 0.9f).sp,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        Text(
                            text = item.message,
                            fontSize = bodySize,
                            color = Color(0xFF0F172A),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2F. ZIP ARCHIVE CONTENTS VIEWER (ZIP)
// ==========================================
@Composable
private fun ZipArchiveViewer(
    archiveDoc: ParsedDocumentContent.ArchiveDocument,
    searchQuery: String,
    onToggleControls: () -> Unit = {}
) {
    val context = LocalContext.current
    var extractedFileToOpen by remember { mutableStateOf<File?>(null) }
    var extractionError by remember { mutableStateOf<String?>(null) }

    val filteredEntries = remember(archiveDoc.entries, searchQuery) {
        if (searchQuery.isBlank()) archiveDoc.entries
        else archiveDoc.entries.filter { it.path.contains(searchQuery, ignoreCase = true) }
    }

    val dirCount = remember(archiveDoc.entries) { archiveDoc.entries.count { it.isDirectory } }
    val fileCount = remember(archiveDoc.entries) { archiveDoc.entries.size - dirCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFEEF2FF),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "ZIP ARCHIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$fileCount Files · $dirCount Folders",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            }

            Text(
                text = "${LocalFileManager.formatFileSize(archiveDoc.totalSizeBytes)} total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(filteredEntries) { _, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (entry.isDirectory) Color(0xFFF8FAFC) else Color.White)
                        .clickable {
                            if (!entry.isDirectory) {
                                try {
                                    val safeName = entry.name.ifBlank { "extracted_file" }
                                    val targetFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}_$safeName")
                                    val ok = DocumentContentParser.extractZipEntry(
                                        archiveDoc.zipBytes,
                                        entry.path,
                                        targetFile
                                    )
                                    if (ok) {
                                        extractedFileToOpen = targetFile
                                    } else {
                                        extractionError = "Could not extract ${entry.name}"
                                    }
                                } catch (e: Exception) {
                                    extractionError = "Extraction failed: ${e.localizedMessage}"
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.GridView else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (entry.isDirectory) Color(0xFFF59E0B) else when (entry.extension) {
                            "PDF" -> Color(0xFFDC2626)
                            "DOC", "DOCX" -> Color(0xFF2563EB)
                            "PPT", "PPTX" -> Color(0xFFD97706)
                            "XLS", "XLSX", "CSV", "TSV" -> Color(0xFF059669)
                            "PNG", "JPG", "JPEG" -> Color(0xFF7C3AED)
                            else -> Color(0xFF64748B)
                        },
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name.ifBlank { entry.path },
                            fontSize = 13.sp,
                            fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Medium,
                            color = Color(0xFF0F172A),
                            maxLines = 1
                        )
                        if (entry.path != entry.name && entry.path.contains("/")) {
                            Text(
                                text = entry.path,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }

                    if (!entry.isDirectory) {
                        Text(
                            text = LocalFileManager.formatFileSize(entry.sizeBytes),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }

    if (extractedFileToOpen != null) {
        val target = extractedFileToOpen!!
        AlertDialog(
            onDismissRequest = { extractedFileToOpen = null },
            title = { Text("Extracted File", fontWeight = FontWeight.Bold) },
            text = { Text("Successfully extracted '${target.name}'. Would you like to view this file?") },
            confirmButton = {
                Button(onClick = {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        target
                    )
                    val mime = LocalFileManager.resolveMimeType(target.name)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Open Extracted File"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
                    }
                    extractedFileToOpen = null
                }) {
                    Text("View File")
                }
            },
            dismissButton = {
                TextButton(onClick = { extractedFileToOpen = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (extractionError != null) {
        Toast.makeText(context, extractionError, Toast.LENGTH_SHORT).show()
        extractionError = null
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
    onToggleControls: () -> Unit = {},
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
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { onDoubleTap() }
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
    onToggleControls: () -> Unit = {},
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
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { onDoubleTap() }
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
    searchQuery: String,
    onToggleControls: () -> Unit = {}
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
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleControls() })
            }
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
    onThumbnailClick: () -> Unit,
    onShareClick: () -> Unit,
    isSaved: Boolean = false,
    onSaveClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    isLandscape: Boolean = false,
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
                .padding(horizontal = 32.dp, vertical = if (isLandscape) 4.dp else 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onThumbnailClick() }
                    .padding(horizontal = 20.dp, vertical = 6.dp)
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
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onShareClick() }
                    .padding(horizontal = 20.dp, vertical = 6.dp)
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
        }
    }
}

@Composable
private fun WordPdfContinuousView(
    renderer: PdfRendererWrapper,
    totalPages: Int,
    currentPageIndex: Int,
    zoomPercentage: Int,
    docGestureScale: Float,
    docPanOffset: Offset,
    onZoomPanChange: (Float, Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onToggleControls: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPageIndex - 1).coerceIn(0, totalPages - 1))
    val density = LocalContext.current.resources.displayMetrics.density
    val baseScale = (zoomPercentage / 100f) * density

    // Synchronize visible page back to parent
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            val page = (index + 1).coerceIn(1, totalPages)
            if (page != currentPageIndex) {
                onPageChanged(page)
            }
        }
    }

    // Scroll smoothly to target page when currentPageIndex changes externally (e.g. from jump dialog)
    LaunchedEffect(currentPageIndex) {
        val targetIdx = (currentPageIndex - 1).coerceIn(0, totalPages - 1)
        if (kotlin.math.abs(listState.firstVisibleItemIndex - targetIdx) > 1) {
            listState.animateScrollToItem(targetIdx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { onDoubleTap() }
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = docGestureScale
                    scaleY = docGestureScale
                    translationX = docPanOffset.x
                    translationY = docPanOffset.y
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(totalPages) { pageIdx ->
                PdfContinuousPageItem(
                    renderer = renderer,
                    pageIndex = pageIdx,
                    totalPages = totalPages,
                    scale = baseScale
                )
            }
        }
    }
}

@Composable
private fun PdfContinuousPageItem(
    renderer: PdfRendererWrapper,
    pageIndex: Int,
    totalPages: Int,
    scale: Float
) {
    var pageBitmap by remember(pageIndex, scale) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, scale) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = renderer.renderPage(pageIndex, scale)
                pageBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1} of $totalPages",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .background(Color.White)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(520.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(0xFF55A9AF),
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Page ${pageIndex + 1} of $totalPages",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Internal interface to abstract PDF rendering across Native Android PdfRenderer and Apache PDFBox.
 * This ensures robust multi-version compatibility (PDF 1.0 - 2.0), encrypted document support,
 * and graceful fallback on non-standard formatting.
 */
interface IPdfDocumentRenderer : AutoCloseable {
    val pageCount: Int
    fun renderPage(pageIndex: Int, scale: Float): Bitmap
    fun getPageDimensions(pageIndex: Int): Pair<Int, Int>?
}

typealias PdfRendererWrapper = IPdfDocumentRenderer

class NativePdfRendererWrapper(private val pfd: ParcelFileDescriptor) : IPdfDocumentRenderer {
    private val renderer = PdfRenderer(pfd)

    override val pageCount: Int
        get() = renderer.pageCount

    override fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        return try {
            synchronized(renderer) {
                val safeIndex = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                val page = renderer.openPage(safeIndex)
                val dims = Pair(page.width, page.height)
                page.close()
                dims
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun renderPage(pageIndex: Int, scale: Float): Bitmap {
        return synchronized(renderer) {
            val safeIndex = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
            val page = renderer.openPage(safeIndex)
            try {
                val aspectRatio = if (page.height > 0) page.width.toFloat() / page.height.toFloat() else 0.707f
                val targetWidth = (page.width * scale).toInt().coerceIn(200, 3600)
                val targetHeight = (targetWidth / aspectRatio).toInt().coerceIn(200, 5400)

                try {
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (oom: Throwable) {
                    val fallbackW = (targetWidth * 0.5f).toInt().coerceAtLeast(100)
                    val fallbackH = (targetHeight * 0.5f).toInt().coerceAtLeast(100)
                    val bitmap = Bitmap.createBitmap(fallbackW, fallbackH, Bitmap.Config.RGB_565)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        try {
            synchronized(renderer) {
                renderer.close()
            }
        } catch (_: Exception) {}
        try {
            pfd.close()
        } catch (_: Exception) {}
    }
}

class PdfBoxRendererWrapper(private val document: PDDocument) : IPdfDocumentRenderer {
    private val pdfRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)

    override val pageCount: Int
        get() = document.numberOfPages

    override fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        return try {
            val safeIndex = pageIndex.coerceIn(0, (document.numberOfPages - 1).coerceAtLeast(0))
            val page = document.getPage(safeIndex)
            val box = page.cropBox ?: page.mediaBox
            Pair(box.width.toInt(), box.height.toInt())
        } catch (_: Exception) {
            null
        }
    }

    override fun renderPage(pageIndex: Int, scale: Float): Bitmap {
        return synchronized(document) {
            val safeIndex = pageIndex.coerceIn(0, (document.numberOfPages - 1).coerceAtLeast(0))
            val safeScale = scale.coerceIn(0.5f, 3.0f)
            try {
                pdfRenderer.renderImage(safeIndex, safeScale, ImageType.RGB)
            } catch (oom: Throwable) {
                try {
                    pdfRenderer.renderImage(safeIndex, 1.0f, ImageType.RGB)
                } catch (_: Throwable) {
                    val fallback = Bitmap.createBitmap(400, 600, Bitmap.Config.RGB_565)
                    Canvas(fallback).drawColor(android.graphics.Color.WHITE)
                    fallback
                }
            }
        }
    }

    override fun close() {
        try {
            document.close()
        } catch (_: Exception) {}
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentViewerScreenPreview() {
    DocumentViewerScreen()
}
