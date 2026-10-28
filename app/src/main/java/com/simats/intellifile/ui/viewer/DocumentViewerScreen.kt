package com.simats.intellifile.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
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

    val isPdf = remember(fileName, fileFormat) {
        fileFormat.equals("PDF", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)
    }

    val isTextBased = remember(fileName, fileFormat) {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        ext in setOf("TXT", "CSV", "JSON", "MD", "DOCX", "PPTX") ||
                fileFormat.uppercase() in setOf("TXT", "CSV", "JSON", "MD", "DOCX", "PPTX")
    }

    // State
    var currentPage by remember { mutableIntStateOf(1) }
    var detectedTotalPages by remember { mutableIntStateOf(totalPages.coerceAtLeast(1)) }
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

    // Jump to page dialog state
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    // PDF renderer states
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Text content state
    var textContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load PDF or Text content
    LaunchedEffect(filePath, fileUriString, fileName) {
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
                        detectedTotalPages = renderer.pageCount.coerceAtLeast(1)
                        currentPage = 1
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
        } else if (isTextBased) {
            withContext(Dispatchers.IO) {
                try {
                    val text = LocalFileManager.readTextContent(
                        context = context,
                        uriString = fileUriString,
                        path = filePath,
                        format = fileFormat
                    )
                    textContent = text.ifBlank { "This document appears to be empty." }
                } catch (e: Exception) {
                    errorMessage = "Error reading document text: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    // Render PDF page when currentPage or zoom changes
    LaunchedEffect(currentPage, zoomPercentage, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (detectedTotalPages <= 0) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val pageIndex = (currentPage - 1).coerceIn(0, detectedTotalPages - 1)
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

    // Cleanup PDF resources on exit
    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
            } catch (_: Exception) {}
            try {
                parcelFileDescriptor?.close()
            } catch (_: Exception) {}
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

    // Jump to Page Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter page number (1 to $detectedTotalPages):", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = jumpPageInput,
                        onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("Page #") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = jumpPageInput.toIntOrNull()
                        if (target != null && target in 1..detectedTotalPages) {
                            currentPage = target
                            showJumpDialog = false
                            jumpPageInput = ""
                        } else {
                            Toast.makeText(context, "Please enter a valid page (1 - $detectedTotalPages)", Toast.LENGTH_SHORT).show()
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
                // Search Mode Header
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
                        placeholder = { Text("Find text in document...") },
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
                // Normal Header Bar
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
                            Text(
                                text = if (isPdf) "PDF Document" else "$fileFormat File",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTextBased) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF111827)
                                )
                            }
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

            // Main Document Page Container
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
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading document...",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    errorMessage != null -> {
                        // Error Card with Fallback
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
                        // Real Rendered PDF Page View
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
                                    contentDescription = "Page $currentPage of $detectedTotalPages",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                )
                            }
                        } else {
                            Text(
                                text = "Rendering page...",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    isTextBased -> {
                        // Real Text Document View (TXT, CSV, JSON, MD, DOCX, PPTX)
                        val textScrollState = rememberScrollState()
                        val fontSizeSp = (14 * (zoomPercentage / 100f)).sp
                        val filteredText = if (searchQuery.isNotBlank()) {
                            val matches = textContent.lines().filter { it.contains(searchQuery, ignoreCase = true) }
                            if (matches.isNotEmpty()) {
                                "=== Search Results for \"$searchQuery\" (${matches.size} lines found) ===\n\n" +
                                        matches.joinToString("\n") +
                                        "\n\n=== Full Document Content ===\n\n" + textContent
                            } else {
                                "No matches found for \"$searchQuery\".\n\n=== Full Document Content ===\n\n" + textContent
                            }
                        } else {
                            textContent
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(16.dp)
                                .verticalScroll(textScrollState)
                        ) {
                            Text(
                                text = filteredText,
                                fontSize = fontSizeSp,
                                color = Color(0xFF1F2937),
                                lineHeight = (fontSizeSp.value * 1.5f).sp,
                                fontFamily = if (fileFormat in setOf("JSON", "CSV")) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                    }

                    else -> {
                        // Binary Office Document (XLS, XLS, DOC, PPT) Preview Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = fileName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "$fileFormat Document",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "This document format is best viewed with an external Office app or uploaded to the AI Vault for semantic extraction.",
                                fontSize = 13.sp,
                                color = Color(0xFF4B5563),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

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
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in System Viewer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Page Indicator and Zoom Bar (Interactive controls)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Page Indicator Pill with Prev / Next arrows
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFF1F5F9), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPdf && detectedTotalPages > 1) {
                        IconButton(
                            onClick = { if (currentPage > 1) currentPage-- },
                            enabled = currentPage > 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Page",
                                tint = if (currentPage > 1) Color(0xFF374151) else Color(0xFFD1D5DB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isPdf) "$currentPage / $detectedTotalPages" else "Page 1 / 1",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151),
                        modifier = Modifier
                            .clickable {
                                if (isPdf && detectedTotalPages > 1) {
                                    showJumpDialog = true
                                }
                            }
                            .padding(horizontal = 8.dp)
                    )

                    if (isPdf && detectedTotalPages > 1) {
                        IconButton(
                            onClick = { if (currentPage < detectedTotalPages) currentPage++ },
                            enabled = currentPage < detectedTotalPages,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Page",
                                tint = if (currentPage < detectedTotalPages) Color(0xFF374151) else Color(0xFFD1D5DB),
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
                        onClick = { if (zoomPercentage > 50) zoomPercentage -= 15 },
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
                        onClick = { if (zoomPercentage < 200) zoomPercentage += 15 },
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

            Spacer(modifier = Modifier.height(12.dp))

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
                    if (isPdf && detectedTotalPages > 1) {
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
            // Thumbnail / Jump Action
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onThumbnailClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Thumbnail",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pages",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
            }

            // Share Action
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

            // Save Action Pill Button
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

            // More Action
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onMoreClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
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
