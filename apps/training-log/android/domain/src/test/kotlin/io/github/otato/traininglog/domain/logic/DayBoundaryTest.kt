package io.github.otato.traininglog.domain.logic

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** 1日の区切り（F-SE-08、F-WO-23）。 */
class DayBoundaryTest {
    private val sunday = LocalDate.of(2026, 11, 15)
    private val saturday = LocalDate.of(2026, 11, 14)

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 15, hour, minute)

    @Test
    fun `区切りが 4 時のとき 日曜の 1時30分は土曜`() {
        assertEquals(saturday, DayBoundary.logicalDate(at(1, 30), boundaryHour = 4))
    }

    @Test
    fun `区切りの時刻ちょうどから新しい日になる`() {
        assertEquals(saturday, DayBoundary.logicalDate(at(3, 59), boundaryHour = 4))
        assertEquals(sunday, DayBoundary.logicalDate(at(4, 0), boundaryHour = 4))
    }

    @Test
    fun `区切りを 0 にすると日付どおり`() {
        assertEquals(sunday, DayBoundary.logicalDate(at(0, 0), boundaryHour = 0))
        assertEquals(sunday, DayBoundary.logicalDate(at(1, 30), boundaryHour = 0))
    }

    @Test
    fun `昼間は区切りに関係なく同じ日`() {
        assertEquals(sunday, DayBoundary.logicalDate(at(15), boundaryHour = 6))
    }

    @Test
    fun `範囲の外の値は 0 から 6 に収める`() {
        assertEquals(0, DayBoundary.normalize(-3))
        assertEquals(6, DayBoundary.normalize(12))
        assertEquals(sunday, DayBoundary.logicalDate(at(7), boundaryHour = 12))
    }
}
