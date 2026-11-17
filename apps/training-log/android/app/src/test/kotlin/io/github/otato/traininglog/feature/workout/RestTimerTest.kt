package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.testing.MutableClock
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 休憩タイマー（アプリ全体で 1 つ）。記録画面を離れても・アプリが落ちても続くこと、
 * 終わりを、前に出ているときは音とバイブで、後ろにあるときは通知（目覚まし）で知らせること。
 */
class RestTimerTest {
    private val harness = ViewModelTestHarness()
    private val clock = MutableClock.at("2026-11-17T10:00:00Z")
    private val storage = InMemoryRestStorage()
    private val alerts = AtomicInteger()
    private var foreground = true

    /** 仕掛けた目覚まし（終わる時刻）。取り消したら null */
    private var alarmAt: Long? = null
    private val alarm = object : RestAlarm {
        override fun schedule(endAtMillis: Long) {
            alarmAt = endAtMillis
        }

        override fun cancel() {
            alarmAt = null
        }
    }

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    /** いま動いているアプリ（の、休憩タイマーが動いている場所） */
    private var process: CoroutineScope? = null

    /** アプリを起動したところ（保存してあった休憩があれば、読み込む）。前に動いていたアプリは、落ちたことにする。 */
    private fun launch(): RestTimer {
        process?.cancel()
        val scope = harness.newScope().also { process = it }
        return RestTimer(scope, clock, storage, alarm, isForeground = { foreground }, alert = { alerts.incrementAndGet() })
    }

    @Test
    fun `始めると、終わる時刻に目覚ましを仕掛け、状態を保存する`() = harness.run {
        val timer = launch()
        assertNull(timer.state.value)

        timer.start("w1", 90)

        val rest = assertNotNull(timer.state.value)
        assertEquals(clock.millis() + 90_000, rest.endAtMillis)
        assertEquals(rest.endAtMillis, alarmAt)
        assertEquals(rest, storage.load())
    }

    @Test
    fun `前に出ているときに終わると、音とバイブで一度だけ知らせ、通知は出さない`() = harness.run {
        val timer = launch()
        timer.start("w1", 90)

        clock.advanceSeconds(91)
        val ended = assertNotNull(timer.state.first { it?.notified == true })

        assertEquals("w1", ended.workoutId)
        delay(600)
        assertEquals(1, alerts.get())
        assertNull(alarmAt, "画面の中で知らせたので、通知のための目覚ましは取り消す")
    }

    @Test
    fun `後ろにあるときに終わると、音は鳴らさず、目覚まし（通知）に任せる`() = harness.run {
        foreground = false
        val timer = launch()
        timer.start("w1", 90)

        clock.advanceSeconds(91)
        timer.state.first { it?.notified == true }

        assertEquals(0, alerts.get())
        assertNotNull(alarmAt, "通知は、目覚ましが出す")
    }

    @Test
    fun `延ばすと、目覚ましも仕掛け直す。終わったあとに延ばすと、そこからもう一度数えて、また知らせる`() = harness.run {
        val timer = launch()
        timer.start("w1", 90)
        val firstEnd = assertNotNull(timer.state.value).endAtMillis

        timer.adjust("w1", 10)
        assertEquals(firstEnd + 10_000, timer.state.value?.endAtMillis)
        assertEquals(firstEnd + 10_000, alarmAt)
        assertEquals(100, timer.state.value?.totalSec)

        clock.advanceSeconds(101)
        timer.state.first { it?.notified == true }
        assertEquals(1, alerts.get())

        timer.adjust("w1", 30)
        val again = assertNotNull(timer.state.value)
        assertEquals(clock.millis() + 30_000, again.endAtMillis)
        assertEquals(false, again.notified)
        clock.advanceSeconds(31)
        timer.state.first { it?.notified == true }
        assertEquals(2, alerts.get())
    }

    @Test
    fun `やめると、状態も目覚ましも消える。ほかのワークアウトの休憩には触らない`() = harness.run {
        val timer = launch()
        timer.start("w1", 90)

        timer.clear("another")
        timer.adjust("another", 30)
        assertEquals("w1", timer.state.value?.workoutId)
        assertEquals(90, timer.state.value?.totalSec)

        timer.clear("w1")
        assertNull(timer.state.value)
        assertNull(alarmAt)
        assertNull(storage.load())
    }

    @Test
    fun `アプリが落ちても、起動し直すと、休憩は続いている`() = harness.run {
        launch().start("w1", 90)
        alarmAt = null // 端末の再起動などで、目覚ましが消えたとする

        clock.advanceSeconds(30)
        val relaunched = launch()

        val rest = assertNotNull(relaunched.state.value)
        assertEquals(clock.millis() + 60_000, rest.endAtMillis)
        assertEquals(rest.endAtMillis, alarmAt, "目覚ましを仕掛け直す")
    }

    @Test
    fun `アプリが止まっている間に終わっていたら、開いたときには鳴らさない。ずっと前の休憩は出さない`() = harness.run {
        launch().start("w1", 90)

        // 2 分後に開く: 「休憩おわり」とは出すが、音は鳴らさない（通知が知らせている）
        clock.advanceSeconds(120)
        val soon = launch()
        assertTrue(assertNotNull(soon.state.value).notified)
        delay(600)
        assertEquals(0, alerts.get())

        // 30 分後に開く: もう出さない
        clock.advanceSeconds(30 * 60)
        val later = launch()
        assertNull(later.state.value)
        assertNull(storage.load())
    }
}
