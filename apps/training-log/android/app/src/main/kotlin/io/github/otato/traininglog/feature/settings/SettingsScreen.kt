package io.github.otato.traininglog.feature.settings

import android.app.AlarmManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.otato.traininglog.designsystem.components.TextAction
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.platform.AdBanner
import io.github.otato.traininglog.ui.common.lastExportShort
import io.github.otato.traininglog.ui.common.rememberContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.CompactStepper
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.SettingRow
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.HomeStripMode
import io.github.otato.traininglog.domain.model.ThemeMode
import io.github.otato.traininglog.domain.model.WeekStart
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatMmSs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val state: StateFlow<AppSettings> =
        repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenGear: () -> Unit,
    onOpenData: () -> Unit,
    onOpenInfo: (InfoKind) -> Unit,
) {
    val container = rememberContainer()
    val viewModel = appViewModel<SettingsViewModel> { SettingsViewModel(it.data.settings) }
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val billing = container.billing
    val price by billing.price.collectAsStateWithLifecycle()
    val message by billing.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalActivity.current
    val context = LocalContext.current

    // 休憩の通知を時刻どおりに出す許可（Android 12 以上）。設定の画面からもどったときに、確かめ直す
    var exactAlarmMissing by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        exactAlarmMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == false
        onPauseOrDispose {}
    }

    // 値段を取りにいき、購入済みかを確かめる（設定を開いたときだけ、Google Play につなぐ）
    LaunchedEffect(billing) { billing.refresh() }
    LaunchedEffect(message) {
        message?.let {
            billing.consumeMessage()
            snackbar.showSnackbar(it.text)
        }
    }

    SettingsScreen(
        settings = settings,
        dataSubtitle = lastExportShort(settings.lastExportAtMillis, container.clock),
        price = price,
        versionName = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" },
        exactAlarmMissing = exactAlarmMissing,
        onAllowExactAlarm = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
        },
        debuggable = remember(context) { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 },
        snackbar = snackbar,
        onBack = onBack,
        onUpdate = viewModel::update,
        onOpenGear = onOpenGear,
        onOpenData = onOpenData,
        onOpenInfo = onOpenInfo,
        onPurchase = { activity?.let(billing::purchase) },
        onRestorePurchase = billing::restore,
    )
}

