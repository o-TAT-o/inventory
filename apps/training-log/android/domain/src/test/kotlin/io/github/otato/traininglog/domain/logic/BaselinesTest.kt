package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate

/** 乗り換え用の初期設定（F-DA-05、F-DA-06）の計算。 */
class BaselinesTest {
    private val date = LocalDate.of(2026, 11, 16)
    private val bench = Exercise(id = "bench", name = "ベンチプレス")
    private val pullUp = Exercise(id = "pullup", name = "懸垂", recordType = RecordType.Bodyweight)
    private val weightedDips = Exercise(id = "dips", name = "ディップス", recordType = RecordType.Bodyweight, useAddedWeight = true)
    private val plank = Exercise(id = "plank", name = "プランク", recordType = RecordType.Duration)
    private val running = Exercise(id = "run", name = "ランニング", recordType = RecordType.DistanceTime)

    @Test
    fun `重量と回数が入っていれば、前回の記録として使える`() {
        val previous = assertNotNull(Baselines.toPreviousRecord(Baseline("bench", weightKg = 60.0, reps = 10), bench, date))
        assertTrue(previous.isBaseline)
        assertEquals(date, previous.date)
        val set = previous.sets.single()
        assertEquals(60.0, set.weightKg)
        assertEquals(10, set.reps)
        assertTrue(set.completed)
    }

    @Test
    fun `直近の値が何もなければ、前回の記録にはならない（自己ベストだけの行）`() {
        assertNull(Baselines.toPreviousRecord(Baseline("bench", best = 80.0), bench, date))
        assertNull(Baselines.toPreviousRecord(Baseline("bench"), bench, date))
    }

    @Test
    fun `自重の種目は回数だけで、加重のある自重は加重も使う`() {
        val plain = assertNotNull(Baselines.toPreviousRecord(Baseline("pullup", reps = 8), pullUp, date))
        assertEquals(0.0, plain.sets.single().weightKg)
        assertEquals(8, plain.sets.single().reps)
        // 加重のない自重の種目に重量が入っていても、使わない
        assertEquals(0.0, assertNotNull(Baselines.toPreviousRecord(Baseline("pullup", weightKg = 10.0, reps = 8), pullUp, date)).sets.single().weightKg)

        val weighted = assertNotNull(Baselines.toPreviousRecord(Baseline("dips", weightKg = 10.0, reps = 8), weightedDips, date))
        assertEquals(10.0, weighted.sets.single().weightKg)
    }

    @Test
    fun `時間の種目は、秒数を使う`() {
        val previous = assertNotNull(Baselines.toPreviousRecord(Baseline("plank", durationSec = 60), plank, date))
        assertEquals(60, previous.sets.single().durationSec)
    }

    @Test
    fun `距離と時間の種目は、初期設定の対象にしない`() {
        assertFalse(Baselines.isSupported(running))
        assertTrue(Baselines.isSupported(bench))
        assertNull(Baselines.toPreviousRecord(Baseline("run", durationSec = 1800), running, date))
    }

    @Test
    fun `自己ベストは、入れた自己ベストと直近の値の大きいほう`() {
        assertEquals(80.0, Baselines.effectiveBest(Baseline("bench", weightKg = 60.0, reps = 10, best = 80.0), bench))
        // 自己ベストを空欄にしたら、直近の値が下限になる
        assertEquals(60.0, Baselines.effectiveBest(Baseline("bench", weightKg = 60.0, reps = 10), bench))
        // 直近の値より低い自己ベストは、直近の値に直す
        assertEquals(60.0, Baselines.effectiveBest(Baseline("bench", weightKg = 60.0, best = 50.0), bench))
        assertNull(Baselines.effectiveBest(Baseline("bench"), bench))
    }

    @Test
    fun `自己ベストの尺度は、種目の種類で変わる`() {
        // 加重のない自重は最大回数、時間は最大の秒数
        assertEquals(12.0, Baselines.effectiveBest(Baseline("pullup", reps = 8, best = 12.0), pullUp))
        assertEquals(8.0, Baselines.effectiveBest(Baseline("pullup", reps = 8), pullUp))
        assertEquals(90.0, Baselines.effectiveBest(Baseline("plank", durationSec = 60, best = 90.0), plank))
    }

    @Test
    fun `セット数を入れていないときは、値だけを使い、ワークアウトのセット数は 3 セットのまま`() {
        val previous = assertNotNull(Baselines.toPreviousRecord(Baseline("bench", weightKg = 60.0, reps = 10), bench, date))
        assertFalse(previous.setCountKnown)
        assertEquals(1, previous.sets.size)
        var n = 0
        val planned = WorkoutPlanner(IdGenerator { "p-${n++}" }).plan("w", 0, bench, previous = previous)
        assertEquals(3, planned.sets.size)
        assertEquals(listOf(60.0, 60.0, 60.0), planned.sets.map { it.weightKg })
        assertEquals(listOf(10, 10, 10), planned.sets.map { it.reps })
    }

    @Test
    fun `セット数を入れていれば、前回の記録はそのセット数になり、ワークアウトのセット数にも使う`() {
        val previous = assertNotNull(Baselines.toPreviousRecord(Baseline("bench", weightKg = 60.0, reps = 10, sets = 5), bench, date))
        assertTrue(previous.setCountKnown)
        assertEquals(5, previous.sets.size)
        assertEquals(setOf(60.0), previous.sets.map { it.weightKg }.toSet())
        assertEquals(5, previous.sets.map { it.id }.toSet().size, "セットの id は、重ならない")

        var n = 0
        val planned = WorkoutPlanner(IdGenerator { "p-${n++}" }).plan("w", 0, bench, previous = previous)
        assertEquals(5, planned.sets.size)
        assertEquals(listOf(10, 10, 10, 10, 10), planned.sets.map { it.reps })
    }

    @Test
    fun `セット数が 1 でも、入れたものとして 1 セットにする（空欄とは別）`() {
        val previous = assertNotNull(Baselines.toPreviousRecord(Baseline("bench", weightKg = 60.0, reps = 10, sets = 1), bench, date))
        assertTrue(previous.setCountKnown)
        var n = 0
        assertEquals(1, WorkoutPlanner(IdGenerator { "p-${n++}" }).plan("w", 0, bench, previous = previous).sets.size)
    }

    @Test
    fun `セット数が大きすぎるときは、上限までにする`() {
        val previous = assertNotNull(Baselines.toPreviousRecord(Baseline("bench", weightKg = 60.0, reps = 10, sets = 99), bench, date))
        assertEquals(Baselines.MAX_SETS, previous.sets.size)
    }
}
