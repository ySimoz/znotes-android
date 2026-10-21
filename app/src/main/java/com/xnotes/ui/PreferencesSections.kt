package com.xnotes.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PagePattern
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.model.Rgba
import com.xnotes.core.pal.FontFace
import com.xnotes.core.tools.InkPalette
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.core.util.NameTemplate
import com.xnotes.platform.FontCatalog
import com.xnotes.settings.CornerStyle
import com.xnotes.settings.ExplorerLayout
import com.xnotes.settings.MaterialColourMode
import com.xnotes.settings.Preferences
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * General section: app language, color themes, material tone, corner style, and fullscreen toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneralPreferencesSection(
    prefs: Preferences,
    fullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onUpdate: (Preferences) -> Unit,
    onUpdateHome: (Preferences) -> Unit,
) {
    val palette = LocalPalette.current
    SectionTitle(stringResource(R.string.pref_general))
    FieldLabel(stringResource(R.string.pref_language))
    val languageOptions = listOf(
        "system" to stringResource(R.string.language_system),
        "en" to stringResource(R.string.language_en),
        "it" to stringResource(R.string.language_it),
        "zh-Hans" to stringResource(R.string.language_zh_hans),
    )
    OptionDropdown(languageOptions, prefs.appLanguage) {
        onUpdate(prefs.copy(appLanguage = it))
    }
    FieldLabel(stringResource(R.string.pref_ui_theme))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.theme_system), prefs.uiAppearance == "system") { onUpdate(prefs.copy(uiAppearance = "system")) }
        Chip(stringResource(R.string.theme_dark), prefs.uiAppearance == "dark") { onUpdate(prefs.copy(uiAppearance = "dark")) }
        Chip(stringResource(R.string.theme_light), prefs.uiAppearance == "light") { onUpdate(prefs.copy(uiAppearance = "light")) }
        Chip(stringResource(R.string.theme_oled), prefs.uiAppearance == "oled") { onUpdate(prefs.copy(uiAppearance = "oled")) }
    }
    val systemColours = prefs.materialMode == MaterialColourMode.SYSTEM
    FieldLabel(stringResource(R.string.material_tone))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.material_system_colours), systemColours) {
            onUpdate(prefs.copy(materialMode = MaterialColourMode.SYSTEM))
        }
        Chip(stringResource(R.string.material_single_tone), prefs.materialMode == MaterialColourMode.SINGLE) {
            onUpdate(prefs.copy(materialMode = MaterialColourMode.SINGLE))
        }
        Chip(stringResource(R.string.material_dual_tone), prefs.materialMode == MaterialColourMode.DUAL) {
            onUpdate(prefs.copy(materialMode = MaterialColourMode.DUAL))
        }
    }
    if (!systemColours) {
        key(prefs.materialMode) { MaterialColourPicker(prefs, onUpdate) }
        CustomMaterialControls(prefs, onUpdate)
    } else if (Build.VERSION.SDK_INT < 31) {
        Text(stringResource(R.string.material_system_fallback), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    }
    FieldLabel(stringResource(R.string.pref_corners))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((style, label) in listOf(
            CornerStyle.SHARP to R.string.corners_sharp,
            CornerStyle.ROUNDED to R.string.corners_rounded,
            CornerStyle.SOFT to R.string.corners_soft,
        )) {
            Chip(stringResource(label), prefs.cornerStyle == style) { onUpdateHome(prefs.copy(cornerStyle = style)) }
        }
    }
    CheckRow(stringResource(R.string.pref_start_fullscreen), fullscreen, onToggleFullscreen)
}

