package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.backup.BackupJson
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.BackupFormatException
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** こわれにくさ: 種目を消したときのバリエーションの扱いと、こわれたファイルを読んだときの守り。 */
class RobustnessTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-17T01:00:00Z"), ZoneOffset.UTC)
    private val data = TestDatabase.create(clock = clock)
    private val ids = TestDatabase.sequentialIds("r")

    /** [exerciseId] を 1 セットやって終えた記録を作る。 */
    private suspend fun record(exerciseId: String, workoutId: String) {
        val exercise = assertNotNull(data.exercises.getExercise(exerciseId))
        val planned = WorkoutPlanner(ids).plan(workoutId, 0, exercise).let { it.copy(sets = it.sets.take(1)) }
        data.workouts.createWorkout(Workout(workoutId, LocalDate.of(2026, 11, 10), null, Instant.parse("2026-11-10T10:00:00Z")), listOf(planned))
        data.workouts.updateSetValues(SetValues(planned.sets.single().id, 24.0, 10))
        data.workouts.setCompleted(planned.sets.single().id, true, Instant.parse("2026-11-10T10:10:00Z"))
        data.workouts.finishWorkout(workoutId, Instant.parse("2026-11-10T11:00:00Z"))
    }

    // ---- 種目を消す ----

    @Test
    fun `記録のない親の種目を消すと、バリエーションも一緒に消える`() = runTest {
        data.exercises.save(Exercise(id = "row", name = "ワンハンドダンベルロウ"))
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく"))
        data.exercises.save(Exercise(id = "stand", parentId = "row", name = "立って手をつく"))

        data.exercises.deleteOrHide("row")

        assertNull(data.exercises.getExercise("row"))
        // バリエーションが、名前だけの種目として残らない
        assertNull(data.exercises.getExercise("knee"))
        assertNull(data.exercises.getExercise("stand"))
    }

    @Test
    fun `記録のあるバリエーションがあれば、親も消さずに非表示にする（記録に、親の名前を出すため）`() = runTest {
        data.exercises.save(Exercise(id = "row", name = "ワンハンドダンベルロウ"))
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく"))
        data.exercises.save(Exercise(id = "stand", parentId = "row", name = "立って手をつく"))
        record("knee", "w1")

        data.exercises.deleteOrHide("row")

        assertTrue(assertNotNull(data.exercises.getExercise("row")).hidden)
        val knee = assertNotNull(data.exercises.getExercise("knee"))
        assertTrue(knee.hidden)
        assertEquals("row", knee.parentId)
        // 記録のないバリエーションは、消える
        assertNull(data.exercises.getExercise("stand"))
        // 種目を選ぶ一覧には、どれも出ない
        assertEquals(emptyList(), data.exercises.observeExercises().first().map { it.id })
        // 記録は残っていて、親の名前つきで読める
        assertEquals("ワンハンドダンベルロウ（膝をベンチにつく）", assertNotNull(data.workouts.observeWorkout("w1").first()).exercises.single().fullName)
    }

    @Test
    fun `バリエーションだけを消しても、親と、ほかのバリエーションは残る`() = runTest {
        data.exercises.save(Exercise(id = "row", name = "ワンハンドダンベルロウ"))
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく"))
        data.exercises.save(Exercise(id = "stand", parentId = "row", name = "立って手をつく"))

        data.exercises.deleteOrHide("knee")

        assertNull(data.exercises.getExercise("knee"))
        assertNotNull(data.exercises.getExercise("row"))
        assertNotNull(data.exercises.getExercise("stand"))
    }

    // ---- こわれたファイル ----

    private suspend fun fill() {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", targetSets = 3))
        record("bench", "w1")
    }

    @Test
    fun `中身のつじつまが合わないファイルは断り、いまのデータは変わらない`() = runTest {
        fill()
        val good = data.backup.snapshot()
        // ない種目（ワークアウトの種目）を指すセットが混ざっている
        val broken = good.copy(sets = good.sets + SetRecord(id = "orphan", workoutExerciseId = "no-such-exercise", completed = true))

        assertFailsWith<BackupFormatException> { data.backup.restore(broken) }

        val after = data.backup.snapshot()
        assertEquals(good.exercises, after.exercises)
        assertEquals(good.routines, after.routines)
        assertEquals(good.workouts, after.workouts)
        assertEquals(good.sets, after.sets)
    }

    @Test
    fun `日付の形がおかしいファイルは、読み込めないと知らせる（落ちない）`() = runTest {
        fill()
        val text = BackupJson.encode(data.backup.snapshot())
        assertTrue("2026-11-10" in text)

        assertFailsWith<BackupFormatException> { BackupJson.decode(text.replace("2026-11-10", "10/11/2026")) }
        // 途中で切れたファイル
        assertFailsWith<BackupFormatException> { BackupJson.decode(text.take(text.length / 2)) }
        // 空のファイル
        assertFailsWith<BackupFormatException> { BackupJson.decode("") }
    }
}
