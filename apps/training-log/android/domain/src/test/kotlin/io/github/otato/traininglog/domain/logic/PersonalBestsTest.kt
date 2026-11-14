package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.SetRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonalBestsTest {
    private fun set(id: String, order: Int, weight: Double, reps: Int, completed: Boolean = true) =
        SetRecord(id = id, workoutExerciseId = "we", sortOrder = order, weightKg = weight, reps = reps, completed = completed)

    @Test
    fun `重量の種目は最大重量で決める`() {
        val sets = listOf(set("a", 0, 60.0, 10), set("b", 1, 65.0, 5), set("c", 2, 62.5, 8))
        assertEquals(65.0, PersonalBests.bestOf(sets, RecordType.WeightReps, useAddedWeight = false))
    }

    @Test
    fun `加重のない自重の種目は最大回数で決める`() {
        val sets = listOf(set("a", 0, 0.0, 12), set("b", 1, 0.0, 15))
        assertEquals(15.0, PersonalBests.bestOf(sets, RecordType.Bodyweight, useAddedWeight = false))
    }

    @Test
    fun `加重のある自重の種目は最大加重で決める（マイナスの補助も大きいほど良い）`() {
        val sets = listOf(set("a", 0, -20.0, 8), set("b", 1, -10.0, 8))
        assertEquals(-10.0, PersonalBests.bestOf(sets, RecordType.Bodyweight, useAddedWeight = true))
    }

    @Test
    fun `未完了のセットは数えない`() {
        val sets = listOf(set("a", 0, 60.0, 10), set("b", 1, 100.0, 10, completed = false))
        assertEquals(60.0, PersonalBests.bestOf(sets, RecordType.WeightReps, useAddedWeight = false))
    }

    @Test
    fun `以前の最高値を超えたセットに印を付ける`() {
        val sets = listOf(set("a", 0, 60.0, 10), set("b", 1, 62.5, 9))
        val ids = PersonalBests.newBestSetIds(sets, RecordType.WeightReps, false, previousBest = 60.0)
        assertEquals(setOf("b"), ids)
    }

    @Test
    fun `同じ値は更新に含めない`() {
        val sets = listOf(set("a", 0, 60.0, 10))
        assertEquals(emptySet(), PersonalBests.newBestSetIds(sets, RecordType.WeightReps, false, previousBest = 60.0))
    }

    @Test
    fun `同じワークアウトで何度か更新しても、最高値のセットだけに印を付ける`() {
        val sets = listOf(set("a", 0, 62.5, 10), set("b", 1, 65.0, 8), set("c", 2, 65.0, 6))
        val ids = PersonalBests.newBestSetIds(sets, RecordType.WeightReps, false, previousBest = 60.0)
        assertEquals(setOf("b"), ids)
    }

    @Test
    fun `以前の記録がないときは初回の記録に印を付けない`() {
        val sets = listOf(set("a", 0, 60.0, 10), set("b", 1, 65.0, 8))
        assertEquals(emptySet(), PersonalBests.newBestSetIds(sets, RecordType.WeightReps, false, previousBest = null))
    }

    @Test
    fun `完了したセットがなければ最高値はない`() {
        assertNull(PersonalBests.bestOf(listOf(set("a", 0, 60.0, 10, completed = false)), RecordType.WeightReps, false))
    }
}
