package com.xnotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Page
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The side panel's tabs. */
enum class SidePanelTab { PAGES, CONTENTS, BOOKMARKS, SEARCH }

/**
 * The side panel. The Pages tab shows page thumbnails with a per-page three-dot menu (add / copy /
 * cut / paste / delete / erase / share / save as) and supports multi-select (long-press, then tap
 * more pages) with a bottom action bar. Share/Save-as need the activity's SAF launchers, so they're
 * passed in as callbacks; everything else acts on [editor] directly.
 */
@Composable
fun SidePanel(
    editor: Editor,
    onSharePages: (indices: List<Int>, asPdf: Boolean) -> Unit = { _, _ -> },
    onSavePagesAsPdf: (indices: List<Int>) -> Unit = {},
    onSavePagesAsImages: (indices: List<Int>) -> Unit = {},
    onInsertPdf: (after: Int) -> Unit = {},
    onImportTemplate: () -> Unit = {},
    onTemplateFromImage: (com.xnotes.core.template.TemplateKind) -> Unit = {},
) {
    val palette = LocalPalette.current
    val tab = editor.sidePanelTab
    var pickingCover by remember { mutableStateOf(false) }
    if (pickingCover) {
        NewNoteScreen(
            editor = editor,
            folderName = "",
            initialName = "",
            error = null,
            busy = false,
            onImportTemplate = onImportTemplate,
            onTemplateFromImage = onTemplateFromImage,
            onCreate = { _, _ -> },
            onClose = { pickingCover = false },
            coverEdit = CoverEdit(editor.firstPageMm, editor.coverStyle) { style ->
                editor.setCover(style)
                pickingCover = false
            },
        )
    }
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(palette.panel.toComposeColor()),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SegIcon(XnotesIcons.thumbnails, stringResource(R.string.pages), tab == SidePanelTab.PAGES) { editor.sidePanelTab = SidePanelTab.PAGES }
            SegIcon(XnotesIcons.contents, stringResource(R.string.contents), tab == SidePanelTab.CONTENTS) { editor.sidePanelTab = SidePanelTab.CONTENTS }
            SegIcon(XnotesIcons.bookmark, stringResource(R.string.bookmarks), tab == SidePanelTab.BOOKMARKS) { editor.sidePanelTab = SidePanelTab.BOOKMARKS }
            SegIcon(XnotesIcons.search, stringResource(R.string.search), tab == SidePanelTab.SEARCH, shortcut = Keys.SEARCH) { editor.sidePanelTab = SidePanelTab.SEARCH }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                SidePanelTab.PAGES -> PagesTab(editor, onSharePages, onSavePagesAsPdf, onSavePagesAsImages, onInsertPdf) { pickingCover = true }
                SidePanelTab.CONTENTS -> ContentsTab(editor)
                SidePanelTab.BOOKMARKS -> BookmarksTab(editor)
                SidePanelTab.SEARCH -> SearchTab(editor)
            }
        }
        if (tab == SidePanelTab.PAGES && editor.inPageSelectionMode) {
            PageSelectionBar(editor, onSharePages, onSavePagesAsPdf, onSavePagesAsImages)
        }
    }
}

