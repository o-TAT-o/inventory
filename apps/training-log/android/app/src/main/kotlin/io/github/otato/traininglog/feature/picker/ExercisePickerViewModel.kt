package io.github.otato.traininglog.feature.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.logic.Baselines
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.repository.BaselineRepository
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 種目をどこに追加するか。 */
enum class PickerMode {
    /** ワークアウトに足す（1 つ選ぶと、足して戻る） */
    Workout,

    /** ルーティンに足す（1 つ選ぶと、足して戻る） */
    Routine,

    /** 乗り換え用の初期設定の一覧に足す（まとめて選んで、「追加」で戻る。F-DA-05） */
    Migration,
}

data class PickerRow(
    val exercise: Exercise,
    /** バリエーションのとき、親の種目名 */
    val parentName: String?,
)

data class PickerUiState(
    val query: String = "",
    val bodyPart: BodyPart? = null,
    val equipment: Equipment? = null,
    val rows: List<PickerRow> = emptyList(),
    val loaded: Boolean = false,
    /** まとめて選ぶモード（乗り換え用の初期設定） */
    val multiSelect: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /** すでに一覧にある種目（選べない） */
    val addedIds: Set<String> = emptySet(),
)

/**
 * 種目の選択（F-EX-06、F-EX-09）。名前で検索し、部位・器具で絞り込める。
 * ワークアウト・ルーティンには、1 つ選ぶと足して戻る。乗り換え用の初期設定では、まとめて選んで足す。
 */
class ExercisePickerViewModel(
    private val mode: PickerMode,
    private val targetId: String,
    exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    private val baselines: BaselineRepository,
    private val addExerciseToWorkout: AddExerciseToWorkout,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {

    private data class Filter(val query: String = "", val bodyPart: BodyPart? = null, val equipment: Equipment? = null)

    private val filter = MutableStateFlow(Filter())
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** すでに初期設定の一覧にある種目。選べなくするためのもので、乗り換え用の初期設定のときだけ。ほかでは常に空 */
    private val addedIds: Flow<Set<String>> = if (mode == PickerMode.Migration) {
        baselines.observeBaselines().map { rows -> rows.map { it.exercise.id }.toSet() }
    } else {
        flowOf(emptySet())
    }

    val state: StateFlow<PickerUiState> = combine(exercises.observeExercises(), filter, selected, addedIds) { all, filter, selected, added ->
        val names = all.associate { it.id to it.name }
        val visible = all.filter { exercise ->
            (mode != PickerMode.Migration || Baselines.isSupported(exercise)) &&
                (filter.bodyPart == null || exercise.bodyPart == filter.bodyPart) &&
                (filter.equipment == null || exercise.equipment == filter.equipment) &&
                (filter.query.isBlank() || exercise.name.contains(filter.query.trim(), ignoreCase = true))
        }
        // 親の種目の直後に、そのバリエーションを並べる（F-EX-17）
        val parents = visible.filter { it.parentId == null || it.parentId !in names }
        val childrenByParent = visible.filter { it.parentId != null && it.parentId in names }.groupBy { it.parentId }
        val ordered = buildList {
            parents.forEach { parent ->
                add(PickerRow(parent, null))
                childrenByParent[parent.id].orEmpty().forEach { add(PickerRow(it, names[parent.id])) }
            }
            // 親が絞り込みで外れたバリエーションも出す
            childrenByParent.filterKeys { key -> parents.none { it.id == key } }.values.flatten().forEach {
                add(PickerRow(it, names[it.parentId]))
            }
        }
        PickerUiState(
            query = filter.query,
            bodyPart = filter.bodyPart,
            equipment = filter.equipment,
            rows = ordered,
            loaded = true,
            multiSelect = mode == PickerMode.Migration,
            selectedIds = selected,
            addedIds = added,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState(multiSelect = mode == PickerMode.Migration))

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 種目を追加し終えた（前の画面に戻る）。 */
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setBodyPart(bodyPart: BodyPart?) = filter.update { it.copy(bodyPart = bodyPart) }

    fun setEquipment(equipment: Equipment?) = filter.update { it.copy(equipment = equipment) }

    /** 種目をタップしたとき。まとめて選ぶモードでは、選択の入れ替え。それ以外は、足して戻る。 */
    fun pick(exerciseId: String) {
        if (mode == PickerMode.Migration) {
            selected.update { if (exerciseId in it) it - exerciseId else it + exerciseId }
            return
        }
        viewModelScope.launch {
            when (mode) {
                PickerMode.Workout -> {
                    val next = workouts.observeWorkout(targetId).first()
                        ?.exercises?.maxOfOrNull { it.workoutExercise.sortOrder }?.plus(1) ?: 0
                    addExerciseToWorkout(targetId, exerciseId, next)
                }
                PickerMode.Routine -> routines.saveItem(
                    RoutineItem(
                        id = ids.next(),
                        routineId = targetId,
                        exerciseId = exerciseId,
                        sortOrder = routines.nextItemSortOrder(targetId),
                    ),
                )
                PickerMode.Migration -> Unit
            }
            _done.emit(Unit)
        }
    }

    /** まとめて選んだ種目を、初期設定の一覧に足す（乗り換え用の初期設定）。 */
    fun confirmSelection() {
        val chosen = selected.value
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            // 画面に出ている順（種目の並び順）で足す
            val ordered = state.value.rows.map { it.exercise.id }.filter { it in chosen }
            baselines.addExercises(ordered + (chosen - ordered.toSet()))
            _done.emit(Unit)
        }
    }
}
