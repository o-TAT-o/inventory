package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WorkoutSummary
import java.time.LocalDate

/** グラフに出す値の種類（F-ST-01、F-ST-02）。 */
enum class ChartMetric {
    /** 最大重量（加重のある自重は最大加重、加重のない自重は最大回数、時間は最長、距離は最長） */
    Best,

    /** 推定 1RM（重量 × 回数の種目だけ） */
    OneRepMax,

    /** ボリューム（重量 × 回数の合計） */
    Volume,
}

/** グラフの期間（F-ST-04）。 */
enum class ChartPeriod(val months: Long?) {
    OneMonth(1),
    ThreeMonths(3),
    OneYear(12),
    All(null),
}

/** グラフの 1 点。[isPersonalBest] は、それまでの最高を更新した点（F-ST-03）。 */
data class ChartPoint(val date: LocalDate, val value: Double, val isPersonalBest: Boolean = false)

/** 種目の統計（グラフの値・推定 1RM）。 */
object ExerciseStats {
    /**
     * 推定 1RM（エプリーの式: 重量 × (1 + 回数 / 30)）。1 回なら重量そのまま。
     * 回数が多いほど当てにならないので、[MAX_REPS] 回を超えるセットは使わない。
     */
    fun oneRepMax(weightKg: Double, reps: Int): Double? = when {
        weightKg <= 0 || reps <= 0 || reps > MAX_REPS -> null
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    /** 種目で選べるグラフの種類。重量 × 回数の種目だけ、推定 1RM とボリュームを選べる。 */
    fun metricsFor(exercise: Exercise): List<ChartMetric> = when {
        exercise.recordType == RecordType.WeightReps -> listOf(ChartMetric.Best, ChartMetric.OneRepMax, ChartMetric.Volume)
        exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight -> listOf(ChartMetric.Best, ChartMetric.Volume)
        else -> listOf(ChartMetric.Best)
    }

    /** 1 回の記録の値。出せなければ null。 */
    fun valueOf(record: PreviousRecord, exercise: Exercise, metric: ChartMetric): Double? {
        val done = record.sets.filter { it.completed }
        return when (metric) {
            ChartMetric.Best -> PersonalBests.bestOf(done, exercise.recordType, exercise.useAddedWeight)
            ChartMetric.OneRepMax -> done.mapNotNull { oneRepMax(it.weightKg, it.reps) }.maxOrNull()
            ChartMetric.Volume -> done.sumOf { Volume.ofSet(it.weightKg, it.reps, exercise.recordType, record.weightMode, record.repMode) }
                .takeIf { done.isNotEmpty() }
        }
    }

    /**
     * グラフの点（古い順）。同じ日に 2 回やっていたら、大きいほうを出す。
     * 自己ベストの印は、期間の外の記録も含めた「それまでの最高」を更新した点に付ける。最初の記録には付けない。
     *
     * @param history その種目の記録（順番は問わない。乗り換え用の初期設定の値は含めない）
     * @param from この日以降の点だけを返す。null なら全部
     */
    fun chart(history: List<PreviousRecord>, exercise: Exercise, metric: ChartMetric, from: LocalDate?): List<ChartPoint> {
        val byDate = history
            .filter { !it.isBaseline }
            .mapNotNull { record -> valueOf(record, exercise, metric)?.let { record.date to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.max() }
            .toSortedMap()
        var best: Double? = null
        val points = byDate.map { (date, value) ->
            val isBest = best != null && value > best!!
            if (best == null || value > best!!) best = value
            ChartPoint(date, value, isBest)
        }
        return if (from == null) points else points.filter { !it.date.isBefore(from) }
    }

    /** 自己ベストの記録（最大重量などが、いちばん大きいセットと、その日）。同じ値なら、早いほう。 */
    fun bestSet(history: List<PreviousRecord>, exercise: Exercise): Pair<PreviousRecord, SetRecord>? =
        history.filter { !it.isBaseline }
            .sortedBy { it.date }
            .flatMap { record -> record.sets.filter { it.completed }.sortedBy { it.sortOrder }.map { record to it } }
            .mapNotNull { pair -> PersonalBests.score(pair.second, exercise.recordType, exercise.useAddedWeight)?.let { pair to it } }
            .maxByOrNull { it.second }
            ?.first

    private const val MAX_REPS = 12
}

/** 月ごとのまとめ（F-ST-05）。トレーニングした回数と、部位ごとの回数（多い順）。 */
data class MonthStats(val workoutCount: Int, val byBodyPart: List<Pair<BodyPart, Int>>)

object PeriodStats {
    /** 終えたワークアウトだけを数える。部位は、そのワークアウトで鍛えた部位を 1 回ずつ数える。 */
    fun of(summaries: List<WorkoutSummary>): MonthStats {
        val finished = summaries.filter { it.workout.isFinished }
        val parts = finished.flatMap { it.bodyParts.distinct() }
            .groupingBy { it }.eachCount()
            .toList()
            // 同じ回数なら、部位の並び順（利用者が足した部位は、そのあと）
            .sortedWith(compareByDescending<Pair<BodyPart, Int>> { it.second }.thenBy { order(it.first) })
        return MonthStats(finished.size, parts)
    }

    private fun order(part: BodyPart): Int = BodyPart.entries.indexOf(part).let { if (it < 0) Int.MAX_VALUE else it }
}
