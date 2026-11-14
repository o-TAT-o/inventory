package io.github.otato.traininglog.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.components.ValueStepper
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.ui.common.label

/** 下のパネルの操作。画面は、これを通して ViewModel を呼ぶ。 */
data class DockActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onAdjust: (FieldKey, Int) -> Unit,
    val onFieldClick: (FieldUi) -> Unit,
    val onDetail: () -> Unit,
    val onAddSet: () -> Unit,
    val onComplete: () -> Unit,
    val onFinish: () -> Unit,
    val onAdjustRest: (Int) -> Unit,
    val onDismissRest: () -> Unit,
)

/**
 * 記録画面の下に固定するパネル（案 C「ドック型」）。
 * 休憩タイマー・種目の切り替え・重量と回数・「詳細」「＋セット」「完了」を、画面の下だけで操作できるようにまとめる。
 */
@Composable
fun WorkoutDock(
    dock: DockUi?,
    allDone: Boolean,
    summaryText: String,
    rest: RestUi?,
    actions: DockActions,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        AppDivider()
        if (rest != null) {
            RestRow(rest, actions)
        }
        when {
            dock != null -> ActiveDock(dock, actions)
            allDone -> AllDonePanel(summaryText, actions)
            else -> Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "種目を追加して、記録を始めましょう",
                    style = AppTheme.typography.body,
                    color = colors.sub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RestRow(rest: RestUi, actions: DockActions) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.background(if (rest.ended) colors.container else colors.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Timer, contentDescription = null, tint = if (rest.ended) colors.onContainer else colors.sub, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (rest.ended) "休憩おわり" else "休憩",
                style = AppTheme.typography.body,
                color = if (rest.ended) colors.onContainer else colors.sub,
            )
            Spacer(Modifier.width(8.dp))
            Text(rest.text, style = AppTheme.typography.timer, color = if (rest.ended) colors.onContainer else colors.fg)
            Spacer(Modifier.weight(1f))
            if (rest.ended) {
                TextAction("＋30 秒", onClick = { actions.onAdjustRest(30) })
                AppIconButton(AppIcons.Close, "休憩の表示を閉じる", actions.onDismissRest, size = 44.dp)
            } else {
                TextAction("−10 秒", onClick = { actions.onAdjustRest(-10) })
                TextAction("＋10 秒", onClick = { actions.onAdjustRest(10) })
                AppIconButton(AppIcons.Skip, "休憩をとばす", actions.onDismissRest, size = 44.dp)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.line)) {
            Box(Modifier.fillMaxWidth(rest.progress.coerceIn(0f, 1f)).height(3.dp).background(colors.primary))
        }
    }
}

@Composable
private fun ActiveDock(dock: DockUi, actions: DockActions) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        // 種目の切り替え ‹ 名前 ›
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                AppIcons.Left,
                "前の種目へ",
                actions.onPrevious,
                tint = if (dock.canGoPrevious) colors.fg else colors.line,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { actions.onOpenExercise(dock.exerciseId) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dock.title, style = AppTheme.typography.heading, color = colors.fg, maxLines = 1)
                Text(dock.subtitle, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1)
            }
            AppIconButton(
                AppIcons.Right,
                "次の種目へ",
                actions.onNext,
                tint = if (dock.canGoNext) colors.fg else colors.line,
            )
        }
        // 重量と回数
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dock.fields.forEach { field ->
                ValueStepper(
                    valueText = field.valueText,
                    unitText = field.unitText,
                    onMinus = { actions.onAdjust(field.key, -1) },
                    onPlus = { actions.onAdjust(field.key, 1) },
                    onValueClick = { actions.onFieldClick(field) },
                    minusLabel = field.minusLabel,
                    plusLabel = field.plusLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 詳細・＋セット・完了
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DockSmallButton(AppIcons.Up, "詳細", actions.onDetail, Modifier.width(68.dp))
            DockSmallButton(AppIcons.Plus, "セット", actions.onAddSet, Modifier.width(76.dp))
            PrimaryButton(
                text = if (dock.editingCompleted) "直した" else "完了",
                onClick = actions.onComplete,
                icon = AppIcons.Check,
                minHeight = 60.dp,
                textStyle = AppTheme.typography.button.copy(fontSize = 17.sp),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 「詳細」「＋セット」の、アイコンの下に小さな文字を並べたボタン。高さ 60dp。 */
@Composable
private fun DockSmallButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(22.dp))
        Text(label, style = AppTheme.typography.caption.copy(fontSize = 11.sp), color = colors.fg)
    }
}

