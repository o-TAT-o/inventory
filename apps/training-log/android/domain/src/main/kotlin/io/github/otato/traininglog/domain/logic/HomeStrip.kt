package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.HomeStripMode
import io.github.otato.traininglog.domain.model.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** ホームの日付の帯に並べる 7 日（F-SE-06）。 */
object HomeStrip {
    fun days(today: LocalDate, mode: HomeStripMode, weekStart: WeekStart): List<LocalDate> = when (mode) {
        HomeStripMode.Week -> {
            val first = today.with(TemporalAdjusters.previousOrSame(weekStart.firstDay()))
            (0L..6L).map { first.plusDays(it) }
        }
        HomeStripMode.CenteredToday -> (-3L..3L).map { today.plusDays(it) }
        HomeStripMode.Last7Days -> (-6L..0L).map { today.plusDays(it) }
    }

    private fun WeekStart.firstDay(): DayOfWeek = when (this) {
        WeekStart.Sunday -> DayOfWeek.SUNDAY
        WeekStart.Monday -> DayOfWeek.MONDAY
    }
}
