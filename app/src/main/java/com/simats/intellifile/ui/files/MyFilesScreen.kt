package com.simats.intellifile.ui.files

import android.net.Uri
import com.simats.intellifile.ui.common.AnimatedMicrophoneButton
import com.simats.intellifile.ui.common.AnimatedRefreshButton
import com.simats.intellifile.ui.common.FilesLoadingCard
import com.simats.intellifile.ui.common.IntelliSpaceIndexingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import com.simats.intellifile.R
import com.simats.intellifile.ui.detail.FileDetailData
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.simats.intellifile.ui.common.DraggableScrollbar
import kotlinx.coroutines.launch

enum class StorageSourceTab {
    DEVICE_FILES,
    INTELLISPACE
}

@Composable
fun MyFilesScreen(
    modifier: Modifier = Modifier,
    priorityUpdateVersion: Int = 0,
    onBackClick: () -> Unit = {},
    onQuickAccessClick: (PriorityLevel) -> Unit = {},
    onNavigateToAiChat: (String) -> Unit = {},
    onMicClick: () -> Unit = {},
    onOpenFile: (FileDetailData) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val docRepo = remember { DocumentRepository(context) }
    val aiRepo = remember { AiRepository(context) }
    val dbHelper = remember { LocalFileDatabaseHelper.getInstance(context) }

    var selectedSource by remember { mutableStateOf(StorageSourceTab.DEVICE_FILES) }
    val listState = rememberLazyListState()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var realFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoadingDeviceFiles by remember { mutableStateOf(true) }

    // IntelliSpace State
    var vaultDocuments by remember {
        mutableStateOf<List<DocumentDto>>(DocumentRepository.getCachedVaultDocs(context))
    }
    var isVaultLoading by remember { mutableStateOf(false) }
    var isUploadingToVault by remember { mutableStateOf(false) }
    var selectedSummaryDoc by remember { mutableStateOf<DocumentDto?>(null) }
    var isGeneratingInsights by remember { mutableStateOf(false) }
    var aiInsightsResult by remember { mutableStateOf<AiSummarizeResponse?>(null) }
    var aiInsightsError by remember { mutableStateOf<String?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentDto?>(null) }
    var resolvingDocId by remember { mutableIntStateOf(-1) }

    // Function to load IntelliSpace documents
    fun loadVault() {
        coroutineScope.launch {
            isVaultLoading = true
            val result = docRepo.getDocuments()
            if (vaultDocuments.isNotEmpty()) {
                delay(400)
            }
            isVaultLoading = false
            result.onSuccess { docs ->
                val distinct = docs.filter { it.id > 0 && it.title.isNotBlank() }
                    .distinctBy { it.id }
                    .distinctBy { it.title.trim().lowercase() }
                vaultDocuments = distinct
                DocumentRepository.updateCachedVaultDocs(context, distinct)
            }.onFailure {
                // If offline or network issue, keep any existing cached
                if (vaultDocuments.isEmpty()) {
                    vaultDocuments = DocumentRepository.getCachedVaultDocs(context)
                }
            }
        }
    }

    // Function to generate and load AI Insights
    fun loadInsightsForDoc(doc: DocumentDto, retry: Boolean = false) {
        selectedSummaryDoc = doc
        aiInsightsError = null

        // Instant render if already available in DocumentDto and not retrying
        if (!retry && doc.insight_status.equals("READY", ignoreCase = true) && !doc.insight_overview.isNullOrBlank()) {
            isGeneratingInsights = false
            val effectiveInsights = if (doc.insight_insights.isNotEmpty()) doc.insight_insights else doc.insight_details
            aiInsightsResult = AiSummarizeResponse(
                success = true,
                title = doc.title,
                format = doc.format,
                status = "READY",
                document_overview = doc.document_overview ?: doc.insight_overview,
                overview = doc.insight_overview,
                main_topics = doc.insight_topics,
                key_points = doc.insight_key_points,
                important_terms = doc.insight_terms,
                important_insights = effectiveInsights,
                important_details = effectiveInsights,
                conclusion = doc.insight_conclusion,
                summary = doc.insight_overview,
                bullet_points = doc.insight_key_points,
                sentence_count = doc.insight_key_points.size
            )
            return
        }

        isGeneratingInsights = true
        aiInsightsResult = null

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
                contentText = contentToSummarize,
                retry = retry
            )

            isGeneratingInsights = false
            res.onSuccess { summaryResponse ->
                aiInsightsResult = summaryResponse
                loadVault()
            }.onFailure { ex ->
                aiInsightsError = ex.message ?: "Failed to generate AI insights. Please tap retry."
            }
        }
    }

    // Query real local document files and IntelliSpace documents asynchronously
    LaunchedEffect(refreshTrigger, priorityUpdateVersion) {
        isLoadingDeviceFiles = true
        loadVault()
        val loaded = withContext(Dispatchers.IO) {
            LocalFileManager.getDeviceFiles(context)
        }
        if (realFiles.isNotEmpty()) {
            delay(400)
        }
        realFiles = loaded
        isLoadingDeviceFiles = false
    }

    // Ensure vault docs are refreshed when switching between tabs
    LaunchedEffect(selectedSource) {
        if (selectedSource == StorageSourceTab.INTELLISPACE) {
            loadVault()
        }
    }

    // SAF Document Picker to upload directly to IntelliSpace (supports multi-selection of 2+ files)
    var uploadProgressMessage by remember { mutableStateOf("") }
    var vaultUploadCurrentIndex by remember { mutableIntStateOf(0) }
    var vaultUploadTotalCount by remember { mutableIntStateOf(0) }
    val uploadDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isUploadingToVault = true
            vaultUploadCurrentIndex = 0
            vaultUploadTotalCount = uris.size
            uploadProgressMessage = "Moving ${uris.size} document${if (uris.size > 1) "s" else ""} to IntelliSpace..."
            coroutineScope.launch {
                var successCount = 0
                var lastError: String? = null
                uris.forEachIndexed { index, uri ->
                    vaultUploadCurrentIndex = index + 1
                    var fileName = "Document_${index + 1}"
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIndex) ?: fileName
                            }
                        }
                    } catch (_: Exception) {}

                    uploadProgressMessage = "Uploading & Extracting content (${index + 1}/${uris.size}): $fileName..."

                    val result = docRepo.uploadToVault(
                        filePath = "",
                        uriString = uri.toString(),
                        fileName = fileName
                    )
                    result.onSuccess { uploadedDoc ->
                        uploadProgressMessage = "AI Insight Ready for: $fileName"
                        dbHelper.setVaultStatus(uri.toString(), fileName, true, uploadedDoc.id)
                        successCount++
                    }.onFailure { ex ->
                        lastError = ex.message
                    }
                }
                isUploadingToVault = false
                uploadProgressMessage = ""
                if (successCount > 0) {
                    val msg = if (successCount == 1) {
                        "1 document moved to IntelliSpace successfully!"
                    } else {
                        "$successCount documents moved to IntelliSpace successfully!"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    loadVault()
                    refreshTrigger++
                } else {
                    Toast.makeText(context, "Upload failed: ${lastError ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val totalFiles = realFiles.size
    val importantCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.IMPORTANT } }
    val normalCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.NORMAL } }
    val lowCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.LOW } }
    val unprioritizedCount = remember(realFiles) { realFiles.count { it.priority == null } }

    // Deduplicate vault documents by ID and title to accurately count stored files without duplicates
    val distinctVaultDocs = remember(vaultDocuments) {
        vaultDocuments
            .filter { it.id > 0 && it.title.isNotBlank() }
            .distinctBy { it.id }
            .distinctBy { it.title.trim().lowercase() }
    }
    // Single source of truth: actual IntelliSpace stored files count
    val vaultFilesCount = distinctVaultDocs.size
    val totalPrioritized = importantCount + normalCount + lowCount

    val importantSweep = if (totalPrioritized > 0) (importantCount.toFloat() / totalPrioritized) * 360f else 0f
    val normalSweep = if (totalPrioritized > 0) (normalCount.toFloat() / totalPrioritized) * 360f else 0f
    val lowSweep = if (totalPrioritized > 0) (lowCount.toFloat() / totalPrioritized) * 360f else 0f

    val animImportantSweep by animateFloatAsState(
        targetValue = importantSweep,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "animImportantSweep"
    )
    val animNormalSweep by animateFloatAsState(
        targetValue = normalSweep,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "animNormalSweep"
    )
    val animLowSweep by animateFloatAsState(
        targetValue = lowSweep,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "animLowSweep"
    )

    val refreshRotation = remember { Animatable(0f) }
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            refreshRotation.animateTo(
                targetValue = refreshRotation.value + 360f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
    }

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
                            .background(Color(0xFF2563EB).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Document Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Universal IntelliSpace Intelligence",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFDEF7EC))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (doc.insight_status.equals("FAILED", ignoreCase = true)) "Failed" else "Insight Ready",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (doc.insight_status.equals("FAILED", ignoreCase = true)) Color(0xFF991B1B) else Color(0xFF03543F)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Document Header Summary Card
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                        .background(Color(0xFFF3F4F6))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = doc.file_size, fontSize = 11.sp, color = Color(0xFF4B5563))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF3F4F6))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "${doc.total_pages} Page${if (doc.total_pages > 1) "s" else ""}", fontSize = 11.sp, color = Color(0xFF4B5563))
                                }
                            }
                        }
                    }

                    if (isGeneratingInsights) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting & Analyzing Content...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Generating universal 6-section insight across ${doc.format}",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    } else if (aiInsightsResult != null) {
                        val insights = aiInsightsResult!!

                        // 1. DOCUMENT OVERVIEW SECTION
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
                                    text = "1. Document Overview",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = insights.effectiveDocumentOverview.ifBlank { "Overview generated from document content." },
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B),
                                lineHeight = 19.sp
                            )
                        }

                        // 2. MAIN TOPICS SECTION
                        if (insights.effectiveMainTopics.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEEF2FF))
                                    .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF4338CA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "2. Main Topics",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF3730A3)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    insights.effectiveMainTopics.forEach { topic ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF4F46E5))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = topic,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF312E81)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. KEY POINTS SECTION
                        if (insights.effectiveKeyPoints.isNotEmpty()) {
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
                                        text = "3. Key Points",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF334155)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                insights.effectiveKeyPoints.forEachIndexed { index, point ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 3.dp)
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

                        // 4. IMPORTANT TERMS SECTION
                        if (insights.effectiveImportantTerms.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFDF4FF))
                                    .border(1.dp, Color(0xFFF0ABFC), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = Color(0xFFC026D3),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "4. Important Terms",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF86198F)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                insights.effectiveImportantTerms.forEach { termItem ->
                                    val parts = termItem.split("—", limit = 2)
                                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Text(
                                            text = parts[0].trim(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF701A75)
                                        )
                                        if (parts.size > 1) {
                                            Text(
                                                text = parts[1].trim(),
                                                fontSize = 11.sp,
                                                color = Color(0xFF4A044E),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. IMPORTANT INSIGHTS SECTION
                        if (insights.effectiveImportantInsights.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFFBEB))
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "5. Important Insights",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                insights.effectiveImportantInsights.forEach { insightItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "▪ ",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                        Text(
                                            text = insightItem,
                                            fontSize = 12.sp,
                                            color = Color(0xFF451A03),
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 6. CONCLUSION SECTION
                        if (insights.effectiveConclusion.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "6. Conclusion",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = insights.effectiveConclusion,
                                    fontSize = 12.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        // Technical Metadata Badges
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
                                    Text("Pages/Slides", fontSize = 10.sp, color = Color(0xFF6B7280))
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
                                    Text("Universal NLP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                    Text("Status", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    Text("Insight Ready", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
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
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Insight Generation Failed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = aiInsightsError ?: "Unknown error occurred.",
                                fontSize = 12.sp,
                                color = Color(0xFF7F1D1D),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { loadInsightsForDoc(doc, retry = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry Generation", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { loadInsightsForDoc(doc, retry = true) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val docTitle = doc.title
                            selectedSummaryDoc = null
                            aiInsightsResult = null
                            onNavigateToAiChat(docTitle)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat with AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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
                Text("Remove from IntelliSpace?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to remove \"${doc.title}\" from the IntelliSpace? The file on your phone will not be deleted.",
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
                                Toast.makeText(context, "\"${toDelete.title}\" removed from IntelliSpace", Toast.LENGTH_SHORT).show()
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

    // IntelliSpace Indexing / Uploading Progress Dialog
    if (isUploadingToVault) {
        IntelliSpaceIndexingDialog(
            currentIndex = vaultUploadCurrentIndex,
            totalCount = vaultUploadTotalCount
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

                AnimatedRefreshButton(
                    onClick = {
                        refreshTrigger++
                        if (selectedSource == StorageSourceTab.INTELLISPACE) loadVault()
                    },
                    isRefreshing = isVaultLoading || isLoadingDeviceFiles,
                    buttonSize = 36.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                // Page Title
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "My Files",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        AnimatedMicrophoneButton(
                            onClick = onMicClick,
                            buttonSize = 36.dp
                        )
                    }
                }

                // Storage Source Selector: Device Files vs IntelliSpace
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
                        val deviceTabBg by animateColorAsState(
                            targetValue = if (isDeviceSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(260),
                            label = "deviceTabBg"
                        )
                        val deviceTextColor by animateColorAsState(
                            targetValue = if (isDeviceSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(260),
                            label = "deviceTextColor"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(deviceTabBg)
                                .clickable { selectedSource = StorageSourceTab.DEVICE_FILES }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = deviceTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Device Files ($totalFiles)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = deviceTextColor
                                )
                            }
                        }

                        // IntelliSpace Tab
                        val isVaultSelected = selectedSource == StorageSourceTab.INTELLISPACE
                        val vaultTabBg by animateColorAsState(
                            targetValue = if (isVaultSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(260),
                            label = "vaultTabBg"
                        )
                        val vaultTextColor by animateColorAsState(
                            targetValue = if (isVaultSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(260),
                            label = "vaultTextColor"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(vaultTabBg)
                                .clickable { selectedSource = StorageSourceTab.INTELLISPACE }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = vaultTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "IntelliSpace ($vaultFilesCount)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = vaultTextColor
                                )
                            }
                        }
                    }
                }

                if (selectedSource == StorageSourceTab.DEVICE_FILES) {
                    if (isLoadingDeviceFiles) {
                        item {
                            FilesLoadingCard(
                                title = "Device Files",
                                loadingText = "Loading files..."
                            )
                        }
                    } else {
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

                        // Priority Distribution Donut Chart Section (Glass Light Animated Card)
                        item {
                            PriorityDistributionCard(
                                importantCount = importantCount,
                                normalCount = normalCount,
                                lowCount = lowCount,
                                onPriorityClick = { priority -> onQuickAccessClick(priority) }
                            )
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
                                    priority = PriorityLevel.IMPORTANT,
                                    count = importantCount,
                                    onClick = { onQuickAccessClick(PriorityLevel.IMPORTANT) }
                                )

                                QuickAccessCard(
                                    priority = PriorityLevel.NORMAL,
                                    count = normalCount,
                                    onClick = { onQuickAccessClick(PriorityLevel.NORMAL) }
                                )

                                QuickAccessCard(
                                    priority = PriorityLevel.LOW,
                                    count = lowCount,
                                    onClick = { onQuickAccessClick(PriorityLevel.LOW) }
                                )
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // FULL INTELLISPACE TAB INTEGRATION
                    // ==========================================

                    // Header & Upload to IntelliSpace Card
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
                                            text = "IntelliSpace Documents",
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

                            // Upload to IntelliSpace Button
                            Button(
                                onClick = {
                                    uploadDocLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-powerpoint",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "text/plain",
                                            "text/csv"
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
                                        text = if (isUploadingToVault) uploadProgressMessage.ifBlank { "Moving to IntelliSpace..." } else "Select & Move Documents to IntelliSpace",
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

                    // IntelliSpace Documents List Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "IntelliSpace Documents ($vaultFilesCount)",
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

                    // Loading State, Empty State, or List of IntelliSpace Documents
                    if (isVaultLoading) {
                        item {
                            FilesLoadingCard(
                                title = "IntelliSpace Documents",
                                loadingText = "Loading IntelliSpace files..."
                            )
                        }
                    } else if (distinctVaultDocs.isEmpty()) {
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
                                    text = "Your IntelliSpace is Empty",
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
                        items(distinctVaultDocs, key = { it.id }) { doc ->
                            VaultDocumentCard(
                                doc = doc,
                                isLoadingFile = resolvingDocId == doc.id,
                                onViewFileClick = {
                                    coroutineScope.launch {
                                        resolvingDocId = doc.id
                                        val (uriStr, filePath) = LocalFileManager.resolveVaultDocumentAccess(
                                            context = context,
                                            doc = doc,
                                            deviceFiles = realFiles
                                        )
                                        resolvingDocId = -1

                                        val fileDetail = FileDetailData(
                                            fileName = doc.title,
                                            fileSize = doc.file_size,
                                            fileType = "${doc.format} Document",
                                            createdDate = doc.created_at ?: "Recent",
                                            modifiedDate = doc.created_at ?: "Recent",
                                            category = "IntelliSpace",
                                            location = doc.location.ifBlank { "IntelliSpace Storage" },
                                            priority = when (doc.priority?.uppercase()) {
                                                "IMPORTANT" -> PriorityLevel.IMPORTANT
                                                "NORMAL" -> PriorityLevel.NORMAL
                                                "LOW" -> PriorityLevel.LOW
                                                else -> null
                                            },
                                            iconResId = when (doc.format.uppercase()) {
                                                "DOCX", "DOC" -> R.drawable.doc
                                                "PPTX", "PPT" -> R.drawable.ppt
                                                else -> R.drawable.pdf
                                            },
                                            uriString = uriStr,
                                            path = filePath,
                                            isVaultSynced = true
                                        )
                                        onOpenFile(fileDetail)
                                    }
                                },
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
                                        text = "Your device documents are kept strictly local by default. Documents only enter the IntelliSpace when you explicitly tap 'Add to IntelliSpace'.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534),
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    // IntelliSpace Capabilities Card
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
                                text = "IntelliSpace Capabilities",
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

            DraggableScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp)
            )
        }
        }
    }
}

@Composable
fun VaultDocumentCard(
    doc: DocumentDto,
    onViewFileClick: () -> Unit,
    onViewSummaryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isLoadingFile: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewFileClick() },
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
                    val iconRes = when (doc.format.uppercase()) {
                        "DOCX", "DOC" -> R.drawable.doc
                        "PPTX", "PPT" -> R.drawable.ppt
                        else -> R.drawable.pdf
                    }
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
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
                        text = "${doc.format} • ${doc.file_size} • ${doc.created_at?.take(10) ?: "Recent"}",
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
                        contentDescription = "Delete from IntelliSpace",
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
                    val statusText = when {
                        doc.insight_status.equals("READY", ignoreCase = true) -> "Insight Ready"
                        doc.insight_status.equals("EXTRACTING", ignoreCase = true) || doc.insight_status.equals("GENERATING", ignoreCase = true) -> "Analyzing..."
                        doc.insight_status.equals("FAILED", ignoreCase = true) -> "Insight Failed"
                        doc.has_text -> "Insight Ready"
                        else -> "Indexed"
                    }
                    val statusBg = when (statusText) {
                        "Insight Failed" -> Color(0xFFFEE2E2)
                        "Analyzing..." -> Color(0xFFE0E7FF)
                        else -> Color(0xFFDEF7EC)
                    }
                    val statusColor = when (statusText) {
                        "Insight Failed" -> Color(0xFF991B1B)
                        "Analyzing..." -> Color(0xFF3730A3)
                        else -> Color(0xFF03543F)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    if (!doc.priority.isNullOrBlank()) {
                        val prioColor = when (doc.priority.uppercase()) {
                            "IMPORTANT" -> Pair(Color(0xFFFFEAEE), Color(0xFFEF4444))
                            "LOW" -> Pair(Color(0xFFE2F7ED), Color(0xFF10B981))
                            else -> Pair(Color(0xFFFFF6D8), Color(0xFFD97706))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(prioColor.first)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = doc.priority,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = prioColor.second
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // View File Button
                    Button(
                        onClick = onViewFileClick,
                        enabled = !isLoadingFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoadingFile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Opening...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "View File",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // AI Insights Button
                    val isFailed = doc.insight_status.equals("FAILED", ignoreCase = true)
                    Button(
                        onClick = onViewSummaryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFailed) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFailed) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFailed) "Retry" else "AI Insights",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
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
fun PriorityDistributionCard(
    importantCount: Int,
    normalCount: Int,
    lowCount: Int,
    onPriorityClick: (PriorityLevel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalPrioritized = importantCount + normalCount + lowCount

    // Smooth sweeping progress animation
    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
        ),
        label = "animProgress"
    )

    LaunchedEffect(totalPrioritized, importantCount, normalCount, lowCount) {
        animationPlayed = true
    }

    // Ambient breathing neon glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "infiniteCardAura")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.26f,
        targetValue = 0.46f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Specular glass light gleam shimmer
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    // Center count scale spring
    val centerScale by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0.75f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "centerScale"
    )

    // Calculate slice sweeps
    val importantSweep = if (totalPrioritized > 0) (importantCount.toFloat() / totalPrioritized) * 360f else 0f
    val normalSweep = if (totalPrioritized > 0) (normalCount.toFloat() / totalPrioritized) * 360f else 0f
    val lowSweep = if (totalPrioritized > 0) (lowCount.toFloat() / totalPrioritized) * 360f else 0f

    val currentImpSweep = importantSweep * animProgress
    val currentNorSweep = normalSweep * animProgress
    val currentLowSweep = lowSweep * animProgress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color(0xFF64748B).copy(alpha = 0.08f),
                spotColor = Color(0xFF64748B).copy(alpha = 0.12f),
                clip = false
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FAFC)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        // Specular 3D Glass Light Sheen
        Canvas(modifier = Modifier.matchParentSize()) {
            val sheenBrush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.15f),
                radius = size.width * 0.5f
            )
            drawRect(brush = sheenBrush)

            val sweepX = size.width * shimmerProgress
            val sweepBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                start = Offset(sweepX - 70.dp.toPx(), 0f),
                end = Offset(sweepX + 70.dp.toPx(), size.height * 0.6f)
            )
            drawRect(brush = sweepBrush)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Text(
                text = "Priority Distribution",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart with Neon Glow
                Box(
                    modifier = Modifier.size(136.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 18.dp.toPx()
                        val glowStrokeWidth = 26.dp.toPx()
                        val startAngleOffset = -90f

                        if (totalPrioritized == 0) {
                            drawArc(
                                color = Color(0xFFE2E8F0),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                        } else {
                            // 1. Neon Radiant Bloom Layer (Outer soft glowing strokes)
                            if (currentImpSweep > 0f) {
                                drawArc(
                                    color = Color(0xFFEF4444).copy(alpha = glowAlpha),
                                    startAngle = startAngleOffset,
                                    sweepAngle = currentImpSweep,
                                    useCenter = false,
                                    style = Stroke(width = glowStrokeWidth)
                                )
                            }
                            if (currentNorSweep > 0f) {
                                drawArc(
                                    color = Color(0xFFF59E0B).copy(alpha = glowAlpha),
                                    startAngle = startAngleOffset + currentImpSweep,
                                    sweepAngle = currentNorSweep,
                                    useCenter = false,
                                    style = Stroke(width = glowStrokeWidth)
                                )
                            }
                            if (currentLowSweep > 0f) {
                                drawArc(
                                    color = Color(0xFF10B981).copy(alpha = glowAlpha),
                                    startAngle = startAngleOffset + currentImpSweep + currentNorSweep,
                                    sweepAngle = currentLowSweep,
                                    useCenter = false,
                                    style = Stroke(width = glowStrokeWidth)
                                )
                            }

                            // 2. Crisp Solid Segments
                            if (currentImpSweep > 0f) {
                                drawArc(
                                    color = Color(0xFFEF4444),
                                    startAngle = startAngleOffset,
                                    sweepAngle = currentImpSweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            if (currentNorSweep > 0f) {
                                drawArc(
                                    color = Color(0xFFF59E0B),
                                    startAngle = startAngleOffset + currentImpSweep,
                                    sweepAngle = currentNorSweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            if (currentLowSweep > 0f) {
                                drawArc(
                                    color = Color(0xFF10B981),
                                    startAngle = startAngleOffset + currentImpSweep + currentNorSweep,
                                    sweepAngle = currentLowSweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        }
                    }

                    // Center Marked Count
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            scaleX = centerScale
                            scaleY = centerScale
                        }
                    ) {
                        Text(
                            text = totalPrioritized.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Marked",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(22.dp))

                // Legend List Matching Reference Design
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DistributionLegendRow(
                        dotColor = Color(0xFFEF4444),
                        label = "Important",
                        countText = "$importantCount files",
                        onClick = { onPriorityClick(PriorityLevel.IMPORTANT) }
                    )
                    DistributionLegendRow(
                        dotColor = Color(0xFFF59E0B),
                        label = "Normal",
                        countText = "$normalCount files",
                        onClick = { onPriorityClick(PriorityLevel.NORMAL) }
                    )
                    DistributionLegendRow(
                        dotColor = Color(0xFF10B981),
                        label = "Low",
                        countText = "$lowCount files",
                        onClick = { onPriorityClick(PriorityLevel.LOW) }
                    )
                }
            }
        }
    }
}

@Composable
fun DistributionLegendRow(
    dotColor: Color,
    label: String,
    countText: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "legendScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = CircleShape,
                        ambientColor = dotColor.copy(alpha = 0.5f),
                        spotColor = dotColor.copy(alpha = 0.5f)
                    )
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
        }

        Text(
            text = countText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
    }
}

@Composable
fun QuickAccessCard(
    priority: PriorityLevel,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "qaCardScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = priority.glowColor,
                spotColor = priority.glowColor
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = priority.borderColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = priority.badgeResId),
                contentDescription = priority.title,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "${priority.title} Files",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "$count file${if (count == 1) "" else "s"}",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(20.dp)
        )
    }
}

// Backward-compatible overload
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
            contentDescription = "Open",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(20.dp)
        )
    }
}


