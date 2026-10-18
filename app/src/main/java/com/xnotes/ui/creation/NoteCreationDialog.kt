package com.xnotes.ui.creation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.model.Rgba
import com.xnotes.platform.CustomAssetFolders
import com.xnotes.platform.TemplateLibrary
import com.xnotes.ui.Editor
import com.xnotes.ui.ModeChip
import com.xnotes.ui.NoteCreationOptions
import com.xnotes.ui.TemplateLibraryUi
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Note creation dialog keeping page, cover, and document settings together.
 */
@Composable
fun NoteCreationDialog(
    title: String,
    initial: String,
    editor: Editor,
    error: String?,
    onImportTemplate: () -> Unit,
    onPickCoverImage: () -> Unit,
    onPickPageImage: () -> Unit,
    onConfirm: (String, NoteCreationOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var text by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    var pageSize by remember { mutableStateOf(editor.defaultNewPageSize) }
    var orientation by remember { mutableStateOf(editor.defaultNewPageOrientation) }
    var customWidthMm by remember { mutableDoubleStateOf(editor.preferences.customPageWidthMm) }
    var customHeightMm by remember { mutableDoubleStateOf(editor.preferences.customPageHeightMm) }
    var advancedOptions by remember { mutableStateOf(true) }
    var appearanceTab by remember { mutableIntStateOf(0) }
    var pageSourceTab by remember { mutableIntStateOf(if (editor.noteCreationPageImageBytes == null) 0 else 1) }
    var coverSourceTab by remember { mutableIntStateOf(if (editor.noteCreationCoverImageBytes == null) 0 else 1) }
    var pageTemplateStyle by remember { mutableStateOf(editor.newNoteStyle) }
    var coverTemplateStyle by remember { mutableStateOf(PageStyle()) }
    var includeCover by remember { mutableStateOf(false) }
    var pageColor by remember { mutableStateOf(editor.newNoteStyle.pageColor) }
    val savedTemplate = editor.newNoteStyle.template
    var pageImageBytes by remember { mutableStateOf(editor.noteCreationPageImageBytes) }
    var pagePattern by remember {
        mutableStateOf(if (editor.noteCreationPageImageBytes != null) PageTemplates.NONE else savedTemplate ?: PageTemplates.NONE)
    }
    var coverColor by remember { mutableStateOf(Rgba(47, 62, 112)) }
    var coverImageBytes by remember { mutableStateOf(editor.noteCreationCoverImageBytes) }
    var coverPattern by remember { mutableStateOf(PageTemplates.NONE) }
    var saveAsDefault by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(editor.noteCreationCoverImageBytes) {
        coverImageBytes = editor.noteCreationCoverImageBytes
        if (editor.noteCreationCoverImageBytes != null) {
            includeCover = true
            coverSourceTab = 1
        }
    }
    LaunchedEffect(editor.noteCreationPageImageBytes) {
        pageImageBytes = editor.noteCreationPageImageBytes
        if (editor.noteCreationPageImageBytes != null) pageSourceTab = 1
    }

    val templateVersion = TemplateLibraryUi.version
    val availableTemplates = TemplateLibrary.all()
    val customPageImages by produceState(initialValue = emptyList<CustomAssetFolders.CustomImage>(), key1 = editor.browseRoot, key2 = templateVersion) {
        value = withContext(Dispatchers.IO) { CustomAssetFolders.loadImages(context, editor.browseRoot, CustomAssetFolders.Kind.PAGE) }
    }
    val customCoverImages by produceState(initialValue = emptyList<CustomAssetFolders.CustomImage>(), key1 = editor.browseRoot, key2 = templateVersion) {
        value = withContext(Dispatchers.IO) { CustomAssetFolders.loadImages(context, editor.browseRoot, CustomAssetFolders.Kind.COVER) }
    }
    val pageMm = remember(pageSize, orientation, customWidthMm, customHeightMm) {
        if (pageSize == com.xnotes.core.model.PageSize.CUSTOM) {
            if (orientation == com.xnotes.core.model.Orientation.LANDSCAPE) {
                maxOf(customWidthMm, customHeightMm) to minOf(customWidthMm, customHeightMm)
            } else {
                minOf(customWidthMm, customHeightMm) to maxOf(customWidthMm, customHeightMm)
            }
        } else {
            pageSize.millimetres(orientation)
        }
    }

    val confirm = {
        val name = text.text.trim()
        if (name.isNotEmpty()) {
            val effectivePagePattern = if (pageImageBytes != null) PageTemplates.NONE else pagePattern
            val baseStyle = pageTemplateStyle.withTemplate(effectivePagePattern, PageTemplates.NONE).copy(pageColor = pageColor)
            val coverStyle = coverTemplateStyle.withTemplate(
                if (coverImageBytes != null) PageTemplates.NONE else coverPattern, PageTemplates.NONE,
            ).copy(pageColor = coverColor, patternColor = Rgba(255, 255, 255, 100))
            if (saveAsDefault) {
                editor.saveNewNoteStyle(baseStyle)
                if (pageSize == com.xnotes.core.model.PageSize.CUSTOM) {
                    editor.applyPreferences(editor.preferences.copy(customPageWidthMm = customWidthMm, customPageHeightMm = customHeightMm))
                }
            }
            onConfirm(
                name,
                NoteCreationOptions(
                    pageSize = pageSize,
                    orientation = orientation,
                    pageStyle = baseStyle,
                    includeCover = includeCover,
                    coverStyle = coverStyle,
                    coverImageBytes = coverImageBytes,
                    pageImageBytes = pageImageBytes,
                    customWidthMm = if (pageSize == com.xnotes.core.model.PageSize.CUSTOM) customWidthMm else null,
                    customHeightMm = if (pageSize == com.xnotes.core.model.PageSize.CUSTOM) customHeightMm else null,
                ),
            )
            editor.noteCreationCoverImageBytes = null
            editor.noteCreationPageImageBytes = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteCreationTitleField(text, { text = it }, error, focus, confirm, onDismiss, pageSize, advancedOptions) {
                    advancedOptions = !advancedOptions
                }
                if (advancedOptions) {
                    HorizontalDivider(color = palette.border.toComposeColor())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModeChip(stringResource(R.string.create_page_appearance), appearanceTab == 0) { appearanceTab = 0 }
                        ModeChip(stringResource(R.string.create_cover_tab), appearanceTab == 1) { appearanceTab = 1 }
                    }
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (appearanceTab == 0) {
                            NoteCreationPageTab(
                                editor, pageSize, { pageSize = it },
                                customWidthMm, { customWidthMm = it },
                                customHeightMm, { customHeightMm = it },
                                orientation, { orientation = it },
                                pageColor, { pageColor = it }, pagePattern, { pagePattern = it },
                                pageImageBytes, { pageImageBytes = it }, pageTemplateStyle, { pageTemplateStyle = it },
                                pageSourceTab, { pageSourceTab = it }, pageMm, availableTemplates, customPageImages,
                                onImportTemplate, onPickPageImage,
                            )
                        } else {
                            NoteCreationCoverTab(
                                editor, includeCover, { includeCover = it }, coverColor, { coverColor = it },
                                coverPattern, { coverPattern = it }, coverImageBytes, { coverImageBytes = it },
                                coverTemplateStyle, { coverTemplateStyle = it }, coverSourceTab, { coverSourceTab = it },
                                pageMm, availableTemplates, customCoverImages, onImportTemplate, onPickCoverImage,
                            )
                        }
                        HorizontalDivider(color = palette.border.toComposeColor())
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { saveAsDefault = !saveAsDefault },
                        ) {
                            Checkbox(checked = saveAsDefault, onCheckedChange = { saveAsDefault = it })
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.create_save_as_default), color = palette.text.toComposeColor(), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(stringResource(R.string.create_save_as_default_help), color = palette.textDim.toComposeColor(), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = text.text.isNotBlank()) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
