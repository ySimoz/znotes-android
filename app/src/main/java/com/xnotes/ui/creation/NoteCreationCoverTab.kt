package com.xnotes.ui.creation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.platform.CustomAssetFolders
import com.xnotes.platform.TemplateLibrary
import com.xnotes.ui.ColorPickerPopup
import com.xnotes.ui.Editor
import com.xnotes.ui.ModeChip
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * Cover appearance options in the note creation dialog.
 */
@Composable
internal fun NoteCreationCoverTab(
    editor: Editor,
    includeCover: Boolean,
    onIncludeCoverChange: (Boolean) -> Unit,
    coverColor: Rgba,
    onCoverColorChange: (Rgba) -> Unit,
    coverPattern: String,
    onCoverPatternChange: (String) -> Unit,
    coverImageBytes: ByteArray?,
    onCoverImageBytesChange: (ByteArray?) -> Unit,
    coverTemplateStyle: PageStyle,
    onCoverTemplateStyleChange: (PageStyle) -> Unit,
    coverSourceTab: Int,
    onCoverSourceTabChange: (Int) -> Unit,
    pageMm: Pair<Double, Double>,
    availableTemplates: List<TemplateLibrary.Entry>,
    customCoverImages: List<CustomAssetFolders.CustomImage>,
    onImportTemplate: () -> Unit,
    onPickCoverImage: () -> Unit,
) {
    val palette = LocalPalette.current
    val coverColours = remember {
        listOf(
            Rgba(47, 62, 112), Rgba(36, 91, 78), Rgba(122, 55, 70),
            Rgba(157, 99, 42), Rgba(45, 49, 58), Rgba(80, 66, 120),
        )
    }

    NoteCreationCoverIncludeToggle(includeCover, onIncludeCoverChange)

    if (includeCover) {
        Text(stringResource(R.string.create_cover_colour), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        NoteCreationCoverColourRow(editor, coverColor, onCoverColorChange, coverColours)
        NoteCreationCoverPatternSection(
            editor = editor,
            coverColor = coverColor,
            coverPattern = coverPattern,
            onCoverPatternChange = onCoverPatternChange,
            coverImageBytes = coverImageBytes,
            onCoverImageBytesChange = onCoverImageBytesChange,
            coverTemplateStyle = coverTemplateStyle,
            onCoverTemplateStyleChange = onCoverTemplateStyleChange,
            coverSourceTab = coverSourceTab,
            onCoverSourceTabChange = onCoverSourceTabChange,
            pageMm = pageMm,
            availableTemplates = availableTemplates,
            customCoverImages = customCoverImages,
            onImportTemplate = onImportTemplate,
            onPickCoverImage = onPickCoverImage,
        )
    }
}

@Composable
private fun NoteCreationCoverIncludeToggle(includeCover: Boolean, onIncludeCoverChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onIncludeCoverChange(!includeCover) },
    ) {
        Checkbox(checked = includeCover, onCheckedChange = onIncludeCoverChange)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.create_add_cover), color = palette.text.toComposeColor(), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(stringResource(R.string.create_cover_help), color = palette.textDim.toComposeColor(), fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCreationCoverColourRow(
    editor: Editor,
    coverColor: Rgba,
    onCoverColorChange: (Rgba) -> Unit,
    presets: List<Rgba>,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        presets.forEach { colour ->
            NoteCreationColour(colour, coverColor == colour) { onCoverColorChange(colour) }
        }
        Box {
            ModeChip(stringResource(R.string.create_custom_colour), coverColor !in presets) {
                pickerOpen = true
            }
            if (pickerOpen) {
                ColorPickerPopup(coverColor, editor.recentColors, { pickerOpen = false }) { onCoverColorChange(it) }
            }
        }
    }
}