/** 設定（F-SE-01〜08）。デザインの「設定」画面と同じ並びにしている。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    dataSubtitle: String,
    price: String?,
    versionName: String,
    debuggable: Boolean,
    exactAlarmMissing: Boolean,
    onAllowExactAlarm: () -> Unit,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenGear: () -> Unit,
    onOpenData: () -> Unit,
    onOpenInfo: (InfoKind) -> Unit,
    onPurchase: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("設定", onBack = onBack)
        Box(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group("単位と入力") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重量の単位", style = AppTheme.typography.bodyStrong, color = colors.fg)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip("kg", settings.weightUnit == WeightUnit.Kg, { onUpdate { it.copy(weightUnit = WeightUnit.Kg) } })
                        AppChip("lb", settings.weightUnit == WeightUnit.Lb, { onUpdate { it.copy(weightUnit = WeightUnit.Lb) } })
                    }
                }
                AppDivider()
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重量の刻み", style = AppTheme.typography.bodyStrong, color = colors.fg)
                    Text(
                        "＋ − ボタンで増減する幅（${WeightUnits.label(settings.weightUnit)}）。種目ごとにも変えられます",
                        style = AppTheme.typography.caption,
                        color = colors.sub,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeightUnits.stepOptions(settings.weightUnit).forEach { step ->
                            AppChip(
                                WeightUnits.format(step),
                                selected = settings.weightStep == step,
                                onClick = {
                                    onUpdate { if (it.weightUnit == WeightUnit.Kg) it.copy(weightStepKg = step) else it.copy(weightStepLb = step) }
                                },
                            )
                        }
                    }
                }
            }
            Group("表示") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("テーマ", style = AppTheme.typography.bodyStrong, color = colors.fg)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip("ライト", settings.themeMode == ThemeMode.Light, { onUpdate { it.copy(themeMode = ThemeMode.Light) } })
                        AppChip("ダーク", settings.themeMode == ThemeMode.Dark, { onUpdate { it.copy(themeMode = ThemeMode.Dark) } })
                        AppChip("端末に合わせる", settings.themeMode == ThemeMode.System, { onUpdate { it.copy(themeMode = ThemeMode.System) } })
                    }
                }
                AppDivider()
                SettingRow("履歴カレンダーの週の始まり") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip("日曜", settings.weekStart == WeekStart.Sunday, { onUpdate { it.copy(weekStart = WeekStart.Sunday) } })
                        AppChip("月曜", settings.weekStart == WeekStart.Monday, { onUpdate { it.copy(weekStart = WeekStart.Monday) } })
                    }
                }
                AppDivider()
                SettingRow("1日の区切り", subtitle = "${settings.dayBoundaryHour}:00 までは前の日") {
                    CompactStepper(
                        valueText = "${settings.dayBoundaryHour}:00",
                        onMinus = { onUpdate { it.copy(dayBoundaryHour = (it.dayBoundaryHour - 1).coerceAtLeast(0)) } },
                        onPlus = { onUpdate { it.copy(dayBoundaryHour = (it.dayBoundaryHour + 1).coerceAtMost(DayBoundary.MAX_HOUR)) } },
                        minusLabel = "区切りを早くする",
                        plusLabel = "区切りを遅くする",
                    )
                }
                AppDivider()
                Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp)) {
                    Text("ホームの日付の帯", style = AppTheme.typography.bodyStrong, color = colors.fg)
                    Text("日付をタップすると、その日の記録を開きます", style = AppTheme.typography.caption, color = colors.sub)
                }
                StripOption("その週", "週の始まりに合わせて 7 日を並べる", settings.homeStripMode == HomeStripMode.Week) {
                    onUpdate { it.copy(homeStripMode = HomeStripMode.Week) }
                }
                StripOption("今日を真ん中", "前後 3 日ずつ。左右にスワイプして動かせる", settings.homeStripMode == HomeStripMode.CenteredToday) {
                    onUpdate { it.copy(homeStripMode = HomeStripMode.CenteredToday) }
                }
                StripOption("直近 7 日", "今日が右端。前日までの入力がしやすい", settings.homeStripMode == HomeStripMode.Last7Days) {
                    onUpdate { it.copy(homeStripMode = HomeStripMode.Last7Days) }
                }
            }
            Group("休憩タイマー") {
                SettingRow("休憩（セット間）", subtitle = "記録中に変えられます") {
                    CompactStepper(
                        valueText = formatMmSs(settings.restBetweenSetsSec.toLong()),
                        onMinus = { onUpdate { it.copy(restBetweenSetsSec = (it.restBetweenSetsSec - 10).coerceAtLeast(10)) } },
                        onPlus = { onUpdate { it.copy(restBetweenSetsSec = it.restBetweenSetsSec + 10) } },
                        minusLabel = "休憩を短くする",
                        plusLabel = "休憩を長くする",
                    )
                }
                AppDivider()
                SettingRow("休憩（種目のあと）", subtitle = "次の種目に進むとき") {
                    CompactStepper(
                        valueText = formatMmSs(settings.restAfterExerciseSec.toLong()),
                        onMinus = { onUpdate { it.copy(restAfterExerciseSec = (it.restAfterExerciseSec - 10).coerceAtLeast(10)) } },
                        onPlus = { onUpdate { it.copy(restAfterExerciseSec = it.restAfterExerciseSec + 10) } },
                        minusLabel = "種目のあとの休憩を短くする",
                        plusLabel = "種目のあとの休憩を長くする",
                    )
                }
                AppDivider()
                SettingRow("セットを完了したら、自動で始める") {
                    Switch(checked = settings.autoStartRest, onCheckedChange = { v -> onUpdate { it.copy(autoStartRest = v) } })
                }
                AppDivider()
                SettingRow("音で知らせる") {
                    Switch(checked = settings.soundOn, onCheckedChange = { v -> onUpdate { it.copy(soundOn = v) } })
                }
                AppDivider()
                SettingRow("バイブで知らせる") {
                    Switch(checked = settings.vibrationOn, onCheckedChange = { v -> onUpdate { it.copy(vibrationOn = v) } })
                }
                if (exactAlarmMissing) {
                    AppDivider()
                    SettingRow(
                        "通知を、時刻どおりに出す",
                        subtitle = "許可がないと、アプリを閉じているときの休憩の通知が、1 分ほど遅れることがあります",
                    ) {
                        TextAction("許可する", onClick = onAllowExactAlarm)
                    }
                }
            }
            Group("管理") {
                LinkRow("ギアの管理", "ベルト・パワーグリップなど", onOpenGear)
                AppDivider()
                LinkRow("データ管理", dataSubtitle, onOpenData)
            }
            Group("広告") {
                if (settings.adsRemoved) {
                    SettingRow("広告を消す", subtitle = "購入済みです。広告は出ません") {
                        Icon(AppIcons.Check, contentDescription = null, tint = colors.primary)
                    }
                } else {
                    SettingRow("広告を消す", subtitle = "買い切り・ずっと非表示") {
                        PrimaryButton(
                            text = if (price != null) "$price で購入" else "購入",
                            onClick = onPurchase,
                            minHeight = 48.dp,
                            textStyle = AppTheme.typography.bodyStrong,
                            horizontalPadding = 16.dp,
                        )
                    }
                }
                AppDivider()
                LinkRow("購入を復元", "機種変更や再インストールのあとに", onRestorePurchase)
                if (debuggable) {
                    AppDivider()
                    SettingRow("（開発用）広告を消した状態にする", subtitle = "公開するアプリには出ません") {
                        Switch(checked = settings.adsRemoved, onCheckedChange = { v -> onUpdate { it.copy(adsRemoved = v) } })
                    }
                }
            }
            Group("このアプリについて") {
                LinkRow("利用規約", null) { onOpenInfo(InfoKind.Terms) }
                AppDivider()
                LinkRow("プライバシーポリシー", null) { onOpenInfo(InfoKind.Privacy) }
                AppDivider()
                LinkRow("ライセンス表示", null) { onOpenInfo(InfoKind.Licenses) }
                AppDivider()
                SettingRow("バージョン") { Text(versionName, style = AppTheme.typography.body, color = colors.sub) }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        // 広告を消す購入をしていれば、何も出ない（F-AD-02）
        AdBanner()
    }
}

/** 「題名／補足 ›」の、タップで次の画面へ進む行。 */
@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    SettingRow(title = title, subtitle = subtitle, onClick = onClick) {
        Icon(AppIcons.Right, contentDescription = null, tint = AppTheme.colors.sub)
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
        SectionLabel(title, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        AppCard(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun StripOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg)
            Text(subtitle, style = AppTheme.typography.caption, color = AppTheme.colors.sub)
        }
    }
}
