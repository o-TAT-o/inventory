package io.github.otato.traininglog.feature.picker

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 種目の選択。乗り換え用の初期設定では、まとめて選んで足す（F-DA-05）。 */
class ExercisePickerViewModelTest {
    private val harness = ViewModelTestHarness()
    private val data = TestDatabase.create()

    private val bench = Exercise(id = "bench", name = "ベンチプレス", equipment = Equipment.Barbell)
    private val squat = Exercise(id = "squat", name = "スクワット", equipment = Equipment.Barbell)
    private val row = Exercise(id = "row", name = "ベントオーバーロウ", equipment = Equipment.Barbell)
    private val jog = Exercise(id = "jog", name = "ランニング", recordType = RecordType.DistanceTime)

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    private fun viewModel(mode: PickerMode, targetId: String = "-") = harness.track(
        ExercisePickerViewModel(
            mode = mode,
            targetId = targetId,
            exercises = data.exercises,
            routines = data.routines,
            workouts = data.workouts,
            baselines = data.baselines,
            addExerciseToWorkout = AddExerciseToWorkout(data.workouts, data.exercises),
        ),
    )

    private suspend fun saveAll(vararg exercises: Exercise) = exercises.forEach { data.exercises.save(it) }

    @Test
    fun `乗り換え用の一覧では、距離と時間の種目は出ない`() = harness.run {
        saveAll(bench, jog)
        val state = viewModel(PickerMode.Migration).state.first { it.loaded }
        assertEquals(listOf("bench"), state.rows.map { it.exercise.id })
        assertTrue(state.multiSelect)
    }

    @Test
    fun `ワークアウトに足すときは、距離と時間の種目も出て、まとめて選ぶ形にはならない`() = harness.run {
        saveAll(bench, jog)
        val state = viewModel(PickerMode.Workout).state.first { it.loaded }
        assertEquals(setOf("bench", "jog"), state.rows.map { it.exercise.id }.toSet())
        assertFalse(state.multiSelect)
    }

    @Test
    fun `ワークアウトに足すときは、初期設定にある種目も「追加済み」にならず、選ぶと足して戻る`() = harness.run {
        saveAll(bench)
        data.baselines.addExercises(listOf("bench"))
        val workoutId = StartWorkout(data.workouts, data.routines, Clock.systemDefaultZone())(LocalDate.of(2026, 11, 16), null)
        val viewModel = viewModel(PickerMode.Workout, workoutId)
        assertTrue(viewModel.state.first { it.loaded }.addedIds.isEmpty())

        val done = CompletableDeferred<String?>()
        background { viewModel.done.collect { done.complete(it) } }
        viewModel.pick("bench")
        assertNull(done.await(), "ワークアウトに足したときは、ルーティンの種目の id はない")

        val detail = assertNotNull(data.workouts.observeWorkout(workoutId).first())
        assertEquals(listOf("bench"), detail.exercises.map { it.exercise.id })
    }

    @Test
    fun `ルーティンに足すときは、足した種目の id を渡して戻る（そのまま目標を入れられるように）`() = harness.run {
        saveAll(bench, squat)
        data.routines.saveRoutine(Routine("push", "Push"))
        val viewModel = viewModel(PickerMode.Routine, "push")
        viewModel.state.first { it.loaded }
        val created = CompletableDeferred<String?>()
        background { viewModel.done.collect { created.complete(it) } }

        viewModel.pick("squat")
        val itemId = assertNotNull(created.await())

        val items = assertNotNull(data.routines.observeRoutine("push").first()).items
        assertEquals(listOf(itemId), items.map { it.item.id })
        assertEquals("squat", items.single().exercise.id)
        assertNull(items.single().item.targetSets, "目標は、空欄で足される（そのあと、ルーティンの編集で入れる）")
    }

    @Test
    fun `タップで選択が入れ替わり、追加を押すと、画面の並び順で足して戻る`() = harness.run {
        saveAll(bench, squat, row)
        val viewModel = viewModel(PickerMode.Migration)
        val shown = viewModel.state.first { it.rows.size == 3 }.rows.map { it.exercise.id }
        val done = CompletableDeferred<Unit>()
        background { viewModel.done.collect { done.complete(Unit) } }

        viewModel.pick("squat")
        viewModel.pick("bench")
        viewModel.pick("row")
        viewModel.pick("row")
        assertEquals(setOf("squat", "bench"), viewModel.state.first { it.selectedIds.size == 2 }.selectedIds)

        viewModel.confirmSelection()
        done.await()

        val added = data.baselines.observeBaselines().first { it.size == 2 }.map { it.exercise.id }
        assertEquals(shown.filter { it == "squat" || it == "bench" }, added)
    }

    @Test
    fun `何も選ばずに追加を押しても、何も起きない`() = harness.run {
        saveAll(bench)
        val viewModel = viewModel(PickerMode.Migration)
        viewModel.state.first { it.loaded }
        val done = CompletableDeferred<Unit>()
        background { viewModel.done.collect { done.complete(Unit) } }

        viewModel.confirmSelection()

        assertFalse(done.isCompleted)
        assertTrue(data.baselines.observeBaselines().first().isEmpty())
    }

    @Test
    fun `すでに一覧にある種目は「追加済み」として分かり、二重には足されない`() = harness.run {
        saveAll(bench, squat)
        data.baselines.addExercises(listOf("bench"))
        val viewModel = viewModel(PickerMode.Migration)
        assertEquals(setOf("bench"), viewModel.state.first { it.addedIds.isNotEmpty() }.addedIds)

        val done = CompletableDeferred<Unit>()
        background { viewModel.done.collect { done.complete(Unit) } }
        viewModel.pick("bench")
        viewModel.pick("squat")
        viewModel.confirmSelection()
        done.await()

        assertEquals(listOf("bench", "squat"), data.baselines.observeBaselines().first { it.size == 2 }.map { it.exercise.id })
    }
}
