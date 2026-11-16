package io.github.otato.traininglog.feature.data

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SettingRow
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.model.BackupFormatException
import io.github.otato.traininglog.domain.model.BackupSnapshot
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.platform.AutoBackupFile
import io.github.otato.traininglog.platform.BackupFiles
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.lastExportHeadline
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 読み込む前の確認に出す中身。 */
data class RestoreConfirmUi(
    val snapshot: BackupSnapshot,
    val sourceLabel: String,
    val lines: List<String>,
)

data class DataUiState(
    /** 「最後の書き出し　10月13日（32日前）」／「まだ書き出していません」 */
    val exportHeadline: String = "",
    val autoBackup: Boolean = true,
    /** 端末の中の自動バックアップ（新しい順）の、表示用の日時 */
    val autoBackups: List<Pair<AutoBackupFile, String>> = emptyList(),
    val showAutoBackups: Boolean = false,
    val confirm: RestoreConfirmUi? = null,
    val busy: Boolean = false,
)

sealed interface DataEvent {
    /** 共有機能を開く */
    data class Share(val intent: Intent) : DataEvent
    data class Message(val text: String) : DataEvent
}

/** データ管理（F-DA-02〜04、F-DA-09、F-DA-10）。 */
class DataViewModel(
    private val files: BackupFiles,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = combine(local, settings.observe()) { s, setting ->
        s.copy(
            exportHeadline = lastExportHeadline(setting.lastExportAtMillis, clock),
            autoBackup = setting.autoBackup,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataUiState())

    private val _events = MutableSharedFlow<DataEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DataEvent> = _events.asSharedFlow()

    private val dateTime = DateTimeFormatter.ofPattern("M月d日 H:mm")

    init {
        refreshAutoBackups()
    }

    fun exportAll() = work { _events.emit(DataEvent.Share(files.exportJson())) }

    fun exportCsv() = work { _events.emit(DataEvent.Share(files.exportCsv())) }

    fun setAutoBackup(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(autoBackup = on) } }
    }

    /** 利用者が選んだファイルを読み、置き換える前の確認を出す（F-DA-03）。 */
    fun pickFile(uri: Uri) = work {
        val snapshot = files.read(uri)
        local.update { it.copy(confirm = confirmOf(snapshot, "選んだファイル")) }
    }

    fun showAutoBackups(show: Boolean) {
        if (show) refreshAutoBackups()
        local.update { it.copy(showAutoBackups = show) }
    }

    fun pickAutoBackup(file: AutoBackupFile) = work {
        val snapshot = files.read(file)
        local.update { it.copy(showAutoBackups = false, confirm = confirmOf(snapshot, "自動バックアップ")) }
    }

    fun cancelRestore() = local.update { it.copy(confirm = null) }

    fun restore() {
        val snapshot = local.value.confirm?.snapshot ?: return
        local.update { it.copy(confirm = null) }
        work {
            files.restore(snapshot)
            refreshAutoBackups()
            _events.emit(DataEvent.Message("データを復元しました"))
        }
    }

    private fun confirmOf(snapshot: BackupSnapshot, source: String): RestoreConfirmUi {
        val summary = snapshot.summary
        val exported = dateTime.format(snapshot.exportedAt.atZone(ZoneId.systemDefault()))
        return RestoreConfirmUi(
            snapshot = snapshot,
            sourceLabel = source,
            lines = listOf(
                "$exported に書き出したデータ",
                "ワークアウト ${summary.workoutCount} 回 · 種目 ${summary.exerciseCount} · ルーティン ${summary.routineCount}",
            ),
        )
    }

    private fun refreshAutoBackups() {
        viewModelScope.launch {
            val list = files.autoBackups().map { it to dateTime.format(it.createdAt.atZone(ZoneId.systemDefault())) }
            local.update { it.copy(autoBackups = list) }
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: BackupFormatException) {
                _events.emit(DataEvent.Message(e.message ?: "読み込めませんでした"))
            } catch (e: java.io.IOException) {
                _events.emit(DataEvent.Message("ファイルを作れませんでした。空き容量を確かめてください"))
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}

@Composable
fun DataRoute(onBack: () -> Unit, onOpenMigration: () -> Unit) {
    val viewModel = appViewModel<DataViewModel> { DataViewModel(it.backupFiles, it.data.settings, it.clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickFile(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DataEvent.Share -> context.startActivity(event.intent)
                is DataEvent.Message -> launch { snackbar.showSnackbar(event.text) }
            }
        }
    }

    DataScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onExportAll = viewModel::exportAll,
        onExportCsv = viewModel::exportCsv,
        // 書き出したファイルは JSON。端末によって種類の見え方が違うので、選べる範囲は広くしておき、中身で確かめる
        onPickFile = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onOpenMigration = onOpenMigration,
        onSetAutoBackup = viewModel::setAutoBackup,
        onShowAutoBackups = viewModel::showAutoBackups,
        onPickAutoBackup = viewModel::pickAutoBackup,
        onRestore = viewModel::restore,
        onCancelRestore = viewModel::cancelRestore,
    )
}