@Composable
private fun SegIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, selected: Boolean, shortcut: String? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Tooltip(desc, shortcut = shortcut) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = desc,
                tint = if (selected) palette.accent.toComposeColor() else palette.textDim.toComposeColor(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PagesTab(
    editor: Editor,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
    onInsertPdf: (Int) -> Unit,
    onPickCover: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Open the panel on the current page, not page 1 (re-runs each open: a hidden panel leaves composition).
    LaunchedEffect(Unit) { listState.scrollToItem(editor.pageIndex) }
    // Keyed by the page's stable id so insert/delete animate and keep each page's cached thumbnail;
    // contentVersion drives a fresh read of the order whenever the page list changes.
    val pages = remember(editor.contentVersion) { editor.pagesSnapshot() }
    val selecting = editor.inPageSelectionMode

    // One panel-wide "the list has stopped moving" flag. The custom scrollbar amplifies a slow
    // finger scrub into large page-space jumps, so the old per-row 150ms debounce still fired ~8
    // renders per window it swept past and stormed the CPU / the single-threaded PDF render lock.
    // Gating every row on this flag means a scrub renders nothing until it settles, then the
    // resting window renders once. drop(1) ignores snapshotFlow's initial emission so first-open
    // paints immediately. Keyed on listState only — not contentVersion — so edits don't reset it.
    val settled by produceState(initialValue = true, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .drop(1)
            .collectLatest {
                value = false
                delay(150)
                value = true
            }
    }

    val dragState = remember(listState, pages.size, editor) {
        PageRailDragState(listState, pages.size, editor::movePage)
    }
    RailDragAutoScroll(dragState)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { dragState.boxCoordinates = it }
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .onGloballyPositioned { dragState.listCoordinates = it },
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(pages, key = { _, page -> page.uid }) { index, page ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.animateItem(),
                ) {
                    if (dragState.isDragging && dragState.dropSlot == index) {
                        DropIndicatorLine()
                        Spacer(Modifier.height(6.dp))
                    }
                    PageThumb(
                        editor = editor,
                        index = index,
                        page = page,
                        selecting = selecting,
                        settled = settled,
                        isDragging = dragState.isDragging && dragState.draggingIndex == index,
                        canDrag = pages.size > 1,
                        rowModifier = Modifier,
                        onSharePages = onSharePages,
                        onSavePagesAsPdf = onSavePagesAsPdf,
                        onSavePagesAsImages = onSavePagesAsImages,
                        onInsertPdf = onInsertPdf,
                        onPickCover = onPickCover,
                        onTap = {
                            if (selecting) editor.togglePageSelection(index) else editor.goToPage(index)
                        },
                        onDragStart = { localOffset -> dragState.startDrag(index, localOffset) },
                        onDrag = dragState::moveDrag,
                        onDrop = dragState::drop,
                        onCancel = dragState::cancel,
                        onLongClick = { editor.togglePageSelection(index) },
                        onPositioned = { coords -> dragState.thumbCoordinates[index] = coords },
                    )
                    if (dragState.isDragging && index == pages.lastIndex && dragState.dropSlot == pages.size) {
                        Spacer(Modifier.height(6.dp))
                        DropIndicatorLine()
                    }
                }
            }
        }
        VerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))

        val ghostPage = dragState.draggingIndex?.let { pages.getOrNull(it) }
        if (dragState.isDragging && ghostPage != null) {
            PageDragGhost(
                editor = editor,
                page = ghostPage,
                index = dragState.draggingIndex ?: 0,
                modifier = Modifier.offset {
                    IntOffset(
                        dragState.ghostTopLeft.x.roundToInt(),
                        dragState.ghostTopLeft.y.roundToInt(),
                    )
                },
            )
        }
    }
}

/** State holder for drag-to-reorder gestures in the thumbnail rail. */
private class PageRailDragState(
    val listState: LazyListState,
    val totalPages: Int,
    val onMovePage: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
    var dropSlot by mutableStateOf<Int?>(null)
    var isDragging by mutableStateOf(false)
    var ghostTopLeft by mutableStateOf(Offset.Zero)
    var fingerPositionInList by mutableStateOf(Offset.Zero)

    var boxCoordinates: LayoutCoordinates? = null
    var listCoordinates: LayoutCoordinates? = null
    val thumbCoordinates = mutableMapOf<Int, LayoutCoordinates>()

    fun startDrag(index: Int, localOffset: Offset) {
        draggingIndex = index
        dropSlot = index
        isDragging = false

        val box = boxCoordinates
        val thumb = thumbCoordinates[index]
        if (box != null && thumb != null && box.isAttached && thumb.isAttached) {
            ghostTopLeft = box.localPositionOf(thumb, Offset.Zero)
            val list = listCoordinates
            fingerPositionInList = if (list != null && list.isAttached) {
                list.localPositionOf(thumb, localOffset)
            } else {
                localOffset
            }
        }
    }

    fun moveDrag(delta: Offset) {
        ghostTopLeft += delta
        fingerPositionInList += delta
        isDragging = true
        dropSlot = calculateDropSlot(fingerPositionInList.y, listState.layoutInfo.visibleItemsInfo, totalPages)
    }

    fun drop() {
        val from = draggingIndex
        val slot = dropSlot
        if (from != null && slot != null) {
            val to = calculateTargetIndex(from, slot, totalPages)
            if (to != from) {
                onMovePage(from, to)
            }
        }
        cancel()
    }

    fun cancel() {
        draggingIndex = null
        dropSlot = null
        isDragging = false
    }
}

