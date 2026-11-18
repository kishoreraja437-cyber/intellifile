package com.simats.intellifile.ui.tools.watermark

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlin.math.max
import kotlin.math.min

// IntelliFile Dusky Aqua Palette
private val DuskyAqua = Color(0xFF55A9AF)
private val LightAqua = Color(0xFF6FBEC3)
private val BackgroundAqua = Color(0xFFEFF8F8)
private val ContainerAqua = Color(0xFFD5ECEE)
private val DarkOceanTeal = Color(0xFF183A3C)
private val MutedAqua = Color(0xFF537779)
private val CardBorderColor = Color(0xFFD5ECEE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkRemoverScreen(
    initialUri: Uri? = null,
    initialFilePath: String = "",
    initialFileName: String = "",
    onBackClick: () -> Unit = {},
    onOpenCleanedPdf: (File) -> Unit = {},
    viewModel: WatermarkRemoverViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showRegionSelectorDialog by remember { mutableStateOf(false) }

    // Storage Access Framework Launcher: Open PDF Document
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.loadAndAnalyzePdf(context, uri = uri)
        }
    }

    // Storage Access Framework Launcher: Save/Export Cleaned PDF
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null) {
            val success = viewModel.saveCleanedPdfToUri(context, destUri)
            if (success) {
                Toast.makeText(context, "Cleaned PDF saved successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed saving cleaned PDF.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-load initial PDF if passed
    LaunchedEffect(initialUri, initialFilePath, initialFileName) {
        if (initialUri != null || initialFilePath.isNotBlank()) {
            viewModel.loadAndAnalyzePdf(
                context = context,
                uri = initialUri,
                filePath = initialFilePath,
                fileName = initialFileName
            )
        }
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PDF Watermark Remover",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOceanTeal
                        )
                        if (uiState.selectedPdfName.isNotBlank()) {
                            Text(
                                text = uiState.selectedPdfName,
                                fontSize = 12.sp,
                                color = MutedAqua,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkOceanTeal
                        )
                    }
                },
                actions = {
                    if (uiState.selectedPdfFile != null) {
                        IconButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open Another PDF",
                                tint = DuskyAqua
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // State 0: No PDF loaded yet
                uiState.selectedPdfFile == null && !uiState.isAnalyzing -> {
                    EmptyWatermarkPickerView(
                        onSelectPdfClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }
                    )
                }

                // State 1: Analyzing Document
                uiState.isAnalyzing -> {
                    LoadingAnalysisView(
                        fileName = uiState.selectedPdfName,
                        progressMessage = uiState.analysisProgressMessage
                    )
                }

                // State 2: Document Loaded & Analyzed (or Cleaned)
                else -> {
                    MainWatermarkContentView(
                        uiState = uiState,
                        onToggleWatermark = { viewModel.toggleWatermarkSelection(it) },
                        onSelectAll = { viewModel.selectAllWatermarks(it) },
                        onCustomTextChanged = { viewModel.setCustomText(it) },
                        onOpenRegionSelector = { showRegionSelectorDialog = true },
                        onClearRegion = { viewModel.clearCustomRegion() },
                        onStartRemoval = { viewModel.startRemoval(context) },
                        onPageChanged = { viewModel.setPreviewPage(context, it) },
                        onPreviewModeChanged = { viewModel.setPreviewMode(it) },
                        onExportPdf = {
                            val defaultName = "${uiState.selectedPdfName.substringBeforeLast('.')}_cleaned.pdf"
                            exportPdfLauncher.launch(defaultName)
                        },
                        onOpenCleaned = { file -> onOpenCleanedPdf(file) },
                        onShareCleaned = { file -> sharePdf(context, file) }
                    )
                }
            }

            // Digital Signature Warning Dialog
            if (uiState.showSignatureWarning) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissSignatureWarning() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Digitally Signed Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "This document contains a cryptographic digital signature.",
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            if (uiState.analysisResult?.securityStatus?.signatureDetails?.isNotBlank() == true) {
                                Text(
                                    text = "Signer: ${uiState.analysisResult?.securityStatus?.signatureDetails}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Text(
                                text = "Modifying content or removing watermarks will invalidate this digital signature. A new unsigned copy will be generated without altering your original signed file.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmProceedWithUnsignedCopy(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua)
                        ) {
                            Text("Proceed with Unsigned Copy", fontSize = 13.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissSignatureWarning() }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                    }
                )
            }

            // Interactive Region Selector Dialog
            if (showRegionSelectorDialog && uiState.originalPageBitmap != null) {
                InteractiveRegionSelectorDialog(
                    pageBitmap = uiState.originalPageBitmap!!,
                    initialRegionRel = uiState.customRegionRel,
                    onDismiss = { showRegionSelectorDialog = false },
                    onConfirm = { rect ->
                        viewModel.setCustomRegion(rect)
                        showRegionSelectorDialog = false
                    }
                )
            }
        }
    }
}

