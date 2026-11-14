package io.github.otato.traininglog.domain.logic

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1日の区切り（F-SE-08、F-WO-23）。0 時から区切りの時刻までは、前の日として扱う。
 * 例: 区切りが 4:00 のとき、日曜の 1:30 は土曜。区切りを 0 にすると、日付どおりになる。
 */
object DayBoundary {
    const val MAX_HOUR = 6

    fun normalize(boundaryHour: Int): Int = boundaryHour.coerceIn(0, MAX_HOUR)

    fun logicalDate(now: LocalDateTime, boundaryHour: Int): LocalDate =
        if (now.hour < normalize(boundaryHour)) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /** 「今日」。ホームの日付の帯・履歴の今日の印・ワークアウトの日付の初期値に使う。 */
    fun today(clock: Clock, boundaryHour: Int): LocalDate =
        logicalDate(LocalDateTime.now(clock), boundaryHour)
}