/**
 * Input section: finger drawing, zoom lock pan mode, shape detection, and stylus gesture triggers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InputPreferencesSection(
    prefs: Preferences,
    onUpdate: (Preferences) -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_input))
    CheckRow(stringResource(R.string.pref_finger_draws), prefs.fingerDraws) { onUpdate(prefs.copy(fingerDraws = it)) }
    FieldLabel(stringResource(R.string.pref_zoom_lock_pan))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.pan_single), prefs.zoomLockPan == "single") { onUpdate(prefs.copy(zoomLockPan = "single")) }
        Chip(stringResource(R.string.pan_two), prefs.zoomLockPan == "double") { onUpdate(prefs.copy(zoomLockPan = "double")) }
        Chip(stringResource(R.string.pan_none), prefs.zoomLockPan == "none") { onUpdate(prefs.copy(zoomLockPan = "none")) }
    }
    CheckRow(stringResource(R.string.pref_detect_shapes), prefs.detectShapes) { onUpdate(prefs.copy(detectShapes = it)) }
    FieldLabel(stringResource(R.string.pref_pen_button_hold))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        penButtonOptions.forEach { (id, label) ->
            Chip(stringResource(label), prefs.penButtonTool == id) { onUpdate(prefs.copy(penButtonTool = id)) }
        }
    }
    if (prefs.penButtonTool == "eraser" || prefs.penButtonTool == "pan") {
        CheckRow(stringResource(R.string.pref_pen_button_hover), prefs.penButtonHover) {
            onUpdate(prefs.copy(penButtonHover = it))
        }
    }
    val tapOptions = tapGestureOptions.map { (id, res) -> id to stringResource(res) }
    FieldLabel(stringResource(R.string.pref_two_finger_tap))
    OptionDropdown(tapOptions, prefs.twoFingerTap) { onUpdate(prefs.copy(twoFingerTap = it)) }
    FieldLabel(stringResource(R.string.pref_three_finger_tap))
    OptionDropdown(tapOptions, prefs.threeFingerTap) { onUpdate(prefs.copy(threeFingerTap = it)) }
    FieldLabel(stringResource(R.string.pref_stylus_double_tap))
    OptionDropdown(tapOptions, prefs.stylusDoubleTap) { onUpdate(prefs.copy(stylusDoubleTap = it)) }
    FieldLabel(stringResource(R.string.pref_stylus_button_tap))
    OptionDropdown(tapOptions, prefs.stylusButtonTap) { onUpdate(prefs.copy(stylusButtonTap = it)) }
    FieldLabel(stringResource(R.string.pref_redmi_buttons))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pen_button_1), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
            OptionDropdown(tapOptions, prefs.stylusButton1Tap) { onUpdate(prefs.copy(stylusButton1Tap = it)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pen_button_2), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
            OptionDropdown(tapOptions, prefs.stylusButton2Tap) { onUpdate(prefs.copy(stylusButton2Tap = it)) }
        }
    }
}

/**
 * New Notes section: naming template string, template presets, and stem preview.
 */
