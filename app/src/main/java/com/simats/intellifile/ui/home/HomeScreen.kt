package com.simats.intellifile.ui.home

import android.Manifest
import androidx.compose.foundation.border
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PriorityLevel(
    val title: String,
    val count: Int,
    val backgroundColor: Color,
    val iconBackgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector
) {
    IMPORTANT(
        title = "Important",
        count = 0,
        backgroundColor = Color(0xFFFFEAEE),
        iconBackgroundColor = Color(0xFFEF4444),
        textColor = Color(0xFFEF4444),
        icon = Icons.Default.PriorityHigh
    ),
    NORMAL(
        title = "Normal",
        count = 0,
        backgroundColor = Color(0xFFFFF6D8),
        iconBackgroundColor = Color(0xFFD97706),
        textColor = Color(0xFFD97706),
        icon = Icons.Default.Remove
    ),
    LOW(
        title = "Low",
        count = 0,
        backgroundColor = Color(0xFFE2F7ED),
        iconBackgroundColor = Color(0xFF10B981),
        textColor = Color(0xFF10B981),
        icon = Icons.Default.Check
    )
}

data class RecentFile(
    val title: String,
    val format: String,
    val size: String,
    val dateText: String,
    val priority: PriorityLevel? = null,
    val iconResId: Int = R.drawable.pdf,
    val uriString: String = "",
    val path: String = ""
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSeeAllClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onPriorityCardClick: (PriorityLevel) -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onShareFileClick: (RecentFile) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var realFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasStoragePermission by remember { mutableStateOf(LocalFileManager.hasStoragePermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = LocalFileManager.hasStoragePermission(context)
                if (updated != hasStoragePermission) {
                    hasStoragePermission = updated
                    refreshTrigger++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = LocalFileManager.hasStoragePermission(context)
        refreshTrigger++
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        refreshTrigger++
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(fallbackIntent)
                } catch (e2: Exception) {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(settingsIntent)
                }
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Query real document files asynchronously from storage
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        hasStoragePermission = LocalFileManager.hasStoragePermission(context)
        val loaded = LocalFileManager.getDeviceFiles(context)
        realFiles = loaded
        isLoading = false
    }

    // SAF Document picker strictly for documents (no images/audio)
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            refreshTrigger++
        }
    }

    val allRecentFiles = remember(realFiles) {
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

    // Priority counts reflect strictly user-marked files
    val importantCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.IMPORTANT } }
    val normalCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.NORMAL } }
    val lowCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.LOW } }

    val filteredFiles = remember(searchQuery, allRecentFiles) {
        if (searchQuery.isBlank()) {
            allRecentFiles.take(10)
        } else {
            allRecentFiles.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Search Bar Section
            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onMicClick = onMicClick
                )
            }

            // Prominent Permission Banner if permission is not yet granted
            if (!hasStoragePermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF8F8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFF55A9AF), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF55A9AF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Grant Document Access",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF183A3C)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Allow document access to find and organize your files.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF537779),
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { requestStoragePermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A9AF)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Grant", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Summary Cards Row with Real Marked Counts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrioritySummaryCard(
                        priority = PriorityLevel.IMPORTANT,
                        count = importantCount,
                        onClick = { onPriorityCardClick(PriorityLevel.IMPORTANT) },
                        modifier = Modifier.weight(1f)
                    )
                    PrioritySummaryCard(
                        priority = PriorityLevel.NORMAL,
                        count = normalCount,
                        onClick = { onPriorityCardClick(PriorityLevel.NORMAL) },
                        modifier = Modifier.weight(1f)
                    )
                    PrioritySummaryCard(
                        priority = PriorityLevel.LOW,
                        count = lowCount,
                        onClick = { onPriorityCardClick(PriorityLevel.LOW) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent Files Header with Refresh button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Recent Files",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        IconButton(
                            onClick = { refreshTrigger++ },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Files",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "See all (${allRecentFiles.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.clickable { onSeeAllClick() }
                    )
                }
            }

            // Empty state when phone has no scanned documents
            if (!isLoading && filteredFiles.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Empty",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "No documents found on device",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "IntelliFile reads document files (PDF, Word, PPT, Excel, Text) locally on your phone.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (!hasStoragePermission) {
                            Button(
                                onClick = { requestStoragePermission() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF55A9AF)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Grant Document Access", fontSize = 14.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    openDocLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.ms-powerpoint",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "text/plain",
                                            "text/csv"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Documents to View", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Recent Real File Items
            items(filteredFiles) { file ->
                RecentFileCard(
                    file = file,
                    onClick = { onFileClick(file) },
                    onShareClick = { onShareFileClick(file) }
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMicPressed by remember { mutableStateOf(false) }
    val micScale by animateFloatAsState(
        targetValue = if (isMicPressed) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "micScale"
    )

    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search Icon",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search documents or ask IntelliFile...",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Animated Mic / Speaker Button with Pulsing Wave Aura
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clickable {
                    coroutineScope.launch {
                        isMicPressed = true
                        delay(180L)
                        isMicPressed = false
                        onMicClick()
                    }
                }
        ) {
            if (isMicPressed) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF26E07F).copy(alpha = 0.8f),
                                Color(0xFF55A9AF).copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer(scaleX = micScale, scaleY = micScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PrioritySummaryCard(
    priority: PriorityLevel,
    count: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(priority.backgroundColor)
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

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = (count ?: priority.count).toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RecentFileCard(
    file: RecentFile,
    onClick: () -> Unit,
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = file.iconResId),
            contentDescription = "File Type Icon",
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${file.format} · ${file.size} · ${file.dateText}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Display priority badge only if the user has explicitly assigned one
        if (file.priority != null) {
            PriorityTag(priority = file.priority)
            Spacer(modifier = Modifier.width(6.dp))
        }

        IconButton(
            onClick = onShareClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share File",
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PriorityTag(
    priority: PriorityLevel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(priority.backgroundColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(priority.iconBackgroundColor)
        )

        Spacer(modifier = Modifier.width(5.dp))

        Text(
            text = priority.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = priority.textColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}
