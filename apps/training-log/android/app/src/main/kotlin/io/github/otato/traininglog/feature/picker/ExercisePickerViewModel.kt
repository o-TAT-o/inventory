package io.github.otato.traininglog.feature.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 種目をどこに追加するか。 */
enum class PickerMode { Workout, Routine }

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
)

/**
 * 種目の選択（F-EX-06、F-EX-09）。名前で検索し、部位・器具で絞り込める。
 * 選ぶと、ワークアウトまたはルーティンに種目が追加され、前の画面に戻る。
 */
class ExercisePickerViewModel(
    private val mode: PickerMode,
    private val targetId: String,
    exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    private val addExerciseToWorkout: AddExerciseToWorkout,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {

    private data class Filter(val query: String = "", val bodyPart: BodyPart? = null, val equipment: Equipment? = null)

    private val filter = MutableStateFlow(Filter())

    val state: StateFlow<PickerUiState> = combine(exercises.observeExercises(), filter) { all, filter ->
        val names = all.associate { it.id to it.name }
        val visible = all.filter { exercise ->
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
        PickerUiState(filter.query, filter.bodyPart, filter.equipment, ordered, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState())

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 種目を追加し終えた（前の画面に戻る）。 */
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setBodyPart(bodyPart: BodyPart?) = filter.update { it.copy(bodyPart = bodyPart) }

    fun setEquipment(equipment: Equipment?) = filter.update { it.copy(equipment = equipment) }

    fun pick(exerciseId: String) {
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
            }
            _done.emit(Unit)
        }
    }
}