/** Automatically scrolls the thumbnail LazyColumn when dragging near its top/bottom edges. */
@Composable
private fun RailDragAutoScroll(dragState: PageRailDragState) {
    LaunchedEffect(dragState.isDragging) {
        while (dragState.isDragging) {
            val layoutInfo = dragState.listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val edgeBand = 100f
                val y = dragState.fingerPositionInList.y
                val scrollDelta = when {
                    y < edgeBand -> -((edgeBand - y) / edgeBand).coerceIn(0f, 1f) * 16f
                    y > viewportHeight - edgeBand -> ((y - (viewportHeight - edgeBand)) / edgeBand).coerceIn(0f, 1f) * 16f
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    dragState.listState.scrollBy(scrollDelta)
                    dragState.dropSlot = calculateDropSlot(
                        dragState.fingerPositionInList.y,
                        dragState.listState.layoutInfo.visibleItemsInfo,
                        dragState.totalPages,
                    )
                }
            }
            delay(16L)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumb(
    editor: Editor,
    index: Int,
    page: Page,
    selecting: Boolean,
    settled: Boolean,
    isDragging: Boolean,
    canDrag: Boolean,
    rowModifier: Modifier,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
    onInsertPdf: (Int) -> Unit,
    onPickCover: () -> Unit,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onLongClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    val palette = LocalPalette.current
    val current = index == editor.pageIndex
    val selected = editor.isPageSelected(index)
    var menuOpen by remember { mutableStateOf(false) }

    val tap by rememberUpdatedState(onTap)
    val dStart by rememberUpdatedState(onDragStart)
    val dMove by rememberUpdatedState(onDrag)
    val dDrop by rememberUpdatedState(onDrop)
    val dCancel by rememberUpdatedState(onCancel)
    val lClick by rememberUpdatedState(onLongClick)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    val bitmap by key(editor.thumbnailVersion) {
        produceState<ImageBitmap?>(editor.cachedPageThumbnail(page), page, editor.contentVersion, editor.pdfThumbTick, settled) {
            val cached = editor.cachedPageThumbnail(page)
            if (cached != null) {
                value = cached
            } else if (settled) {
                value = editor.pageThumbnail(page, 300)
            }
        }
    }
    val aspect = editor.pageAspectRatio(page)
    val borderColor = if (selected || current) palette.accent else palette.border

    Column(rowModifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(150.dp)
                .height(150.dp * aspect)
                .alpha(if (isDragging) 0.35f else 1f)
                .border(if (selected || current) 2.dp else 1.dp, borderColor.toComposeColor())
                .onGloballyPositioned { onPositioned(it) }
                .pointerInput(page.uid) {
                    detectTapGestures(onTap = { tap() })
                }
                .pointerInput(page.uid, canDrag) {
                    if (!canDrag) return@pointerInput
                    var totalDistance = 0f
                    var moved = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { localOffset ->
                            totalDistance = 0f
                            moved = false
                            dStart(localOffset)
                        },
                        onDrag = { _, delta ->
                            totalDistance += delta.getDistance()
                            if (totalDistance > touchSlop) moved = true
                            dMove(delta)
                        },
                        onDragEnd = { if (moved) dDrop() else lClick() },
                        onDragCancel = { dCancel() },
                    )
                },
        ) {
            PageThumbBadges(
                bitmap = bitmap,
                index = index,
                selected = selected,
                selecting = selecting,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                editor = editor,
                isCover = page.cover,
                onSharePages = onSharePages,
                onSavePagesAsPdf = onSavePagesAsPdf,
                onSavePagesAsImages = onSavePagesAsImages,
                onInsertPdf = onInsertPdf,
                onPickCover = onPickCover,
            )
        }
        val captionColor = (if (current || selected) palette.accent else palette.textDim).toComposeColor()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("%02d".format(index + 1), color = captionColor, fontSize = 10.sp)
            if (page.cover) Text(stringResource(R.string.cover), color = captionColor, fontSize = 10.sp)
        }
    }
}