// =========================================================================
// EMPTY STATE: DOCUMENT PICKER & FEATURES OVERVIEW
// =========================================================================

@Composable
private fun EmptyWatermarkPickerView(
    onSelectPdfClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(BackgroundAqua)
                .border(2.dp, DuskyAqua.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null,
                tint = DuskyAqua,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Clean Your PDF Documents",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DarkOceanTeal,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Detect and remove text watermarks, transparent stamps, image overlays, and scanned artifacts with full on-device privacy.",
            fontSize = 14.sp,
            color = MutedAqua,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onSelectPdfClick,
            colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
        ) {
            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Select PDF from Device", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Feature Highlights Grid
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Supported Watermark Types",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkOceanTeal
                )

                FeatureRow(
                    icon = Icons.Default.TextFields,
                    title = "Text Watermarks",
                    desc = "CONFIDENTIAL, DRAFT, SAMPLE, repeated text, diagonal / angled text"
                )

                FeatureRow(
                    icon = Icons.Default.Opacity,
                    title = "Opacity & Transparent Elements",
                    desc = "Low-opacity watermarks, graphic states, and marked content /Artifacts"
                )

                FeatureRow(
                    icon = Icons.Default.Image,
                    title = "Image & Form XObjects",
                    desc = "Soft-masked logo stamps, semi-transparent images, and form templates"
                )

                FeatureRow(
                    icon = Icons.Default.Brush,
                    title = "Scanned Document Inpainting",
                    desc = "Background color restoration and pixel inpainting for paper scans"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy Guarantee Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "100% Local On-Device Processing • Never Uploaded",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(BackgroundAqua),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = DuskyAqua, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkOceanTeal)
            Text(text = desc, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 15.sp)
        }
    }
}

// =========================================================================
// LOADING ANALYSIS VIEW
// =========================================================================

@Composable
private fun LoadingAnalysisView(
    fileName: String,
    progressMessage: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = DuskyAqua, strokeWidth = 3.5.dp, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Analyzing Document Structure", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkOceanTeal)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = fileName, fontSize = 13.sp, color = MutedAqua, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = progressMessage, fontSize = 12.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
    }
}

// =========================================================================
// MAIN WATERMARK CONTENT VIEW (DETECTION, OPTIONS, PREVIEWS)
// =========================================================================

