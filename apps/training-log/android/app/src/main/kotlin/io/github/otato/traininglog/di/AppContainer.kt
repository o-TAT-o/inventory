package io.github.otato.traininglog.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import io.github.otato.traininglog.domain.usecase.AddSet
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.feature.workout.RestTimer
import io.github.otato.traininglog.platform.AppForeground
import io.github.otato.traininglog.platform.BackupFiles
import io.github.otato.traininglog.platform.NotifyingRestAlarm
import io.github.otato.traininglog.platform.PrefsRestStorage
import io.github.otato.traininglog.ui.common.RestAlert
import io.github.otato.traininglog.platform.RemoveAdsBilling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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

    /** 書き出し・読み込み・自動バックアップのファイルまわり */
    val backupFiles = BackupFiles(context.applicationContext, data.backup, data.settings, clock)

    /**
     * 休憩タイマー。アプリ全体で 1 つ。記録画面を離れても・アプリが落ちても、休憩が続くように、ここで持つ。
     * アプリが前に出ているときは、どの画面を見ていても、音とバイブで知らせる。後ろにあるときは、通知で知らせる。
     */
    val restTimer = RestTimer(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        clock = clock,
        storage = PrefsRestStorage(context),
        alarm = NotifyingRestAlarm(context),
        isForeground = { AppForeground.isForeground },
        alert = {
            val settings = data.settings.get()
            RestAlert.notify(context.applicationContext, settings.soundOn, settings.vibrationOn)
        },
    )

    /** 広告を消す買い切り。Google Play には、使うときにはじめてつなぐ */
    val billing = RemoveAdsBilling(context, data.settings, CoroutineScope(SupervisorJob() + Dispatchers.Main))

    init {
        // 初回起動時に、種目とギアのプリセットを入れる
        appScope.launch { data.exercises.seedPresetsIfEmpty() }
        // ワークアウトを終えるたびに、端末の中に自動バックアップを残す（F-DA-10）
        appScope.launch {
            data.workouts.observeLastFinished()
                .map { it?.workout?.finishedAt }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { backupFiles.autoBackupAfter(it) }
        }
    }

    private companion object {
        const val DATABASE_NAME = "training-log.db"
    }
}
