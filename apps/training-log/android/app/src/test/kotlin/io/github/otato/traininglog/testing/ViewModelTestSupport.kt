package io.github.otato.traininglog.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

/**
 * ViewModel のテストの土台。
 *
 * ViewModel は「1 秒ごとのタイマー」のような、終わらない処理を持つ。テストの仮想時間（runTest）で動かすと、
 * この処理が仮想の時間を際限なく進めてしまい、テストが終わらなくなる。そこで、
 * - 本物の時間で動かし（runBlocking）、
 * - 終わったら、ViewModel の処理をすべて止める
 * ようにしている。DB は、メモリ上の SQLite を、呼び出したスレッドでそのまま動かす。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTestHarness {
    private val viewModels = mutableListOf<ViewModel>()
    private val jobs = mutableListOf<Job>()

    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    fun tearDown() {
        jobs.forEach { it.cancel() }
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    /** 作った ViewModel を、あとで止めるために覚えておく。 */
    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    /**
     * テストの本体。止まらないテストが残らないよう、制限時間を付ける。
     * 終わったら、バックグラウンドで集めていた処理を止める（止めないと、runBlocking がいつまでも終わらない）。
     */
    fun run(block: suspend Scope.() -> Unit) = runBlocking {
        try {
            withTimeout(TIMEOUT_MILLIS) { Scope(this@runBlocking).block() }
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    inner class Scope(private val scope: CoroutineScope) {
        /** 画面が見ている状態の代わりに、バックグラウンドで集め続ける（購読している間だけ動く Flow のため）。 */
        fun background(block: suspend CoroutineScope.() -> Unit) {
            // すぐに購読を始め、受け取った値もその場で処理する（出来事を取りこぼしたり、反映が遅れたりしないため）
            jobs += scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED, block = block)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
    }
}
