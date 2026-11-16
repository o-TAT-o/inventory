package io.github.otato.traininglog.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.otato.traininglog.designsystem.components.AppBottomBar
import io.github.otato.traininglog.designsystem.components.BottomBarItem
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.designsystem.theme.TrainingLogTheme
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.ThemeMode
import io.github.otato.traininglog.ui.common.rememberContainer
import io.github.otato.traininglog.ui.navigation.AppNavHost
import io.github.otato.traininglog.ui.navigation.Routes

private val tabs = listOf(
    Routes.HOME to BottomBarItem("今日", AppIcons.Home),
    Routes.ROUTINES to BottomBarItem("ルーティン", AppIcons.List),
    Routes.HISTORY to BottomBarItem("履歴", AppIcons.Calendar),
)

/** アプリ全体の入口。テーマ（ライト / ダーク / 端末に合わせる）と、下のタブ、画面のつながりを組み立てる。 */
@Composable
fun AppRoot() {
    val container = rememberContainer()
    val settingsFlow = remember(container) { container.data.settings.observe() }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val dark = when (settings.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> null
    }

    TrainingLogTheme(darkTheme = dark) {
        SystemBarsEffect(dark = AppTheme.colors.isDark)
        Surface(color = AppTheme.colors.bg, modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                // キーボードが出たら、画面をその分だけ縮める（入力欄・下のボタンがキーボードに隠れないように）
                AppNavHost(navController = navController, modifier = Modifier.weight(1f).imePadding())
                if (route in Routes.topLevel) {
                    AppBottomBar(
                        items = tabs.map { it.second },
                        selectedIndex = tabs.indexOfFirst { it.first == route }.coerceAtLeast(0),
                        onSelect = { index ->
                            val target = tabs[index].first
                            if (target != route) {
                                navController.navigate(target) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** ステータスバー・ナビゲーションバーの文字の色を、アプリのテーマ（ライト / ダーク）に合わせる。 */
@Composable
private fun SystemBarsEffect(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}
