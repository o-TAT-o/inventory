package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.usecase.AddSet
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.testing.MutableClock
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private suspend fun startPushWorkout(): String {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス", equipment = Equipment.Barbell))
        data.exercises.save(Exercise(id = "incline", name = "インクラインダンベルプレス", equipment = Equipment.Dumbbell, weightMode = WeightMode.PerSide))
        data.routines.saveRoutine(Routine("push", "Push", memo = "ベンチが空いていなければダンベルに変更"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", sortOrder = 0, targetSets = 3, targetWeightKg = 60.0, targetReps = 10, restAfterSeconds = 200))
        data.routines.saveItem(RoutineItem("i2", "push", "incline", sortOrder = 1, targetSets = 2, targetWeightKg = 20.0, targetReps = 10))
        return StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)(LocalDate.of(2026, 11, 14), "push")
    }

    private fun viewModel(workoutId: String) = harness.track(
        WorkoutViewModel(
            workoutId = workoutId,
            workouts = data.workouts,
            routines = data.routines,
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
        assertIs<WorkoutEvent.SetDeleted>(events.single())

        vm.undoDelete()
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
}
