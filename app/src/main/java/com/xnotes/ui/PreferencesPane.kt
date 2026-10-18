package com.xnotes.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.Rgba
import com.xnotes.settings.ExplorerLayout
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.core.util.NameTemplate
import com.xnotes.settings.Preferences
import com.xnotes.settings.MaterialStyle
import com.xnotes.settings.CornerStyle
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import com.xnotes.settings.MaterialColourMode
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.delay

internal val pageColorPresets = listOf(
    Rgba(22, 22, 22), Rgba(13, 13, 13), Rgba(255, 255, 255), Rgba(247, 243, 233), Rgba(232, 232, 232),
)
/** One-tap filename templates offered beside the free-text field. */
internal val NAME_TEMPLATE_PRESETS = listOf(
    NameTemplate.DEFAULT,
    "note_YYYY-MM-DD",
    "note_YYYY-MM-DD_HH-mm",
)
internal val penButtonOptions = listOf("eraser" to R.string.tool_eraser, "pan" to R.string.tool_pan, "select" to R.string.tool_select, "none" to R.string.none)
internal val tapGestureOptions = listOf(
    "none" to R.string.none,
    "undo" to R.string.undo,
    "redo" to R.string.redo,
    "toggle_pan" to R.string.toggle_pan,
    "toggle_eraser" to R.string.toggle_eraser,
    "toggle_previous" to R.string.toggle_previous,
)

