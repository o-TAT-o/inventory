package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.HomeStripMode
import io.github.otato.traininglog.domain.model.ThemeMode
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.model.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseRepositoryTest {
    private val data = TestDatabase.create()

    @Test
    fun `初回だけプリセットの種目とギアが入る`() = runTest {
        data.exercises.seedPresetsIfEmpty()
        val first = data.exercises.observeExercises().first()
        assertTrue(first.size >= 30, "代表的な種目が入っている")
        assertTrue(first.any { it.name == "ベンチプレス" })
        assertEquals(6, data.gear.observeGear().first().size)

        // 2 回目は何も足さない
        data.exercises.seedPresetsIfEmpty()
        assertEquals(first.size, data.exercises.observeExercises().first().size)
    }

    @Test
    fun `種目を保存して、同じ id で保存し直すと更新される`() = runTest {
        val bench = Exercise(id = "bench", name = "ベンチプレス", bodyPart = BodyPart.Chest, equipment = Equipment.Barbell)
        data.exercises.save(bench)
        data.exercises.save(bench.copy(name = "バーベルベンチプレス", memo = "肩甲骨を寄せる"))
        val loaded = assertNotNull(data.exercises.getExercise("bench"))
        assertEquals("バーベルベンチプレス", loaded.name)
        assertEquals("肩甲骨を寄せる", loaded.memo)
        assertEquals(1, data.exercises.observeExercises().first().size)
    }

    @Test
    fun `記録がない種目は削除され、記録がある種目は非表示になる`() = runTest {
        val unused = Exercise(id = "unused", name = "使わない種目")
        val used = Exercise(id = "used", name = "使った種目")
        data.exercises.save(unused)
        data.exercises.save(used)
        val planned = WorkoutPlanner(TestDatabase.sequentialIds()).plan("w", 0, used)
        data.workouts.createWorkout(Workout("w", LocalDate.of(2026, 11, 14), null, Instant.EPOCH), listOf(planned))

        data.exercises.deleteOrHide("unused")
        data.exercises.deleteOrHide("used")

        assertNull(data.exercises.getExercise("unused"))
        assertEquals(true, data.exercises.getExercise("used")?.hidden)
        assertTrue(data.exercises.observeExercises().first().isEmpty(), "非表示の種目は一覧に出ない")
        assertEquals(1, data.exercises.observeExercises(includeHidden = true).first().size)
    }
}

class SettingsRepositoryTest {
    private val data = TestDatabase.create()

    @Test
    fun `何も保存していなければ初期値`() = runTest {
        val settings = data.settings.get()
        assertEquals(WeightUnit.Kg, settings.weightUnit)
        assertEquals(4, settings.dayBoundaryHour)
        assertEquals(90, settings.restBetweenSetsSec)
    }

    @Test
    fun `変えた設定は保存され、次に読んでも同じ`() = runTest {
        data.settings.update {
            it.copy(
                weightUnit = WeightUnit.Lb,
                weightStepLb = 2.5,
                themeMode = ThemeMode.Dark,
                dayBoundaryHour = 3,
                homeStripMode = HomeStripMode.CenteredToday,
                autoStartRest = false,
            )
        }
        val settings = data.settings.observe().first()
        assertEquals(WeightUnit.Lb, settings.weightUnit)
        assertEquals(2.5, settings.weightStepLb)
        assertEquals(2.5, settings.weightStep)
        assertEquals(ThemeMode.Dark, settings.themeMode)
        assertEquals(3, settings.dayBoundaryHour)
        assertEquals(HomeStripMode.CenteredToday, settings.homeStripMode)
        assertEquals(false, settings.autoStartRest)
    }

    @Test
    fun `範囲の外の 1日の区切りは 0 から 6 に収める`() = runTest {
        data.settings.update { it.copy(dayBoundaryHour = 20) }
        assertEquals(6, data.settings.get().dayBoundaryHour)
    }
}
