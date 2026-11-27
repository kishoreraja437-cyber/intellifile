package com.simats.intellifile.ui.files

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.simats.intellifile.R
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.manager.ZipExtractor
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.DocumentRepository
import com.simats.intellifile.ui.common.DraggableScrollbar
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.home.PriorityLevel
import kotlinx.coroutines.launch
import java.io.File

data class ExtractedFolderItem(
    val file: File,
    val isDirectory: Boolean,
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val format: String,
    val iconResId: Int,
    val vectorIcon: ImageVector? = null,
    val dateModifiedMs: Long,
    val dateText: String,
    val childCount: Int = 0,
    val priority: PriorityLevel? = null,
    val isBookmarked: Boolean = false,
    val isVaultSynced: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractedFolderViewerScreen(
    rootFolder: File,
    onBackClick: () -> Unit,
    onOpenFileDetails: (FileDetailData) -> Unit,
    onOpenDirectViewer: (LocalFileItem) -> Unit,
    onShareFile: (LocalFileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbHelper = remember { LocalFileDatabaseHelper.getInstance(context) }
    val docRepo = remember { DocumentRepository(context) }

    // Breadcrumb navigation stack: starts at rootFolder
    var currentDirectory by remember(rootFolder) { mutableStateOf(rootFolder) }
    val navHistory = remember(rootFolder) { mutableStateListOf<File>() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var refreshVersion by remember { mutableIntStateOf(0) }

    // Selected file for priority assignment, IntelliSpace dialog, preview, or unsupported format
    var prioritySheetItem by remember { mutableStateOf<ExtractedFolderItem?>(null) }
    var vaultConfirmItem by remember { mutableStateOf<ExtractedFolderItem?>(null) }
    var isUploadingToVault by remember { mutableStateOf(false) }
    var viewingImageItem by remember { mutableStateOf<ExtractedFolderItem?>(null) }
    var unsupportedFileItem by remember { mutableStateOf<ExtractedFolderItem?>(null) }

    // Handle system back navigation inside folders
    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (navHistory.isNotEmpty()) {
            currentDirectory = navHistory.removeAt(navHistory.size - 1)
        } else {
            onBackClick()
        }
    }

    // Load items in current directory
    val folderItems by remember(currentDirectory, refreshVersion) {
        derivedStateOf {
            val children = currentDirectory.listFiles() ?: emptyArray()
            val metadataMap = dbHelper.getAllMetadata()

            children.map { child ->
                val isDir = child.isDirectory
                val format = if (isDir) "FOLDER" else LocalFileManager.resolveFormat(child.name, null)
                val iconRes = if (isDir) R.drawable.normal else LocalFileManager.resolveIconResId(format)
                val meta = metadataMap[child.absolutePath] ?: metadataMap[child.name]

                val vectorIcon = when {
                    isDir -> Icons.Default.Folder
                    format == "ZIP" -> Icons.Default.FolderZip
                    format == "PDF" -> Icons.Default.PictureAsPdf
                    format in listOf("DOC", "DOCX") -> Icons.Default.Description
                    format in listOf("PPT", "PPTX") -> Icons.Default.Slideshow
                    format in listOf("XLS", "XLSX", "CSV") -> Icons.Default.TableChart
                    format in listOf("PNG", "JPG", "JPEG", "WEBP") -> Icons.Default.Image
                    format in listOf("MP4", "MKV", "AVI") -> Icons.Default.VideoFile
                    format in listOf("MP3", "WAV", "AAC") -> Icons.Default.AudioFile
                    else -> Icons.Default.InsertDriveFile
                }

                val childCount = if (isDir) (child.listFiles()?.size ?: 0) else 0

                ExtractedFolderItem(
                    file = child,
                    isDirectory = isDir,
                    name = child.name,
                    sizeBytes = if (isDir) 0L else child.length(),
                    sizeFormatted = if (isDir) "$childCount item${if (childCount != 1) "s" else ""}" else LocalFileManager.formatFileSize(child.length()),
                    format = format,
                    iconResId = iconRes,
                    vectorIcon = vectorIcon,
                    dateModifiedMs = child.lastModified(),
                    dateText = LocalFileManager.formatDate(child.lastModified()),
                    childCount = childCount,
                    priority = meta?.priority,
                    isBookmarked = meta?.isBookmarked ?: false,
                    isVaultSynced = meta?.isVaultSynced ?: false
                )
            }.sortedWith(
                compareByDescending<ExtractedFolderItem> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    // Filtered items based on search query
    val filteredItems = remember(folderItems, searchQuery) {
        if (searchQuery.isBlank()) folderItems
        else folderItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Stats for the current directory
    val (totalFiles, totalSubfolders, totalSize) = remember(currentDirectory, refreshVersion) {
        ZipExtractor.scanFolderStats(currentDirectory)
    }

    // Breadcrumb path segments from root to current
    val breadcrumbSegments = remember(currentDirectory, navHistory.size) {
        val list = mutableListOf<File>()
        list.addAll(navHistory)
        list.add(currentDirectory)
        list
    }

    // Function to navigate into a subfolder
    fun navigateInto(subfolder: File) {
        navHistory.add(currentDirectory)
        currentDirectory = subfolder
        searchQuery = ""
    }

    // Function to navigate to a specific ancestor in breadcrumbs
    fun navigateToAncestor(targetDir: File) {
        val index = navHistory.indexOf(targetDir)
        if (index != -1) {
            // Keep up to index
            val toRemove = navHistory.subList(index + 1, navHistory.size).toList()
            navHistory.removeAll(toRemove)
            navHistory.removeAt(index)
            currentDirectory = targetDir
            searchQuery = ""
        } else if (targetDir == rootFolder && currentDirectory != rootFolder) {
            navHistory.clear()
            currentDirectory = rootFolder
            searchQuery = ""
        }
    }

    // Helper to obtain a secure FileProvider content URI with fallback
    fun getSafeContentUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    // Convert an ExtractedFolderItem to a LocalFileItem
    fun toLocalFileItem(item: ExtractedFolderItem): LocalFileItem {
        val safeUri = getSafeContentUri(item.file)
        return LocalFileItem(
            id = item.file.hashCode().toLong(),
            title = item.name,
            path = item.file.absolutePath,
            uri = safeUri,
            sizeBytes = item.sizeBytes,
            sizeFormatted = LocalFileManager.formatFileSize(item.sizeBytes),
            dateModifiedMs = item.dateModifiedMs,
            dateText = item.dateText,
            format = item.format,
            mimeType = LocalFileManager.resolveMimeType(item.name),
            priority = item.priority,
            isBookmarked = item.isBookmarked,
            isVaultSynced = item.isVaultSynced,
            iconResId = item.iconResId
        )
    }

    // Convert an ExtractedFolderItem to FileDetailData
    fun toFileDetailData(item: ExtractedFolderItem): FileDetailData {
        val safeUri = getSafeContentUri(item.file)
        return FileDetailData(
            fileName = item.name,
            fileSize = LocalFileManager.formatFileSize(item.sizeBytes),
            fileType = "${item.format} Document",
            createdDate = item.dateText,
            modifiedDate = item.dateText,
            category = "Extracted File",
            location = item.file.parent ?: "Extracted Folder",
            priority = item.priority,
            iconResId = item.iconResId,
            uriString = safeUri.toString(),
            path = item.file.absolutePath,
            isVaultSynced = item.isVaultSynced
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Header Bar
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            IconButton(onClick = {
                                if (navHistory.isNotEmpty()) {
                                    currentDirectory = navHistory.removeAt(navHistory.size - 1)
                                } else {
                                    onBackClick()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF1E293B)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Column {
                                Text(
                                    text = currentDirectory.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (currentDirectory == rootFolder) "Extracted Root Archive" else "Inside: ${rootFolder.name}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchActive) Color(0xFF00897B) else Color(0xFF64748B)
                                )
                            }

                            IconButton(onClick = { refreshVersion++ }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // Optional Search Bar
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Filter files in folder...", color = Color(0xFF94A3B8), fontSize = 13.5.sp)
                                        }
                                        inner()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Breadcrumbs Bar (Support nested folders and multi-level navigation)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbSegments.forEachIndexed { idx, dir ->
                            val isCurrent = idx == breadcrumbSegments.lastIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isCurrent) { navigateToAncestor(dir) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                if (idx == 0) {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFF00897B) else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = dir.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) Color(0xFF00897B) else Color(0xFF64748B)
                                )
                            }

                            if (idx < breadcrumbSegments.lastIndex) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Stats Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE0F2F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF00897B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Extracted Contents",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "$totalFiles files · $totalSubfolders subfolders",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Text(
                        text = LocalFileManager.formatFileSize(totalSize),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00897B)
                    )
                }
            }

            // 4. File Items List
            val listState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (filteredItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No items match \"$searchQuery\"" else "This folder is empty",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems, key = { it.file.absolutePath }) { item ->
                            ExtractedItemCard(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        navigateInto(item.file)
                                    } else {
                                        val localItem = toLocalFileItem(item)
                                        val ext = item.file.extension.lowercase()
                                        val formatUpper = item.format.uppercase()

                                        val documentExtensions = setOf(
                                            "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "csv",
                                            "txt", "md", "markdown", "py", "js", "ts", "jsx", "tsx",
                                            "html", "htm", "css", "scss", "json", "xml", "yaml", "yml",
                                            "kt", "java", "c", "cpp", "h", "cs", "go", "rs", "php",
                                            "sql", "sh", "bat", "ini", "conf", "properties", "log"
                                        )
                                        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg")
                                        val mediaExtensions = setOf("mp3", "wav", "m4a", "flac", "aac", "ogg", "mp4", "mkv", "mov", "avi", "3gp")

                                        when {
                                            ext in documentExtensions || formatUpper in listOf("PDF", "PPT", "PPTX", "DOC", "DOCX", "TXT", "XLSX", "CSV", "CODE") -> {
                                                onOpenDirectViewer(localItem)
                                            }
                                            ext in imageExtensions -> {
                                                viewingImageItem = item
                                            }
                                            ext in mediaExtensions -> {
                                                LocalFileManager.openFileWithSystem(context, localItem)
                                            }
                                            else -> {
                                                unsupportedFileItem = item
                                            }
                                        }
                                    }
                                },
                                onInfoClick = {
                                    if (!item.isDirectory) {
                                        onOpenFileDetails(toFileDetailData(item))
                                    }
                                },
                                onShareClick = {
                                    if (!item.isDirectory) {
                                        onShareFile(toLocalFileItem(item))
                                    }
                                },
                                onSetPriorityClick = {
                                    prioritySheetItem = item
                                },
                                onAddToVaultClick = {
                                    vaultConfirmItem = item
                                }
                            )
                        }
                    }
                }

                DraggableScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }

    // Modal Sheet for Setting Priority on Extracted File
    if (prioritySheetItem != null) {
        val target = prioritySheetItem!!
        AlertDialog(
            onDismissRequest = { prioritySheetItem = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Set Priority",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = target.name,
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PriorityLevel.values().forEach { priority ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (target.priority == priority) Color(0xFFE0F2F1) else Color(0xFFF8FAFC))
                                .clickable {
                                    dbHelper.setPriority(target.file.absolutePath, target.name, priority)
                                    refreshVersion++
                                    prioritySheetItem = null
                                    Toast.makeText(context, "Priority set to ${priority.title}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = priority.title,
                                fontWeight = FontWeight.SemiBold,
                                color = priority.textColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    // Option to clear priority
                    if (target.priority != null) {
                        TextButton(
                            onClick = {
                                dbHelper.setPriority(target.file.absolutePath, target.name, null)
                                refreshVersion++
                                prioritySheetItem = null
                                Toast.makeText(context, "Priority cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Priority", color = Color(0xFFEF4444))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { prioritySheetItem = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // Modal Confirmation for Adding Extracted File to IntelliSpace
    if (vaultConfirmItem != null) {
        val target = vaultConfirmItem!!
        AlertDialog(
            onDismissRequest = { if (!isUploadingToVault) vaultConfirmItem = null },
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF00897B),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add to IntelliSpace?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Upload '${target.name}' to IntelliSpace for AI indexing, semantic search, and document Q&A?",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isUploadingToVault = true
                        scope.launch {
                            val res = docRepo.uploadToVault(
                                filePath = target.file.absolutePath,
                                uriString = Uri.fromFile(target.file).toString(),
                                fileName = target.name,
                                category = "Extracted Document",
                                priority = target.priority?.name
                            )
                            isUploadingToVault = false
                            vaultConfirmItem = null
                            res.onSuccess { doc ->
                                dbHelper.setVaultStatus(target.file.absolutePath, target.name, true, doc.id)
                                refreshVersion++
                                Toast.makeText(context, "${target.name} added to IntelliSpace successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "IntelliSpace upload failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isUploadingToVault) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Upload", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vaultConfirmItem = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // 6. Image Viewer Dialog for previewing extracted images
    if (viewingImageItem != null) {
        ExtractedImageViewerDialog(
            item = viewingImageItem!!,
            onDismiss = { viewingImageItem = null },
            onOpenExternal = {
                val target = viewingImageItem!!
                viewingImageItem = null
                LocalFileManager.openFileWithSystem(context, toLocalFileItem(target))
            },
            onShare = {
                val target = viewingImageItem!!
                viewingImageItem = null
                onShareFile(toLocalFileItem(target))
            }
        )
    }

    // 7. Unsupported Format Dialog for non-previewable files
    if (unsupportedFileItem != null) {
        ExtractedUnsupportedFormatDialog(
            item = unsupportedFileItem!!,
            onDismiss = { unsupportedFileItem = null },
            onOpenExternal = {
                val target = unsupportedFileItem!!
                unsupportedFileItem = null
                LocalFileManager.openFileWithSystem(context, toLocalFileItem(target))
            },
            onOpenDetails = {
                val target = unsupportedFileItem!!
                unsupportedFileItem = null
                onOpenFileDetails(toFileDetailData(target))
            }
        )
    }
}

@Composable
private fun ExtractedItemCard(
    item: ExtractedFolderItem,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onShareClick: () -> Unit,
    onSetPriorityClick: () -> Unit,
    onAddToVaultClick: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (item.isDirectory) Color(0xFFE2E8F0) else Color(0xFFF1F5F9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (item.isDirectory) Color(0xFFFFF7ED)
                        else when (item.format) {
                            "PDF" -> Color(0xFFFFEBEE)
                            "DOC", "DOCX" -> Color(0xFFEFF6FF)
                            "PPT", "PPTX" -> Color(0xFFFFF1F2)
                            "XLS", "XLSX" -> Color(0xFFF0FDF4)
                            else -> Color(0xFFF8FAFC)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isDirectory) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                } else if (item.vectorIcon != null) {
                    Icon(
                        imageVector = item.vectorIcon,
                        contentDescription = null,
                        tint = when (item.format) {
                            "PDF" -> Color(0xFFDC2626)
                            "DOC", "DOCX" -> Color(0xFF2563EB)
                            "PPT", "PPTX" -> Color(0xFFEA580C)
                            "XLS", "XLSX" -> Color(0xFF16A34A)
                            "ZIP" -> Color(0xFF00897B)
                            else -> Color(0xFF64748B)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = item.iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File/Folder Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.sizeFormatted,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    if (!item.isDirectory) {
                        Text(
                            text = " · ${item.format}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    if (item.priority != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(item.priority.backgroundColor)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = item.priority.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = item.priority.textColor
                            )
                        }
                    }
                    if (item.isVaultSynced) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Trailing Action
            if (item.isDirectory) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Folder",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Box {
                    IconButton(
                        onClick = { isMenuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open File") },
                            onClick = {
                                isMenuOpen = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File Details") },
                            onClick = {
                                isMenuOpen = false
                                onInfoClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                isMenuOpen = false
                                onShareClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set Priority") },
                            onClick = {
                                isMenuOpen = false
                                onSetPriorityClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        if (!item.isVaultSynced) {
                            DropdownMenuItem(
                                text = { Text("Add to IntelliSpace") },
                                onClick = {
                                    isMenuOpen = false
                                    onAddToVaultClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractedImageViewerDialog(
    item: ExtractedFolderItem,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    val bitmap = remember(item.file) {
        try {
            BitmapFactory.decodeFile(item.file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with filename and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.format} • ${item.sizeFormatted}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Unable to preview image",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: Share / Open External
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF55A9AF))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF55A9AF))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = Color(0xFF55A9AF), fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onOpenExternal,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF))
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Full", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractedUnsupportedFormatDialog(
    item: ExtractedFolderItem,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Open With Another App",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${item.name} (${item.format}) cannot be directly previewed inside the in-app viewer.",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Would you like to open it using an external compatible application or inspect its metadata details?",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenExternal()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Externally", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
                TextButton(onClick = {
                    onDismiss()
                    onOpenDetails()
                }) {
                    Text("Details", color = Color(0xFF55A9AF), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