@Composable
internal fun NewNotesPreferencesSection(
    prefs: Preferences,
    stemPreview: String,
    onUpdate: (Preferences) -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_new_notes))
    FieldLabel(stringResource(R.string.pref_filename_template))
    Text(stringResource(R.string.pref_filename_template_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    OutlinedTextField(
        value = prefs.newNoteNameTemplate,
        onValueChange = { onUpdate(prefs.copy(newNoteNameTemplate = it)) },
        singleLine = true,
        placeholder = { Text(NameTemplate.DEFAULT) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.width(280.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NAME_TEMPLATE_PRESETS.forEach { t ->
            Chip(t, prefs.newNoteNameTemplate == t) { onUpdate(prefs.copy(newNoteNameTemplate = t)) }
        }
    }
    Text(
        stringResource(R.string.pref_next_note_named, stemPreview),
        color = palette.textDim.toComposeColor(),
        fontSize = 12.sp,
    )
}

/**
 * Page section: page size, orientation, page borders, side margins, and background paper color.
 */
@Composable
internal fun PagePreferencesSection(
    prefs: Preferences,
    editor: Editor,
    onUpdate: (Preferences) -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_page))
    FieldLabel(stringResource(R.string.pref_default_page_size))
    SizeDropdown(prefs.defaultPageSize) { it?.let { size -> onUpdate(prefs.copy(defaultPageSize = size)) } }
    if (prefs.defaultPageSize == PageSize.CUSTOM) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MillimetreField(stringResource(R.string.width_mm), prefs.customPageWidthMm) {
                onUpdate(prefs.copy(customPageWidthMm = it))
            }
            MillimetreField(stringResource(R.string.height_mm), prefs.customPageHeightMm) {
                onUpdate(prefs.copy(customPageHeightMm = it))
            }
        }
    } else {
        FieldLabel(stringResource(R.string.pref_orientation))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.orientation_portrait), prefs.defaultPageOrientation == Orientation.PORTRAIT) {
                onUpdate(prefs.copy(defaultPageOrientation = Orientation.PORTRAIT))
            }
            Chip(stringResource(R.string.orientation_landscape), prefs.defaultPageOrientation == Orientation.LANDSCAPE) {
                onUpdate(prefs.copy(defaultPageOrientation = Orientation.LANDSCAPE))
            }
        }
    }
    CheckRow(stringResource(R.string.pref_hide_page_borders), prefs.hidePageBorders) {
        onUpdate(prefs.copy(hidePageBorders = it))
    }
    FieldLabel(stringResource(R.string.pref_side_margin_px, prefs.sideMargin.toInt()))
    Slider(
        value = prefs.sideMargin.toFloat(),
        onValueChange = { onUpdate(prefs.copy(sideMargin = it.toDouble())) },
        valueRange = 0f..64f,
        modifier = Modifier.width(280.dp),
    )
    FieldLabel(stringResource(R.string.pref_page_colour))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SwatchCircles(editor, com.xnotes.settings.SwatchRole.PAPER, prefs.pageColor) { onUpdate(prefs.copy(pageColor = it)) }
    }
    CheckRow(stringResource(R.string.pref_page_colour_follows_theme), prefs.pageColor == null) {
        onUpdate(prefs.copy(pageColor = if (it) null else editor.swatches(com.xnotes.settings.SwatchRole.PAPER).first()))
    }

    val style = editor.newNoteStyle
    val template = style.template ?: PagePattern.LINES.id
    FieldLabel(stringResource(R.string.caption_template))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.none), template == PageTemplates.NONE) {
            editor.saveNewNoteStyle(style.copy(template = PageTemplates.NONE))
        }
        Chip(stringResource(R.string.pattern_lines), template == PagePattern.LINES.id) {
            editor.saveNewNoteStyle(style.copy(template = PagePattern.LINES.id))
        }
        Chip(stringResource(R.string.pattern_dots), template == PagePattern.DOTS.id) {
            editor.saveNewNoteStyle(style.copy(template = PagePattern.DOTS.id))
        }
        Chip(stringResource(R.string.pattern_grid), template == PagePattern.GRID.id) {
            editor.saveNewNoteStyle(style.copy(template = PagePattern.GRID.id))
        }
    }
    if (template != PageTemplates.NONE) {
        FieldLabel(stringResource(R.string.caption_pattern_colour))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwatchCircles(editor, com.xnotes.settings.SwatchRole.LINES, style.patternColor?.copy(a = 255)) {
                editor.saveNewNoteStyle(style.copy(patternColor = it))
            }
        }
        FieldLabel(stringResource(R.string.caption_accent_colour))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwatchCircles(editor, com.xnotes.settings.SwatchRole.ACCENT, style.accentColor?.copy(a = 255)) {
                editor.saveNewNoteStyle(style.copy(accentColor = it))
            }
        }
    }
}

/**
 * Canvas section: default pattern, spacing, pattern color and paper color.
 */
