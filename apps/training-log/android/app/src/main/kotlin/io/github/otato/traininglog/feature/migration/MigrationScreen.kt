package io.github.otato.traininglog.feature.migration

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.traininglog.designsystem.components.AppCellField
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.ui.common.appViewModel
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable

// 4 つの入力欄が、幅 360dp の画面でも、種目名の幅を残して並ぶように、細めにしている
private val WeightColumn = 58.dp
private val RepsColumn = 46.dp
private val SetsColumn = 42.dp
private val BestColumn = 58.dp
private val ColumnGap = 4.dp

@Composable
fun MigrationRoute(
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    onEditExercise: (String) -> Unit,
) {
    val viewModel = appViewModel<MigrationViewModel> { MigrationViewModel(it.data.baselines, it.data.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MigrationScreen(
        state = state,
        onBack = onBack,
        onAddExercises = onAddExercises,
        onEditExercise = onEditExercise,
        onWeight = viewModel::onWeight,
        onReps = viewModel::onReps,
        onSets = viewModel::onSets,
        onBest = viewModel::onBest,
        onRemove = viewModel::remove,
    )
}

/**
 * 乗り換え用の初期設定の画面（デザインの「乗り換え用の初期設定」）。
 * 種目ごとに、直近の重量・回数・自己ベストを、表のように並べて入れる。
 */
@Composable
fun MigrationScreen(
    state: MigrationUiState,
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    onEditExercise: (String) -> Unit,
    onWeight: (String, String) -> Unit,
    onReps: (String, String) -> Unit,
    onSets: (String, String) -> Unit,
    onBest: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var removeTarget by remember { mutableStateOf<MigrationRowUi?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("乗り換え用の初期設定", onBack = onBack)
        Text(
            "前のアプリの画面を見ながら、種目ごとの直近の値を入れます。セット数は空欄でもかまいません。入れた値は、はじめのワークアウトで「前回の記録」になり、次のセットの初期値にもなります。",
            style = AppTheme.typography.caption.copy(fontSize = 13.sp),
            color = colors.sub,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
        HeaderRow(state.weightUnitLabel)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.rows, key = { it.exerciseId }) { row ->
                MigrationRow(
                    row = row,
                    onEdit = { onEditExercise(row.exerciseId) },
                    onLongPress = { removeTarget = row },
                    onWeight = { onWeight(row.exerciseId, it) },
                    onReps = { onReps(row.exerciseId, it) },
                    onSets = { onSets(row.exerciseId, it) },
                    onBest = { onBest(row.exerciseId, it) },
                )
                AppDivider()
            }
            item(key = "add") {
                if (state.loaded && state.rows.isEmpty()) {
                    Text(
                        "前のアプリで使っていた種目を、「種目を足す」から選んでください。",
                        style = AppTheme.typography.body,
                        color = colors.sub,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clickable(role = Role.Button, onClick = onAddExercises)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(AppIcons.Plus, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Text("種目を足す", style = AppTheme.typography.bodyStrong, color = colors.primary)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AppDivider()
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                Text(
                    "${state.filledCount} 種目を入力済み · いつでも直せます",
                    style = AppTheme.typography.caption,
                    color = colors.sub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
                // 入力は打つたびに保存されているので、ここでは画面を閉じるだけ
                PrimaryButton(
                    "保存する",
                    onClick = onBack,
                    minHeight = 60.dp,
                    textStyle = AppTheme.typography.button.copy(fontSize = 18.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    removeTarget?.let { target ->
        ConfirmDialog(
            title = "一覧から外しますか？",
            message = "「${target.name}」の直近の値と自己ベストが、消えます。種目は残ります。",
            confirmText = "外す",
            destructive = true,
            onConfirm = {
                onRemove(target.exerciseId)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }
}

@Composable
private fun HeaderRow(weightUnitLabel: String) {
    val colors = AppTheme.colors
    val style = AppTheme.typography.label.copy(fontSize = 11.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.surfaceVariant)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Text("種目", style = style, color = colors.sub, modifier = Modifier.weight(1f))
        Text("重量 $weightUnitLabel", style = style, color = colors.sub, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(WeightColumn))
        Text("回数", style = style, color = colors.sub, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(RepsColumn))
        Text("セット", style = style, color = colors.sub, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(SetsColumn))
        Text("自己ベスト", style = style, color = colors.sub, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(BestColumn))
    }
}

/** 1 種目の行。種目名をタップすると、種目の編集（器具・数え方など）を開く。長押しで、一覧から外す。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MigrationRow(
    row: MigrationRowUi,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    onWeight: (String) -> Unit,
    onReps: (String) -> Unit,
    onSets: (String) -> Unit,
    onBest: (String) -> Unit,
) {
    val colors = AppTheme.colors
    // 打った文字は、この行が持つ（保存した値で上書きしない。「62.」の途中で「62」に戻らないように）
    var weight by remember(row.exerciseId) { mutableStateOf(row.weight.initialText) }
    var reps by remember(row.exerciseId) { mutableStateOf(row.reps.initialText) }
    var sets by remember(row.exerciseId) { mutableStateOf(row.sets.initialText) }
    var best by remember(row.exerciseId) { mutableStateOf(row.best.initialText) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .combinedClickable(role = Role.Button, onClick = onEdit, onLongClick = onLongPress),
        ) {
            Text(row.name, style = AppTheme.typography.bodyStrong.copy(fontSize = 14.sp), color = colors.fg)
            Text(row.meta, style = AppTheme.typography.caption.copy(fontSize = 11.sp), color = colors.sub)
        }
        AppCellField(
            value = weight,
            onValueChange = {
                weight = it
                onWeight(it)
            },
            contentDescription = row.weight.description,
            enabled = row.weight.enabled,
            keyboard = row.weight.keyboard,
            modifier = Modifier.width(WeightColumn),
        )
        AppCellField(
            value = reps,
            onValueChange = {
                reps = it
                onReps(it)
            },
            contentDescription = row.reps.description,
            enabled = row.reps.enabled,
            keyboard = row.reps.keyboard,
            maxLength = 4,
            modifier = Modifier.width(RepsColumn),
        )
        AppCellField(
            value = sets,
            onValueChange = {
                sets = it
                onSets(it)
            },
            contentDescription = row.sets.description,
            enabled = row.sets.enabled,
            keyboard = row.sets.keyboard,
            maxLength = 2,
            modifier = Modifier.width(SetsColumn),
        )
        AppCellField(
            value = best,
            onValueChange = {
                best = it
                onBest(it)
            },
            contentDescription = row.best.description,
            enabled = row.best.enabled,
            keyboard = row.best.keyboard,
            modifier = Modifier.width(BestColumn),
        )
    }
}
