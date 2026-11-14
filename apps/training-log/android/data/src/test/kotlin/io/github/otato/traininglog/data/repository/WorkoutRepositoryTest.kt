package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PlannedWorkoutExercise
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRepositoryTest {
    private val data: DataModule = TestDatabase.create()
    private val planner = WorkoutPlanner(TestDatabase.sequentialIds())
    private val bench = Exercise(id = "bench", name = "ベンチプレス", bodyPart = BodyPart.Chest, equipment = Equipment.Barbell)
    private val squat = Exercise(id = "squat", name = "スクワット", bodyPart = BodyPart.Legs, equipment = Equipment.Barbell)
    private val day1 = LocalDate.of(2026, 11, 12)
    private val day2 = LocalDate.of(2026, 11, 14)

    @BeforeTest
    fun setUp() = runTest {
        data.exercises.save(bench)
        data.exercises.save(squat)
    }

    private suspend fun startWorkout(id: String, date: LocalDate, vararg exercises: Exercise): List<PlannedWorkoutExercise> {
        val planned = exercises.mapIndexed { index, exercise -> planner.plan(id, index, exercise) }
        data.workouts.createWorkout(Workout(id, date, null, Instant.parse("${date}T10:00:00Z")), planned)
        return planned
    }

    private suspend fun detail(id: String): WorkoutDetail = assertNotNull(data.workouts.observeWorkout(id).first())

    /** 種目の先頭から [count] セットを、重量・回数を入れて完了にする。 */
    private suspend fun completeSets(id: String, exerciseId: String, weight: Double, reps: Int, count: Int) {
        val exercise = detail(id).exercises.first { it.exercise.id == exerciseId }
        exercise.sets.take(count).forEach { set ->
            data.workouts.updateSetValues(SetValues(set.id, weight, reps))
            data.workouts.setCompleted(set.id, true, Instant.parse("2026-11-14T10:30:00Z"))
        }
    }

    @Test
    fun `ワークアウトを作ると、種目と未完了のセットが読める`() = runTest {
        startWorkout("w1", day2, bench, squat)
        val detail = detail("w1")
        assertEquals(listOf("ベンチプレス", "スクワット"), detail.exercises.map { it.exercise.name })
        assertEquals(3, detail.exercises[0].sets.size)
        assertTrue(detail.exercises.all { exercise -> exercise.sets.none { it.completed } })
        assertEquals(day2, detail.workout.date)
        assertNull(detail.workout.finishedAt)
    }

    @Test
    fun `セットを完了にして値を保存すると、画面が受け取る詳細に反映される`() = runTest {
        startWorkout("w1", day2, bench)
        completeSets("w1", "bench", weight = 62.5, reps = 8, count = 2)
        val sets = detail("w1").exercises.single().sets
        assertEquals(listOf(true, true, false), sets.map { it.completed })
        assertEquals(62.5, sets[0].weightKg)
        assertEquals(8, sets[1].reps)
    }

    @Test
    fun `ワークアウトを終えると、未完了のセットと、完了のない種目は消える`() = runTest {
        startWorkout("w1", day2, bench, squat)
        completeSets("w1", "bench", 60.0, 10, count = 2)

        assertTrue(data.workouts.finishWorkout("w1", Instant.parse("2026-11-14T11:00:00Z")))

        val detail = detail("w1")
        assertEquals(listOf("ベンチプレス"), detail.exercises.map { it.exercise.name })
        assertEquals(2, detail.exercises.single().sets.size)
        assertNotNull(detail.workout.finishedAt)
    }

    @Test
    fun `完了したセットが 1 つもなければ、ワークアウトごと破棄する`() = runTest {
        startWorkout("w1", day2, bench)
        assertFalse(data.workouts.finishWorkout("w1", Instant.parse("2026-11-14T11:00:00Z")))
        assertNull(data.workouts.observeWorkout("w1").first())
    }

    @Test
    fun `セットの削除は、同じ内容で元に戻せる`() = runTest {
        startWorkout("w1", day2, bench)
        completeSets("w1", "bench", 60.0, 10, count = 1)
        val target = detail("w1").exercises.single().sets.first()

        data.workouts.deleteSet(target.id)
        assertEquals(2, detail("w1").exercises.single().sets.size)

        data.workouts.addSet(target)
        val restored = detail("w1").exercises.single().sets
        assertEquals(3, restored.size)
        assertEquals(target, restored.first { it.id == target.id })
    }

    @Test
    fun `前回の記録は、終えたワークアウトの完了したセットで、いまのワークアウトは含まない`() = runTest {
        startWorkout("w1", day1, bench)
        completeSets("w1", "bench", 60.0, 10, count = 3)
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-12T11:00:00Z"))

        startWorkout("w2", day2, bench)
        completeSets("w2", "bench", 65.0, 8, count = 1)

        val previous = assertNotNull(data.workouts.previousRecord("bench", excludeWorkoutId = "w2"))
        assertEquals(day1, previous.date)
        assertEquals(listOf(60.0, 60.0, 60.0), previous.sets.map { it.weightKg })
        // 終えていないワークアウトは、前回の記録に数えない
        assertNull(data.workouts.previousRecord("bench", excludeWorkoutId = "w1"))
    }

    @Test
    fun `自己ベストは、終えたワークアウトの最大重量`() = runTest {
        assertNull(data.workouts.personalBest(bench, excludeWorkoutId = null))

        startWorkout("w1", day1, bench)
        completeSets("w1", "bench", 60.0, 10, count = 3)
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-12T11:00:00Z"))
        assertEquals(60.0, data.workouts.personalBest(bench, excludeWorkoutId = null))

        startWorkout("w2", day2, bench)
        completeSets("w2", "bench", 70.0, 5, count = 1)
        data.workouts.finishWorkout("w2", Instant.parse("2026-11-14T11:00:00Z"))
        assertEquals(70.0, data.workouts.personalBest(bench, excludeWorkoutId = null))
        // いまのワークアウトを除けば、その前までの最高値
        assertEquals(60.0, data.workouts.personalBest(bench, excludeWorkoutId = "w2"))
    }

    @Test
    fun `期間の要約には、日付・セット数・ボリューム・種目名・部位が入る`() = runTest {
        startWorkout("w1", day2, bench, squat)
        completeSets("w1", "bench", 60.0, 10, count = 3)
        completeSets("w1", "squat", 100.0, 5, count = 2)
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-14T11:00:00Z"))

        val summaries = data.workouts.observeSummaries(day1, day2).first()
        val summary = summaries.single()
        assertEquals(day2, summary.workout.date)
        assertEquals(5, summary.completedSetCount)
        assertEquals(60.0 * 10 * 3 + 100.0 * 5 * 2, summary.volumeKg)
        assertEquals(listOf("ベンチプレス", "スクワット"), summary.exerciseNames)
        assertEquals(listOf(BodyPart.Chest, BodyPart.Legs), summary.bodyParts)
        assertTrue(data.workouts.observeSummaries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)).first().isEmpty())
    }

    @Test
    fun `記録中のワークアウトと、最後に終えたワークアウトを区別して取れる`() = runTest {
        startWorkout("w1", day1, bench)
        completeSets("w1", "bench", 60.0, 10, count = 1)
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-12T11:00:00Z"))
        startWorkout("w2", day2, squat)

        assertEquals("w2", data.workouts.observeActiveWorkout().first()?.id)
        assertEquals("w1", data.workouts.observeLastFinished().first()?.workout?.id)
    }

    @Test
    fun `ギアは種目ごとに付け外しでき、前回のギアが次の下書きに引き継がれる`() = runTest {
        data.exercises.seedPresetsIfEmpty()
        val belt = data.gear.observeGear().first().first().id
        startWorkout("w1", day1, bench)
        val weId = detail("w1").exercises.single().workoutExercise.id
        data.workouts.setGear(weId, setOf(belt))
        completeSets("w1", "bench", 60.0, 10, count = 1)
        data.workouts.finishWorkout("w1", Instant.parse("2026-11-12T11:00:00Z"))

        assertEquals(setOf(belt), detail("w1").exercises.single().gearIds)
        assertEquals(setOf(belt), data.workouts.previousRecord("bench", null)?.gearIds)
    }

    @Test
    fun `その日だけ数え方を変えても、種目の設定は変わらない`() = runTest {
        startWorkout("w1", day2, bench)
        val weId = detail("w1").exercises.single().workoutExercise.id
        data.workouts.updateExerciseModes(
            weId,
            io.github.otato.traininglog.domain.model.WeightMode.PerSide,
            io.github.otato.traininglog.domain.model.RepMode.BothSidesTotal,
        )
        val workoutExercise = detail("w1").exercises.single().workoutExercise
        assertEquals(io.github.otato.traininglog.domain.model.WeightMode.PerSide, workoutExercise.weightMode)
        assertEquals(io.github.otato.traininglog.domain.model.WeightMode.Total, data.exercises.getExercise("bench")?.weightMode)
    }
}
