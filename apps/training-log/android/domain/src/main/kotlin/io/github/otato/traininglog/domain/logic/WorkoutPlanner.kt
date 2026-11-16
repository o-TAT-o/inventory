package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PlannedWorkoutExercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WorkoutExercise

/**
 * ワークアウトに種目を入れるときの、セットの下書きを作る（F-WO-03、F-RT-04）。
 *
 * セットの数と値は、次の順で決める。
 * - 数: ルーティンの目標セット数 → 前回のセット数 → 3
 * - 重量・回数: ルーティンの目標 → 前回の同じ番号のセット → 前回の最後のセット → 初期値（重量 0、回数 10）
 */
class WorkoutPlanner(private val ids: IdGenerator = UuidGenerator) {

    fun plan(
        workoutId: String,
        sortOrder: Int,
        exercise: Exercise,
        routineItem: RoutineItem? = null,
        previous: PreviousRecord? = null,
    ): PlannedWorkoutExercise {
        val workoutExercise = WorkoutExercise(
            id = ids.next(),
            workoutId = workoutId,
            exerciseId = exercise.id,
            sortOrder = sortOrder,
            weightMode = exercise.weightMode,
            repMode = exercise.repMode,
            restSeconds = routineItem?.restSeconds,
            restAfterSeconds = routineItem?.restAfterSeconds,
        )
        val previousSets = previous?.sets.orEmpty()
        // 乗り換え用の初期設定の値は 1 セット分なので、セット数の目安にはしない（値だけを使う）
        val previousCount = previous?.takeUnless { it.isBaseline }?.sets?.size
        val count = routineItem?.targetSets
            ?: previousCount?.takeIf { it > 0 }?.coerceAtMost(MAX_SETS_FROM_PREVIOUS)
            ?: DEFAULT_SET_COUNT
        val sets = (0 until count).map { index ->
            val reference = previousSets.getOrNull(index) ?: previousSets.lastOrNull()
            newSet(
                workoutExerciseId = workoutExercise.id,
                sortOrder = index,
                exercise = exercise,
                weightKg = if (exercise.usesWeight) routineItem?.targetWeightKg ?: reference?.weightKg ?: 0.0 else 0.0,
                reps = routineItem?.targetReps ?: reference?.reps ?: DEFAULT_REPS,
                reference = reference,
            )
        }
        return PlannedWorkoutExercise(
            workoutExercise = workoutExercise,
            sets = sets,
            gearIds = previous?.gearIds.orEmpty(),
        )
    }

    /** 「＋セット」で足すセット。直前のセットの値で用意される（F-WO-16）。 */
    fun nextSet(workoutExerciseId: String, exercise: Exercise, existing: List<SetRecord>): SetRecord {
        val last = existing.maxByOrNull { it.sortOrder }
        return newSet(
            workoutExerciseId = workoutExerciseId,
            sortOrder = (last?.sortOrder ?: -1) + 1,
            exercise = exercise,
            weightKg = last?.weightKg ?: 0.0,
            reps = last?.reps ?: DEFAULT_REPS,
            reference = last,
        )
    }

    private fun newSet(
        workoutExerciseId: String,
        sortOrder: Int,
        exercise: Exercise,
        weightKg: Double,
        reps: Int,
        reference: SetRecord?,
    ): SetRecord = SetRecord(
        id = ids.next(),
        workoutExerciseId = workoutExerciseId,
        sortOrder = sortOrder,
        weightKg = weightKg,
        reps = if (exercise.usesReps) reps else 0,
        durationSec = if (exercise.recordType == RecordType.Duration || exercise.recordType == RecordType.DistanceTime) {
            reference?.durationSec ?: DEFAULT_DURATION_SEC
        } else {
            null
        },
        distanceM = if (exercise.recordType == RecordType.DistanceTime) reference?.distanceM ?: 0.0 else null,
    )

    companion object {
        const val DEFAULT_SET_COUNT = 3
        const val DEFAULT_REPS = 10
        const val DEFAULT_DURATION_SEC = 60
        private const val MAX_SETS_FROM_PREVIOUS = 10
    }
}
