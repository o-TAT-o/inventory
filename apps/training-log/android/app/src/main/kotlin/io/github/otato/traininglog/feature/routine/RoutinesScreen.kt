package io.github.otato.traininglog.feature.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppSegmented
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.ReorderHandle
import io.github.otato.traininglog.designsystem.components.ReorderableRow
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.components.rememberReorderState
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.logic.NextRoutine
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.label
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class RoutineRowUi(
    val id: String,
    val name: String,
    /** 「ベンチプレス・インクラインダンベルプレス」 */
    val exerciseNames: String,
    /** 「2 種目 · 前回 11/7（土）」 */
    val summary: String,
    /** 次にやるルーティンの提案（F-RT-07）。「次」の印を付ける */
    val isNext: Boolean,
)

data class ExerciseRowUi(val id: String, val name: String, val meta: String)

data class RoutinesUiState(
    val loaded: Boolean = false,
    val rows: List<RoutineRowUi> = emptyList(),
    val exercises: List<ExerciseRowUi> = emptyList(),
)

sealed interface RoutinesEvent {
    data class OpenRoutine(val routineId: String) : RoutinesEvent
    data class OpenWorkout(val workoutId: String) : RoutinesEvent

    /** 並べ替えた。直後なら、元に戻せる（F-UI-03）。 */
    data object Reordered : RoutinesEvent
}

/**
 * ルーティンの一覧と、種目の一覧（F-RT-01、F-UI-01、F-EX-02）。ルーティンは、作ると、そのまま編集画面を開く。
 * 並べ替えは、つまみをドラッグして行い、直後なら取り消せる。
 */
