package com.simats.intellifile.ui.viewer.ppt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.platform.LocalConfiguration
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.ui.viewer.DocumentInfoSection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

// IntelliFile Dusky Aqua Palette
private val DuskyAqua = Color(0xFF55A9AF)
private val LightAqua = Color(0xFF6FBEC3)
private val BackgroundAqua = Color(0xFFEFF8F8)
private val ContainerAqua = Color(0xFFD5ECEE)
private val DarkOceanTeal = Color(0xFF183A3C)
private val MutedAqua = Color(0xFF537779)
private val CardBorderColor = Color(0xFFD5ECEE)

@Composable
fun PPTViewerScreen(
    fileName: String = "Presentation.pptx",
    filePath: String = "",
    fileUriString: String = "",
    documentId: Int? = null,
    onBackClick: () -> Unit = {},
    onShareClick: (() -> Unit)? = null,
    viewModel: PPTViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    val key = if (filePath.isNotBlank()) filePath else fileUriString
    var isBookmarked by remember(key) {
        mutableStateOf(if (key.isNotBlank()) LocalFileDatabaseHelper.getInstance(context).isBookmarked(key) else false)
    }

    LaunchedEffect(key, fileName) {
        if (key.isNotBlank()) {
            LocalFileDatabaseHelper.getInstance(context).recordFileOpened(key, fileName)
        }
    }

    val totalSlides = uiState.totalSlides.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = uiState.currentSlideIndex.coerceIn(0, totalSlides - 1),
        pageCount = { totalSlides }
    )

    // Synchronize horizontal swipe gestures -> ViewModel slide navigation & indicators
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != uiState.currentSlideIndex) {
                viewModel.goToSlide(page)
            }
        }
    }

    // Synchronize ViewModel navigation -> Pager smooth animation
    LaunchedEffect(uiState.currentSlideIndex) {
        if (pagerState.currentPage != uiState.currentSlideIndex) {
            pagerState.animateScrollToPage(uiState.currentSlideIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            viewModel.clearSearch()
        }
    }

    val toggleOrientation: () -> Unit = {
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Load presentation
    LaunchedEffect(filePath, fileUriString, fileName, documentId) {
        viewModel.loadPresentation(
            context = context,
            uriString = fileUriString,
            filePath = filePath,
            fileName = fileName,
            documentId = documentId
        )
    }

    // Auto-scroll thumbnail strip to current slide
    val thumbnailListState = rememberLazyListState()
    LaunchedEffect(uiState.currentSlideIndex) {
        if (uiState.showThumbnailsDrawer && uiState.document != null) {
            thumbnailListState.animateScrollToItem(uiState.currentSlideIndex)
        }
    }

    // Handle back button for fullscreen, notes, and search
    BackHandler {
        when {
            uiState.showJumpDialog -> viewModel.setShowJumpDialog(false)
            uiState.showNotes -> viewModel.toggleNotes()
            uiState.isSearchActive -> viewModel.setSearchActive(false)
            uiState.showThumbnailsDrawer -> viewModel.setThumbnailsDrawer(false)
            uiState.isFullscreen -> viewModel.setFullscreen(false)
            else -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                onBackClick()
            }
        }
    }

    val effectiveShareClick: () -> Unit = onShareClick ?: {
        try {
            val shareUri = if (fileUriString.isNotBlank()) {
                Uri.parse(fileUriString)
            } else if (filePath.isNotBlank()) {
                val f = java.io.File(filePath)
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    f
                )
            } else null

            if (shareUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (fileName.endsWith(".ppt", ignoreCase = true)) {
                        "application/vnd.ms-powerpoint"
                    } else {
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    }
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share $fileName"))
            } else {
                Toast.makeText(context, "Cannot share presentation file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = BackgroundAqua,
        topBar = {
            AnimatedVisibility(
                visible = !uiState.isFullscreen,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(220)) + shrinkVertically(animationSpec = tween(220))
            ) {
                PPTViewerTopBar(
                    fileName = fileName,
                    currentSlideNumber = uiState.currentSlideNumber,
                    totalSlides = uiState.totalSlides,
                    isSearchActive = uiState.isSearchActive,
                    searchQuery = uiState.searchQuery,
                    matchingCount = uiState.matchingSlideIndices.size,
                    hasNotes = !uiState.currentSlideInfo?.notes.isNullOrBlank(),
                    isThumbnailsOpen = uiState.showThumbnailsDrawer,
                    isLandscape = isLandscape,
                    isBookmarked = isBookmarked,
                    onToggleBookmark = {
                        isBookmarked = !isBookmarked
                        if (key.isNotBlank()) {
                            LocalFileDatabaseHelper.getInstance(context).setBookmarked(key, fileName, isBookmarked)
                            Toast.makeText(
                                context,
                                if (isBookmarked) "Added to Bookmarks" else "Removed from Bookmarks",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onBackClick = {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        viewModel.clearSearch()
                        onBackClick()
                    },
                    onToggleSearch = { viewModel.setSearchActive(!uiState.isSearchActive) },
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onNextSearchResult = { viewModel.nextSearchResult() },
                    onPreviousSearchResult = { viewModel.previousSearchResult() },
                    onToggleNotes = { viewModel.toggleNotes() },
                    onToggleThumbnails = { viewModel.toggleThumbnailsDrawer() },
                    onToggleOrientation = toggleOrientation,
                    onToggleFullscreen = { viewModel.toggleFullscreen() },
                    onShareClick = effectiveShareClick
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !uiState.isFullscreen && !uiState.isLoading && uiState.errorMessage == null,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(220)) + shrinkVertically(animationSpec = tween(220))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .shadow(8.dp)
                ) {
                    // Slide Thumbnails Strip
                    AnimatedVisibility(
                        visible = uiState.showThumbnailsDrawer,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        PPTThumbnailsStrip(
                            slides = uiState.document?.slides ?: emptyList(),
                            thumbnails = uiState.thumbnails,
                            currentSlideIndex = uiState.currentSlideIndex,
                            aspectRatio = uiState.document?.aspectRatio ?: (16f / 9f),
                            listState = thumbnailListState,
                            onSlideSelected = { slideIdx ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(slideIdx)
                                    viewModel.goToSlide(slideIdx)
                                }
                            }
                        )
                    }

                    // Main Slide Navigation Bar
                    PPTBottomNavigationBar(
                        currentSlideNumber = uiState.currentSlideNumber,
                        totalSlides = uiState.totalSlides,
                        zoomPercentage = (uiState.zoomScale * 100).toInt(),
                        canGoPrevious = uiState.canGoPrevious,
                        canGoNext = uiState.canGoNext,
                        isLandscape = isLandscape,
                        onPreviousClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                } else {
                                    viewModel.previousSlide()
                                }
                            }
                        },
                        onNextClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage < totalSlides - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                } else {
                                    viewModel.nextSlide()
                                }
                            }
                        },
                        onSlidePillClick = { viewModel.setShowJumpDialog(true) },
                        onZoomIn = { viewModel.zoomIn() },
                        onZoomOut = { viewModel.zoomOut() },
                        onResetZoom = { viewModel.resetZoom() }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    PPTLoadingView()
                }
                uiState.errorMessage != null -> {
                    PPTErrorView(
                        message = uiState.errorMessage ?: "Failed to open presentation.",
                        onRetry = { viewModel.retry(context) },
                        onBack = onBackClick
                    )
                }
                uiState.document != null -> {
                    PPTVisualCanvasView(
                        pagerState = pagerState,
                        aspectRatio = uiState.document?.aspectRatio ?: (16f / 9f),
                        zoomScale = uiState.zoomScale,
                        panOffset = uiState.panOffset,
                        isFullscreen = uiState.isFullscreen,
                        thumbnails = uiState.thumbnails,
                        currentSlideIndex = uiState.currentSlideIndex,
                        currentSlideBitmap = uiState.currentSlideBitmap,
                        onZoomPanChange = { zoomDelta, panDelta ->
                            viewModel.updateZoomAndPan(zoomDelta, panDelta)
                        },
                        onDoubleTap = {
                            if (uiState.zoomScale > 1.1f) viewModel.resetZoom() else viewModel.updateZoomAndPan(2.2f, Offset.Zero)
                        },
                        onToggleControls = {
                            viewModel.toggleFullscreen()
                        }
                    )
                }
            }

            // Speaker Notes Bottom Dialog
            if (uiState.showNotes && !uiState.currentSlideInfo?.notes.isNullOrBlank()) {
                PPTSpeakerNotesDialog(
                    slideNumber = uiState.currentSlideNumber,
                    notes = uiState.currentSlideInfo!!.notes,
                    onDismiss = { viewModel.toggleNotes() }
                )
            }

            // Jump To Slide Dialog
            if (uiState.showJumpDialog) {
                PPTJumpToSlideDialog(
                    currentSlide = uiState.currentSlideNumber,
                    totalSlides = uiState.totalSlides,
                    onConfirm = { slideNum ->
                        val targetIdx = (slideNum - 1).coerceIn(0, totalSlides - 1)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIdx)
                            viewModel.goToSlide(targetIdx)
                        }
                    },
                    onDismiss = { viewModel.setShowJumpDialog(false) }
                )
            }
        }
    }
}

