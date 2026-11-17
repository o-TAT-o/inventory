package io.github.otato.traininglog.feature.workout

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.math.max

/** 動いている休憩。 */
data class RunningRest(
    /** どのワークアウトの休憩か */
    val workoutId: String,
    /** 終わる時刻（エポックミリ秒） */
    val endAtMillis: Long,
    val totalSec: Int,
    /** 終わりを、もう知らせたか。知らせたあとも、消すまでは「休憩おわり +0:12」と出し続ける */
    val notified: Boolean = false,
)

/** 休憩の状態を、アプリが落ちても残すための保存先。 */
interface RestStorage {
    fun load(): RunningRest?
    fun save(rest: RunningRest?)
}

/** 休憩の終わりの時刻に、アプリが後ろにあっても（落ちていても）知らせるための目覚まし。 */
interface RestAlarm {
    fun schedule(endAtMillis: Long)
    fun cancel()
}

/** 保存しない保存先（テストと、初期値）。 */
class InMemoryRestStorage(private var rest: RunningRest? = null) : RestStorage {
    override fun load(): RunningRest? = rest
    override fun save(rest: RunningRest?) {
        this.rest = rest
    }
}

/** 何もしない目覚まし（テストと、初期値）。 */
object NoRestAlarm : RestAlarm {
    override fun schedule(endAtMillis: Long) = Unit
    override fun cancel() = Unit
}

/**
 * 休憩タイマー（F-TM-01〜04）。アプリ全体で 1 つ。
 *
 * 記録画面の ViewModel ではなく、ここで持つ。記録画面は、戻る操作で閉じると ViewModel ごと消えるので、
 * そこに持つと「休憩中にホームへ戻ると、タイマーが消える」「通知だけがあとから鳴る」ことになる。
 * ここで持てば、どの画面を見ていても・アプリが落ちても、休憩は続く。
 *
 * 終わりの知らせ方:
 * - アプリが前に出ているとき: 音とバイブ（[alert]）。どの画面を見ていても鳴る
 * - アプリが後ろにあるとき・落ちているとき: 目覚まし（[alarm]）が、通知を出す
 */
class RestTimer(
    scope: CoroutineScope,
    private val clock: Clock,
    private val storage: RestStorage = InMemoryRestStorage(),
    private val alarm: RestAlarm = NoRestAlarm,
    private val isForeground: () -> Boolean = { true },
    private val alert: suspend () -> Unit = {},
) {
    private val _state = MutableStateFlow(restore(storage.load()))

    /** 動いている休憩。なければ null。 */
    val state: StateFlow<RunningRest?> = _state.asStateFlow()

    init {
        // アプリが落ちている間も、目覚ましは残っているはずだが、端末の再起動などで消えることがあるので、仕掛け直す
        val restored = _state.value
        if (restored != null && !restored.notified) alarm.schedule(restored.endAtMillis)
        if (restored == null) storage.save(null)

        scope.launch {
            _state.collectLatest { rest ->
                if (rest == null || rest.notified) return@collectLatest
                // 時計を見に行く形で待つ（端末の時刻が変わっても、ずれない）。延ばしたり・やめたりしたら、待ち直す
                while (clock.millis() < rest.endAtMillis) delay(POLL_MILLIS)
                if (isForeground()) {
                    // 画面の中で知らせるので、通知は出さない
                    alarm.cancel()
                    alert()
                }
                set(rest.copy(notified = true))
            }
        }
    }

    /** 休憩を始める。動いている休憩があれば、置き換える。 */
    fun start(workoutId: String, seconds: Int) {
        val rest = RunningRest(workoutId, clock.millis() + seconds * 1000L, seconds)
        set(rest)
        alarm.schedule(rest.endAtMillis)
    }

    /** 休憩の延長・短縮（「±10 秒」「＋30 秒」）。終わったあとに延ばすと、そこからもう一度数える。 */
    fun adjust(workoutId: String, deltaSeconds: Int) {
        val rest = _state.value?.takeIf { it.workoutId == workoutId } ?: return
        val now = clock.millis()
        val next = if (now >= rest.endAtMillis) {
            if (deltaSeconds <= 0) return
            RunningRest(workoutId, now + deltaSeconds * 1000L, deltaSeconds)
        } else {
            rest.copy(
                endAtMillis = max(now, rest.endAtMillis + deltaSeconds * 1000L),
                totalSec = max(1, rest.totalSec + deltaSeconds),
                notified = false,
            )
        }
        set(next)
        alarm.schedule(next.endAtMillis)
    }

    /** 休憩をやめる（とばす・表示を閉じる・ワークアウトを終える）。ほかのワークアウトの休憩には、触らない。 */
    fun clear(workoutId: String) {
        if (_state.value?.workoutId != workoutId) return
        set(null)
        alarm.cancel()
    }

    private fun set(rest: RunningRest?) {
        _state.value = rest
        storage.save(rest)
    }

    /** 保存してあった休憩を、起動したときの状態に直す。 */
    private fun restore(saved: RunningRest?): RunningRest? {
        saved ?: return null
        val now = clock.millis()
        return when {
            // ずっと前に終わった休憩は、もう出さない
            now - saved.endAtMillis > STALE_MILLIS -> null
            // アプリが止まっている間に終わっていたら、通知が知らせているので、開いたときには鳴らさない
            now >= saved.endAtMillis -> saved.copy(notified = true)
            else -> saved
        }
    }

    private companion object {
        const val POLL_MILLIS = 250L

        /** 終わってからこの時間が過ぎた休憩は、アプリを開いても出さない */
        const val STALE_MILLIS = 10 * 60 * 1000L
    }
}
