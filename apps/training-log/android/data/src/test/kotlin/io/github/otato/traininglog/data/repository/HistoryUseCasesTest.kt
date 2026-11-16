package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.usecase.CopyWorkout
import io.github.otato.traininglog.domain.usecase.SaveWorkoutAsRoutine
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

/** 過去のワークアウトのコピー（F-HI-04）と、ルーティンとして保存。本物のデータ層で動かす。 */
class HistoryUseCasesTest {
    private val data = TestDatabase.create()
    private val ids = TestDatabase.sequentialIds("h")
    private val clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)
    private val start = StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)
    private val copy = CopyWorkout(data.workouts, data.exercises, clock, ids)
    private val saveAsRoutine = SaveWorkoutAsRoutine(data.workouts, data.routines, ids)
    private val today = LocalDate.of(2026, 11, 14)

    @BeforeTest
    fun setUp() = runTest {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        data.exercises.save(Exercise(id = "incline", name = "インクラインダンベルプレス"))
        data.exercises.save(Exercise(id = "fly", name = "フライ"))
        data.gear.save(Gear("belt", "ベルト"))
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", sortOrder = 0, targetSets = 3, restSeconds = 150))
        data.routines.saveItem(RoutineItem("i2", "push", "incline", sortOrder = 1, targetSets = 3))
        data.routines.saveItem(RoutineItem("i3", "push", "fly", sortOrder = 2, targetSets = 3))
    }

    /** ベンチを 2 セット（60×10、62.5×8）、インクラインを 1 セットやって終える。フライはやらない。 */
    private suspend fun finishedWorkout(): String {
        val id = start(today.minusDays(7), "push")
        val detail = assertNotNull(data.workouts.observeWorkout(id).first())
        val at = Instant.parse("2026-11-07T10:30:00Z")
        val bench = detail.exercises[0]
        data.workouts.updateExerciseModes(bench.workoutExercise.id, WeightMode.PerSide, bench.workoutExercise.repMode)
        data.workouts.setGear(bench.workoutExercise.id, setOf("belt"))
        data.workouts.updateSetValues(SetValues(bench.sets[0].id, 60.0, 10))
        data.workouts.setCompleted(bench.sets[0].id, true, at)
        data.workouts.updateSetValues(SetValues(bench.sets[1].id, 62.5, 8))
        data.workouts.setCompleted(bench.sets[1].id, true, at)
        val incline = detail.exercises[1]
        data.workouts.updateSetValues(SetValues(incline.sets[0].id, 20.0, 12))
        data.workouts.setCompleted(incline.sets[0].id, true, at)
        data.workouts.finishWorkout(id, Instant.parse("2026-11-07T11:00:00Z"))
        return id
    }

    @Test
    fun `コピーすると、やった種目とセットの値で、今日のワークアウトが始まる`() = runTest {
        val source = finishedWorkout()

        val id = assertNotNull(copy(source, today))
        val detail = assertNotNull(data.workouts.observeWorkout(id).first())

        assertEquals(today, detail.workout.date)
        assertEquals("push", detail.workout.routineId)
        assertNull(detail.workout.finishedAt)
        // やっていない種目（フライ）は入らない
        assertEquals(listOf("ベンチプレス", "インクラインダンベルプレス"), detail.exercises.map { it.exercise.name })
        val bench = detail.exercises[0]
        assertEquals(listOf(60.0, 62.5), bench.sets.map { it.weightKg })
        assertEquals(listOf(10, 8), bench.sets.map { it.reps })
        assertEquals(listOf(false, false), bench.sets.map { it.completed })
        // その日だけ変えた数え方・ギア・休憩も写る
        assertEquals(WeightMode.PerSide, bench.workoutExercise.weightMode)
        assertEquals(setOf("belt"), bench.gearIds)
        assertEquals(150, bench.workoutExercise.restSeconds)
    }

    @Test
    fun `完了したセットのないワークアウトは、コピーできない`() = runTest {
        val empty = start(today.minusDays(1), "push")
        assertNull(copy(empty, today))
        assertNull(copy("no-such-workout", today))
    }

    @Test
    fun `ルーティンとして保存すると、やった種目とセット数が目標になる`() = runTest {
        val source = finishedWorkout()

        val routineId = assertNotNull(saveAsRoutine(source, "  胸の日  "))
        val routine = assertNotNull(data.routines.getRoutine(routineId))

        assertEquals("胸の日", routine.routine.name)
        assertEquals(listOf("ベンチプレス", "インクラインダンベルプレス"), routine.items.map { it.exercise.name })
        assertEquals(listOf(2, 1), routine.items.map { it.item.targetSets })
        assertEquals(150, routine.items[0].item.restSeconds)
        // 一覧では、いまあるルーティンのあとに並ぶ
        assertEquals(listOf("Push", "胸の日"), data.routines.observeRoutines().first().map { it.routine.name })
    }
}
