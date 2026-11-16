package io.github.otato.traininglog.domain.usecase

import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * 過去のワークアウトをコピーして、新しいワークアウトを始める（F-HI-04）。
 * 種目の並び・セットの数と値・ギア・調整項目を、元の記録（完了したセット）から用意する。記録のない種目は入れない。
 */
class CopyWorkout(
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
    private val clock: Clock,
    private val ids: IdGenerator = UuidGenerator,
    private val planner: WorkoutPlanner = WorkoutPlanner(ids),
) {
    /** @return 始めたワークアウトの id。元の記録が見つからないとき・コピーする種目がないときは null */
    suspend operator fun invoke(sourceWorkoutId: String, date: LocalDate): String? {
        val source = workouts.observeWorkout(sourceWorkoutId).first() ?: return null
        val done = source.exercises.filter { it.completedSets.isNotEmpty() }
        if (done.isEmpty()) return null
        val workoutId = ids.next()
        val planned = done.mapIndexed { index, detail ->
            val reference = PreviousRecord(
                date = source.workout.date,
                sets = detail.completedSets.sortedBy { it.sortOrder },
                weightMode = detail.workoutExercise.weightMode,
                repMode = detail.workoutExercise.repMode,
                gearIds = detail.gearIds,
                exerciseId = detail.exercise.id,
                adjustments = detail.adjustments,
            )
            val plan = planner.plan(workoutId, index, detail.exercise, previous = reference, adjustmentFields = exercises.getAdjustmentFields(detail.exercise.id))
            // 数え方と休憩は、元の記録のものにする（その日だけ変えていたら、それも写す）
            plan.copy(
                workoutExercise = plan.workoutExercise.copy(
                    weightMode = detail.workoutExercise.weightMode,
                    repMode = detail.workoutExercise.repMode,
                    restSeconds = detail.workoutExercise.restSeconds,
                    restAfterSeconds = detail.workoutExercise.restAfterSeconds,
                ),
            )
        }
        workouts.createWorkout(
            Workout(id = workoutId, date = date, routineId = source.workout.routineId, startedAt = clock.instant()),
            planned,
        )
        return workoutId
    }
}

/**
 * 終えたワークアウトを、ルーティンとして保存する（完了画面の「ルーティンとして保存」）。
 * 種目の並びと、やったセット数を目標にする。重量・回数の目標は入れない（次からは、前回の記録が初期値になる）。
 */
class SaveWorkoutAsRoutine(
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
    private val ids: IdGenerator = UuidGenerator,
) {
    /** @return 作ったルーティンの id。保存する種目がないときは null */
    suspend operator fun invoke(workoutId: String, name: String): String? {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "ルーティンの名前が空です" }
        val detail = workouts.observeWorkout(workoutId).first() ?: return null
        val done = detail.exercises.filter { it.completedSets.isNotEmpty() }
        if (done.isEmpty()) return null
        val routineId = ids.next()
        routines.saveRoutine(Routine(id = routineId, name = trimmed, sortOrder = routines.nextRoutineSortOrder()))
        done.forEachIndexed { index, exercise ->
            routines.saveItem(
                RoutineItem(
                    id = ids.next(),
                    routineId = routineId,
                    exerciseId = exercise.exercise.id,
                    sortOrder = index,
                    targetSets = exercise.completedSets.size,
                    restSeconds = exercise.workoutExercise.restSeconds,
                    restAfterSeconds = exercise.workoutExercise.restAfterSeconds,
                ),
            )
        }
        return routineId
    }
}