@Composable
private fun MainWatermarkContentView(
    uiState: WatermarkRemoverUiState,
    onToggleWatermark: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onCustomTextChanged: (String) -> Unit,
    onOpenRegionSelector: () -> Unit,
    onClearRegion: () -> Unit,
    onStartRemoval: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onPreviewModeChanged: (WatermarkPreviewMode) -> Unit,
    onExportPdf: () -> Unit,
    onOpenCleaned: (File) -> Unit,
    onShareCleaned: (File) -> Unit
) {
    val analysis = uiState.analysisResult
    val isCleaned = uiState.removalResult?.isSuccess == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Document Info Banner
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundAqua),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = DuskyAqua)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.selectedPdfName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DarkOceanTeal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.totalPages} Page(s) • ${if (analysis?.isScannedPdf == true) "Scanned Document" else "Native Vector PDF"}",
                            fontSize = 11.sp,
                            color = MutedAqua
                        )
                    }
                }
            }
        }

        // 2. Scanned Document Inpainting Notice
        if (analysis?.isScannedPdf == true) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Scanned PDF Notice",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "This is a scanned document containing raster images. Watermark removal uses adaptive background inpainting. Because original content under the watermark was flattened at scan time, perfect reconstruction is not guaranteed.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. DRM / Protected Document Banner
        if (analysis?.securityStatus?.canModify == false) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = analysis.securityStatus.restrictionMessage ?: "Document modification is restricted by owner DRM permissions.",
                            fontSize = 12.sp,
                            color = Color(0xFF991B1B)
                        )
                    }
                }
            }
        }

        // 4. Before & After Preview Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page Preview",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOceanTeal
                        )

                        // Mode Selector: Before vs After
                        if (isCleaned) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(2.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.previewMode == WatermarkPreviewMode.BEFORE,
                                    onClick = { onPreviewModeChanged(WatermarkPreviewMode.BEFORE) },
                                    label = { Text("Original", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = uiState.previewMode == WatermarkPreviewMode.AFTER,
                                    onClick = { onPreviewModeChanged(WatermarkPreviewMode.AFTER) },
                                    label = { Text("Cleaned", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preview Image Viewport
                    val bmpToDisplay = if (isCleaned && uiState.previewMode == WatermarkPreviewMode.AFTER) {
                        uiState.cleanedPageBitmap ?: uiState.originalPageBitmap
                    } else {
                        uiState.originalPageBitmap
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmpToDisplay != null) {
                            Image(
                                bitmap = bmpToDisplay.asImageBitmap(),
                                contentDescription = "Page Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            CircularProgressIndicator(color = DuskyAqua, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }

                        // Mode watermark badge overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCleaned && uiState.previewMode == WatermarkPreviewMode.AFTER) DuskyAqua else Color(0xFF64748B))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isCleaned && uiState.previewMode == WatermarkPreviewMode.AFTER) "CLEANED" else "ORIGINAL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Page Navigation Strip
                    if (uiState.totalPages > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onPageChanged(uiState.currentPreviewPage - 1) },
                                enabled = uiState.currentPreviewPage > 0
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev")
                            }

                            Text(
                                text = "Page ${uiState.currentPreviewPage + 1} of ${uiState.totalPages}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkOceanTeal,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            IconButton(
                                onClick = { onPageChanged(uiState.currentPreviewPage + 1) },
                                enabled = uiState.currentPreviewPage < uiState.totalPages - 1
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                            }
                        }
                    }
                }
            }
        }

        // 5. Cleaned Actions & Success Summary (When Removal is Complete)
        if (isCleaned && uiState.removalResult?.cleanedFile != null) {
            val cleanedFile = uiState.removalResult.cleanedFile!!
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundAqua),
                    border = BorderStroke(1.5.dp, DuskyAqua)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = DuskyAqua)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cleaned PDF Ready!",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkOceanTeal
                            )
                        }

                        Text(
                            text = uiState.removalResult.message,
                            fontSize = 12.sp,
                            color = MutedAqua
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenCleaned(cleanedFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Document", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onExportPdf,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DuskyAqua),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, tint = DuskyAqua, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save As...", fontSize = 12.sp, color = DuskyAqua)
                            }

                            IconButton(
                                onClick = { onShareCleaned(cleanedFile) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ContainerAqua)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = DuskyAqua)
                            }
                        }
                    }
                }
            }
        }

        // 6. Detected Watermarks List
        if (!isCleaned) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Watermarks (${analysis?.detectedWatermarks?.size ?: 0})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkOceanTeal
                    )

                    if ((analysis?.detectedWatermarks?.size ?: 0) > 1) {
                        TextButton(
                            onClick = {
                                val allSelected = uiState.selectedWatermarkIds.size == analysis?.detectedWatermarks?.size
                                onSelectAll(!allSelected)
                            }
                        ) {
                            Text(
                                text = if (uiState.selectedWatermarkIds.size == analysis?.detectedWatermarks?.size) "Deselect All" else "Select All",
                                fontSize = 12.sp,
                                color = DuskyAqua
                            )
                        }
                    }
                }
            }

            if (analysis?.detectedWatermarks?.isEmpty() == true) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, CardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No standard watermark keywords detected automatically.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkOceanTeal,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You can manually enter custom text or select a watermark region below.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(analysis?.detectedWatermarks ?: emptyList()) { watermark ->
                    WatermarkItemCard(
                        watermark = watermark,
                        isSelected = uiState.selectedWatermarkIds.contains(watermark.id),
                        onToggle = { onToggleWatermark(watermark.id) }
                    )
                }
            }

            // 7. Manual Watermark Selection Tools
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Manual Watermark Tools",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOceanTeal
                        )

                        // Custom Text Input
                        OutlinedTextField(
                            value = uiState.customTextToRemove,
                            onValueChange = onCustomTextChanged,
                            placeholder = { Text("Custom text or stamp word to erase...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Interactive Region Selector Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onOpenRegionSelector,
                                colors = ButtonDefaults.buttonColors(containerColor = ContainerAqua),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CropFree, contentDescription = null, tint = DuskyAqua)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Region on Page", fontSize = 12.sp, color = DuskyAqua, fontWeight = FontWeight.SemiBold)
                            }

                            if (uiState.customRegionRel != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Region active", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                                    IconButton(onClick = onClearRegion) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Progress Indicator (When Processing)
            if (uiState.isProcessing) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundAqua),
                        border = BorderStroke(1.dp, CardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = DuskyAqua, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = uiState.processingStatus, fontSize = 13.sp, color = DarkOceanTeal, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { uiState.processingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = DuskyAqua,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }

            // 9. Primary Action Button
            item {
                Button(
                    onClick = onStartRemoval,
                    enabled = uiState.canStartRemoval,
                    colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(elevation = if (uiState.canStartRemoval) 4.dp else 0.dp, shape = RoundedCornerShape(14.dp))
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isProcessing) "Processing..." else "Remove Selected Watermarks",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =========================================================================
// WATERMARK CANDIDATE CARD ITEM
// =========================================================================

@Composable
private fun WatermarkItemCard(
    watermark: DetectedWatermark,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0FDF4) else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF86EFAC) else CardBorderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = DuskyAqua)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ContainerAqua)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = watermark.type.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOceanTeal
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${(watermark.confidence * 100).toInt()}% confidence",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = watermark.textOrName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkOceanTeal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${watermark.detectionReason} • ${watermark.affectedPagesSummary}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

