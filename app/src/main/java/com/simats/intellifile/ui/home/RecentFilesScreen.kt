package com.simats.intellifile.ui.home

import android.widget.Toast
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.DocumentRepository
import kotlinx.coroutines.launch

enum class FileFormatFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    DOC("DOC"),
    OTHER("Other")
}

@Composable
fun RecentFilesScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onShareFileClick: (RecentFile) -> Unit = {},
    onMoreOptionsClick: (RecentFile) -> Unit = {},
    onMicClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val docRepo = remember { DocumentRepository(context) }
    val dbHelper = remember { LocalFileDatabaseHelper.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFormatFilter by remember { mutableStateOf(FileFormatFilter.ALL) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var realFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Multi-select & Batch Vault Move State
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<RecentFile>() }
    var isMovingToVault by remember { mutableStateOf(false) }
    var moveProgressMessage by remember { mutableStateOf("") }
    var showVaultConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        realFiles = LocalFileManager.getDeviceFiles(context)
        isLoading = false
    }

    val recentFiles = remember(realFiles) {
        realFiles.map { item ->
            RecentFile(
                title = item.title,
                format = item.format,
                size = item.sizeFormatted,
                dateText = item.dateText,
                priority = item.priority,
                iconResId = item.iconResId,
                uriString = item.uri.toString(),
                path = item.path
            )
        }
    }

    val filteredFiles = remember(recentFiles, searchQuery, selectedFormatFilter) {
        recentFiles.filter { file ->
            val matchesSearch = file.title.contains(searchQuery, ignoreCase = true)
            val matchesFormat = when (selectedFormatFilter) {
                FileFormatFilter.ALL -> true
                FileFormatFilter.PDF -> file.format.equals("PDF", ignoreCase = true)
                FileFormatFilter.DOC -> file.format.equals("DOC", ignoreCase = true) || file.format.equals("DOCX", ignoreCase = true)
                FileFormatFilter.OTHER -> !listOf("PDF", "DOC", "DOCX").contains(file.format.uppercase())
            }
            matchesSearch && matchesFormat
        }
    }

    fun toggleFileSelection(file: RecentFile) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
            if (selectedFiles.isEmpty()) {
                isMultiSelectMode = false
            }
        } else {
            selectedFiles.add(file)
        }
    }

    fun executeBatchVaultMove() {
        if (selectedFiles.isEmpty()) return
        isMovingToVault = true
        val filesToMove = selectedFiles.toList()

        coroutineScope.launch {
            var successCount = 0
            var lastError: String? = null

            filesToMove.forEachIndexed { index, file ->
                moveProgressMessage = "Moving file ${index + 1} of ${filesToMove.size}: ${file.title}..."
                val result = docRepo.uploadToVault(
                    filePath = file.path,
                    uriString = file.uriString,
                    fileName = file.title,
                    priority = file.priority?.name
                )
                result.onSuccess {
                    val key = if (file.path.isNotBlank()) file.path else file.uriString
                    dbHelper.setVaultStatus(key, file.title, true)
                    successCount++
                }.onFailure { ex ->
                    lastError = ex.message
                }
            }

            isMovingToVault = false
            moveProgressMessage = ""

            if (successCount > 0) {
                val msg = if (successCount == 1) {
                    "1 file moved to AI Vault successfully!"
                } else {
                    "$successCount files moved to AI Vault successfully!"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                selectedFiles.clear()
                isMultiSelectMode = false
                refreshTrigger++
            } else {
                Toast.makeText(context, "Move failed: ${lastError ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Confirmation Dialog for moving files to AI Vault
    if (showVaultConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isMovingToVault) showVaultConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Move ${selectedFiles.size} File${if (selectedFiles.size > 1) "s" else ""} to AI Vault?",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Moving ${selectedFiles.size} document${if (selectedFiles.size > 1) "s" else ""} into the AI Vault enables complete text extraction, semantic RAG search, AI summarization, and interactive Q&A.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF374151)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Selected for AI Vault:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    selectedFiles.take(4).forEach { f ->
                        Text(
                            text = "• ${f.title}",
                            fontSize = 12.sp,
                            color = Color(0xFF4B5563),
                            maxLines = 1
                        )
                    }
                    if (selectedFiles.size > 4) {
                        Text(
                            text = "...and ${selectedFiles.size - 4} more documents",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVaultConfirmDialog = false
                        executeBatchVaultMove()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (selectedFiles.size > 2) "Move ${selectedFiles.size} Files" else "Move to Vault",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showVaultConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            }
        )
    }

    // Modal Progress Dialog while moving batch files to AI Vault
    if (isMovingToVault) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissible while processing */ },
            title = {
                Text(
                    text = "Moving to AI Vault...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = moveProgressMessage,
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {}
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            if (isMultiSelectMode) {
                // Multi-selection Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedFiles.clear()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Selection",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${selectedFiles.size} selected",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    TextButton(onClick = {
                        if (selectedFiles.size == filteredFiles.size && filteredFiles.isNotEmpty()) {
                            selectedFiles.clear()
                        } else {
                            selectedFiles.clear()
                            selectedFiles.addAll(filteredFiles)
                        }
                    }) {
                        Text(
                            text = if (selectedFiles.size == filteredFiles.size && filteredFiles.isNotEmpty()) "Deselect All" else "Select All",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // Default Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Recent Files",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMicClick) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Assistant",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { isMultiSelectMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Select Files to Move",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Files",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = if (isMultiSelectMode && selectedFiles.isNotEmpty()) 90.dp else 24.dp)
            ) {
                item {
                    // Search Input Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search documents...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                item {
                    // Filter Chips Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(FileFormatFilter.values()) { filter ->
                            val isSelected = filter == selectedFormatFilter
                            FormatFilterChip(
                                filter = filter,
                                isSelected = isSelected,
                                onClick = { selectedFormatFilter = filter }
                            )
                        }
                    }
                }

                // Empty state if no documents match
                if (!isLoading && filteredFiles.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No documents found matching \"$searchQuery\"" else "No ${selectedFormatFilter.label} documents found",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "IntelliFile displays documents from your phone's storage.",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Real Recent Files List
                items(filteredFiles) { file ->
                    val isSelected = selectedFiles.contains(file)
                    RecentFileItemCard(
                        file = file,
                        isMultiSelectMode = isMultiSelectMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isMultiSelectMode) {
                                toggleFileSelection(file)
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) {
                                isMultiSelectMode = true
                                selectedFiles.add(file)
                            } else {
                                toggleFileSelection(file)
                            }
                        },
                        onShareClick = { onShareFileClick(file) }
                    )
                }
            }
        }

        // Floating Bottom Action Bar for Multi-selection & Move to AI Vault
        AnimatedVisibility(
            visible = isMultiSelectMode && selectedFiles.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedFiles.size} document${if (selectedFiles.size > 1) "s" else ""} selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedFiles.size > 2) "Ready for batch AI Vault move" else "Ready to move into AI Vault",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Button(
                        onClick = { showVaultConfirmDialog = true },
                        enabled = !isMovingToVault,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedFiles.size > 2) "Move ${selectedFiles.size} to Vault"
                            else if (selectedFiles.size == 2) "Move 2 to Vault"
                            else "Move to Vault",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormatFilterChip(
    filter: FileFormatFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary

    val chipScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "chipScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = chipScale, scaleY = chipScale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filter.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileItemCard(
    file: RecentFile,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            val checkScale by animateFloatAsState(
                targetValue = if (isSelected) 1.0f else 0.2f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "checkScale"
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFD1D5DB), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp).graphicsLayer(scaleX = checkScale, scaleY = checkScale)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Image(
            painter = painterResource(id = file.iconResId),
            contentDescription = "File Type Icon",
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${file.format} · ${file.size} · ${file.dateText}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Display priority tag only if explicitly set by the user
        if (file.priority != null) {
            PriorityTag(priority = file.priority)
        }

        if (!isMultiSelectMode) {
            Spacer(modifier = Modifier.width(4.dp))

            Box {
                IconButton(
                    onClick = { isMenuOpen = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color(0xFF374151),
                        modifier = Modifier.size(18.dp)
                    )
                }

                FileOptionsDropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false },
                    onShareClick = onShareClick,
                    onDeleteClick = { /* Handle delete file */ }
                )
            }
        }
    }
}

@Composable
fun FileOptionsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF374151),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Share",
                        fontSize = 14.sp,
                        color = Color(0xFF374151)
                    )
                }
            },
            onClick = {
                onDismissRequest()
                onShareClick()
            }
        )

        HorizontalDivider(color = Color(0xFFF3F4F6))

        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Delete",
                        fontSize = 14.sp,
                        color = Color(0xFFEF4444)
                    )
                }
            },
            onClick = {
                onDismissRequest()
                onDeleteClick()
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecentFilesScreenPreview() {
    RecentFilesScreen()
}
