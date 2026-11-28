package com.simats.intellifile.ui.storage

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.CategoryStorageData
import com.simats.intellifile.data.manager.DocumentFormatItem
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.manager.PhoneStorageManager
import com.simats.intellifile.data.manager.PhoneStorageStats
import com.simats.intellifile.data.manager.StorageAnalysisResult
import com.simats.intellifile.data.manager.StorageCategory
import com.simats.intellifile.data.manager.StorageCategoryData
import com.simats.intellifile.data.manager.StorageDocumentCategory
import com.simats.intellifile.ui.common.AnimatedRefreshButton
import kotlinx.coroutines.launch

enum class StorageViewMode {
    ALL_FILES,
    DOCUMENT_TYPES
}

@Composable
fun PhoneStorageScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(LocalFileManager.hasStoragePermission(context)) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var analysisResult by remember { mutableStateOf<StorageAnalysisResult?>(null) }
    var viewMode by remember { mutableStateOf(StorageViewMode.ALL_FILES) }

    // Launcher for Android 11+ All Files Access
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = LocalFileManager.hasStoragePermission(context)
        refreshTrigger++
    }

    // Launcher for Android 10 and below
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasPermission = LocalFileManager.hasStoragePermission(context)
        refreshTrigger++
    }

    // Refresh permission status on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = LocalFileManager.hasStoragePermission(context)
                if (updated != hasPermission) {
                    hasPermission = updated
                    refreshTrigger++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Scan storage on launch and upon refresh
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        val result = PhoneStorageManager.scanStorageCategories(context)
        analysisResult = result
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Top Header Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = "Phone Storage",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Device & Document Storage Analysis",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    AnimatedRefreshButton(
                        onClick = { refreshTrigger++ },
                        isRefreshing = isLoading
                    )
                }
            }

            // 2. Storage Permission Banner if not yet granted
            if (!hasPermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF8F8)),
                        border = BorderStroke(1.2.dp, Color(0xFF55A9AF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF55A9AF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SdStorage,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Full Storage Access Required",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF183A3C)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Grant storage permission to scan all accessible document files across your device.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF537779),
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            manageStorageLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            manageStorageLauncher.launch(fallback)
                                        }
                                    } else {
                                        legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Grant", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Whole Phone Storage Card (Device-Level)
            item {
                val stats = analysisResult?.phoneStats ?: PhoneStorageManager.getPhoneStorageStats()
                WholePhoneStorageCard(stats = stats)
            }

            // 4. View Switcher: Main Categories (5) vs Document Types (8)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAllFiles = viewMode == StorageViewMode.ALL_FILES
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isAllFiles) Color.White else Color.Transparent)
                            .clickable { viewMode = StorageViewMode.ALL_FILES }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Main Categories (5)",
                            fontSize = 13.sp,
                            fontWeight = if (isAllFiles) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAllFiles) Color(0xFF00A896) else Color(0xFF64748B)
                        )
                    }

                    val isDocs = viewMode == StorageViewMode.DOCUMENT_TYPES
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isDocs) Color.White else Color.Transparent)
                            .clickable { viewMode = StorageViewMode.DOCUMENT_TYPES }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Document Types (8)",
                            fontSize = 13.sp,
                            fontWeight = if (isDocs) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDocs) Color(0xFF00A896) else Color(0xFF64748B)
                        )
                    }
                }
            }

            // 5. Section Header & Total Banner
            if (viewMode == StorageViewMode.ALL_FILES) {
                item {
                    val complete = analysisResult?.completeAnalysis
                    val totalFiles = complete?.totalAccessibleFiles ?: 0
                    val totalSize = complete?.formattedAccessibleSize ?: "0 B"
                    val phonePercent = complete?.accessiblePercentageOfPhone ?: 0f

                    CombinedAccessibleStorageBanner(
                        totalFiles = totalFiles,
                        formattedSize = totalSize,
                        phonePercentage = phonePercent
                    )
                }

                item {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "Google Files Storage Categories",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Accessible media, documents and archives across phone",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                item {
                    val result = analysisResult
                    val totalDocs = result?.totalDocumentFiles ?: 0
                    val totalFormatted = result?.totalDocumentFormatted ?: "0 B"
                    val phonePercent = result?.documentPercentageOfPhone ?: 0f

                    CombinedDocumentStorageBanner(
                        totalFiles = totalDocs,
                        formattedSize = totalFormatted,
                        phonePercentage = phonePercent
                    )
                }

                item {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "File-Type-Wise Storage Usage",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Storage breakdown across the 8 document categories on phone",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Loading indicator while initial scan is taking place
            if (isLoading && analysisResult == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.2.dp, Color(0xFFBCE3E5))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.8.dp,
                                color = Color(0xFF00A896),
                                trackColor = Color(0xFFC9E8EB),
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = "Scanning phone storage...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

            // 6. Category Items depending on selected view mode
            if (viewMode == StorageViewMode.ALL_FILES) {
                val complete = analysisResult?.completeAnalysis
                val categories = complete?.categories ?: emptyList()
                val docBreakdown = complete?.documentBreakdown ?: emptyList()

                items(categories, key = { it.category.name }) { catData ->
                    GoogleFilesCategoryCard(
                        categoryData = catData,
                        documentBreakdown = if (catData.category == StorageCategory.DOCUMENTS) docBreakdown else emptyList()
                    )
                }
            } else {
                analysisResult?.let { result ->
                    items(result.categories, key = { it.category.name }) { categoryData ->
                        CategoryStorageCard(categoryData = categoryData)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Card displaying whole phone storage metrics:
 * Total, Used, Available / Free, Storage Usage Percentage, and visual progress bar.
 */
@Composable
private fun WholePhoneStorageCard(
    stats: PhoneStorageStats,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (stats.usagePercentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "wholePhoneProgress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, Color(0xFFBCE3E5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Icon + Title + Usage Percentage Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEFF8F8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device Storage",
                            tint = Color(0xFF00A896),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Whole Phone Storage",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Actual on-device partition space",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Storage Usage Percentage Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF8F8))
                        .border(1.dp, Color(0xFFBCE3E5), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", stats.usagePercentage)}% Used",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00A896)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Visual Storage Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF55A9AF), Color(0xFF00A896))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3-Column Metrics: Used, Available / Free, Total Storage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Used Phone Storage
                StorageStatColumn(
                    label = "Used Storage",
                    value = stats.usedFormatted,
                    valueColor = Color(0xFF183A3C),
                    dotColor = Color(0xFF00A896)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color(0xFFCBD5E1))
                )

                // Available / Free Storage
                StorageStatColumn(
                    label = "Free / Available",
                    value = stats.freeFormatted,
                    valueColor = Color(0xFF059669),
                    dotColor = Color(0xFF10B981)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color(0xFFCBD5E1))
                )

                // Total Phone Storage
                StorageStatColumn(
                    label = "Total Storage",
                    value = stats.totalFormatted,
                    valueColor = Color(0xFF475569),
                    dotColor = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun StorageStatColumn(
    label: String,
    value: String,
    valueColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

/**
 * Banner displaying combined storage occupied by all included document and archive files.
 */
@Composable
private fun CombinedAccessibleStorageBanner(
    totalFiles: Int,
    formattedSize: String,
    phonePercentage: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.2.dp, Color(0xFF86EFAC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Combined Documents",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Combined Document Storage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$totalFiles files across 8 categories",
                        fontSize = 12.sp,
                        color = Color(0xFF15803D)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedSize,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14532D)
                )
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", phonePercentage)}% of Phone",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF16A34A)
                )
            }
        }
    }
}