/** Overlay icons, selection indicator, and context menu inside the thumbnail card. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PageThumbBadges(
    bitmap: ImageBitmap?,
    index: Int,
    selected: Boolean,
    selecting: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    editor: Editor,
    isCover: Boolean,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
    onInsertPdf: (Int) -> Unit,
    onPickCover: () -> Unit,
) {
    val palette = LocalPalette.current
    bitmap?.let { Image(it, contentDescription = stringResource(R.string.page_n, index + 1), modifier = Modifier.fillMaxSize()) }

    if (selected) {
        Box(Modifier.matchParentSize().background(palette.selectionBackground.withAlpha(77).toComposeColor()))
        Box(
            Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp)
                .clip(CircleShape).background(palette.accent.toComposeColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(XnotesIcons.check, stringResource(R.string.selected), tint = palette.onAccent.toComposeColor(), modifier = Modifier.size(13.dp))
        }
    } else if (!selecting) {
        Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
            Tooltip(stringResource(R.string.page_options)) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(palette.panel.toComposeColor().copy(alpha = 0.7f))
                        .clickable { onMenuOpenChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(XnotesIcons.more, stringResource(R.string.page_options), tint = palette.text.toComposeColor(), modifier = Modifier.size(16.dp))
                }
            }
            PageContextMenu(editor, index, isCover, menuOpen, { onMenuOpenChange(false) }, onSharePages, onSavePagesAsPdf, onSavePagesAsImages, onInsertPdf, onPickCover)
        }
    }
}

/** Visual drop target line shown where a dragged page will land in the rail. */
@Composable
private fun DropIndicatorLine(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .width(150.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(palette.accent.toComposeColor())
    )
}

/** Floating thumbnail tracking the user's finger during a page reorder drag. */
@Composable
private fun PageDragGhost(
    editor: Editor,
    page: Page,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val aspect = editor.pageAspectRatio(page)
    val bitmap = editor.cachedPageThumbnail(page)
    Box(
        modifier
            .zIndex(10f)
            .width(150.dp)
            .height(150.dp * aspect)
            .shadow(10.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(palette.panel.toComposeColor())
            .border(2.dp, palette.accent.toComposeColor(), RoundedCornerShape(6.dp))
            .alpha(0.9f),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                "%02d".format(index + 1),
                color = palette.accent.toComposeColor(),
                fontSize = 16.sp,
            )
        }
    }
}

/**
 * Computes the target page index when moving a page from [fromIndex] to insertion [dropSlot].
 *
 * Slot 0 is before page 0, slot k is between pages k-1 and k, and slot N is after the last page.
 * Moving to adjacent slots (same position) returns [fromIndex] as a no-op.
 * Target is clamped within [0, totalItems - 1].
 */
internal fun calculateTargetIndex(fromIndex: Int, dropSlot: Int, totalItems: Int): Int {
    if (totalItems <= 1) return 0
    val rawTarget = if (dropSlot > fromIndex) dropSlot - 1 else dropSlot
    return rawTarget.coerceIn(0, totalItems - 1)
}

/**
 * Computes the insertion slot (0..totalItems) given the finger Y position relative to the
 * LazyColumn viewport, list of visible item bounds, and total number of items.
 */
internal fun calculateDropSlot(
    fingerY: Float,
    visibleItems: List<LazyListItemInfo>,
    totalItems: Int,
): Int {
    if (visibleItems.isEmpty() || totalItems <= 0) return 0
    val first = visibleItems.first()
    if (fingerY < first.offset + first.size / 2f) {
        return first.index.coerceIn(0, totalItems)
    }
    val last = visibleItems.last()
    if (fingerY >= last.offset + last.size / 2f) {
        return (last.index + 1).coerceIn(0, totalItems)
    }
    for (i in 0 until visibleItems.size - 1) {
        val curr = visibleItems[i]
        val next = visibleItems[i + 1]
        val currMid = curr.offset + curr.size / 2f
        val nextMid = next.offset + next.size / 2f
        if (fingerY in currMid..nextMid) {
            return next.index.coerceIn(0, totalItems)
        }
    }
    return totalItems
}

