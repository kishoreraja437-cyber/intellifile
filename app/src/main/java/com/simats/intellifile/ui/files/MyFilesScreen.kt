package com.simats.intellifile.ui.files

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.DocumentDto
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.DocumentRepository
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.ui.text.style.TextAlign
import com.simats.intellifile.data.model.AiSummarizeResponse
import com.simats.intellifile.data.repository.AiRepository

import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PrioritySummaryCard
import kotlinx.coroutines.launch

enum class StorageSourceTab {
    DEVICE_FILES,
    AI_VAULT
}

@Composable
fun MyFilesScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onQuickAccessClick: (PriorityLevel) -> Unit = {},
    onNavigateToAiChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val docRepo = remember { DocumentRepository(context) }
    val aiRepo = remember { AiRepository(context) }
    val dbHelper = remember { LocalFileDatabaseHelper.getInstance(context) }

    var selectedSource by remember { mutableStateOf(StorageSourceTab.DEVICE_FILES) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var realFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoadingDeviceFiles by remember { mutableStateOf(true) }

    // AI Vault State
    var vaultDocuments by remember { mutableStateOf<List<DocumentDto>>(emptyList()) }
    var isVaultLoading by remember { mutableStateOf(false) }
    var isUploadingToVault by remember { mutableStateOf(false) }
    var selectedSummaryDoc by remember { mutableStateOf<DocumentDto?>(null) }
    var isGeneratingInsights by remember { mutableStateOf(false) }
    var aiInsightsResult by remember { mutableStateOf<AiSummarizeResponse?>(null) }
    var aiInsightsError by remember { mutableStateOf<String?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentDto?>(null) }

    // Function to generate and load AI Insights
    fun loadInsightsForDoc(doc: DocumentDto) {
        selectedSummaryDoc = doc
        isGeneratingInsights = true
        aiInsightsResult = null
        aiInsightsError = null

        coroutineScope.launch {
            var contentToSummarize = doc.content_text
            if (contentToSummarize.isNullOrBlank()) {
                val matchingLocal = realFiles.find { it.title.equals(doc.title, ignoreCase = true) }
                if (matchingLocal != null) {
                    try {
                        contentToSummarize = LocalFileManager.readTextContent(
                            context = context,
                            uriString = matchingLocal.uri.toString(),
                            path = matchingLocal.path,
                            format = matchingLocal.format
                        )
                    } catch (_: Exception) {}
                }
            }

            val res = aiRepo.summarizeDocument(
                documentId = if (doc.id > 0) doc.id else null,
                title = doc.title,
                contentText = contentToSummarize
            )

            isGeneratingInsights = false
            res.onSuccess { summaryResponse ->
                aiInsightsResult = summaryResponse
            }.onFailure { ex ->
                if (!contentToSummarize.isNullOrBlank()) {
                    val rawSentences = contentToSummarize.split(Regex("(?<=[.!?\\n])\\s+")).filter { it.isNotBlank() }
                    val bullets = rawSentences.take(4).map { it.trim().trimStart('-', '*', '•').trim() }
                    val fallbackSummary = if (rawSentences.size > 2) rawSentences.take(2).joinToString(" ") else contentToSummarize.take(300)
                    aiInsightsResult = AiSummarizeResponse(
                        success = true,
                        title = doc.title,
                        summary = fallbackSummary,
                        bullet_points = bullets,
                        sentence_count = bullets.size
                    )
                } else {
                    aiInsightsError = ex.message ?: "Failed to generate AI insights"
                }
            }
        }
    }

    // Query real local document files
    LaunchedEffect(refreshTrigger) {
        isLoadingDeviceFiles = true
        realFiles = LocalFileManager.getDeviceFiles(context)
        isLoadingDeviceFiles = false
    }

    // Function to load AI Vault documents
    fun loadVault() {
        coroutineScope.launch {
            isVaultLoading = true
            val result = docRepo.getDocuments()
            isVaultLoading = false
            result.onSuccess { docs ->
                vaultDocuments = docs
            }.onFailure {
                // If offline or network issue, keep any existing
            }
        }
    }

    // Load vault docs when switching to AI Vault tab
    LaunchedEffect(selectedSource) {
        if (selectedSource == StorageSourceTab.AI_VAULT) {
            loadVault()
        }
    }

    // SAF Document Picker to upload directly to AI Vault
    val uploadDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "Document"
                }
            }
            isUploadingToVault = true
            coroutineScope.launch {
                val result = docRepo.uploadToVault(
                    filePath = "",
                    uriString = uri.toString(),
                    fileName = fileName
                )
                isUploadingToVault = false
                result.onSuccess {
                    dbHelper.setVaultStatus(uri.toString(), fileName, true)
                    Toast.makeText(context, "\"$fileName\" added to AI Vault successfully!", Toast.LENGTH_SHORT).show()
                    loadVault()
                    refreshTrigger++
                }.onFailure { ex ->
                    Toast.makeText(context, "Upload failed: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val totalFiles = realFiles.size
    val importantCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.IMPORTANT } }
    val normalCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.NORMAL } }
    val lowCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.LOW } }
    val unprioritizedCount = remember(realFiles) { realFiles.count { it.priority == null } }

    val vaultFilesCount = vaultDocuments.size.coerceAtLeast(realFiles.count { it.isVaultSynced })
    val totalPrioritized = importantCount + normalCount + lowCount

    val importantSweep = if (totalPrioritized > 0) (importantCount.toFloat() / totalPrioritized) * 360f else 0f
    val normalSweep = if (totalPrioritized > 0) (normalCount.toFloat() / totalPrioritized) * 360f else 0f
    val lowSweep = if (totalPrioritized > 0) (lowCount.toFloat() / totalPrioritized) * 360f else 0f

    // AI Document Insights Modal Dialog
    selectedSummaryDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = {
                selectedSummaryDoc = null
                aiInsightsResult = null
                aiInsightsError = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Document Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Neural Analysis & Summarization",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Document Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = doc.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE0E7FF))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = doc.format, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3730A3))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (doc.priority.uppercase()) {
                                                "IMPORTANT" -> Color(0xFFFEE2E2)
                                                "NORMAL" -> Color(0xFFDEF7EC)
                                                else -> Color(0xFFF3F4F6)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = doc.priority,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (doc.priority.uppercase()) {
                                            "IMPORTANT" -> Color(0xFF991B1B)
                                            "NORMAL" -> Color(0xFF03543F)
                                            else -> Color(0xFF374151)
                                        }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF3F4F6))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = doc.file_size, fontSize = 11.sp, color = Color(0xFF4B5563))
                                }
                            }
                        }
                    }

                    if (isGeneratingInsights) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Analyzing document with AI...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Extracting executive summary and key points",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    } else if (aiInsightsResult != null) {
                        val insights = aiInsightsResult!!

                        // 1. Executive Summary Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0FDF4))
                                .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Executive Summary",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = insights.summary ?: "Summary generated successfully.",
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B),
                                lineHeight = 19.sp
                            )
                        }

                        // 2. Key Highlights / Bullet points
                        if (insights.bullet_points.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = Color(0xFFEAB308),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Key Takeaways & Points",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF334155)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                insights.bullet_points.forEachIndexed { index, point ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = point,
                                            fontSize = 12.sp,
                                            color = Color(0xFF374151),
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Document Analysis Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Pages", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    Text("${doc.total_pages}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Engine", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    Text("Neural NLP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Vault Status", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    Text("Indexed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                }
                            }
                        }

                        // 4. Raw Extracted Preview (if available)
                        if (!doc.content_text.isNullOrBlank()) {
                            var showRawText by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showRawText = !showRawText },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Extracted Text Preview",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = if (showRawText) "Hide" else "Show",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (showRawText) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = doc.content_text.take(600) + if (doc.content_text.length > 600) "..." else "",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else if (aiInsightsError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEF2F2))
                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Unable to Generate Insights",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiInsightsError ?: "Unknown error occurred.",
                                fontSize = 12.sp,
                                color = Color(0xFF7F1D1D),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { loadInsightsForDoc(doc) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry Analysis", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val docTitle = doc.title
                        selectedSummaryDoc = null
                        aiInsightsResult = null
                        onNavigateToAiChat(docTitle)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat with AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSummaryDoc = null
                        aiInsightsResult = null
                        aiInsightsError = null
                    }
                ) {
                    Text("Close", color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = {
                Text("Remove from AI Vault?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to remove \"${doc.title}\" from the AI Vault? The file on your phone will not be deleted.",
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = doc
                        docToDelete = null
                        coroutineScope.launch {
                            val res = docRepo.deleteDocument(toDelete.id)
                            res.onSuccess {
                                Toast.makeText(context, "\"${toDelete.title}\" removed from vault", Toast.LENGTH_SHORT).show()
                                loadVault()
                            }.onFailure { err ->
                                Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Remove", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Cancel")
                }
            }
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
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = {
                    refreshTrigger++
                    if (selectedSource == StorageSourceTab.AI_VAULT) loadVault()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                // Page Title
                item {
                    Text(
                        text = "My Files",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Storage Source Selector: Device Files vs AI Vault
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        // Device Files Tab
                        val isDeviceSelected = selectedSource == StorageSourceTab.DEVICE_FILES
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDeviceSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { selectedSource = StorageSourceTab.DEVICE_FILES }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isDeviceSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Device Files ($totalFiles)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDeviceSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // AI Vault Tab
                        val isVaultSelected = selectedSource == StorageSourceTab.AI_VAULT
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isVaultSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { selectedSource = StorageSourceTab.AI_VAULT }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isVaultSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Vault ($vaultFilesCount)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVaultSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedSource == StorageSourceTab.DEVICE_FILES) {
                    // Summary Cards Row with Real User-Marked Counts
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrioritySummaryCard(
                                priority = PriorityLevel.IMPORTANT,
                                count = importantCount,
                                onClick = { onQuickAccessClick(PriorityLevel.IMPORTANT) },
                                modifier = Modifier.weight(1f)
                            )
                            PrioritySummaryCard(
                                priority = PriorityLevel.NORMAL,
                                count = normalCount,
                                onClick = { onQuickAccessClick(PriorityLevel.NORMAL) },
                                modifier = Modifier.weight(1f)
                            )
                            PrioritySummaryCard(
                                priority = PriorityLevel.LOW,
                                count = lowCount,
                                onClick = { onQuickAccessClick(PriorityLevel.LOW) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Total Files Card with Real Document Count
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Documents",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$totalPrioritized prioritized · $unprioritizedCount unassigned",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }

                            Text(
                                text = totalFiles.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                        }
                    }

                    // Priority Distribution Donut Chart Section
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Priority Distribution",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Donut Chart
                                Box(
                                    modifier = Modifier.size(130.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 22.dp.toPx()
                                        val startAngleOffset = -90f

                                        if (totalPrioritized == 0) {
                                            drawArc(
                                                color = Color(0xFFE5E7EB),
                                                startAngle = 0f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = strokeWidth)
                                            )
                                        } else {
                                            if (importantSweep > 0) {
                                                drawArc(
                                                    color = Color(0xFFEF4444),
                                                    startAngle = startAngleOffset,
                                                    sweepAngle = importantSweep,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth)
                                                )
                                            }
                                            if (normalSweep > 0) {
                                                drawArc(
                                                    color = Color(0xFFF59E0B),
                                                    startAngle = startAngleOffset + importantSweep,
                                                    sweepAngle = normalSweep,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth)
                                                )
                                            }
                                            if (lowSweep > 0) {
                                                drawArc(
                                                    color = Color(0xFF10B981),
                                                    startAngle = startAngleOffset + importantSweep + normalSweep,
                                                    sweepAngle = lowSweep,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = totalPrioritized.toString(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                        Text(
                                            text = "Marked",
                                            fontSize = 12.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(20.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    DistributionLegendRow(
                                        dotColor = Color(0xFFEF4444),
                                        label = "Important",
                                        countText = "$importantCount files"
                                    )
                                    DistributionLegendRow(
                                        dotColor = Color(0xFFF59E0B),
                                        label = "Normal",
                                        countText = "$normalCount files"
                                    )
                                    DistributionLegendRow(
                                        dotColor = Color(0xFF10B981),
                                        label = "Low",
                                        countText = "$lowCount files"
                                    )
                                }
                            }
                        }
                    }

                    // Quick Access Section
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Quick Access",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )

                            QuickAccessCard(
                                label = "Important Files ($importantCount)",
                                badgeColor = Color(0xFFEF4444),
                                ringColor = Color(0xFFFFEAEE),
                                onClick = { onQuickAccessClick(PriorityLevel.IMPORTANT) }
                            )

                            QuickAccessCard(
                                label = "Normal Files ($normalCount)",
                                badgeColor = Color(0xFFD97706),
                                ringColor = Color(0xFFFFF6D8),
                                onClick = { onQuickAccessClick(PriorityLevel.NORMAL) }
                            )

                            QuickAccessCard(
                                label = "Low Priority Files ($lowCount)",
                                badgeColor = Color(0xFF10B981),
                                ringColor = Color(0xFFE2F7ED),
                                onClick = { onQuickAccessClick(PriorityLevel.LOW) }
                            )
                        }
                    }
                } else {
                    // ==========================================
                    // FULL AI VAULT TAB INTEGRATION
                    // ==========================================

                    // Header & Upload to AI Vault Card
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(20.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "AI Document Vault",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                        Text(
                                            text = "$vaultFilesCount documents indexed with AI",
                                            fontSize = 13.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                }
                            }

                            // Upload to Vault Button
                            Button(
                                onClick = {
                                    uploadDocLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "text/plain"
                                        )
                                    )
                                },
                                enabled = !isUploadingToVault,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isUploadingToVault) "Uploading & Extracting..." else "Upload Document to AI Vault",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            if (isUploadingToVault) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Vault Documents List Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Vault Documents (${vaultDocuments.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            if (isVaultLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Empty State or List of Vault Documents
                    if (vaultDocuments.isEmpty() && !isVaultLoading) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Your AI Vault is Empty",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1F2937)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Upload documents to extract text, run semantic Q&A, and get instant summaries.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6B7280),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        items(vaultDocuments) { doc ->
                            VaultDocumentCard(
                                doc = doc,
                                onViewSummaryClick = { loadInsightsForDoc(doc) },
                                onDeleteClick = { docToDelete = doc }
                            )
                        }
                    }

                    // Privacy Guarantee Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF0FDF4))
                                .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Privacy Protected & Encrypted",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Your device documents are kept strictly local by default. Documents only enter the AI Vault when you explicitly tap 'Add to AI Vault'.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534),
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    // AI Vault Capabilities Card
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "AI Vault Capabilities",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            VaultBenefitItem(
                                title = "Deep Content Extraction",
                                desc = "Full text extraction for PDF, DOCX, XLSX, and TXT files."
                            )
                            VaultBenefitItem(
                                title = "Semantic AI Search & Q&A",
                                desc = "Search by concept and ask questions in natural language."
                            )
                            VaultBenefitItem(
                                title = "Executive Key Summaries",
                                desc = "Instant key-takeaways and outlines generated by the local AI engine."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultDocumentCard(
    doc: DocumentDto,
    onViewSummaryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${doc.format} · ${doc.file_size} · ${doc.created_at?.take(10) ?: "Recent"}",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from Vault",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Status and Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFDEF7EC))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (doc.has_text) "AI Ready" else "Indexed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF03543F)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = doc.priority,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4B5563)
                        )
                    }
                }

                Button(
                    onClick = onViewSummaryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI Insights",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultBenefitItem(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF2563EB))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937)
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun DistributionLegendRow(
    dotColor: Color,
    label: String,
    countText: String,
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color(0xFF4B5563)
            )
        }

        Text(
            text = countText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827)
        )
    }
}

@Composable
fun QuickAccessCard(
    label: String,
    badgeColor: Color,
    ringColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MyFilesScreenPreview() {
    MyFilesScreen()
}
