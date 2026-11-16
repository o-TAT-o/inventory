package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme

/**
 * 1 つを選ぶカード（「ふつうに記録 / あとから入力」「どのフォームでやりますか？」など）。
 * 選んでいるものは淡い面と塗りつぶしの丸、選んでいないものは枠だけ。説明は [lines] に 1 行ずつ。
 */
@Composable
fun AppRadioCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lines: List<String> = emptyList(),
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(colors.container)
                else Modifier.border(BorderStroke(1.5.dp, colors.line), shape),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioMark(selected, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.typography.heading,
                color = if (selected) colors.onContainer else colors.fg,
            )
            lines.forEach { line ->
                Text(line, style = AppTheme.typography.caption, color = if (selected) colors.onContainer else colors.sub)
            }
        }
    }
}

/** ラジオボタンの丸。選んでいるときは、枠の中に塗りつぶしの丸。 */
@Composable
private fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(28.dp)
            .border(BorderStroke(2.dp, if (selected) colors.primary else colors.sub), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(16.dp).background(colors.primary, CircleShape))
    }
}

/**
 * 画面の上に差し込む帯（「並べ替え中　指を離すと、そこに置きます」「あとから入力　タイマーなし　[ふつうに記録]」）。
 * [actionText] があるときは、右端に文字のボタンを出す。
 */
@Composable
fun AppBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container)
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = 16.dp, end = if (actionText != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = AppTheme.typography.bodyStrong, color = colors.onContainer, fontWeight = FontWeight.Bold)
        if (detail != null) {
            Spacer(Modifier.width(12.dp))
            Text(detail, style = AppTheme.typography.bodyStrong, color = colors.onContainer, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (actionText != null) TextAction(actionText, onClick = onAction, color = colors.onContainer)
    }
}

/**
 * 下からのシートの見出し。名前（と説明）の右に、閉じる ⌄ を置く。
 * シートの上の取っ手の下に出る。
 */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = AppTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AppTheme.typography.title, color = colors.fg)
            if (subtitle != null) Text(subtitle, style = AppTheme.typography.caption, color = colors.sub)
        }
        AppIconButton(AppIcons.Down, "とじる", onClose)
    }
}
