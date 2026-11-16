package io.github.otato.traininglog.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.logic.HomeStrip
import io.github.otato.traininglog.domain.logic.NextRoutine
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.RoutineDetail
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutSummary
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.ui.common.PreviousUi
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.formatVolume
import io.github.otato.traininglog.ui.common.jaShort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * ホーム（今日）の ViewModel。
 * 読むだけの情報（前回のワークアウト・次のルーティンの中身）と、操作するもの（日付の帯・開始ボタン）を組み立てる。
 */
class HomeViewModel(
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
    settingsRepository: SettingsRepository,
    private val startWorkout: StartWorkout,
    private val clock: Clock,
) : ViewModel() {

    private val settings: Flow<AppSettings> = settingsRepository.observe()

    /** 日付が変わるのに追いつくため、1 分ごとに「今日」を取り直す。 */
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MILLIS)
        }
    }

    private val today: Flow<LocalDate> = combine(settings, minuteTicker) { s, _ ->
        DayBoundary.today(clock, s.dayBoundaryHour)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val strip: Flow<Pair<List<LocalDate>, List<WorkoutSummary>>> =
        combine(today, settings) { today, s -> HomeStrip.days(today, s.homeStripMode, s.weekStart) }
            .distinctUntilChanged()
            .flatMapLatest { days -> workouts.observeSummaries(days.first(), days.last()).map { days to it } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lastFinished: Flow<Pair<WorkoutSummary, WorkoutDetail>?> =
        workouts.observeLastFinished().flatMapLatest { summary ->
            if (summary == null) {
                flowOf(null)
            } else {
                workouts.observeWorkout(summary.workout.id).map { detail -> detail?.let { summary to it } }
            }
        }

    private data class Inputs(val today: LocalDate, val settings: AppSettings, val routines: List<RoutineDetail>)
    private data class History(val last: Pair<WorkoutSummary, WorkoutDetail>?, val lastRoutineId: String?, val active: Workout?)

    val state: StateFlow<HomeUiState> = combine(
        combine(today, settings, routines.observeRoutines(), ::Inputs),
        combine(lastFinished, workouts.observeLastRoutineId(), workouts.observeActiveWorkout(), ::History),
        strip,
    ) { inputs, history, (days, summaries) ->
        build(inputs, history, days, summaries)
        // 他の画面から戻ったときに、古い表示が一瞬出ないように、ViewModel が生きている間は更新し続ける
    }.stateIn(viewModelScope, SharingStarted.Lazily, HomeUiState())

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    /** ワークアウトを始める。[date] は、始める日（今日以外もある。F-HI-05）。 */
    fun start(routineId: String?, date: LocalDate) {
        viewModelScope.launch {
            _events.emit(HomeEvent.OpenWorkout(startWorkout(date, routineId)))
        }
    }

    private suspend fun build(
        inputs: Inputs,
        history: History,
        days: List<LocalDate>,
        summaries: List<WorkoutSummary>,
    ): HomeUiState {
        val ordered = inputs.routines.sortedBy { it.routine.sortOrder }
        val suggested = NextRoutine.suggest(ordered.map { it.routine }, history.lastRoutineId)
        val next = suggested?.let { routine -> ordered.first { it.routine.id == routine.id } }
        val routineNames = ordered.associate { it.routine.id to it.routine.name }
        return HomeUiState(
            loaded = true,
            today = inputs.today,
            last = history.last?.let { (summary, detail) -> lastWorkoutUi(summary, detail, inputs.settings) },
            next = next?.let { nextRoutineUi(it, ordered, inputs.settings) },
            hasRoutines = ordered.isNotEmpty(),
            routines = ordered.map { RoutineChoice(it.routine.id, it.routine.name) },
            active = history.active?.let { workout ->
                ActiveUi(
                    workoutId = workout.id,
                    title = workout.routineId?.let(routineNames::get) ?: "自由に記録",
                    dateText = workout.date.takeIf { it != inputs.today }?.let(::formatDateWithDow),
                )
            },
            strip = days.map { date ->
                val summary = summaries.firstOrNull { it.workout.date == date }
                DayCellUi(
                    date = date,
                    dowLabel = date.dayOfWeek.jaShort(),
                    dayNumber = date.dayOfMonth,
                    label = summary?.let { it.routineName ?: "自由" } ?: "今日".takeIf { date == inputs.today },
                    isToday = date == inputs.today,
                    workoutId = summary?.workout?.id,
                )
            },
        )
    }

    private fun lastWorkoutUi(summary: WorkoutSummary, detail: WorkoutDetail, settings: AppSettings): LastWorkoutUi {
        val minutes = summary.workout.finishedAt?.let { Duration.between(summary.workout.startedAt, it).toMinutes() }
        val title = summary.routineName ?: "自由に記録"
        val stats = listOfNotNull(
            minutes?.let { "$it 分" },
            "${summary.completedSetCount} セット",
            formatVolume(summary.volumeKg, settings.weightUnit).takeIf { summary.volumeKg > 0 },
        ).joinToString(" · ")
        val headline = "$title　$stats"

        val withSets = detail.exercises.filter { it.completedSets.isNotEmpty() }
        val first = withSets.firstOrNull()
        val highlight = first?.let { exercise ->
            RecordFormat.setsLine(
                sets = exercise.sets,
                exercise = exercise.exercise,
                weightMode = exercise.workoutExercise.weightMode,
                repMode = exercise.workoutExercise.repMode,
                unit = settings.weightUnit,
            )?.let { line -> HighlightUi(exercise.exercise.name, line, hasMore = withSets.size > 1) }
        }
        return LastWorkoutUi(dateText = formatDateWithDow(summary.workout.date), headline = headline, highlight = highlight)
    }

    /** 次のルーティンの中身。行は最大 3 行で、4 種目以上は、先頭の 2 種目と「ほか N 種目」にする（F-RT-13）。 */
    private suspend fun nextRoutineUi(next: RoutineDetail, all: List<RoutineDetail>, settings: AppSettings): NextRoutineUi {
        val items = next.items.sortedBy { it.item.sortOrder }
        val shown = if (items.size <= MAX_ROWS) items else items.take(MAX_ROWS - 1)
        return NextRoutineUi(
            routineId = next.routine.id,
            name = next.routine.name,
            sequence = all.map { SequenceChip(it.routine.name, isNext = it.routine.id == next.routine.id) },
            items = shown.map { detail ->
                val previous = workouts.previousRecord(detail.exercise.id, excludeWorkoutId = null)
                val previousUi = previous?.let { record ->
                    RecordFormat.setsLine(record.sets, detail.exercise, record.weightMode, record.repMode, settings.weightUnit)
                        ?.let { PreviousUi.of(record, it) }
                }
                NextItemUi(
                    name = detail.exercise.name,
                    previous = previousUi,
                    setsText = "${detail.item.targetSets ?: previous?.sets?.size ?: DEFAULT_SETS} セット",
                )
            },
            moreCount = items.size - shown.size,
        )
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val MAX_ROWS = 3
        const val DEFAULT_SETS = 3
    }
}
