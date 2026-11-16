package io.github.otato.traininglog.feature.workout

import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.usecase.AddSet
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.testing.MutableClock
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 記録画面の ViewModel を、本物のデータ層（メモリ上の SQLite）で動かして確かめる。
 * 下のパネルの操作（± と「完了」）が、DB の記録・休憩タイマー・画面の状態に正しくつながっているか。
 */
class WorkoutViewModelTest {
    private val harness = ViewModelTestHarness()
    private val data = TestDatabase.create()
    private val clock = MutableClock.at("2026-11-14T10:00:00Z")
    private val ids = TestDatabase.sequentialIds("t")

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    /** ベンチプレス 3 セット（60 kg × 10、種目のあとの休憩 200 秒）→ インクライン 2 セット、のルーティンを始める。 */
    private suspend fun startPushWorkout(date: LocalDate = LocalDate.of(2026, 11, 14), quickEntry: Boolean = false): String {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス", equipment = Equipment.Barbell))
        data.exercises.save(Exercise(id = "incline", name = "インクラインダンベルプレス", equipment = Equipment.Dumbbell, weightMode = WeightMode.PerSide))
        data.routines.saveRoutine(Routine("push", "Push", memo = "ベンチが空いていなければダンベルに変更"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", sortOrder = 0, targetSets = 3, targetWeightKg = 60.0, targetReps = 10, restAfterSeconds = 200))
        data.routines.saveItem(RoutineItem("i2", "push", "incline", sortOrder = 1, targetSets = 2, targetWeightKg = 20.0, targetReps = 10))
        return StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)(date, "push", quickEntry)
    }

    /** 終えたワークアウトを 1 つ作る（1 セット完了）。 */
    private suspend fun recordPast(exercise: Exercise, id: String, date: LocalDate, weight: Double, reps: Int) {
        val planned = WorkoutPlanner(ids).plan(id, 0, exercise).let { it.copy(sets = it.sets.take(1)) }
        data.workouts.createWorkout(Workout(id, date, null, Instant.parse("${date}T10:00:00Z")), listOf(planned))
        data.workouts.updateSetValues(SetValues(planned.sets.single().id, weight, reps))
        data.workouts.setCompleted(planned.sets.single().id, true, Instant.parse("${date}T10:10:00Z"))
        data.workouts.finishWorkout(id, Instant.parse("${date}T11:00:00Z"))
    }

    private fun viewModel(workoutId: String) = harness.track(
        WorkoutViewModel(
            workoutId = workoutId,
            workouts = data.workouts,
            routines = data.routines,
            exercises = data.exercises,
            gearRepository = data.gear,
            settingsRepository = data.settings,
            addSetUseCase = AddSet(data.workouts, WorkoutPlanner(ids)),
            clock = clock,
        ),
    )

    private suspend fun detail(workoutId: String): WorkoutDetail = assertNotNull(data.workouts.observeWorkout(workoutId).first())

    @Test
    fun `画面の状態には、ルーティンの名前・メモ・下のパネルの対象が出る`() = harness.run {
        val vm = viewModel(startPushWorkout())
        background { vm.state.collect {} }
        val state = vm.state.first { it.dock != null }

        assertEquals("Push", state.title)
        assertEquals("ベンチが空いていなければダンベルに変更", state.routineMemo)
        assertEquals(listOf("ベンチプレス", "インクラインダンベルプレス"), state.exercises.map { it.name })
        assertEquals("ベンチプレス　1 セット目", state.dock?.title)
    }

    @Test
    fun `完了すると、記録され、次のセットに値が引き継がれ、休憩が始まる`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        vm.adjust(FieldKey.Weight, 1) // 60 → 62.5（刻みの初期値は 2.5 kg）
        vm.adjust(FieldKey.Reps, -1) // 10 → 9
        vm.complete()

        val bench = detail(workoutId).exercises[0].sets
        assertEquals(listOf(true, false, false), bench.map { it.completed })
        assertEquals(62.5, bench[0].weightKg)
        assertEquals(9, bench[0].reps)
        // 次のセットは、いま入力した値で用意される（そのまま「完了」を押せば記録できる）
        assertEquals(62.5, bench[1].weightKg)
        assertEquals(9, bench[1].reps)

        val rest = assertNotNull(vm.rest.first { it != null })
        assertEquals("1:30", rest.text) // セット間の休憩の初期値（90 秒）
    }

    @Test
    fun `種目の最後のセットでは「種目のあとの休憩」、最後の種目の最後のセットでは休憩なし`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        repeat(3) { vm.complete() } // ベンチプレスの 3 セット
        assertEquals("3:20", assertNotNull(vm.rest.first { it != null }).text) // ルーティンの設定（200 秒）

