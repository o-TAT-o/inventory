package io.github.otato.traininglog.data

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * データベースの構造を変えたとき、すでに使っている端末の記録を壊さずに移せること（要件 6.2: マイグレーション）。
 */
class SchemaMigrationTest {
    @Test
    fun `バージョンは、マイグレーションの数に合わせて上がっている`() {
        assertTrue(DataModule.schema.version >= 2, "最初の版（1）から、乗り換え用の初期設定の表を足した版（2）以降")
    }

    @Test
    fun `バージョン 1 のデータベースを移すと、記録はそのままで、初期設定の表が増える`() = runTest {
        val driver = TestDatabase.newDriver()
        DataModule.schema.create(driver)
        val before = DataModule(driver, Dispatchers.Unconfined)
        before.exercises.save(Exercise(id = "bench", name = "ベンチプレス"))

        // バージョン 1 の状態に戻す（初期設定の表がなかった頃）
        driver.execute(null, "DROP TABLE exercise_baseline", 0)

        DataModule.schema.migrate(driver, 1, DataModule.schema.version)

        val after = DataModule(driver, Dispatchers.Unconfined)
        assertEquals("ベンチプレス", assertNotNull(after.exercises.getExercise("bench")).name, "移す前の記録は残っている")
        after.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10))
        assertEquals(listOf("bench"), after.baselines.observeBaselines().first().map { it.exercise.id })
    }
}