/** Page sub-pages of the three-dot menu (main / share-format / save-format). */
private const val MENU_MAIN = 0
private const val MENU_SHARE = 1
private const val MENU_SAVE = 2

@Composable
private fun PageContextMenu(
    editor: Editor,
    index: Int,
    isCover: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
    onInsertPdf: (Int) -> Unit,
    onPickCover: () -> Unit,
) {
    val palette = LocalPalette.current
    var sub by remember { mutableStateOf(MENU_MAIN) }
    LaunchedEffect(expanded) { if (!expanded) sub = MENU_MAIN } // always reopen on the main page
    val one = listOf(index)
    fun menuIcon(icon: ImageVector) = @Composable { Icon(icon, null, tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp)) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (sub) {
            MENU_SHARE -> {
                DropdownMenuItem(text = { Text(stringResource(R.string.back_share_as)) }, onClick = { sub = MENU_MAIN })
                DropdownMenuItem(text = { Text(stringResource(R.string.image_png)) }, leadingIcon = menuIcon(XnotesIcons.image), onClick = { onSharePages(one, false); onDismiss() })
                DropdownMenuItem(text = { Text("PDF") }, leadingIcon = menuIcon(XnotesIcons.exportDoc), onClick = { onSharePages(one, true); onDismiss() })
            }
            MENU_SAVE -> {
                DropdownMenuItem(text = { Text(stringResource(R.string.back_save_as)) }, onClick = { sub = MENU_MAIN })
                DropdownMenuItem(text = { Text(stringResource(R.string.image_png)) }, leadingIcon = menuIcon(XnotesIcons.image), onClick = { onSavePagesAsImages(one); onDismiss() })
                DropdownMenuItem(text = { Text("PDF") }, leadingIcon = menuIcon(XnotesIcons.exportDoc), onClick = { onSavePagesAsPdf(one); onDismiss() })
            }
            else -> {
                // The cover is only ever the first page, so only it offers these.
                if (isCover) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.change_cover)) }, leadingIcon = menuIcon(XnotesIcons.palette), onClick = { onDismiss(); onPickCover() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_cover)) }, leadingIcon = menuIcon(XnotesIcons.trash), onClick = { editor.removeCover(); onDismiss() })
                    HorizontalDivider(color = palette.border.toComposeColor())
                } else if (index == 0) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_cover_ellipsis)) }, leadingIcon = menuIcon(XnotesIcons.palette), onClick = { onDismiss(); onPickCover() })
                    HorizontalDivider(color = palette.border.toComposeColor())
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.add_page)) }, leadingIcon = menuIcon(XnotesIcons.plus), onClick = { editor.insertPageAfter(index); onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_to_contents)) }, leadingIcon = menuIcon(XnotesIcons.contents), onClick = { editor.addOutlineItem(index, "Page ${index + 1}"); editor.sidePanelTab = SidePanelTab.CONTENTS; onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.insert_pdf)) }, leadingIcon = menuIcon(XnotesIcons.importDoc), onClick = { onInsertPdf(index); onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, leadingIcon = menuIcon(XnotesIcons.copy), onClick = { editor.copyPages(one); onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.cut)) }, leadingIcon = menuIcon(XnotesIcons.cut), onClick = { editor.cutPages(one); onDismiss() })
                if (editor.canPastePages) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.paste)) }, leadingIcon = menuIcon(XnotesIcons.paste), onClick = { editor.pastePagesAfter(index); onDismiss() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = menuIcon(XnotesIcons.trash), onClick = { editor.deletePages(one); onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.erase_page)) }, leadingIcon = menuIcon(XnotesIcons.eraser), onClick = { editor.erasePage(index); onDismiss() })
                DropdownMenuItem(text = { Text(stringResource(R.string.share_ellipsis)) }, leadingIcon = menuIcon(XnotesIcons.share), onClick = { sub = MENU_SHARE })
                DropdownMenuItem(text = { Text(stringResource(R.string.save_as_ellipsis)) }, leadingIcon = menuIcon(XnotesIcons.download), onClick = { sub = MENU_SAVE })
            }
        }
    }
}

