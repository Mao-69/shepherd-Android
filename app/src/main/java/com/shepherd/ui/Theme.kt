package com.shepherd.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shepherd's visual identity: a calm, twilight palette.
 * Primary is a soft "shepherd's-lantern" amber-gold; secondary a dusk indigo.
 * Both dark and light schemes are first-class — toggled at runtime.
 */
object Brand {
    // Shared accents
    val Lantern   = Color(0xFFE9B66B)   // warm gold — primary
    val LanternHi = Color(0xFFF4C97E)
    val Indigo    = Color(0xFF7C8CF0)   // dusk indigo — secondary
    val Sage      = Color(0xFF8FD3B6)   // coherent / positive
    val Clay      = Color(0xFFE08A6E)   // alert

    // Dark (twilight)
    val NightBg   = Color(0xFF0C0E14)
    val NightCard = Color(0xFF161924)
    val NightHi   = Color(0xFF1F2433)
    val NightLine = Color(0xFF2A3042)
    val NightText = Color(0xFFEDEFF6)
    val NightMid  = Color(0xFF9AA0B5)
    val NightDim  = Color(0xFF5A6075)

    // Light (dawn)
    val DayBg     = Color(0xFFF6F4EF)
    val DayCard   = Color(0xFFFFFFFF)
    val DayHi     = Color(0xFFEFEBE2)
    val DayLine   = Color(0xFFE0DACE)
    val DayText   = Color(0xFF1E2230)
    val DayMid    = Color(0xFF5E6377)
    val DayDim    = Color(0xFF9A9DAA)
}

private val DarkScheme = darkColorScheme(
    primary = Brand.Lantern, onPrimary = Brand.NightBg,
    secondary = Brand.Indigo, tertiary = Brand.Sage,
    background = Brand.NightBg, surface = Brand.NightCard,
    surfaceVariant = Brand.NightHi, outline = Brand.NightLine,
    error = Brand.Clay, onBackground = Brand.NightText, onSurface = Brand.NightText,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB7853A), onPrimary = Color.White,
    secondary = Color(0xFF4A57C4), tertiary = Color(0xFF3E8E6B),
    background = Brand.DayBg, surface = Brand.DayCard,
    surfaceVariant = Brand.DayHi, outline = Brand.DayLine,
    error = Color(0xFFC75A3C), onBackground = Brand.DayText, onSurface = Brand.DayText,
)

/** Convenience accessors that resolve per-theme palette tokens in composables. */
class Palette(val dark: Boolean) {
    val bg     get() = if (dark) Brand.NightBg   else Brand.DayBg
    val card   get() = if (dark) Brand.NightCard else Brand.DayCard
    val hi     get() = if (dark) Brand.NightHi   else Brand.DayHi
    val line   get() = if (dark) Brand.NightLine else Brand.DayLine
    val text   get() = if (dark) Brand.NightText else Brand.DayText
    val mid    get() = if (dark) Brand.NightMid  else Brand.DayMid
    val dim    get() = if (dark) Brand.NightDim  else Brand.DayDim
    val accent get() = if (dark) Brand.Lantern   else Color(0xFFB7853A)
    val sage   get() = if (dark) Brand.Sage      else Color(0xFF3E8E6B)
    val clay   get() = if (dark) Brand.Clay      else Color(0xFFC75A3C)
    val indigo get() = if (dark) Brand.Indigo    else Color(0xFF4A57C4)
}

@Composable
fun ShepherdTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
