package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import io.github.otato.traininglog.domain.usecase.AddSet
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

class RoutineRepositoryTest {
    private val data = TestDatabase.create()

    @BeforeTest
    fun setUp() = runTest {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        data.exercises.save(Exercise(id = "incline", name = "インクラインダンベルプレス"))
    }

    @Test
    fun `ルーティンと種目を保存すると、並び順どおりに種目名つきで読める`() = runTest {
        data.routines.saveRoutine(Routine("push", "Push", sortOrder = 0))
        data.routines.saveRoutine(Routine("pull", "Pull", sortOrder = 1))
        data.routines.saveItem(RoutineItem("i2", "push", "incline", sortOrder = 1, targetSets = 3))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", sortOrder = 0, targetSets = 4, targetWeightKg = 60.0, targetReps = 8, restSeconds = 120, restAfterSeconds = 180))

        val routines = data.routines.observeRoutines().first()
        assertEquals(listOf("Push", "Pull"), routines.map { it.routine.name })
        val push = routines.first()
        assertEquals(listOf("ベンチプレス", "インクラインダンベルプレス"), push.items.map { it.exercise.name })
        val first = push.items.first().item
        assertEquals(4, first.targetSets)
        assertEquals(60.0, first.targetWeightKg)
        assertEquals(120, first.restSeconds)
        assertEquals(180, first.restAfterSeconds)
    }

    @Test
    fun `ルーティンを削除すると、中の種目も消えるが、種目は残る`() = runTest {
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench"))
        data.routines.deleteRoutine("push")

        assertNull(data.routines.getRoutine("push"))
        assertNotNull(data.exercises.getExercise("bench"))
    }

    @Test
    fun `ルーティンの種目を更新しても、行は増えない`() = runTest {
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", targetSets = 3))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", targetSets = 5))
        val items = assertNotNull(data.routines.getRoutine("push")).items
        assertEquals(1, items.size)
        assertEquals(5, items.single().item.targetSets)
    }

    @Test
    fun `次の並び順の番号は、いまの最大の次`() = runTest {
        assertEquals(0, data.routines.nextRoutineSortOrder())
        data.routines.saveRoutine(Routine("push", "Push", sortOrder = 0))
        assertEquals(1, data.routines.nextRoutineSortOrder())
        assertEquals(0, data.routines.nextItemSortOrder("push"))
    }
}

/** ワークアウトを始める・種目を足す・セットを足す、の組み立て（ドメインのユースケースを、本物のデータ層で動かす）。 */
class StartWorkoutTest {
    private val data = TestDatabase.create()
    private val ids = TestDatabase.sequentialIds("u")
    private val clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)
    private val start = StartWorkout(data.workouts, data.routines, clock, ids)
    private val today = LocalDate.of(2026, 11, 14)

    @BeforeTest
    fun setUp() = runTest {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        data.exercises.save(Exercise(id = "incline", name = "インクラインダンベルプレス"))
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", sortOrder = 0, targetSets = 4, targetWeightKg = 60.0, targetReps = 8, restSeconds = 120))
        data.routines.saveItem(RoutineItem("i2", "push", "incline", sortOrder = 1))
    }

    @Test
    fun `ルーティンから始めると、種目と目標のセットが展開される`() = runTest {
        val id = start(today, "push")
        val detail = assertNotNull(data.workouts.observeWorkout(id).first())

        assertEquals("push", detail.workout.routineId)
        assertEquals(listOf("ベンチプレス", "インクラインダンベルプレス"), detail.exercises.map { it.exercise.name })
        val bench = detail.exercises[0]
        assertEquals(4, bench.sets.size)
        assertEquals(listOf(60.0, 60.0, 60.0, 60.0), bench.sets.map { it.weightKg })
        assertEquals(listOf(8, 8, 8, 8), bench.sets.map { it.reps })
        assertEquals(120, bench.workoutExercise.restSeconds)
        // 目標のない種目は、3 セット・回数 10 から始まる
        assertEquals(3, detail.exercises[1].sets.size)
    }

    @Test
    fun `前回の記録があれば、目標のない種目はそこから始まる`() = runTest {
        val first = start(today.minusDays(2), "push")
        val incline = assertNotNull(data.workouts.observeWorkout(first).first()).exercises[1]
        incline.sets.forEachIndexed { index, set ->
            data.workouts.updateSetValues(SetValues(set.id, weightKg = 20.0 + index, reps = 10 - index))
            data.workouts.setCompleted(set.id, true, Instant.parse("2026-11-12T10:30:00Z"))
        }
        data.workouts.finishWorkout(first, Instant.parse("2026-11-12T11:00:00Z"))

        val second = start(today, "push")
        val sets = assertNotNull(data.workouts.observeWorkout(second).first()).exercises[1].sets
        assertEquals(listOf(20.0, 21.0, 22.0), sets.map { it.weightKg })
        assertEquals(listOf(10, 9, 8), sets.map { it.reps })
    }

    @Test
    fun `ルーティンなしで始めると、種目のない自由記録になり、あとから種目とセットを足せる`() = runTest {
        val id = start(today, null)
        assertEquals(emptyList(), assertNotNull(data.workouts.observeWorkout(id).first()).exercises)

        AddExerciseToWorkout(data.workouts, data.exercises, io.github.otato.traininglog.domain.logic.WorkoutPlanner(ids))(id, "bench", 0)
        val exercise = assertNotNull(data.workouts.observeWorkout(id).first()).exercises.single()
        assertEquals(3, exercise.sets.size)

        AddSet(data.workouts, io.github.otato.traininglog.domain.logic.WorkoutPlanner(ids))(exercise.workoutExercise.id, exercise.exercise, exercise.sets)
        assertEquals(4, assertNotNull(data.workouts.observeWorkout(id).first()).exercises.single().sets.size)
    }
}