@Composable
private fun AllDonePanel(summaryText: String, actions: DockActions) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
        Text(
            "すべてのセットが終わりました",
            style = AppTheme.typography.heading,
            color = colors.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            summaryText,
            style = AppTheme.typography.caption,
            color = colors.sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                "もう1セット",
                onClick = actions.onAddSet,
                minHeight = 60.dp,
                horizontalPadding = 8.dp,
                textStyle = AppTheme.typography.button.copy(fontSize = 15.sp),
                modifier = Modifier.width(124.dp),
            )
            PrimaryButton(
                "ワークアウトを終える",
                onClick = actions.onFinish,
                minHeight = 60.dp,
                horizontalPadding = 12.dp,
                textStyle = AppTheme.typography.button.copy(fontSize = 16.sp),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 種目の詳細パネル（ギアの付け外し・その日だけの数え方・メモ）。下のパネルの「詳細」から開く。
 * 前回と同じギアが最初から選ばれているので、いつもと同じなら何も触らなくてよい（F-GR-03）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailSheet(
    detail: DetailUi,
    onDismiss: () -> Unit,
    onToggleGear: (String) -> Unit,
    onSetModes: (WeightMode, RepMode) -> Unit,
    onSetMemo: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var memo by remember(detail.workoutExerciseId) { mutableStateOf(detail.memo) }

    // 閉じるときに、書きかけのメモも保存する
    ModalBottomSheet(
        onDismissRequest = {
            if (memo != detail.memo) onSetMemo(memo)
            onDismiss()
        },
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(detail.exerciseName, style = AppTheme.typography.title, color = colors.fg)
                Text(detail.subtitle, style = AppTheme.typography.caption, color = colors.sub)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("ギア　タップで付け外し・前回と同じものが選ばれています")
                if (detail.gear.isEmpty()) {
                    Text("ギアがまだありません", style = AppTheme.typography.body, color = colors.sub)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.gear.forEach { gear ->
                        AppChip(gear.name, selected = gear.selected, onClick = { onToggleGear(gear.id) })
                    }
                }
            }
            if (detail.showWeightMode || detail.showRepMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("数え方　今日の記録だけ変えます。種目の設定は変わりません")
                    if (detail.showWeightMode) {
                        SegmentRow("重量") {
                            listOf(WeightMode.Total, WeightMode.PerSide).forEach { mode ->
                                AppChip(
                                    mode.label(detail.equipment.takeIf { mode == WeightMode.PerSide } ?: Equipment.Machine),
                                    selected = detail.weightMode == mode,
                                    onClick = { onSetModes(mode, detail.repMode) },
                                )
                            }
                        }
                    }
                    if (detail.showRepMode) {
                        SegmentRow("回数") {
                            listOf(RepMode.Normal, RepMode.PerSide, RepMode.BothSidesTotal).forEach { mode ->
                                AppChip(
                                    mode.label(),
                                    selected = detail.repMode == mode,
                                    onClick = { onSetModes(detail.weightMode, mode) },
                                )
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("この種目のメモ")
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    placeholder = { Text("今日の記録にメモを書く（例: 3セット目でフォームが崩れた）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            OutlineButton(
                "とじる",
                onClick = {
                    if (memo != detail.memo) onSetMemo(memo)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AppTheme.typography.body, color = AppTheme.colors.sub, modifier = Modifier.width(40.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
