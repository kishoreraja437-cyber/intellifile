package com.simats.intellifile.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Interactive, touch-accessible draggable scrollbar for LazyColumn.
 * Always accessible along the right edge with a comfortable touch strip, expands on grab,
 * and allows fast-scrolling/scrubbing by dragging or tapping directly on the track.
 */
@Composable
fun DraggableScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    defaultWidth: Dp = 6.dp,
    expandedWidth: Dp = 12.dp,
    minThumbHeight: Dp = 44.dp,
    touchWidth: Dp = 36.dp,
    paddingEnd: Dp = 3.dp,
    paddingTop: Dp = 4.dp,
    paddingBottom: Dp = 4.dp,
    alwaysVisible: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragJob by remember { mutableStateOf<Job?>(null) }

    val resolvedThumbColor = if (thumbColor.isSpecified) thumbColor else MaterialTheme.colorScheme.primary
    val resolvedTrackColor = if (trackColor.isSpecified) trackColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val isScrollInProgress = state.isScrollInProgress
    val isInteracting = isScrollInProgress || isDragging

    val alpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else if (alwaysVisible) 0.45f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "draggableScrollbarAlpha"
    )

    val currentThumbWidth by animateDpAsState(
        targetValue = if (isDragging) expandedWidth else defaultWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "draggableScrollbarWidth"
    )

    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo

    if (totalItems <= 1 || visibleItems.isEmpty()) return

    val allItemsVisible = visibleItems.size >= totalItems
    val firstItem = visibleItems.first()
    val lastItem = visibleItems.last()
    val isFirstAtTop = firstItem.index == 0 && firstItem.offset >= 0
    val isLastAtBottom = lastItem.index == totalItems - 1 &&
            (lastItem.offset + lastItem.size) <= (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)

    if (allItemsVisible && isFirstAtTop && isLastAtBottom) return

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val paddingTopPx = with(density) { paddingTop.toPx() }
    val paddingBottomPx = with(density) { paddingBottom.toPx() }
    val paddingEndPx = with(density) { paddingEnd.toPx() }

    fun scrollToFraction(fraction: Float, trackHeight: Float, thumbH: Float) {
        val scrollableTrack = (trackHeight - thumbH).coerceAtLeast(1f)
        val clampedThumbTop = (fraction * scrollableTrack).coerceIn(0f, scrollableTrack)
        val progress = (clampedThumbTop / scrollableTrack).coerceIn(0f, 1f)

        val totalScrollable = (totalItems - 1).coerceAtLeast(1)
        val targetFloat = progress * totalScrollable
        val targetIndex = targetFloat.toInt().coerceIn(0, totalItems - 1)
        val subProgress = targetFloat - targetIndex

        val avgItemSize = if (visibleItems.isNotEmpty()) {
            visibleItems.sumOf { it.size } / visibleItems.size
        } else 100
        val targetOffset = (subProgress * avgItemSize).toInt()

        dragJob?.cancel()
        dragJob = coroutineScope.launch {
            state.scrollToItem(targetIndex, targetOffset)
        }
    }

    Box(
        modifier = modifier
            .width(touchWidth)
            .pointerInput(totalItems) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val availableH = (size.height.toFloat() - paddingTopPx - paddingBottomPx).coerceAtLeast(1f)
                        val ratio = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 0.70f)
                        val thumbH = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
                        val relativeY = (offset.y - paddingTopPx - thumbH / 2f).coerceAtLeast(0f)
                        val scrollableH = (availableH - thumbH).coerceAtLeast(1f)
                        val frac = (relativeY / scrollableH).coerceIn(0f, 1f)
                        scrollToFraction(frac, availableH, thumbH)

                        tryAwaitRelease()
                        isDragging = false
                    }
                )
            }
            .pointerInput(totalItems) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val availableH = (size.height.toFloat() - paddingTopPx - paddingBottomPx).coerceAtLeast(1f)
                        val ratio = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 0.70f)
                        val thumbH = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
                        val relativeY = (offset.y - paddingTopPx - thumbH / 2f).coerceAtLeast(0f)
                        val scrollableH = (availableH - thumbH).coerceAtLeast(1f)
                        val frac = (relativeY / scrollableH).coerceIn(0f, 1f)
                        scrollToFraction(frac, availableH, thumbH)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val availableH = (size.height.toFloat() - paddingTopPx - paddingBottomPx).coerceAtLeast(1f)
                        val ratio = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 0.70f)
                        val thumbH = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
                        val relativeY = (change.position.y - paddingTopPx - thumbH / 2f).coerceAtLeast(0f)
                        val scrollableH = (availableH - thumbH).coerceAtLeast(1f)
                        val frac = (relativeY / scrollableH).coerceIn(0f, 1f)
                        scrollToFraction(frac, availableH, thumbH)
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(touchWidth)
        ) {
            val availableH = (size.height - paddingTopPx - paddingBottomPx).coerceAtLeast(0f)
            if (availableH <= 0f) return@Canvas

            val firstVisible = state.firstVisibleItemIndex
            val firstOffset = state.firstVisibleItemScrollOffset
            val firstItemSize = firstItem.size.toFloat().coerceAtLeast(1f)
            val fractionalIndex = firstVisible + (firstOffset.toFloat() / firstItemSize).coerceIn(0f, 1f)

            val visibleCount = visibleItems.size.toFloat()
            val maxScrollableItems = (totalItems - visibleCount).coerceAtLeast(1f)
            val scrollProgress = (fractionalIndex / maxScrollableItems).coerceIn(0f, 1f)

            val viewportRatio = (visibleCount / totalItems.toFloat()).coerceIn(0.08f, 0.70f)
            val thumbHeight = (availableH * viewportRatio).coerceAtLeast(minThumbHeightPx)
            val scrollableArea = (availableH - thumbHeight).coerceAtLeast(0f)
            val thumbY = paddingTopPx + (scrollProgress * scrollableArea)

            val thumbWPx = currentThumbWidth.toPx()
            val cornerR = thumbWPx / 2f
            val thumbX = size.width - paddingEndPx - thumbWPx

            // Draw track line
            drawRoundRect(
                color = resolvedTrackColor.copy(alpha = resolvedTrackColor.alpha * alpha.coerceAtLeast(0.3f)),
                topLeft = Offset(thumbX + (thumbWPx - 4.dp.toPx()) / 2f, paddingTopPx),
                size = Size(4.dp.toPx(), availableH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Draw thumb pill
            drawRoundRect(
                color = resolvedThumbColor.copy(alpha = (if (isDragging) 1.0f else 0.85f) * alpha),
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbWPx, thumbHeight),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
        }
    }
}

