package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutExercise
import io.github.otato.traininglog.domain.model.WorkoutExerciseDetail
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 記録画面の状態の組み立て（下のパネルの対象・自己ベストの印・表記）。DB にも Android にも触れない。 */
class WorkoutUiMapperTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val bench = Exercise(id = "bench", name = "ベンチプレス", equipment = Equipment.Barbell)
    private val incline = Exercise(id = "incline", name = "インクラインダンベルプレス", equipment = Equipment.Dumbbell, weightMode = WeightMode.PerSide)

    private fun set(id: String, we: String, order: Int, weight: Double, reps: Int, completed: Boolean) =
        SetRecord(id = id, workoutExerciseId = we, sortOrder = order, weightKg = weight, reps = reps, completed = completed)

    private fun exercise(we: String, order: Int, exercise: Exercise, vararg sets: SetRecord) = WorkoutExerciseDetail(
        workoutExercise = WorkoutExercise(we, "w", exercise.id, order, exercise.weightMode, exercise.repMode),
        exercise = exercise,
        sets = sets.toList(),
    )

    private fun detail(date: LocalDate = today, vararg exercises: WorkoutExerciseDetail) = WorkoutDetail(
        Workout("w", date, null, Instant.parse("2026-11-14T10:00:00Z")),
        exercises.toList(),
    )

    private fun map(
        detail: WorkoutDetail,
        contexts: Map<String, ExerciseContext> = emptyMap(),
        focused: String? = null,
        selected: String? = null,
        draft: Draft? = null,
        settings: AppSettings = AppSettings(),
    ) = WorkoutUiMapper.map(detail, settings, contexts, focused, selected, draft, emptyList(), "Push", "ベンチが空いていなければダンベルに変更", today)

    private val twoExercises = detail(
        today,
        exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, true), set("a2", "we1", 1, 60.0, 10, false)),
        exercise("we2", 1, incline, set("b1", "we2", 0, 20.0, 10, false)),
    )

    @Test
    fun `下のパネルの対象は、先頭から数えて最初の未完了のセット`() {
        val target = assertNotNull(WorkoutUiMapper.resolveTarget(twoExercises, null, null))
        assertEquals("a2", target.set.id)
        assertFalse(target.editingCompleted)
    }

    @Test
    fun `見ている種目を指定すると、その種目のセットが対象になる`() {
        val target = assertNotNull(WorkoutUiMapper.resolveTarget(twoExercises, focusedWorkoutExerciseId = "we2", selectedSetId = null))
        assertEquals("b1", target.set.id)
    }

    @Test
    fun `タップして選んだセットは、完了していても直せる`() {
        val target = assertNotNull(WorkoutUiMapper.resolveTarget(twoExercises, null, selectedSetId = "a1"))
        assertEquals("a1", target.set.id)
        assertTrue(target.editingCompleted)
    }

    @Test
    fun `すべて完了したら対象はなく、「すべて終わりました」の状態になる`() {
        val done = detail(today, exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, true)))
        assertNull(WorkoutUiMapper.resolveTarget(done, null, null))
        val state = map(done)
        assertNull(state.dock)
        assertTrue(state.allDone)
        assertFalse(state.hasIncompleteSets)
    }

    @Test
    fun `重量と回数の入力欄に、単位と数え方の印が付く`() {
        val dock = assertNotNull(map(twoExercises).dock)
        assertEquals("ベンチプレス　2 セット目", dock.title)
        val weight = dock.fields.first { it.key == FieldKey.Weight }
        assertEquals("60", weight.valueText)
        assertEquals("kg 合計", weight.unitText)
        assertEquals("回", dock.fields.first { it.key == FieldKey.Reps }.unitText)

        val dumbbell = assertNotNull(map(twoExercises, focused = "we2").dock)
        assertEquals("kg 片手", dumbbell.fields.first { it.key == FieldKey.Weight }.unitText)
    }

    @Test
    fun `入力途中の値が、保存を待たずに画面へ出る`() {
        val dock = assertNotNull(map(twoExercises, draft = Draft("a2", 62.5, 9, null, null)).dock)
        assertEquals("62.5", dock.fields.first { it.key == FieldKey.Weight }.valueText)
        assertEquals("9", dock.fields.first { it.key == FieldKey.Reps }.valueText)
        // 入力中の行にも同じ値が出る
        val row = map(twoExercises, draft = Draft("a2", 62.5, 9, null, null)).exercises[0].sets[1]
        assertEquals("62.5 kg", row.weightText)
        assertTrue(row.isCurrent)
    }

    @Test
    fun `lb に切り替えると、表示も入力欄も lb になる`() {
        val lb = AppSettings(weightUnit = WeightUnit.Lb)
        val dock = assertNotNull(map(twoExercises, settings = lb).dock)
        assertEquals("132.28", dock.fields.first { it.key == FieldKey.Weight }.valueText)
        assertTrue(dock.fields.first { it.key == FieldKey.Weight }.unitText.startsWith("lb"))
    }

    @Test
    fun `以前の最高値を超えたセットに、自己ベストの印が付く`() {
        val detail = detail(today, exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, true), set("a2", "we1", 1, 65.0, 5, true)))
        val withHistory = map(detail, contexts = mapOf("bench" to ExerciseContext(previous = null, bestBefore = 62.5)))
        assertEquals(listOf(false, true), withHistory.exercises.single().sets.map { it.isPersonalBest })
        // 以前の記録がなければ、初回には印を付けない
        val firstTime = map(detail, contexts = mapOf("bench" to ExerciseContext(previous = null, bestBefore = null)))
        assertTrue(firstTime.exercises.single().sets.none { it.isPersonalBest })
    }

    @Test
    fun `前回の記録が、種目の見出しと下のパネルの副題に出る`() {
        val previous = PreviousRecord(
            date = LocalDate.of(2026, 11, 7),
            sets = listOf(set("p1", "pw", 0, 57.5, 10, true), set("p2", "pw", 1, 57.5, 9, true)),
            weightMode = WeightMode.Total,
            repMode = RepMode.Normal,
        )
        val state = map(twoExercises, contexts = mapOf("bench" to ExerciseContext(previous, 57.5)))
        assertEquals("11/7", state.exercises[0].previous?.dateText)
        assertEquals("× 10 · 9", state.exercises[0].previous?.line?.repsText)
        // 2 セット目の副題は、前回の 2 セット目
        assertEquals("前回 57.5 kg × 9", state.dock?.subtitle)
    }

    @Test
    fun `今日以外の日付のときは、日付が出る`() {
        val yesterday = detail(today.minusDays(1), exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, false)))
        assertEquals("11/13（金）", map(yesterday).dateLabel)
        assertNull(map(twoExercises).dateLabel)
    }

    @Test
    fun `ルーティンのメモとタイトルが出る`() {
        val state = map(twoExercises)
        assertEquals("Push", state.title)
        assertEquals("ベンチが空いていなければダンベルに変更", state.routineMemo)
    }

    @Test
    fun `種目の切り替えは、未完了のセットがある種目の中で前後に動ける`() {
        val first = assertNotNull(map(twoExercises).dock)
        assertFalse(first.canGoPrevious)
        assertTrue(first.canGoNext)
        val second = assertNotNull(map(twoExercises, focused = "we2").dock)
        assertTrue(second.canGoPrevious)
        assertFalse(second.canGoNext)
    }
}
