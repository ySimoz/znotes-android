package com.xnotes.ui.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.model.Rgba
import com.xnotes.platform.CustomAssetFolders
import com.xnotes.platform.TemplateLibrary
import com.xnotes.ui.CreationTemplateSettings
import com.xnotes.ui.CustomImageStrip
import com.xnotes.ui.Editor
import com.xnotes.ui.ModeChip
import com.xnotes.ui.TemplateStrip
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pattern and custom image strip section for cover appearance. */
@Composable
internal fun NoteCreationCoverPatternSection(
    editor: Editor,
    coverColor: Rgba,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Text(stringResource(R.string.create_cover_pattern), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    ModeChip(stringResource(R.string.none), coverPattern == PageTemplates.NONE && coverImageBytes == null) {
        onCoverPatternChange(PageTemplates.NONE)
        onCoverImageBytesChange(null)
        editor.noteCreationCoverImageBytes = null
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModeChip(stringResource(R.string.create_template_source), coverSourceTab == 0) { onCoverSourceTabChange(0) }
        ModeChip(stringResource(R.string.create_image_source), coverSourceTab == 1) { onCoverSourceTabChange(1) }
    }
    Text(
        stringResource(if (coverSourceTab == 0) R.string.create_template_vector_help else R.string.create_template_image_help),
        color = palette.textDim.toComposeColor(),
        fontSize = 11.sp,
    )
    if (coverSourceTab == 0) {
        TemplateStrip(
            entries = availableTemplates,
            selected = coverPattern,
            pageMm = pageMm,
            ink = coverTemplateStyle.patternColor ?: Rgba(245, 245, 245),
            accent = coverTemplateStyle.accentColor ?: Rgba(255, 190, 120),
            paper = coverColor,
            onSelect = {
                onCoverPatternChange(it)
                onCoverImageBytesChange(null)
                editor.noteCreationCoverImageBytes = null
                onCoverTemplateStyleChange(coverTemplateStyle.withTemplate(it, PageTemplates.NONE))
            },
            onImport = onImportTemplate,
            onRemove = { editor.removeTemplate(it) },
            onKeep = { },
        )
        CreationTemplateSettings(
            entry = availableTemplates.firstOrNull { it.key == coverPattern }.takeIf { coverImageBytes == null },
            style = coverTemplateStyle,
            pageMm = pageMm,
            ink = Rgba(245, 245, 245),
            accent = Rgba(255, 190, 120),
            paper = coverColor,
            onChange = onCoverTemplateStyleChange,
        )
    } else {
        Text(stringResource(R.string.create_custom_cover_patterns), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        CustomImageStrip(
            images = customCoverImages,
            selectedBytes = coverImageBytes,
            pageMm = pageMm,
            paper = coverColor,
            onSelect = { bytes ->
                onCoverImageBytesChange(bytes)
                editor.noteCreationCoverImageBytes = bytes
                onCoverPatternChange(PageTemplates.NONE)
            },
            onImport = onPickCoverImage,
            onClear = {
                onCoverImageBytesChange(null)
                editor.noteCreationCoverImageBytes = null
            },
            onDelete = { entry ->
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        CustomAssetFolders.deleteImage(
                            context, editor.browseRoot,
                            CustomAssetFolders.Kind.COVER, entry.id,
                        )
                    }
                    if (deleted) {
                        onCoverImageBytesChange(null)
                        editor.noteCreationCoverImageBytes = null
                        editor.customAssetsChanged()
                    } else {
                        editor.message = context.getString(R.string.create_delete_asset_failed)
                    }
                }
            },
        )
    }
}
