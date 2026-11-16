package io.github.otato.traininglog.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppSegmented
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.CompactStepper
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.components.TonalButton
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.usecase.AddVariation
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.label
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** バリエーション（フォーム）の 1 行。「膝をベンチにつく　記録 24 回」 */
data class VariationRowUi(val id: String, val name: String, val detail: String)

data class ExerciseEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val exercise: Exercise? = null,
    /** 調整項目（F-EX-10）。保存するまでは、下書き */
    val fields: List<AdjustmentField> = emptyList(),
    /** バリエーション（F-EX-14）。新しい種目と、バリエーション自身では空 */
    val variations: List<VariationRowUi> = emptyList(),
    /** 親の種目の名前（バリエーションを編集しているとき） */
    val parentName: String? = null,
    /** この種目そのものに付いている記録の数。バリエーションへまとめて移せる（F-EX-18） */
    val ownRecordCount: Int = 0,
) {
    val isVariation: Boolean get() = exercise?.parentId != null

    /** バリエーションを足せるか（保存済みの、親の種目だけ） */
    val canHaveVariations: Boolean get() = !isNew && !isVariation
}

sealed interface ExerciseEditEvent {
    /** 保存・削除が済んだ（前の画面へ戻る） */
    data object Done : ExerciseEditEvent

    /** バリエーションを作った（その編集を開く） */
    data class OpenVariation(val exerciseId: String) : ExerciseEditEvent
}

/** 種目の追加・編集（F-EX-02〜04、F-EX-08〜10、F-EX-13〜15、F-EX-18、F-EX-19）。 */
class ExerciseEditViewModel(
    private val exerciseId: String,
    private val exercises: ExerciseRepository,
    private val ids: IdGenerator = UuidGenerator,
    private val addVariation: AddVariation = AddVariation(exercises, ids),
) : ViewModel() {
    private val _state = MutableStateFlow(ExerciseEditUiState())
    val state: StateFlow<ExerciseEditUiState> = _state

    private val _events = MutableSharedFlow<ExerciseEditEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ExerciseEditEvent> = _events.asSharedFlow()

    private var watching: Job? = null

    init {
        viewModelScope.launch {
            val existing = if (exerciseId == NEW) null else exercises.getExercise(exerciseId)
            val exercise = existing ?: Exercise(id = ids.next(), name = "", sortOrder = exercises.nextSortOrder())
            _state.value = ExerciseEditUiState(
                loaded = true,
                isNew = existing == null,
                exercise = exercise,
                fields = if (existing == null) emptyList() else exercises.getAdjustmentFields(exercise.id),
                parentName = exercise.parentId?.let { exercises.getExercise(it)?.name },
            )
            if (existing != null) watch(exercise.id)
        }
    }

    /** バリエーションの一覧と記録の数は、ほかの画面で変わるので、見続ける。 */
    private fun watch(id: String) {
        watching?.cancel()
        watching = viewModelScope.launch {
            combine(exercises.observeChildren(id), exercises.observeRecordCounts()) { children, counts ->
                children.map { child ->
                    val count = counts[child.id] ?: 0
                    VariationRowUi(child.id, child.name, if (count > 0) "記録 $count 回" else "記録なし")
                } to (counts[id] ?: 0)
            }.collect { (rows, own) -> _state.update { it.copy(variations = rows, ownRecordCount = own) } }
        }
    }

    fun edit(transform: (Exercise) -> Exercise) {
        _state.update { s -> s.exercise?.let { s.copy(exercise = transform(it)) } ?: s }
    }

    fun addField() {
        val exercise = _state.value.exercise ?: return
        _state.update { it.copy(fields = it.fields + AdjustmentField(id = ids.next(), exerciseId = exercise.id, name = "")) }
    }

    fun editField(id: String, transform: (AdjustmentField) -> AdjustmentField) {
        _state.update { s -> s.copy(fields = s.fields.map { if (it.id == id) transform(it) else it }) }
    }

    fun removeField(id: String) {
        _state.update { s -> s.copy(fields = s.fields.filterNot { it.id == id }) }
    }

    fun save() {
        if (_state.value.exercise?.name.isNullOrBlank()) return
        viewModelScope.launch {
            persist()
            _events.emit(ExerciseEditEvent.Done)
        }
    }

    /** いまの入力を保存する。名前のない調整項目は、保存しない。 */
    private suspend fun persist(): Exercise? {
        val exercise = _state.value.exercise?.takeIf { it.name.isNotBlank() } ?: return null
        val saved = exercise.copy(name = exercise.name.trim())
        exercises.save(saved)
        val fields = _state.value.fields
            .filter { it.name.isNotBlank() }
            .mapIndexed { index, field -> field.copy(name = field.name.trim(), unit = field.unit.trim(), sortOrder = index) }
        exercises.saveAdjustmentFields(saved.id, fields)
        return saved
    }

    /**
     * バリエーション（フォーム）を足す。いまの入力を先に保存してから作るので、部位・器具・数え方・調整項目が引き継がれる（F-EX-15）。
     * 作ったら、その編集を開く。
     */
    fun addVariation(name: String) {
        if (name.isBlank() || !_state.value.canHaveVariations) return
        viewModelScope.launch {
            val parent = persist() ?: return@launch
            val id = addVariation(parent.id, name)
            _events.emit(ExerciseEditEvent.OpenVariation(id))
        }
    }

    /** この種目に付いている記録を、選んだバリエーションへまとめて移す（F-EX-18）。 */
    fun moveRecordsTo(variationId: String) {
        val exercise = _state.value.exercise ?: return
        viewModelScope.launch { exercises.moveRecords(exercise.id, variationId) }
    }

    /** 記録があれば非表示、なければ削除する（F-EX-05）。 */
    fun delete() {
        val exercise = _state.value.exercise ?: return
        viewModelScope.launch {
            exercises.deleteOrHide(exercise.id)
            _events.emit(ExerciseEditEvent.Done)
        }
    }

    companion object {
        const val NEW = "new"
    }
}

