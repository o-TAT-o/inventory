package io.github.otato.traininglog.domain.usecase

import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import java.time.Clock
import java.time.LocalDate

/**
 * ワークアウトを始める（F-RT-04、F-WO-01）。
 * ルーティンを選ぶと、種目と目標のセットが展開される。選ばなければ、種目のない自由記録になる。
 */
class StartWorkout(
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val clock: Clock,
    private val ids: IdGenerator = UuidGenerator,
    private val planner: WorkoutPlanner = WorkoutPlanner(ids),
) {
    /** @return 始めたワークアウトの id */
    suspend operator fun invoke(date: LocalDate, routineId: String?, quickEntry: Boolean = false): String {
        val workoutId = ids.next()
        val workout = Workout(id = workoutId, date = date, routineId = routineId, startedAt = clock.instant(), quickEntry = quickEntry)
        val planned = if (routineId == null) {
            emptyList()
        } else {
            val routine = checkNotNull(routines.getRoutine(routineId)) { "ルーティンが見つかりません: $routineId" }
            routine.items.sortedBy { it.item.sortOrder }.mapIndexed { index, detail ->
                val previous = workouts.previousRecord(detail.exercise.id, excludeWorkoutId = null)
                val fields = exercises.getAdjustmentFields(detail.exercise.id)
                planner.plan(workoutId, index, detail.exercise, detail.item, previous, fields)
            }
        }
        workouts.createWorkout(workout, planned)
        return workoutId
    }
}

/** 記録中のワークアウトに、種目を足す（F-RT-05）。セットは、前回の記録をもとに用意される。 */
class AddExerciseToWorkout(
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
    private val planner: WorkoutPlanner = WorkoutPlanner(),
) {
    suspend operator fun invoke(workoutId: String, exerciseId: String, sortOrder: Int) {
        val exercise = checkNotNull(exercises.getExercise(exerciseId)) { "種目が見つかりません: $exerciseId" }
        val previous = workouts.previousRecord(exerciseId, excludeWorkoutId = workoutId)
        val fields = exercises.getAdjustmentFields(exerciseId)
        workouts.addExercise(planner.plan(workoutId, sortOrder, exercise, previous = previous, adjustmentFields = fields), workoutId)
    }
}

/** 種目に「＋セット」で 1 セット足す。直前のセットの値で用意される（F-WO-16）。 */
class AddSet(
    private val workouts: WorkoutRepository,
    private val planner: WorkoutPlanner = WorkoutPlanner(),
) {
    suspend operator fun invoke(workoutExerciseId: String, exercise: Exercise, existing: List<SetRecord>) {
        workouts.addSet(planner.nextSet(workoutExerciseId, exercise, existing))
    }
}
