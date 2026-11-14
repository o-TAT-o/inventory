package io.github.otato.traininglog.feature.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.CompactStepper
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.RoutineDetail
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.RoutineItemDetail
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatMmSs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineEditUiState(
    val loaded: Boolean = false,
    val detail: RoutineDetail? = null,
    val settings: AppSettings = AppSettings(),
)

/** ルーティンの編集（F-RT-01〜03、F-RT-08、F-RT-11、F-RT-12）。 */
class RoutineEditViewModel(
    private val routineId: String,
    private val routines: RoutineRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<RoutineEditUiState> =
        combine(routines.observeRoutine(routineId), settings.observe()) { detail, s ->
            RoutineEditUiState(loaded = true, detail = detail, settings = s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditUiState())

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    fun rename(name: String) = update { it.copy(name = name) }

    fun setMemo(memo: String) = update { it.copy(memo = memo) }

    private fun update(transform: (io.github.otato.traininglog.domain.model.Routine) -> io.github.otato.traininglog.domain.model.Routine) {
        val current = state.value.detail?.routine ?: return
        viewModelScope.launch { routines.saveRoutine(transform(current)) }
    }

    fun saveItem(item: RoutineItem) {
        viewModelScope.launch { routines.saveItem(item) }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch { routines.deleteItem(itemId) }
    }

    fun delete() {
        viewModelScope.launch {
            routines.deleteRoutine(routineId)
            _deleted.emit(Unit)
        }
    }
}

@Composable
fun RoutineEditRoute(routineId: String, onBack: () -> Unit, onAddExercise: (String) -> Unit) {
    val viewModel = appViewModel<RoutineEditViewModel>(key = "routine-$routineId") {
        RoutineEditViewModel(routineId, it.data.routines, it.data.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }
    RoutineEditScreen(
        state = state,
        onBack = onBack,
        onRename = viewModel::rename,
        onMemo = viewModel::setMemo,
        onAddExercise = { onAddExercise(routineId) },
        onSaveItem = viewModel::saveItem,
        onRemoveItem = viewModel::removeItem,
        onDelete = viewModel::delete,
    )
}

@Composable
fun RoutineEditScreen(
    state: RoutineEditUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onMemo: (String) -> Unit,
    onAddExercise: () -> Unit,
    onSaveItem: (RoutineItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val detail = state.detail
    var editing by remember { mutableStateOf<RoutineItemDetail?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = detail?.routine?.name ?: "ルーティン",
            onBack = onBack,
            actions = { if (detail != null) AppIconButton(AppIcons.Trash, "ルーティンを削除", { confirmDelete = true }) },
        )
        if (detail == null) return@Column
        var name by remember(detail.routine.id) { mutableStateOf(detail.routine.name) }
        var memo by remember(detail.routine.id) { mutableStateOf(detail.routine.memo) }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) onRename(it.trim())
                        },
                        label = { Text("名前") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = memo,
                        onValueChange = {
                            memo = it
                            onMemo(it)
                        },
                        label = { Text("メモ（記録画面で見られます）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    SectionLabel("種目")
                }
            }
            items(detail.items, key = { it.item.id }) { itemDetail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 64.dp)
                        .clickable(role = Role.Button) { editing = itemDetail }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(itemDetail.exercise.name, style = AppTheme.typography.bodyStrong, color = colors.fg)
                        Text(targetSummary(itemDetail.item, state.settings), style = AppTheme.typography.caption, color = colors.sub)
                    }
                    TextAction("外す", onClick = { onRemoveItem(itemDetail.item.id) }, color = colors.error)
                }
                AppDivider()
            }
            item {
                OutlineButton(
                    "種目を追加",
                    onClick = onAddExercise,
                    icon = AppIcons.Plus,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    editing?.let { target ->
        ItemEditorSheet(
            detail = target,
            settings = state.settings,
            onSave = {
                onSaveItem(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "このルーティンを削除しますか？",
            message = "これまでの記録は消えません。",
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

private fun targetSummary(item: RoutineItem, settings: AppSettings): String {
    val parts = buildList {
        item.targetSets?.let { add("$it セット") }
        item.targetWeightKg?.let { add(WeightUnits.formatKg(it, settings.weightUnit) + " " + WeightUnits.label(settings.weightUnit)) }
        item.targetReps?.let { add("$it 回") }
        item.restSeconds?.let { add("休憩 ${formatMmSs(it.toLong())}") }
        item.restAfterSeconds?.let { add("種目のあと ${formatMmSs(it.toLong())}") }
    }
    return if (parts.isEmpty()) "目標なし（前回の記録から始まります）" else parts.joinToString(" · ")
}

/** 種目ごとの目標（セット数・重量・回数）と休憩の編集。空欄は、前回の記録や設定の休憩時間を使う。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditorSheet(
    detail: RoutineItemDetail,
    settings: AppSettings,
    onSave: (RoutineItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var item by remember(detail.item.id) { mutableStateOf(detail.item) }
    val stepKg = detail.exercise.weightStepKg ?: WeightUnits.fromDisplay(settings.weightStep, settings.weightUnit)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colors.surface) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(detail.exercise.name, style = AppTheme.typography.title, color = AppTheme.colors.fg)
            if (detail.exercise.usesWeight) {
                OptionalRow(
                    label = "重量（${WeightUnits.label(settings.weightUnit)}）",
                    valueText = item.targetWeightKg?.let { WeightUnits.formatKg(it, settings.weightUnit) },
                    onMinus = { item = item.copy(targetWeightKg = ((item.targetWeightKg ?: 0.0) - stepKg).coerceAtLeast(0.0)) },
                    onPlus = { item = item.copy(targetWeightKg = (item.targetWeightKg ?: 0.0) + stepKg) },
                    onClear = { item = item.copy(targetWeightKg = null) },
                )
            }
            OptionalRow(
                label = "セット数",
                valueText = item.targetSets?.toString(),
                onMinus = { item = item.copy(targetSets = ((item.targetSets ?: 3) - 1).coerceAtLeast(1)) },
                onPlus = { item = item.copy(targetSets = ((item.targetSets ?: 2) + 1).coerceAtMost(20)) },
                onClear = { item = item.copy(targetSets = null) },
            )
            if (detail.exercise.usesReps) {
                OptionalRow(
                    label = "回数",
                    valueText = item.targetReps?.toString(),
                    onMinus = { item = item.copy(targetReps = ((item.targetReps ?: 10) - 1).coerceAtLeast(1)) },
                    onPlus = { item = item.copy(targetReps = ((item.targetReps ?: 9) + 1).coerceAtMost(100)) },
                    onClear = { item = item.copy(targetReps = null) },
                )
            }
            OptionalRow(
                label = "休憩（セット間）",
                valueText = item.restSeconds?.let { formatMmSs(it.toLong()) },
                onMinus = { item = item.copy(restSeconds = ((item.restSeconds ?: settings.restBetweenSetsSec) - 10).coerceAtLeast(10)) },
                onPlus = { item = item.copy(restSeconds = (item.restSeconds ?: settings.restBetweenSetsSec) + 10) },
                onClear = { item = item.copy(restSeconds = null) },
            )
            OptionalRow(
                label = "休憩（種目のあと）",
                valueText = item.restAfterSeconds?.let { formatMmSs(it.toLong()) },
                onMinus = { item = item.copy(restAfterSeconds = ((item.restAfterSeconds ?: settings.restAfterExerciseSec) - 10).coerceAtLeast(10)) },
                onPlus = { item = item.copy(restAfterSeconds = (item.restAfterSeconds ?: settings.restAfterExerciseSec) + 10) },
                onClear = { item = item.copy(restAfterSeconds = null) },
            )
            Text("空欄のところは、前回の記録や、設定の休憩時間が使われます", style = AppTheme.typography.caption, color = AppTheme.colors.sub)
            Spacer(Modifier.height(4.dp))
            PrimaryButton("保存", onClick = { onSave(item) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OptionalRow(
    label: String,
    valueText: String?,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg, modifier = Modifier.weight(1f))
        if (valueText != null) {
            TextAction("空欄に戻す", onClick = onClear, color = AppTheme.colors.sub)
            Spacer(Modifier.width(4.dp))
        }
        CompactStepper(
            valueText = valueText ?: "—",
            onMinus = onMinus,
            onPlus = onPlus,
            minusLabel = "$label を減らす",
            plusLabel = "$label を増やす",
        )
    }
}