// =========================================================================
// TOP APP BAR
// =========================================================================

@Composable
private fun PPTViewerTopBar(
    fileName: String,
    currentSlideNumber: Int,
    totalSlides: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    matchingCount: Int,
    hasNotes: Boolean,
    isThumbnailsOpen: Boolean,
    isLandscape: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onNextSearchResult: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onToggleNotes: () -> Unit,
    onToggleThumbnails: () -> Unit,
    onToggleOrientation: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onShareClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isLandscape) {
                // Landscape: Compact single row across wide screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkOceanTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    DocumentInfoSection(
                        fileName = fileName,
                        fileFormat = "PPTX",
                        currentPage = currentSlideNumber,
                        totalPages = totalSlides,
                        wordCount = null,
                        isCompact = true,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = onToggleSearch, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchActive) DuskyAqua else DarkOceanTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color(0xFF6C5CE7) else DarkOceanTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (hasNotes) {
                            IconButton(onClick = onToggleNotes, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Speaker Notes",
                                    tint = DuskyAqua,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(onClick = onToggleThumbnails, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Thumbnails",
                                tint = if (isThumbnailsOpen) DuskyAqua else DarkOceanTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onToggleOrientation, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = if (isLandscape) DuskyAqua else DarkOceanTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = DarkOceanTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = DarkOceanTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // Portrait Mode: 2-Tier Layout ensuring Document Information has full width and is never squeezed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DarkOceanTeal
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(onClick = onToggleSearch, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchActive) DuskyAqua else DarkOceanTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) Color(0xFF6C5CE7) else DarkOceanTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (hasNotes) {
                                IconButton(onClick = onToggleNotes, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "Speaker Notes",
                                        tint = DuskyAqua,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(onClick = onToggleThumbnails, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Thumbnails",
                                    tint = if (isThumbnailsOpen) DuskyAqua else DarkOceanTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = onToggleOrientation, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Rotate Screen",
                                    tint = if (isLandscape) DuskyAqua else DarkOceanTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = DarkOceanTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = DarkOceanTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Document Information Section: Full Width, properly spaced, readable font, no clipping
                    DocumentInfoSection(
                        fileName = fileName,
                        fileFormat = "PPTX",
                        currentPage = currentSlideNumber,
                        totalPages = totalSlides,
                        wordCount = null,
                        isCompact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)
                    )
                }
            }

            // Search Bar Input
            AnimatedVisibility(visible = isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text("Search slide contents...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNextSearchResult() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DarkOceanTeal,
                            unfocusedTextColor = DarkOceanTeal,
                            focusedBorderColor = DuskyAqua,
                            unfocusedBorderColor = CardBorderColor,
                            cursorColor = DuskyAqua
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (matchingCount > 0) "$matchingCount match" else "No match",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (matchingCount > 0) DuskyAqua else Color(0xFFEF4444)
                        )

                        if (matchingCount > 1) {
                            IconButton(onClick = onPreviousSearchResult) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Match",
                                    tint = DuskyAqua
                                )
                            }
                            IconButton(onClick = onNextSearchResult) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Match",
                                    tint = DuskyAqua
                                )
                            }
                        }
                    }

                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Search",
                            tint = DarkOceanTeal
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// VISUAL PRESENTATION CANVAS VIEW (WITH ZOOM, PAN & SWIPE)
// =========================================================================

