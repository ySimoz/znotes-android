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

/** Pattern and custom image strip section for page appearance. */
@Composable
internal fun NoteCreationPagePatternSection(
    editor: Editor,
    pagePattern: String,
    onPagePatternChange: (String) -> Unit,
    pageImageBytes: ByteArray?,
    onPageImageBytesChange: (ByteArray?) -> Unit,
    pageTemplateStyle: PageStyle,
    onPageTemplateStyleChange: (PageStyle) -> Unit,
    pageSourceTab: Int,
    onPageSourceTabChange: (Int) -> Unit,
    pageMm: Pair<Double, Double>,
    pageColor: Rgba,
    availableTemplates: List<TemplateLibrary.Entry>,
    customPageImages: List<CustomAssetFolders.CustomImage>,
    onImportTemplate: () -> Unit,
    onPickPageImage: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Text(stringResource(R.string.create_page_pattern), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    ModeChip(stringResource(R.string.none), pagePattern == PageTemplates.NONE && pageImageBytes == null) {
        onPagePatternChange(PageTemplates.NONE)
        onPageImageBytesChange(null)
        editor.noteCreationPageImageBytes = null
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModeChip(stringResource(R.string.create_template_source), pageSourceTab == 0) { onPageSourceTabChange(0) }
        ModeChip(stringResource(R.string.create_image_source), pageSourceTab == 1) { onPageSourceTabChange(1) }
    }
    Text(
        stringResource(if (pageSourceTab == 0) R.string.create_template_vector_help else R.string.create_template_image_help),
        color = palette.textDim.toComposeColor(),
        fontSize = 11.sp,
    )
    if (pageSourceTab == 0) {
        TemplateStrip(
            entries = availableTemplates,
            selected = pagePattern,
            pageMm = pageMm,
            ink = pageTemplateStyle.patternColor ?: Rgba(70, 70, 70),
            accent = pageTemplateStyle.accentColor ?: PageStyle.DEFAULT_ACCENT_COLOR,
            paper = pageColor,
            onSelect = {
                onPagePatternChange(it)
                onPageImageBytesChange(null)
                editor.noteCreationPageImageBytes = null
                onPageTemplateStyleChange(pageTemplateStyle.withTemplate(it, PageTemplates.NONE))
            },
            onImport = onImportTemplate,
            onRemove = { editor.removeTemplate(it) },
            onKeep = { },
        )
        CreationTemplateSettings(
            entry = availableTemplates.firstOrNull { it.key == pagePattern }.takeIf { pageImageBytes == null },
            style = pageTemplateStyle,
            pageMm = pageMm,
            ink = Rgba(70, 70, 70),
            accent = PageStyle.DEFAULT_ACCENT_COLOR,
            paper = pageColor,
            onChange = onPageTemplateStyleChange,
        )
    } else {
        Text(stringResource(R.string.create_custom_page_patterns), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        CustomImageStrip(
            images = customPageImages,
            selectedBytes = pageImageBytes,
            pageMm = pageMm,
            paper = pageColor,
            onSelect = { bytes ->
                onPageImageBytesChange(bytes)
                editor.noteCreationPageImageBytes = bytes
                onPagePatternChange(PageTemplates.NONE)
            },
            onImport = onPickPageImage,
            onClear = {
                onPageImageBytesChange(null)
                editor.noteCreationPageImageBytes = null
            },
            onDelete = { entry ->
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        CustomAssetFolders.deleteImage(
                            context, editor.browseRoot,
                            CustomAssetFolders.Kind.PAGE, entry.id,
                        )
                    }
                    if (deleted) {
                        onPageImageBytesChange(null)
                        editor.noteCreationPageImageBytes = null
                        editor.customAssetsChanged()
                    } else {
                        editor.message = context.getString(R.string.create_delete_asset_failed)
                    }
                }
            },
        )
    }
}
