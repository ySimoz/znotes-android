package com.xnotes.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/**
 * Fullscreen launch screen for zNotes: displays the icon and styled zNotes wordmark
 * on a dark background while documents and state restore, then cleanly fades out.
 */
@Composable
fun XnotesLoader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appIcon = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap()
        }.getOrNull()
    }
    val infiniteTransition = rememberInfiniteTransition(label = "loader_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )

    Box(
        modifier.fillMaxSize().background(Color(0xFF0A0B0E)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (appIcon != null) {
                Image(appIcon, contentDescription = "zNotes", modifier = Modifier.size(80.dp))
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF1DAEF5), fontWeight = FontWeight.ExtraBold)) {
                        append("z")
                    }
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                        append("Notes")
                    }
                },
                fontSize = 36.sp,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "$ loading",
                color = Color(0xFF1DAEF5).copy(alpha = alpha),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}