@Composable
private fun PPTVisualCanvasView(
    pagerState: PagerState,
    aspectRatio: Float,
    zoomScale: Float,
    panOffset: Offset,
    isFullscreen: Boolean,
    thumbnails: Map<Int, Bitmap>,
    currentSlideIndex: Int,
    currentSlideBitmap: Bitmap?,
    onZoomPanChange: (Float, Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onToggleControls: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundAqua),
        userScrollEnabled = zoomScale <= 1.05f
    ) { page ->
        val slideBitmap by produceState<Bitmap?>(
            initialValue = if (page == currentSlideIndex && currentSlideBitmap != null) {
                currentSlideBitmap
            } else {
                PPTDocumentHandler.getCachedSlideBitmap(page)
            },
            key1 = page,
            key2 = if (page == currentSlideIndex) currentSlideBitmap else null
        ) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    PPTDocumentHandler.renderSlideBitmap(page, scale = 1.5f)
                }
            }
        }

        val placeholderThumbnail = thumbnails[page]
        val isCurrentPage = page == currentSlideIndex

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isCurrentPage) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onToggleControls() },
                                    onDoubleTap = { onDoubleTap() }
                                )
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var prevDist = 0f
                                    var prevCentroid = Offset.Zero
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.size >= 2) {
                                            // Multi-finger pinch to zoom in/out
                                            val p1 = pressed[0].position
                                            val p2 = pressed[1].position
                                            val currDist = (p1 - p2).getDistance()
                                            val currCentroid = (p1 + p2) / 2f
                                            if (prevDist > 0f) {
                                                val zoomRatio = currDist / prevDist
                                                val panDelta = currCentroid - prevCentroid
                                                onZoomPanChange(zoomRatio, panDelta)
                                            }
                                            prevDist = currDist
                                            prevCentroid = currCentroid
                                            event.changes.forEach { it.consume() }
                                        } else if (pressed.size == 1 && zoomScale > 1.05f) {
                                            // Single finger pan when zoomed into slide
                                            val change = pressed[0]
                                            val panDelta = change.positionChange()
                                            if (panDelta != Offset.Zero) {
                                                onZoomPanChange(1.0f, panDelta)
                                                change.consume()
                                            }
                                            prevDist = 0f
                                        } else {
                                            // Single finger at 1.0x: allow HorizontalPager horizontal swipe
                                            prevDist = 0f
                                        }
                                    } while (pressed.isNotEmpty())
                                }
                            }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val safeAspect = if (aspectRatio > 0.5f) aspectRatio else 16f / 9f
                val maxAvailableWidth = maxWidth * (if (isFullscreen) 1f else 0.98f)
                val maxAvailableHeight = maxHeight * (if (isFullscreen) 1f else 0.96f)

                val widthBasedHeight = maxAvailableWidth / safeAspect
                val heightBasedWidth = maxAvailableHeight * safeAspect

                val slideWidth: androidx.compose.ui.unit.Dp
                val slideHeight: androidx.compose.ui.unit.Dp
                if (widthBasedHeight <= maxAvailableHeight) {
                    slideWidth = maxAvailableWidth
                    slideHeight = widthBasedHeight
                } else {
                    slideWidth = heightBasedWidth
                    slideHeight = maxAvailableHeight
                }

                Box(
                    modifier = Modifier
                        .size(slideWidth, slideHeight)
                        .graphicsLayer {
                            if (isCurrentPage) {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                        }
                ) {
                    PPTRenderingManager.RenderVisualSlide(
                        bitmap = slideBitmap,
                        placeholderThumbnail = placeholderThumbnail,
                        aspectRatio = safeAspect,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// =========================================================================
// BOTTOM NAVIGATION BAR
// =========================================================================

@Composable
private fun PPTBottomNavigationBar(
    currentSlideNumber: Int,
    totalSlides: Int,
    zoomPercentage: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    isLandscape: Boolean = false,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSlidePillClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit
) {
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isLandscape) 2.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Slide Button
            FilledIconButton(
                onClick = onPreviousClick,
                enabled = canGoPrevious,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (canGoPrevious) DuskyAqua else Color(0xFFE2E8F0),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFF1F5F9),
                    disabledContentColor = Color(0xFF94A3B8)
                ),
                shape = CircleShape,
                modifier = Modifier.size(if (isLandscape) 34.dp else 42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Slide",
                    modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                )
            }

            // Middle: Slide Counter Pill & Zoom Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
            ) {
                // Zoom Out Button
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.size(if (isLandscape) 30.dp else 36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = DarkOceanTeal,
                        modifier = Modifier.size(if (isLandscape) 18.dp else 24.dp)
                    )
                }

                // Slide Indicator Pill (Clickable to jump)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ContainerAqua)
                        .border(1.dp, DuskyAqua.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onSlidePillClick)
                        .padding(horizontal = if (isLandscape) 8.dp else 12.dp, vertical = if (isLandscape) 3.dp else 6.dp)
                ) {
                    Text(
                        text = "$currentSlideNumber / $totalSlides",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLandscape) 11.5.sp else 13.sp,
                        color = DarkOceanTeal
                    )
                }

                // Zoom Level Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = if (isLandscape) 8.dp else 10.dp, vertical = if (isLandscape) 3.dp else 6.dp)
                ) {
                    Text(
                        text = "$zoomPercentage%",
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isLandscape) 11.5.sp else 12.sp,
                        color = MutedAqua
                    )
                }

                // Zoom In Button
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.size(if (isLandscape) 30.dp else 36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = DarkOceanTeal,
                        modifier = Modifier.size(if (isLandscape) 18.dp else 24.dp)
                    )
                }
            }

            // Next Slide Button
            FilledIconButton(
                onClick = onNextClick,
                enabled = canGoNext,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (canGoNext) DuskyAqua else Color(0xFFE2E8F0),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFF1F5F9),
                    disabledContentColor = Color(0xFF94A3B8)
                ),
                shape = CircleShape,
                modifier = Modifier.size(if (isLandscape) 34.dp else 42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Slide",
                    modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                )
            }
        }
    }
}