/** Bottom action bar shown while pages are multi-selected. */
@Composable
private fun PageSelectionBar(
    editor: Editor,
    onSharePages: (List<Int>, Boolean) -> Unit,
    onSavePagesAsPdf: (List<Int>) -> Unit,
    onSavePagesAsImages: (List<Int>) -> Unit,
) {
    val palette = LocalPalette.current
    // The bar sits at the panel's foot, so its tips open upward.
    CompositionLocalProvider(LocalTipSide provides TipSide.ABOVE) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(palette.surface.toComposeColor()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tooltip(stringResource(R.string.clear_selection)) {
                IconButton(onClick = { editor.clearPageSelection() }) {
                    Icon(XnotesIcons.close, stringResource(R.string.clear_selection), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp))
                }
            }
            Text("${editor.pageSelectionCount}", color = palette.text.toComposeColor(), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            BarAction(XnotesIcons.copy, stringResource(R.string.copy)) { editor.copyPages(editor.selectedPageIndices()) }
            BarAction(XnotesIcons.cut, stringResource(R.string.cut)) { editor.cutPages(editor.selectedPageIndices()) }
            BarAction(XnotesIcons.trash, stringResource(R.string.delete)) { editor.deletePages(editor.selectedPageIndices()) }
            FormatMenu(XnotesIcons.download, stringResource(R.string.save_as), { onSavePagesAsImages(editor.selectedPageIndices()) }, { onSavePagesAsPdf(editor.selectedPageIndices()) })
            FormatMenu(XnotesIcons.share, stringResource(R.string.share), { onSharePages(editor.selectedPageIndices(), false) }, { onSharePages(editor.selectedPageIndices(), true) })
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Tooltip(desc) {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(icon, desc, tint = LocalPalette.current.textDim.toComposeColor(), modifier = Modifier.size(19.dp))
        }
    }
}

/** An action that opens a small Image/PDF chooser (used by the bottom bar's Save as / Share). */
@Composable
private fun FormatMenu(icon: ImageVector, desc: String, onImage: () -> Unit, onPdf: () -> Unit) {
    val palette = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    fun menuIcon(i: ImageVector) = @Composable { Icon(i, null, tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp)) }
    Box {
        BarAction(icon, desc) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.image_png)) }, leadingIcon = menuIcon(XnotesIcons.image), onClick = { open = false; onImage() })
            DropdownMenuItem(text = { Text("PDF") }, leadingIcon = menuIcon(XnotesIcons.exportDoc), onClick = { open = false; onPdf() })
        }
    }
}

private class ScrollbarMetrics(
    val viewport: Float,
    val contentHeight: Float,
    val fraction: Float,
    val avgItem: Float,
    val totalItems: Int,
)

/**
 * A draggable scrollbar for [listState], shown only while the list overflows. Square thumb
 * (no rounded corners) that turns the accent colour while pressed/dragged. Item extents are
 * approximated from the visible items (the thumbnails are near-uniform height), which is
 * accurate enough to position the thumb and map a drag back onto a scroll.
 */
