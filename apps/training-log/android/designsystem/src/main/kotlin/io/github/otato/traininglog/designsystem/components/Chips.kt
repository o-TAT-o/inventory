package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otato.traininglog.designsystem.theme.AppTheme

/** 選べるチップ（部位・ギア・ルーティンの選択など）。高さ 44dp。選択中は淡い面と太字。 */
@Composable
fun AppChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(colors.container)
                else Modifier.border(BorderStroke(1.dp, colors.line), shape),
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AppTheme.typography.body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.onContainer else colors.fg,
        )
    }
}

/** 「合計」「片手」「左右計」「加重」などの、数値の横に付ける小さな印（P-4）。 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text,
        style = AppTheme.typography.caption.copy(fontSize = 11.sp),
        color = AppTheme.colors.sub,
        modifier = modifier
            .border(BorderStroke(1.dp, AppTheme.colors.line), shape)
            .padding(horizontal = 4.dp),
    )
}