@Composable
private fun CombinedDocumentStorageBanner(
    totalFiles: Int,
    formattedSize: String,
    phonePercentage: Float,
    modifier: Modifier = Modifier
) {
    CombinedAccessibleStorageBanner(
        totalFiles = totalFiles,
        formattedSize = formattedSize,
        phonePercentage = phonePercentage,
        modifier = modifier
    )
}

/**
 * Individual Card for each of the 5 Google Files categories.
 */
@Composable
private fun GoogleFilesCategoryCard(
    categoryData: StorageCategoryData,
    documentBreakdown: List<DocumentFormatItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val (iconVector, bgTint, iconTint) = when (categoryData.category) {
        StorageCategory.IMAGES -> Triple(Icons.Filled.Image, Color(0xFFFFF1F2), Color(0xFFF43F5E))
        StorageCategory.VIDEOS -> Triple(Icons.Filled.Videocam, Color(0xFFF5F3FF), Color(0xFF8B5CF6))
        StorageCategory.AUDIO -> Triple(Icons.Filled.Audiotrack, Color(0xFFFEF3C7), Color(0xFFF59E0B))
        StorageCategory.DOCUMENTS -> Triple(Icons.AutoMirrored.Filled.Article, Color(0xFFCCFBF1), Color(0xFF00A896))
        StorageCategory.ARCHIVES_OTHER -> Triple(Icons.Filled.FolderZip, Color(0xFFEFF6FF), Color(0xFF3B82F6))
    }

    val progressFraction = (categoryData.percentageOfAccessibleFiles / 100f).coerceIn(0f, 1f)
    val hasBreakdown = categoryData.category == StorageCategory.DOCUMENTS && documentBreakdown.isNotEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.25f))
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = categoryData.title,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = categoryData.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${categoryData.fileCount} files · ${categoryData.formattedSize}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgTint)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${categoryData.percentageOfAccessibleFiles.toInt()}% of files",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = iconTint
                        )
                    }

                    if (hasBreakdown) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (progressFraction > 0f) progressFraction.coerceAtLeast(0.02f) else 0f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(iconTint)
                )
            }

            if (hasBreakdown && isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    documentBreakdown.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFEEF2F6))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.formatBadge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                                Text(
                                    text = item.title,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF334155)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${item.fileCount} files",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "·",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = item.formattedSize,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Card for each of the 8 file-type categories:
 * File type, total files, total storage (KB / MB / GB), percentage, and individual progress bar.
 */
