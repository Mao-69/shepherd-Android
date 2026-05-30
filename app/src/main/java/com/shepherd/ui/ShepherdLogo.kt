package com.shepherd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shepherd mark: a shepherd's crook rising through a soft guiding ring —
 * "leading awareness". Drawn vectorially so it scales crisply and themes
 * with the palette.
 */
@Composable
fun ShepherdLogo(size: Dp = 56.dp, accent: Color, ring: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val stroke = w * 0.085f

        // Outer guiding ring (slightly open at top, like a halo/aperture)
        drawArc(
            color = ring,
            startAngle = -65f,
            sweepAngle = 310f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round)
        )

        // The crook: a vertical staff that curls at the top
        val staffBottom = h * 0.80f
        val staffTop = h * 0.42f
        val crook = Path().apply {
            moveTo(cx, staffBottom)
            lineTo(cx, staffTop)
            // hook curling to the left then up
            cubicTo(
                cx, h * 0.28f,
                cx - w * 0.20f, h * 0.26f,
                cx - w * 0.18f, h * 0.40f
            )
        }
        drawPath(
            crook,
            brush = Brush.verticalGradient(listOf(accent, accent)),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // A small guiding point (the "flock"/focus dot) at lower right
        drawCircle(
            color = accent,
            radius = w * 0.05f,
            center = Offset(cx + w * 0.16f, staffBottom - h * 0.02f)
        )
    }
}
