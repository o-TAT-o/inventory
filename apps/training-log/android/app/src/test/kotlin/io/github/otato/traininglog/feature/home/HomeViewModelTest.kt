package io.github.otato.traininglog.feature.home

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.HomeStripMode
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.testing.MutableClock
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ホームの状態（次のルーティンの中身・日付の帯・再開）を、本物のデータ層で組み立てて確かめる。 */
class HomeViewModelTest {
    private val harness = ViewModelTestHarness()
    private val data = TestDatabase.create()
    private val ids = TestDatabase.sequentialIds("h")

    // 2026-11-14（土）の 10:00（UTC）。1日の区切り（4 時）より後なので、今日は 11/14
    private val clock = MutableClock.at("2026-11-14T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 14)
    private val start = StartWorkout(data.workouts, data.routines, data.exercises, clock, ids)

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    private fun viewModel(clock: java.time.Clock = this.clock) =
        harness.track(HomeViewModel(data.workouts, data.routines, data.settings, start, clock))

    private suspend fun routine(id: String, name: String, order: Int, vararg exerciseNames: String) {
        data.routines.saveRoutine(Routine(id, name, sortOrder = order))
        exerciseNames.forEachIndexed { index, exerciseName ->
            val exerciseId = "$id-$index"
            data.exercises.save(Exercise(id = exerciseId, name = exerciseName))
            data.routines.saveItem(RoutineItem("$id-item-$index", id, exerciseId, sortOrder = index, targetSets = 3))
        }
    }

    private suspend fun doOneSetAndFinish(workoutId: String) {
        val detail = assertNotNull(data.workouts.observeWorkout(workoutId).first())
        val set = detail.exercises.first().sets.first()
        data.workouts.updateSetValues(SetValues(set.id, 60.0, 10))
        data.workouts.setCompleted(set.id, true, Instant.parse("2026-11-14T10:30:00Z"))
        data.workouts.finishWorkout(workoutId, Instant.parse("2026-11-14T11:00:00Z"))
    }

    @Test
    fun `ルーティンがなければ、次のルーティンは出ない`() = harness.run {
        val state = viewModel().state.first { it.loaded }
        assertNull(state.next)
        assertEquals(false, state.hasRoutines)
        assertEquals(today, state.today)
    }

    @Test
    fun `次のルーティンの中身は最大 3 行で、4 種目以上は 2 種目と「ほか N 種目」になる`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス", "インクライン", "ショルダープレス", "サイドレイズ", "プッシュダウン")
        val next = assertNotNull(viewModel().state.first { it.next != null }.next)

        assertEquals("Push", next.name)
        assertEquals(listOf("ベンチプレス", "インクライン"), next.items.map { it.name })
        assertEquals(3, next.moreCount)
        assertEquals("3 セット", next.items.first().setsText)
    }

    @Test
    fun `3 種目までは、すべて出す`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス", "インクライン", "ショルダープレス")
        val next = assertNotNull(viewModel().state.first { it.next != null }.next)
        assertEquals(3, next.items.size)
        assertEquals(0, next.moreCount)
    }

    @Test
    fun `ルーティンをやったら、次は並びの次のルーティンを提案する`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス")
        routine("pull", "Pull", 1, "ラットプルダウン")
        val vm = viewModel()
        background { vm.state.collect {} }
        assertEquals("Push", vm.state.first { it.next != null }.next?.name)

        doOneSetAndFinish(start(today, "push"))

        val state = vm.state.first { it.next?.name == "Pull" }
        assertEquals(listOf("Push", "Pull"), state.next?.sequence?.map { it.name })
        assertEquals(listOf(false, true), state.next?.sequence?.map { it.isNext })
        val last = assertNotNull(state.last, "前回のワークアウトが出る")
        assertEquals("Push", last.headline.substringBefore("　"))
        assertEquals("ベンチプレス", last.highlight?.exerciseName)
    }

    @Test
    fun `次のルーティンの種目には、前回の記録が付く`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス")
        doOneSetAndFinish(start(today.minusDays(2), "push"))

        val next = assertNotNull(viewModel().state.first { it.next != null }.next)
        val previous = assertNotNull(next.items.single().previous)
        assertEquals("前回 11/12", previous.label)
        assertEquals("60 kg", previous.line.weightText)
    }

    @Test
    fun `日付の帯には 7 日が並び、今日の印と、記録のある日のルーティン名が出る`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス")
        doOneSetAndFinish(start(today.minusDays(2), "push"))

        val strip = viewModel().state.first { it.strip.any { cell -> cell.workoutId != null } }.strip

        assertEquals(7, strip.size)
        // 月曜始まりの週: 11/9（月）〜 11/15（日）
        assertEquals(LocalDate.of(2026, 11, 9), strip.first().date)
        assertEquals(today, strip.single { it.isToday }.date)
        assertEquals("今日", strip.single { it.isToday }.label)
        assertEquals("Push", strip.single { it.date == today.minusDays(2) }.label)
    }

    @Test
    fun `日付の帯の並びは、設定で変わる`() = harness.run {
        data.settings.update { it.copy(homeStripMode = HomeStripMode.Last7Days) }
        val strip = viewModel().state.first { it.loaded && it.strip.isNotEmpty() }.strip
        assertEquals(today.minusDays(6), strip.first().date)
        assertEquals(today, strip.last().date)
    }

    @Test
    fun `1日の区切りより前の時刻は、前の日が今日になる`() = harness.run {
        // UTC の 2026-11-15 01:30（区切りは 4 時）→ 今日は 11/14
        val early = MutableClock.at("2026-11-15T01:30:00Z")
        assertEquals(today, viewModel(early).state.first { it.loaded }.today)
        // 区切りを 0 時にすると、日付どおり（11/15）
        data.settings.update { it.copy(dayBoundaryHour = 0) }
        assertEquals(LocalDate.of(2026, 11, 15), viewModel(early).state.first { it.loaded }.today)
    }

    @Test
    fun `記録の途中のワークアウトがあれば、再開が出る`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス")
        val id = start(today, "push")

        val active = assertNotNull(viewModel().state.first { it.active != null }.active)

        assertEquals(id, active.workoutId)
        assertEquals("Push", active.title)
        assertNull(active.dateText, "今日の記録なら日付は出さない")
    }

    @Test
    fun `ルーティンを選んで始めると、ワークアウトが作られて開く合図が出る`() = harness.run {
        routine("push", "Push", 0, "ベンチプレス")
        val vm = viewModel()
        val opened = mutableListOf<HomeEvent>()
        background { vm.events.collect { opened += it } }

        vm.start("push", today)

        val event = opened.single() as HomeEvent.OpenWorkout
        val detail = assertNotNull(data.workouts.observeWorkout(event.workoutId).first())
        assertEquals("push", detail.workout.routineId)
        assertTrue(detail.exercises.isNotEmpty())
    }
}
