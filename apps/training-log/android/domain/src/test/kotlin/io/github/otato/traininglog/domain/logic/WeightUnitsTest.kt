package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class WeightUnitsTest {
    @Test
    fun `kg の値は入力したとおりに表示される`() {
        assertEquals("60", WeightUnits.format(60.0))
        assertEquals("62.5", WeightUnits.format(62.5))
        assertEquals("61.25", WeightUnits.format(61.25))
        assertEquals("0.25", WeightUnits.format(0.25))
        assertEquals("0", WeightUnits.format(0.0))
    }

    @Test
    fun `マイナス（補助）も表示できる`() {
        assertEquals("-10", WeightUnits.format(-10.0))
        assertEquals("-2.5", WeightUnits.format(-2.5))
    }

    @Test
    fun `kg と lb を行き来しても入力した値が崩れない`() {
        // 45 lb を入力 → kg で持つ → lb で表示しても 45 のまま
        val kg = WeightUnits.fromDisplay(45.0, WeightUnit.Lb)
        assertEquals("45", WeightUnits.formatKg(kg, WeightUnit.Lb))
        // kg でも見られる（小数第 2 位で丸める）
        assertEquals("20.41", WeightUnits.formatKg(kg, WeightUnit.Kg))
    }

    @Test
    fun `0_25 kg 刻みは誤差なく足し引きできる`() {
        var kg = 60.0
        repeat(10) { kg += 0.25 }
        assertEquals(62.5, kg)
        assertEquals("62.5", WeightUnits.formatKg(kg, WeightUnit.Kg))
    }

    @Test
    fun `刻みの選択肢は単位ごとに違う`() {
        assertEquals(listOf(0.25, 0.5, 1.0, 1.25, 2.0, 2.5, 5.0), WeightUnits.stepOptions(WeightUnit.Kg))
        assertEquals(listOf(0.5, 1.0, 2.5, 5.0, 10.0), WeightUnits.stepOptions(WeightUnit.Lb))
    }
}