@Composable
private fun VerticalScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacingPx = with(density) { 10.dp.toPx() } // matches the list's Arrangement.spacedBy
    val minThumbPx = with(density) { 28.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }

    val metrics = remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            val viewport = info.viewportSize.height.toFloat()
            if (total == 0 || visible.isEmpty() || viewport <= 0f) return@derivedStateOf null
            val avgItem = visible.sumOf { it.size }.toFloat() / visible.size + spacingPx
            val contentHeight = avgItem * total
            if (contentHeight <= viewport) return@derivedStateOf null // everything fits: no scrollbar
            val scrolled = avgItem * listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset
            ScrollbarMetrics(viewport, contentHeight, (scrolled / (contentHeight - viewport)).coerceIn(0f, 1f), avgItem, total)
        }
    }

    val thumbColor = (if (dragging) palette.accent else palette.textDim).toComposeColor()
    Box(
        modifier
            .fillMaxHeight()
            .width(8.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (metrics.value == null) return@awaitEachGesture // nothing to scroll
                    down.consume()
                    dragging = true
                    var lastY = down.position.y
                    // Track the thumb position locally (seeded from the current scroll) and accumulate
                    // drag deltas, so rapid moves don't fight the derived state's recomposition lag.
                    var frac = metrics.value?.fraction ?: 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        val m = metrics.value
                        if (dy != 0f && m != null) {
                            val track = size.height.toFloat()
                            val thumb = (m.viewport / m.contentHeight * track).coerceAtLeast(minThumbPx)
                            val travel = track - thumb
                            if (travel > 0f && m.avgItem > 0f) {
                                // Map the thumb's new track position to an absolute page index + offset
                                // and jump straight there. scrollBy(dy * factor) instead made
                                // LazyColumn measure/compose *every* page it scrolled past on the UI
                                // thread — pegging it for a 497-page PDF. scrollToItem jumps directly
                                // to the destination and only composes the landing window.
                                frac = (frac + dy / travel).coerceIn(0f, 1f)
                                val targetPx = frac * (m.contentHeight - m.viewport)
                                val index = (targetPx / m.avgItem).toInt().coerceIn(0, m.totalItems - 1)
                                val offset = (targetPx - index * m.avgItem).toInt().coerceAtLeast(0)
                                scope.launch { listState.scrollToItem(index, offset) }
                            }
                            change.consume()
                        }
                    }
                    dragging = false
                }
            }
            .drawBehind {
                val m = metrics.value ?: return@drawBehind
                val thumb = (m.viewport / m.contentHeight * size.height).coerceAtLeast(minThumbPx)
                drawRect(
                    color = thumbColor,
                    topLeft = Offset(0f, (size.height - thumb) * m.fraction),
                    size = Size(size.width, thumb),
                )
            },
    )
}

private fun visibleOutlineItems(outline: List<com.xnotes.core.model.OutlineItem>): List<Pair<Int, com.xnotes.core.model.OutlineItem>> {
    val visible = mutableListOf<Pair<Int, com.xnotes.core.model.OutlineItem>>()
    var hiddenBelowLevel: Int? = null
    for ((index, item) in outline.withIndex()) {
        if (hiddenBelowLevel != null) {
            if (item.level > hiddenBelowLevel) {
                continue
            } else {
                hiddenBelowLevel = null
            }
        }
        visible.add(index to item)
        if (!item.expanded) {
            hiddenBelowLevel = item.level
        }
    }
    return visible
}

