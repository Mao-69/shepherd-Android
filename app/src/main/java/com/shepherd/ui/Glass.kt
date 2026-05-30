package com.shepherd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Glassmorphism kit. Cards are semi-transparent frosted panels that float over a
 * fixed gradient; the top bar is transparent so content scrolls beneath it.
 *
 * Note: Compose can't do true backdrop blur cheaply across all API levels, so the
 * "frost" is simulated with a translucent fill + a soft top-edge highlight + a
 * hairline border — the standard, performant approach for glass UIs on Android.
 */

/** The fixed gradient that everything floats over. */
@Composable
fun GradientBackground(pal: Palette, content: @Composable BoxScope.() -> Unit) {
    val colors = if (pal.dark)
        listOf(Color(0xFF0A0C13), Color(0xFF12161F), Color(0xFF0E1A1C))
    else
        listOf(Color(0xFFF7F4EC), Color(0xFFEFEDE6), Color(0xFFE9EEEA))
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = colors,
                start = Offset(0f, 0f),
                end = Offset(900f, 1800f),
            )
        ),
        content = content
    )
}

/** Glass fill brush + border color for the current theme. */
object Glass {
    fun fill(pal: Palette): Brush = if (pal.dark)
        Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0DFFFFFF)))
    else
        Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x99FFFFFF)))

    fun border(pal: Palette): Color =
        if (pal.dark) Color(0x33FFFFFF) else Color(0x55FFFFFF)

    fun strong(pal: Palette): Brush = if (pal.dark)
        Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF)))
    else
        Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB3FFFFFF)))
}

/** A frosted glass card. */
@Composable
fun GlassCard(
    pal: Palette,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    radius: Int = 20,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(if (strong) Glass.strong(pal) else Glass.fill(pal))
            .border(1.dp, Glass.border(pal), RoundedCornerShape(radius.dp))
            .padding(16.dp),
        content = content
    )
}

/** Top bar with fixed Shepherd branding. [scrolledAlpha] (0..1) fades the backing
 *  from transparent (at rest) to near-opaque (once content scrolls under it). */
@Composable
fun GlassTopBar(pal: Palette, scrolledAlpha: Float = 0f, trailing: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth()
            .background(pal.bg.copy(alpha = 0.97f * scrolledAlpha))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .statusBarsPaddingCompat()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShepherdLogo(size = 36.dp, accent = pal.accent, ring = pal.indigo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Shepherd", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp)
                Text("Guided CRV Sessions", color = pal.mid, fontSize = 12.sp)
            }
            trailing?.invoke()
        }
        // hairline divider appears with the backing
        Box(Modifier.fillMaxWidth().height(1.dp)
            .background(Glass.border(pal).copy(alpha = scrolledAlpha)))
    }
}

/** Simple status-bar inset without extra deps (approximate, safe across devices). */
@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier =
    this.then(Modifier.padding(top = 28.dp))

/* ---- Bottom navigation ---- */

enum class Tab(val label: String) { SESSION("Session"), TARGETS("Targets"), MODES("Modes"), SETTINGS("Settings") }

@Composable
fun GlassBottomNav(pal: Palette, current: Tab, scrolledAlpha: Float = 0f,
                   onSelect: (Tab) -> Unit, iconFor: (Tab) -> ImageVector) {
    // Base: light glass at rest; fades toward solid card as content scrolls under it.
    val base = Glass.strong(pal)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(base)
            .background(pal.card.copy(alpha = 0.98f * scrolledAlpha))
            .border(1.dp, Glass.border(pal), RoundedCornerShape(22.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { tab ->
            val selected = tab == current
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (selected) pal.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(tab), tab.label,
                        tint = if (selected) pal.accent else pal.mid,
                        modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(tab.label, fontSize = 10.sp,
                    color = if (selected) pal.accent else pal.mid,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Clickable without the ripple bleeding outside the rounded shape. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
