package io.github.otato.traininglog.ui.common

import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.WeightUnit
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToLong

private val dowShort = arrayOf("月", "火", "水", "木", "金", "土", "日")

fun DayOfWeek.jaShort(): String = dowShort[value - 1]

/** 11/7 */
fun formatMonthDay(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}"

/** 11/12（木） */
fun formatDateWithDow(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}（${date.dayOfWeek.jaShort()}）"

/** 11月13日（金） */
fun formatDateLong(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日（${date.dayOfWeek.jaShort()}）"

/** 2026年11月 */
fun formatYearMonth(date: LocalDate): String = "${date.year}年${date.monthValue}月"

/** 1:30 / 24:10。休憩タイマーと、経過時間に使う。 */
fun formatMmSs(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** 1,920 kg。ボリュームは整数で出す。 */
fun formatVolume(kg: Double, unit: WeightUnit): String {
    val value = WeightUnits.toDisplay(kg, unit).roundToLong()
    return "%,d %s".format(value, WeightUnits.label(unit))
}
