package com.mountzou.flytly.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R

val AtlasFont = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold)
)
private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = AtlasFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

val Typography = Typography(
    displayLarge = style(66, 72, FontWeight.SemiBold),
    displayMedium = style(54, 60, FontWeight.SemiBold),
    headlineLarge = style(34, 41, FontWeight.Bold),
    headlineMedium = style(26, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.Medium),
    titleMedium = style(17, 23, FontWeight.Medium),
    bodyLarge = style(17, 24), bodyMedium = style(15, 22), bodySmall = style(13, 19),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 18, FontWeight.Medium), labelSmall = style(10, 16, FontWeight.Medium)
)
