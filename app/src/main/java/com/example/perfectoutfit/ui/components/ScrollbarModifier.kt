package com.example.perfectoutfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

private val scrollbarBaseColor = Color(0x88888888)

/** Draws a vertical scrollbar overlay for a Column with [verticalScroll].
 *  Visible only while scrolling; fades out 300 ms after scrolling stops. */
fun Modifier.verticalScrollbar(scrollState: ScrollState): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (scrollState.isScrollInProgress) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (scrollState.isScrollInProgress) 0 else 400,
            delayMillis   = if (scrollState.isScrollInProgress) 0 else 300
        ),
        label = "scrollbar_alpha"
    )
    drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val maxScroll = scrollState.maxValue.toFloat()
            if (maxScroll > 0f) {
                val viewH = size.height
                val barH  = (viewH * viewH / (viewH + maxScroll)).coerceAtLeast(40f)
                val barTop = (scrollState.value.toFloat() / maxScroll) * (viewH - barH)
                drawRoundRect(
                    color      = scrollbarBaseColor.copy(alpha = alpha),
                    topLeft    = Offset(size.width - 6f, barTop),
                    size       = Size(4f, barH),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }
    }
}

/** Draws a vertical scrollbar overlay for a LazyColumn with [LazyListState].
 *  Visible only while scrolling; fades out 300 ms after scrolling stops. */
fun Modifier.verticalScrollbar(listState: LazyListState): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (listState.isScrollInProgress) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (listState.isScrollInProgress) 0 else 400,
            delayMillis   = if (listState.isScrollInProgress) 0 else 300
        ),
        label = "scrollbar_alpha"
    )
    drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val info         = listState.layoutInfo
            val totalCount   = info.totalItemsCount
            val visibleItems = info.visibleItemsInfo
            if (totalCount > 0 && visibleItems.size < totalCount) {
                val viewH      = size.height
                val barH       = (viewH * visibleItems.size / totalCount).coerceAtLeast(40f)
                val scrollRange = (totalCount - visibleItems.size).toFloat().coerceAtLeast(1f)
                val barTop     = ((listState.firstVisibleItemIndex / scrollRange) * (viewH - barH))
                    .coerceIn(0f, viewH - barH)
                drawRoundRect(
                    color      = scrollbarBaseColor.copy(alpha = alpha),
                    topLeft    = Offset(size.width - 6f, barTop),
                    size       = Size(4f, barH),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }
    }
}
