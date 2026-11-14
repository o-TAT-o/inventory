package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 要件定義 6.2 のボリュームの表を、そのまま確かめる。 */
class VolumeTest {
    private fun volume(weightMode: WeightMode, repMode: RepMode, type: RecordType = RecordType.WeightReps, weight: Double = 20.0, reps: Int = 10) =
        Volume.ofSet(weight, reps, type, weightMode, repMode)

    @Test
    fun `合計 × 通常 は 重量 × 回数`() {
        assertEquals(200.0, volume(WeightMode.Total, RepMode.Normal))
    }

    @Test
    fun `片側 × 通常 は 重量 × 2 × 回数`() {
        assertEquals(400.0, volume(WeightMode.PerSide, RepMode.Normal))
    }

    @Test
    fun `片側 × 片側 は 重量 × 回数 × 2`() {
        assertEquals(400.0, volume(WeightMode.PerSide, RepMode.PerSide))
    }

    @Test
    fun `片側 × 左右合計 は 重量 × 回数`() {
        assertEquals(200.0, volume(WeightMode.PerSide, RepMode.BothSidesTotal))
    }

    @Test
    fun `合計 × 片側 は 重量 × 回数 × 2`() {
        assertEquals(400.0, volume(WeightMode.Total, RepMode.PerSide))
    }

    @Test
    fun `合計 × 左右合計 は 重量 × 回数`() {
        assertEquals(200.0, volume(WeightMode.Total, RepMode.BothSidesTotal))
    }

    @Test
    fun `自重の種目は加重 × 回数で、体重は含めない`() {
        assertEquals(100.0, volume(WeightMode.Total, RepMode.Normal, RecordType.Bodyweight, weight = 10.0))
        assertEquals(0.0, volume(WeightMode.Total, RepMode.Normal, RecordType.Bodyweight, weight = 0.0))
    }

    @Test
    fun `補助（マイナスの加重）はボリュームを 0 として数える`() {
        assertEquals(0.0, volume(WeightMode.Total, RepMode.Normal, RecordType.Bodyweight, weight = -20.0))
    }

    @Test
    fun `時間と距離の種目はボリュームを持たない`() {
        assertEquals(0.0, volume(WeightMode.Total, RepMode.Normal, RecordType.Duration))
        assertEquals(0.0, volume(WeightMode.Total, RepMode.Normal, RecordType.DistanceTime))
    }
}
