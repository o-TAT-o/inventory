package io.github.otato.traininglog.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.repository.SqlBaselineRepository
import io.github.otato.traininglog.data.repository.SqlExerciseRepository
import io.github.otato.traininglog.data.repository.SqlGearRepository
import io.github.otato.traininglog.data.repository.SqlRoutineRepository
import io.github.otato.traininglog.data.repository.SqlSettingsRepository
import io.github.otato.traininglog.data.repository.SqlWorkoutRepository
import io.github.otato.traininglog.domain.repository.BaselineRepository
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock

/**
 * データ層の入口。ドライバー（Android なら Android の SQLite、テストなら JVM の SQLite）を渡すと、
 * ドメイン層の interface を実装したリポジトリを返す。画面側は SQLDelight の型を知らなくてよい。
 */
class DataModule(
    driver: SqlDriver,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: Clock = Clock.systemDefaultZone(),
) {
    private val database = TrainingLogDatabase(driver)

    val exercises: ExerciseRepository = SqlExerciseRepository(database, ioDispatcher)
    val gear: GearRepository = SqlGearRepository(database, ioDispatcher)
    val routines: RoutineRepository = SqlRoutineRepository(database, ioDispatcher)
    val workouts: WorkoutRepository = SqlWorkoutRepository(database, ioDispatcher, clock)
    val settings: SettingsRepository = SqlSettingsRepository(database, ioDispatcher)
    val baselines: BaselineRepository = SqlBaselineRepository(database, ioDispatcher, clock)

    companion object {
        /** ドライバーを作るときに渡すスキーマ（テーブルの定義とマイグレーション）。 */
        val schema: SqlSchema<QueryResult.Value<Unit>> get() = TrainingLogDatabase.Schema
    }
}
