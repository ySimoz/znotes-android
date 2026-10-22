package com.xnotes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.settings.ToolbarLook
import com.xnotes.settings.ToolbarPosition
import com.xnotes.settings.ToolbarSize
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** The toolbar look from Preferences, provided once above both panes. */
val LocalToolbarLook = staticCompositionLocalOf { ToolbarLook() }

/** What every toolbar widget sizes itself by, and which way the bar runs. */
internal data class BarMetrics(
    val button: Dp,
    val circle: Dp,
    val icon: Dp,
    val swatch: Dp,
    val rule: Dp,
    val label: TextUnit,
    val vertical: Boolean = false,
) {
    /** Across the bar: one button with a little air either side. */
    val thickness: Dp get() = button + 8.dp
}

internal fun barMetrics(size: ToolbarSize): BarMetrics = when (size) {
    ToolbarSize.COMPACT -> BarMetrics(36.dp, 30.dp, 20.dp, 24.dp, 22.dp, 11.sp)
    ToolbarSize.REGULAR -> BarMetrics(42.dp, 34.dp, 22.dp, 28.dp, 26.dp, 12.sp)
    ToolbarSize.COMFORTABLE -> BarMetrics(50.dp, 40.dp, 26.dp, 34.dp, 30.dp, 13.sp)
}

internal val LocalBar = staticCompositionLocalOf { barMetrics(ToolbarSize.REGULAR) }

/** Where a menu opens from its anchor; a side rail sets it so its menus open beside the rail. */
internal val LocalMenuOffset = compositionLocalOf { DpOffset.Zero }

/** How far a floating bar sits in from the canvas edge. */
private val FLOAT_MARGIN = 8.dp

/** What a floating bar covers of the canvas, for overlays that must stay out from under it. */
internal val LocalToolbarCover = compositionLocalOf { PaddingValues(0.dp) }

/** Callback to collapse the toolbar from its pinned collapse button. */
internal val LocalToolbarCollapse = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Minimal floating pill button displayed when the toolbar is collapsed, allowing the user
 * to expand it back with a single tap.
 */
@Composable
internal fun ToolbarExpandPill(
    position: ToolbarPosition,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val expandIcon = when (position) {
        ToolbarPosition.TOP -> XnotesIcons.chevronDown
        ToolbarPosition.BOTTOM -> XnotesIcons.chevronUp
        ToolbarPosition.LEFT -> XnotesIcons.chevronRight
        ToolbarPosition.RIGHT -> XnotesIcons.chevronLeft
    }
    Box(
        modifier = modifier
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(palette.panel.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), CircleShape)
            .clickable(onClick = onExpand)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = expandIcon,
            contentDescription = stringResource(R.string.show_toolbar),
            tint = palette.text.toComposeColor(),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Lays a pane out with its [bar] along the edge Preferences chose and [content] in the rest. A
 * floating bar is handed to [content] to lay over its canvas instead, and [onCover] learns how many
 * px of each edge (left, top, right, bottom) it covers so the canvas keeps its pages clear of it.
 *
 * Supports smooth, GPU-accelerated fading animation for auto-hide and manual collapse via [visible]
 * and [onToggleVisible].
 */
@Composable
internal fun ColumnScope.ToolbarAround(
    bar: @Composable () -> Unit,
    visible: Boolean = true,
    onToggleVisible: () -> Unit = {},
    onCover: (Double, Double, Double, Double) -> Unit,
    content: @Composable (floatingBar: @Composable BoxScope.() -> Unit) -> Unit,
) {
    val look = LocalToolbarLook.current
    val rest = Modifier.weight(1f).fillMaxWidth()
    val triggerEdge = when (look.position) {
        ToolbarPosition.TOP -> Alignment.TopEnd
        ToolbarPosition.BOTTOM -> Alignment.BottomEnd
        ToolbarPosition.LEFT -> AbsoluteAlignment.TopLeft
        ToolbarPosition.RIGHT -> AbsoluteAlignment.TopRight
    }

    if (!look.floating) {
        SideEffect { onCover(0.0, 0.0, 0.0, 0.0) }
        val docked: @Composable BoxScope.() -> Unit = {
            androidx.compose.animation.AnimatedVisibility(
                visible = !visible,
                modifier = Modifier.align(triggerEdge).padding(FLOAT_MARGIN),
                enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(150, easing = FastOutLinearInEasing)),
            ) {
                ToolbarExpandPill(look.position, onToggleVisible)
            }
        }
        val across = Arrangement.Absolute.Left
        val barAnimated: @Composable () -> Unit = {
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)) +
                        (if (look.position.vertical) expandHorizontally(tween(200, easing = FastOutSlowInEasing))
                         else expandVertically(tween(200, easing = FastOutSlowInEasing))),
                exit = fadeOut(tween(150, easing = FastOutLinearInEasing)) +
                       (if (look.position.vertical) shrinkHorizontally(tween(180, easing = FastOutSlowInEasing))
                        else shrinkVertically(tween(180, easing = FastOutSlowInEasing))),
            ) {
                CompositionLocalProvider(LocalToolbarCollapse provides onToggleVisible) {
                    bar()
                }
            }
        }
        when (look.position) {
            ToolbarPosition.TOP -> { barAnimated(); Box(rest) { content(docked) } }
            ToolbarPosition.BOTTOM -> { Box(rest) { content(docked) }; barAnimated() }
            ToolbarPosition.LEFT -> Row(rest, across) { barAnimated(); Box(Modifier.weight(1f).fillMaxHeight()) { content(docked) } }
            ToolbarPosition.RIGHT -> Row(rest, across) { Box(Modifier.weight(1f).fillMaxHeight()) { content(docked) }; barAnimated() }
        }
        return
    }

    val depth = barMetrics(look.size).thickness + FLOAT_MARGIN
    val px = with(LocalDensity.current) { depth.toPx().toDouble() }
    val position = look.position
    SideEffect {
        val actualPx = if (visible) px else 0.0
        onCover(
            if (position == ToolbarPosition.LEFT) actualPx else 0.0,
            if (position == ToolbarPosition.TOP) actualPx else 0.0,
            if (position == ToolbarPosition.RIGHT) actualPx else 0.0,
            if (position == ToolbarPosition.BOTTOM) actualPx else 0.0,
        )
    }
    val (cover, edge) = when (position) {
        ToolbarPosition.TOP -> (if (visible) PaddingValues(top = depth) else PaddingValues(0.dp)) to Alignment.TopCenter
        ToolbarPosition.BOTTOM -> (if (visible) PaddingValues(bottom = depth) else PaddingValues(0.dp)) to Alignment.BottomCenter
        ToolbarPosition.LEFT -> (if (visible) PaddingValues.Absolute(left = depth) else PaddingValues(0.dp)) to AbsoluteAlignment.CenterLeft
        ToolbarPosition.RIGHT -> (if (visible) PaddingValues.Absolute(right = depth) else PaddingValues(0.dp)) to AbsoluteAlignment.CenterRight
    }
    CompositionLocalProvider(LocalToolbarCover provides cover) {
        Box(rest) {
            content {
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    modifier = Modifier.align(edge).padding(FLOAT_MARGIN),
                    enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(tween(150, easing = FastOutLinearInEasing)),
                ) {
                    CompositionLocalProvider(LocalToolbarCollapse provides onToggleVisible) {
                        bar()
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !visible,
                    modifier = Modifier.align(triggerEdge).padding(FLOAT_MARGIN),
                    enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(tween(150, easing = FastOutLinearInEasing)),
                ) {
                    ToolbarExpandPill(look.position, onToggleVisible)
                }
            }
        }
    }
}

