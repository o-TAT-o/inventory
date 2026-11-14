package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 重量の単位の変換と表示。重量は内部ではすべて kg で持ち、表示のときに変換する（6.2）。
 * 0.25 kg 刻みの値は 2 進数で誤差なく表せるので、kg のまま入力した値はそのまま表示される。
 */
object WeightUnits {
    const val KG_PER_LB = 0.45359237

    private val kgSteps = listOf(0.25, 0.5, 1.0, 1.25, 2.0, 2.5, 5.0)
    private val lbSteps = listOf(0.5, 1.0, 2.5, 5.0, 10.0)

    fun toDisplay(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.Kg) kg else kg / KG_PER_LB

    fun fromDisplay(value: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.Kg) value else value * KG_PER_LB

    /** ± の刻みの選択肢（F-SE-02）。 */
    fun stepOptions(unit: WeightUnit): List<Double> = if (unit == WeightUnit.Kg) kgSteps else lbSteps

    /** 表示用の文字列。小数第 2 位で丸め、末尾の 0 は省く（60 / 62.5 / 61.25）。 */
    fun format(value: Double): String {
        val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        // 0 のとき stripTrailingZeros が "0E+2" のような表記にならないように toPlainString を使う
        return if (rounded.signum() == 0) "0" else rounded.toPlainString()
    }

    /** kg で持っている重量を、単位に合わせて表示用の文字列にする。 */
    fun formatKg(kg: Double, unit: WeightUnit): String = format(toDisplay(kg, unit))

    fun label(unit: WeightUnit): String = if (unit == WeightUnit.Kg) "kg" else "lb"
}
