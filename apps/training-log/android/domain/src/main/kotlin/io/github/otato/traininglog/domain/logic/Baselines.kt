package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.SetRecord
import java.time.LocalDate

/** 乗り換え用の初期設定（[Baseline]）を、前回の記録と自己ベストとして使うための計算（F-DA-06）。 */
object Baselines {
    /** 初期設定で入力できる種目か。距離 × 時間の種目は、対象にしない。 */
    fun isSupported(exercise: Exercise): Boolean = exercise.recordType != RecordType.DistanceTime

    /** 直近の値（前回の記録の代わり）。直近の値が 1 つもなければ null。 */
    fun toPreviousRecord(baseline: Baseline, exercise: Exercise, date: LocalDate): PreviousRecord? {
        if (!hasLatest(baseline, exercise)) return null
        val set = SetRecord(
            id = "baseline:${baseline.exerciseId}",
            workoutExerciseId = "baseline",
            sortOrder = 0,
            weightKg = if (exercise.usesWeight) baseline.weightKg ?: 0.0 else 0.0,
            reps = baseline.reps ?: 0,
            durationSec = if (exercise.recordType == RecordType.Duration) baseline.durationSec else null,
            completed = true,
        )
        return PreviousRecord(
            date = date,
            sets = listOf(set),
            weightMode = exercise.weightMode,
            repMode = exercise.repMode,
            isBaseline = true,
        )
    }

    /**
     * 自己ベストの下限。入れた自己ベストと、直近の値のうち、大きいほう。
     * 自己ベストを空欄にしても、直近の値より低い自己ベストにはならない（はじめのワークアウトで、むやみに「自己ベスト」の印が付かないように）。
     */
    fun effectiveBest(baseline: Baseline, exercise: Exercise): Double? =
        listOfNotNull(baseline.best, latestScore(baseline, exercise)).maxOrNull()

    private fun hasLatest(baseline: Baseline, exercise: Exercise): Boolean = when (exercise.recordType) {
        RecordType.WeightReps -> baseline.weightKg != null || baseline.reps != null
        RecordType.Bodyweight -> baseline.reps != null || (exercise.useAddedWeight && baseline.weightKg != null)
        RecordType.Duration -> baseline.durationSec != null
        RecordType.DistanceTime -> false
    }

    /** 直近の値を、自己ベストと同じ尺度（PersonalBests.score）で表したもの。 */
    private fun latestScore(baseline: Baseline, exercise: Exercise): Double? = when (exercise.recordType) {
        RecordType.WeightReps -> baseline.weightKg
        RecordType.Bodyweight -> if (exercise.useAddedWeight) baseline.weightKg else baseline.reps?.toDouble()
        RecordType.Duration -> baseline.durationSec?.toDouble()
        RecordType.DistanceTime -> null
    }
}
