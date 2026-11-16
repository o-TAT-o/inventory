package io.github.otato.traininglog.feature.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.CompactStepper
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.ReorderHandle
import io.github.otato.traininglog.designsystem.components.ReorderableRow
import io.github.otato.traininglog.designsystem.components.rememberReorderState
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineDetail
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.RoutineItemDetail
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatMmSs
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineEditUiState(
    val loaded: Boolean = false,
    val detail: RoutineDetail? = null,
    val settings: AppSettings = AppSettings(),
)

sealed interface RoutineEditEvent {
    data class ItemRemoved(val exerciseName: String) : RoutineEditEvent
    data object ItemsReordered : RoutineEditEvent
}

/** ルーティンの編集（F-RT-01〜03、F-RT-08、F-RT-11、F-RT-12）。 */
class RoutineEditViewModel(
    private val routineId: String,
    private val routines: RoutineRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<RoutineEditUiState> =
        combine(routines.observeRoutine(routineId), settings.observe()) { detail, s ->
            RoutineEditUiState(loaded = true, detail = detail, settings = s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditUiState())

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    private val _events = MutableSharedFlow<RoutineEditEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RoutineEditEvent> = _events.asSharedFlow()

    /** 外した種目（元に戻すために持つ）と、並べ替える前の並び。どちらか一方だけを持つ。 */
    private var removedItem: RoutineItem? = null
    private var previousOrder: List<String>? = null

    /**
     * 種目の目標の保存は、打った順に 1 つずつ行う。＋ − を長押しすると、短い間に何度も保存するので、
     * 別々に動かすと、古い値があとから書き込まれてしまうことがある。
     */
    private val itemSaveQueue = Channel<RoutineItem>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (item in itemSaveQueue) routines.saveItem(item)
        }
    }

    fun rename(name: String) = update { it.copy(name = name) }

    fun setMemo(memo: String) = update { it.copy(memo = memo) }

    private fun update(transform: (Routine) -> Routine) {
        val current = state.value.detail?.routine ?: return
        viewModelScope.launch { routines.saveRoutine(transform(current)) }
    }

    fun saveItem(item: RoutineItem) {
        itemSaveQueue.trySend(item)
    }

    /** 種目をルーティンから外す。直後なら、元に戻せる（F-UI-03）。 */
    fun removeItem(itemId: String) {
        viewModelScope.launch {
            // 画面の状態ではなく、いまの保存内容から取る（画面がまだ読み込み前でも、外す操作は必ず効かせる）
            val target = routines.observeRoutine(routineId).first()?.items?.firstOrNull { it.item.id == itemId }
            removedItem = target?.item
            previousOrder = null
            routines.deleteItem(itemId)
            if (target != null) _events.emit(RoutineEditEvent.ItemRemoved(target.exercise.name))
        }
    }

    /** 種目の並びを変える（F-RT-02）。直後なら、元に戻せる。 */
    fun reorderItems(orderedIds: List<String>) {
        viewModelScope.launch {
            previousOrder = routines.observeRoutine(routineId).first()?.items?.map { it.item.id }
            removedItem = null
            routines.reorderItems(routineId, orderedIds)
            _events.emit(RoutineEditEvent.ItemsReordered)
        }
    }

    /** 直前の「外す」「並べ替え」を、元に戻す。 */
    fun undo() {
        val removed = removedItem
        val order = previousOrder
        removedItem = null
        previousOrder = null
        viewModelScope.launch {
            if (removed != null) routines.saveItem(removed)
            if (order != null) routines.reorderItems(routineId, order)
        }
    }

    fun delete() {
        viewModelScope.launch {
            routines.deleteRoutine(routineId)
            _deleted.emit(Unit)
        }
    }
}

/**
 * @param newItemId 種目選択で、いま足したばかりの種目（ルーティンの種目）の id。あれば、その種目の目標をすぐ入れられるように開く
 * @param onNewItemShown [newItemId] を開いたあとに呼ぶ（同じ種目を、何度も開かないように、受け取り口を空にする）
 */