// =========================================================================
// INTERACTIVE REGION SELECTOR DIALOG
// =========================================================================

@Composable
private fun InteractiveRegionSelectorDialog(
    pageBitmap: Bitmap,
    initialRegionRel: RectF?,
    onDismiss: () -> Unit,
    onConfirm: (RectF) -> Unit
) {
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxEnd by remember { mutableStateOf<Offset?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drag box over watermark",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkOceanTeal
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    boxStart = offset
                                    boxEnd = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    boxEnd = (boxEnd ?: change.position) + dragAmount
                                }
                            )
                        }
                        .onGloballyPositioned { imageSize = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = pageBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Draw Selection Box Overlay
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = boxStart
                        val e = boxEnd
                        if (s != null && e != null) {
                            val left = min(s.x, e.x)
                            val top = min(s.y, e.y)
                            val right = max(s.x, e.x)
                            val bottom = max(s.y, e.y)
                            val width = right - left
                            val height = bottom - top

                            drawRect(
                                color = DuskyAqua.copy(alpha = 0.25f),
                                topLeft = Offset(left, top),
                                size = Size(width, height)
                            )
                            drawRect(
                                color = DuskyAqua,
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val s = boxStart
                            val e = boxEnd
                            if (s != null && e != null && imageSize.width > 0 && imageSize.height > 0) {
                                val left = min(s.x, e.x) / imageSize.width
                                val top = min(s.y, e.y) / imageSize.height
                                val right = max(s.x, e.x) / imageSize.width
                                val bottom = max(s.y, e.y) / imageSize.height
                                onConfirm(RectF(left, top, right, bottom))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
                        enabled = boxStart != null && boxEnd != null
                    ) {
                        Text("Confirm Region")
                    }
                }
            }
        }
    }
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Cleaned PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
