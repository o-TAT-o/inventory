package io.github.otato.traininglog.feature.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.ReorderHandle
import io.github.otato.traininglog.designsystem.components.ReorderableRow
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.components.rememberReorderState
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ギアの一覧の 1 行。「ベルト　使用 31 回」「エルボースリーブ　非表示 · 使用 0 回」「チョーク　自分で追加 · 使用 6 回」 */
data class GearRowUi(
    val id: String,
    val name: String,
    val detail: String,
    val visible: Boolean,
)

/** ギアの管理（F-GR-01）。追加・名前の変更・非表示・並べ替え。 */
class GearViewModel(
    private val repository: GearRepository,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {
    val rows: StateFlow<List<GearRowUi>> =
        combine(repository.observeGear(includeHidden = true), repository.observeUsage()) { gear, usage ->
            gear.map { item ->
                val parts = buildList {
                    if (item.hidden) add("非表示")
                    if (!item.id.startsWith(PRESET_PREFIX)) add("自分で追加")
                    add("使用 ${usage[item.id] ?: 0} 回")
                }
                GearRowUi(item.id, item.name, parts.joinToString(" · "), visible = !item.hidden)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.save(Gear(id = ids.next(), name = trimmed, sortOrder = repository.nextSortOrder()))
        }
    }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    /** 非表示にしても、過去の記録には残る。記録画面の一覧に出なくなるだけ。 */
    fun setVisible(id: String, visible: Boolean) = change(id) { it.copy(hidden = !visible) }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorder(orderedIds) }
    }

    private fun change(id: String, transform: (Gear) -> Gear) {
        viewModelScope.launch {
            val gear = repository.observeGear(includeHidden = true).first().firstOrNull { it.id == id } ?: return@launch
            repository.save(transform(gear))
        }
    }

    private companion object {
        const val PRESET_PREFIX = "preset:"
    }
}

@Composable
fun GearRoute(onBack: () -> Unit) {
    val viewModel = appViewModel<GearViewModel> { GearViewModel(it.data.gear) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    GearScreen(rows, onBack, viewModel::add, viewModel::rename, viewModel::setVisible, viewModel::reorder)
}

@Composable
fun GearScreen(
    rows: List<GearRowUi>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onSetVisible: (id: String, visible: Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val colors = AppTheme.colors
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<GearRowUi?>(null) }
    val reorder = rememberReorderState(
        source = rows,
        key = { it.id },
        leadingItems = 1,
        onCommit = { onReorder(it.map { row -> row.id }) },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("ギアの管理", onBack = onBack)
        LazyColumn(state = reorder.listState, modifier = Modifier.weight(1f)) {
            item(key = "lead") {
                Column {
                    Text(
                        "記録画面で付け外しできる道具の一覧です。使わないものは非表示にできます（過去の記録は残ります）。",
                        style = AppTheme.typography.caption,
                        color = colors.sub,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    AppDivider()
                }
            }
            items(reorder.items, key = { it.id }) { row ->
                ReorderableRow(reorder, key = row.id) { handle, _ ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(start = 4.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ReorderHandle(handle)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(role = Role.Button, onClickLabel = "名前を変える") { renaming = row }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(row.name, style = AppTheme.typography.bodyStrong, color = if (row.visible) colors.fg else colors.sub)
                                Text(row.detail, style = AppTheme.typography.caption, color = colors.sub)
                            }
                            Switch(
                                checked = row.visible,
                                onCheckedChange = { onSetVisible(row.id, it) },
                                modifier = Modifier.semantics { contentDescription = "${row.name}を記録画面に出す" },
                            )
                        }
                        AppDivider()
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.bg)) {
            PrimaryButton(
                "ギアを追加",
                onClick = { adding = true },
                icon = AppIcons.Plus,
                minHeight = 60.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (adding) {
        TextInputDialog(
            title = "ギアを追加",
            initialText = "",
            label = "ギアの名前（例: チョーク）",
            onConfirm = {
                adding = false
                onAdd(it)
            },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { row ->
        TextInputDialog(
            title = "名前を変える",
            initialText = row.name,
            label = "ギアの名前",
            onConfirm = {
                renaming = null
                onRename(row.id, it)
            },
            onDismiss = { renaming = null },
        )
    }
}
