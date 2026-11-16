package io.github.otato.traininglog.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.components.AppRadioCard
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.SheetHeader
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme

/**
 * フォーム（バリエーション）を選ぶシート（F-RT-10、F-EX-16）。
 * 前回使ったフォームが最初から選ばれていて、そのまま「このフォームで始める」を押せる。
 * 初めて使うフォームには、同じ種目のほかのフォームの直近の記録を、目安として添える。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    ui: FormSheetUi,
    onChoose: (exerciseId: String) -> Unit,
    onConfirm: () -> Unit,
    onAddForm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    var adding by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(ui.title, onClose = onDismiss, subtitle = "どのフォームでやりますか？")
            if (ui.options.isEmpty()) {
                Text(
                    "まだフォームがありません。「フォームを追加」で、この種目のフォーム違いを作れます。",
                    style = AppTheme.typography.body,
                    color = colors.sub,
                )
            }
            ui.options.forEach { option ->
                AppRadioCard(
                    title = option.name,
                    selected = option.exerciseId == ui.selectedId,
                    onClick = { onChoose(option.exerciseId) },
                    lines = option.lines,
                )
            }
            TextButton(
                onClick = { adding = true },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("フォームを追加", style = AppTheme.typography.bodyStrong)
            }
            PrimaryButton(
                "このフォームで始める",
                onClick = onConfirm,
                enabled = ui.selectedId != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (adding) {
        TextInputDialog(
            title = "フォームを追加",
            initialText = "",
            label = "フォームの名前（例: 膝をベンチにつく）",
            onConfirm = {
                adding = false
                onAddForm(it)
            },
            onDismiss = { adding = false },
        )
    }
}

/**
 * 日付と、記録のしかたを変えるシート（F-WO-20、F-WO-21）。
 * 「あとから入力」は、今日以外の日付のためのタイマーなしの簡易入力。記録中でも、ふつうの記録と行き来できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateModeSheet(
    dateLabel: String,
    quickEntry: Boolean,
    canQuickEntry: Boolean,
    onPickDate: () -> Unit,
    onSetQuickEntry: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colors.surface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader("${dateLabel}の記録", onClose = onDismiss)
            OutlineButton("日付を変える", onClick = onPickDate, icon = AppIcons.Calendar, modifier = Modifier.fillMaxWidth())
            if (canQuickEntry) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("記録のしかた")
                    RecordingStyleChoices(quickEntry = quickEntry, onSelect = onSetQuickEntry)
                }
            }
        }
    }
}

/** 「ふつうに記録」「あとから入力」のどちらかを選ぶカード。ホームの日付のシートと、記録画面の日付のシートで使う。 */
@Composable
fun RecordingStyleChoices(quickEntry: Boolean, onSelect: (quickEntry: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppRadioCard(
            title = "ふつうに記録",
            selected = !quickEntry,
            onClick = { onSelect(false) },
            lines = listOf("休憩タイマーつきの、いつもの画面です。夜中に、前日の分として記録するときなど"),
        )
        AppRadioCard(
            title = "あとから入力",
            selected = quickEntry,
            onClick = { onSelect(true) },
            lines = listOf("タイマーなしの簡易入力です。思い出して入れるとき"),
        )
    }
}
