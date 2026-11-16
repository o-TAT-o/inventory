package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.usecase.StartWorkout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 乗り換え用の初期設定（F-DA-05、F-DA-06）。 */
class BaselineRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val data: DataModule = TestDatabase.create(clock = clock)
    private val today = LocalDate.of(2026, 11, 16)

    @BeforeTest
    fun setUp() = runTest {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        data.exercises.save(Exercise(id = "squat", name = "スクワット"))
        data.exercises.save(Exercise(id = "row", name = "ロウ"))
    }

    private suspend fun baselines() = data.baselines.observeBaselines().first()

    @Test
    fun `種目を足すと、値が空の行として、足した順に並ぶ`() = runTest {
        data.baselines.addExercises(listOf("squat", "bench"))

        val rows = baselines()
        assertEquals(listOf("squat", "bench"), rows.map { it.exercise.id })
        assertTrue(rows.none { it.hasValues })
    }

    @Test
    fun `すでにある種目を足しても、増えない`() = runTest {
        data.baselines.addExercises(listOf("bench"))
        data.baselines.addExercises(listOf("bench", "squat"))
        assertEquals(listOf("bench", "squat"), baselines().map { it.exercise.id })
    }

    @Test
    fun `値を保存すると、同じ行が更新され、並び順は変わらない`() = runTest {
        data.baselines.addExercises(listOf("bench", "squat"))
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10, best = 70.0))
        data.baselines.save(Baseline("bench", weightKg = 62.5, reps = 8, best = 70.0))

        val rows = baselines()
        assertEquals(listOf("bench", "squat"), rows.map { it.exercise.id })
        val bench = rows.first().baseline
        assertEquals(62.5, bench.weightKg)
        assertEquals(8, bench.reps)
        assertEquals(70.0, bench.best)
        assertTrue(rows.first().hasValues)
    }

    @Test
    fun `一覧にない種目の値を保存すると、一覧に足されて保存される`() = runTest {
        data.baselines.save(Baseline("row", weightKg = 40.0, reps = 10))
        assertEquals(listOf("row"), baselines().map { it.exercise.id })
    }

    @Test
    fun `一覧から外せる`() = runTest {
        data.baselines.addExercises(listOf("bench", "squat"))
        data.baselines.remove("bench")
        assertEquals(listOf("squat"), baselines().map { it.exercise.id })
    }

    @Test
    fun `実際の記録がなければ、初期設定の直近の値が前回の記録になる`() = runTest {
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10))

        val previous = assertNotNull(data.workouts.previousRecord("bench", excludeWorkoutId = null))
        assertTrue(previous.isBaseline)
        assertEquals(today, previous.date)
        assertEquals(60.0, previous.sets.single().weightKg)
        assertEquals(10, previous.sets.single().reps)
    }

    @Test
    fun `自己ベストだけの行は、前回の記録にならない`() = runTest {
        data.baselines.save(Baseline("bench", best = 80.0))
        assertNull(data.workouts.previousRecord("bench", excludeWorkoutId = null))
    }

    @Test
    fun `実際の記録ができたら、そちらが前回の記録になる`() = runTest {
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10))
        val planner = WorkoutPlanner(TestDatabase.sequentialIds())
        val planned = planner.plan("w1", 0, Exercise(id = "bench", name = "ベンチプレス"))
        data.workouts.createWorkout(Workout("w1", today.minusDays(3), null, Instant.parse("2026-11-13T10:00:00Z")), listOf(planned))
        planned.sets.forEach {
            data.workouts.updateSetValues(SetValues(it.id, 65.0, 8))
            data.workouts.setCompleted(it.id, true, Instant.parse("2026-11-13T10:30:00Z"))
        }
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-13T11:00:00Z"))

        val previous = assertNotNull(data.workouts.previousRecord("bench", excludeWorkoutId = null))
        assertEquals(false, previous.isBaseline)
        assertEquals(65.0, previous.sets.first().weightKg)
    }

    @Test
    fun `自己ベストは、記録と初期設定の大きいほう`() = runTest {
        val bench = Exercise(id = "bench", name = "ベンチプレス")
        assertNull(data.workouts.personalBest(bench, excludeWorkoutId = null))

        // 自己ベストを入れなくても、直近の値が下限になる
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10))
        assertEquals(60.0, data.workouts.personalBest(bench, null))

        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10, best = 80.0))
        assertEquals(80.0, data.workouts.personalBest(bench, null))

        // 記録が初期設定を超えたら、記録のほう
        val planned = WorkoutPlanner(TestDatabase.sequentialIds()).plan("w1", 0, bench)
        data.workouts.createWorkout(Workout("w1", today, null, Instant.parse("2026-11-16T10:00:00Z")), listOf(planned))
        data.workouts.updateSetValues(SetValues(planned.sets.first().id, 85.0, 3))
        data.workouts.setCompleted(planned.sets.first().id, true, Instant.parse("2026-11-16T10:10:00Z"))
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-16T10:30:00Z"))
        assertEquals(85.0, data.workouts.personalBest(bench, null))
    }

    @Test
    fun `初期設定を入れた種目でルーティンを始めると、3 セットとも直近の値で並ぶ`() = runTest {
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10))
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench"))

        val workoutId = StartWorkout(data.workouts, data.routines, data.exercises, clock, TestDatabase.sequentialIds("s"))(today, "push")
        val sets = assertNotNull(data.workouts.observeWorkout(workoutId).first()).exercises.single().sets

        assertEquals(3, sets.size)
        assertEquals(listOf(60.0, 60.0, 60.0), sets.map { it.weightKg })
        assertEquals(listOf(10, 10, 10), sets.map { it.reps })
    }

    @Test
    fun `セット数を保存でき、空欄にも戻せる`() = runTest {
        data.baselines.addExercises(listOf("bench"))
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10, sets = 4))
        assertEquals(4, baselines().single().baseline.sets)

        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10, sets = null))
        assertNull(baselines().single().baseline.sets)
    }

    @Test
    fun `セット数を入れていれば、前回の記録もそのセット数になり、ルーティンを始めたときのセット数も合う`() = runTest {
        data.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10, sets = 4))

        val previous = assertNotNull(data.workouts.previousRecord("bench", excludeWorkoutId = null))
        assertEquals(4, previous.sets.size)
        assertTrue(previous.setCountKnown)

        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench"))
        val workoutId = StartWorkout(data.workouts, data.routines, data.exercises, clock, TestDatabase.sequentialIds("s"))(today, "push")
        val sets = assertNotNull(data.workouts.observeWorkout(workoutId).first()).exercises.single().sets
        assertEquals(4, sets.size)
        assertEquals(listOf(60.0, 60.0, 60.0, 60.0), sets.map { it.weightKg })
    }

    @Test
    fun `種目を削除しても（非表示でなく消えるとき）、初期設定の行も消える`() = runTest {
        data.exercises.save(Exercise(id = "temp", name = "仮の種目"))
        data.baselines.addExercises(listOf("temp"))
        data.exercises.deleteOrHide("temp")
        assertTrue(baselines().isEmpty())
    }
}
