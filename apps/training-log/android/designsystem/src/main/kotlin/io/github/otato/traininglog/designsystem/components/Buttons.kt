package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.theme.AppTheme

private val ButtonShape = RoundedCornerShape(16.dp)

/** 主ボタン（塗りつぶし）。親指で押しやすいよう、高さは 56dp 以上。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    textStyle: TextStyle = AppTheme.typography.button,
    horizontalPadding: Dp = 20.dp,
) {
    val colors = AppTheme.colors
    ButtonBody(
        text = text,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
        textStyle = textStyle,
        horizontalPadding = horizontalPadding,
        contentColor = colors.onPrimary,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(ButtonShape)
            .background(colors.primary),
    )
}

/** 副ボタン（枠だけ）。 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    textStyle: TextStyle = AppTheme.typography.button,
    horizontalPadding: Dp = 20.dp,
) {
    val colors = AppTheme.colors
    ButtonBody(
        text = text,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
        textStyle = textStyle,
        horizontalPadding = horizontalPadding,
        contentColor = colors.fg,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(ButtonShape)
            .border(BorderStroke(1.5.dp, colors.line), ButtonShape),
    )
}

/** 無効なボタンの、文字とアイコンの濃さ。 */
private const val DISABLED_CONTENT_ALPHA = 0.4f

@Composable
private fun ButtonBody(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector?,
    enabled: Boolean,
    textStyle: TextStyle,
    horizontalPadding: Dp,
    contentColor: Color,
    modifier: Modifier,
) {
    // 無効のときの薄さは、文字とアイコンの色の濃さだけで表す。
    // Modifier.alpha を、有効かどうかで付けたり外したりすると、無効から有効に変わった瞬間に、
    // ボタン全体（背景も文字も）が描かれなくなることがあった（種目の追加の「保存」が見えなくなる不具合）。
    // 修飾（Modifier）の並びは、状態が変わっても同じにしておく。
    val shownColor = if (enabled) contentColor else contentColor.copy(alpha = DISABLED_CONTENT_ALPHA)
    Row(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = shownColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = textStyle, color = shownColor, textAlign = TextAlign.Center)
    }
}

/** 丸いアイコンボタン（48dp）。 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.fg,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** 小さな文字のボタン（「−10 秒」「＋30 秒」など）。タップできる大きさ（高さ 44dp）は確保する。 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.primary,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.typography.bodyStrong, color = color)
    }
}
