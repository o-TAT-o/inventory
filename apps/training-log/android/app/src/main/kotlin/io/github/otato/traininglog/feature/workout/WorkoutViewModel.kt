package io.github.otato.traininglog.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.logic.RestTimes
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.AddSet
import io.github.otato.traininglog.ui.common.formatMmSs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToLong

/** 画面に伝える一度きりの出来事（状態ではなく、音を鳴らす・画面を移るなど）。 */
sealed interface WorkoutEvent {
    /** 休憩の時間になった（音・バイブ） */
    data object RestFinished : WorkoutEvent

    /** セットを削除した（元に戻す案内を出す。F-WO-17） */
    data class SetDeleted(val message: String) : WorkoutEvent

    /** ワークアウトを終えた（完了画面へ） */
    data class Finished(val workoutId: String) : WorkoutEvent

    /** 完了したセットがなく、記録を破棄した */
    data object Discarded : WorkoutEvent

    /** 終えたあとの記録を閉じる */
    data object Closed : WorkoutEvent
}

/**
 * 記録画面（最重要の画面）の ViewModel。
 *
 * - 入力は下のパネルで行う。± を押すと画面は即座に変わり、保存は少し遅れて追いつく（F-WO-07）。
 * - 「完了」でセットを記録し、休憩タイマーを始める（F-TM-01）。休憩の長さは、完了したセットで決まる（F-TM-05）。
 * - 画面を閉じても、記録はすべて DB に残っている。
 */
