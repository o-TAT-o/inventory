package io.github.otato.traininglog.feature.done

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.PersonalBests
import io.github.otato.traininglog.domain.logic.Volume
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.SaveWorkoutAsRoutine
import io.github.otato.traininglog.feature.workout.WorkoutUiMapper
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.SetsLine
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateLong
import io.github.otato.traininglog.ui.common.formatMonthDay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.math.roundToLong

/** 自己ベストを更新した種目。「ベンチプレス　62.5 kg 〔合計〕× 10」「最大重量の更新　これまで 60 kg 合計」 */
data class DoneBestUi(val name: String, val line: SetsLine, val note: String)

/** 種目ごとの 1 行。「ベンチプレス」「62.5 kg 合計 × 10 · 9 · 9 · リストラップ」 */
data class DoneExerciseUi(val name: String, val line: SetsLine, val extras: String)

data class DoneUiState(
    val loaded: Boolean = false,
    /** ルーティンの名前。ルーティンなしなら「自由に記録」 */
    val title: String = "",
    val dateText: String = "",
    /** 時間（分）。あとから入力の記録では出さない */
    val minutesText: String? = null,
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val volumeNumber: String = "",
    val volumeUnit: String = "",
    val personalBests: List<DoneBestUi> = emptyList(),
    val exercises: List<DoneExerciseUi> = emptyList(),
    val memo: String = "",
    /** 「ルーティンとして保存」の、名前の初期値 */
    val suggestedRoutineName: String = "",
)

/** ワークアウト完了の要約（時間・種目・セット・ボリューム・自己ベスト・種目ごと・メモ）と、ルーティンとして保存。 */
class WorkoutDoneViewModel(
    private val workoutId: String,
    private val workouts: WorkoutRepository,
    routines: RoutineRepository,
    gear: GearRepository,
    settingsRepository: SettingsRepository,
    private val saveAsRoutine: SaveWorkoutAsRoutine,
) : ViewModel() {
    private val _state = MutableStateFlow(DoneUiState())
    val state: StateFlow<DoneUiState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.get()
            val detail = workouts.observeWorkout(workoutId).first() ?: return@launch
            val unit = settings.weightUnit
            val gearNames = gear.observeGear(includeHidden = true).first().associate { it.id to it.name }
            val routineName = detail.workout.routineId?.let { routines.getRoutine(it)?.routine?.name }
            val done = detail.exercises.filter { it.completedSets.isNotEmpty() }
            val minutes = detail.workout.finishedAt
                ?.takeIf { !detail.workout.quickEntry }
                ?.let { Duration.between(detail.workout.startedAt, it).toMinutes() }

            val bests = done.mapNotNull { exercise ->
                val before = workouts.personalBest(exercise.exercise, excludeWorkoutId = workoutId)
                val ids = PersonalBests.newBestSetIds(exercise.sets, exercise.exercise.recordType, exercise.exercise.useAddedWeight, before)
                val set = exercise.sets.firstOrNull { it.id in ids } ?: return@mapNotNull null
                val line = RecordFormat.setsLine(listOf(set), exercise.exercise, exercise.workoutExercise.weightMode, exercise.workoutExercise.repMode, unit)
                    ?: return@mapNotNull null
                DoneBestUi(exercise.fullName, line, WorkoutUiMapper.pbNote(exercise, before, unit).orEmpty())
            }
            val volume = WeightUnits.toDisplay(done.sumOf { Volume.ofExercise(it) }, unit).roundToLong()
            _state.value = DoneUiState(
                loaded = true,
                title = routineName ?: "自由に記録",
                dateText = formatDateLong(detail.workout.date),
                minutesText = minutes?.toString(),
                exerciseCount = done.size,
                setCount = done.sumOf { it.completedSets.size },
                volumeNumber = "%,d".format(volume),
                volumeUnit = WeightUnits.label(unit),
                personalBests = bests,
                exercises = done.mapNotNull { exercise ->
                    val line = RecordFormat.setsLine(exercise.sets, exercise.exercise, exercise.workoutExercise.weightMode, exercise.workoutExercise.repMode, unit)
                        ?: return@mapNotNull null
                    val gearText = exercise.gearIds.mapNotNull(gearNames::get)
                    val adjustText = exercise.adjustmentFields.mapNotNull { field ->
                        exercise.adjustments[field.id]?.let { WorkoutUiMapper.adjustmentText(field, it) }
                    }
                    DoneExerciseUi(exercise.fullName, line, (gearText + adjustText).joinToString(" · "))
                },
                memo = detail.workout.memo,
                suggestedRoutineName = routineName?.let { "$it 2" } ?: "${formatMonthDay(detail.workout.date)} のメニュー",
            )
        }
    }

    /** メモは、打つたびに保存する。 */
    fun setMemo(memo: String) {
        _state.update { it.copy(memo = memo) }
        viewModelScope.launch { workouts.updateMemo(workoutId, memo) }
    }

    fun saveAsRoutine(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = saveAsRoutine(workoutId, name)
            _messages.emit(if (id != null) "ルーティン「${name.trim()}」を作りました" else "保存できる種目がありません")
        }
    }
}