// =========================================================================
// THUMBNAILS HORIZONTAL STRIP
// =========================================================================

@Composable
private fun PPTThumbnailsStrip(
    slides: List<PPTSlideInfo>,
    thumbnails: Map<Int, Bitmap>,
    currentSlideIndex: Int,
    aspectRatio: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSlideSelected: (Int) -> Unit
) {
    Surface(
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = "SLIDES OVERVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedAqua,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(slides) { index, slide ->
                    PPTRenderingManager.VisualSlideThumbnailItem(
                        slideNumber = slide.slideNumber,
                        title = slide.title,
                        thumbnailBitmap = thumbnails[index],
                        isSelected = index == currentSlideIndex,
                        aspectRatio = aspectRatio,
                        onClick = { onSlideSelected(index) }
                    )
                }
            }
        }
    }
}

// =========================================================================
// FULLSCREEN FLOATING OVERLAY CONTROLS
// =========================================================================

@Composable
private fun BoxScope.PPTFullscreenOverlayControls(
    currentSlideNumber: Int,
    totalSlides: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    isLandscape: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleOrientation: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    // Screen Rotation Button
    FilledIconButton(
        onClick = onToggleOrientation,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White
        ),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 16.dp, end = 68.dp)
            .size(44.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = "Rotate Screen"
        )
    }

    // Exit Fullscreen Button at Top-Right
    FilledIconButton(
        onClick = onExitFullscreen,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White
        ),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .size(44.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FullscreenExit,
            contentDescription = "Exit Fullscreen"
        )
    }

    // Bottom Navigation Pill in Fullscreen
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onPreviousClick,
                enabled = canGoPrevious,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Slide",
                    tint = if (canGoPrevious) Color.White else Color.Gray
                )
            }

            Text(
                text = "$currentSlideNumber / $totalSlides",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            IconButton(
                onClick = onNextClick,
                enabled = canGoNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Slide",
                    tint = if (canGoNext) Color.White else Color.Gray
                )
            }
        }
    }
}

