package com.xnotes.ui.creation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.PageSize
import com.xnotes.ui.ModeChip
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** Title text field and summary toggle row for the note creation dialog. */
@Composable
internal fun NoteCreationTitleField(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    error: String?,
    focus: FocusRequester,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    pageSize: PageSize,
    advancedOptions: Boolean,
    onToggleAdvanced: () -> Unit,
) {
    val palette = LocalPalette.current
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { ev ->
            when {
                ev.type != KeyEventType.KeyDown -> false
                ev.key == Key.Enter || ev.key == Key.NumPadEnter -> { onDone(); true }
                ev.key == Key.Escape -> { onDismiss(); true }
                else -> false
            }
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.create_note_summary, pageSize.displayName),
            color = palette.textDim.toComposeColor(),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        val toggleLabel = stringResource(if (advancedOptions) R.string.create_basic_options else R.string.create_advanced_options)
        ModeChip(toggleLabel, advancedOptions, onToggleAdvanced)
    }
}