@Composable
fun RoutineEditRoute(
    routineId: String,
    newItemId: String?,
    onNewItemShown: () -> Unit,
    onBack: () -> Unit,
    onAddExercise: (String) -> Unit,
) {
    val viewModel = appViewModel<RoutineEditViewModel>(key = "routine-$routineId") {
        RoutineEditViewModel(routineId, it.data.routines, it.data.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            val message = when (event) {
                is RoutineEditEvent.ItemRemoved -> "${event.exerciseName}を外しました"
                RoutineEditEvent.ItemsReordered -> "並べ替えました"
            }
            if (snackbar.showSnackbar(message, actionLabel = "元に戻す") == SnackbarResult.ActionPerformed) viewModel.undo()
        }
    }
    RoutineEditScreen(
        state = state,
        snackbar = snackbar,
        onReorderItems = viewModel::reorderItems,
        newItemId = newItemId,
        onNewItemShown = onNewItemShown,
        onBack = onBack,
        onRename = viewModel::rename,
        onMemo = viewModel::setMemo,
        onAddExercise = { onAddExercise(routineId) },
        onSaveItem = viewModel::saveItem,
        onRemoveItem = viewModel::removeItem,
        onDelete = viewModel::delete,
    )
}

/**
 * ルーティンの編集。種目は、カードで並べ、カードを開くと、その場で目標（セット数・重量・回数）と休憩を決められる
 * （デザインの「ルーティンの編集」）。種目を足した直後は、足した種目のカードが開いている。
 */