@Composable
fun ExerciseEditRoute(exerciseId: String, onBack: () -> Unit, onOpenExercise: (exerciseId: String) -> Unit) {
    val viewModel = appViewModel<ExerciseEditViewModel>(key = "exercise-edit-$exerciseId") {
        ExerciseEditViewModel(exerciseId, it.data.exercises)
    }
    val container = io.github.otato.traininglog.ui.common.rememberContainer()
    val settingsFlow = remember(container) { container.data.settings.observe() }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ExerciseEditEvent.Done -> onBack()
                is ExerciseEditEvent.OpenVariation -> onOpenExercise(event.exerciseId)
            }
        }
    }
    ExerciseEditScreen(
        state = state,
        defaultStepText = settings?.let { "${WeightUnits.format(it.weightStep)} ${WeightUnits.label(it.weightUnit)}" } ?: "",
        onBack = onBack,
        onEdit = viewModel::edit,
        onAddField = viewModel::addField,
        onEditField = viewModel::editField,
        onRemoveField = viewModel::removeField,
        onAddVariation = viewModel::addVariation,
        onOpenVariation = onOpenExercise,
        onMoveRecords = viewModel::moveRecordsTo,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/** この種目だけの重量の刻み（kg）。null は「設定のまま」。 */
private val stepChoices: List<Double?> = listOf<Double?>(null) + WeightUnits.stepOptions(WeightUnit.Kg)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    state: ExerciseEditUiState,
    defaultStepText: String,
    onBack: () -> Unit,
    onEdit: ((Exercise) -> Exercise) -> Unit,
    onAddField: () -> Unit,
    onEditField: (id: String, (AdjustmentField) -> AdjustmentField) -> Unit,
    onRemoveField: (id: String) -> Unit,
    onAddVariation: (name: String) -> Unit,
    onOpenVariation: (exerciseId: String) -> Unit,
    onMoveRecords: (variationId: String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val exercise = state.exercise
    var confirmDelete by remember { mutableStateOf(false) }
    var addingVariation by remember { mutableStateOf(false) }
    var choosingMoveTarget by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<VariationRowUi?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            when {
                state.isNew -> "新しい種目"
                state.isVariation -> "フォームの編集"
                else -> "種目の編集"
            },
            onBack = onBack,
        )
        if (exercise == null) return@Column
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            state.parentName?.let {
                Text("「$it」のフォームです。部位や数え方は、このフォームだけ変えられます。", style = AppTheme.typography.caption, color = colors.sub)
            }
            // 入力欄の文字は、その場で持つ（ViewModel の状態を経由して戻すと、日本語の変換中に文字が欠けることがある）
            var name by remember(exercise.id) { mutableStateOf(exercise.name) }
            OutlinedTextField(
                value = name,
                onValueChange = { v ->
                    name = v
                    onEdit { it.copy(name = v) }
                },
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
                        Text("加重（補助）を使う", style = AppTheme.typography.bodyStrong, color = colors.fg)
                        Text("ウェイトベスト・ディップベルト・アシストマシンなど", style = AppTheme.typography.caption, color = colors.sub)
                    }
                    Switch(checked = exercise.useAddedWeight, onCheckedChange = { v -> onEdit { it.copy(useAddedWeight = v) } })
                }
            }
            if (exercise.usesWeight) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("重量の刻み")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("この種目だけ", style = AppTheme.typography.bodyStrong, color = colors.fg)
                            Text("設定のままなら $defaultStepText", style = AppTheme.typography.caption, color = colors.sub)
                        }
                        val index = stepChoices.indexOf(exercise.weightStepKg).coerceAtLeast(0)
                        CompactStepper(
                            valueText = exercise.weightStepKg?.let { "${WeightUnits.format(it)}kg" } ?: "設定",
                            onMinus = { onEdit { it.copy(weightStepKg = stepChoices[(index - 1).coerceAtLeast(0)]) } },
                            onPlus = { onEdit { it.copy(weightStepKg = stepChoices[(index + 1).coerceAtMost(stepChoices.lastIndex)]) } },
                            minusLabel = "刻みを小さくする",
                            plusLabel = "刻みを大きくする",
                        )
                    }
                }
            }

            // ---- 調整項目（F-EX-10） ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelWithHint("調整項目", "ベンチ角度・シート位置など")
                state.fields.forEach { field ->
                    FieldCard(
                        field = field,
                        onEdit = { transform -> onEditField(field.id, transform) },
                        onRemove = { onRemoveField(field.id) },
                    )
                }
                TonalButton("＋ 調整項目を追加", onClick = onAddField, modifier = Modifier.fillMaxWidth())
            }

            // ---- バリエーション（F-EX-14） ----
            if (!state.isVariation) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelWithHint("バリエーション", "フォーム違い")
                    if (!state.canHaveVariations) {
                        Text("種目を保存すると、フォーム違いを足せます。", style = AppTheme.typography.caption, color = colors.sub)
                    } else {
                        if (state.variations.isNotEmpty()) {
                            AppCard(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                                state.variations.forEachIndexed { index, row ->
                                    if (index > 0) AppDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 60.dp)
                                            .clickable(role = Role.Button) { onOpenVariation(row.id) }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(row.name, style = AppTheme.typography.bodyStrong, color = colors.fg)
                                            Text(row.detail, style = AppTheme.typography.caption, color = colors.sub)
                                        }
                                        Icon(AppIcons.Right, contentDescription = null, tint = colors.sub)
                                    }
                                }
                            }
                        }
                        TonalButton("＋ バリエーションを追加", onClick = { addingVariation = true }, enabled = exercise.name.isNotBlank(), modifier = Modifier.fillMaxWidth())
                        Text("追加したフォームは、部位・器具・数え方・調整項目を引き継ぎます。", style = AppTheme.typography.caption, color = colors.sub)
                        if (state.variations.isNotEmpty() && state.ownRecordCount > 0) {
                            OutlineButton(
                                "これまでの記録を、あるフォームへまとめて移す",
                                onClick = { choosingMoveTarget = true },
                                textStyle = AppTheme.typography.bodyStrong,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            var memo by remember(exercise.id) { mutableStateOf(exercise.memo) }
            OutlinedTextField(
                value = memo,
                onValueChange = { v ->
                    memo = v
                    onEdit { it.copy(memo = v) }
                },
                label = { Text("メモ（フォームの注意点など）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (!state.isNew) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlineButton("この種目を削除", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
                    Text("記録がある種目は、削除せずに非表示にします。過去の記録は残ります。", style = AppTheme.typography.caption, color = colors.sub)
                }
            }
        }
        // 保存は、スクロールする内容の外（画面の下）に固定する。入力欄にフォーカスがあって
        // キーボードが出ていても、いつも見えて、押せるように（キーボードの分は、画面全体が縮む）
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AppDivider()
            PrimaryButton(
                "保存",
                onClick = onSave,
                enabled = exercise.name.isNotBlank(),
                minHeight = 60.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
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
    if (addingVariation) {
        TextInputDialog(
            title = "バリエーションを追加",
            initialText = "",
            label = "フォームの名前（例: 膝をベンチにつく）",
            onConfirm = {
                addingVariation = false
                onAddVariation(it)
            },
            onDismiss = { addingVariation = false },
        )
    }
    if (choosingMoveTarget) {
        AlertDialog(
            onDismissRequest = { choosingMoveTarget = false },
            title = { Text("どのフォームへ移しますか？") },
            text = {
                Column {
                    Text("この種目に付いている ${state.ownRecordCount} 回分の記録を、選んだフォームの記録にします。", style = AppTheme.typography.caption, color = colors.sub)
                    state.variations.forEach { row ->
                        Text(
                            row.name,
                            style = AppTheme.typography.bodyStrong,
                            color = colors.fg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) {
                                    choosingMoveTarget = false
                                    moveTarget = row
                                }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingMoveTarget = false }) { Text("キャンセル") } },
        )
    }
    moveTarget?.let { target ->
        ConfirmDialog(
            title = "記録を「${target.name}」へ移しますか？",
            message = "${state.ownRecordCount} 回分の記録が、このフォームの記録になります。元には戻せません。",
            confirmText = "移す",
            onConfirm = {
                moveTarget = null
                onMoveRecords(target.id)
            },
            onDismiss = { moveTarget = null },
        )
    }
}

/** 調整項目 1 つの入力（項目名・数値か文字か・単位）。 */
@Composable
private fun FieldCard(field: AdjustmentField, onEdit: ((AdjustmentField) -> AdjustmentField) -> Unit, onRemove: () -> Unit) {
    // 入力欄の文字は、その場で持つ（名前の欄と同じ理由）
    var name by remember(field.id) { mutableStateOf(field.name) }
    var unit by remember(field.id) { mutableStateOf(field.unit) }
    AppCard(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { v ->
                        name = v
                        onEdit { it.copy(name = v) }
                    },
                    label = { Text("項目名") },
                    placeholder = { Text("例: ベンチ角度") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(AppIcons.Trash, "この調整項目を消す", onRemove, tint = AppTheme.colors.sub)
            }
            Column(modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val types = AdjustmentValueType.entries
                AppSegmented(
                    options = types.map { if (it == AdjustmentValueType.Number) "数値" else "文字" },
                    selectedIndex = types.indexOf(field.valueType),
                    onSelect = { index -> onEdit { it.copy(valueType = types[index]) } },
                )
                if (field.valueType == AdjustmentValueType.Number) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { v ->
                            unit = v
                            onEdit { it.copy(unit = v) }
                        },
                        label = { Text("単位（なくてもよい）") },
                        placeholder = { Text("例: °") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 「調整項目　ベンチ角度・シート位置など」のように、見出しの横に、小さな補足を並べる。 */
@Composable
private fun LabelWithHint(label: String, hint: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        Text(hint, style = AppTheme.typography.caption, color = AppTheme.colors.sub)
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