        repeat(2) { vm.complete() } // インクラインの 2 セット
        assertTrue(detail(workoutId).exercises.all { it.isAllDone })
        assertNull(vm.rest.first(), "すべて終わったあとは、休憩を始めない")
        assertTrue(vm.state.first { it.allDone }.allDone)
    }

    @Test
    fun `休憩は延ばしたり短くしたり、とばしたりできる`() = harness.run {
        val vm = viewModel(startPushWorkout())
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        vm.complete()

        vm.adjustRest(10)
        assertEquals("1:40", assertNotNull(vm.rest.first { it != null }).text)
        vm.adjustRest(-30)
        assertEquals("1:10", assertNotNull(vm.rest.first { it != null }).text)
        vm.dismissRest()
        assertNull(vm.rest.first())
    }

    @Test
    fun `休憩の時間になると、一度だけ知らせて、終わった表示になる`() = harness.run {
        val vm = viewModel(startPushWorkout())
        val events = mutableListOf<WorkoutEvent>()
        background { vm.events.toList(events) }
        background { vm.state.collect {} }
        background { vm.rest.collect {} }
        vm.state.first { it.dock != null }
        vm.complete()

        clock.advanceSeconds(95) // 休憩（90 秒）を過ぎる。タイマーは 1 秒ごとに見に来る
        val rest = assertNotNull(vm.rest.first { it?.ended == true })

        assertTrue(rest.text.startsWith("+"))
        assertEquals(1, events.count { it == WorkoutEvent.RestFinished })
    }

    @Test
    fun `数値キーボードで直接入力すると、その値で保存される（lb は kg に直して持つ）`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        vm.input(FieldKey.Weight, 62.25)
        vm.input(FieldKey.Reps, 7.0)
        awaitSet(workoutId) { it.weightKg == 62.25 && it.reps == 7 }

        data.settings.update { it.copy(weightUnit = WeightUnit.Lb) }
        vm.state.first { it.unit == WeightUnit.Lb }
        vm.input(FieldKey.Weight, 135.0)
        awaitSet(workoutId) { kotlin.math.abs(it.weightKg - 135.0 * 0.45359237) < 1e-6 }
    }

    /** 入力の保存は、少し遅れて追いつく。保存されるまで待って確かめる。 */
    private suspend fun awaitSet(workoutId: String, condition: (io.github.otato.traininglog.domain.model.SetRecord) -> Boolean) {
        data.workouts.observeWorkout(workoutId).first { it?.exercises?.get(0)?.sets?.get(0)?.let(condition) == true }
    }

    @Test
    fun `「＋セット」で、いま見ている種目に、直前のセットの値で 1 セット増える`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        vm.addSet()

        val bench = detail(workoutId).exercises[0].sets
        assertEquals(4, bench.size)
        assertEquals(60.0, bench.last().weightKg)
        assertEquals(10, bench.last().reps)
    }

    @Test
    fun `セットを削除したら知らせて、元に戻せる`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        val events = mutableListOf<WorkoutEvent>()
        background { vm.events.toList(events) }
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        val target = detail(workoutId).exercises[1].sets[0]

        vm.deleteSet(target.id)
        assertEquals(1, detail(workoutId).exercises[1].sets.size)
        assertIs<WorkoutEvent.Undoable>(events.single())

        vm.undo()
        assertEquals(2, detail(workoutId).exercises[1].sets.size)
    }

    @Test
    fun `完了したセットをタップして直せる（完了のまま値だけ変わる）`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        vm.complete()
        val first = detail(workoutId).exercises[0].sets[0]

        vm.selectSet(first.id)
        val dock = assertNotNull(vm.state.first { it.dock?.setId == first.id }.dock)
        assertTrue(dock.editingCompleted)
        vm.adjust(FieldKey.Reps, -2)
        vm.complete() // 「直した」

        awaitSet(workoutId) { it.completed && it.reps == 8 }
        assertEquals("ベンチプレス　2 セット目", vm.state.first { it.dock?.setId != first.id }.dock?.title)
    }

    @Test
    fun `ワークアウトを終えると、未完了のセットは消えて、完了画面へ進む`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        val events = mutableListOf<WorkoutEvent>()
        background { vm.events.toList(events) }
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        vm.complete()

        vm.finish()

        assertEquals(WorkoutEvent.Finished(workoutId), events.last())
        val detail = detail(workoutId)
        assertNotNull(detail.workout.finishedAt)
        assertEquals(listOf("ベンチプレス"), detail.exercises.map { it.exercise.name })
        assertEquals(1, detail.exercises.single().sets.size)
    }

    @Test
    fun `完了したセットがなければ、終えると記録を破棄する`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        val events = mutableListOf<WorkoutEvent>()
        background { vm.events.toList(events) }

        vm.finish()

        assertEquals(WorkoutEvent.Discarded, events.last())
        assertNull(data.workouts.observeWorkout(workoutId).first())
    }

    @Test
    fun `ギアの付け外しと、その日の数え方の変更が保存される`() = harness.run {
        data.exercises.seedPresetsIfEmpty()
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        val weId = detail(workoutId).exercises[0].workoutExercise.id
        val belt = data.gear.observeGear().first().first().id

        vm.toggleGear(weId, belt)
        assertEquals(setOf(belt), detail(workoutId).exercises[0].gearIds)
        vm.toggleGear(weId, belt)
        assertEquals(emptySet(), detail(workoutId).exercises[0].gearIds)

        vm.setModes(weId, WeightMode.PerSide, RepMode.PerSide)
        val workoutExercise = detail(workoutId).exercises[0].workoutExercise
        assertEquals(WeightMode.PerSide, workoutExercise.weightMode)
        assertEquals(RepMode.PerSide, workoutExercise.repMode)
    }

    @Test
    fun `種目を外すと知らせて、元に戻すと、セット・完了・並びごと戻る`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        val events = mutableListOf<WorkoutEvent>()
        background { vm.events.toList(events) }
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        vm.complete() // ベンチの 1 セット目を完了
        val bench = detail(workoutId).exercises[0]

        vm.removeExercise(bench.workoutExercise.id)
        assertEquals(listOf("incline"), detail(workoutId).exercises.map { it.exercise.id })
        assertEquals(WorkoutEvent.Undoable("ベンチプレスを外しました"), events.last())

        vm.undo()
        val restored = detail(workoutId).exercises
        assertEquals(listOf("bench", "incline"), restored.map { it.exercise.id }, "元の並びに戻る")
        assertEquals(listOf(true, false, false), restored[0].sets.map { it.completed }, "完了も戻る")
    }

    @Test
    fun `種目を並べ替えると並びが変わり、知らせて、元に戻せる。同じ並びなら何もしない`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        val events = mutableListOf<WorkoutEvent>()
        background { vm.events.toList(events) }
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        val ids = detail(workoutId).exercises.map { it.workoutExercise.id }

        vm.reorderExercises(ids)
        assertTrue(events.isEmpty(), "並びが変わっていないので、知らせない")

        vm.reorderExercises(ids.reversed())
        assertEquals(listOf("incline", "bench"), detail(workoutId).exercises.map { it.exercise.id })
        assertEquals(WorkoutEvent.Undoable("種目を並べ替えました"), events.last())
        assertEquals(listOf("incline", "bench"), vm.state.first { it.exercises.first().exerciseId == "incline" }.exercises.map { it.exerciseId })

        vm.undo()
        assertEquals(listOf("bench", "incline"), detail(workoutId).exercises.map { it.exercise.id })
    }

    @Test
    fun `完了した種目は 1 行にたたまれ、いま完了した種目は残り、タップで開閉できる`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        val benchId = detail(workoutId).exercises[0].workoutExercise.id

        repeat(3) { vm.complete() } // ベンチの 3 セット
        assertEquals(false, vm.state.first { it.exercises[0].doneCount == 3 }.exercises[0].collapsed, "いま完了したばかりの種目は、たたまない")

        vm.complete() // インクラインに進んで、1 セット完了
        assertEquals(true, vm.state.first { it.exercises[1].doneCount == 1 }.exercises[0].collapsed)

        vm.toggleExpanded(benchId)
        assertEquals(false, vm.state.first { !it.exercises[0].collapsed }.exercises[0].collapsed, "タップで開く")
        vm.toggleExpanded(benchId)
        assertEquals(true, vm.state.first { it.exercises[0].collapsed }.exercises[0].collapsed, "もう一度タップで、たたむ")

        vm.complete() // インクラインの 2 セット目で、すべて終わる
        assertTrue(vm.state.first { it.allDone }.exercises.all { it.collapsed })
    }

    @Test
    fun `フォームを選ぶシートは、前回使ったフォームが選ばれていて、選んで始めると前回の値で用意し直される`() = harness.run {
        val row = Exercise(id = "row", name = "ワンハンドダンベルロウ", equipment = Equipment.Dumbbell)
        val knee = Exercise(id = "row-knee", parentId = "row", name = "膝をベンチにつく", equipment = Equipment.Dumbbell, sortOrder = 0)
        val stand = Exercise(id = "row-stand", parentId = "row", name = "立って背もたれに手をつく", equipment = Equipment.Dumbbell, sortOrder = 1)
        listOf(row, knee, stand).forEach { data.exercises.save(it) }
        recordPast(knee, "p-knee", LocalDate.of(2026, 11, 10), 26.0, 10)
        recordPast(stand, "p-stand", LocalDate.of(2026, 11, 7), 30.0, 8)
        data.routines.saveRoutine(Routine("pull", "Pull"))
        data.routines.saveItem(RoutineItem("p1", "pull", "row", sortOrder = 0))
        val workoutId = StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)(LocalDate.of(2026, 11, 14), "pull")
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        background { vm.formSheet.collect {} }
        val weId = detail(workoutId).exercises.single().workoutExercise.id

        val before = vm.state.first { it.exercises.isNotEmpty() }.exercises.single()
        assertEquals("ワンハンドダンベルロウ", before.name)
        assertEquals("フォーム: 未選択", before.formLabel)
        assertEquals(26.0, detail(workoutId).exercises.single().sets[0].weightKg, "親の種目は、直近のフォームの値で始まる")

        vm.openFormSheet(weId)
        val sheet = assertNotNull(vm.formSheet.first { it != null })
        assertEquals(listOf("row-knee", "row-stand"), sheet.options.map { it.exerciseId })
        assertEquals("row-knee", sheet.selectedId, "前回使ったフォーム（記録が新しいほう）が、最初から選ばれている")
        assertEquals(listOf("前回 11/10　26 kg 合計 × 10"), sheet.options[0].lines)
        assertEquals(listOf("前回 11/7　30 kg 合計 × 8"), sheet.options[1].lines)

        vm.chooseForm("row-stand")
        assertEquals("row-stand", assertNotNull(vm.formSheet.first { it?.selectedId == "row-stand" }).selectedId)
        vm.confirmForm(weId, "row-stand")

        val after = detail(workoutId).exercises.single()
        assertEquals("row-stand", after.exercise.id, "種目が、選んだフォームに付け替わる")
        assertTrue(after.sets.all { it.weightKg == 30.0 && it.reps == 8 }, "完了していないセットは、そのフォームの前回の値で用意し直される")
        assertNull(vm.formSheet.first(), "選んだらシートは閉じる")
        val chosen = vm.state.first { it.exercises.singleOrNull()?.formLabel == "フォーム: 立って背もたれに手をつく" }.exercises.single()
        assertEquals("ワンハンドダンベルロウ", chosen.name)
    }

    @Test
    fun `シートでフォームを足すと、選択肢に増えて、足したフォームが選ばれる`() = harness.run {
        data.exercises.save(Exercise(id = "row", name = "ワンハンドダンベルロウ"))
        data.routines.saveRoutine(Routine("pull", "Pull"))
        data.routines.saveItem(RoutineItem("p1", "pull", "row", sortOrder = 0))
        val workoutId = StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)(LocalDate.of(2026, 11, 14), "pull")
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        background { vm.formSheet.collect {} }
        val weId = detail(workoutId).exercises.single().workoutExercise.id

        vm.openFormSheet(weId)
        assertEquals(emptyList(), assertNotNull(vm.formSheet.first { it != null }).options, "フォームがまだないときは、選択肢は空")

        vm.addForm(weId, "ケーブルで")
        val sheet = assertNotNull(vm.formSheet.first { it?.options?.isNotEmpty() == true })
        assertEquals(listOf("ケーブルで"), sheet.options.map { it.name })
        assertEquals(sheet.options.single().exerciseId, sheet.selectedId, "足したフォームを選んだ状態にする")
        assertEquals("row", data.exercises.getExercise(sheet.options.single().exerciseId)?.parentId)
    }

    @Test
    fun `調整項目の値を入れると、見出しと詳細に出る。空にすると消える`() = harness.run {
        val workoutId = startPushWorkout()
        data.exercises.saveAdjustmentFields("bench", listOf(AdjustmentField("angle", "bench", "ベンチ角度", AdjustmentValueType.Number, "°")))
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        val weId = detail(workoutId).exercises[0].workoutExercise.id

        vm.setAdjustment(weId, "angle", "30")
        val state = vm.state.first { it.exercises[0].meta.contains("ベンチ角度 30°") }
        assertEquals("30", state.dock?.detail?.adjustments?.single()?.value)

        vm.setAdjustment(weId, "angle", null)
        assertEquals(emptyMap(), detail(workoutId).exercises[0].adjustments)
    }

    @Test
    fun `ギアをその場で作ると、この種目に付く。名前が空なら何もしない`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }
        val weId = detail(workoutId).exercises[0].workoutExercise.id

        vm.addGear(weId, "   ")
        assertEquals(emptyList(), data.gear.observeGear().first())

        vm.addGear(weId, "  ベルト ")
        val belt = data.gear.observeGear().first().single()
        assertEquals("ベルト", belt.name)
        assertEquals(setOf(belt.id), detail(workoutId).exercises[0].gearIds)
    }

    @Test
    fun `あとから入力では、完了しても休憩タイマーは動かない。ふつうに記録に戻すと動く`() = harness.run {
        val workoutId = startPushWorkout(date = LocalDate.of(2026, 11, 13), quickEntry = true)
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        val state = vm.state.first { it.dock != null }
        assertTrue(state.quickEntry)
        assertFalse(state.isToday)

        vm.complete()
        assertNull(vm.rest.first(), "あとから入力は、休憩タイマーなし")

        vm.setQuickEntry(false)
        assertFalse(vm.state.first { !it.quickEntry }.quickEntry)
        vm.complete()
        assertNotNull(vm.rest.first { it != null }, "ふつうに記録に戻すと、休憩が始まる")

        vm.setQuickEntry(true)
        assertNull(vm.rest.first { it == null }, "あとから入力にすると、動いている休憩は止まる")
    }

    @Test
    fun `日付を今日にすると、あとから入力はふつうの記録に戻る`() = harness.run {
        val workoutId = startPushWorkout(date = LocalDate.of(2026, 11, 13), quickEntry = true)
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        vm.changeDate(LocalDate.of(2026, 11, 12))
        assertTrue(detail(workoutId).workout.quickEntry, "今日でない日付のあいだは、あとから入力のまま")

        vm.changeDate(LocalDate.of(2026, 11, 14)) // 今日
        assertFalse(detail(workoutId).workout.quickEntry)
    }

    @Test
    fun `種目のないまま離れると、空の記録は残らない。種目があれば、途中の記録として残る`() = harness.run {
        val empty = StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)(LocalDate.of(2026, 11, 14), null)
        val vm = viewModel(empty)
        background { vm.state.collect {} }
        vm.state.first { it.loaded }
        val closed = CompletableDeferred<WorkoutEvent>()
        background { closed.complete(vm.events.first()) }

        vm.leave()

        assertEquals(WorkoutEvent.Closed, closed.await())
        assertNull(data.workouts.observeWorkout(empty).first())
        // 自分で消したので、「この記録は見つかりません」は出さない
        delay(200)
        assertFalse(vm.state.value.missing)

        val withExercises = startPushWorkout()
        val second = viewModel(withExercises)
        background { second.state.collect {} }
        second.state.first { it.dock != null }
        val closedSecond = CompletableDeferred<WorkoutEvent>()
        background { closedSecond.complete(second.events.first()) }

        second.leave()

        assertEquals(WorkoutEvent.Closed, closedSecond.await())
        assertNotNull(data.workouts.observeWorkout(withExercises).first())
    }

    @Test
    fun `すべて完了したあとの「もう1セット」は、最後に完了した種目に足される`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        // 先にインクライン（並びでは 2 番め）を終え、そのあとベンチプレス（1 番め）を終える
        vm.shiftExercise(1)
        repeat(2) { vm.complete() }
        repeat(3) { vm.complete() }
        vm.state.first { it.allDone }

        vm.addSet()

        val exercises = vm.state.first { !it.allDone }.exercises
        assertEquals(listOf(4, 2), exercises.map { it.totalCount })
        assertEquals("ベンチプレス　4 セット目", vm.state.value.dock?.title)
    }

    @Test
    fun `日付は、先の日には変えられない`() = harness.run {
        val workoutId = startPushWorkout()
        val vm = viewModel(workoutId)
        background { vm.state.collect {} }
        vm.state.first { it.dock != null }

        vm.changeDate(LocalDate.of(2026, 11, 15)) // 明日
        vm.changeDate(LocalDate.of(2026, 11, 12)) // おととい

        assertEquals(LocalDate.of(2026, 11, 12), vm.state.first { it.date == LocalDate.of(2026, 11, 12) }.date)
        assertEquals(LocalDate.of(2026, 11, 12), detail(workoutId).workout.date)
    }
}
