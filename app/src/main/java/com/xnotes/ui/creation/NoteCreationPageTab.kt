package com.xnotes.ui.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.platform.CustomAssetFolders
import com.xnotes.platform.TemplateLibrary
import com.xnotes.ui.ColorPickerPopup
import com.xnotes.ui.Editor
import com.xnotes.ui.MillimetreField
import com.xnotes.ui.ModeChip
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * Page appearance options in the note creation dialog.
 */
@Composable
internal fun NoteCreationPageTab(
    editor: Editor,
    pageSize: PageSize,
    onPageSizeChange: (PageSize) -> Unit,
    customWidthMm: Double,
    onCustomWidthMmChange: (Double) -> Unit,
    customHeightMm: Double,
    onCustomHeightMmChange: (Double) -> Unit,
    orientation: Orientation,
    onOrientationChange: (Orientation) -> Unit,
    pageColor: Rgba?,
    onPageColorChange: (Rgba?) -> Unit,
    pagePattern: String,
    onPagePatternChange: (String) -> Unit,
    pageImageBytes: ByteArray?,
    onPageImageBytesChange: (ByteArray?) -> Unit,
    pageTemplateStyle: PageStyle,
    onPageTemplateStyleChange: (PageStyle) -> Unit,
    pageSourceTab: Int,
    onPageSourceTabChange: (Int) -> Unit,
    pageMm: Pair<Double, Double>,
    availableTemplates: List<TemplateLibrary.Entry>,
    customPageImages: List<CustomAssetFolders.CustomImage>,
    onImportTemplate: () -> Unit,
    onPickPageImage: () -> Unit,
) {
    val palette = LocalPalette.current
    val pageColours = remember {
        listOf(
            Rgba(255, 255, 255), Rgba(250, 247, 235), Rgba(231, 241, 255),
            Rgba(232, 246, 235), Rgba(255, 236, 236), Rgba(35, 39, 48),
        )
    }

    NoteCreationPageSizeRow(
        pageSize, onPageSizeChange,
        customWidthMm, onCustomWidthMmChange,
        customHeightMm, onCustomHeightMmChange,
    )
    NoteCreationOrientationRow(orientation, onOrientationChange)

    Text(stringResource(R.string.create_page_appearance), color = palette.text.toComposeColor(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Text(stringResource(R.string.create_paper_colour), color = palette.textDim.toComposeColor(), fontSize = 12.sp)

    NoteCreationPaperColourRow(editor, pageColor, onPageColorChange, pageColours)

    NoteCreationPagePatternSection(
        editor = editor,
        pagePattern = pagePattern,
        onPagePatternChange = onPagePatternChange,
        pageImageBytes = pageImageBytes,
        onPageImageBytesChange = onPageImageBytesChange,
        pageTemplateStyle = pageTemplateStyle,
        onPageTemplateStyleChange = onPageTemplateStyleChange,
        pageSourceTab = pageSourceTab,
        onPageSourceTabChange = onPageSourceTabChange,
        pageMm = pageMm,
        pageColor = pageColor ?: palette.paper,
        availableTemplates = availableTemplates,
        customPageImages = customPageImages,
        onImportTemplate = onImportTemplate,
        onPickPageImage = onPickPageImage,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCreationPageSizeRow(
    pageSize: PageSize,
    onPageSizeChange: (PageSize) -> Unit,
    customWidthMm: Double,
    onCustomWidthMmChange: (Double) -> Unit,
    customHeightMm: Double,
    onCustomHeightMmChange: (Double) -> Unit,
) {
    val palette = LocalPalette.current
    Text(stringResource(R.string.create_page_size), color = palette.text.toComposeColor(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PageSize.entries.forEach { size ->
            val label = when (size) {
                PageSize.CUSTOM -> stringResource(R.string.page_size_custom)
                PageSize.LETTER -> stringResource(R.string.page_size_letter)
                PageSize.LEGAL -> stringResource(R.string.page_size_legal)
                PageSize.SLIDE_16_9 -> stringResource(R.string.page_size_slide_16_9)
                PageSize.SLIDE_4_3 -> stringResource(R.string.page_size_slide_4_3)
                else -> size.displayName
            }
            ModeChip(label, pageSize == size) { onPageSizeChange(size) }
        }
    }
    if (pageSize == PageSize.CUSTOM) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MillimetreField(stringResource(R.string.width_mm), customWidthMm) { onCustomWidthMmChange(it) }
            MillimetreField(stringResource(R.string.height_mm), customHeightMm) { onCustomHeightMmChange(it) }
        }
    }
}

@Composable
private fun NoteCreationOrientationRow(orientation: Orientation, onOrientationChange: (Orientation) -> Unit) {
    val palette = LocalPalette.current
    Text(stringResource(R.string.create_orientation), color = palette.text.toComposeColor(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModeChip(stringResource(R.string.orientation_portrait), orientation == Orientation.PORTRAIT) { onOrientationChange(Orientation.PORTRAIT) }
        ModeChip(stringResource(R.string.orientation_landscape), orientation == Orientation.LANDSCAPE) { onOrientationChange(Orientation.LANDSCAPE) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCreationPaperColourRow(
    editor: Editor,
    pageColor: Rgba?,
    onPageColorChange: (Rgba?) -> Unit,
    presets: List<Rgba>,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ModeChip(stringResource(R.string.default_choice), pageColor == null) { onPageColorChange(null) }
        presets.forEach { colour ->
            NoteCreationColour(colour, pageColor == colour) { onPageColorChange(colour) }
        }
        Box {
            ModeChip(stringResource(R.string.create_custom_colour), pageColor != null && pageColor !in presets) {
                pickerOpen = true
            }
            if (pickerOpen) {
                ColorPickerPopup(pageColor, editor.recentColors, { pickerOpen = false }) { onPageColorChange(it) }
            }
        }
    }
}
