package com.xnotes.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.infinite.CanvasBackground
import com.xnotes.core.model.PagePattern
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.settings.SwatchRole
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/**
 * The full-screen New canvas creation screen. Displays an interactive live preview of the infinite
 * canvas background beside controls for pattern (None, Lines, Dots, Grid), spacing, pattern colour/opacity,
 * and paper colour, with one-tap default saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewCanvasScreen(
    editor: Editor,
    folderName: String,
    initialName: String,
    error: String?,
    busy: Boolean,
    onCreate: (String, CanvasBackground) -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val accent = palette.accent.toComposeColor()
    val border = palette.border.toComposeColor()

    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    var selectedOnce by remember { mutableStateOf(false) }
    var keepAllUntil by remember { mutableStateOf(0L) }
    var nameFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val fieldBounds = remember { HashMap<String, androidx.compose.ui.geometry.Rect>() }

    var background by remember {
        mutableStateOf(editor.newCanvasBackground ?: CanvasBackground())
    }
    var saveAsDefault by remember { mutableStateOf(false) }

    fun create() {
        if (busy) return
        if (saveAsDefault) {
            editor.saveNewCanvasBackground(background)
        }
        onCreate(name.text, background)
    }

    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            focusManager.clearFocus()
            imeWasVisible = false
        }
    }

    Scaffold(
        containerColor = palette.surface.toComposeColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (fieldBounds.values.none { it.contains(offset) }) {
                            focusManager.clearFocus()
                        }
                    }
                },
        ) {
            val screenW = maxWidth
            val wide = screenW >= 640.dp
            val nameStyle = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)

            Column(Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(XnotesIcons.close, stringResource(R.string.close_new_note), tint = text)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$folderName / ${stringResource(R.string.new_canvas)}",
                            color = dim,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        BasicTextField(
                            value = name,
                            onValueChange = { v ->
                                val caretOnly = v.text == name.text
                                name = if (caretOnly && android.os.SystemClock.uptimeMillis() < keepAllUntil) {
                                    v.copy(selection = TextRange(0, v.text.length))
                                } else v
                            },
                            singleLine = true,
                            textStyle = nameStyle,
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            decorationBox = { field ->
                                Box {
                                    if (name.text.isEmpty()) {
                                        Text(initialName, style = nameStyle.copy(color = dim.copy(alpha = 0.5f)), maxLines = 1)
                                    }
                                    field()
                                }
                            },
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .onGloballyPositioned { fieldBounds["name"] = it.boundsInRoot() }
                                .padding(vertical = 4.dp)
                                .onFocusChanged {
                                    nameFocused = it.isFocused
                                    if (it.isFocused && !selectedOnce) {
                                        selectedOnce = true
                                        keepAllUntil = android.os.SystemClock.uptimeMillis() + 500
                                        name = name.copy(selection = TextRange(0, name.text.length))
                                    }
                                },
                        )
                        Box(
                            Modifier.widthIn(max = 480.dp).fillMaxWidth().height(if (nameFocused) 2.dp else 1.dp)
                                .background(if (nameFocused) accent else dim.copy(alpha = 0.3f)),
                        )
                        if (error != null) {
                            Text(error, color = palette.danger.toComposeColor(), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = { create() },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = palette.onAccent.toComposeColor()),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = palette.onAccent.toComposeColor(), strokeWidth = 2.dp)
                        } else {
                            Icon(XnotesIcons.check, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.create), fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(color = border)

                // Body content: side-by-side on wide screens, stacked on narrow
                if (wide) {
                    val stageW = (screenW * 0.45f).coerceIn(340.dp, 560.dp)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        // Left: Preview
                        Box(
                            Modifier
                                .width(stageW)
                                .fillMaxHeight()
                                .background(palette.bg.toComposeColor())
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CanvasBackgroundPreview(
                                background = background,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp, max = 440.dp)
                                    .shadow(4.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, palette.paperBorder.toComposeColor(), RoundedCornerShape(8.dp)),
                            )
                        }

                        // Right: Controls
                        val scroll = rememberScrollState()
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(scroll)
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                        ) {
                            CanvasTuneControls(
                                editor = editor,
                                background = background,
                                saveAsDefault = saveAsDefault,
                                onSaveAsDefaultChange = { saveAsDefault = it },
                                onChange = { background = it },
                            )
                        }
                    }
                } else {
                    // Mobile: stacked column
                    val scroll = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(16.dp),
                    ) {
                        CanvasBackgroundPreview(
                            background = background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .shadow(3.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, palette.paperBorder.toComposeColor(), RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.height(20.dp))
                        CanvasTuneControls(
                            editor = editor,
                            background = background,
                            saveAsDefault = saveAsDefault,
                            onSaveAsDefaultChange = { saveAsDefault = it },
                            onChange = { background = it },
                        )
                    }
                }
            }
        }
    }
}

/** Interactive pattern, color, and spacing controls for canvas customization. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasTuneControls(
    editor: Editor,
    background: CanvasBackground,
    saveAsDefault: Boolean,
    onSaveAsDefaultChange: (Boolean) -> Unit,
    onChange: (CanvasBackground) -> Unit,
) {
    val palette = LocalPalette.current
    val dim = palette.textDim.toComposeColor()
    val text = palette.text.toComposeColor()
    val accent = palette.accent.toComposeColor()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.caption_pattern), color = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        TextButton(
            onClick = { onChange(CanvasBackground()) },
            colors = ButtonDefaults.textButtonColors(contentColor = accent),
        ) {
            Text(stringResource(R.string.reset), fontWeight = FontWeight.Medium)
        }
    }

    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip(stringResource(R.string.none), background.pattern == PagePattern.NONE) {
            onChange(background.copy(pattern = PagePattern.NONE))
        }
        ModeChip(stringResource(R.string.pattern_lines), background.pattern == PagePattern.LINES) {
            onChange(background.copy(pattern = PagePattern.LINES))
        }
        ModeChip(stringResource(R.string.pattern_dots), background.pattern == PagePattern.DOTS) {
            onChange(background.copy(pattern = PagePattern.DOTS))
        }
        ModeChip(stringResource(R.string.pattern_grid), background.pattern == PagePattern.GRID) {
            onChange(background.copy(pattern = PagePattern.GRID))
        }
    }

    if (background.pattern != PagePattern.NONE) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.caption_spacing), color = dim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Slider(
            value = background.clampedSpacing.toFloat(),
            onValueChange = { onChange(background.copy(spacing = it.toDouble())) },
            valueRange = PageStyle.MIN_SPACING.toFloat()..PageStyle.MAX_SPACING.toFloat(),
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
        )

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.caption_pattern_colour), color = dim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SwatchCircles(editor, SwatchRole.LINES, background.patternColor.copy(a = 255)) {
                onChange(background.copy(patternColor = it.copy(a = background.patternColor.a)))
            }
        }

        Spacer(Modifier.height(12.dp))
        val pct = background.patternColor.a * 100f / 255f
        Text(stringResource(R.string.caption_opacity_percent, pct.roundToInt()), color = dim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Slider(
            value = pct,
            onValueChange = { v ->
                val alpha = (v / 100f * 255f).roundToInt().coerceIn(0, 255)
                onChange(background.copy(patternColor = background.patternColor.copy(a = alpha)))
            },
            valueRange = 5f..100f,
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.caption_paper), color = dim, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ModeChip(stringResource(R.string.paper_theme), background.paperColor == null) {
            onChange(background.copy(paperColor = null))
        }
        SwatchCircles(editor, SwatchRole.PAPER, background.paperColor) {
            onChange(background.copy(paperColor = it))
        }
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = palette.border.toComposeColor())
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().offset(x = (-12).dp)) {
        Checkbox(checked = saveAsDefault, onCheckedChange = onSaveAsDefaultChange)
        Text(
            stringResource(R.string.default_for_new_canvases),
            color = text,
            fontSize = 13.sp,
        )
    }
}

/** Live vector rendering of the canvas background pattern. */
@Composable
private fun CanvasBackgroundPreview(
    background: CanvasBackground,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val paper = background.paperColor ?: palette.paper
    val patternColor = background.patternColor

    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawRect(color = paper.toComposeColor())

        val spacingPx = background.clampedSpacing.toFloat()
        if (spacingPx <= 0f || background.pattern == PagePattern.NONE) return@Canvas

        val strokeColor = patternColor.toComposeColor()
        val strokeWidth = 1.2.dp.toPx()
        val dotRadius = 1.5.dp.toPx()

        when (background.pattern) {
            PagePattern.LINES -> {
                var y = spacingPx
                while (y < size.height) {
                    drawLine(
                        color = strokeColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                    y += spacingPx
                }
            }
            PagePattern.GRID -> {
                var x = spacingPx
                while (x < size.width) {
                    drawLine(
                        color = strokeColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = strokeWidth,
                    )
                    x += spacingPx
                }
                var y = spacingPx
                while (y < size.height) {
                    drawLine(
                        color = strokeColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                    y += spacingPx
                }
            }
            PagePattern.DOTS -> {
                var x = spacingPx
                while (x < size.width) {
                    var y = spacingPx
                    while (y < size.height) {
                        drawCircle(
                            color = strokeColor,
                            radius = dotRadius,
                            center = Offset(x, y),
                        )
                        y += spacingPx
                    }
                    x += spacingPx
                }
            }
            PagePattern.NONE -> {}
        }
    }
}
