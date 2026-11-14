package io.github.otato.traininglog.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import io.github.otato.traininglog.TrainingLogApplication
import io.github.otato.traininglog.di.AppContainer

/** アプリの依存の入れ物（AppContainer）を取り出す。 */
@Composable
fun rememberContainer(): AppContainer =
    (LocalContext.current.applicationContext as TrainingLogApplication).container

/**
 * ViewModel を作る。DI のフレームワークは使わず、AppContainer から必要なものを取って渡す。
 * [key] を変えると別の ViewModel になる（同じ画面を別の id で開くとき）。
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = rememberContainer()
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