/**
 * Preferences as a backstage pane (spec 10 §8). Edits apply live — each change is
 * pushed straight to the [Editor] (and persisted), so theme/page tweaks are seen
 * immediately, including in the surrounding backstage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesPane(
    editor: Editor,
    compact: Boolean,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    onBackToHome: () -> Unit,
    onImportCodeTheme: () -> Unit = {},
    onImportFont: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val focusManager = LocalFocusManager.current
    var prefs by remember { mutableStateOf(editor.preferences) }
    fun update(p: Preferences) {
        prefs = p
        editor.applyPreferences(p)
    }
    // Home, explorer and export settings leave the open note alone, so they skip its canvas refresh.
    fun updateHome(p: Preferences) {
        prefs = p
        editor.applyHomePreferences(p)
    }
    var namingColor by remember { mutableStateOf<Rgba?>(null) }
    namingColor?.let { c -> ColorNameDialog(editor, c) { namingColor = null } }
    // Follow out-of-pane preference changes too (the .scm import round-trips a picker).
    LaunchedEffect(editor.prefsVersion) { prefs = editor.preferences }

    val scrollState = rememberScrollState()
    // Which bar the toolbar section is arranging. The two are separate layouts, so the drag state
    // below always belongs to whichever is on screen.
    var canvasTab by remember { mutableStateOf(false) }
    val layout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    // Toolbar drag state, hoisted here so the floating "ghost" chip can be drawn at the pane
    // root (above and outside the scroll, so it is never clipped). Bounds are plain maps read
    // only at drag time; observable state is just the dragged item, finger, and drop slot.
    var dragItem by remember { mutableStateOf<ToolbarItem?>(null) }
    var dragFinger by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val chipCoords = remember { mutableMapOf<ToolbarItem, androidx.compose.ui.layout.LayoutCoordinates>() }
    val sectionCoords = remember { mutableMapOf<Int, androidx.compose.ui.layout.LayoutCoordinates>() }
    val sectionGrabCoords = remember { mutableMapOf<Int, androidx.compose.ui.layout.LayoutCoordinates>() }
    // Section-drag state (the whole card, by its grip handle); shares the finger/grab with the
    // chip drag since only one gesture runs at a time. The drop slot is an insertion index.
    var dragSection by remember { mutableStateOf<Int?>(null) }
    var sectionDropTarget by remember { mutableStateOf<Int?>(null) }
    var paneTopLeft by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    fun editedLayout(): ToolbarLayout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    fun applyEdited(next: ToolbarLayout) {
        if (canvasTab) editor.applyCanvasToolbarLayout(next) else editor.applyToolbarLayout(next)
    }
    fun retarget() {
        dropTarget = toolbarDropTarget(editedLayout(), dragFinger, chipCoords, sectionCoords)
    }
    fun retargetSection() {
        sectionDropTarget = toolbarSectionDropTarget(editedLayout(), dragFinger, sectionCoords)
    }
    fun showTab(canvas: Boolean) {
        if (canvasTab == canvas) return
        canvasTab = canvas
        // The bounds maps are keyed by item and the two bars share most items, so stale entries
        // from the other tab would aim a drop at a chip that is no longer there.
        chipCoords.clear()
        sectionCoords.clear()
        sectionGrabCoords.clear()
        dragItem = null
        dragSection = null
        dropTarget = null
        sectionDropTarget = null
    }

    // A tap on empty space dismisses the template field's focus; children consume their own taps.
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                if (b.left >= 0) paneTopLeft = b.topLeft
            },
    ) {
    Column(Modifier.fillMaxSize()) {
        // Leading button inline with the title, at a constant row height so toggling the sidebar never
        // shifts the settings below: a back arrow to Home on compact, else a hamburger when hidden.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                Tooltip(stringResource(R.string.back_to_home)) {
                    IconButton(onClick = onBackToHome) {
                        Icon(XnotesIcons.prev, stringResource(R.string.back_to_home), tint = palette.text.toComposeColor(), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
            } else if (!sidebarOpen) {
                Tooltip(stringResource(R.string.show_sidebar)) {
                    IconButton(onClick = onShowSidebar) {
                        Icon(XnotesIcons.menu, stringResource(R.string.show_sidebar), tint = palette.text.toComposeColor(), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(stringResource(R.string.preferences), color = palette.text.toComposeColor(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { update(Preferences()) }) { Text(stringResource(R.string.reset_to_defaults), fontSize = 13.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(scrollState)
                .onGloballyPositioned {
                    val b = it.boundsInRoot()
                    if (b.left >= 0 && (viewport == Rect.Zero || dragItem != null || dragSection != null)) {
                        viewport = b
                    }
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GeneralPreferencesSection(
                prefs = prefs,
                fullscreen = editor.fullscreen,
                onToggleFullscreen = { editor.setFullscreenPref(it) },
                onUpdate = ::update,
                onUpdateHome = ::updateHome,
            )
            InputPreferencesSection(prefs = prefs, onUpdate = ::update)
            NewNotesPreferencesSection(
                prefs = prefs,
                stemPreview = "${editor.newNoteStem(emptySet())}.xnote",
                onUpdate = ::update,
            )
            PagePreferencesSection(prefs = prefs, onUpdate = ::update)
            ExplorerPreferencesSection(
                prefs = prefs,
                editor = editor,
                onUpdateHome = ::updateHome,
                onNameColor = { namingColor = it },
            )
            PdfExportPreferencesSection(prefs = prefs, onUpdateHome = ::updateHome)
            PerformancePreferencesSection(prefs = prefs, onUpdate = ::update)
            if (editor.treeSitterAvailable) {
                CodeHighlightingPreferencesSection(
                    prefs = prefs,
                    editor = editor,
                    onUpdate = ::update,
                    onImportCodeTheme = onImportCodeTheme,
                )
            }
            FontsPreferencesSection(
                customFonts = editor.customFonts,
                onImportFont = onImportFont,
                onRemoveFont = { editor.removeCustomFont(it) },
            )
            ToolbarControlsSection(
                prefs = prefs,
                editor = editor,
                canvasTab = canvasTab,
                onSelectCanvasTab = ::showTab,
                onUpdateHome = ::updateHome,
                onApplyEdited = ::applyEdited,
            )
            ToolbarCustomizerBody(
                layout = layout,
                dragItem = dragItem,
                dropTarget = dropTarget,
                dragSection = dragSection,
                sectionDropTarget = sectionDropTarget,
                chipCoords = chipCoords,
                sectionCoords = sectionCoords,
                sectionGrabCoords = sectionGrabCoords,
                onToggle = { s, i -> applyEdited(editedLayout().toggleVisible(s, i)) },
                onDelete = { s -> applyEdited(editedLayout().removeSection(s)) },
                onAdd = { applyEdited(editedLayout().addSection()) },
                onDragStart = { item, local ->
                    dragItem = item
                    dragGrab = local
                    val b = chipCoords[item]?.takeIf { it.isAttached }?.boundsInRoot()
                    dragFinger = (b?.topLeft ?: Offset.Zero) + local
                    retarget()
                },
                onDrag = { delta ->
                    dragFinger += delta
                    retarget()
                },
                onDrop = {
                    val target = dropTarget
                    val moving = dragItem
                    if (target != null && moving != null) {
                        val cur = editedLayout()
                        val fSec = cur.sections.indexOfFirst { s -> s.entries.any { it.item == moving } }
                        val fIdx = if (fSec >= 0) cur.sections[fSec].entries.indexOfFirst { it.item == moving } else -1
                        if (fSec >= 0 && fIdx >= 0) {
                            applyEdited(cur.moveItem(fSec, fIdx, target.first, target.second))
                        }
                    }
                    dragItem = null
                    dragSection = null
                    dropTarget = null
                    sectionDropTarget = null
                },
                onCancel = {
                    dragItem = null
                    dragSection = null
                    dropTarget = null
                    sectionDropTarget = null
                },
                onSectionDragStart = { sec, local ->
                    dragSection = sec
                    val grab = sectionGrabCoords[sec]?.takeIf { it.isAttached }?.boundsInRoot()?.topLeft
                        ?: sectionCoords[sec]?.takeIf { it.isAttached }?.boundsInRoot()?.topLeft
                        ?: Offset.Zero
                    val card = sectionCoords[sec]?.takeIf { it.isAttached }?.boundsInRoot()?.topLeft ?: Offset.Zero
                    dragFinger = grab + local
                    dragGrab = dragFinger - card
                    retargetSection()
                },
                onSectionDrag = { delta ->
                    dragFinger += delta
                    retargetSection()
                },
                onSectionDrop = {
                    val from = dragSection
                    val insertAt = sectionDropTarget
                    if (from != null && insertAt != null) {
                        val cur = editedLayout()
                        val to = (if (insertAt > from) insertAt - 1 else insertAt).coerceIn(0, cur.sections.lastIndex)
                        if (to != from) applyEdited(cur.moveSection(from, to))
                    }
                    dragItem = null
                    dragSection = null
                    dropTarget = null
                    sectionDropTarget = null
                },
                onSectionCancel = {
                    dragItem = null
                    dragSection = null
                    dropTarget = null
                    sectionDropTarget = null
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
        // The dragged chip/section's floating copy: a pane-root sibling so the scroll never clips it.
        val ghostChip = dragItem
        val ghostSecIdx = dragSection
        if (ghostChip != null) {
            val gx = (dragFinger.x - dragGrab.x - paneTopLeft.x).toInt()
            val gy = (dragFinger.y - dragGrab.y - paneTopLeft.y).toInt()
            Box(Modifier.offset { IntOffset(gx, gy) }) { ToolbarDragGhost(ghostChip) }
        } else if (ghostSecIdx != null) {
            val gx = (dragFinger.x - dragGrab.x - paneTopLeft.x).toInt()
            val gy = (dragFinger.y - dragGrab.y - paneTopLeft.y).toInt()
            val sectionG = (if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout)
                .sections.getOrNull(ghostSecIdx)
            val widthG = sectionCoords[ghostSecIdx]?.takeIf { it.isAttached }?.boundsInRoot()?.width
            if (sectionG != null && widthG != null) {
                Box(Modifier.offset { IntOffset(gx, gy) }) { SectionCardGhost(sectionG, widthG) }
            }
        }
    }

    // While dragging near a vertical edge of the scroll viewport, ease the list along so items
    // off-screen can be reached without lifting the finger.
    LaunchedEffect(dragItem != null || dragSection != null) {
        while (dragItem != null || dragSection != null) {
            val band = 90f
            val y = dragFinger.y
            val delta = when {
                y < viewport.top + band -> -((viewport.top + band - y) / band) * 14f
                y > viewport.bottom - band -> ((y - (viewport.bottom - band)) / band) * 14f
                else -> 0f
            }
            if (delta != 0f) {
                scrollState.scrollBy(delta)
                if (dragItem != null) retarget() else retargetSection()
            }
            delay(16L)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, color = LocalPalette.current.text.toComposeColor(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = LocalPalette.current.accent.toComposeColor(), fontSize = 13.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomMaterialControls(prefs: Preferences, update: (Preferences) -> Unit) {
    val palette = LocalPalette.current
    val styles = listOf(
        MaterialStyle.TONAL_SPOT to R.string.material_style_soft,
        MaterialStyle.VIBRANT to R.string.material_style_vivid,
        MaterialStyle.FIDELITY to R.string.material_style_original,
        MaterialStyle.EXPRESSIVE to R.string.material_style_playful,
        MaterialStyle.FRUIT_SALAD to R.string.material_style_fresh,
        MaterialStyle.RAINBOW to R.string.material_style_clean,
        MaterialStyle.NEUTRAL to R.string.material_style_muted,
        MaterialStyle.MONOCHROME to R.string.material_style_grayscale,
    )
    FieldLabel(stringResource(R.string.material_style))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { (style, label) ->
            Chip(stringResource(label), prefs.materialStyle == style) {
                update(prefs.copy(materialStyle = style))
            }
        }
    }
    val description = when (prefs.materialStyle) {
        MaterialStyle.TONAL_SPOT -> R.string.material_style_soft_description
        MaterialStyle.VIBRANT -> R.string.material_style_vivid_description
        MaterialStyle.FIDELITY -> R.string.material_style_original_description
        MaterialStyle.EXPRESSIVE -> R.string.material_style_playful_description
        MaterialStyle.FRUIT_SALAD -> R.string.material_style_fresh_description
        MaterialStyle.RAINBOW -> R.string.material_style_clean_description
        MaterialStyle.NEUTRAL -> R.string.material_style_muted_description
        MaterialStyle.MONOCHROME -> R.string.material_style_grayscale_description
    }
    Text(stringResource(description), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    val percent = Math.round(prefs.materialContrast * 100).toInt()
    FieldLabel(stringResource(R.string.material_contrast, if (percent > 0) "+$percent%" else "$percent%"))
    Slider(
        value = prefs.materialContrast.toFloat(),
        onValueChange = { update(prefs.copy(materialContrast = Math.round(it * 10) / 10.0)) },
        valueRange = -1f..1f,
        steps = 19,
        modifier = Modifier.width(280.dp),
    )
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) palette.selectionBackground.toComposeColor() else palette.surface.toComposeColor())
            .border(1.dp, if (selected) palette.accent.toComposeColor() else palette.border.toComposeColor(), MaterialTheme.shapes.small)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) palette.selectionForeground.toComposeColor() else palette.text.toComposeColor(),
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun ColorDot(color: Color, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val label = Rgba.toHex(Rgba.fromArgb(color.toArgb()))
    Box(
        Modifier
            .size(30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .then(if (selected) Modifier.border(2.dp, palette.accent.toComposeColor(), CircleShape) else Modifier)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, palette.border.toComposeColor(), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** A spectrum wheel signals that this dot opens the full picker. */
