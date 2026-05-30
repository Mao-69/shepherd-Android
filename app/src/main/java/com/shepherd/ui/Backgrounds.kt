package com.shepherd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Background system. Built-in designs are drawn procedurally (so they scale to any
 * screen and theme) or from a bundled image; users can also pick their own photo
 * from the gallery (stored as a content URI string).
 *
 * The chosen background is identified by a string key persisted in prefs:
 *   "gradient", "geometric", "aurora", "mesh", "starfield"  → built-in
 *   "custom:<uri>"                                          → user image
 */

enum class BgKind(val key: String, val label: String) {
    GRADIENT("gradient", "Twilight gradient"),
    GEOMETRIC("geometric", "Geometric (blue)"),
    AURORA("aurora", "Aurora"),
    MESH("mesh", "Mesh glow"),
    STARFIELD("starfield", "Starfield");

    companion object {
        fun fromKey(k: String?): BgKind? = entries.firstOrNull { it.key == k }
    }
}

object Backgrounds {
    val builtins = BgKind.entries.toList()
}

/**
 * Renders the selected background filling the box, then places [content] over it.
 * [selection] is a BgKind.key or "custom:<uri>".
 */
@Composable
fun AppBackground(
    pal: Palette,
    selection: String,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            selection.startsWith("custom:") -> {
                val path = selection.removePrefix("custom:")
                val bmp = remember(path) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
                    }.getOrNull()
                }
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // Scrim so glass cards stay legible over arbitrary photos.
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(pal.bg.copy(alpha = 0.30f), pal.bg.copy(alpha = 0.55f))
                        )))
                } else {
                    GradientFill(pal)
                }
            }
            selection == BgKind.GEOMETRIC.key -> GeometricBackground(pal)
            selection == BgKind.AURORA.key -> AuroraBackground(pal)
            selection == BgKind.MESH.key -> MeshBackground(pal)
            selection == BgKind.STARFIELD.key -> StarfieldBackground(pal)
            else -> GradientFill(pal)
        }
        content()
    }
}

@Composable
private fun GradientFill(pal: Palette) {
    val colors = if (pal.dark)
        listOf(Color(0xFF0A0C13), Color(0xFF12161F), Color(0xFF0E1A1C))
    else
        listOf(Color(0xFFF7F4EC), Color(0xFFEFEDE6), Color(0xFFE9EEEA))
    Box(Modifier.fillMaxSize().background(
        Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(900f, 1800f))
    ))
}

/** Bundled image preset (the uploaded geometric design). */
@Composable
private fun GeometricBackground(pal: Palette) {
    val img = androidx.compose.ui.res.painterResource(
        id = com.shepherd.R.drawable.bg_geometric
    )
    Image(painter = img, contentDescription = null,
        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    // light scrim to seat glass cards
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0x33000000), Color(0x55000000)))
    ))
}

/** Soft diagonal "aurora" bands. */
@Composable
private fun AuroraBackground(pal: Palette) {
    val base = if (pal.dark) Color(0xFF0A0E14) else Color(0xFFF2F4F2)
    Box(Modifier.fillMaxSize().background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val bands = listOf(
                pal.indigo.copy(alpha = if (pal.dark) 0.30f else 0.18f) to 0.18f,
                pal.sage.copy(alpha = if (pal.dark) 0.22f else 0.14f) to 0.45f,
                pal.accent.copy(alpha = if (pal.dark) 0.20f else 0.12f) to 0.72f,
            )
            bands.forEach { (c, yFrac) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(c, Color.Transparent),
                        center = Offset(w * 0.5f, h * yFrac),
                        radius = w * 0.9f
                    ),
                    radius = w * 0.9f,
                    center = Offset(w * 0.5f, h * yFrac),
                )
            }
        }
    }
}

/** Triangulated "mesh" glow with subtle lines, evoking the uploaded style. */
@Composable
private fun MeshBackground(pal: Palette) {
    val base = if (pal.dark) Color(0xFF0B0F16) else Color(0xFFEFF1EE)
    val line = if (pal.dark) pal.indigo.copy(alpha = 0.20f) else pal.indigo.copy(alpha = 0.12f)
    Box(Modifier.fillMaxSize().background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // glow
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(pal.accent.copy(alpha = if (pal.dark) 0.18f else 0.10f), Color.Transparent),
                    center = Offset(w * 0.25f, h * 0.30f), radius = w * 0.7f),
                radius = w * 0.7f, center = Offset(w * 0.25f, h * 0.30f))
            // diagonal hairlines (like the reference image)
            val step = w / 7f
            for (i in -3..10) {
                val x = i * step
                drawLine(line, Offset(x, 0f), Offset(x + h, h), strokeWidth = 1.5f, cap = StrokeCap.Round)
            }
            // a few accent squares
            val sq = listOf(
                Triple(0.22f, 0.28f, 0.10f), Triple(0.6f, 0.4f, 0.06f), Triple(0.45f, 0.62f, 0.05f)
            )
            sq.forEach { (xf, yf, s) ->
                rotate(45f, pivot = Offset(w * xf, h * yf)) {
                    drawRect(
                        brush = Brush.linearGradient(listOf(pal.accent, pal.indigo)),
                        topLeft = Offset(w * xf - w * s / 2, h * yf - w * s / 2),
                        size = androidx.compose.ui.geometry.Size(w * s, w * s),
                    )
                }
            }
        }
    }
}

/** Calm starfield for meditative sessions. */
@Composable
private fun StarfieldBackground(pal: Palette) {
    val base = if (pal.dark) Color(0xFF06070D) else Color(0xFFEDEFF4)
    val star = if (pal.dark) Color(0xFFAAB7D8) else Color(0xFF6A7390)
    val seeds = remember { List(120) { Triple(rnd(), rnd(), rnd()) } }
    Box(Modifier.fillMaxSize().background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(pal.indigo.copy(alpha = if (pal.dark) 0.22f else 0.10f), Color.Transparent),
                    center = Offset(w * 0.7f, h * 0.25f), radius = w * 0.8f),
                radius = w * 0.8f, center = Offset(w * 0.7f, h * 0.25f))
            seeds.forEach { (x, y, r) ->
                drawCircle(star.copy(alpha = 0.3f + r * 0.6f),
                    radius = 0.6f + r * 1.8f, center = Offset(w * x, h * y))
            }
        }
    }
}

private fun rnd(): Float = kotlin.random.Random.nextFloat()