/**
 * The strip both toolbars are laid out in: a row along the top or bottom, a column down either
 * side. [content] runs along it and scrolls when it runs long, [trailing] stays pinned at the end,
 * and [armed] is the tool the gliding circle sits under.
 */
@Composable
internal fun ToolbarFrame(
    armed: Any?,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val look = LocalToolbarLook.current
    val bar = barMetrics(look.size).copy(vertical = look.position.vertical)
    val glide = remember { ToolGlide() }
    val palette = LocalPalette.current
    val shape = MaterialTheme.shapes.extraLarge
    val onCollapse = LocalToolbarCollapse.current
    // Docked, the strip fills its edge; floating, a pill only as long as its tools, up to that edge.
    val surface = if (!look.floating) {
        Modifier.background(palette.panel.toComposeColor())
    } else {
        Modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(palette.panel.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), shape)
    }
    val stretch = when {
        look.floating -> Modifier
        bar.vertical -> Modifier.fillMaxHeight()
        else -> Modifier.fillMaxWidth()
    }
    // Material places a menu below its anchor; shifting it one bar across and one button up puts it
    // beside the rail, and on a right rail material mirrors it to the left side on its own.
    val menuOffset = if (bar.vertical) DpOffset(bar.thickness, -bar.button) else DpOffset.Zero
    val tipSide = when (look.position) {
        ToolbarPosition.TOP -> TipSide.BELOW
        ToolbarPosition.BOTTOM -> TipSide.ABOVE
        ToolbarPosition.LEFT -> TipSide.RIGHT
        ToolbarPosition.RIGHT -> TipSide.LEFT
    }
    val collapseIcon = when (look.position) {
        ToolbarPosition.TOP -> XnotesIcons.chevronUp
        ToolbarPosition.BOTTOM -> XnotesIcons.chevronDown
        ToolbarPosition.LEFT -> XnotesIcons.chevronLeft
        ToolbarPosition.RIGHT -> XnotesIcons.chevronRight
    }
    val collapseButton: (@Composable () -> Unit)? = if (onCollapse != null) {
        {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(bar.button),
            ) {
                Icon(
                    imageVector = collapseIcon,
                    contentDescription = stringResource(R.string.hide_toolbar),
                    tint = palette.textDim.toComposeColor(),
                    modifier = Modifier.size(bar.icon),
                )
            }
        }
    } else null

    CompositionLocalProvider(LocalToolGlide provides glide, LocalBar provides bar, LocalMenuOffset provides menuOffset, LocalTipSide provides tipSide) {
        if (bar.vertical) {
            Column(
                stretch.width(bar.thickness).then(surface),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .weight(1f, fill = !look.floating)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                        .toolGlide(glide, armed),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { content() }
                collapseButton?.invoke()
                trailing?.invoke()
            }
        } else {
            Row(
                stretch.height(bar.thickness).then(surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f, fill = !look.floating)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                        .toolGlide(glide, armed),
                    verticalAlignment = Alignment.CenterVertically,
                ) { content() }
                collapseButton?.invoke()
                trailing?.invoke()
            }
        }
    }
}