@Composable
fun RoutineEditScreen(
    state: RoutineEditUiState,
    snackbar: SnackbarHostState,
    onReorderItems: (List<String>) -> Unit,
    newItemId: String?,
    onNewItemShown: () -> Unit,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onMemo: (String) -> Unit,
    onAddExercise: () -> Unit,
    onSaveItem: (RoutineItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val detail = state.detail
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var expandedId by remember { mutableStateOf<String?>(null) }
    val itemIds = detail?.items?.map { it.item.id }.orEmpty()
    // 先頭の 1 つ（名前とメモの欄）は、並べ替えの対象ではない
    val reorder = rememberReorderState(
        source = detail?.items.orEmpty(),
        key = { it.item.id },
        listState = listState,
        leadingItems = 1,
        onCommit = { onReorderItems(it.map { d -> d.item.id }) },
    )

    // 種目選択で足した種目は、すぐ目標を入れられるように、カードを開く
    LaunchedEffect(newItemId, itemIds) {
        if (newItemId != null && newItemId in itemIds) {
            expandedId = newItemId
            onNewItemShown()
        }
    }
    // 開いたカードが見えるところまで、一覧をスクロールする（先頭は、名前とメモの欄）
    LaunchedEffect(expandedId) {
        val index = itemIds.indexOf(expandedId ?: return@LaunchedEffect)
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = detail?.routine?.name ?: "ルーティン",
            onBack = onBack,
            actions = { if (detail != null) AppIconButton(AppIcons.Trash, "ルーティンを削除", { confirmDelete = true }) },
        )
        if (detail == null) return@Column
        var name by remember(detail.routine.id) { mutableStateOf(detail.routine.name) }
        var memo by remember(detail.routine.id) { mutableStateOf(detail.routine.memo) }
        Box(modifier = Modifier.weight(1f)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) onRename(it.trim())
                        },
                        label = { Text("名前") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = memo,
                        onValueChange = {
                            memo = it
                            onMemo(it)
                        },
                        label = { Text("メモ（記録画面で見られます）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    SectionLabel("種目")
                }
            }
            items(reorder.items, key = { it.item.id }) { itemDetail ->
                ReorderableRow(reorder, key = itemDetail.item.id) { handle, _ ->
                    RoutineItemCard(
                        detail = itemDetail,
                        settings = state.settings,
                        expanded = expandedId == itemDetail.item.id,
                        handle = handle,
                        onToggle = { expandedId = if (expandedId == itemDetail.item.id) null else itemDetail.item.id },
                        onChange = onSaveItem,
                        onRemove = {
                            if (expandedId == itemDetail.item.id) expandedId = null
                            onRemoveItem(itemDetail.item.id)
                        },
                    )
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        // 種目を足す操作は、画面の下に固定する（一覧が長くなっても、いつも押せる）
        Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface)) {
            AppDivider()
            OutlineButton(
                "種目を追加",
                onClick = onAddExercise,
                icon = AppIcons.Plus,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "このルーティンを削除しますか？",
            message = "これまでの記録は消えません。",
            confirmText = "削除",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * 種目のカード。閉じているときは、目標のまとめを出し、開くと、セット数・重量・回数・休憩を、その場で決められる。
 * 値は、変えるたびにその場で保存する。空欄のところは、前回の記録や、設定の休憩時間が使われる。
 */
@Composable
private fun RoutineItemCard(
    detail: RoutineItemDetail,
    settings: AppSettings,
    expanded: Boolean,
    handle: Modifier,
    onToggle: () -> Unit,
    onChange: (RoutineItem) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    val exercise = detail.exercise
    // 打った値は、保存を待たずに、すぐこのカードに出す（＋ を長押ししている間も、続けて増えるように）
    var item by remember(detail.item.id) { mutableStateOf(detail.item) }
    fun change(next: RoutineItem) {
        item = next
        onChange(next)
    }
    val stepKg = exercise.weightStepKg ?: WeightUnits.fromDisplay(settings.weightStep, settings.weightUnit)
    val summary = targetLine(item, exercise, settings)

    AppCard(cornerRadius = 16.dp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReorderHandle(handle)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(exercise.name, style = AppTheme.typography.bodyStrong, color = colors.fg)
                when {
                    expanded -> Text(
                        "${exercise.equipment.label()} · 目標と休憩を設定中",
                        style = AppTheme.typography.caption,
                        color = colors.sub,
                    )
                    summary == null -> Text(
                        "${exercise.equipment.label()} · 目標を決める",
                        style = AppTheme.typography.caption,
                        color = colors.primary,
                    )
                    else -> Text(
                        "${exercise.equipment.label()} · $summary",
                        style = AppTheme.typography.caption,
                        color = colors.sub,
                    )
                }
                if (!expanded) {
                    Text(restLine(item), style = AppTheme.typography.caption, color = colors.sub)
                }
            }
            // 開け閉めの矢印。タップできることが分かるように、いつも出す（見出しの行のどこを押しても、開け閉めできる）
            AppIconButton(
                icon = if (expanded) AppIcons.Up else AppIcons.Down,
                contentDescription = if (expanded) "${exercise.name}の目標をとじる" else "${exercise.name}の目標をひらく",
                onClick = onToggle,
                tint = colors.sub,
            )
        }
        if (expanded) {
            AppDivider()
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
                if (exercise.usesWeight) {
                    OptionalRow(
                        label = "重量（${WeightUnits.label(settings.weightUnit)}）",
                        valueText = item.targetWeightKg?.let { WeightUnits.formatKg(it, settings.weightUnit) },
                        onMinus = { change(item.copy(targetWeightKg = ((item.targetWeightKg ?: 0.0) - stepKg).coerceAtLeast(0.0))) },
                        onPlus = { change(item.copy(targetWeightKg = (item.targetWeightKg ?: 0.0) + stepKg)) },
                        onClear = { change(item.copy(targetWeightKg = null)) },
                    )
                }
                OptionalRow(
                    label = "セット数",
                    valueText = item.targetSets?.toString(),
                    onMinus = { change(item.copy(targetSets = ((item.targetSets ?: 3) - 1).coerceAtLeast(1))) },
                    onPlus = { change(item.copy(targetSets = ((item.targetSets ?: 2) + 1).coerceAtMost(20))) },
                    onClear = { change(item.copy(targetSets = null)) },
                )
                if (exercise.usesReps) {
                    OptionalRow(
                        label = "回数",
                        valueText = item.targetReps?.toString(),
                        onMinus = { change(item.copy(targetReps = ((item.targetReps ?: 10) - 1).coerceAtLeast(1))) },
                        onPlus = { change(item.copy(targetReps = ((item.targetReps ?: 9) + 1).coerceAtMost(100))) },
                        onClear = { change(item.copy(targetReps = null)) },
                    )
                }
                OptionalRow(
                    label = "休憩（セット間）",
                    valueText = item.restSeconds?.let { formatMmSs(it.toLong()) },
                    onMinus = { change(item.copy(restSeconds = ((item.restSeconds ?: settings.restBetweenSetsSec) - 10).coerceAtLeast(10))) },
                    onPlus = { change(item.copy(restSeconds = (item.restSeconds ?: settings.restBetweenSetsSec) + 10)) },
                    onClear = { change(item.copy(restSeconds = null)) },
                )
                OptionalRow(
                    label = "休憩（種目のあと）",
                    valueText = item.restAfterSeconds?.let { formatMmSs(it.toLong()) },
                    onMinus = { change(item.copy(restAfterSeconds = ((item.restAfterSeconds ?: settings.restAfterExerciseSec) - 10).coerceAtLeast(10))) },
                    onPlus = { change(item.copy(restAfterSeconds = (item.restAfterSeconds ?: settings.restAfterExerciseSec) + 10)) },
                    onClear = { change(item.copy(restAfterSeconds = null)) },
                )
                Text(
                    "空欄（—）のところは、前回の記録や、設定の休憩時間が使われます。＋ − は、押し続けると速く変わります",
                    style = AppTheme.typography.caption,
                    color = colors.sub,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
                // 文字ボタンの内側の余白の分だけ左へ寄せて、上の題名と左端を揃える
                TextAction("この種目をルーティンから外す", onClick = onRemove, color = colors.error, modifier = Modifier.offset(x = (-10).dp))
            }
        }
    }
}

/** 「3 セット × 55 kg 合計 × 12 回」。目標が 1 つもなければ null。 */
private fun targetLine(item: RoutineItem, exercise: Exercise, settings: AppSettings): String? {
    val sets = item.targetSets?.let { "$it セット" }
    val weight = item.targetWeightKg?.takeIf { exercise.usesWeight }?.let {
        "${WeightUnits.formatKg(it, settings.weightUnit)} ${WeightUnits.label(settings.weightUnit)} ${exercise.weightMode.tag(exercise.equipment)}"
    }
    val reps = item.targetReps?.takeIf { exercise.usesReps }?.let { "$it 回" + (exercise.repMode.tag()?.let { tag -> " $tag" } ?: "") }
    return listOfNotNull(sets, weight, reps).takeIf { it.isNotEmpty() }?.joinToString(" × ")
}

/** 「休憩 1:30 · 種目のあと 2:00」。決めていなければ「休憩は設定のとおり」。 */
private fun restLine(item: RoutineItem): String {
    val parts = listOfNotNull(
        item.restSeconds?.let { "休憩 ${formatMmSs(it.toLong())}" },
        item.restAfterSeconds?.let { "種目のあと ${formatMmSs(it.toLong())}" },
    )
    return if (parts.isEmpty()) "休憩は設定のとおり" else parts.joinToString(" · ")
}

/**
 * 値を空欄にできる、＋ − の行。値がなければ「—」を出す。値があるときは、題名の下に「空欄に戻す」を出す
 * （幅が狭い画面でも、題名が折り返されないように、ボタンは並べない）。
 */
@Composable
private fun OptionalRow(
    label: String,
    valueText: String?,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg)
            if (valueText != null) {
                Text(
                    "空欄に戻す",
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.primary,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clickable(role = Role.Button, onClick = onClear)
                        .padding(top = 6.dp, end = 12.dp),
                )
            }
        }
        CompactStepper(
            valueText = valueText ?: "—",
            onMinus = onMinus,
            onPlus = onPlus,
            minusLabel = "$label を減らす",
            plusLabel = "$label を増やす",
        )
    }
}