class RoutinesViewModel(
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
    settings: SettingsRepository,
    private val startWorkout: StartWorkout,
    private val clock: Clock,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {

    private val settingsFlow = settings.observe()

    val state: StateFlow<RoutinesUiState> = combine(
        routines.observeRoutines(),
        workouts.observeLastRoutineId(),
        workouts.observeLastDateByRoutine(),
        exercises.observeExercises(),
        combine(exercises.observeRecordCounts(), settingsFlow) { counts, s -> counts to s },
    ) { list, lastRoutineId, lastDates, allExercises, _ ->
        val next = NextRoutine.suggest(list.map { it.routine }, lastRoutineId)
        val topLevel = allExercises.filter { !it.isVariation }
        val variationCounts = allExercises.filter { it.isVariation }.groupingBy { it.parentId }.eachCount()
        RoutinesUiState(
            loaded = true,
            rows = list.map { detail ->
                val names = detail.items.map { it.displayName }
                val last = lastDates[detail.routine.id]?.let { "前回 ${formatDateWithDow(it)}" }
                RoutineRowUi(
                    id = detail.routine.id,
                    name = detail.routine.name,
                    exerciseNames = names.take(3).joinToString("・") + if (names.size > 3) " ほか" else "",
                    summary = if (names.isEmpty()) "種目がありません" else listOfNotNull("${names.size} 種目", last).joinToString(" · "),
                    isNext = next?.id == detail.routine.id,
                )
            },
            exercises = topLevel.map { exercise ->
                ExerciseRowUi(
                    id = exercise.id,
                    name = exercise.name,
                    meta = listOfNotNull(
                        exercise.bodyPart.label(),
                        exercise.equipment.label(),
                        variationCounts[exercise.id]?.let { "バリエーション $it" },
                    ).joinToString(" · "),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    private val _events = MutableSharedFlow<RoutinesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RoutinesEvent> = _events.asSharedFlow()

    /** 並べ替える前の並び。元に戻すために持つ。 */
    private var undoRoutines: List<String>? = null
    private var undoExercises: List<String>? = null

    fun create(name: String) {
        viewModelScope.launch {
            val id = ids.next()
            routines.saveRoutine(Routine(id = id, name = name, sortOrder = routines.nextRoutineSortOrder()))
            _events.emit(RoutinesEvent.OpenRoutine(id))
        }
    }

    /** ルーティンを選んで、今日のワークアウトを始める（一覧の ▶）。 */
    fun start(routineId: String) {
        viewModelScope.launch {
            val today = DayBoundary.today(clock, settingsFlow.first().dayBoundaryHour)
            _events.emit(RoutinesEvent.OpenWorkout(startWorkout(today, routineId)))
        }
    }

    fun reorderRoutines(orderedIds: List<String>) {
        viewModelScope.launch {
            undoRoutines = state.value.rows.map { it.id }
            undoExercises = null
            routines.reorderRoutines(orderedIds)
            _events.emit(RoutinesEvent.Reordered)
        }
    }

    fun reorderExercises(orderedIds: List<String>) {
        viewModelScope.launch {
            undoExercises = state.value.exercises.map { it.id }
            undoRoutines = null
            exercises.reorder(orderedIds)
            _events.emit(RoutinesEvent.Reordered)
        }
    }

    /** 直前の並べ替えを、元に戻す。 */
    fun undoReorder() {
        viewModelScope.launch {
            undoRoutines?.let { routines.reorderRoutines(it) }
            undoExercises?.let { exercises.reorder(it) }
            undoRoutines = null
            undoExercises = null
        }
    }
}

@Composable
fun RoutinesRoute(
    onOpenRoutine: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
) {
    val viewModel = appViewModel<RoutinesViewModel> {
        RoutinesViewModel(it.data.routines, it.data.workouts, it.data.exercises, it.data.settings, it.startWorkout, it.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RoutinesEvent.OpenRoutine -> onOpenRoutine(event.routineId)
                is RoutinesEvent.OpenWorkout -> onOpenWorkout(event.workoutId)
                RoutinesEvent.Reordered -> {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar("並べ替えました", actionLabel = "元に戻す") == SnackbarResult.ActionPerformed) {
                        viewModel.undoReorder()
                    }
                }
            }
        }
    }
    RoutinesScreen(
        state = state,
        snackbar = snackbar,
        onOpen = onOpenRoutine,
        onCreate = viewModel::create,
        onStart = viewModel::start,
        onReorderRoutines = viewModel::reorderRoutines,
        onOpenExercise = onOpenExercise,
        onCreateExercise = onCreateExercise,
        onReorderExercises = viewModel::reorderExercises,
    )
}

@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    snackbar: SnackbarHostState,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    onStart: (String) -> Unit,
    onReorderRoutines: (List<String>) -> Unit,
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
    onReorderExercises: (List<String>) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("ルーティン")
        AppSegmented(
            options = listOf("ルーティン", "種目"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (tab == 0) {
                RoutineList(state, onOpen, onStart, onReorderRoutines)
            } else {
                ExerciseList(state, onOpenExercise, onReorderExercises)
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        if (tab == 0) {
            PrimaryButton(
                "ルーティンを作る",
                onClick = { creating = true },
                icon = AppIcons.Plus,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        } else {
            OutlineButton(
                "新しい種目",
                onClick = onCreateExercise,
                icon = AppIcons.Plus,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
    if (creating) {
        TextInputDialog(
            title = "新しいルーティン",
            initialText = "",
            label = "名前（例: Push、Day A）",
            onConfirm = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun RoutineList(
    state: RoutinesUiState,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val colors = AppTheme.colors
    if (state.loaded && state.rows.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ルーティンはまだありません", style = AppTheme.typography.heading, color = colors.fg)
            Text(
                "「Push」「Day A」のように、いつもの種目をまとめておくと、ワンタップで始められます。",
                style = AppTheme.typography.body,
                color = colors.sub,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }
    val reorder = rememberReorderState(source = state.rows, key = { it.id }, onCommit = { onReorder(it.map { row -> row.id }) })
    LazyColumn(state = reorder.listState, modifier = Modifier.fillMaxSize()) {
        items(reorder.items, key = { it.id }) { row ->
            ReorderableRow(reorder, key = row.id) { handle, _ ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 80.dp)
                            .clickable(role = Role.Button) { onOpen(row.id) }
                            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReorderHandle(handle)
                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.name, style = AppTheme.typography.heading, color = colors.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (row.isNext) {
                                    Text(
                                        "次",
                                        style = AppTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        color = colors.onPrimary,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.primary)
                                            .padding(horizontal = 8.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            if (row.exerciseNames.isNotEmpty()) {
                                Text(row.exerciseNames, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(row.summary, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.container)
                                .clickable(role = Role.Button) { onStart(row.id) }
                                .semantics { contentDescription = "${row.name}を始める" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Play, contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(22.dp))
                        }
                    }
                    AppDivider()
                }
            }
        }
    }
}

@Composable
private fun ExerciseList(
    state: RoutinesUiState,
    onOpen: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val colors = AppTheme.colors
    val reorder = rememberReorderState(source = state.exercises, key = { it.id }, onCommit = { onReorder(it.map { row -> row.id }) })
    LazyColumn(state = reorder.listState, modifier = Modifier.fillMaxSize()) {
        items(reorder.items, key = { it.id }) { row ->
            ReorderableRow(reorder, key = row.id) { handle, _ ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .clickable(role = Role.Button) { onOpen(row.id) }
                            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReorderHandle(handle)
                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(row.name, style = AppTheme.typography.bodyStrong, color = colors.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.meta, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1)
                        }
                        Icon(AppIcons.Right, contentDescription = null, tint = colors.sub, modifier = Modifier.size(20.dp))
                    }
                    AppDivider()
                }
            }
        }
    }
}