@Composable
internal fun CanvasPreferencesSection(editor: Editor) {
    val palette = LocalPalette.current
    val currentBg = editor.newCanvasBackground ?: com.xnotes.core.infinite.CanvasBackground()
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_canvas))
    FieldLabel(stringResource(R.string.caption_pattern))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.none), currentBg.pattern == PagePattern.NONE) {
            editor.saveNewCanvasBackground(currentBg.copy(pattern = PagePattern.NONE))
        }
        Chip(stringResource(R.string.pattern_lines), currentBg.pattern == PagePattern.LINES) {
            editor.saveNewCanvasBackground(currentBg.copy(pattern = PagePattern.LINES))
        }
        Chip(stringResource(R.string.pattern_dots), currentBg.pattern == PagePattern.DOTS) {
            editor.saveNewCanvasBackground(currentBg.copy(pattern = PagePattern.DOTS))
        }
        Chip(stringResource(R.string.pattern_grid), currentBg.pattern == PagePattern.GRID) {
            editor.saveNewCanvasBackground(currentBg.copy(pattern = PagePattern.GRID))
        }
    }
    if (currentBg.pattern != PagePattern.NONE) {
        FieldLabel(stringResource(R.string.caption_spacing))
        Slider(
            value = currentBg.clampedSpacing.toFloat(),
            onValueChange = { editor.saveNewCanvasBackground(currentBg.copy(spacing = it.toDouble())) },
            valueRange = PageStyle.MIN_SPACING.toFloat()..PageStyle.MAX_SPACING.toFloat(),
            modifier = Modifier.width(280.dp),
        )
        FieldLabel(stringResource(R.string.caption_pattern_colour))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwatchCircles(editor, com.xnotes.settings.SwatchRole.LINES, currentBg.patternColor.copy(a = 255)) {
                editor.saveNewCanvasBackground(currentBg.copy(patternColor = it.copy(a = currentBg.patternColor.a)))
            }
        }
    }
    FieldLabel(stringResource(R.string.caption_paper))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SwatchCircles(editor, com.xnotes.settings.SwatchRole.PAPER, currentBg.paperColor) {
            editor.saveNewCanvasBackground(currentBg.copy(paperColor = it))
        }
    }
    CheckRow(stringResource(R.string.pref_page_colour_follows_theme), currentBg.paperColor == null) { followsTheme ->
        editor.saveNewCanvasBackground(currentBg.copy(paperColor = if (followsTheme) null else editor.swatches(com.xnotes.settings.SwatchRole.PAPER).first()))
    }
}

