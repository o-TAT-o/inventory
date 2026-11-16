package io.github.otato.traininglog.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import io.github.otato.traininglog.domain.usecase.AddSet
import io.github.otato.traininglog.domain.usecase.StartWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * 依存の組み立て（DI のフレームワークは使わない。要件 9 章）。
 * アプリ全体で 1 つだけ作り、ViewModel に必要なものを渡す。
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val driver = AndroidSqliteDriver(
        schema = DataModule.schema,
        context = context,
        name = DATABASE_NAME,
        callback = object : AndroidSqliteDriver.Callback(DataModule.schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                // 外部キー（親を消すと子の記録も消える等）を有効にする
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    val clock: Clock = Clock.systemDefaultZone()
    val data = DataModule(driver, clock = clock)

    val startWorkout = StartWorkout(data.workouts, data.routines, data.exercises, clock)
    val addExerciseToWorkout = AddExerciseToWorkout(data.workouts, data.exercises)
    val addSet = AddSet(data.workouts)

    init {
        // 初回起動時に、種目とギアのプリセットを入れる
        appScope.launch { data.exercises.seedPresetsIfEmpty() }
    }

    private companion object {
        const val DATABASE_NAME = "training-log.db"
    }
}
