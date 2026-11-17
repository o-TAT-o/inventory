package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
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
        expanded: Set<String> = emptySet(),
        lastCompleted: String? = null,
        now: Instant = Instant.parse("2026-11-14T10:27:00Z"),
    ) = WorkoutUiMapper.map(
        detail, settings, contexts, focused, selected, draft, emptyList(), "Push", "ベンチが空いていなければダンベルに変更", today,
        expandedIds = expanded, lastCompletedId = lastCompleted, now = now,
    )

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
        assertEquals("前回 11/7", state.exercises[0].previous?.label)
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

    /** ベンチ 3 セット完了 / インクライン 1 セット未完了、のワークアウト。 */
    private val benchDone = detail(
        today,
        exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, true), set("a2", "we1", 1, 60.0, 9, true), set("a3", "we1", 2, 60.0, 9, true)),
        exercise("we2", 1, incline, set("b1", "we2", 0, 20.0, 10, false)),
    )

    @Test
    fun `すべて完了した種目は、1 行にたたまれ、完了の数と要約が出る`() {
        val first = map(benchDone).exercises[0]
        assertTrue(first.collapsed)
        assertEquals("3/3", "${first.doneCount}/${first.totalCount}")
        assertEquals("60 kg 合計 × 10 · 9 · 9", first.summary)
        assertFalse(map(benchDone).exercises[1].collapsed, "未完了のセットがある種目は、たたまない")
    }

    @Test
    fun `いま完了したばかりの種目と、自分で開いた種目は、たたまない`() {
        assertFalse(map(benchDone, lastCompleted = "we1").exercises[0].collapsed, "いま完了したばかり（自己ベストの説明などを見せるため）")
        assertFalse(map(benchDone, expanded = setOf("we1")).exercises[0].collapsed, "タップして開いた")
        // タップして直しているセットの種目も、たたまない
        assertFalse(map(benchDone, selected = "a1").exercises[0].collapsed)
    }

    @Test
    fun `すべてのセットが終わったら、いま完了した種目も含めて、全部たたむ`() {
        val allDone = detail(
            today,
            exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, true)),
            exercise("we2", 1, incline, set("b1", "we2", 0, 20.0, 10, true)),
        )
        val state = map(allDone, lastCompleted = "we2")
        assertTrue(state.allDone)
        assertTrue(state.exercises.all { it.collapsed })
    }

    @Test
    fun `フォームのある種目は、親の名前が見出しで、フォームの行が出る`() {
        val row = Exercise(id = "row", name = "ワンハンドダンベルロウ", equipment = Equipment.Dumbbell)
        val knee = Exercise(id = "row-knee", parentId = "row", name = "膝をベンチにつく", equipment = Equipment.Dumbbell)
        val unselected = exercise("we1", 0, row, set("a1", "we1", 0, 26.0, 10, false))
            .copy(forms = listOf(knee))
        val chosen = exercise("we1", 0, knee, set("a1", "we1", 0, 26.0, 10, false))
            .copy(parentName = "ワンハンドダンベルロウ", forms = listOf(knee))
        val plain = exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, false))

        val a = map(detail(today, unselected)).exercises.single()
        assertEquals("ワンハンドダンベルロウ", a.name)
        assertEquals("フォーム: 未選択", a.formLabel)
        assertTrue(a.formUnselected)

        val b = map(detail(today, chosen)).exercises.single()
        assertEquals("ワンハンドダンベルロウ", b.name, "見出しは親の名前")
        assertEquals("フォーム: 膝をベンチにつく", b.formLabel)
        assertFalse(b.formUnselected)
        assertEquals("ワンハンドダンベルロウ　1 セット目", map(detail(today, chosen)).dock?.title)

        assertNull(map(detail(today, plain)).exercises.single().formLabel, "フォームのない種目には、フォームの行は出ない")
    }

    @Test
    fun `調整項目の値は見出しの下に出て、詳細には前回の値が出る`() {
        val angle = AdjustmentField("angle", "incline", "ベンチ角度", AdjustmentValueType.Number, "°")
        val grip = AdjustmentField("grip", "incline", "グリップ", AdjustmentValueType.Text)
        val withValues = exercise("we2", 0, incline, set("b1", "we2", 0, 20.0, 10, false))
            .copy(adjustmentFields = listOf(angle, grip), adjustments = mapOf("angle" to "30", "grip" to "ワイド"))
        val previous = PreviousRecord(
            date = LocalDate.of(2026, 11, 7),
            sets = listOf(set("p1", "pw", 0, 20.0, 10, true)),
            weightMode = WeightMode.PerSide,
            repMode = RepMode.Normal,
            adjustments = mapOf("angle" to "45"),
        )

        val state = map(detail(today, withValues), contexts = mapOf("incline" to ExerciseContext(previous, 20.0)))

        assertEquals("ダンベル · ベンチ角度 30° · グリップ ワイド", state.exercises.single().meta)
        val items = state.dock!!.detail.adjustments
        assertEquals(listOf("30", "ワイド"), items.map { it.value })
        assertEquals("前回 45°", items[0].previousText)
        assertEquals("45", items[0].previousValue)
        assertNull(items[1].previousText, "前回の値がない項目には、前回は出ない")
    }

    @Test
    fun `自己ベストのセットの下に、これまでの最高値の説明が付く`() {
        val detail = detail(today, exercise("we1", 0, bench, set("a1", "we1", 0, 65.0, 5, true), set("a2", "we1", 1, 60.0, 8, true)))
        val state = map(detail, contexts = mapOf("bench" to ExerciseContext(previous = null, bestBefore = 62.5)))

        val sets = state.exercises.single().sets
        assertEquals("最大重量の更新　これまで 62.5 kg 合計", sets[0].pbNote)
        assertNull(sets[1].pbNote, "自己ベストでないセットには付かない")
        assertTrue(state.exercises.single().hasPersonalBest)
    }

    @Test
    fun `あとから入力のときは、まとめの文に時間を出さない。ふつうのときは分を出す`() {
        val workout = detail(today, exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, true)))
        assertEquals("1 種目 · 1 セット · 27 分", map(workout).summaryText)

        val quick = workout.copy(workout = workout.workout.copy(quickEntry = true))
        val state = map(quick)
        assertEquals("1 種目 · 1 セット", state.summaryText)
        assertTrue(state.quickEntry)
    }

    @Test
    fun `日付の見出しと、今日かどうかが出る`() {
        val yesterday = detail(today.minusDays(1), exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, false)))
        val state = map(yesterday)
        assertEquals("11月13日（金）", state.dateTitle)
        assertFalse(state.isToday)
        assertTrue(map(twoExercises).isToday)
    }

    @Test
    fun `初めて使うフォームの前回には、どのフォームの記録かが添えられる`() {
        val previous = PreviousRecord(
            date = LocalDate.of(2026, 11, 7),
            sets = listOf(set("p1", "pw", 0, 26.0, 10, true)),
            weightMode = WeightMode.Total,
            repMode = RepMode.Normal,
            sourceExerciseName = "膝をベンチにつく",
        )
        val state = map(twoExercises, contexts = mapOf("bench" to ExerciseContext(previous, 26.0)))
        assertEquals("「膝をベンチにつく」の直近 11/7", state.exercises[0].previous?.label)
    }

    @Test
    fun `詳細のパネルの副題に、重量の数え方が出るのは、重量と回数の種目だけ`() {
        val weighted = map(detail(today, exercise("we1", 0, incline, set("a1", "we1", 0, 20.0, 10, false))))
        assertEquals("ダンベル · 重量は片手", weighted.dock?.detail?.subtitle)

        val dips = Exercise(id = "dips", name = "ディップス", equipment = Equipment.Bodyweight, recordType = RecordType.Bodyweight, useAddedWeight = true)
        val bodyweight = map(detail(today, exercise("we1", 0, dips, set("a1", "we1", 0, 0.0, 10, false))))
        assertEquals("自重", bodyweight.dock?.detail?.subtitle)
    }

    @Test
    fun `メモは、あるものだけが出る。前回のメモは、同じ種目の実際の記録に書いたものだけ`() {
        val own = PreviousRecord(
            date = today.minusDays(7),
            sets = listOf(set("p1", "old", 0, 60.0, 10, true)),
            weightMode = WeightMode.Total,
            repMode = RepMode.Normal,
            memo = "3セット目でフォームが崩れた",
        )
        val withNotes = bench.copy(memo = "肩甲骨を寄せる")
        val written = exercise("we1", 0, withNotes, set("a1", "we1", 0, 60.0, 10, false))
            .let { it.copy(workoutExercise = it.workoutExercise.copy(memo = "今日は調子がいい")) }

        val state = map(detail(today, written), contexts = mapOf("bench" to ExerciseContext(previous = own, bestBefore = null)))
        val ui = state.exercises.single()
        assertEquals("3セット目でフォームが崩れた", ui.previousMemo)
        assertEquals("今日は調子がいい", ui.memo)
        assertEquals("肩甲骨を寄せる", ui.exerciseMemo)
        assertEquals("3セット目でフォームが崩れた", state.dock?.detail?.previousMemo)
        assertEquals("肩甲骨を寄せる", state.dock?.detail?.exerciseMemo)

        // ほかのフォームの記録を目安にしているときと、メモがないときは、出さない
        val fromSibling = own.copy(sourceExerciseName = "ナローグリップ")
        val plain = exercise("we1", 0, bench, set("a1", "we1", 0, 60.0, 10, false))
        val none = map(detail(today, plain), contexts = mapOf("bench" to ExerciseContext(previous = fromSibling, bestBefore = null))).exercises.single()
        assertNull(none.previousMemo)
        assertNull(none.memo)
        assertNull(none.exerciseMemo)
    }
}
