package io.github.otato.traininglog.feature.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.TextInputDialog
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.ui.common.appViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineRowUi(val id: String, val name: String, val summary: String)

data class RoutinesUiState(val loaded: Boolean = false, val rows: List<RoutineRowUi> = emptyList())

/** ルーティンの一覧（F-RT-01）。作成すると、そのまま編集画面を開く。 */
class RoutinesViewModel(
    private val routines: RoutineRepository,
    private val ids: IdGenerator = UuidGenerator,
) : ViewModel() {
    val state: StateFlow<RoutinesUiState> = routines.observeRoutines().map { list ->
        RoutinesUiState(
            loaded = true,
            rows = list.map { detail ->
                val names = detail.items.map { it.exercise.name }
                RoutineRowUi(
                    id = detail.routine.id,
                    name = detail.routine.name,
                    summary = if (names.isEmpty()) "種目がありません" else "${names.size} 種目 · " + names.take(3).joinToString("、") + if (names.size > 3) " ほか" else "",
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    private val _created = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val created: SharedFlow<String> = _created.asSharedFlow()

    fun create(name: String) {
        viewModelScope.launch {
            val id = ids.next()
            routines.saveRoutine(Routine(id = id, name = name, sortOrder = routines.nextRoutineSortOrder()))
            _created.emit(id)
        }
    }
}

@Composable
fun RoutinesRoute(onOpenRoutine: (String) -> Unit) {
    val viewModel = appViewModel<RoutinesViewModel> { RoutinesViewModel(it.data.routines) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.created.collect(onOpenRoutine) }
    RoutinesScreen(state, onOpen = onOpenRoutine, onCreate = viewModel::create)
}

@Composable
fun RoutinesScreen(state: RoutinesUiState, onOpen: (String) -> Unit, onCreate: (String) -> Unit) {
    val colors = AppTheme.colors
    var creating by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("ルーティン")
        if (state.loaded && state.rows.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ルーティンはまだありません", style = AppTheme.typography.heading, color = colors.fg)
                Text(
                    "「Push」「Day A」のように、いつもの種目をまとめておくと、ワンタップで始められます。",
                    style = AppTheme.typography.body,
                    color = colors.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 72.dp)
                            .clickable(role = Role.Button) { onOpen(row.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, style = AppTheme.typography.heading, color = colors.fg)
                            Text(row.summary, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(AppIcons.Right, contentDescription = null, tint = colors.sub, modifier = Modifier.size(20.dp))
                    }
                    AppDivider()
                }
            }
        }
        PrimaryButton(
            "ルーティンを作る",
            onClick = { creating = true },
            icon = AppIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
    if (creating) {
        TextInputDialog(
            title = "新しいルーティン",
            initialText = "",
            label = "名前（例: Push、Day A）",
            onConfirm = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
}
