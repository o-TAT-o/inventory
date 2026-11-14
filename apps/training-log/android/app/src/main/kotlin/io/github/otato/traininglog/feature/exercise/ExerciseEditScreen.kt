package io.github.otato.traininglog.feature.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.label
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val exercise: Exercise? = null,
)

/** 種目の追加・編集（F-EX-02〜04、F-EX-08、F-EX-09、F-EX-13、F-EX-19）。 */
class ExerciseEditViewModel(
    private val exerciseId: String,
    private val exercises: ExerciseRepository,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {
    private val _state = MutableStateFlow(ExerciseEditUiState())
    val state: StateFlow<ExerciseEditUiState> = _state

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            val existing = if (exerciseId == NEW) null else exercises.getExercise(exerciseId)
            _state.value = ExerciseEditUiState(
                loaded = true,
                isNew = existing == null,
                exercise = existing ?: Exercise(id = ids.next(), name = "", sortOrder = exercises.nextSortOrder()),
            )
        }
    }

    fun edit(transform: (Exercise) -> Exercise) {
        _state.update { s -> s.exercise?.let { s.copy(exercise = transform(it)) } ?: s }
    }

    fun save() {
        val exercise = _state.value.exercise ?: return
        if (exercise.name.isBlank()) return
        viewModelScope.launch {
            exercises.save(exercise.copy(name = exercise.name.trim()))
            _done.emit(Unit)
        }
    }

    /** 記録があれば非表示、なければ削除する（F-EX-05）。 */
    fun delete() {
        val exercise = _state.value.exercise ?: return
        viewModelScope.launch {
            exercises.deleteOrHide(exercise.id)
            _done.emit(Unit)
        }
    }

    companion object {
        const val NEW = "new"
    }
}

@Composable
fun ExerciseEditRoute(exerciseId: String, onBack: () -> Unit) {
    val viewModel = appViewModel<ExerciseEditViewModel>(key = "exercise-edit-$exerciseId") {
        ExerciseEditViewModel(exerciseId, it.data.exercises)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    ExerciseEditScreen(state, onBack, viewModel::edit, viewModel::save, viewModel::delete)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    state: ExerciseEditUiState,
    onBack: () -> Unit,
    onEdit: ((Exercise) -> Exercise) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val exercise = state.exercise
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(if (state.isNew) "新しい種目" else "種目の編集", onBack = onBack)
        if (exercise == null) return@Column
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = exercise.name,
                onValueChange = { v -> onEdit { it.copy(name = v) } },
                label = { Text("名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Section("部位") {
                BodyPart.entries.forEach { part ->
                    AppChip(part.label(), exercise.bodyPart == part, { onEdit { it.copy(bodyPart = part) } })
                }
            }
            Section("記録の種類") {
                RecordType.entries.forEach { type ->
                    AppChip(
                        type.label(),
                        exercise.recordType == type,
                        {
                            onEdit {
                                it.copy(
                                    recordType = type,
                                    useAddedWeight = if (type == RecordType.Bodyweight) it.useAddedWeight else false,
                                )
                            }
                        },
                    )
                }
            }
            Section("器具") {
                Equipment.entries.forEach { equipment ->
                    AppChip(equipment.label(), exercise.equipment == equipment, { onEdit { it.copy(equipment = equipment) } })
                }
            }
            if (exercise.recordType == RecordType.WeightReps) {
                Section("重量の数え方") {
                    AppChip("合計", exercise.weightMode == WeightMode.Total, { onEdit { it.copy(weightMode = WeightMode.Total) } })
                    AppChip(
                        if (exercise.equipment == Equipment.Dumbbell) "片手" else "片側",
                        exercise.weightMode == WeightMode.PerSide,
                        { onEdit { it.copy(weightMode = WeightMode.PerSide) } },
                    )
                }
            }
            if (exercise.recordType == RecordType.WeightReps || exercise.recordType == RecordType.Bodyweight) {
                Section("回数の数え方") {
                    RepMode.entries.forEach { mode ->
                        AppChip(mode.label(), exercise.repMode == mode, { onEdit { it.copy(repMode = mode) } })
                    }
                }
            }
            if (exercise.recordType == RecordType.Bodyweight) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("加重（補助）を使う", style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg)
                        Text("ウェイトベスト・ディップベルト・アシストマシンなど", style = AppTheme.typography.caption, color = AppTheme.colors.sub)
                    }
                    Switch(checked = exercise.useAddedWeight, onCheckedChange = { v -> onEdit { it.copy(useAddedWeight = v) } })
                }
            }
            OutlinedTextField(
                value = exercise.memo,
                onValueChange = { v -> onEdit { it.copy(memo = v) } },
                label = { Text("メモ（フォームの注意点など）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            PrimaryButton("保存", onClick = onSave, enabled = exercise.name.isNotBlank(), modifier = Modifier.fillMaxWidth())
            if (!state.isNew) {
                OutlineButton("この種目を削除", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "この種目を削除しますか？",
            message = "記録がある種目は、記録を残したまま非表示になります。",
            confirmText = "削除",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
