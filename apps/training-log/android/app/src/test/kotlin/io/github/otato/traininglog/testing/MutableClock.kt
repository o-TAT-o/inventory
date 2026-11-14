package io.github.otato.traininglog.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** テストで、時刻を好きなだけ進められる時計。 */
class MutableClock(
    private var millis: Long,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(millis, zone)

    override fun instant(): Instant = Instant.ofEpochMilli(millis)

    fun advanceSeconds(seconds: Long) {
        millis += seconds * 1000
    }

    companion object {
        fun at(iso: String) = MutableClock(Instant.parse(iso).toEpochMilli())
    }
}
