package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WorkoutExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutineLogicTest {
    private val routines = listOf(
        Routine(id = "push", name = "Push", sortOrder = 0),
        Routine(id = "pull", name = "Pull", sortOrder = 1),
        Routine(id = "legs", name = "Legs", sortOrder = 2),
    )

    @Test
    fun `履歴がなければ先頭のルーティンを提案する`() {
        assertEquals("push", NextRoutine.suggest(routines, lastRoutineId = null)?.id)
    }

    @Test
    fun `最後にやったルーティンの次を提案し、最後まで来たら先頭に戻る`() {
        assertEquals("pull", NextRoutine.suggest(routines, "push")?.id)
        assertEquals("legs", NextRoutine.suggest(routines, "pull")?.id)
        assertEquals("push", NextRoutine.suggest(routines, "legs")?.id)
    }

    @Test
    fun `削除されたルーティンが最後でも先頭から始める`() {
        assertEquals("push", NextRoutine.suggest(routines, "deleted")?.id)
    }

    @Test
    fun `ルーティンがなければ提案しない`() {
        assertNull(NextRoutine.suggest(emptyList(), null))
    }

    private fun workoutExercise(rest: Int? = null, restAfter: Int? = null) = WorkoutExercise(
        id = "we", workoutId = "w", exerciseId = "e", weightMode = WeightMode.Total, repMode = RepMode.Normal,
        restSeconds = rest, restAfterSeconds = restAfter,
    )

    @Test
    fun `セット間の休憩は、種目の設定がなければ設定の初期値`() {
        val settings = AppSettings(restBetweenSetsSec = 90, restAfterExerciseSec = 120)
        assertEquals(90, RestTimes.afterSet(false, workoutExercise(), settings))
        assertEquals(120, RestTimes.afterSet(true, workoutExercise(), settings))
    }

    @Test
    fun `ルーティンの種目に休憩の設定があればそれを使う`() {
        val settings = AppSettings(restBetweenSetsSec = 90, restAfterExerciseSec = 120)
        val exercise = workoutExercise(rest = 150, restAfter = 240)
        assertEquals(150, RestTimes.afterSet(false, exercise, settings))
        assertEquals(240, RestTimes.afterSet(true, exercise, settings))
    }
}
