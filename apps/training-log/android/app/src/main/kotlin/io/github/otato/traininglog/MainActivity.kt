package io.github.otato.traininglog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.otato.traininglog.platform.AppForeground
import io.github.otato.traininglog.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }

    override fun onStart() {
        super.onStart()
        AppForeground.isForeground = true
        // アプリにもどったら、休憩の通知は要らない
        getSystemService(android.app.NotificationManager::class.java)?.cancelAll()
    }

    override fun onStop() {
        AppForeground.isForeground = false
        super.onStop()
    }
}
