package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.theme.AppTheme

/**
 * 切り替え（セグメント）。「ルーティン | 種目」「最大重量 | 推定 1RM | ボリューム」のように、いくつかから 1 つを選ぶ。
 * 高さ 48dp。選んでいるものは、淡い面と太字。
 */
@Composable
fun AppSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(BorderStroke(1.5.dp, colors.line), shape),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .then(if (selected) Modifier.background(colors.container) else Modifier)
                    .clickable(role = Role.Tab) {
                        // 入力欄のフォーカスを外して、キーボードをしまう（ほかの押せる部品と同じ）
                        focusManager.clearFocus()
                        onSelect(index)
                    }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = AppTheme.typography.body,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) colors.onContainer else colors.fg,
                )
            }
        }
    }
}
