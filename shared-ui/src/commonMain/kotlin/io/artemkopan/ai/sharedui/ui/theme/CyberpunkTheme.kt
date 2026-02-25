package io.artemkopan.ai.sharedui.ui.theme

import aiassistant.shared_ui.generated.resources.Res
import aiassistant.shared_ui.generated.resources.inter_regular
import aiassistant.shared_ui.generated.resources.jetbrains_mono_regular
import aiassistant.shared_ui.generated.resources.noto_emoji_regular
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkColors.Yellow,
    onPrimary = Color.Black,
    secondary = CyberpunkColors.Cyan,
    onSecondary = Color.Black,
    tertiary = CyberpunkColors.NeonGreen,
    background = CyberpunkColors.DarkBackground,
    onBackground = CyberpunkColors.TextPrimary,
    surface = CyberpunkColors.SurfaceDark,
    onSurface = CyberpunkColors.TextPrimary,
    surfaceVariant = CyberpunkColors.CardDark,
    onSurfaceVariant = CyberpunkColors.TextSecondary,
    outline = CyberpunkColors.BorderDark,
    error = CyberpunkColors.Red,
    onError = Color.White,
)

private val CyberpunkTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun CyberpunkTheme(content: @Composable () -> Unit) {
    val emojiFont = Font(Res.font.noto_emoji_regular)
    val interFont = Font(Res.font.inter_regular)
    val jetbrainsMonoFont = Font(Res.font.jetbrains_mono_regular)

    val typography = remember(interFont, jetbrainsMonoFont, emojiFont) {
        val sansEmoji = FontFamily(interFont, emojiFont)
        val monoEmoji = FontFamily(jetbrainsMonoFont, emojiFont)
        CyberpunkTypography.copy(
            bodyLarge = CyberpunkTypography.bodyLarge.copy(fontFamily = sansEmoji),
            bodyMedium = CyberpunkTypography.bodyMedium.copy(fontFamily = sansEmoji),
            bodySmall = CyberpunkTypography.bodySmall.copy(fontFamily = monoEmoji),
        )
    }

    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = typography,
        content = content,
    )
}
