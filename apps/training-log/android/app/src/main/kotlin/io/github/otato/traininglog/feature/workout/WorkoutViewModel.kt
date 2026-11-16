package io.github.otato.traininglog.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.logic.RestTimes
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutExerciseDetail
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.AddSet
import io.github.otato.traininglog.domain.usecase.AddVariation
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.formatMmSs
import io.github.otato.traininglog.ui.common.formatMonthDay
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

    /** 取り消せる操作をした（セットの削除・種目を外す・種目の並べ替え）。元に戻す案内を出す（F-WO-17、F-UI-03） */
    data class Undoable(val message: String) : WorkoutEvent

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
    private val exercises: ExerciseRepository,
    private val gearRepository: GearRepository,
    settingsRepository: SettingsRepository,
    private val addSetUseCase: AddSet,
    private val clock: Clock,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {

    private sealed interface Source {
        data object Loading : Source
        data class Value(val detail: WorkoutDetail?) : Source
    }

    private data class Local(
        val focusedWorkoutExerciseId: String? = null,
        val selectedSetId: String? = null,
        val rest: RestState? = null,
        /** すべて完了してたたまれた種目のうち、タップして開いたもの */
        val expandedIds: Set<String> = emptySet(),
        /** いちばん最近、セットを完了した種目（全部が終わるまで、たたまずに残す） */
        val lastCompletedId: String? = null,
    )

    /** 直前の取り消せる操作。次の操作をしたら、置き換わる。 */
    private sealed interface Undo {
        data class SetDeleted(val set: SetRecord) : Undo
        data class ExerciseRemoved(val detail: WorkoutExerciseDetail) : Undo
        data class Reordered(val previousOrder: List<String>) : Undo
    }

    private data class RestState(
        val endAtMillis: Long,
        val totalSec: Int,
        val notified: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private val draft = MutableStateFlow<Draft?>(null)
    private val contexts = MutableStateFlow<Map<String, ExerciseContext>>(emptyMap())
    private var undoAction: Undo? = null
    private val planner = WorkoutPlanner(ids)
    private val addVariation = AddVariation(exercises, ids)

    /** フォームを選ぶシートを開いている種目（ワークアウトの種目の id）と、シートで選んでいるフォーム */
    private val formSheetFor = MutableStateFlow<String?>(null)
    private val formSheetSelected = MutableStateFlow<String?>(null)

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
                    expandedIds = local.expandedIds,
                    lastCompletedId = local.lastCompletedId,
                    now = clock.instant(),
                )
            } ?: WorkoutUiState(loaded = true, missing = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    /** フォーム（バリエーション）を選ぶシート（F-RT-10）。開いていないときは null。前回の記録を読むので、状態とは別に出す。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val formSheet: StateFlow<FormSheetUi?> = combine(formSheetFor, formSheetSelected, source, settings) { id, selected, source, settings ->
        FormSheetInputs(id, selected, (source as? Source.Value)?.detail, settings.weightUnit)
    }.flatMapLatest { inputs ->
        val target = inputs.detail?.exercises?.firstOrNull { it.workoutExercise.id == inputs.workoutExerciseId }
        if (target == null) flowOf<FormSheetUi?>(null) else flow<FormSheetUi?> { emit(buildFormSheet(target, inputs.selectedId, inputs.unit)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class FormSheetInputs(
        val workoutExerciseId: String?,
        val selectedId: String?,
        val detail: WorkoutDetail?,
        val unit: io.github.otato.traininglog.domain.model.WeightUnit,
    )

    /** 休憩タイマーの表示（1 秒ごとに変わるので、状態とは別に出す）。 */
    /**
     * 休憩が終わる時刻（エポックミリ秒）。休憩していなければ null。
     * アプリが後ろにあるときも通知で知らせるために、画面がこの時刻に目覚ましを仕掛ける（F-TM-03）。
     */
    val restEndAtMillis: StateFlow<Long?> = local.map { it.rest?.endAtMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            val values = currentDraft(target).toValues()
            workouts.updateSetValues(values)
            workouts.setCompleted(target.set.id, true, clock.instant())

            val exercise = target.exercise
            // 次のセットには、いま入力した値を引き継ぐ。そのまま「完了」を押せば記録できる（F-WO-03）
            exercise.sets.filter { !it.completed && it.id != target.set.id }.minByOrNull { it.sortOrder }?.let { next ->
                val carried = values.copy(setId = next.id)
                workouts.updateSetValues(carried)
                draft.value = Draft(carried.setId, carried.weightKg, carried.reps, carried.durationSec, carried.distanceM)
            }
            val remainingHere = exercise.sets.count { !it.completed && it.id != target.set.id }
            val remainingElsewhere = detail.exercises.any {
                it.workoutExercise.id != exercise.workoutExercise.id && it.currentSet != null
            }
            val settings = settings.value
            // 終えたあとの記録と、あとから入力（タイマーなし）では、休憩タイマーを動かさない
            val restSeconds = if (!detail.workout.isFinished && !detail.workout.quickEntry && settings.autoStartRest && (remainingHere > 0 || remainingElsewhere)) {
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
                    lastCompletedId = exercise.workoutExercise.id,
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
            undoAction = Undo.SetDeleted(set)
            workouts.deleteSet(setId)
            local.update { if (it.selectedSetId == setId) it.copy(selectedSetId = null) else it }
            _events.emit(WorkoutEvent.Undoable("セットを削除しました"))
        }
    }

    /** 直前の「セットの削除」「種目を外す」「種目の並べ替え」を、元に戻す（F-UI-03）。 */
    fun undo() {
        val action = undoAction ?: return
        undoAction = null
        viewModelScope.launch {
            when (action) {
                is Undo.SetDeleted -> workouts.addSet(action.set)
                is Undo.ExerciseRemoved -> workouts.addExercise(action.detail.toPlanned(), workoutId)
                is Undo.Reordered -> workouts.reorderExercises(workoutId, action.previousOrder)
            }
        }
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

    /** 種目を外す。入力したセットも消えるが、直後なら元に戻せる（F-UI-03）。 */
    fun removeExercise(workoutExerciseId: String) {
        viewModelScope.launch {
            val target = currentDetail()?.exercises?.firstOrNull { it.workoutExercise.id == workoutExerciseId } ?: return@launch
            undoAction = Undo.ExerciseRemoved(target)
            workouts.removeExercise(workoutExerciseId)
            local.update {
                if (it.focusedWorkoutExerciseId == workoutExerciseId) it.copy(focusedWorkoutExerciseId = null, selectedSetId = null) else it
            }
            _events.emit(WorkoutEvent.Undoable("${target.title}を外しました"))
        }
    }

    /** 種目の並びを変える（F-WO-13）。直後なら、元に戻せる。 */
    fun reorderExercises(orderedIds: List<String>) {
        viewModelScope.launch {
            val before = currentDetail()?.exercises?.map { it.workoutExercise.id } ?: return@launch
            if (before == orderedIds) return@launch
            undoAction = Undo.Reordered(before)
            workouts.reorderExercises(workoutId, orderedIds)
            _events.emit(WorkoutEvent.Undoable("種目を並べ替えました"))
        }
    }

    /** 完了してたたまれた種目を、開く・たたむ。 */
    fun toggleExpanded(workoutExerciseId: String) {
        local.update {
            val expanded = if (workoutExerciseId in it.expandedIds) it.expandedIds - workoutExerciseId else it.expandedIds + workoutExerciseId
            // いま完了したばかりの種目をたたむときは、その印も外す（外さないと、たたんだままにならない）
            it.copy(expandedIds = expanded, lastCompletedId = it.lastCompletedId.takeIf { id -> id != workoutExerciseId })
        }
    }

    // ---- 調整項目・ギアの追加 ----

    /** 調整項目（ベンチ角度など）の値を入れる（F-WO-12）。空にすると、値を消す。 */
    fun setAdjustment(workoutExerciseId: String, fieldId: String, value: String?) {
        viewModelScope.launch { workouts.setAdjustment(workoutExerciseId, fieldId, value) }
    }

    /** ギアを、その場で新しく作って、この種目に付ける（F-GR-01、F-GR-02）。 */
    fun addGear(workoutExerciseId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val exercise = currentDetail()?.exercises?.firstOrNull { it.workoutExercise.id == workoutExerciseId } ?: return@launch
            val gear = Gear(id = ids.next(), name = trimmed, sortOrder = gearRepository.nextSortOrder())
            gearRepository.save(gear)
            workouts.setGear(workoutExerciseId, exercise.gearIds + gear.id)
        }
    }

    // ---- フォーム（バリエーション。F-RT-10、F-EX-16） ----

    fun openFormSheet(workoutExerciseId: String) {
        formSheetSelected.value = null
        formSheetFor.value = workoutExerciseId
    }

    fun closeFormSheet() {
        formSheetFor.value = null
        formSheetSelected.value = null
    }

    /** シートで、フォームを選ぶ（まだ、記録には反映しない）。 */
    fun chooseForm(exerciseId: String) {
        formSheetSelected.value = exerciseId
    }

    /** 選んだフォームで始める。完了していないセットは、そのフォームの前回の値で用意し直す。 */
    fun confirmForm(workoutExerciseId: String, formId: String) {
        viewModelScope.launch {
            val target = currentDetail()?.exercises?.firstOrNull { it.workoutExercise.id == workoutExerciseId }
            val form = target?.forms?.firstOrNull { it.id == formId }
            if (target != null && form != null && form.id != target.exercise.id) {
                val previous = workouts.previousRecord(form.id, excludeWorkoutId = workoutId)
                workouts.changeExercise(workoutExerciseId, form, planner.refill(target.sets, previous))
                // まだギアを付けていなければ、そのフォームで前回使ったギアを付けておく（F-GR-03）
                if (target.gearIds.isEmpty() && previous != null && previous.gearIds.isNotEmpty()) {
                    workouts.setGear(workoutExerciseId, previous.gearIds)
                }
                // 用意し直した値が、入力途中の古い値で上書きされないようにする
                if (draft.value?.setId in target.sets.map { it.id }) draft.value = null
            }
            closeFormSheet()
        }
    }

    /** フォームを、その場で足す。親の種目の設定と調整項目を引き継ぐ（F-EX-15）。足したフォームを選んだ状態にする。 */
    fun addForm(workoutExerciseId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val target = currentDetail()?.exercises?.firstOrNull { it.workoutExercise.id == workoutExerciseId } ?: return@launch
            val id = addVariation(target.exercise.id, name)
            formSheetSelected.value = id
        }
    }

    private suspend fun buildFormSheet(target: WorkoutExerciseDetail, selectedId: String?, unit: io.github.otato.traininglog.domain.model.WeightUnit): FormSheetUi {
        val options = target.forms.map { form ->
            val record = workouts.previousRecord(form.id, excludeWorkoutId = workoutId)
            val line = record?.let { RecordFormat.setsLine(it.sets, form, it.weightMode, it.repMode, unit) }
            val text = line?.let { listOfNotNull(it.weightText.takeIf(String::isNotEmpty), it.tag, it.repsText).joinToString(" ") }
            val own = record != null && record.sourceExerciseName == null
            val lines = when {
                own && text != null -> listOf("前回 ${formatMonthDay(record!!.date)}　$text")
                record != null && text != null -> listOf("まだ記録がありません", "「${record.sourceExerciseName}」の直近: $text")
                else -> listOf("まだ記録がありません")
            }
            Triple(FormOptionUi(form.id, form.name, lines), record?.takeIf { own }?.date, form)
        }
        // 最初に選んでおくのは、いま選んでいるフォーム。まだなら、前回使ったフォーム（記録の日付が新しいもの）
        val initial = target.exercise.id.takeIf { target.exercise.parentId != null }
            ?: options.filter { it.second != null }.maxByOrNull { it.second!! }?.first?.exerciseId
            ?: options.firstOrNull()?.first?.exerciseId
        return FormSheetUi(
            workoutExerciseId = target.workoutExercise.id,
            title = target.title,
            options = options.map { it.first },
            selectedId = selectedId?.takeIf { id -> options.any { it.first.exerciseId == id } } ?: initial,
        )
    }

    // ---- あとから入力（F-WO-21） ----

    /** 「あとから入力」（タイマーなし）と「ふつうに記録」を行き来する。あとから入力にすると、動いている休憩は止める。 */
    fun setQuickEntry(quickEntry: Boolean) {
        viewModelScope.launch {
            workouts.setQuickEntry(workoutId, quickEntry)
            if (quickEntry) local.update { it.copy(rest = null) }
        }
    }

    // ---- ワークアウト全体 ----

    fun changeDate(date: LocalDate) {
        viewModelScope.launch {
            workouts.updateDate(workoutId, date)
            // あとから入力は、今日以外の日付のためのもの。今日にしたら、ふつうの記録に戻す
            if (date == DayBoundary.today(clock, settings.value.dayBoundaryHour) && currentDetail()?.workout?.quickEntry == true) {
                workouts.setQuickEntry(workoutId, false)
            }
        }
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