class WorkoutViewModel(
    private val workoutId: String,
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
    gearRepository: GearRepository,
    settingsRepository: SettingsRepository,
    private val addSetUseCase: AddSet,
    private val clock: Clock,
) : ViewModel() {

    private sealed interface Source {
        data object Loading : Source
        data class Value(val detail: WorkoutDetail?) : Source
    }

    private data class Local(
        val focusedWorkoutExerciseId: String? = null,
        val selectedSetId: String? = null,
        val rest: RestState? = null,
    )

    private data class RestState(
        val endAtMillis: Long,
        val totalSec: Int,
        val notified: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private val draft = MutableStateFlow<Draft?>(null)
    private val contexts = MutableStateFlow<Map<String, ExerciseContext>>(emptyMap())
    private var lastDeleted: SetRecord? = null

    private val settings: StateFlow<AppSettings> =
        settingsRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val source: StateFlow<Source> = workouts.observeWorkout(workoutId)
        .map<WorkoutDetail?, Source> { Source.Value(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Source.Loading)

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock.millis())
            delay(TICK_MILLIS)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    /** 入力値の保存。± を速く押しても、最後の値だけが書かれる。 */
    private val persistQueue = Channel<io.github.otato.traininglog.domain.model.SetValues>(Channel.CONFLATED)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val routineInfo: Flow<Pair<String?, String?>> = source
        .filterIsInstance<Source.Value>()
        .map { it.detail?.workout?.routineId }
        .distinctUntilChanged()
        .flatMapLatest { routineId ->
            if (routineId == null) flowOf(null to null) else routines.observeRoutine(routineId).map { it?.routine?.name to it?.routine?.memo }
        }

    val state: StateFlow<WorkoutUiState> = combine(
        combine(source, settings, contexts) { s, settings, contexts -> Triple(s, settings, contexts) },
        local,
        draft,
        gearRepository.observeGear(),
        routineInfo,
    ) { (source, settings, contexts), local, draft, gear, routine ->
        when (source) {
            Source.Loading -> WorkoutUiState()
            is Source.Value -> source.detail?.let { detail ->
                WorkoutUiMapper.map(
                    detail = detail,
                    settings = settings,
                    contexts = contexts,
                    focusedWorkoutExerciseId = local.focusedWorkoutExerciseId,
                    selectedSetId = local.selectedSetId,
                    draft = draft,
                    gearCatalog = gear,
                    routineName = routine.first,
                    routineMemo = routine.second,
                    today = DayBoundary.today(clock, settings.dayBoundaryHour),
                )
            } ?: WorkoutUiState(loaded = true, missing = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    /** 休憩タイマーの表示（1 秒ごとに変わるので、状態とは別に出す）。 */
    val rest: StateFlow<RestUi?> = combine(local.map { it.rest }.distinctUntilChanged(), ticker) { rest, now ->
        rest?.let {
            val remaining = (it.endAtMillis - now + 999) / 1000
            if (remaining > 0) {
                RestUi(ended = false, text = formatMmSs(remaining), progress = 1f - remaining.toFloat() / it.totalSec.coerceAtLeast(1))
            } else {
                RestUi(ended = true, text = "+" + formatMmSs((now - it.endAtMillis) / 1000), progress = 1f)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** ワークアウトの経過時間（「24:10」）。 */
    val elapsed: StateFlow<String> = combine(
        source.filterIsInstance<Source.Value>().mapNotNull { it.detail?.workout },
        ticker,
    ) { workout, now ->
        val end = workout.finishedAt?.toEpochMilli() ?: now
        formatMmSs((end - workout.startedAt.toEpochMilli()) / 1000)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0:00")

    init {
        // 入力値の保存
        viewModelScope.launch {
            for (values in persistQueue) workouts.updateSetValues(values)
        }
        // 種目ごとの前回の記録と自己ベストを読む（このワークアウトより前のもの）
        viewModelScope.launch {
            source.filterIsInstance<Source.Value>()
                .mapNotNull { it.detail }
                .map { detail -> detail.exercises.map { it.exercise }.distinctBy { it.id } }
                .distinctUntilChanged()
                .collect { exercises ->
                    val missing = exercises.filter { it.id !in contexts.value }
                    if (missing.isNotEmpty()) {
                        val loaded = missing.associate { exercise ->
                            exercise.id to ExerciseContext(
                                previous = workouts.previousRecord(exercise.id, excludeWorkoutId = workoutId),
                                bestBefore = workouts.personalBest(exercise, excludeWorkoutId = workoutId),
                            )
                        }
                        contexts.update { it + loaded }
                    }
                }
        }
        // 休憩の時間になったら、音とバイブで知らせる
        viewModelScope.launch {
            ticker.collect { now ->
                val rest = local.value.rest
                if (rest != null && !rest.notified && now >= rest.endAtMillis) {
                    local.update { it.copy(rest = it.rest?.copy(notified = true)) }
                    _events.emit(WorkoutEvent.RestFinished)
                }
            }
        }
    }

    // ---- 見ている種目・セットの切り替え ----

    fun selectSet(setId: String) {
        local.update { it.copy(selectedSetId = setId) }
    }

    fun focusExercise(workoutExerciseId: String) {
        local.update { it.copy(focusedWorkoutExerciseId = workoutExerciseId, selectedSetId = null) }
    }

    /** 下のパネルの ‹ ›。未完了のセットがある種目の中で、前後に移る。 */
    fun shiftExercise(direction: Int) {
        val detail = currentDetail() ?: return
        val target = resolveTarget(detail) ?: return
        val open = detail.exercises.filter { it.currentSet != null }
        val index = open.indexOfFirst { it.workoutExercise.id == target.exercise.workoutExercise.id }
        val next = open.getOrNull(index + direction) ?: return
        focusExercise(next.workoutExercise.id)
    }

    // ---- 入力 ----

    fun adjust(key: FieldKey, direction: Int) {
        val detail = currentDetail() ?: return
        val target = resolveTarget(detail) ?: return
        val exercise = target.exercise.exercise
        val base = currentDraft(target)
        val settings = settings.value
        val next = when (key) {
            FieldKey.Weight -> {
                val stepKg = exercise.weightStepKg ?: WeightUnits.fromDisplay(settings.weightStep, settings.weightUnit)
                val lowest = if (exercise.recordType == RecordType.Bodyweight) -MAX_ASSIST_KG else 0.0
                base.copy(weightKg = roundKg((base.weightKg + direction * stepKg).coerceAtLeast(lowest)))
            }
            FieldKey.Reps -> base.copy(reps = (base.reps + direction).coerceAtLeast(0))
            FieldKey.Duration -> {
                val step = if (exercise.recordType == RecordType.DistanceTime) DISTANCE_TIME_STEP_SEC else DURATION_STEP_SEC
                base.copy(durationSec = ((base.durationSec ?: 0) + direction * step).coerceAtLeast(0))
            }
            FieldKey.Distance -> base.copy(distanceM = ((base.distanceM ?: 0.0) + direction * DISTANCE_STEP_M).coerceAtLeast(0.0))
        }
        apply(next)
    }

    /** 数値キーボードで直接入力した値。重量は表示している単位の値、時間（距離 × 時間の種目）は分。 */
    fun input(key: FieldKey, value: Double) {
        val detail = currentDetail() ?: return
        val target = resolveTarget(detail) ?: return
        val exercise = target.exercise.exercise
        val base = currentDraft(target)
        val next = when (key) {
            FieldKey.Weight -> {
                val lowest = if (exercise.recordType == RecordType.Bodyweight) -MAX_ASSIST_KG else 0.0
                base.copy(weightKg = roundKg(WeightUnits.fromDisplay(value, settings.value.weightUnit).coerceAtLeast(lowest)))
            }
            FieldKey.Reps -> base.copy(reps = value.roundToLong().toInt().coerceAtLeast(0))
            FieldKey.Duration -> {
                val seconds = if (exercise.recordType == RecordType.DistanceTime) value * 60 else value
                base.copy(durationSec = seconds.roundToLong().toInt().coerceAtLeast(0))
            }
            FieldKey.Distance -> base.copy(distanceM = (value * 1000).coerceAtLeast(0.0))
        }
        apply(next)
    }

    /** 「完了」。セットを記録し、休憩タイマーを始める。完了したセットを直していたときは、直し終えるだけ。 */
    fun complete() {
        viewModelScope.launch {
            val detail = currentDetail() ?: return@launch
            val target = resolveTarget(detail) ?: return@launch
            if (target.editingCompleted) {
                workouts.updateSetValues(currentDraft(target).toValues())
                local.update { it.copy(selectedSetId = null) }
                return@launch
            }
            workouts.updateSetValues(currentDraft(target).toValues())
            workouts.setCompleted(target.set.id, true, clock.instant())

            val exercise = target.exercise
            val remainingHere = exercise.sets.count { !it.completed && it.id != target.set.id }
            val remainingElsewhere = detail.exercises.any {
                it.workoutExercise.id != exercise.workoutExercise.id && it.currentSet != null
            }
            val settings = settings.value
            val restSeconds = if (!detail.workout.isFinished && settings.autoStartRest && (remainingHere > 0 || remainingElsewhere)) {
                RestTimes.afterSet(
                    isLastSetOfExercise = remainingHere == 0,
                    workoutExercise = exercise.workoutExercise,
                    settings = settings,
                )
            } else {
                null
            }
            local.update {
                it.copy(
                    selectedSetId = null,
                    // 同じ種目に続きのセットがあれば、その種目のまま進む
                    focusedWorkoutExerciseId = exercise.workoutExercise.id.takeIf { remainingHere > 0 },
                    rest = restSeconds?.let { seconds -> RestState(clock.millis() + seconds * 1000L, seconds) },
                )
            }
        }
    }

    /** 「＋セット」。いま見ている種目に、直前のセットの値で 1 セット足す（F-WO-16）。 */
    fun addSet() {
        viewModelScope.launch {
            val detail = currentDetail() ?: return@launch
            val exercise = resolveTarget(detail)?.exercise ?: detail.exercises.lastOrNull() ?: return@launch
            addSetUseCase(exercise.workoutExercise.id, exercise.exercise, exercise.sets)
            local.update { it.copy(focusedWorkoutExerciseId = exercise.workoutExercise.id, selectedSetId = null) }
        }
    }

    /** 行を左にスワイプして削除する。直後に取り消せる（F-WO-17、F-UI-03）。 */
    fun deleteSet(setId: String) {
        viewModelScope.launch {
            val detail = currentDetail() ?: return@launch
            val set = detail.exercises.flatMap { it.sets }.firstOrNull { it.id == setId } ?: return@launch
            lastDeleted = set
            workouts.deleteSet(setId)
            local.update { if (it.selectedSetId == setId) it.copy(selectedSetId = null) else it }
            _events.emit(WorkoutEvent.SetDeleted("セットを削除しました"))
        }
    }

    fun undoDelete() {
        val set = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { workouts.addSet(set) }
    }

    // ---- 休憩タイマー ----

    /** 休憩の延長・短縮（「±10 秒」「＋30 秒」）。終わったあとに延ばすと、そこからもう一度数える。 */
    fun adjustRest(deltaSeconds: Int) {
        val now = clock.millis()
        local.update { state ->
            val rest = state.rest ?: return@update state
            if (now >= rest.endAtMillis) {
                if (deltaSeconds <= 0) state else state.copy(rest = RestState(now + deltaSeconds * 1000L, deltaSeconds))
            } else {
                val end = max(now, rest.endAtMillis + deltaSeconds * 1000L)
                state.copy(rest = rest.copy(endAtMillis = end, totalSec = max(1, rest.totalSec + deltaSeconds), notified = false))
            }
        }
    }

    fun dismissRest() {
        local.update { it.copy(rest = null) }
    }

    // ---- 種目の詳細（ギア・数え方・メモ） ----

    fun toggleGear(workoutExerciseId: String, gearId: String) {
        viewModelScope.launch {
            val exercise = currentDetail()?.exercises?.firstOrNull { it.workoutExercise.id == workoutExerciseId } ?: return@launch
            val next = if (gearId in exercise.gearIds) exercise.gearIds - gearId else exercise.gearIds + gearId
            workouts.setGear(workoutExerciseId, next)
        }
    }

    fun setModes(workoutExerciseId: String, weightMode: WeightMode, repMode: RepMode) {
        viewModelScope.launch { workouts.updateExerciseModes(workoutExerciseId, weightMode, repMode) }
    }

    fun setExerciseMemo(workoutExerciseId: String, memo: String) {
        viewModelScope.launch { workouts.updateExerciseMemo(workoutExerciseId, memo) }
    }

    fun removeExercise(workoutExerciseId: String) {
        viewModelScope.launch {
            workouts.removeExercise(workoutExerciseId)
            local.update {
                if (it.focusedWorkoutExerciseId == workoutExerciseId) it.copy(focusedWorkoutExerciseId = null, selectedSetId = null) else it
            }
        }
    }

    // ---- ワークアウト全体 ----

    fun changeDate(date: LocalDate) {
        viewModelScope.launch { workouts.updateDate(workoutId, date) }
    }

    /** ワークアウトを終える。未完了のセットは記録に含めない（F-WO-18）。完了したセットがなければ破棄する。 */
    fun finish() {
        viewModelScope.launch {
            val detail = currentDetail() ?: return@launch
            if (detail.workout.isFinished) {
                _events.emit(WorkoutEvent.Closed)
                return@launch
            }
            resolveTarget(detail)?.let { workouts.updateSetValues(currentDraft(it).toValues()) }
            val kept = workouts.finishWorkout(workoutId, clock.instant())
            local.update { it.copy(rest = null) }
            _events.emit(if (kept) WorkoutEvent.Finished(workoutId) else WorkoutEvent.Discarded)
        }
    }

    // ---- 内部 ----

    private fun currentDetail(): WorkoutDetail? = (source.value as? Source.Value)?.detail

    private fun resolveTarget(detail: WorkoutDetail): DockTarget? =
        WorkoutUiMapper.resolveTarget(detail, local.value.focusedWorkoutExerciseId, local.value.selectedSetId)

    private fun currentDraft(target: DockTarget): Draft =
        draft.value?.takeIf { it.setId == target.set.id } ?: Draft.of(target.set)

    private fun apply(next: Draft) {
        draft.value = next
        persistQueue.trySend(next.toValues())
    }

    private fun roundKg(kg: Double): Double = (kg * 1_000_000).roundToLong() / 1_000_000.0

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MAX_ASSIST_KG = 200.0
        const val DURATION_STEP_SEC = 5
        const val DISTANCE_TIME_STEP_SEC = 30
        const val DISTANCE_STEP_M = 500.0
    }
}