@Composable
fun DataScreen(
    state: DataUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onExportAll: () -> Unit,
    onExportCsv: () -> Unit,
    onPickFile: () -> Unit,
    onOpenMigration: () -> Unit,
    onSetAutoBackup: (Boolean) -> Unit,
    onShowAutoBackups: (Boolean) -> Unit,
    onPickAutoBackup: (AutoBackupFile) -> Unit,
    onRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("データ管理", onBack = onBack)
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.container)
                        .padding(16.dp),
                ) {
                    Text(state.exportHeadline, style = AppTheme.typography.heading, color = colors.onContainer)
                    Text(
                        "データはこの端末の中だけにあります。機種変更の前に書き出してください。",
                        style = AppTheme.typography.caption,
                        color = colors.onContainer,
                    )
                }
                AppDivider()
                ActionRow(AppIcons.Import, "ファイルから復元する", "読み込む前に、上書きするかを確認します", onPickFile)
                AppDivider()
                ActionRow(AppIcons.Table, "CSV で書き出す", "表計算ソフトで見られます", onExportCsv)
                AppDivider()
                ActionRow(AppIcons.Swap, "乗り換え用の初期設定", "種目ごとの直近の値をまとめて入れます", onOpenMigration)
                AppDivider()
                SettingRow("端末内に自動でバックアップを残す", subtitle = "直近 3 回分。データの不具合のときの復旧用です") {
                    Switch(checked = state.autoBackup, onCheckedChange = onSetAutoBackup)
                }
                AppDivider()
                if (state.autoBackups.isNotEmpty()) {
                    ActionRow(
                        AppIcons.Restore,
                        "自動バックアップから戻す",
                        "いちばん新しいもの: ${state.autoBackups.first().second}",
                        onClick = { onShowAutoBackups(true) },
                    )
                    AppDivider()
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        PrimaryButton(
            "全データを書き出す",
            onClick = onExportAll,
            icon = AppIcons.Export,
            enabled = !state.busy,
            minHeight = 60.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    if (state.showAutoBackups) {
        AlertDialog(
            onDismissRequest = { onShowAutoBackups(false) },
            title = { Text("どのバックアップに戻しますか？") },
            text = {
                Column {
                    state.autoBackups.forEach { (file, label) ->
                        Text(
                            label,
                            style = AppTheme.typography.bodyStrong,
                            color = colors.fg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { onPickAutoBackup(file) }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onShowAutoBackups(false) }) { Text("キャンセル") } },
        )
    }

    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("いまのデータを、上書きしますか？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${confirm.sourceLabel}の中身に、すべて置き換えます。")
                    confirm.lines.forEach { Text(it, style = AppTheme.typography.caption, color = colors.sub) }
                    Text("いまのデータは、自動バックアップに残るので、あとから戻せます。", style = AppTheme.typography.caption, color = colors.sub)
                }
            },
            confirmButton = { TextButton(onClick = onRestore) { Text("上書きする", color = colors.error) } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("取り消し") } },
        )
    }
}

/** 「(丸いアイコン) 題名／補足 ›」の行。 */
@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.line.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = AppTheme.typography.bodyStrong, color = colors.fg)
            Text(subtitle, style = AppTheme.typography.caption, color = colors.sub)
        }
        Icon(AppIcons.Right, contentDescription = null, tint = colors.sub)
    }
}