@Composable
private fun CategoryStorageCard(
    categoryData: CategoryStorageData,
    modifier: Modifier = Modifier
) {
    val category = categoryData.category
    val styling = getCategoryStyling(category)

    val animatedProgress by animateFloatAsState(
        targetValue = (categoryData.percentageOfDocuments / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "categoryProgress_${category.name}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, styling.borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row 1: Icon + Title & Formats + Occupied Storage & Files Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category Icon / Drawable Badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(styling.backgroundContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (styling.drawableResId != null) {
                            Image(
                                painter = painterResource(id = styling.drawableResId),
                                contentDescription = category.title,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (styling.imageVector != null) {
                            Icon(
                                imageVector = styling.imageVector,
                                contentDescription = category.title,
                                tint = styling.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = category.formatsLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Size & Files Count
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = categoryData.formattedSize,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF183A3C)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${categoryData.fileCount} ${if (categoryData.fileCount == 1) "file" else "files"}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: Individual Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(styling.accentColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Progress Info & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share of documents",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", categoryData.percentageOfDocuments)}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = styling.accentColor
                )
            }
        }
    }
}

/**
 * Visual styling definition for each document category.
 */
private data class CategoryStyling(
    val accentColor: Color,
    val backgroundContainer: Color,
    val borderColor: Color,
    val drawableResId: Int? = null,
    val imageVector: ImageVector? = null
)

private fun getCategoryStyling(category: StorageDocumentCategory): CategoryStyling {
    return when (category) {
        StorageDocumentCategory.PDF -> CategoryStyling(
            accentColor = Color(0xFFEF4444),
            backgroundContainer = Color(0xFFFEE2E2),
            borderColor = Color(0xFFFECACA),
            drawableResId = R.drawable.pdf
        )
        StorageDocumentCategory.POWERPOINT -> CategoryStyling(
            accentColor = Color(0xFFEA580C),
            backgroundContainer = Color(0xFFFFEDD5),
            borderColor = Color(0xFFFED7AA),
            drawableResId = R.drawable.ppt
        )
        StorageDocumentCategory.WORD -> CategoryStyling(
            accentColor = Color(0xFF2563EB),
            backgroundContainer = Color(0xFFDBEAFE),
            borderColor = Color(0xFFBFDBFE),
            drawableResId = R.drawable.doc
        )
        StorageDocumentCategory.EXCEL -> CategoryStyling(
            accentColor = Color(0xFF059669),
            backgroundContainer = Color(0xFFD1FAE5),
            borderColor = Color(0xFFA7F3D0),
            imageVector = Icons.Default.TableChart
        )
        StorageDocumentCategory.TEXT -> CategoryStyling(
            accentColor = Color(0xFF00A896),
            backgroundContainer = Color(0xFFCCECEE),
            borderColor = Color(0xFF99DDE0),
            imageVector = Icons.AutoMirrored.Filled.Article
        )
        StorageDocumentCategory.DATA -> CategoryStyling(
            accentColor = Color(0xFF7C3AED),
            backgroundContainer = Color(0xFFEDE9FE),
            borderColor = Color(0xFFDDD6FE),
            imageVector = Icons.Default.DataObject
        )
        StorageDocumentCategory.ARCHIVE -> CategoryStyling(
            accentColor = Color(0xFFD97706),
            backgroundContainer = Color(0xFFFEF3C7),
            borderColor = Color(0xFFFDE68A),
            imageVector = Icons.Default.FolderZip
        )
        StorageDocumentCategory.OTHER -> CategoryStyling(
            accentColor = Color(0xFF475569),
            backgroundContainer = Color(0xFFF1F5F9),
            borderColor = Color(0xFFE2E8F0),
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile
        )
    }
}
