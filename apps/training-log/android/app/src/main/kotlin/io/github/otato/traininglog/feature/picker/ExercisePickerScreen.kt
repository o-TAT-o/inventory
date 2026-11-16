package io.github.otato.traininglog.feature.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag

@Composable
fun ExercisePickerRoute(
    mode: PickerMode,
    targetId: String,
    onBack: () -> Unit,
    onCreateExercise: () -> Unit,
) {
    val viewModel = appViewModel<ExercisePickerViewModel>(key = "picker-$mode-$targetId") { container ->
        ExercisePickerViewModel(
            mode = mode,
            targetId = targetId,
            exercises = container.data.exercises,
            routines = container.data.routines,
            workouts = container.data.workouts,
            baselines = container.data.baselines,
            addExerciseToWorkout = container.addExerciseToWorkout,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }

    ExercisePickerScreen(
        state = state,
        onBack = onBack,
        onQuery = viewModel::setQuery,
        onBodyPart = viewModel::setBodyPart,
        onEquipment = viewModel::setEquipment,
        onPick = viewModel::pick,
        onConfirm = viewModel::confirmSelection,
        onCreateExercise = onCreateExercise,
    )
}

@Composable
fun ExercisePickerScreen(
    state: PickerUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onBodyPart: (BodyPart?) -> Unit,
    onEquipment: (Equipment?) -> Unit,
    onPick: (String) -> Unit,
    onConfirm: () -> Unit,
    onCreateExercise: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = if (state.multiSelect) "種目を選ぶ（いくつでも）" else "種目を選ぶ", onBack = onBack)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("種目名で検索") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { AppChip("すべての部位", selected = state.bodyPart == null, onClick = { onBodyPart(null) }) }
            items(BodyPart.entries.toList()) { part ->
                AppChip(part.label(), selected = state.bodyPart == part, onClick = { onBodyPart(if (state.bodyPart == part) null else part) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { AppChip("すべての器具", selected = state.equipment == null, onClick = { onEquipment(null) }) }
            items(Equipment.entries.toList()) { equipment ->
                AppChip(
                    equipment.label(),
                    selected = state.equipment == equipment,
                    onClick = { onEquipment(if (state.equipment == equipment) null else equipment) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        AppDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.rows, key = { it.exercise.id }) { row ->
                val added = row.exercise.id in state.addedIds
                val checked = added || row.exercise.id in state.selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 60.dp)
                        .clickable(enabled = !added, role = Role.Button) { onPick(row.exercise.id) }
                        .padding(start = if (row.parentName != null) 32.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.multiSelect) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = !added)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.exercise.name, style = AppTheme.typography.bodyStrong, color = colors.fg)
                        Text(
                            listOfNotNull(
                                row.exercise.bodyPart.label(),
                                row.exercise.equipment.label(),
                                row.parentName?.let { "$it のバリエーション" },
                            ).joinToString(" · "),
                            style = AppTheme.typography.caption,
                            color = colors.sub,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (added) {
                        Text("追加済み", style = AppTheme.typography.caption, color = colors.sub)
                    } else {
                        Tag(row.exercise.weightMode.tag(row.exercise.equipment))
                    }
                }
                AppDivider()
            }
            item {
                if (state.loaded && state.rows.isEmpty()) {
                    Text(
                        "見つかりません。新しい種目を追加できます。",
                        style = AppTheme.typography.body,
                        color = colors.sub,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                OutlineButton(
                    "新しい種目を追加",
                    onClick = onCreateExercise,
                    icon = AppIcons.Plus,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        if (state.multiSelect) {
            // まとめて選ぶとき（乗り換え用の初期設定）は、下に「N 種目を追加」を置く
            AppDivider()
            PrimaryButton(
                text = if (state.selectedIds.isEmpty()) "種目を選んでください" else "${state.selectedIds.size} 種目を追加",
                onClick = onConfirm,
                enabled = state.selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}
