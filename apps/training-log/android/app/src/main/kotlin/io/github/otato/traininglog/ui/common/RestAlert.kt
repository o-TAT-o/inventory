package io.github.otato.traininglog.ui.common

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 休憩が終わったときの、音とバイブレーション（F-TM-03）。設定でそれぞれ切れる。 */
object RestAlert {
    fun notify(context: Context, sound: Boolean, vibration: Boolean) {
        if (vibration) vibrate(context)
        if (sound) beep()
    }

    private fun vibrate(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 400)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 700)
        }
    }
}
