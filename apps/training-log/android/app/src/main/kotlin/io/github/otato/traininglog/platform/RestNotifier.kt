package io.github.otato.traininglog.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.otato.traininglog.R

/** アプリが前に出ているか。前に出ているときは、通知ではなく、画面の中の音とバイブで知らせる。 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
}

/**
 * 休憩が終わったことを、アプリが後ろにあるときも通知で知らせる（F-TM-03）。
 *
 * 休憩が始まったら、終わる時刻に目覚まし（AlarmManager）を仕掛ける。休憩をやめたら、取り消す。
 * 時刻になったとき、アプリが前に出ていれば、何もしない（画面の中で知らせる）。
 *
 * 正確な時刻に鳴らすには、端末の「アラームとリマインダー」の許可が要る。許可がないときは、
 * 少し遅れることがある仕掛け方（省電力中は、数分遅れることがある）に切り替える。
 */
object RestNotifier {
    private const val CHANNEL_ID = "rest"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_CODE = 1

    /** [endAtMillis]（エポックミリ秒）に、休憩の終わりを知らせるように仕掛ける。前の仕掛けは置き換わる。 */
    fun schedule(context: Context, endAtMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)
        // 前の休憩の通知が残っていたら消す
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, intent)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, intent)
        }
    }

    /** 仕掛けを取り消し、出ている通知も消す。 */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** 通知を出せるか（Android 13 以上では、利用者の許可が要る）。 */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun show(context: Context) {
        if (AppForeground.isForeground || !canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "休憩タイマー", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "休憩が終わったことを知らせます"
                },
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("休憩が終わりました")
            .setContentText("タップすると、記録にもどります")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RestAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** 休憩の終わりの時刻に呼ばれる。 */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        RestNotifier.show(context)
    }
}
