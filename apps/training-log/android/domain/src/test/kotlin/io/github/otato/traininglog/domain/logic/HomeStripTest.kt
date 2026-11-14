package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.HomeStripMode
import io.github.otato.traininglog.domain.model.WeekStart
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** ホームの日付の帯（F-SE-06）。2026-11-14 は土曜。 */
class HomeStripTest {
    private val saturday = LocalDate.of(2026, 11, 14)

    @Test
    fun `その週（月曜始まり）は 月曜から日曜の 7 日`() {
        val days = HomeStrip.days(saturday, HomeStripMode.Week, WeekStart.Monday)
        assertEquals(LocalDate.of(2026, 11, 9), days.first())
        assertEquals(LocalDate.of(2026, 11, 15), days.last())
        assertEquals(7, days.size)
    }

    @Test
    fun `その週（日曜始まり）は 日曜から土曜の 7 日`() {
        val days = HomeStrip.days(saturday, HomeStripMode.Week, WeekStart.Sunday)
        assertEquals(LocalDate.of(2026, 11, 8), days.first())
        assertEquals(LocalDate.of(2026, 11, 14), days.last())
    }

    @Test
    fun `今日を真ん中にすると 前後 3 日ずつ`() {
        val days = HomeStrip.days(saturday, HomeStripMode.CenteredToday, WeekStart.Monday)
        assertEquals(LocalDate.of(2026, 11, 11), days.first())
        assertEquals(saturday, days[3])
        assertEquals(LocalDate.of(2026, 11, 17), days.last())
    }

    @Test
    fun `直近 7 日は 今日が右端`() {
        val days = HomeStrip.days(saturday, HomeStripMode.Last7Days, WeekStart.Monday)
        assertEquals(LocalDate.of(2026, 11, 8), days.first())
        assertEquals(saturday, days.last())
    }

    @Test
    fun `週の始まりの日が今日なら その日から始まる`() {
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals(monday, HomeStrip.days(monday, HomeStripMode.Week, WeekStart.Monday).first())
    }
}
