package io.github.otato.traininglog.ui.common

import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** セットの要約の書き方（数値の横に、必ず数え方の印を付ける。P-4）。 */
class RecordFormatTest {
    private val bench = Exercise(id = "bench", name = "ベンチプレス", equipment = Equipment.Barbell)
    private val dumbbell = Exercise(id = "db", name = "ダンベルプレス", equipment = Equipment.Dumbbell, weightMode = WeightMode.PerSide)

    private fun sets(vararg pairs: Pair<Double, Int>) = pairs.mapIndexed { i, (w, r) ->
        SetRecord(id = "s$i", workoutExerciseId = "we", sortOrder = i, weightKg = w, reps = r, completed = true)
    }

    @Test
    fun `重量も回数も同じなら 「× 10 を 3 セット」とまとめる`() {
        val line = RecordFormat.setsLine(sets(60.0 to 10, 60.0 to 10, 60.0 to 10), bench, WeightMode.Total, RepMode.Normal, WeightUnit.Kg)!!
        assertEquals("60 kg", line.weightText)
        assertEquals("合計", line.tag)
        assertEquals("× 10 を 3 セット", line.repsText)
    }

    @Test
    fun `回数が違うときは 「× 10 · 9 · 8」と並べる`() {
        val line = RecordFormat.setsLine(sets(60.0 to 10, 60.0 to 9, 60.0 to 8), bench, WeightMode.Total, RepMode.Normal, WeightUnit.Kg)!!
        assertEquals("× 10 · 9 · 8", line.repsText)
    }

    @Test
    fun `ダンベルの片側は 「片手」の印`() {
        val line = RecordFormat.setsLine(sets(20.0 to 10), dumbbell, WeightMode.PerSide, RepMode.Normal, WeightUnit.Kg)!!
        assertEquals("片手", line.tag)
    }

    @Test
    fun `回数の数え方が通常でなければ、回数の横に印が付く`() {
        val line = RecordFormat.setsLine(sets(20.0 to 10), dumbbell, WeightMode.PerSide, RepMode.BothSidesTotal, WeightUnit.Kg)!!
        assertEquals("× 10（左右計）", line.repsText)
    }

    @Test
    fun `重量は設定の単位で表示される`() {
        val line = RecordFormat.setsLine(sets(45.359237 to 10), bench, WeightMode.Total, RepMode.Normal, WeightUnit.Lb)!!
        assertEquals("100 lb", line.weightText)
    }

    @Test
    fun `自重の種目は 「自重」と書き、加重と補助は印が付く`() {
        val pullUp = Exercise(id = "pu", name = "懸垂", recordType = RecordType.Bodyweight, useAddedWeight = true)
        assertEquals("自重", RecordFormat.setsLine(sets(0.0 to 8), pullUp, WeightMode.Total, RepMode.Normal, WeightUnit.Kg)!!.weightText)
        val added = RecordFormat.setsLine(sets(10.0 to 8), pullUp, WeightMode.Total, RepMode.Normal, WeightUnit.Kg)!!
        assertEquals("+10 kg", added.weightText)
        assertEquals("加重", added.tag)
        val assisted = RecordFormat.setsLine(sets(-20.0 to 8), pullUp, WeightMode.Total, RepMode.Normal, WeightUnit.Kg)!!
        assertEquals("補助", assisted.tag)
    }

    @Test
    fun `完了したセットがなければ要約はない`() {
        val open = listOf(SetRecord(id = "s", workoutExerciseId = "we", weightKg = 60.0, reps = 10, completed = false))
        assertNull(RecordFormat.setsLine(open, bench, WeightMode.Total, RepMode.Normal, WeightUnit.Kg))
    }

    @Test
    fun `日付と時間の書式`() {
        assertEquals("1:30", formatMmSs(90))
        assertEquals("0:05", formatMmSs(5))
        assertEquals("24:10", formatMmSs(24 * 60 + 10))
        assertEquals("0:00", formatMmSs(-3))
        assertEquals("11/12（木）", formatDateWithDow(java.time.LocalDate.of(2026, 11, 12)))
        assertEquals("1,920 kg", formatVolume(1920.0, WeightUnit.Kg))
    }
}
