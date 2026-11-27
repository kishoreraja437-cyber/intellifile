package com.simats.intellifile.ui.dialog

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.simats.intellifile.data.manager.ZipExtractor
import com.simats.intellifile.ui.detail.FileDetailData
import kotlinx.coroutines.launch
import java.io.File

private enum class ExtractionStage {
    CONFIGURE,
    EXTRACTING,
    SUCCESS,
    ERROR
}

private enum class DestinationOption(val label: String) {
    SAME_LOCATION("Same location as ZIP"),
    DOWNLOADS("Downloads folder"),
    DOCUMENTS("Documents folder")
}

@Composable
fun ZipExtractionDialog(
    zipFileData: FileDetailData,
    onDismiss: () -> Unit,
    onExtractionComplete: (File) -> Unit
) {
    ZipExtractionDialog(
        zipFileName = zipFileData.fileName,
        zipFilePath = zipFileData.path,
        zipUriString = zipFileData.uriString,
        zipFileSize = zipFileData.fileSize,
        onDismiss = onDismiss,
        onOpenExtractedFolder = onExtractionComplete
    )
}

@Composable
fun ZipExtractionDialog(
    zipFileName: String,
    zipFilePath: String,
    zipUriString: String,
    zipFileSize: String = "",
    onDismiss: () -> Unit,
    onOpenExtractedFolder: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(ExtractionStage.CONFIGURE) }
    var folderName by remember {
        mutableStateOf(zipFileName.removeSuffix(".zip").removeSuffix(".ZIP").ifBlank { "Extracted_Folder" })
    }
    var createSubfolder by remember { mutableStateOf(true) }
    var selectedDestination by remember { mutableStateOf(DestinationOption.SAME_LOCATION) }

    // Progress state
    var currentExtractFile by remember { mutableStateOf("") }
    var extractCount by remember { mutableIntStateOf(0) }
    var totalExtractCount by remember { mutableIntStateOf(1) }
    var extractPercentage by remember { mutableIntStateOf(0) }

    // Result state
    var resultFolder by remember { mutableStateOf<File?>(null) }
    var resultFilesCount by remember { mutableIntStateOf(0) }
    var resultSubfoldersCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Resolve the destination parent directory based on option
    fun resolveParentDir(): File {
        return when (selectedDestination) {
            DestinationOption.SAME_LOCATION -> {
                ZipExtractor.getDefaultDestinationFolder(context, if (zipFilePath.isNotBlank()) zipFilePath else zipUriString)
            }
            DestinationOption.DOWNLOADS -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
            }
            DestinationOption.DOCUMENTS -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
            }
        }
    }

    Dialog(onDismissRequest = {
        if (stage != ExtractionStage.EXTRACTING) onDismiss()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                when (stage) {
                    ExtractionStage.CONFIGURE -> {
                        // Title Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE0F2F1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = Color(0xFF00897B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Extract ZIP Archive",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = if (zipFileSize.isNotBlank()) "$zipFileName · $zipFileSize" else zipFileName,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Folder Name Input
                        Text(
                            text = "Extraction Folder Name",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00897B),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                cursorColor = Color(0xFF00897B)
                            ),
                            placeholder = { Text("Enter folder name") }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Destination Choice
                        Text(
                            text = "Destination Location",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DestinationOption.values().forEach { option ->
                                val isSelected = option == selectedDestination
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFFE0F2F1) else Color(0xFFF8FAFC))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF80CBC4) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedDestination = option }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDestination = option },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00897B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF00695C) else Color(0xFF475569)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Create Subfolder Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { createSubfolder = !createSubfolder }
                        ) {
                            Checkbox(
                                checked = createSubfolder,
                                onCheckedChange = { createSubfolder = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00897B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create dedicated folder for contents",
                                fontSize = 12.5.sp,
                                color = Color(0xFF475569)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text("Cancel", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (folderName.isBlank() && createSubfolder) {
                                        Toast.makeText(context, "Please enter a folder name", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    stage = ExtractionStage.EXTRACTING
                                    scope.launch {
                                        val parentDir = resolveParentDir()
                                        val targetUriOrPath = if (zipFilePath.isNotBlank()) zipFilePath else zipUriString
                                        val res = ZipExtractor.extractZip(
                                            context = context,
                                            zipUriOrPath = targetUriOrPath,
                                            zipDisplayName = zipFileName,
                                            targetParentDir = parentDir,
                                            folderName = folderName,
                                            createSubfolder = createSubfolder,
                                            onProgress = { p ->
                                                currentExtractFile = p.currentFileName
                                                extractCount = p.extractedCount
                                                totalExtractCount = p.totalCount
                                                extractPercentage = p.percentage
                                            }
                                        )

                                        if (res.success && res.extractedFolder != null) {
                                            resultFolder = res.extractedFolder
                                            resultFilesCount = res.totalFiles
                                            resultSubfoldersCount = res.totalSubfolders
                                            stage = ExtractionStage.SUCCESS
                                        } else {
                                            errorMessage = res.errorMessage ?: "Unknown extraction error."
                                            stage = ExtractionStage.ERROR
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Extract ZIP", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    ExtractionStage.EXTRACTING -> {
                        // Progress Layout
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = Color(0xFF00897B)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Extracting ZIP Archive...",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (currentExtractFile.isNotBlank()) "Extracting: $currentExtractFile" else "Preparing extraction...",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            LinearProgressIndicator(
                                progress = { extractPercentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF00897B),
                                trackColor = Color(0xFFE2E8F0)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$extractCount of $totalExtractCount files",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "$extractPercentage%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00897B)
                                )
                            }
                        }
                    }

                    ExtractionStage.SUCCESS -> {
                        // Success Layout
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Extraction Complete!",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Successfully extracted $resultFilesCount files${if (resultSubfoldersCount > 0) " and $resultSubfoldersCount subfolders" else ""} into '${resultFolder?.name}'.",
                                fontSize = 13.sp,
                                color = Color(0xFF475569),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Done", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        onDismiss()
                                        resultFolder?.let { onOpenExtractedFolder(it) }
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Folder", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    ExtractionStage.ERROR -> {
                        // Error Layout
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Extraction Failed",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = errorMessage ?: "An unexpected error occurred during extraction.",
                                fontSize = 13.sp,
                                color = Color(0xFFB91C1C),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
