package com.classics.readingroom.ui


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 明亮纸白配色：浅底、深墨字、朱砂点缀，长时间阅读不压眼
val Paper = Color(0xFFFFFFFF)
val PaperStrong = Color(0xFFFFFFFF)
val Canvas = Color(0xFFFAF8F3)
val CanvasDeep = Color(0xFFF2EEE5)
val Ink = Color(0xFF23201B)
val InkSoft = Color(0xFF4A443B)
val Muted = Color(0xFF6F6759)
val Line = Color(0xFFE8E2D6)
val LineStrong = Color(0xFFD5CCBB)
val Cinnabar = Color(0xFFB4472F)
val CinnabarDeep = Color(0xFF8E3320)
val Jade = Color(0xFF2F6B5C)
val Gold = Color(0xFFB08A45)

val NightBg = Color(0xFF1C1A17)
val NightCard = Color(0xFF26231F)
val NightInk = Color(0xFFE8E0D0)
val NightMuted = Color(0xFFA1978A)

val SerifFamily = FontFamily.Serif
val SansFamily = FontFamily.SansSerif

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE9E2),
    onPrimaryContainer = CinnabarDeep,
    secondary = Jade,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F0EA),
    onSecondaryContainer = Color(0xFF1F4A3E),
    tertiary = Gold,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF4F1EA),
    onSurfaceVariant = Muted,
    outline = LineStrong,
    outlineVariant = Line,
    error = Cinnabar
)

// 夜间阅读模式（仅在阅读器里手动开启）
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0A08C),
    onPrimary = Color(0xFF3A1109),
    primaryContainer = Color(0xFF5B2A20),
    onPrimaryContainer = Color(0xFFF7DCD2),
    secondary = Color(0xFF9CC3B4),
    onSecondary = Color(0xFF10231D),
    background = NightBg,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF322E29),
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF5A534A),
    outlineVariant = Color(0xFF3A352F),
    error = Color(0xFFE2A093)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = SansFamily, fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = SansFamily, fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = SansFamily, fontSize = 10.sp, lineHeight = 14.sp)
)

data class Space(val page: Int = 16, val card: Int = 16, val gap: Int = 12)

val LocalSpace = staticCompositionLocalOf { Space() }

@Composable
fun ClassicsTheme(nightOverride: Boolean? = null, content: @Composable () -> Unit) {
    // 默认始终使用明亮主题；只有阅读器手动开启夜间模式时才切换
    val dark = nightOverride == true
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography
    ) {
        CompositionLocalProvider(LocalSpace provides Space()) {
            content()
        }
    }
}

val CardRadius = 14.dp
