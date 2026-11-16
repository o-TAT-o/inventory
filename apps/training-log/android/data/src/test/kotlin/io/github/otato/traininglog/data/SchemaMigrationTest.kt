package io.github.otato.traininglog.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * データベースの構造を変えたとき、すでに使っている端末の記録を壊さずに移せること（要件 6.2: マイグレーション）。
 */
class SchemaMigrationTest {
    @Test
    fun `バージョンは、マイグレーションの数に合わせて上がっている`() {
        assertTrue(
            DataModule.schema.version >= 3,
            "最初の版（1）から、初期設定の表を足した版（2）、腕を二頭と三頭に分けて初期設定にセット数を足した版（3）以降",
        )
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
        after.baselines.save(Baseline("bench", weightKg = 60.0, reps = 10, sets = 3))
        assertEquals(listOf("bench"), after.baselines.observeBaselines().first().map { it.exercise.id })
    }

    @Test
    fun `バージョン 2 のデータベースを移すと、保存されている「腕」が、二頭と三頭に分かれる`() = runTest {
        val driver = versionTwoDatabase()
        assertEquals(6, count(driver, "body_part = 'Arms'"), "移す前は、6 つの種目が「腕」")

        DataModule.schema.migrate(driver, 2, DataModule.schema.version)

        // 保存された名前そのものが変わっている（読み込みのときの読み替えに頼らない）
        assertEquals(0, count(driver, "body_part = 'Arms'"))
        assertEquals(4, count(driver, "body_part = 'Triceps'"))
        assertEquals(2, count(driver, "body_part = 'Biceps'"))
        assertEquals(1, count(driver, "body_part = 'Chest'"), "腕ではない種目は、そのまま")
    }

    @Test
    fun `移したあとの種目は、プリセットも自分で作った種目も、名前から二頭と三頭に分かれて読める`() = runTest {
        val driver = versionTwoDatabase()

        DataModule.schema.migrate(driver, 2, DataModule.schema.version)

        val after = DataModule(driver, Dispatchers.Unconfined)
        suspend fun bodyPart(id: String) = assertNotNull(after.exercises.getExercise(id)).bodyPart
        assertEquals(BodyPart.Biceps, bodyPart("preset:barbell_curl"))
        assertEquals(BodyPart.Triceps, bodyPart("preset:triceps_pushdown"))
        assertEquals(BodyPart.Triceps, bodyPart("preset:skull_crusher"))
        assertEquals(BodyPart.Biceps, bodyPart("custom-1"), "三頭と分かる名前でなければ、二頭にする")
        assertEquals(BodyPart.Triceps, bodyPart("custom-2"), "キックバック")
        assertEquals(BodyPart.Triceps, bodyPart("custom-3"), "トライセプス")
        assertEquals(BodyPart.Chest, bodyPart("bench"))
    }

    @Test
    fun `移したあとも、初期設定の値は残り、セット数の列は空で、値を取り違えない`() = runTest {
        val driver = versionTwoDatabase()

        DataModule.schema.migrate(driver, 2, DataModule.schema.version)

        // 列の並びが、新しく作ったデータベースと同じ（足した列は末尾）なので、SELECT * で読んでも値がずれない
        val after = DataModule(driver, Dispatchers.Unconfined)
        val row = after.baselines.observeBaselines().first().single().baseline
        assertEquals(30.0, row.weightKg)
        assertEquals(10, row.reps)
        assertEquals(40.0, row.best)
        assertNull(row.sets)

        after.baselines.save(row.copy(sets = 4))
        val saved = after.baselines.observeBaselines().first().single().baseline
        assertEquals(4, saved.sets)
        assertEquals(30.0, saved.weightKg)
        assertEquals(40.0, saved.best)
    }

    /**
     * バージョン 2 の状態のデータベース: 部位が「腕」（Arms）の種目が 6 つ（プリセット 3、自分で作った種目 3）と、
     * 胸の種目が 1 つ。初期設定の表には、セット数の列がなく、バーベルカールの値が 1 行ある。
     */
    private suspend fun versionTwoDatabase(): SqlDriver {
        val driver = TestDatabase.newDriver()
        DataModule.schema.create(driver)
        val data = DataModule(driver, Dispatchers.Unconfined)
        listOf(
            "preset:barbell_curl" to "バーベルカール",
            "preset:triceps_pushdown" to "トライセプスプッシュダウン",
            "preset:skull_crusher" to "スカルクラッシャー",
            "custom-1" to "ケーブルカール",
            "custom-2" to "ダンベルキックバック",
            "custom-3" to "ライイングトライセプスエクステンション",
        ).forEach { (id, name) -> data.exercises.save(Exercise(id = id, name = name, bodyPart = BodyPart.Biceps)) }
        data.exercises.save(Exercise(id = "bench", name = "ベンチプレス", bodyPart = BodyPart.Chest))
        driver.execute(null, "UPDATE exercise SET body_part = 'Arms' WHERE id <> 'bench'", 0)

        driver.execute(null, "DROP TABLE exercise_baseline", 0)
        driver.execute(
            null,
            "CREATE TABLE exercise_baseline (" +
                "exercise_id TEXT NOT NULL PRIMARY KEY REFERENCES exercise(id) ON DELETE CASCADE, " +
                "weight_kg REAL, reps INTEGER, duration_sec INTEGER, best REAL, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO exercise_baseline (exercise_id, weight_kg, reps, best, sort_order, updated_at) " +
                "VALUES ('preset:barbell_curl', 30.0, 10, 40.0, 0, 0)",
            0,
        )
        return driver
    }

    private fun count(driver: SqlDriver, where: String): Long =
        driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM exercise WHERE $where",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            0,
        ).value
}
