package io.github.otato.traininglog

import android.app.Application
import io.github.otato.traininglog.di.AppContainer

class TrainingLogApplication : Application() {
    /** 依存の組み立て。ViewModel は、ここから必要なものを受け取る。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