/**
 * Home Explorer section: default layout, layout switchers, sidebar sections, date style, and folder options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExplorerPreferencesSection(
    prefs: Preferences,
    editor: Editor,
    onUpdateHome: (Preferences) -> Unit,
    onNameColor: (Rgba) -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_home_explorer))
    Text(stringResource(R.string.pref_home_explorer_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    FieldLabel(stringResource(R.string.pref_default_layout))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExplorerLayout.entries.forEach { l -> Chip(stringResource(l.labelRes), editor.explorerView.layout == l) { editor.setDefaultLayout(l) } }
    }
    FieldLabel(stringResource(R.string.pref_switcher_layouts))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExplorerLayout.entries.forEach { l ->
            val on = l in prefs.switcherLayouts
            ExplorerChip(stringResource(l.labelRes), on, icon = if (on) XnotesIcons.check else XnotesIcons.plus) {
                val next = if (on) prefs.switcherLayouts - l else prefs.switcherLayouts + l
                if (next.isNotEmpty()) onUpdateHome(prefs.copy(switcherLayouts = ExplorerLayout.entries.filter { it in next }))
            }
        }
    }
    FieldLabel(stringResource(R.string.pref_sidebar_sections))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun section(label: String, on: Boolean, flip: () -> Preferences) =
            ExplorerChip(label, on, icon = if (on) XnotesIcons.check else XnotesIcons.plus) { onUpdateHome(flip()) }
        section(stringResource(R.string.recent), prefs.sidebarRecent) { prefs.copy(sidebarRecent = !prefs.sidebarRecent) }
        section(stringResource(R.string.pinned), prefs.sidebarPinned) { prefs.copy(sidebarPinned = !prefs.sidebarPinned) }
        section(stringResource(R.string.toolbar_colours), prefs.sidebarColours) { prefs.copy(sidebarColours = !prefs.sidebarColours) }
        section(stringResource(R.string.folder_tree_title), prefs.sidebarFolderTree) { prefs.copy(sidebarFolderTree = !prefs.sidebarFolderTree) }
        section(stringResource(R.string.tree_show_files), prefs.sidebarTreeFiles) { prefs.copy(sidebarTreeFiles = !prefs.sidebarTreeFiles) }
        if (prefs.trashDays != 0) section(stringResource(R.string.trash), prefs.sidebarTrash) { prefs.copy(sidebarTrash = !prefs.sidebarTrash) }
    }
    FieldLabel(stringResource(R.string.pref_home_opens_to))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.top_folder), prefs.homeOpensTo == "top") { onUpdateHome(prefs.copy(homeOpensTo = "top")) }
        Chip(stringResource(R.string.last_folder), prefs.homeOpensTo == "last") { onUpdateHome(prefs.copy(homeOpensTo = "last")) }
        Chip(stringResource(R.string.recent_and_pinned), prefs.homeOpensTo == "shelves") { onUpdateHome(prefs.copy(homeOpensTo = "shelves")) }
    }
    val words = rememberExplorerWords()
    FieldLabel(stringResource(R.string.pref_dates))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(words.daysAgo(2), prefs.dateStyle == "relative") { onUpdateHome(prefs.copy(dateStyle = "relative")) }
        Chip("${words.shortWeekday(java.time.DayOfWeek.WEDNESDAY)} ${clockText(words, java.time.LocalTime.of(17, 30), true)}", prefs.dateStyle == "day") { onUpdateHome(prefs.copy(dateStyle = "day")) }
        Chip(words.dayMonthYear(16, java.time.Month.SEPTEMBER, 2026), prefs.dateStyle == "date") { onUpdateHome(prefs.copy(dateStyle = "date")) }
    }
    FieldLabel(stringResource(R.string.pref_tapping_file))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.tap_opens), !prefs.tapPreviews) { onUpdateHome(prefs.copy(tapPreviews = false)) }
        Chip(stringResource(R.string.tap_previews), prefs.tapPreviews) { onUpdateHome(prefs.copy(tapPreviews = true)) }
    }
    FieldLabel(stringResource(R.string.pref_keep_deleted))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.off), prefs.trashDays == 0) { onUpdateHome(prefs.copy(trashDays = 0)) }
        Chip(pluralStringResource(R.plurals.days_count, 7, 7), prefs.trashDays == 7) { onUpdateHome(prefs.copy(trashDays = 7)) }
        Chip(pluralStringResource(R.plurals.days_count, 30, 30), prefs.trashDays == 30) { onUpdateHome(prefs.copy(trashDays = 30)) }
        Chip(stringResource(R.string.until_emptied), prefs.trashDays == Preferences.TRASH_FOREVER) { onUpdateHome(prefs.copy(trashDays = Preferences.TRASH_FOREVER)) }
    }
    if (editor.browseRoot != null) {
        FieldLabel(stringResource(R.string.pref_colour_names))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.colorNames.entries.sortedBy { it.value.lowercase() }.forEach { (c, name) ->
                Row(
                    Modifier.clip(MaterialTheme.shapes.small).background(palette.surface.toComposeColor())
                        .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.small)
                        .clickable { onNameColor(c) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(codeTint(c, palette)))
                    Text(name, color = palette.text.toComposeColor(), fontSize = 14.sp, maxLines = 1)
                }
            }
            var picking by remember { mutableStateOf(false) }
            Box {
                ExplorerChip(stringResource(R.string.name_a_colour), false, icon = XnotesIcons.plus) { picking = true }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    ColorCodeMenuContent { c -> picking = false; if (c != null) onNameColor(c) }
                }
            }
        }
    }
    CheckRow(stringResource(R.string.pref_per_folder_views), prefs.perFolderViews) { onUpdateHome(prefs.copy(perFolderViews = it)) }
    CheckRow(stringResource(R.string.pref_show_folder_counts), prefs.showFolderCounts) { onUpdateHome(prefs.copy(showFolderCounts = it)) }
    CheckRow(stringResource(R.string.pref_show_extensions), prefs.showExtensions) { onUpdateHome(prefs.copy(showExtensions = it)) }
}

/**
 * PDF export preferences section: bookmark creation option.
 */
@Composable
internal fun PdfExportPreferencesSection(
    prefs: Preferences,
    onUpdateHome: (Preferences) -> Unit,
) {
    HorizontalDivider(color = LocalPalette.current.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_pdf_export))
    CheckRow(stringResource(R.string.pref_pdf_heading_bookmarks), prefs.pdfHeadingBookmarks) {
        onUpdateHome(prefs.copy(pdfHeadingBookmarks = it))
    }
}

/**
 * Performance preferences section: max cache resolution and front buffering toggles.
 */
