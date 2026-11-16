package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** バリエーション（F-EX-14〜18）、調整項目（F-EX-10、F-WO-12）、並べ替え（F-UI-01）。 */
class VariationAdjustmentReorderTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val data: DataModule = TestDatabase.create(clock = clock)
    private val today = LocalDate.of(2026, 11, 16)
    private val planner = WorkoutPlanner(TestDatabase.sequentialIds("p"))
    private var workoutCounter = 0

    private val row = Exercise(id = "row", name = "ワンハンドダンベルロウ")
    private val kneeRow = Exercise(id = "row-knee", parentId = "row", name = "膝をベンチにつく", sortOrder = 0)
    private val standRow = Exercise(id = "row-stand", parentId = "row", name = "立って背もたれに手をつく", sortOrder = 1)

    /** 終えたワークアウトを 1 つ作る（1 セット完了。重量 [weight]、回数 10）。 */
    private suspend fun finishedWorkout(exercise: Exercise, date: LocalDate, weight: Double, adjustments: Map<String, String> = emptyMap()): String {
        val id = "w${workoutCounter++}"
        val planned = planner.plan(id, 0, exercise).let { it.copy(sets = it.sets.take(1), adjustments = adjustments) }
        data.workouts.createWorkout(Workout(id, date, null, Instant.parse("${date}T10:00:00Z")), listOf(planned))
        data.workouts.updateSetValues(SetValues(planned.sets.single().id, weight, 10))
        data.workouts.setCompleted(planned.sets.single().id, true, Instant.parse("${date}T10:10:00Z"))
        data.workouts.finishWorkout(id, Instant.parse("${date}T11:00:00Z"))
        return id
    }

    @Test
    fun `バリエーションを持つ親の種目と、バリエーションの一覧が取れる`() = runTest {
        listOf(row, kneeRow, standRow).forEach { data.exercises.save(it) }
        assertEquals(listOf("row-knee", "row-stand"), data.exercises.observeChildren("row").first().map { it.id })
        data.exercises.save(standRow.copy(hidden = true))
        assertEquals(listOf("row-knee"), data.exercises.observeChildren("row").first().map { it.id }, "非表示のバリエーションは出さない")
    }

    @Test
    fun `初めて使うバリエーションは、ほかのバリエーションの直近の記録を前回の目安にする`() = runTest {
        listOf(row, kneeRow, standRow).forEach { data.exercises.save(it) }
        finishedWorkout(kneeRow, today.minusDays(5), 26.0)

        val own = assertNotNull(data.workouts.previousRecord("row-knee", null))
        assertNull(own.sourceExerciseName, "自分の記録のときは、出どころは付かない")

        val sibling = assertNotNull(data.workouts.previousRecord("row-stand", null))
        assertEquals("膝をベンチにつく", sibling.sourceExerciseName)
        assertEquals(26.0, sibling.sets.single().weightKg)

        // 親の種目も、バリエーションの直近の記録を目安にする
        assertEquals("膝をベンチにつく", assertNotNull(data.workouts.previousRecord("row", null)).sourceExerciseName)
    }

    @Test
    fun `記録がすでにある種目に、あとからバリエーションを作ったら、記録を移せる`() = runTest {
        listOf(row, kneeRow).forEach { data.exercises.save(it) }
        finishedWorkout(row, today.minusDays(7), 24.0)
        finishedWorkout(row, today.minusDays(3), 26.0)
        assertEquals(mapOf("row" to 2), data.exercises.observeRecordCounts().first())

        data.exercises.moveRecords("row", "row-knee")

        assertEquals(mapOf("row-knee" to 2), data.exercises.observeRecordCounts().first())
        assertEquals(2, data.workouts.observeExerciseHistory("row-knee").first().size)
        assertTrue(data.workouts.observeExerciseHistory("row").first().isEmpty())
    }

    @Test
    fun `履歴は、バリエーションをまとめて見ることも、別々に見ることもできる`() = runTest {
        listOf(row, kneeRow, standRow).forEach { data.exercises.save(it) }
        finishedWorkout(row, today.minusDays(9), 22.0)
        finishedWorkout(kneeRow, today.minusDays(5), 26.0)
        finishedWorkout(standRow, today.minusDays(1), 24.0)

        val together = data.workouts.observeExerciseHistory("row", includeVariations = true).first()
        assertEquals(listOf("row-stand", "row-knee", "row"), together.map { it.exerciseId }, "新しい順")
        assertEquals(1, data.workouts.observeExerciseHistory("row").first().size, "まとめないときは、親の記録だけ")
        assertEquals(1, data.workouts.observeExerciseHistory("row-knee").first().size)
    }

    @Test
    fun `調整項目を定義すると、前回の値が、次のワークアウトの初期値になる`() = runTest {
        data.exercises.save(row.copy(id = "bench", name = "ベンチプレス"))
        data.exercises.saveAdjustmentFields("bench", listOf(AdjustmentField("angle", "bench", "ベンチ角度", AdjustmentValueType.Number, "°")))
        val first = finishedWorkout(Exercise(id = "bench", name = "ベンチプレス"), today.minusDays(7), 60.0, mapOf("angle" to "30"))
        assertEquals(mapOf("angle" to "30"), assertNotNull(data.workouts.previousRecord("bench", null)).adjustments)

        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench"))
        val started = StartWorkout(data.workouts, data.routines, data.exercises, clock, TestDatabase.sequentialIds("s"))(today, "push")

        val detail = assertNotNull(data.workouts.observeWorkout(started).first()).exercises.single()
        assertEquals(mapOf("angle" to "30"), detail.adjustments, "前回の値が入っている")
        assertEquals(listOf("ベンチ角度"), detail.adjustmentFields.map { it.name })
        assertTrue(first.isNotEmpty())
    }

    @Test
    fun `調整項目の値を変えると、その記録だけ変わる。消すこともできる`() = runTest {
        data.exercises.save(row.copy(id = "bench", name = "ベンチプレス"))
        data.exercises.saveAdjustmentFields("bench", listOf(AdjustmentField("angle", "bench", "ベンチ角度", AdjustmentValueType.Number, "°")))
        val planned = planner.plan("w", 0, Exercise(id = "bench", name = "ベンチプレス"))
        data.workouts.createWorkout(Workout("w", today, null, Instant.parse("2026-11-16T09:00:00Z")), listOf(planned))
        val weId = planned.workoutExercise.id

        data.workouts.setAdjustment(weId, "angle", "45")
        assertEquals("45", data.workouts.observeWorkout("w").first()!!.exercises.single().adjustments["angle"])
        data.workouts.setAdjustment(weId, "angle", null)
        assertTrue(data.workouts.observeWorkout("w").first()!!.exercises.single().adjustments.isEmpty())
    }

    @Test
    fun `調整項目の定義を置き換えると、無くなった項目の値も消える`() = runTest {
        data.exercises.save(row.copy(id = "bench", name = "ベンチプレス"))
        val angle = AdjustmentField("angle", "bench", "ベンチ角度", AdjustmentValueType.Number, "°")
        val seat = AdjustmentField("seat", "bench", "シート位置", AdjustmentValueType.Text)
        data.exercises.saveAdjustmentFields("bench", listOf(angle, seat))
        finishedWorkout(Exercise(id = "bench", name = "ベンチプレス"), today.minusDays(2), 60.0, mapOf("angle" to "30", "seat" to "3"))

        data.exercises.saveAdjustmentFields("bench", listOf(seat.copy(name = "シートの位置")))

        assertEquals(listOf("シートの位置"), data.exercises.getAdjustmentFields("bench").map { it.name })
        assertEquals(mapOf("seat" to "3"), assertNotNull(data.workouts.previousRecord("bench", null)).adjustments)
    }

    @Test
    fun `親の種目で始めた記録を、選んだバリエーションに付け替えられる`() = runTest {
        listOf(row, kneeRow).forEach { data.exercises.save(it) }
        val planned = planner.plan("w", 0, row)
        data.workouts.createWorkout(Workout("w", today, null, Instant.parse("2026-11-16T09:00:00Z")), listOf(planned))
        val weId = planned.workoutExercise.id

        val values = planned.sets.map { SetValues(it.id, 26.0, 10) }
        data.workouts.changeExercise(weId, kneeRow, values)

        val detail = data.workouts.observeWorkout("w").first()!!.exercises.single()
        assertEquals("row-knee", detail.exercise.id)
        assertEquals(setOf(26.0), detail.sets.map { it.weightKg }.toSet())
    }

    @Test
    fun `種目・ギア・ルーティン・ルーティンの種目・記録の種目を、並べ替えられる`() = runTest {
        listOf("a", "b", "c").forEach { data.exercises.save(Exercise(id = it, name = "種目$it")) }
        data.exercises.reorder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), data.exercises.observeExercises().first().map { it.id })

        listOf("g1", "g2", "g3").forEach { data.gear.save(Gear(it, "ギア$it")) }
        data.gear.reorder(listOf("g3", "g1", "g2"))
        assertEquals(listOf("g3", "g1", "g2"), data.gear.observeGear().first().map { it.id })

        listOf("r1", "r2").forEach { data.routines.saveRoutine(Routine(it, "ルーティン$it")) }
        data.routines.reorderRoutines(listOf("r2", "r1"))
        assertEquals(listOf("r2", "r1"), data.routines.observeRoutines().first().map { it.routine.id })

        listOf("a", "b", "c").forEachIndexed { index, id -> data.routines.saveItem(RoutineItem("i-$id", "r1", id, sortOrder = index)) }
        data.routines.reorderItems("r1", listOf("i-c", "i-a", "i-b"))
        assertEquals(listOf("c", "a", "b"), data.routines.observeRoutine("r1").first()!!.items.map { it.exercise.id })

        val planned = listOf("a", "b", "c").mapIndexed { index, id -> planner.plan("w", index, Exercise(id = id, name = "種目$id")) }
        data.workouts.createWorkout(Workout("w", today, null, Instant.parse("2026-11-16T09:00:00Z")), planned)
        val ids = planned.map { it.workoutExercise.id }
        data.workouts.reorderExercises("w", listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("c", "a", "b"), data.workouts.observeWorkout("w").first()!!.exercises.map { it.exercise.id })
    }

    @Test
    fun `ギアの使った回数を数えられる`() = runTest {
        data.gear.save(Gear("belt", "ベルト"))
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        val planned = planner.plan("w", 0, Exercise(id = "bench", name = "ベンチプレス")).copy(gearIds = setOf("belt"))
        data.workouts.createWorkout(Workout("w", today, null, Instant.parse("2026-11-16T09:00:00Z")), listOf(planned))
        assertEquals(mapOf("belt" to 1), data.gear.observeUsage().first())
    }
}
