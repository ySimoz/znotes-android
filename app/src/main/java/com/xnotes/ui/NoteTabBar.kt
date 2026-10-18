package com.xnotes.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xnotes.R
import com.xnotes.canvas.NoteTabManager
import com.xnotes.canvas.NoteTabSession
import com.xnotes.ui.icons.XnotesIcons

/**
 * Slim GoodNotes-style multi-note horizontal tab strip (36dp).
 * Provides rapid, sub-3ms switching across opened notes without destroying OpenGL surfaces,
 * with long-press drag-and-drop tab reordering.
 */
@Composable
fun NoteTabBar(
    tabManager: NoteTabManager,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (tabManager.tabs.isEmpty()) return

    val scrollState = rememberScrollState()

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var chipWidthPx by remember { mutableFloatStateOf(160f) }
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            tabManager.tabs.forEachIndexed { index, tab ->
                val isActive = index == tabManager.activeIndex
                val isDragging = draggingIndex == index
                key(tab.id) {
                    NoteTabChip(
                        tab = tab,
                        isActive = isActive,
                        isDragging = isDragging,
                        dragOffsetX = if (isDragging) dragOffsetX else 0f,
                        onSelect = { if (draggingIndex == null) onSelectTab(index) },
                        onClose = { onCloseTab(index) },
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingIndex = index
                            dragOffsetX = 0f
                        },
                        onDrag = { deltaX ->
                            dragOffsetX += deltaX
                            val currentIdx = draggingIndex ?: return@NoteTabChip
                            val threshold = chipWidthPx.coerceAtLeast(80f) * 0.50f
                            if (dragOffsetX > threshold && currentIdx < tabManager.tabs.lastIndex) {
                                onMoveTab(currentIdx, currentIdx + 1)
                                draggingIndex = currentIdx + 1
                                dragOffsetX -= threshold
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetX < -threshold && currentIdx > 0) {
                                onMoveTab(currentIdx, currentIdx - 1)
                                draggingIndex = currentIdx - 1
                                dragOffsetX += threshold
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            draggingIndex = null
                            dragOffsetX = 0f
                        },
                        onWidthMeasured = { chipWidthPx = it },
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Open note / home (+) button to pick an existing note or create one via standard dialog
            IconButton(
                onClick = onAddTab,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Icon(
                    imageVector = XnotesIcons.plus,
                    contentDescription = stringResource(R.string.home),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteTabChip(
    tab: NoteTabSession,
    isActive: Boolean,
    isDragging: Boolean,
    dragOffsetX: Float,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onWidthMeasured: (Float) -> Unit,
) {
    val bg = if (isActive) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "tabScale")
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "tabElevation")

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                translationX = dragOffsetX
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
            }
            .onSizeChanged { if (it.width > 0) onWidthMeasured(it.width.toFloat()) }
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else bg)
            .pointerInput(tab.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dirty indicator dot
        if (tab.isDirty) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Title
        Text(
            text = tab.title.ifEmpty { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 40.dp, max = 160.dp),
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Close button (x)
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = XnotesIcons.close,
                contentDescription = stringResource(R.string.close_pane),
                tint = if (isActive) contentColor else contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
