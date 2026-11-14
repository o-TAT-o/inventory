package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.RepMode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutPlannerTest {
    private var counter = 0
    private val planner = WorkoutPlanner(IdGenerator { "id-${counter++}" })
    private val bench = Exercise(id = "bench", name = "ベンチプレス", weightMode = WeightMode.Total)

    private fun previous(vararg sets: Pair<Double, Int>) = PreviousRecord(
        date = LocalDate.of(2026, 11, 7),
        sets = sets.mapIndexed { i, (w, r) ->
            SetRecord(id = "p$i", workoutExerciseId = "pw", sortOrder = i, weightKg = w, reps = r, completed = true)
        },
        weightMode = WeightMode.Total,
        repMode = RepMode.Normal,
    )

    @Test
    fun `前回も目標もなければ 重量 0 回数 10 の 3 セット`() {
        val planned = planner.plan("w", 0, bench)
        assertEquals(3, planned.sets.size)
        assertEquals(listOf(0.0, 0.0, 0.0), planned.sets.map { it.weightKg })
        assertEquals(listOf(10, 10, 10), planned.sets.map { it.reps })
        assertEquals(false, planned.sets.any { it.completed })
    }

    @Test
    fun `前回の記録があれば、セット数と値を引き継ぐ`() {
        val planned = planner.plan("w", 0, bench, previous = previous(60.0 to 10, 60.0 to 9, 62.5 to 8))
        assertEquals(listOf(60.0, 60.0, 62.5), planned.sets.map { it.weightKg })
        assertEquals(listOf(10, 9, 8), planned.sets.map { it.reps })
    }

    @Test
    fun `ルーティンの目標が前回より優先される`() {
        val item = RoutineItem(id = "i", routineId = "r", exerciseId = "bench", targetSets = 4, targetWeightKg = 70.0, targetReps = 5)
        val planned = planner.plan("w", 0, bench, routineItem = item, previous = previous(60.0 to 10))
        assertEquals(4, planned.sets.size)
        assertEquals(listOf(70.0, 70.0, 70.0, 70.0), planned.sets.map { it.weightKg })
        assertEquals(listOf(5, 5, 5, 5), planned.sets.map { it.reps })
    }

    @Test
    fun `目標の一部だけが空欄なら、空欄のところは前回の値で埋める`() {
        val item = RoutineItem(id = "i", routineId = "r", exerciseId = "bench", targetSets = 3)
        val planned = planner.plan("w", 0, bench, routineItem = item, previous = previous(60.0 to 10, 60.0 to 9))
        assertEquals(listOf(60.0, 60.0, 60.0), planned.sets.map { it.weightKg })
        assertEquals(listOf(10, 9, 9), planned.sets.map { it.reps })
    }

    @Test
    fun `ルーティンの休憩の設定は、ワークアウトの種目に引き継がれる`() {
        val item = RoutineItem(id = "i", routineId = "r", exerciseId = "bench", restSeconds = 120, restAfterSeconds = 180)
        val planned = planner.plan("w", 2, bench, routineItem = item)
        assertEquals(120, planned.workoutExercise.restSeconds)
        assertEquals(180, planned.workoutExercise.restAfterSeconds)
        assertEquals(2, planned.workoutExercise.sortOrder)
    }

    @Test
    fun `重量を使わない種目は、重量 0 のまま`() {
        val pullUp = Exercise(id = "pullup", name = "懸垂", recordType = RecordType.Bodyweight, useAddedWeight = false)
        val planned = planner.plan("w", 0, pullUp, previous = previous(0.0 to 8, 0.0 to 7))
        assertEquals(listOf(0.0, 0.0), planned.sets.map { it.weightKg })
        assertEquals(listOf(8, 7), planned.sets.map { it.reps })
    }

    @Test
    fun `前回使ったギアが初期値になる`() {
        val prev = previous(60.0 to 10).copy(gearIds = setOf("belt"))
        assertEquals(setOf("belt"), planner.plan("w", 0, bench, previous = prev).gearIds)
    }

    @Test
    fun `＋セットは直前のセットの値で用意され、番号が続く`() {
        val existing = listOf(
            SetRecord(id = "a", workoutExerciseId = "we", sortOrder = 0, weightKg = 60.0, reps = 10, completed = true),
            SetRecord(id = "b", workoutExerciseId = "we", sortOrder = 1, weightKg = 62.5, reps = 8, completed = true),
        )
        val next = planner.nextSet("we", bench, existing)
        assertEquals(2, next.sortOrder)
        assertEquals(62.5, next.weightKg)
        assertEquals(8, next.reps)
        assertEquals(false, next.completed)
    }
}