@Composable
private fun ContentsTab(editor: Editor) {
    val palette = LocalPalette.current
    val outlineVersion = editor.outlineVersion
    val outline = remember(outlineVersion) { editor.outline }
    val tocVersion = editor.tocVersion
    val pdfToc = remember(tocVersion) { editor.tableOfContents }

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<Pair<Int, com.xnotes.core.model.OutlineItem>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.contents),
                color = palette.textDim.toComposeColor(),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            Tooltip(stringResource(R.string.add_to_contents)) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        XnotesIcons.plus,
                        stringResource(R.string.add_to_contents),
                        tint = palette.textDim.toComposeColor(),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (outline.isEmpty() && pdfToc.isEmpty()) {
            EmptyHint(stringResource(R.string.no_contents))
        } else if (outline.isNotEmpty()) {
            val visibleItems = remember(outlineVersion) { visibleOutlineItems(outline) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(visibleItems.size, key = { visibleItems[it].second.id }) { i ->
                    val (realIndex, item) = visibleItems[i]
                    val hasChildren = realIndex + 1 < outline.size && outline[realIndex + 1].level > item.level
                    val indentDp = (item.level.coerceAtMost(8) * 12).dp
                    var menuOpen by remember { mutableStateOf(false) }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp + indentDp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasChildren) {
                            IconButton(
                                onClick = { editor.toggleOutlineItemExpanded(realIndex) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    if (item.expanded) XnotesIcons.chevronDown else XnotesIcons.chevronRight,
                                    contentDescription = null,
                                    tint = palette.textDim.toComposeColor(),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }

                        Text(
                            item.title,
                            color = palette.text.toComposeColor(),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editor.goToPage(item.page) }
                                .padding(vertical = 4.dp),
                        )

                        Text(
                            "p.${item.page + 1}",
                            color = palette.textDim.toComposeColor(),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { editor.goToPage(item.page) }
                                .padding(horizontal = 4.dp),
                        )

                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    XnotesIcons.more,
                                    contentDescription = stringResource(R.string.page_options),
                                    tint = palette.textDim.toComposeColor(),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val canIndent = realIndex > 0 && item.level <= outline[realIndex - 1].level
                                val canOutdent = item.level > 0
                                val canMoveUp = realIndex > 0
                                val canMoveDown = realIndex < outline.lastIndex
                                fun menuIcon(icon: ImageVector) = @Composable {
                                    Icon(icon, null, tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp))
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.indent)) },
                                    leadingIcon = menuIcon(XnotesIcons.chevronRight),
                                    enabled = canIndent,
                                    onClick = { editor.indentOutlineItem(realIndex); menuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.outdent)) },
                                    leadingIcon = menuIcon(XnotesIcons.chevronLeft),
                                    enabled = canOutdent,
                                    onClick = { editor.outdentOutlineItem(realIndex); menuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_up)) },
                                    leadingIcon = menuIcon(XnotesIcons.arrowUp),
                                    enabled = canMoveUp,
                                    onClick = { editor.moveOutlineItem(realIndex, realIndex - 1); menuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_down)) },
                                    leadingIcon = menuIcon(XnotesIcons.arrowDown),
                                    enabled = canMoveDown,
                                    onClick = { editor.moveOutlineItem(realIndex, realIndex + 1); menuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename)) },
                                    leadingIcon = menuIcon(XnotesIcons.edit),
                                    onClick = { itemToRename = realIndex to item; menuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remove)) },
                                    leadingIcon = menuIcon(XnotesIcons.trash),
                                    onClick = { editor.removeOutlineItem(realIndex); menuOpen = false },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Fallback for native PDF outline before conversion
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(pdfToc) { _, e ->
                    val hasTarget = e.destPage >= 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (hasTarget) Modifier.clickable { editor.goToTocEntry(e) } else Modifier)
                            .padding(
                                start = (10 + e.level.coerceAtMost(6) * 12).dp,
                                end = 10.dp,
                                top = 6.dp,
                                bottom = 6.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            e.title,
                            color = (if (hasTarget) palette.text else palette.textDim).toComposeColor(),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val defaultLabel = stringResource(R.string.page_n, editor.pageIndex + 1)
        var label by remember { mutableStateOf(defaultLabel) }
        var pageText by remember { mutableStateOf((editor.pageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_to_contents)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pageText,
                        onValueChange = { pageText = it },
                        label = { Text("Page") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetPage = (pageText.toIntOrNull()?.minus(1) ?: editor.pageIndex)
                        .coerceIn(0, editor.pageCount - 1)
                    editor.addOutlineItem(targetPage, label.ifBlank { defaultLabel })
                    showAddDialog = false
                }) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    itemToRename?.let { (idx, item) ->
        var newTitle by remember { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) {
                        editor.renameOutlineItem(idx, newTitle.trim())
                    }
                    itemToRename = null
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BookmarksTab(editor: Editor) {
    val palette = LocalPalette.current
    var showAdd by remember { mutableStateOf(false) }
    // Read the version so the list recomposes on add/remove.
    val version = editor.bookmarkVersion
    val bookmarks = remember(version) { editor.bookmarks }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            Tooltip(stringResource(R.string.add_bookmark)) {
                IconButton(onClick = { showAdd = true }) {
                    Icon(XnotesIcons.plus, stringResource(R.string.add_bookmark), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (bookmarks.isEmpty()) {
            EmptyHint(stringResource(R.string.no_bookmarks))
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(bookmarks) { i, bm ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editor.goToPage(bm.page) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.bookmark_entry, bm.label, bm.page + 1),
                            color = palette.text.toComposeColor(),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Tooltip(stringResource(R.string.remove)) {
                            IconButton(onClick = { editor.removeBookmark(i) }) {
                                Icon(XnotesIcons.trash, stringResource(R.string.remove), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        val defaultLabel = stringResource(R.string.page_n, editor.pageIndex + 1)
        var label by remember { mutableStateOf(defaultLabel) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.add_bookmark)) },
            text = { OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { editor.addBookmark(label.ifBlank { defaultLabel }); showAdd = false }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = LocalPalette.current.textDim.toComposeColor(), fontSize = 12.sp)
    }
}