@Composable
internal fun PerformancePreferencesSection(
    prefs: Preferences,
    onUpdate: (Preferences) -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_performance))
    FieldLabel(stringResource(R.string.pref_max_cache_px, prefs.maxCacheResolution))
    Slider(
        value = prefs.maxCacheResolution.toFloat(),
        onValueChange = { onUpdate(prefs.copy(maxCacheResolution = Math.round(it))) },
        valueRange = 1024f..4096f,
        steps = 5,
        modifier = Modifier.width(280.dp),
    )
    Text(stringResource(R.string.pref_cache_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    CheckRow(stringResource(R.string.pref_disable_front_buffering), prefs.disableFrontBuffering) {
        onUpdate(prefs.copy(disableFrontBuffering = it))
    }
    Text(stringResource(R.string.pref_front_buffering_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
}

/**
 * Code highlighting preferences section: default language and Helix theme imports.
 */
@Composable
internal fun CodeHighlightingPreferencesSection(
    prefs: Preferences,
    editor: Editor,
    onUpdate: (Preferences) -> Unit,
    onImportCodeTheme: () -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_code_highlighting))
    val langOptions = remember { editor.scmLanguages().map { it to it } }
    FieldLabel(stringResource(R.string.pref_default_code_language))
    Text(stringResource(R.string.pref_code_language_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    OptionDropdown(listOf("plain" to stringResource(R.string.code_plain)) + langOptions, prefs.defaultCodeLanguage) {
        onUpdate(prefs.copy(defaultCodeLanguage = it))
    }
    FieldLabel(stringResource(R.string.pref_code_theme))
    Text(stringResource(R.string.pref_code_theme_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    if (editor.hasCustomCodeTheme) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                prefs.codeThemeName ?: stringResource(R.string.custom_theme),
                color = palette.text.toComposeColor(),
                fontSize = 13.sp,
            )
            TextButton(onClick = { editor.resetCodeTheme() }) { Text(stringResource(R.string.reset), fontSize = 13.sp) }
        }
    }
    TextButton(onClick = onImportCodeTheme) { Text(stringResource(R.string.import_theme), fontSize = 13.sp) }
}

/**
 * Fonts preferences section: font imports and custom font management.
 */
@Composable
internal fun FontsPreferencesSection(
    customFonts: List<FontCatalog.Choice>,
    onImportFont: () -> Unit,
    onRemoveFont: (FontFace) -> Unit,
) {
    val palette = LocalPalette.current
    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_fonts))
    Text(stringResource(R.string.pref_fonts_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    TextButton(onClick = onImportFont) { Text(stringResource(R.string.import_font), fontSize = 13.sp) }
    for (font in customFonts) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                font.label,
                color = palette.text.toComposeColor(),
                fontSize = 13.sp,
                style = TextStyle(fontFamily = font.face.toComposeFamily()),
            )
            if (font.mono) {
                Text(stringResource(R.string.mono_suffix), color = palette.accent.toComposeColor(), fontSize = 11.sp)
            }
            TextButton(onClick = { onRemoveFont(font.face) }) { Text(stringResource(R.string.remove), fontSize = 12.sp) }
        }
    }
}

/**
 * Toolbar style, position, sizing, and swatch controls (the top part of toolbar preferences).
 */