// =========================================================================
// SPEAKER NOTES DIALOG
// =========================================================================

@Composable
private fun PPTSpeakerNotesDialog(
    slideNumber: Int,
    notes: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Slide $slideNumber Speaker Notes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DarkOceanTeal
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = DarkOceanTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .background(BackgroundAqua, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = notes,
                        fontSize = 13.sp,
                        color = DarkOceanTeal,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// JUMP TO SLIDE DIALOG
// =========================================================================

@Composable
private fun PPTJumpToSlideDialog(
    currentSlide: Int,
    totalSlides: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("$currentSlide") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Jump to Slide",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkOceanTeal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter slide number (1 to $totalSlides)",
                    fontSize = 13.sp,
                    color = MutedAqua
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.all { char -> char.isDigit() }) input = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DuskyAqua,
                        cursorColor = DuskyAqua
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(140.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MutedAqua)
                    }

                    Button(
                        onClick = {
                            val target = input.toIntOrNull()
                            if (target != null && target in 1..totalSlides) {
                                onConfirm(target)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Jump", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// LOADING & ERROR VIEWS
// =========================================================================

@Composable
private fun PPTLoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = DuskyAqua,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Rendering PowerPoint Presentation...",
                color = DarkOceanTeal,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Generating visual slides, themes and graphics",
                color = MutedAqua,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PPTErrorView(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Unable to Render Presentation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = DarkOceanTeal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = MutedAqua,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Go Back", color = DarkOceanTeal)
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = DuskyAqua),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

