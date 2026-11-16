package io.github.otato.traininglog.feature.migration

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.designsystem.components.CellKeyboard
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 乗り換え用の初期設定の画面の状態と、打った文字の保存（F-DA-05）。本物のデータ層で確かめる。 */
class MigrationViewModelTest {
    private val harness = ViewModelTestHarness()
    private val data = TestDatabase.create()

    private val bench = Exercise(id = "bench", name = "ベンチプレス", equipment = Equipment.Barbell)
    private val pullUp = Exercise(id = "pullup", name = "懸垂", recordType = RecordType.Bodyweight, useAddedWeight = true)
    private val pushUp = Exercise(id = "pushup", name = "腕立て伏せ", recordType = RecordType.Bodyweight, useAddedWeight = false)
    private val plank = Exercise(id = "plank", name = "プランク", recordType = RecordType.Duration)

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    private fun newViewModel() = harness.track(MigrationViewModel(data.baselines, data.settings))

    /** 種目を作って一覧に足し、画面に行が出るまで待つ（入力は、行が出てから始まる）。 */
    private suspend fun prepared(vararg exercises: Exercise): MigrationViewModel {
        exercises.forEach { data.exercises.save(it) }
        data.baselines.addExercises(exercises.map { it.id })
        return newViewModel().also { viewModel -> viewModel.state.first { it.rows.size == exercises.size } }
    }

    /** 保存の反映を待って、その種目の初期設定を返す（保存は、打った順に別の処理で行われる）。 */
    private suspend fun saved(exerciseId: String, until: (Baseline) -> Boolean): Baseline =
        data.baselines.observeBaselines()
            .first { rows -> rows.any { it.exercise.id == exerciseId && until(it.baseline) } }
            .first { it.exercise.id == exerciseId }
            .baseline

    @Test
    fun `種目を足した直後は、値が空で、入力済みは 0 種目`() = harness.run {
        val state = prepared(bench, plank).state.first { it.rows.size == 2 }
        assertEquals(0, state.filledCount)
        assertEquals(listOf("ベンチプレス", "プランク"), state.rows.map { it.name })
        assertEquals("", state.rows.first().weight.initialText)
    }

    @Test
    fun `重量と回数を続けて打っても、どちらも残り、入力済みに数えられる`() = harness.run {
        val viewModel = prepared(bench)
        viewModel.onWeight("bench", "62.5")
        viewModel.onReps("bench", "8")

        val baseline = saved("bench") { it.reps == 8 }
        assertEquals(62.5, baseline.weightKg)
        assertEquals(8, baseline.reps)
        assertEquals(1, viewModel.state.first { it.filledCount == 1 }.filledCount)
    }

    @Test
    fun `打った値は、画面を開き直しても入力欄の初期値として出る`() = harness.run {
        val viewModel = prepared(bench)
        viewModel.onWeight("bench", "62.5")
        viewModel.onReps("bench", "8")
        viewModel.onBest("bench", "70")
        saved("bench") { it.best == 70.0 && it.reps == 8 }

        val row = newViewModel().state.first { it.rows.isNotEmpty() }.rows.single()
        assertEquals("62.5", row.weight.initialText)
        assertEquals("8", row.reps.initialText)
        assertEquals("70", row.best.initialText)
    }

    @Test
    fun `lb で打った重量は、kg に直して保存される`() = harness.run {
        data.settings.update { it.copy(weightUnit = WeightUnit.Lb) }
        val viewModel = prepared(bench)
        assertEquals("lb", viewModel.state.first { it.rows.isNotEmpty() }.weightUnitLabel)

        viewModel.onWeight("bench", "132.28")
        val kg = assertNotNull(saved("bench") { it.weightKg != null }.weightKg)
        assertEquals(60.0, kg, 0.01)
    }

    @Test
    fun `空欄にすると、その欄だけが空に戻る`() = harness.run {
        val viewModel = prepared(bench)
        viewModel.onWeight("bench", "60")
        viewModel.onReps("bench", "10")
        saved("bench") { it.reps == 10 }

        viewModel.onWeight("bench", "")
        val baseline = saved("bench") { it.weightKg == null }
        assertNull(baseline.weightKg)
        assertEquals(10, baseline.reps)
    }

    @Test
    fun `時間の種目では、重量の欄は使えず、回数の欄が秒数になる`() = harness.run {
        val viewModel = prepared(plank)
        val row = viewModel.state.first { it.rows.isNotEmpty() }.rows.single()
        assertFalse(row.weight.enabled)
        assertEquals("プランクの秒数", row.reps.description)

        viewModel.onReps("plank", "90")
        val baseline = saved("plank") { it.durationSec != null }
        assertEquals(90, baseline.durationSec)
        assertNull(baseline.reps)
    }

    @Test
    fun `自重の種目は、加重を使うときだけ重量の欄が使え、補助のマイナスも打てる`() = harness.run {
        val rows = prepared(pullUp, pushUp).state.first { it.rows.size == 2 }.rows
        val weighted = rows.first { it.exerciseId == "pullup" }
        val plain = rows.first { it.exerciseId == "pushup" }

        assertTrue(weighted.weight.enabled)
        assertEquals(CellKeyboard.SignedDecimal, weighted.weight.keyboard)
        assertFalse(plain.weight.enabled)
        // 加重のない自重の自己ベストは、重量ではなく回数
        assertEquals(CellKeyboard.Integer, plain.best.keyboard)
    }

    @Test
    fun `一覧から外すと、行が消え、種目そのものは残る`() = harness.run {
        val viewModel = prepared(bench, plank)
        viewModel.remove("bench")

        assertEquals(listOf("plank"), viewModel.state.first { it.rows.size == 1 }.rows.map { it.exerciseId })
        assertTrue(data.exercises.observeExercises().first().any { it.id == "bench" })
    }

    @Test
    fun `打った文字の変換 マイナスは加重のある自重だけ、読めない文字は空欄`() {
        assertEquals(-20.0, MigrationInput.weightKg("-20", WeightUnit.Kg, pullUp))
        // 重量の種目では、マイナスは 0 に丸める
        assertEquals(0.0, MigrationInput.weightKg("-20", WeightUnit.Kg, bench))
        assertEquals(62.0, MigrationInput.weightKg("62.", WeightUnit.Kg, bench))
        assertNull(MigrationInput.weightKg("", WeightUnit.Kg, bench))
        assertNull(MigrationInput.weightKg(".", WeightUnit.Kg, bench))
        assertNull(MigrationInput.weightKg("abc", WeightUnit.Kg, bench))
    }

    @Test
    fun `自己ベストは、重量の種目は kg、加重のない自重は回数、時間は秒で保存する`() {
        assertEquals(100.0, MigrationInput.best("100", WeightUnit.Kg, bench))
        assertEquals(100.0, assertNotNull(MigrationInput.best("220.46", WeightUnit.Lb, bench)), 0.01)
        assertEquals(25.0, MigrationInput.best("25", WeightUnit.Kg, pushUp))
        assertEquals(120.0, MigrationInput.best("120", WeightUnit.Kg, plank))
        assertEquals(12, MigrationInput.count("12"))
        assertNull(MigrationInput.count(""))
    }
}