@Composable
fun WorkoutDoneRoute(workoutId: String, onClose: () -> Unit) {
    val viewModel = appViewModel<WorkoutDoneViewModel>(key = "done-$workoutId") { container ->
        WorkoutDoneViewModel(
            workoutId = workoutId,
            workouts = container.data.workouts,
            routines = container.data.routines,
            gear = container.data.gear,
            settingsRepository = container.data.settings,
            saveAsRoutine = SaveWorkoutAsRoutine(container.data.workouts, container.data.routines),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { launch { snackbar.showSnackbar(it) } } }
    WorkoutDoneScreen(state, snackbar, onClose, viewModel::setMemo, viewModel::saveAsRoutine)
}

@Composable
fun WorkoutDoneScreen(
    state: DoneUiState,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onMemo: (String) -> Unit,
    onSaveAsRoutine: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var naming by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("おつかれさまでした", onBack = onClose)
        Box(modifier = Modifier.weight(1f)) {
            if (state.loaded) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.title, style = AppTheme.typography.title.copy(fontWeight = FontWeight.Bold), color = colors.fg, modifier = Modifier.weight(1f, fill = false))
                        Text(state.dateText, style = AppTheme.typography.caption, color = colors.sub, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.minutesText?.let { StatCard("時間", it, "分", Modifier.weight(1f)) }
                        StatCard("種目", state.exerciseCount.toString(), null, Modifier.weight(1f))
                        StatCard("セット", state.setCount.toString(), null, Modifier.weight(1f))
                        StatCard("ボリューム", state.volumeNumber, state.volumeUnit, Modifier.weight(1.3f))
                    }
                    if (state.personalBests.isNotEmpty()) {
                        SectionLabel("自己ベスト", modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                        AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                            state.personalBests.forEachIndexed { index, best ->
                                if (index > 0) AppDivider()
                                BestRow(best)
                            }
                        }
                    }
                    if (state.exercises.isNotEmpty()) {
                        SectionLabel("種目ごと", modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                        AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                            state.exercises.forEachIndexed { index, exercise ->
                                if (index > 0) AppDivider()
                                ExerciseRow(exercise)
                            }
                        }
                    }
                    SectionLabel("ワークアウトのメモ", modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                    OutlinedTextField(
                        value = state.memo,
                        onValueChange = onMemo,
                        placeholder = { Text("今日の記録にメモを書く") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("記録はいつでも、履歴から見直せます", style = AppTheme.typography.caption, color = colors.sub)
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.exerciseCount > 0) {
                OutlineButton("ルーティンとして保存", onClick = { naming = true }, icon = AppIcons.List, modifier = Modifier.fillMaxWidth())
            }
            PrimaryButton("ホームへ", onClick = onClose, minHeight = 60.dp, modifier = Modifier.fillMaxWidth())
        }
    }

    if (naming) {
        TextInputDialog(
            title = "ルーティンとして保存",
            initialText = state.suggestedRoutineName,
            label = "ルーティンの名前",
            onConfirm = {
                naming = false
                onSaveAsRoutine(it)
            },
            onDismiss = { naming = false },
        )
    }
}

/** 「時間　27 分」のような、小さな数字のカード。 */
@Composable
private fun StatCard(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier, cornerRadius = 16.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = AppTheme.typography.caption, color = colors.sub)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = AppTheme.typography.heading.copy(fontWeight = FontWeight.Bold), color = colors.fg, maxLines = 1)
                if (unit != null) {
                    Spacer(Modifier.width(2.dp))
                    Text(unit, style = AppTheme.typography.caption, color = colors.sub)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BestRow(best: DoneBestUi) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.5.dp, colors.fg, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Star, contentDescription = null, tint = colors.fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("自己ベスト", style = AppTheme.typography.caption, color = colors.fg, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(best.name, style = AppTheme.typography.bodyStrong, color = colors.fg)
                if (best.line.weightText.isNotEmpty()) Text(best.line.weightText, style = AppTheme.typography.bodyStrong, color = colors.fg)
                best.line.tag?.let { Tag(it) }
                Text(best.line.repsText, style = AppTheme.typography.bodyStrong, color = colors.fg)
            }
            if (best.note.isNotEmpty()) Text(best.note, style = AppTheme.typography.caption, color = colors.sub)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseRow(exercise: DoneExerciseUi) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(exercise.name, style = AppTheme.typography.bodyStrong, color = colors.fg)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (exercise.line.weightText.isNotEmpty()) Text(exercise.line.weightText, style = AppTheme.typography.caption, color = colors.sub)
            exercise.line.tag?.let { Tag(it) }
            Text(exercise.line.repsText, style = AppTheme.typography.caption, color = colors.sub)
            if (exercise.extras.isNotEmpty()) Text("· ${exercise.extras}", style = AppTheme.typography.caption, color = colors.sub)
        }
    }
}
