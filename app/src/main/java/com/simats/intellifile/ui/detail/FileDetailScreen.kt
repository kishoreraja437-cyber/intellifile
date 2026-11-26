package com.simats.intellifile.ui.detail

import android.net.Uri
import com.simats.intellifile.ui.common.IntelliSpaceIndexingDialog
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import com.simats.intellifile.data.repository.DocumentRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.simats.intellifile.data.manager.FileMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.home.PriorityLevel

data class FileDetailData(
    val fileName: String = "Document.pdf",
    val fileSize: String = "0 KB",
    val fileType: String = "PDF Document",
    val createdDate: String = "Recent",
    val modifiedDate: String = "Recent",
    val category: String = "Document",
    val location: String = "Internal Storage",
    val priority: PriorityLevel? = null,
    val iconResId: Int = R.drawable.pdf,
    val uriString: String = "",
    val path: String = "",
    val isVaultSynced: Boolean = false,
    val dateAdded: String? = null,
    val lastOpened: String? = null,
    val contentCountLabel: String? = null,
    val contentCountValue: String? = null
)

@Composable
fun FileDetailScreen(
    fileData: FileDetailData = FileDetailData(),
    onBackClick: () -> Unit = {},
    onOpenFileClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onMoreOptionsClick: () -> Unit = {},
    onSetPriorityClick: () -> Unit = {},
    onPriorityChanged: (PriorityLevel?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docRepository = remember { DocumentRepository(context) }
    val dbHelper = remember { LocalFileDatabaseHelper.getInstance(context) }
    var isUploadingToVault by remember { mutableStateOf(false) }
    var selectedPriority by remember(fileData.priority) { mutableStateOf(fileData.priority) }

    val effectivePath = fileData.path.takeIf { it.isNotBlank() && it != "Internal Storage" } ?: ""
    val effectiveUri = fileData.uriString.takeIf { it.isNotBlank() } ?: ""
    val key = when {
        effectivePath.isNotBlank() -> effectivePath
        effectiveUri.isNotBlank() -> effectiveUri
        else -> fileData.fileName
    }

    var isVaultSynced by remember(fileData.isVaultSynced, fileData.path, fileData.uriString, fileData.fileName) {
        val meta = (if (effectivePath.isNotBlank()) dbHelper.getMetadata(effectivePath) else null)
            ?: (if (effectiveUri.isNotBlank()) dbHelper.getMetadata(effectiveUri) else null)
            ?: dbHelper.getMetadata(fileData.fileName)
        mutableStateOf(fileData.isVaultSynced || (meta?.isVaultSynced == true))
    }
    var isCheckingVaultStatus by remember { mutableStateOf(false) }

    // Dynamic Metadata Extraction States (File Format, File Size, Page/Slide Count, Date Added, Last Opened)
    var fileFormat by remember(fileData.fileName, fileData.fileType) {
        mutableStateOf(FileMetadataExtractor.resolveFormat(fileData.fileName, fileData.fileType))
    }
    var fileSize by remember(fileData.fileSize) {
        mutableStateOf(fileData.fileSize.takeIf { it.isNotBlank() && it != "0 KB" } ?: "Loading...")
    }
    var contentCountLabel by remember(fileFormat) {
        mutableStateOf(fileData.contentCountLabel ?: FileMetadataExtractor.getCountLabel(fileFormat))
    }
    var contentCountValue by remember(fileData.contentCountValue) {
        mutableStateOf(fileData.contentCountValue ?: "Calculating...")
    }
    var dateAddedText by remember(fileData.dateAdded) {
        mutableStateOf(fileData.dateAdded ?: "Checking...")
    }
    var lastOpenedText by remember(fileData.lastOpened) {
        mutableStateOf(fileData.lastOpened ?: "Checking...")
    }

    LaunchedEffect(key, fileData.fileName, fileData.path, fileData.uriString) {
        val details = FileMetadataExtractor.extractDetails(
            context = context,
            fileName = fileData.fileName,
            filePath = fileData.path,
            fileUriString = fileData.uriString,
            fallbackFileSize = fileData.fileSize,
            fallbackFormat = fileData.fileType
        )
        fileFormat = details.format
        fileSize = details.formattedSize
        contentCountLabel = details.countLabel
        contentCountValue = details.countValue
        dateAddedText = details.dateAddedText
        lastOpenedText = details.lastOpenedText
    }

    // Live Server Synchronization (Requirement 1, 6, 7, 8, 9, 10):
    // 1. Checks whether current file exists in IntelliSpace on the backend.
    // 2. Uses unique identifier (vaultDocId or title + location/path) to prevent false duplicates.
    // 3. If file was deleted from IntelliSpace, resets isVaultSynced to false to allow re-upload.
    // 4. If file exists, sets isVaultSynced = true and updates SQLite cache.
    LaunchedEffect(key, fileData.fileName) {
        isCheckingVaultStatus = true
        withContext(Dispatchers.IO) {
            try {
                val result = docRepository.getDocuments()
                result.onSuccess { serverDocs ->
                    val currentMeta = (if (effectivePath.isNotBlank()) dbHelper.getMetadata(effectivePath) else null)
                        ?: (if (effectiveUri.isNotBlank()) dbHelper.getMetadata(effectiveUri) else null)
                        ?: dbHelper.getMetadata(fileData.fileName)
                    val savedDocId = currentMeta?.vaultDocId

                    // Priority 1: Match by saved server document ID
                    val matchedDoc = serverDocs.firstOrNull { doc ->
                        savedDocId != null && doc.id == savedDocId
                    } ?: serverDocs.firstOrNull { doc ->
                        // Priority 2: Match by exact title AND matching location/path to distinguish identical names
                        val titleMatches = doc.title.equals(fileData.fileName, ignoreCase = true)
                        val locationMatches = when {
                            effectivePath.isNotBlank() && doc.location.equals(effectivePath, ignoreCase = true) -> true
                            effectivePath.isNotBlank() && doc.file_path?.contains(effectivePath) == true -> true
                            fileData.location.isNotBlank() && fileData.location != "Internal Storage" && doc.location.equals(fileData.location, ignoreCase = true) -> true
                            else -> false
                        }
                        titleMatches && locationMatches
                    } ?: if (serverDocs.count { it.title.equals(fileData.fileName, ignoreCase = true) } == 1) {
                        // Priority 3: Single matching document by title
                        serverDocs.firstOrNull { it.title.equals(fileData.fileName, ignoreCase = true) }
                    } else {
                        null
                    }

                    withContext(Dispatchers.Main) {
                        if (matchedDoc != null) {
                            isVaultSynced = true
                            dbHelper.setVaultStatus(key, fileData.fileName, true, matchedDoc.id)
                            if (effectivePath.isNotBlank()) {
                                dbHelper.setVaultStatus(effectivePath, fileData.fileName, true, matchedDoc.id)
                            }
                            if (effectiveUri.isNotBlank()) {
                                dbHelper.setVaultStatus(effectiveUri, fileData.fileName, true, matchedDoc.id)
                            }
                            dbHelper.setVaultStatus(fileData.fileName, fileData.fileName, true, matchedDoc.id)
                        } else {
                            // Document does not exist on server (deleted or never uploaded)
                            isVaultSynced = false
                            dbHelper.setVaultStatus(key, fileData.fileName, false, null)
                            if (effectivePath.isNotBlank()) {
                                dbHelper.setVaultStatus(effectivePath, fileData.fileName, false, null)
                            }
                            if (effectiveUri.isNotBlank()) {
                                dbHelper.setVaultStatus(effectiveUri, fileData.fileName, false, null)
                            }
                            dbHelper.setVaultStatus(fileData.fileName, fileData.fileName, false, null)
                        }
                    }
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                isCheckingVaultStatus = false
            }
        }
    }
    var showVaultConfirmDialog by remember { mutableStateOf(false) }
    var showOpenOptionsDialog by remember { mutableStateOf(false) }

    // Dialog: Choose between In-App Document Viewer or External App
    if (showOpenOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOpenOptionsDialog = false },
            title = {
                Text(
                    text = "Open Document",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = fileData.fileName,
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: View in IntelliFile (In-App)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(14.dp))
                            .clickable {
                                showOpenOptionsDialog = false
                                val now = System.currentTimeMillis()
                                dbHelper.recordFileOpened(key, fileData.fileName, now)
                                lastOpenedText = FileMetadataExtractor.formatLastOpened(now)
                                onOpenFileClick()
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "View in IntelliFile",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Read & view inside this app",
                                fontSize = 12.sp,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }

                    // Option 2: Open with External System App
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF9FAFB))
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
                            .clickable {
                                showOpenOptionsDialog = false
                                val now = System.currentTimeMillis()
                                dbHelper.recordFileOpened(key, fileData.fileName, now)
                                lastOpenedText = FileMetadataExtractor.formatLastOpened(now)
                                val localItem = LocalFileItem(
                                    title = fileData.fileName,
                                    path = fileData.path,
                                    uri = if (fileData.uriString.isNotBlank()) Uri.parse(fileData.uriString) else Uri.EMPTY,
                                    mimeType = LocalFileManager.resolveMimeType(fileData.fileName)
                                )
                                LocalFileManager.openFileWithSystem(context, localItem)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4B5563)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open with Another App",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Google Drive, PDF Reader, etc.",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOpenOptionsDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            }
        )
    }

    // Privacy confirmation dialog for server upload
    if (showVaultConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploadingToVault) showVaultConfirmDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color(0xFFF1EEF4),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF04B577),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add to IntelliSpace?",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }
            },
            text = {
                Text(
                    text = "Upload this file to IntelliSpace for AI analysis, content search, and quick summaries?",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isUploadingToVault) return@Button
                        showVaultConfirmDialog = false
                        isUploadingToVault = true
                        scope.launch {
                            val result = docRepository.uploadToVault(
                                filePath = effectivePath,
                                uriString = effectiveUri,
                                fileName = fileData.fileName,
                                category = fileData.category,
                                priority = selectedPriority?.name,
                                location = if (effectivePath.isNotBlank()) effectivePath else fileData.location
                            )
                            isUploadingToVault = false
                            result.onSuccess { doc ->
                                isVaultSynced = true
                                dbHelper.setVaultStatus(
                                    uriOrPath = key,
                                    fileName = fileData.fileName,
                                    isSynced = true,
                                    vaultDocId = doc.id
                                )
                                if (effectivePath.isNotBlank()) {
                                    dbHelper.setVaultStatus(
                                        uriOrPath = effectivePath,
                                        fileName = fileData.fileName,
                                        isSynced = true,
                                        vaultDocId = doc.id
                                    )
                                }
                                if (effectiveUri.isNotBlank()) {
                                    dbHelper.setVaultStatus(
                                        uriOrPath = effectiveUri,
                                        fileName = fileData.fileName,
                                        isSynced = true,
                                        vaultDocId = doc.id
                                    )
                                }
                                dbHelper.setVaultStatus(
                                    uriOrPath = fileData.fileName,
                                    fileName = fileData.fileName,
                                    isSynced = true,
                                    vaultDocId = doc.id
                                )
                                Toast.makeText(context, "${fileData.fileName} added to IntelliSpace successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "IntelliSpace upload failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0E8A8F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
                ) {
                    Text(
                        text = "Upload to IntelliSpace",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showVaultConfirmDialog = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF55606E)
                    )
                }
            }
        )
    }

    // IntelliSpace Indexing / Uploading Progress Dialog
    if (isUploadingToVault) {
        IntelliSpaceIndexingDialog(
            currentIndex = 1,
            totalCount = 1,
            fileName = fileData.fileName
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // 1. Header Bar with Back Button and Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "File Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.size(36.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. File Name, File Size, and File Format (Compact Identity Card)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEAEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = fileData.iconResId),
                        contentDescription = "File Icon",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileData.fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$fileSize · $fileFormat",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. IntelliSpace (Server Synced) Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isVaultSynced) Color(0xFFEFF6FF) else Color(0xFFF9FAFB))
                    .border(1.dp, if (isVaultSynced) Color(0xFFBFDBFE) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isVaultSynced) Icons.Default.CloudDone else Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = if (isVaultSynced) Color(0xFF2563EB) else Color(0xFF6B7280),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isVaultSynced) "In IntelliSpace (Server Synced)" else "Device File (Local Only)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVaultSynced) Color(0xFF1E40AF) else Color(0xFF374151),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = if (isVaultSynced) "AI semantic search & Q&A active" else "Kept private on phone",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isVaultSynced) {
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFFD1FAE5),
                                disabledContentColor = Color(0xFF065F46)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color(0xFF059669)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Added",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF065F46)
                            )
                        }
                    } else {
                        Button(
                            onClick = { showVaultConfirmDialog = true },
                            enabled = !isUploadingToVault && !isCheckingVaultStatus,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            if (isUploadingToVault || isCheckingVaultStatus) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Add to IntelliSpace", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. File Information Card (5 Dynamically Retrieved Rows)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF9FAFB))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. File Format
                    MetadataRow(
                        icon = Icons.Outlined.Description,
                        label = "File Format",
                        value = fileFormat
                    )

                    // 2. File Size
                    MetadataRow(
                        icon = Icons.Outlined.SdStorage,
                        label = "File Size",
                        value = fileSize
                    )

                    // 3. Page / Slide Count
                    val countIcon = when (fileFormat.uppercase()) {
                        "PPT", "PPTX", "PPS", "PPSX", "POT", "POTX" -> Icons.Outlined.Slideshow
                        "XLS", "XLSX", "CSV", "TSV", "ODS" -> Icons.Outlined.TableChart
                        else -> Icons.Outlined.AutoStories
                    }
                    MetadataRow(
                        icon = countIcon,
                        label = contentCountLabel,
                        value = contentCountValue
                    )

                    // 4. Date Added to Device
                    MetadataRow(
                        icon = Icons.Outlined.CalendarToday,
                        label = "Date Added to Device",
                        value = dateAddedText
                    )

                    // 5. Last Opened
                    MetadataRow(
                        icon = Icons.Outlined.Schedule,
                        label = "Last Opened",
                        value = lastOpenedText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. Priority Section with Important, Normal, and Low in a Single Horizontal Row
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Priority",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedPriority != null) "(${selectedPriority?.title})" else "(Not Set)",
                        fontSize = 12.sp,
                        color = if (selectedPriority != null) selectedPriority!!.textColor else Color(0xFF6B7280),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PriorityLevel.entries.forEach { priority ->
                        val isSelected = priority == selectedPriority
                        SelectablePriorityCard(
                            priority = priority,
                            isSelected = isSelected,
                            onClick = {
                                val newPriority = if (selectedPriority == priority) null else priority
                                selectedPriority = newPriority
                                val key = if (fileData.path.isNotBlank()) fileData.path else fileData.uriString
                                dbHelper.setPriority(
                                    uriOrPath = key,
                                    fileName = fileData.fileName,
                                    priority = newPriority
                                )
                                if (fileData.uriString.isNotBlank()) {
                                    dbHelper.setPriority(
                                        uriOrPath = fileData.uriString,
                                        fileName = fileData.fileName,
                                        priority = newPriority
                                    )
                                }
                                if (fileData.fileName.isNotBlank()) {
                                    dbHelper.setPriority(
                                        uriOrPath = fileData.fileName,
                                        fileName = fileData.fileName,
                                        priority = newPriority
                                    )
                                }
                                onPriorityChanged(newPriority)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Save Priority Button
            Button(
                onClick = {
                    val key = if (fileData.path.isNotBlank()) fileData.path else fileData.uriString
                    dbHelper.setPriority(
                        uriOrPath = key,
                        fileName = fileData.fileName,
                        priority = selectedPriority
                    )
                    if (fileData.uriString.isNotBlank()) {
                        dbHelper.setPriority(
                            uriOrPath = fileData.uriString,
                            fileName = fileData.fileName,
                            priority = selectedPriority
                        )
                    }
                    if (fileData.fileName.isNotBlank()) {
                        dbHelper.setPriority(
                            uriOrPath = fileData.fileName,
                            fileName = fileData.fileName,
                            priority = selectedPriority
                        )
                    }

                    // Sync priority to server if file is in IntelliSpace
                    val meta = dbHelper.getMetadata(key) ?: dbHelper.getMetadata(fileData.fileName)
                    val vaultId = meta?.vaultDocId
                    if (vaultId != null && selectedPriority != null) {
                        scope.launch {
                            docRepository.setPriority(vaultId, selectedPriority!!.name)
                        }
                    }

                    onPriorityChanged(selectedPriority)
                    Toast.makeText(
                        context,
                        if (selectedPriority != null) "Priority saved as ${selectedPriority?.title}!" else "Priority cleared!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Priority",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedPriority != null) "Save Priority (${selectedPriority?.title})" else "Save Priority",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Open File Button (Presents In-App Viewer or External Chooser)
            Button(
                onClick = { showOpenOptionsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Text(
                    text = "Open File",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 8. Share Button
            Button(
                onClick = onShareClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MetadataRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1.1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = label,
                fontSize = 12.5.sp,
                color = Color(0xFF6B7280),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun SelectablePriorityCard(
    priority: PriorityLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectableCardScale"
    )

    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 1.8.dp,
            color = priority.textColor,
            shape = RoundedCornerShape(14.dp)
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = priority.borderColor.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp)
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(
                elevation = if (isSelected) 4.dp else 1.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = if (isSelected) priority.glowColor else Color.Black.copy(alpha = 0.05f),
                spotColor = if (isSelected) priority.glowColor else Color.Black.copy(alpha = 0.05f),
                clip = false
            )
            .clip(RoundedCornerShape(14.dp))
            .background(brush = Brush.verticalGradient(priority.gradientColors))
            .then(borderModifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        // Specular 3D Glass Arc Highlight
        Canvas(modifier = Modifier.matchParentSize()) {
            val cornerRadius = 14.dp.toPx()
            val highlightWidth = size.width * 0.44f
            val highlightHeight = size.height * 0.32f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.80f),
                        Color.White.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(cornerRadius * 0.85f, cornerRadius * 0.80f),
                    radius = cornerRadius * 1.3f
                ),
                topLeft = Offset(4.dp.toPx(), 3.dp.toPx()),
                size = Size(highlightWidth, highlightHeight)
            )
        }

        // Selected checkmark indicator on top-right
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(priority.textColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = priority.badgeResId),
                contentDescription = priority.title,
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = priority.title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = priority.textColor,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileDetailScreenPreview() {
    FileDetailScreen()
}
