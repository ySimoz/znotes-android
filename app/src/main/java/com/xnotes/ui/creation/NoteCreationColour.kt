package com.xnotes.ui.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xnotes.core.model.Rgba
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * Circular colour swatch with selection border for note and cover colour pickers.
 */
@Composable
internal fun NoteCreationColour(
    colour: Rgba,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val borderColor = if (selected) palette.accent else palette.border
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colour.toComposeColor())
            .border(if (selected) 3.dp else 1.dp, borderColor.toComposeColor(), CircleShape)
            .clickable(onClick = onClick),
    )
}
