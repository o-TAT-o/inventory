package io.github.otato.traininglog.feature.routine

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** ルーティンの編集。種目の目標は、変えるたびにその場で保存する（保存ボタンはない）。 */
class RoutineEditViewModelTest {
    private val harness = ViewModelTestHarness()
    private val data = TestDatabase.create()

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    private fun viewModel() = harness.track(RoutineEditViewModel("push", data.routines, data.settings))

    private suspend fun prepare() {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        data.routines.saveRoutine(Routine("push", "Push"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench"))
    }

    @Test
    fun `＋ を長押しして、短い間に何度も保存しても、最後に打った値が残る`() = harness.run {
        prepare()
        val viewModel = viewModel()

        // 長押しの繰り返しのように、続けて 100 回保存する。順番が入れ替わると、古い値が残ってしまう
        repeat(100) { n ->
            viewModel.saveItem(RoutineItem("i1", "push", "bench", targetWeightKg = 20.0 + n * 2.5, targetSets = n + 1))
        }

        val last = 20.0 + 99 * 2.5
        val item = assertNotNull(
            data.routines.observeRoutine("push").first { it?.items?.singleOrNull()?.item?.targetWeightKg == last },
        ).items.single().item
        assertEquals(last, item.targetWeightKg)
        assertEquals(100, item.targetSets)
    }

    @Test
    fun `種目を外すと、ルーティンから消える`() = harness.run {
        prepare()
        val viewModel = viewModel()
        viewModel.removeItem("i1")
        assertEquals(emptyList(), assertNotNull(data.routines.observeRoutine("push").first { it?.items?.isEmpty() == true }).items)
    }

    @Test
    fun `外した種目は、元に戻すと、目標もそのまま戻る`() = harness.run {
        prepare()
        data.routines.saveItem(RoutineItem("i1", "push", "bench", targetWeightKg = 60.0, targetSets = 3))
        val viewModel = viewModel()

        viewModel.removeItem("i1")
        data.routines.observeRoutine("push").first { it?.items?.isEmpty() == true }
        viewModel.undo()

        val item = assertNotNull(
            data.routines.observeRoutine("push").first { it?.items?.size == 1 },
        ).items.single().item
        assertEquals(60.0, item.targetWeightKg)
        assertEquals(3, item.targetSets)
    }

    @Test
    fun `種目を並べ替えると並びが変わり、元に戻すと前の並びに戻る`() = harness.run {
        prepare()
        data.exercises.save(Exercise(id = "squat", name = "スクワット"))
        data.routines.saveItem(RoutineItem("i1", "push", "bench", sortOrder = 0))
        data.routines.saveItem(RoutineItem("i2", "push", "squat", sortOrder = 1))
        val viewModel = viewModel()

        viewModel.reorderItems(listOf("i2", "i1"))
        val reordered = data.routines.observeRoutine("push").first { detail -> detail?.items?.map { it.item.id } == listOf("i2", "i1") }
        assertEquals(listOf("i2", "i1"), assertNotNull(reordered).items.map { it.item.id })

        viewModel.undo()
        val restored = data.routines.observeRoutine("push").first { detail -> detail?.items?.map { it.item.id } == listOf("i1", "i2") }
        assertEquals(listOf("i1", "i2"), assertNotNull(restored).items.map { it.item.id })
    }
}
