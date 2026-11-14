package io.github.otato.traininglog.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 色のトークン。デザイン（design/canvas の C14_tokens）の CSS 変数と、名前も値も合わせてある。
 * 色は、画面のコードに直接書かず、必ずここから取る。
 */
@Immutable
data class AppColors(
    /** 画面の背景 */
    val bg: Color,
    /** カード・パネルの面 */
    val surface: Color,
    /** 一段くぼんだ面（± ボタンなど） */
    val surfaceVariant: Color,
    /** 文字 */
    val fg: Color,
    /** 補助の文字 */
    val sub: Color,
    /** 主役の色（主ボタン） */
    val primary: Color,
    val onPrimary: Color,
    /** 選択中などの、淡い面 */
    val container: Color,
    val onContainer: Color,
    /** 区切り線・枠 */
    val line: Color,
    val error: Color,
    /** 逆色の面（スナックバー） */
    val inverse: Color,
    val onInverse: Color,
    val inverseAction: Color,
    /** 自己ベストの印 */
    val pb: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    bg = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4E9F0),
    fg = Color(0xFF141A22),
    sub = Color(0xFF4D5766),
    primary = Color(0xFF1F3A5F),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFD6E3F5),
    onContainer = Color(0xFF0D2440),
    line = Color(0xFFC5CCD6),
    error = Color(0xFFBA1A1A),
    inverse = Color(0xFF2B3340),
    onInverse = Color(0xFFF1F3F7),
    inverseAction = Color(0xFFA9C7F0),
    pb = Color(0xFFB26A00),
    isDark = false,
)

val DarkAppColors = AppColors(
    bg = Color(0xFF0F1318),
    surface = Color(0xFF181D24),
    surfaceVariant = Color(0xFF242B34),
    fg = Color(0xFFE4E8EE),
    sub = Color(0xFF9DA7B5),
    primary = Color(0xFFA9C7F0),
    onPrimary = Color(0xFF0B2748),
    container = Color(0xFF263B56),
    onContainer = Color(0xFFD6E3F5),
    line = Color(0xFF38414D),
    error = Color(0xFFFFB4AB),
    inverse = Color(0xFFE4E8EE),
    onInverse = Color(0xFF141A22),
    inverseAction = Color(0xFF1F3A5F),
    pb = Color(0xFFFFB95C),
    isDark = true,
)
