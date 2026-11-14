package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme

/** アプリバー。高さ 56dp。戻るボタンは [onBack] があるときだけ出す。右側に操作を置ける。 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            AppIconButton(AppIcons.Back, "戻る", onBack)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = AppTheme.typography.title,
            color = AppTheme.colors.fg,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        actions()
    }
}

/** セクションの小さな見出し（「前回のワークアウト」など）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.typography.label, color = AppTheme.colors.sub, modifier = modifier)
}

/** カード（白い面と枠線）。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(BorderStroke(1.dp, AppTheme.colors.line), shape),
        content = content,
    )
}

/** 区切り線。 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = AppTheme.colors.line)
}

/** 設定などの、1 行（題名・補足・右側の操作）。タップできる行は [onClick] を渡す。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg)
            if (subtitle != null) {
                Text(subtitle, style = AppTheme.typography.caption, color = AppTheme.colors.sub)
            }
        }
        trailing()
    }
}

/** 下のタブ（今日 / ルーティン / 履歴）。選んだものは、淡い丸い面の中に太字で出す。高さ 72dp。 */
@Composable
fun AppBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.background(colors.surface)) {
        AppDivider()
        Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clickable(role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .then(if (selected) Modifier.background(colors.container) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (selected) colors.onContainer else colors.sub,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        style = AppTheme.typography.caption,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) colors.fg else colors.sub,
                    )
                }
            }
        }
    }
}

data class BottomBarItem(val label: String, val icon: ImageVector)
