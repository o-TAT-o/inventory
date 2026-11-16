package io.github.otato.traininglog.ui.common

import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.WeightUnit
import java.time.DayOfWeek
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

/** 最後に書き出してからの日数。まだ書き出していなければ null。 */
fun daysSinceExport(lastExportAtMillis: Long?, clock: Clock): Long? {
    if (lastExportAtMillis == null) return null
    val zone = clock.zone
    val last = Instant.ofEpochMilli(lastExportAtMillis).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(last, LocalDate.now(clock)).coerceAtLeast(0)
}

/** 「最後の書き出し　10月13日（32日前）」／「まだ書き出していません」（F-DA-09） */
fun lastExportHeadline(lastExportAtMillis: Long?, clock: Clock): String {
    val days = daysSinceExport(lastExportAtMillis, clock) ?: return "まだ書き出していません"
    val date = Instant.ofEpochMilli(lastExportAtMillis!!).atZone(clock.zone).toLocalDate()
    val ago = if (days == 0L) "今日" else "${days}日前"
    return "最後の書き出し　${date.monthValue}月${date.dayOfMonth}日（$ago）"
}

/** 設定の「データ管理」の行の補足。「最後の書き出しから 32 日」 */
fun lastExportShort(lastExportAtMillis: Long?, clock: Clock): String {
    val days = daysSinceExport(lastExportAtMillis, clock) ?: return "まだ書き出していません"
    return if (days == 0L) "今日、書き出しました" else "最後の書き出しから $days 日"
}
