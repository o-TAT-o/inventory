package io.github.otato.traininglog.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }
private val LocalAppTypography = staticCompositionLocalOf { AppTypography() }

/** 画面のコードが使う入口。`AppTheme.colors.primary` のように、トークンの名前で取る。 */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
}

/**
 * アプリのテーマ。[darkTheme] が null なら端末の設定に合わせる（設定の「ライト / ダーク / 端末に合わせる」）。
 */
@Composable
fun TrainingLogTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    val colors = if (dark) DarkAppColors else LightAppColors
    val typography = AppTypography()
    val material = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.container,
            onPrimaryContainer = colors.onContainer,
            background = colors.bg,
            onBackground = colors.fg,
            surface = colors.surface,
            onSurface = colors.fg,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.sub,
            outline = colors.line,
            outlineVariant = colors.line,
            error = colors.error,
            inverseSurface = colors.inverse,
            inverseOnSurface = colors.onInverse,
            inversePrimary = colors.inverseAction,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.container,
            onPrimaryContainer = colors.onContainer,
            background = colors.bg,
            onBackground = colors.fg,
            surface = colors.surface,
            onSurface = colors.fg,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.sub,
            outline = colors.line,
            outlineVariant = colors.line,
            error = colors.error,
            inverseSurface = colors.inverse,
            inverseOnSurface = colors.onInverse,
            inversePrimary = colors.inverseAction,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
    ) {
        MaterialTheme(colorScheme = material, typography = typography.toMaterial(), content = content)
    }
}
