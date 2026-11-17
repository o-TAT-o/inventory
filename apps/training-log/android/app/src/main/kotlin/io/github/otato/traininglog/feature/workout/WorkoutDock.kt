package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.designsystem.components.dismissKeyboardOnTouch
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppSegmented
import io.github.otato.traininglog.designsystem.components.NumberInputDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.SheetHeader
import io.github.otato.traininglog.designsystem.components.StepButton
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.components.TonalButton
import io.github.otato.traininglog.designsystem.components.ValueStepper
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.WeightUnits
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
 * 種目の詳細パネル（ギアの付け外し・調整項目・その日だけの数え方・メモ）。下のパネルの「詳細」から開く。
 * 前回と同じギアと調整項目の値が最初から入っているので、いつもと同じなら何も触らなくてよい（F-GR-03、F-WO-12）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailSheet(
    detail: DetailUi,
    onDismiss: () -> Unit,
    onToggleGear: (String) -> Unit,
    onAddGear: (String) -> Unit,
    onSetAdjustment: (fieldId: String, value: String?) -> Unit,
    onSetModes: (WeightMode, RepMode) -> Unit,
    onSetMemo: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var memo by remember(detail.workoutExerciseId) { mutableStateOf(detail.memo) }
    var addingGear by remember { mutableStateOf(false) }

    // 閉じるときに、書きかけのメモも保存する
    val close = {
        if (memo != detail.memo) onSetMemo(memo)
        onDismiss()
    }

    // 開いたときに、全部を見せる（半分だけ開くと、メモの欄がキーボードや端に隠れて、スクロールが要る）
    ModalBottomSheet(
        onDismissRequest = close,
        containerColor = colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .dismissKeyboardOnTouch()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(detail.exerciseName, onClose = close, subtitle = detail.subtitle)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelWithHint("ギア", "タップで付け外し・前回と同じものが選ばれています")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.gear.forEach { gear ->
                        AppChip(
                            gear.name,
                            selected = gear.selected,
                            onClick = { onToggleGear(gear.id) },
                            icon = if (gear.selected) AppIcons.Check else null,
                        )
                    }
                    AppChip("追加", selected = false, onClick = { addingGear = true }, icon = AppIcons.Plus)
                }
            }
            if (detail.adjustments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("調整項目")
                    detail.adjustments.forEach { item ->
                        AdjustmentRow(item, onChange = { onSetAdjustment(item.fieldId, it) })
                    }
                }
            }
            // メモは、数え方より上に置く（よく使うものを、スクロールしなくても届く所に）
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("この種目のメモ")
                // 書くときの手がかりに、いつものメモ（種目に書いた注意点）と、前回のメモを出す
                detail.exerciseMemo?.let { Text("いつものメモ: $it", style = AppTheme.typography.caption, color = colors.sub) }
                detail.previousMemo?.let { Text("前回のメモ: $it", style = AppTheme.typography.caption, color = colors.sub) }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    placeholder = { Text("この日の記録にメモを書く（例: 3セット目でフォームが崩れた）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            if (detail.showWeightMode || detail.showRepMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelWithHint("数え方", "今日の記録だけ変わります")
                    if (detail.showWeightMode) {
                        val modes = listOf(WeightMode.PerSide, WeightMode.Total)
                        SegmentRow(
                            label = "重量",
                            options = modes.map { it.label(detail.equipment) },
                            selectedIndex = modes.indexOf(detail.weightMode),
                            onSelect = { onSetModes(modes[it], detail.repMode) },
                        )
                    }
                    if (detail.showRepMode) {
                        val modes = listOf(RepMode.Normal, RepMode.PerSide, RepMode.BothSidesTotal)
                        SegmentRow(
                            label = "回数",
                            options = modes.map { it.label() },
                            selectedIndex = modes.indexOf(detail.repMode),
                            onSelect = { onSetModes(detail.weightMode, modes[it]) },
                        )
                    }
                }
            }
            TonalButton("とじる", onClick = close, modifier = Modifier.fillMaxWidth())
        }
    }

    if (addingGear) {
        TextInputDialog(
            title = "ギアを追加",
            initialText = "",
            label = "ギアの名前（例: ベルト）",
            onConfirm = {
                addingGear = false
                onAddGear(it)
            },
            onDismiss = { addingGear = false },
        )
    }
}

/** 「ギア　タップで付け外し…」のように、見出しの横に、小さな補足を並べる。 */
@Composable
private fun LabelWithHint(label: String, hint: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AppTheme.typography.label, color = AppTheme.colors.fg, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(hint, style = AppTheme.typography.caption, color = AppTheme.colors.sub, modifier = Modifier.weight(1f))
    }
}

/** 調整項目の 1 行。数値の項目は ＋ − と数字（タップで直接入力）、文字の項目は入力欄。変えるたびにその場で保存する。 */
@Composable
private fun AdjustmentRow(item: AdjustmentUi, onChange: (String?) -> Unit) {
    val colors = AppTheme.colors
    if (item.isNumber) {
        // 長押しで速く増減しても追いつくように、いまの値は手元で持つ（保存は、そのつど書く）
        var current by remember(item.fieldId) { mutableStateOf(item.value) }
        var editing by remember(item.fieldId) { mutableStateOf(false) }
        val base = current.toDoubleOrNull() ?: item.previousValue?.toDoubleOrNull() ?: 0.0
        val set = { value: Double ->
            val text = WeightUnits.format(value)
            current = text
            onChange(text)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = AppTheme.typography.heading, color = colors.fg)
                item.previousText?.let { Text(it, style = AppTheme.typography.caption, color = colors.sub) }
            }
            StepButton(AppIcons.Minus, "${item.name}を減らす", onClick = { set(base - 1) }, fireOnPress = false)
            Row(
                modifier = Modifier
                    .width(88.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "${item.name}を入力する") { editing = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current.ifEmpty { "—" }, style = AppTheme.typography.number, color = colors.fg)
                if (current.isNotEmpty() && item.unit.isNotEmpty()) {
                    Text(item.unit, style = AppTheme.typography.caption, color = colors.sub)
                }
            }
            StepButton(AppIcons.Plus, "${item.name}を増やす", onClick = { set(base + 1) }, fireOnPress = false)
        }
        if (editing) {
            NumberInputDialog(
                title = item.name,
                initialText = current.ifEmpty { item.previousValue.orEmpty() },
                unitText = item.unit,
                allowDecimal = true,
                onConfirm = {
                    set(it)
                    editing = false
                },
                onDismiss = { editing = false },
            )
        }
    } else {
        var text by remember(item.fieldId) { mutableStateOf(item.value) }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it.ifBlank { null })
            },
            label = { Text(item.name) },
            placeholder = item.previousText?.let { previous -> { Text(previous) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 「重量 ［片手｜合計］」のように、見出しと、切り替えを 1 行に並べる。 */
@Composable
private fun SegmentRow(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = AppTheme.typography.body, color = AppTheme.colors.sub, modifier = Modifier.width(36.dp))
        AppSegmented(options = options, selectedIndex = selectedIndex, onSelect = onSelect, modifier = Modifier.weight(1f))
    }
}