private val spectrumBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
    ),
)

/**
 * A spectrum dot that opens [grid], a popup of colour swatches — shared by the accent and
 * page-colour rows. Until a colour outside the row's presets is chosen the dot shows the
 * spectrum wheel; once one is, it fills with that colour and reads as selected.
 */
@Composable
internal fun ColorPickerDot(
    current: Rgba?,
    custom: Boolean,
    onPick: (Rgba) -> Unit,
    dismissOnPick: Boolean = true,
    enabled: Boolean = true,
    grid: @Composable (onDismiss: () -> Unit, onPick: (Rgba) -> Unit) -> Unit,
) {
    val palette = LocalPalette.current
    val label = stringResource(R.string.material_custom_colour)
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(30.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .semantics { contentDescription = label }
                .then(if (custom) Modifier.border(2.dp, palette.accent.toComposeColor(), CircleShape) else Modifier)
                .padding(4.dp)
                .clip(CircleShape)
                .then(if (custom && current != null) Modifier.background(current.toComposeColor()) else Modifier.background(spectrumBrush))
                .border(1.dp, palette.border.toComposeColor(), CircleShape)
                .clickable(enabled = enabled) { open = true },
        )
        // A live picker (e.g. the page/ink popup) edits across several taps, so it stays open until a
        // tap outside; a one-shot grid (the accent swatches) closes the moment a colour is chosen.
        if (open) grid({ open = false }, { onPick(it); if (dismissOnPick) open = false })
    }
}