@Composable
internal fun ToolbarControlsSection(
    prefs: Preferences,
    editor: Editor,
    canvasTab: Boolean,
    onSelectCanvasTab: (Boolean) -> Unit,
    onUpdateHome: (Preferences) -> Unit,
    onApplyEdited: (ToolbarLayout) -> Unit,
) {
    val palette = LocalPalette.current
    val look = prefs.toolbarLook

    HorizontalDivider(color = palette.border.toComposeColor())
    SectionTitle(stringResource(R.string.pref_toolbar))
    FieldLabel(stringResource(R.string.pref_toolbar_style))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.toolbar_docked), !look.floating) { onUpdateHome(prefs.copy(toolbarLook = look.copy(floating = false))) }
        Chip(stringResource(R.string.toolbar_floating), look.floating) { onUpdateHome(prefs.copy(toolbarLook = look.copy(floating = true))) }
    }
    FieldLabel(stringResource(R.string.pref_toolbar_position))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((position, label) in listOf(
            ToolbarPosition.TOP to R.string.edge_top,
            ToolbarPosition.BOTTOM to R.string.edge_bottom,
            ToolbarPosition.LEFT to R.string.edge_left,
            ToolbarPosition.RIGHT to R.string.edge_right,
        )) {
            Chip(stringResource(label), look.position == position) { onUpdateHome(prefs.copy(toolbarLook = look.copy(position = position))) }
        }
    }
    FieldLabel(stringResource(R.string.pref_toolbar_size))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((size, label) in listOf(
            ToolbarSize.COMPACT to R.string.toolbar_size_compact,
            ToolbarSize.REGULAR to R.string.toolbar_size_regular,
            ToolbarSize.COMFORTABLE to R.string.toolbar_size_comfortable,
        )) {
            Chip(stringResource(label), look.size == size) { onUpdateHome(prefs.copy(toolbarLook = look.copy(size = size))) }
        }
    }
    FieldLabel(stringResource(R.string.pref_toolbar_colours_n, editor.toolbarColorCount))
    Slider(
        value = editor.toolbarColorCount.toFloat(),
        onValueChange = { editor.applyToolbarColorCount(Math.round(it)) },
        valueRange = 1f..InkPalette.MAX_SWATCHES.toFloat(),
        steps = InkPalette.MAX_SWATCHES - 2,
        modifier = Modifier.width(280.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip("xnote", selected = !canvasTab) { onSelectCanvasTab(false) }
        Chip("xcanvas", selected = canvasTab) { onSelectCanvasTab(true) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onApplyEdited(if (canvasTab) ToolbarLayout.CANVAS_DEFAULT else ToolbarLayout.DEFAULT) }) {
            Text(stringResource(R.string.reset), fontSize = 13.sp)
        }
    }
    Text(stringResource(R.string.pref_toolbar_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
}

/**
 * Toolbar preferences section: style, positioning, sizing, swatch slider, and toolbar customizer layout.
 */
@Composable
internal fun ToolbarPreferencesSection(
    prefs: Preferences,
    editor: Editor,
    canvasTab: Boolean,
    onSelectCanvasTab: (Boolean) -> Unit,
    onUpdateHome: (Preferences) -> Unit,
    onApplyEdited: (ToolbarLayout) -> Unit,
    dragItem: ToolbarItem?,
    dropTarget: Pair<Int, Int>?,
    dragSection: Int?,
    sectionDropTarget: Int?,
    chipCoords: MutableMap<ToolbarItem, androidx.compose.ui.layout.LayoutCoordinates>,
    sectionCoords: MutableMap<Int, androidx.compose.ui.layout.LayoutCoordinates>,
    sectionGrabCoords: MutableMap<Int, androidx.compose.ui.layout.LayoutCoordinates>,
    onClearDrag: () -> Unit,
    onStartItemDrag: (ToolbarItem, Offset) -> Unit,
    onMoveItemDrag: (Offset) -> Unit,
    onStartSectionDrag: (Int, Offset) -> Unit,
    onMoveSectionDrag: (Offset) -> Unit,
) {
    val layout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    ToolbarControlsSection(
        prefs = prefs,
        editor = editor,
        canvasTab = canvasTab,
        onSelectCanvasTab = onSelectCanvasTab,
        onUpdateHome = onUpdateHome,
        onApplyEdited = onApplyEdited,
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
        onToggle = { s, i -> onApplyEdited(layout.toggleVisible(s, i)) },
        onDelete = { s -> onApplyEdited(layout.removeSection(s)) },
        onAdd = { onApplyEdited(layout.addSection()) },
        onDragStart = onStartItemDrag,
        onDrag = onMoveItemDrag,
        onDrop = {
            val target = dropTarget
            val moving = dragItem
            if (target != null && moving != null) {
                val fSec = layout.sections.indexOfFirst { s -> s.entries.any { it.item == moving } }
                val fIdx = if (fSec >= 0) layout.sections[fSec].entries.indexOfFirst { it.item == moving } else -1
                if (fSec >= 0 && fIdx >= 0) {
                    onApplyEdited(layout.moveItem(fSec, fIdx, target.first, target.second))
                }
            }
            onClearDrag()
        },
        onCancel = onClearDrag,
        onSectionDragStart = onStartSectionDrag,
        onSectionDrag = onMoveSectionDrag,
        onSectionDrop = {
            val from = dragSection
            val insertAt = sectionDropTarget
            if (from != null && insertAt != null) {
                val to = (if (insertAt > from) insertAt - 1 else insertAt).coerceIn(0, layout.sections.lastIndex)
                if (to != from) onApplyEdited(layout.moveSection(from, to))
            }
            onClearDrag()
        },
        onSectionCancel = onClearDrag,
    )
    Spacer(Modifier.size(8.dp))
}
