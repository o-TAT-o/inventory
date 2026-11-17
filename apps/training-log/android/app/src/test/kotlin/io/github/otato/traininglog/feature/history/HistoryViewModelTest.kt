package io.github.otato.traininglog.feature.history

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import io.github.otato.traininglog.domain.usecase.CopyWorkout
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.testing.MutableClock
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 履歴（カレンダーと、選んだ日の記録）。ワークアウトのメモを読み返せること、先の日には始められないこと。 */
class HistoryViewModelTest {
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

    private fun viewModel() = harness.track(
        HistoryViewModel(
            workouts = data.workouts,
            routines = data.routines,
            settingsRepository = data.settings,
            startWorkout = start,
            copyWorkout = CopyWorkout(data.workouts, data.exercises, clock, ids),
            clock = clock,
        ),
    )

    /** [date] に、ベンチプレスを 1 セットやって終えた記録を作る。 */
    private suspend fun finished(date: LocalDate): String {
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))
        val workoutId = start(date, null)
        AddExerciseToWorkout(data.workouts, data.exercises)(workoutId, "bench", 0)
        val set = assertNotNull(data.workouts.observeWorkout(workoutId).first()).exercises.single().sets.first()
        data.workouts.setCompleted(set.id, true, clock.instant())
        data.workouts.finishWorkout(workoutId, clock.instant())
        return workoutId
    }

    @Test
    fun `選んだ日の記録には、ワークアウトのメモが出て、直せる`() = harness.run {
        val workoutId = finished(today)
        data.workouts.updateMemo(workoutId, "寝不足で、重量を落とした")
        val vm = viewModel()
        background { vm.state.collect {} }

        val item = vm.state.first { it.items.isNotEmpty() }.items.single()
        assertEquals("寝不足で、重量を落とした", item.memo)
        assertEquals("ベンチプレス", item.exercises)

        vm.setMemo(workoutId, "寝不足。次は早く寝る")
        assertEquals("寝不足。次は早く寝る", vm.state.first { it.items.single().memo.startsWith("寝不足。") }.items.single().memo)

        // 空にすると、メモは消える
        vm.setMemo(workoutId, "")
        assertEquals("", vm.state.first { it.items.single().memo.isEmpty() }.items.single().memo)
    }

    @Test
    fun `先の日は選べるが、記録は始められない`() = harness.run {
        val vm = viewModel()
        background { vm.state.collect {} }
        assertFalse(vm.state.first { it.loaded }.selectedIsFuture)

        vm.select(today.plusDays(1))
        assertTrue(vm.state.first { it.selectedDate == today.plusDays(1) }.selectedIsFuture)
        vm.startOnSelected(routineId = null, quickEntry = false)
        delay(200)
        assertNull(data.workouts.observeActiveWorkout().first())

        // 過ぎた日は、始められる
        vm.select(today.minusDays(1))
        val past = vm.state.first { it.selectedDate == today.minusDays(1) }
        assertFalse(past.selectedIsFuture)
        assertFalse(past.selectedIsToday)
        vm.startOnSelected(routineId = null, quickEntry = true)
        val started = assertNotNull(data.workouts.observeActiveWorkout().first { it != null })
        assertEquals(today.minusDays(1), started.date)
        assertTrue(started.quickEntry)
    }
}
