package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WorkoutExerciseDetail

/**
 * ボリューム（重量 × 回数の合計）。実際に動かした量になるように、数え方で係数を変える（6.2 の表）。
 *
 * | 重量 | 回数 | ボリューム |
 * |------|------|------------|
 * | 合計 | 通常 | 重量 × 回数 |
 * | 片側 | 通常 | 重量 × 2 × 回数 |
 * | 片側 | 片側 | 重量 × 回数 × 2 |
 * | 片側 | 左右合計 | 重量 × 回数 |
 * | 合計 | 片側 | 重量 × 回数 × 2 |
 * | 合計 | 左右合計 | 重量 × 回数 |
 * | 加重（自重の種目） | 通常 | 加重 × 回数（体重は含めない。補助はマイナスなので 0 とする） |
 */
object Volume {
    fun ofSet(
        weightKg: Double,
        reps: Int,
        recordType: RecordType,
        weightMode: WeightMode,
        repMode: RepMode,
    ): Double = when (recordType) {
        RecordType.WeightReps -> weightKg * reps * sideFactor(weightMode, repMode)
        RecordType.Bodyweight -> maxOf(0.0, weightKg) * reps
        RecordType.Duration, RecordType.DistanceTime -> 0.0
    }

    /** ワークアウトの 1 種目の、完了したセットのボリューム。 */
    fun ofExercise(detail: WorkoutExerciseDetail): Double =
        detail.completedSets.sumOf { set -> ofSet(set, detail) }

    private fun ofSet(set: SetRecord, detail: WorkoutExerciseDetail): Double = ofSet(
        weightKg = set.weightKg,
        reps = set.reps,
        recordType = detail.exercise.recordType,
        weightMode = detail.workoutExercise.weightMode,
        repMode = detail.workoutExercise.repMode,
    )

    private fun sideFactor(weightMode: WeightMode, repMode: RepMode): Int = when (repMode) {
        RepMode.Normal -> if (weightMode == WeightMode.PerSide) 2 else 1
        RepMode.PerSide -> 2
        RepMode.BothSidesTotal -> 1
    }
}
