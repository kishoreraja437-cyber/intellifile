package com.simats.intellifile.ui.detail

import android.net.Uri
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
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
    val isVaultSynced: Boolean = false
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
    var isUploadingToVault by remember { mutableStateOf(false) }
    var selectedPriority by remember(fileData.priority) { mutableStateOf(fileData.priority) }
    var isVaultSynced by remember(fileData.isVaultSynced) { mutableStateOf(fileData.isVaultSynced) }
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
            onDismissRequest = { showVaultConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to AI Vault?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "IntelliFile keeps your files strictly local on your phone by default.\n\n" +
                            "Uploading \"${fileData.fileName}\" to the AI Vault enables deep text extraction, " +
                            "semantic question answering, and AI summarization. Do you want to proceed with uploading this specific file?",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF374151)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVaultConfirmDialog = false
                        isUploadingToVault = true
                        scope.launch {
                            val result = docRepository.uploadToVault(
                                filePath = fileData.path,
                                uriString = fileData.uriString,
                                fileName = fileData.fileName,
                                category = fileData.category,
                                priority = selectedPriority?.name
                            )
                            isUploadingToVault = false
                            result.onSuccess {
                                isVaultSynced = true
                                val key = if (fileData.path.isNotBlank()) fileData.path else fileData.uriString
                                LocalFileDatabaseHelper.getInstance(context).setVaultStatus(
                                    uriOrPath = key,
                                    fileName = fileData.fileName,
                                    isSynced = true
                                )
                                Toast.makeText(context, "${fileData.fileName} uploaded to AI Vault successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "AI Vault upload failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Upload to Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVaultConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "File Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = onMoreOptionsClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large File Icon
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFFEAEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = fileData.iconResId),
                        contentDescription = "File Icon",
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title and Subtitle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = fileData.fileName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${fileData.fileSize} · ${fileData.fileType}",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Vault Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isVaultSynced) Color(0xFFEFF6FF) else Color(0xFFF9FAFB))
                    .border(1.dp, if (isVaultSynced) Color(0xFFBFDBFE) else Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                    .padding(16.dp)
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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isVaultSynced) "In AI Vault (Server Synced)" else "Device File (Local Only)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVaultSynced) Color(0xFF1E40AF) else Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isVaultSynced) "AI semantic search & Q&A active" else "Kept private on phone",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    if (!isVaultSynced) {
                        Button(
                            onClick = { showVaultConfirmDialog = true },
                            enabled = !isUploadingToVault,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isUploadingToVault) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Add to Vault", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metadata Attributes List
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetadataRow(
                    icon = Icons.Outlined.CalendarToday,
                    label = "Created",
                    value = fileData.createdDate
                )
                MetadataRow(
                    icon = Icons.Outlined.Folder,
                    label = "Modified",
                    value = fileData.modifiedDate
                )
                MetadataRow(
                    icon = Icons.Outlined.Folder,
                    label = "Category",
                    value = fileData.category
                )
                MetadataRow(
                    icon = Icons.Outlined.Adjust,
                    label = "Location",
                    value = fileData.location
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Priority Section Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetPriorityClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Priority",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPriority != null) "(${selectedPriority?.title})" else "(Not Set)",
                        fontSize = 13.sp,
                        color = if (selectedPriority != null) selectedPriority!!.textColor else Color(0xFF6B7280),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Change",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Priority Cards Selection Row (User explicitly assigns or toggles off)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            LocalFileDatabaseHelper.getInstance(context).setPriority(
                                uriOrPath = key,
                                fileName = fileData.fileName,
                                priority = newPriority
                            )
                            onPriorityChanged(newPriority)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Open File Button (Presents In-App Viewer or External Chooser)
            Button(
                onClick = { showOpenOptionsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Open File",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onShareClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                fontSize = 15.sp,
                color = Color(0xFF6B7280)
            )
        }

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4B5563),
            maxLines = 1
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
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 1.5.dp,
            color = priority.textColor,
            shape = RoundedCornerShape(20.dp)
        )
    } else Modifier

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(priority.backgroundColor)
            .then(borderModifier)
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(priority.iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = priority.icon,
                contentDescription = priority.title,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = priority.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = priority.textColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FileDetailScreenPreview() {
    FileDetailScreen()
}