/** One tappable colour cell in a picker grid. */
/**
 * The colour-code picker shown inside a note/folder's overflow menu: a None row to clear the colour,
 * then the picker's full matrix (pale tints through near-black plus the greyscale row). [onPick] is
 * called with null for None. Rendered directly inside the menu's own dropdown column.
 */
@Composable
internal fun ColorCodeMenuContent(onPick: (Rgba?) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.clip(MaterialTheme.shapes.extraSmall).clickable { onPick(null) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(2.dp))
                    .border(1.dp, palette.border.toComposeColor(), RoundedCornerShape(2.dp)),
            )
            Text(stringResource(R.string.none), color = palette.text.toComposeColor(), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
        FullSwatchGrid { onPick(it) }
    }
}

/**
 * Page/pattern colour picker: the shared [ColorPickerPopup] (Swatches + Spectrum tabs, full
 * 13×13 range from pale tint to near-black plus a greyscale row, and HEX/RGB fields), so paper-like
 * and muted page backgrounds are reachable, not just the saturated ones. It keeps no recents list.
 */
@Composable
internal fun PageColorGridPopup(initial: Rgba?, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial = initial, recents = emptyList(), onDismiss = onDismiss, onPick = onPick)
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(2.dp))
        Text(label, color = LocalPalette.current.text.toComposeColor(), fontSize = 14.sp)
    }
}

