package io.github.otato.traininglog.platform

import android.content.Context
import io.github.otato.traininglog.feature.workout.RestAlarm
import io.github.otato.traininglog.feature.workout.RestStorage
import io.github.otato.traininglog.feature.workout.RunningRest

/** 休憩の状態を、端末に残す（アプリが落ちても、休憩が続くように）。小さな値なので、SharedPreferences に置く。 */
class PrefsRestStorage(context: Context) : RestStorage {
    private val prefs = context.applicationContext.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    override fun load(): RunningRest? {
        val workoutId = prefs.getString(KEY_WORKOUT, null) ?: return null
        return RunningRest(
            workoutId = workoutId,
            endAtMillis = prefs.getLong(KEY_END_AT, 0L),
            totalSec = prefs.getInt(KEY_TOTAL, 0),
            notified = prefs.getBoolean(KEY_NOTIFIED, false),
        )
    }

    override fun save(rest: RunningRest?) {
        prefs.edit().apply {
            if (rest == null) {
                clear()
            } else {
                putString(KEY_WORKOUT, rest.workoutId)
                putLong(KEY_END_AT, rest.endAtMillis)
                putInt(KEY_TOTAL, rest.totalSec)
                putBoolean(KEY_NOTIFIED, rest.notified)
            }
        }.apply()
    }

    private companion object {
        const val KEY_WORKOUT = "workout_id"
        const val KEY_END_AT = "end_at"
        const val KEY_TOTAL = "total_sec"
        const val KEY_NOTIFIED = "notified"
    }
}

/** 休憩の終わりの時刻に、通知を出すための目覚まし（[RestNotifier]）。 */
class NotifyingRestAlarm(context: Context) : RestAlarm {
    private val appContext = context.applicationContext

    override fun schedule(endAtMillis: Long) = RestNotifier.schedule(appContext, endAtMillis)

    override fun cancel() = RestNotifier.cancel(appContext)
}