/**
 * Interactive, touch-accessible draggable scrollbar for Column (ScrollState).
 */
@Composable
fun DraggableScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    defaultWidth: Dp = 6.dp,
    expandedWidth: Dp = 12.dp,
    minThumbHeight: Dp = 44.dp,
    touchWidth: Dp = 36.dp,
    paddingEnd: Dp = 3.dp,
    paddingTop: Dp = 4.dp,
    paddingBottom: Dp = 4.dp,
    alwaysVisible: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragJob by remember { mutableStateOf<Job?>(null) }

    val resolvedThumbColor = if (thumbColor.isSpecified) thumbColor else MaterialTheme.colorScheme.primary
    val resolvedTrackColor = if (trackColor.isSpecified) trackColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val isScrollInProgress = state.isScrollInProgress
    val isInteracting = isScrollInProgress || isDragging

    val alpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else if (alwaysVisible) 0.45f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollStateAlpha"
    )

    val currentThumbWidth by animateDpAsState(
        targetValue = if (isDragging) expandedWidth else defaultWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollStateWidth"
    )

    val maxValue = state.maxValue
    if (maxValue <= 0) return

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val paddingTopPx = with(density) { paddingTop.toPx() }
    val paddingBottomPx = with(density) { paddingBottom.toPx() }
    val paddingEndPx = with(density) { paddingEnd.toPx() }

    fun scrollToFraction(fraction: Float, trackHeight: Float, thumbH: Float) {
        val scrollableTrack = (trackHeight - thumbH).coerceAtLeast(1f)
        val clampedThumbTop = (fraction * scrollableTrack).coerceIn(0f, scrollableTrack)
        val progress = (clampedThumbTop / scrollableTrack).coerceIn(0f, 1f)
        val targetScroll = (progress * maxValue.toFloat()).toInt().coerceIn(0, maxValue)

        dragJob?.cancel()
        dragJob = coroutineScope.launch {
            state.scrollTo(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .width(touchWidth)
            .pointerInput(maxValue) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val availableH = (size.height.toFloat() - paddingTopPx - paddingBottomPx).coerceAtLeast(1f)
                        val totalH = (size.height.toFloat() + maxValue.toFloat()).coerceAtLeast(1f)
                        val ratio = (size.height.toFloat() / totalH).coerceIn(0.08f, 0.70f)
                        val thumbH = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
                        val relativeY = (offset.y - paddingTopPx - thumbH / 2f).coerceAtLeast(0f)
                        val scrollableH = (availableH - thumbH).coerceAtLeast(1f)
                        val frac = (relativeY / scrollableH).coerceIn(0f, 1f)
                        scrollToFraction(frac, availableH, thumbH)

                        tryAwaitRelease()
                        isDragging = false
                    }
                )
            }
            .pointerInput(maxValue) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val availableH = (size.height.toFloat() - paddingTopPx - paddingBottomPx).coerceAtLeast(1f)
                        val totalH = (size.height.toFloat() + maxValue.toFloat()).coerceAtLeast(1f)
                        val ratio = (size.height.toFloat() / totalH).coerceIn(0.08f, 0.70f)
                        val thumbH = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
                        val relativeY = (offset.y - paddingTopPx - thumbH / 2f).coerceAtLeast(0f)
                        val scrollableH = (availableH - thumbH).coerceAtLeast(1f)
                        val frac = (relativeY / scrollableH).coerceIn(0f, 1f)
                        scrollToFraction(frac, availableH, thumbH)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val availableH = (size.height.toFloat() - paddingTopPx - paddingBottomPx).coerceAtLeast(1f)
                        val totalH = (size.height.toFloat() + maxValue.toFloat()).coerceAtLeast(1f)
                        val ratio = (size.height.toFloat() / totalH).coerceIn(0.08f, 0.70f)
                        val thumbH = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
                        val relativeY = (change.position.y - paddingTopPx - thumbH / 2f).coerceAtLeast(0f)
                        val scrollableH = (availableH - thumbH).coerceAtLeast(1f)
                        val frac = (relativeY / scrollableH).coerceIn(0f, 1f)
                        scrollToFraction(frac, availableH, thumbH)
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(touchWidth)
        ) {
            val availableH = (size.height - paddingTopPx - paddingBottomPx).coerceAtLeast(0f)
            if (availableH <= 0f) return@Canvas

            val totalH = (size.height + maxValue).coerceAtLeast(1f)
            val ratio = (size.height / totalH).coerceIn(0.08f, 0.70f)
            val thumbHeight = (availableH * ratio).coerceAtLeast(minThumbHeightPx)
            val scrollableArea = (availableH - thumbHeight).coerceAtLeast(0f)
            val scrollProgress = (state.value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            val thumbY = paddingTopPx + (scrollProgress * scrollableArea)

            val thumbWPx = currentThumbWidth.toPx()
            val cornerR = thumbWPx / 2f
            val thumbX = size.width - paddingEndPx - thumbWPx

            // Draw track line
            drawRoundRect(
                color = resolvedTrackColor.copy(alpha = resolvedTrackColor.alpha * alpha.coerceAtLeast(0.3f)),
                topLeft = Offset(thumbX + (thumbWPx - 4.dp.toPx()) / 2f, paddingTopPx),
                size = Size(4.dp.toPx(), availableH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Draw thumb pill
            drawRoundRect(
                color = resolvedThumbColor.copy(alpha = (if (isDragging) 1.0f else 0.85f) * alpha),
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbWPx, thumbHeight),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
        }
    }
}

/**
 * Draws an auto-fading vertical scrollbar on a LazyColumn via drawWithContent.
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    thumbColor: Color = Color.Unspecified,
    trackColor: Color = Color.Transparent,
    thumbWidth: Dp = 5.dp,
    minThumbHeight: Dp = 40.dp,
    paddingEnd: Dp = 3.dp,
    paddingTop: Dp = 4.dp,
    paddingBottom: Dp = 4.dp,
    alwaysShow: Boolean = true
): Modifier = composed {
    val resolvedThumbColor = if (thumbColor.isSpecified) thumbColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)

    val isScrollInProgress = state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrollInProgress) 1f else if (alwaysShow) 0.45f else 0f,
        animationSpec = tween(
            durationMillis = if (isScrollInProgress) 150 else 450,
            delayMillis = if (isScrollInProgress) 0 else 750
        ),
        label = "lazyScrollbarAlpha"
    )

    val minThumbHeightPx = with(LocalDensity.current) { minThumbHeight.toPx() }
    val thumbWidthPx = with(LocalDensity.current) { thumbWidth.toPx() }
    val paddingEndPx = with(LocalDensity.current) { paddingEnd.toPx() }
    val paddingTopPx = with(LocalDensity.current) { paddingTop.toPx() }
    val paddingBottomPx = with(LocalDensity.current) { paddingBottom.toPx() }

    this.drawWithContent {
        drawContent()

        if (alpha > 0.01f) {
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo

            if (totalItems > 1 && visibleItems.isNotEmpty()) {
                val allItemsVisible = visibleItems.size >= totalItems
                val firstItem = visibleItems.first()
                val lastItem = visibleItems.last()
                val isFirstAtTop = firstItem.index == 0 && firstItem.offset >= 0
                val isLastAtBottom = lastItem.index == totalItems - 1 &&
                        (lastItem.offset + lastItem.size) <= (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)

                if (!(allItemsVisible && isFirstAtTop && isLastAtBottom)) {
                    val firstVisible = state.firstVisibleItemIndex
                    val firstOffset = state.firstVisibleItemScrollOffset
                    val firstItemSize = firstItem.size.toFloat().coerceAtLeast(1f)
                    val fractionalIndex = firstVisible + (firstOffset.toFloat() / firstItemSize).coerceIn(0f, 1f)

                    val visibleCount = visibleItems.size.toFloat()
                    val maxScrollableItems = (totalItems - visibleCount).coerceAtLeast(1f)
                    val scrollProgress = (fractionalIndex / maxScrollableItems).coerceIn(0f, 1f)

                    val viewportHeight = size.height
                    val availableTrackHeight = (viewportHeight - paddingTopPx - paddingBottomPx).coerceAtLeast(0f)
                    val viewportRatio = (visibleCount / totalItems.toFloat()).coerceIn(0.08f, 0.70f)
                    val thumbHeight = (availableTrackHeight * viewportRatio).coerceAtLeast(minThumbHeightPx)
                    val scrollableArea = (availableTrackHeight - thumbHeight).coerceAtLeast(0f)
                    val thumbY = paddingTopPx + (scrollProgress * scrollableArea)
                    val thumbX = size.width - paddingEndPx - thumbWidthPx
                    val cornerR = thumbWidthPx / 2f

                    if (trackColor.alpha > 0f) {
                        drawRoundRect(
                            color = trackColor.copy(alpha = trackColor.alpha * alpha),
                            topLeft = Offset(thumbX, paddingTopPx),
                            size = Size(thumbWidthPx, availableTrackHeight),
                            cornerRadius = CornerRadius(cornerR, cornerR)
                        )
                    }

                    drawRoundRect(
                        color = resolvedThumbColor.copy(alpha = resolvedThumbColor.alpha * alpha),
                        topLeft = Offset(thumbX, thumbY),
                        size = Size(thumbWidthPx, thumbHeight),
                        cornerRadius = CornerRadius(cornerR, cornerR)
                    )
                }
            }
        }
    }
}

/**
 * Draws an auto-fading vertical scrollbar on a scrollable Column (ScrollState).
 */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    thumbColor: Color = Color.Unspecified,
    trackColor: Color = Color.Transparent,
    thumbWidth: Dp = 5.dp,
    minThumbHeight: Dp = 40.dp,
    paddingEnd: Dp = 3.dp,
    paddingTop: Dp = 4.dp,
    paddingBottom: Dp = 4.dp,
    alwaysShow: Boolean = true
): Modifier = composed {
    val resolvedThumbColor = if (thumbColor.isSpecified) thumbColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)

    val isScrollInProgress = state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrollInProgress) 1f else if (alwaysShow) 0.45f else 0f,
        animationSpec = tween(
            durationMillis = if (isScrollInProgress) 150 else 450,
            delayMillis = if (isScrollInProgress) 0 else 750
        ),
        label = "scrollStateAlpha"
    )

    val minThumbHeightPx = with(LocalDensity.current) { minThumbHeight.toPx() }
    val thumbWidthPx = with(LocalDensity.current) { thumbWidth.toPx() }
    val paddingEndPx = with(LocalDensity.current) { paddingEnd.toPx() }
    val paddingTopPx = with(LocalDensity.current) { paddingTop.toPx() }
    val paddingBottomPx = with(LocalDensity.current) { paddingBottom.toPx() }

    this.drawWithContent {
        drawContent()

        if (alpha > 0.01f) {
            val maxValue = state.maxValue
            if (maxValue > 0) {
                val viewportHeight = size.height
                val availableTrackHeight = (viewportHeight - paddingTopPx - paddingBottomPx).coerceAtLeast(0f)
                val totalHeight = (viewportHeight + maxValue).coerceAtLeast(1f)
                val ratio = (viewportHeight / totalHeight).coerceIn(0.08f, 0.70f)
                val thumbHeight = (availableTrackHeight * ratio).coerceAtLeast(minThumbHeightPx)
                val scrollableArea = (availableTrackHeight - thumbHeight).coerceAtLeast(0f)
                val scrollProgress = (state.value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val thumbY = paddingTopPx + (scrollProgress * scrollableArea)
                val thumbX = size.width - paddingEndPx - thumbWidthPx
                val cornerR = thumbWidthPx / 2f

                if (trackColor.alpha > 0f) {
                    drawRoundRect(
                        color = trackColor.copy(alpha = trackColor.alpha * alpha),
                        topLeft = Offset(thumbX, paddingTopPx),
                        size = Size(thumbWidthPx, availableTrackHeight),
                        cornerRadius = CornerRadius(cornerR, cornerR)
                    )
                }

                drawRoundRect(
                    color = resolvedThumbColor.copy(alpha = resolvedThumbColor.alpha * alpha),
                    topLeft = Offset(thumbX, thumbY),
                    size = Size(thumbWidthPx, thumbHeight),
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
            }
        }
    }
}