/**
 * One side of a custom page, in millimetres. The field keeps whatever is typed so a number can be
 * cleared and retyped; only a value that parses inside the settable range reaches the preference.
 */
@Composable
internal fun MillimetreField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(formatMm(value)) }
    // Adopt an outside change (Reset to defaults) without ever rewriting what is being typed.
    LaunchedEffect(value) { if (text.trim().toDoubleOrNull() != value) text = formatMm(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.trim().toDoubleOrNull()?.let {
                if (it in Preferences.CUSTOM_PAGE_MIN_MM..Preferences.CUSTOM_PAGE_MAX_MM) onChange(it)
            }
        },
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.width(136.dp),
    )
}

/** Drop a whole number's ".0" so the field reads "210", not "210.0". */
private fun formatMm(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toInt().toString() else v.toString()

@Composable
internal fun SizeDropdown(size: PageSize, onSelect: (PageSize) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    Box {
        Box(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(pageSizeLabel(size), color = palette.text.toComposeColor(), fontSize = 14.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PageSize.entries.forEach { s ->
                DropdownMenuItem(text = { Text(pageSizeLabel(s)) }, onClick = { onSelect(s); expanded = false })
            }
        }
    }
}

@Composable
internal fun OptionDropdown(options: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    val label = options.firstOrNull { it.first == selectedId }?.second ?: options.first().second
    Box {
        Box(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, color = palette.text.toComposeColor(), fontSize = 14.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, lbl) ->
                DropdownMenuItem(text = { Text(lbl) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
