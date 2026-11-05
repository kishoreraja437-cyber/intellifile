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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.simats.intellifile.ui.common.DraggableScrollbar

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path

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
    val icon: ImageVector,
    val badgeResId: Int = R.drawable.priority_badge_important,
    val borderColor: Color = Color(0xFFFFA0B4),
    val glowColor: Color = Color(0xFFFF6B8B),
    val gradientColors: List<Color> = listOf(Color(0xFFFFF0F4), Color(0xFFFFE2EA))
) {
    IMPORTANT(
        title = "Important",
        count = 0,
        backgroundColor = Color(0xFFFFF0F3),
        iconBackgroundColor = Color(0xFFE11D48),
        textColor = Color(0xFFE11D48),
        icon = Icons.Default.PriorityHigh,
        badgeResId = R.drawable.priority_badge_important,
        borderColor = Color(0xFFFFA0B4),
        glowColor = Color(0xFFFF6B8B).copy(alpha = 0.35f),
        gradientColors = listOf(Color(0xFFFFF0F4), Color(0xFFFFE2EA))
    ),
    NORMAL(
        title = "Normal",
        count = 0,
        backgroundColor = Color(0xFFFFFBF0),
        iconBackgroundColor = Color(0xFFF59E0B),
        textColor = Color(0xFFD97706),
        icon = Icons.Default.Remove,
        badgeResId = R.drawable.priority_badge_normal,
        borderColor = Color(0xFFF6C768),
        glowColor = Color(0xFFF59E0B).copy(alpha = 0.35f),
        gradientColors = listOf(Color(0xFFFFFDF5), Color(0xFFFFF2D6))
    ),
    LOW(
        title = "Low",
        count = 0,
        backgroundColor = Color(0xFFF0FDF4),
        iconBackgroundColor = Color(0xFF10B981),
        textColor = Color(0xFF059669),
        icon = Icons.Default.Check,
        badgeResId = R.drawable.priority_badge_low,
        borderColor = Color(0xFF86EFAC),
        glowColor = Color(0xFF10B981).copy(alpha = 0.35f),
        gradientColors = listOf(Color(0xFFF0FDF5), Color(0xFFDCFCE7))
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

    externalSearchQuery: String = "",

    onSeeAllClick: () -> Unit = {},

    onMicClick: () -> Unit = {},

    onPriorityCardClick: (PriorityLevel) -> Unit = {},

    onFileClick: (RecentFile) -> Unit = {},

    onShareFileClick: (RecentFile) -> Unit = {}

) {

    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf(externalSearchQuery) }



    LaunchedEffect(externalSearchQuery) {

        if (externalSearchQuery.isNotBlank()) {

            searchQuery = externalSearchQuery

        }

    }

    val listState = rememberLazyListState()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val refreshRotation = remember { Animatable(0f) }
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            refreshRotation.animateTo(
                targetValue = refreshRotation.value + 360f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
    }

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

            state = listState,

            modifier = Modifier.fillMaxSize(),

            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),

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

                                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = refreshRotation.value)

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

                            text = "IntelliFile reads document files (PDF, Word, Excel, Text) locally on your phone.",

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

        DraggableScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 16.dp, bottom = 16.dp)
        )
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

    val infiniteTransition = rememberInfiniteTransition(label = "ambientMicPulse")
    val idlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulseScale"
    )
    val idlePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulseAlpha"
    )

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

                    text = "Search...",

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

            // Ambient breathing wave
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = idlePulseScale, scaleY = idlePulseScale, alpha = idlePulseAlpha)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF26E07F).copy(alpha = 0.6f),
                            Color(0xFF55A9AF).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
            }

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
    val interactionSource = remember { MutableInteractionSource() }
    val isCardPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = priority.glowColor,
                spotColor = priority.glowColor,
                clip = false
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(priority.gradientColors)
            )
            .border(
                width = 1.8.dp,
                color = priority.borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        // Specular 3D Glass Arc Highlight in top-left corner
        Canvas(modifier = Modifier.matchParentSize()) {
            val cornerRadius = 24.dp.toPx()
            val highlightWidth = size.width * 0.44f
            val highlightHeight = size.height * 0.32f

            // Soft specular gleam oval in top-left curve
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(cornerRadius * 0.85f, cornerRadius * 0.80f),
                    radius = cornerRadius * 1.3f
                ),
                topLeft = Offset(7.dp.toPx(), 6.dp.toPx()),
                size = Size(highlightWidth, highlightHeight)
            )

            // Inner glass rim line along top edge
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    startX = cornerRadius * 0.7f,
                    endX = size.width * 0.65f
                ),
                start = Offset(cornerRadius * 0.7f, 2.5.dp.toPx()),
                end = Offset(size.width * 0.65f, 2.5.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Card Content: 3D Badge, Title, Dynamic Count
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 18.dp, start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = priority.badgeResId),
                contentDescription = priority.title,
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = priority.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = priority.textColor,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = (count ?: priority.count).toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1
            )
        }
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

            .border(1.dp, priority.borderColor, CircleShape)

            .padding(horizontal = 8.dp, vertical = 4.dp),

        verticalAlignment = Alignment.CenterVertically

    ) {

        Image(

            painter = painterResource(id = priority.badgeResId),

            contentDescription = priority.title,

            modifier = Modifier.size(14.dp)

        )



        Spacer(modifier = Modifier.width(5.dp))



        Text(

            text = priority.title,

            fontSize = 11.sp,

            fontWeight = FontWeight.Bold,

            color = priority.textColor

        )

    }

}



@Preview(showBackground = true)

@Composable

fun HomeScreenPreview() {

    HomeScreen()

}

