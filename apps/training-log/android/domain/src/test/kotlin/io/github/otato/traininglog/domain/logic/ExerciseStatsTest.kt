package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutSummary
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseStatsTest {
    private val bench = Exercise(id = "bench", name = "ベンチプレス")
    private val day = LocalDate.of(2026, 11, 1)

    private fun record(date: LocalDate, vararg sets: Pair<Double, Int>, weightMode: WeightMode = WeightMode.Total, baseline: Boolean = false) =
        PreviousRecord(
            date = date,
            sets = sets.mapIndexed { i, (w, r) -> SetRecord("s-$date-$i", "we", sortOrder = i, weightKg = w, reps = r, completed = true) },
            weightMode = weightMode,
            repMode = RepMode.Normal,
            isBaseline = baseline,
        )

    @Test
    fun `推定 1RM は、1 回なら重量そのまま、10 回なら 3 分の 4 倍`() {
        assertEquals(100.0, ExerciseStats.oneRepMax(100.0, 1))
        assertEquals(80.0, ExerciseStats.oneRepMax(60.0, 10))
    }

    @Test
    fun `推定 1RM は、回数が多すぎるセットと、重量のないセットでは出さない`() {
        assertNull(ExerciseStats.oneRepMax(60.0, 13))
        assertNull(ExerciseStats.oneRepMax(0.0, 10))
        assertNull(ExerciseStats.oneRepMax(60.0, 0))
    }

    @Test
    fun `選べるグラフは、重量と回数の種目なら 3 つ、加重のない自重なら 1 つ`() {
        assertEquals(3, ExerciseStats.metricsFor(bench).size)
        assertEquals(listOf(ChartMetric.Best), ExerciseStats.metricsFor(bench.copy(recordType = RecordType.Bodyweight)))
        assertEquals(
            listOf(ChartMetric.Best, ChartMetric.Volume),
            ExerciseStats.metricsFor(bench.copy(recordType = RecordType.Bodyweight, useAddedWeight = true)),
        )
    }

    @Test
    fun `グラフは古い順で、それまでの最高を更新した点に印が付く。最初の記録には付かない`() {
        val history = listOf(
            record(day.plusDays(14), 62.5 to 8),
            record(day, 60.0 to 10),
            record(day.plusDays(7), 60.0 to 10),
        )
        val points = ExerciseStats.chart(history, bench, ChartMetric.Best, from = null)
        assertEquals(listOf(60.0, 60.0, 62.5), points.map { it.value })
        assertEquals(listOf(false, false, true), points.map { it.isPersonalBest })
    }

    @Test
    fun `期間で絞っても、自己ベストの印は、期間の外の記録も含めて決める`() {
        val history = listOf(
            record(day.minusMonths(6), 70.0 to 5),
            record(day, 60.0 to 10),
            record(day.plusDays(7), 65.0 to 8),
        )
        val points = ExerciseStats.chart(history, bench, ChartMetric.Best, from = day.minusMonths(1))
        assertEquals(listOf(60.0, 65.0), points.map { it.value })
        assertEquals(listOf(false, false), points.map { it.isPersonalBest })
    }

    @Test
    fun `ボリュームは、片側の重量なら 2 倍で数える`() {
        val total = record(day, 20.0 to 10, 20.0 to 10)
        val perSide = record(day, 20.0 to 10, 20.0 to 10, weightMode = WeightMode.PerSide)
        assertEquals(400.0, ExerciseStats.valueOf(total, bench, ChartMetric.Volume))
        assertEquals(800.0, ExerciseStats.valueOf(perSide, bench, ChartMetric.Volume))
    }

    @Test
    fun `乗り換え用の初期設定の値は、グラフにも自己ベストにも入れない`() {
        val history = listOf(record(day, 100.0 to 1, baseline = true), record(day.plusDays(1), 60.0 to 10))
        assertEquals(listOf(60.0), ExerciseStats.chart(history, bench, ChartMetric.Best, null).map { it.value })
        assertEquals(60.0, ExerciseStats.bestSet(history, bench)?.second?.weightKg)
    }

    @Test
    fun `自己ベストの記録は、同じ値なら早いほうを返す`() {
        val history = listOf(record(day.plusDays(7), 60.0 to 8), record(day, 60.0 to 10))
        val (best, set) = ExerciseStats.bestSet(history, bench)!!
        assertEquals(day, best.date)
        assertEquals(10, set.reps)
    }

    @Test
    fun `月のまとめは、終えたワークアウトだけを数え、部位は多い順に並ぶ`() {
        fun summary(id: String, finished: Boolean, vararg parts: BodyPart) = WorkoutSummary(
            workout = Workout(id, day, startedAt = Instant.EPOCH, finishedAt = if (finished) Instant.EPOCH else null),
            routineName = null,
            completedSetCount = 3,
            volumeKg = 0.0,
            exerciseNames = emptyList(),
            bodyParts = parts.toList(),
        )
        val stats = PeriodStats.of(
            listOf(
                summary("a", true, BodyPart.Chest, BodyPart.Triceps),
                summary("b", true, BodyPart.Back),
                summary("c", true, BodyPart.Chest),
                summary("d", false, BodyPart.Legs),
            ),
        )
        assertEquals(3, stats.workoutCount)
        assertEquals(listOf(BodyPart.Chest to 2, BodyPart.Back to 1, BodyPart.Triceps to 1), stats.byBodyPart)
    }
}
