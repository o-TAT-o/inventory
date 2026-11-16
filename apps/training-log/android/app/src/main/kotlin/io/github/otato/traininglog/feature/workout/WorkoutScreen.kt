package io.github.otato.traininglog.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.traininglog.designsystem.components.AppBanner
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.NumberInputDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.ReorderHandle
import io.github.otato.traininglog.designsystem.components.ReorderableRow
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.components.rememberReorderState
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.ui.common.PreviousUi
import io.github.otato.traininglog.ui.common.RestAlert
import io.github.otato.traininglog.ui.common.appViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 記録画面。ViewModel を作り、状態と出来事（音・移動・取り消しの案内）を受け取って、[WorkoutScreen] に渡す。
 */
@Composable
fun WorkoutRoute(
    workoutId: String,
    onBack: () -> Unit,
    onAddExercise: (workoutId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onFinished: (workoutId: String) -> Unit,
) {
    val viewModel = appViewModel<WorkoutViewModel>(key = "workout-$workoutId") { container ->
        WorkoutViewModel(
            workoutId = workoutId,
            workouts = container.data.workouts,
            routines = container.data.routines,
            exercises = container.data.exercises,
            gearRepository = container.data.gear,
            settingsRepository = container.data.settings,
            addSetUseCase = container.addSet,
            clock = container.clock,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val formSheet by viewModel.formSheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val latest by rememberUpdatedState(state)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutEvent.RestFinished -> RestAlert.notify(context, latest.soundOn, latest.vibrationOn)
                // 案内は別に出す（出している間も、休憩の合図などの出来事を受け取り続ける）
                is WorkoutEvent.Undoable -> launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(event.message, actionLabel = "元に戻す", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
                is WorkoutEvent.Finished -> onFinished(event.workoutId)
                WorkoutEvent.Discarded, WorkoutEvent.Closed -> onBack()
            }
        }
    }

    WorkoutScreen(
        state = state,
        rest = rest,
        elapsed = elapsed,
        formSheet = formSheet,
        snackbar = snackbar,
        actions = WorkoutActions(
            onBack = onBack,
            onFinish = viewModel::finish,
            onAddExercise = { onAddExercise(workoutId) },
            onOpenExercise = onOpenExercise,
            onSelectSet = viewModel::selectSet,
            onDeleteSet = viewModel::deleteSet,
            onRemoveExercise = viewModel::removeExercise,
            onToggleExpanded = viewModel::toggleExpanded,
            onReorder = viewModel::reorderExercises,
            onChangeDate = viewModel::changeDate,
            onSetQuickEntry = viewModel::setQuickEntry,
            onToggleGear = viewModel::toggleGear,
            onAddGear = viewModel::addGear,
            onSetModes = viewModel::setModes,
            onSetMemo = viewModel::setExerciseMemo,
            onSetAdjustment = viewModel::setAdjustment,
            onOpenFormSheet = viewModel::openFormSheet,
            onCloseFormSheet = viewModel::closeFormSheet,
            onChooseForm = viewModel::chooseForm,
            onConfirmForm = viewModel::confirmForm,
            onAddForm = viewModel::addForm,
            onInput = viewModel::input,
        ),
        dockActions = DockActions(
            onPrevious = { viewModel.shiftExercise(-1) },
            onNext = { viewModel.shiftExercise(1) },
            onOpenExercise = onOpenExercise,
            onAdjust = viewModel::adjust,
            onFieldClick = {},
            onDetail = {},
            onAddSet = viewModel::addSet,
            onComplete = viewModel::complete,
            onFinish = viewModel::finish,
            onAdjustRest = viewModel::adjustRest,
            onDismissRest = viewModel::dismissRest,
        ),
    )
}

/** 記録画面でできる操作。画面は、状態を描いて、ここに操作を渡すだけにする。 */
data class WorkoutActions(
    val onBack: () -> Unit,
    val onFinish: () -> Unit,
    val onAddExercise: () -> Unit,
    val onOpenExercise: (exerciseId: String) -> Unit,
    val onSelectSet: (setId: String) -> Unit,
    val onDeleteSet: (setId: String) -> Unit,
    val onRemoveExercise: (workoutExerciseId: String) -> Unit,
    val onToggleExpanded: (workoutExerciseId: String) -> Unit,
    val onReorder: (orderedWorkoutExerciseIds: List<String>) -> Unit,
    val onChangeDate: (LocalDate) -> Unit,
    val onSetQuickEntry: (Boolean) -> Unit,
    val onToggleGear: (workoutExerciseId: String, gearId: String) -> Unit,
    val onAddGear: (workoutExerciseId: String, name: String) -> Unit,
    val onSetModes: (workoutExerciseId: String, weight: WeightMode, rep: RepMode) -> Unit,
    val onSetMemo: (workoutExerciseId: String, memo: String) -> Unit,
    val onSetAdjustment: (workoutExerciseId: String, fieldId: String, value: String?) -> Unit,
    val onOpenFormSheet: (workoutExerciseId: String) -> Unit,
    val onCloseFormSheet: () -> Unit,
    val onChooseForm: (exerciseId: String) -> Unit,
    val onConfirmForm: (workoutExerciseId: String, exerciseId: String) -> Unit,
    val onAddForm: (workoutExerciseId: String, name: String) -> Unit,
    val onInput: (FieldKey, Double) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    rest: RestUi?,
    elapsed: String,
    formSheet: FormSheetUi?,
    snackbar: SnackbarHostState,
    actions: WorkoutActions,
    dockActions: DockActions,
) {
    var editingField by remember { mutableStateOf<FieldUi?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    // 種目をドラッグして並べ替えている間（見出しだけにたたみ、帯を出し、下のパネルを薄くする）
    var dragging by remember { mutableStateOf(false) }

    if (state.missing) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("この記録は見つかりません", style = AppTheme.typography.heading, color = AppTheme.colors.fg)
                OutlineButton("戻る", onClick = actions.onBack)
            }
        }
        return
    }

    val finish = { if (state.hasIncompleteSets) showFinishConfirm = true else actions.onFinish() }
    val dock = dockActions.copy(
        onFieldClick = { editingField = it },
        onDetail = { showDetail = true },
        onFinish = finish,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        WorkoutTopBar(
            state = state,
            elapsed = elapsed,
            onBack = actions.onBack,
            onDateClick = { showDateSheet = true },
            onFinish = finish,
        )
        if (state.quickEntry) {
            AppBanner(
                text = "あとから入力",
                icon = AppIcons.Clock,
                detail = "タイマーなし",
                actionText = "ふつうに記録",
                onAction = { actions.onSetQuickEntry(false) },
            )
        }
        if (dragging) {
            AppBanner(text = "並べ替え中", icon = AppIcons.Drag, detail = "指を離すと、そこに置きます")
        }
        state.routineMemo?.let { if (!dragging) MemoRow(it) }

        Box(modifier = Modifier.weight(1f)) {
            ExerciseList(state = state, actions = actions, onDraggingChange = { dragging = it })
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }

        WorkoutDock(
            dock = state.dock,
            allDone = state.allDone,
            summaryText = state.summaryText,
            rest = rest,
            actions = dock,
            // 並べ替え中は、下のパネルは使えない
            modifier = Modifier.alpha(if (dragging) 0.45f else 1f),
        )
    }

    editingField?.let { field ->
        NumberInputDialog(
            title = when (field.key) {
                FieldKey.Weight -> "重量"
                FieldKey.Reps -> "回数"
                FieldKey.Duration -> if (field.unitText == "秒") "時間（秒）" else "時間（分）"
                FieldKey.Distance -> "距離（km）"
            },
            initialText = field.inputText,
            unitText = field.unitText,
            allowDecimal = field.allowDecimal,
            onConfirm = {
                actions.onInput(field.key, it)
                editingField = null
            },
            onDismiss = { editingField = null },
        )
    }

    if (showDetail) {
        state.dock?.detail?.let { detail ->
            ExerciseDetailSheet(
                detail = detail,
                onDismiss = { showDetail = false },
                onToggleGear = { actions.onToggleGear(detail.workoutExerciseId, it) },
                onAddGear = { actions.onAddGear(detail.workoutExerciseId, it) },
                onSetAdjustment = { fieldId, value -> actions.onSetAdjustment(detail.workoutExerciseId, fieldId, value) },
                onSetModes = { w, r -> actions.onSetModes(detail.workoutExerciseId, w, r) },
                onSetMemo = { actions.onSetMemo(detail.workoutExerciseId, it) },
            )
        } ?: run { showDetail = false }
    }

    formSheet?.let { sheet ->
        FormSheet(
            ui = sheet,
            onChoose = actions.onChooseForm,
            onConfirm = { sheet.selectedId?.let { actions.onConfirmForm(sheet.workoutExerciseId, it) } },
            onAddForm = { actions.onAddForm(sheet.workoutExerciseId, it) },
            onDismiss = actions.onCloseFormSheet,
        )
    }

    if (showDateSheet) {
        DateModeSheet(
            dateLabel = state.dateLabel ?: state.dateTitle,
            quickEntry = state.quickEntry,
            canQuickEntry = !state.isToday,
            onPickDate = {
                showDateSheet = false
                showDatePicker = true
            },
            onSetQuickEntry = actions.onSetQuickEntry,
            onDismiss = { showDateSheet = false },
        )
    }

    if (showFinishConfirm) {
        ConfirmDialog(
            title = "ワークアウトを終えますか？",
            message = "まだ完了していないセットは、記録に含まれません。",
            confirmText = "終える",
            onConfirm = {
                showFinishConfirm = false
                actions.onFinish()
            },
            onDismiss = { showFinishConfirm = false },
        )
    }

    if (showDatePicker) {
        val initial = (state.date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            actions.onChangeDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showDatePicker = false
                    },
                ) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * 画面の上の帯。
 * - ふつう: 戻る・題名・（今日以外なら日付のチップ）・経過時間・終える（C2n）
 * - あとから入力: 戻る・日付（タップで日付と記録のしかたを変える）・終える。経過時間は出さない（C2j）
 */
@Composable
private fun WorkoutTopBar(
    state: WorkoutUiState,
    elapsed: String,
    onBack: () -> Unit,
    onDateClick: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(AppIcons.Back, "戻る", onBack)
        if (state.quickEntry) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onDateClick)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.dateTitle, style = AppTheme.typography.title, color = colors.fg, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Icon(AppIcons.Down, contentDescription = "日付と記録のしかたを変える", tint = colors.fg, modifier = Modifier.size(20.dp))
            }
        } else {
            Row(modifier = Modifier.weight(1f).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.title, style = AppTheme.typography.title, color = colors.fg, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (state.dateLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    DateChip(state.dateLabel, onClick = onDateClick)
                }
            }
            Text(elapsed, style = AppTheme.typography.timer, color = colors.sub)
        }
        TextAction(if (state.finished) "とじる" else "終える", onClick = onFinish, color = colors.primary)
    }
}

/** 「11/14（土） ⌄」。今日以外の日付の記録だと分かるように、淡い面のチップで出す（F-WO-20）。 */
@Composable
private fun DateChip(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.typography.bodyStrong, color = colors.onContainer, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(AppIcons.Down, contentDescription = "日付と記録のしかたを変える", tint = colors.onContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MemoRow(memo: String) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.container)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "メモ: $memo",
            style = AppTheme.typography.caption,
            color = colors.onContainer,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) AppIcons.Up else AppIcons.Down,
            contentDescription = if (expanded) "メモをとじる" else "メモをひらく",
            tint = colors.onContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 種目の一覧。1 つの種目（見出しとセット）が 1 行で、つまみ（≡）を持って動かすと並べ替えられる（F-WO-13、F-UI-01）。
 * 動かしている間は、全部の種目を見出しだけにたたむので、長いワークアウトでも端から端へ動かせる（F-UI-02）。
 */
@Composable
private fun ExerciseList(
    state: WorkoutUiState,
    actions: WorkoutActions,
    onDraggingChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        source = state.exercises,
        key = { it.workoutExerciseId },
        listState = listState,
        onCommit = { actions.onReorder(it.map { exercise -> exercise.workoutExerciseId }) },
    )
    val dragging = reorder.isDragging
    LaunchedEffect(dragging) { onDraggingChange(dragging) }

    // 入力するセットが変わったら、そのセットが見える位置までスクロールする。
    // まず、その種目の行までスクロールし（見えていなければ）、行の中のセットは、セットの行が自分で画面に入れる。
    val currentSetId = state.dock?.setId
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSetId) {
        scrollTarget = currentSetId
        if (currentSetId == null) return@LaunchedEffect
        val index = reorder.items.indexOfFirst { exercise -> exercise.sets.any { it.id == currentSetId } }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.animateScrollToItem(index)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp),
    ) {
        items(reorder.items, key = { it.workoutExerciseId }) { exercise ->
            ReorderableRow(reorder, key = exercise.workoutExerciseId) { handle, isDragged ->
                ExerciseBlock(
                    exercise = exercise,
                    compact = dragging,
                    isDragged = isDragged,
                    handle = handle,
                    scrollTarget = scrollTarget,
                    onScrolled = { scrollTarget = null },
                    actions = actions,
                )
            }
        }
        item(key = "add-exercise") {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlineButton(
                    text = "種目を追加",
                    onClick = actions.onAddExercise,
                    icon = AppIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 1 つの種目。ふつうは見出しとセットの行、完了していれば 1 行にたたみ、並べ替え中は見出しだけ。
 *
 * つまみ（≡）は、どの見た目でも同じ場所に 1 つだけ置く。見た目ごとに別のつまみを置くと、
 * ドラッグを始めて見出しだけにたたんだ瞬間に、押していたつまみが作り直されて、ドラッグが切れてしまう。
 */
@Composable
private fun ExerciseBlock(
    exercise: ExerciseUi,
    compact: Boolean,
    isDragged: Boolean,
    handle: Modifier,
    scrollTarget: String?,
    onScrolled: () -> Unit,
    actions: WorkoutActions,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val expanded = !compact && !exercise.collapsed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 持ち上げている種目は、枠で囲んで、浮いて見せる
            .then(
                if (isDragged) Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(shape).background(colors.surface).border(2.dp, colors.primary, shape)
                else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = if (expanded) 0.dp else 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ReorderHandle(handle)
            Box(modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    compact -> CompactHeader(exercise)
                    exercise.collapsed -> CollapsedRow(exercise, onToggle = { actions.onToggleExpanded(exercise.workoutExerciseId) })
                    else -> ExerciseHeader(
                        exercise = exercise,
                        onOpenExercise = { actions.onOpenExercise(exercise.exerciseId) },
                        onOpenForm = { actions.onOpenFormSheet(exercise.workoutExerciseId) },
                        onRemove = { actions.onRemoveExercise(exercise.workoutExerciseId) },
                    )
                }
            }
        }
        if (expanded) {
            exercise.sets.forEach { set ->
                SwipeableSetRow(
                    set = set,
                    scrollRequested = set.id == scrollTarget,
                    onScrolled = onScrolled,
                    onClick = { actions.onSelectSet(set.id) },
                    onDelete = { actions.onDeleteSet(set.id) },
                )
                set.pbNote?.let { PbNote(it) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!isDragged) AppDivider()
    }
}

/** 並べ替え中の見出し。「ラットプルダウン　マシン · 目標 3 セット」（つまみは [ExerciseBlock] が置く） */
@Composable
private fun CompactHeader(exercise: ExerciseUi) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
        Text(exercise.name, style = AppTheme.typography.heading, color = colors.fg, maxLines = 1)
        Text(exercise.dragMeta, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1)
    }
}

/** すべて完了した種目の 1 行。「ベンチプレス　62.5 kg 合計 × 10 · 9 · 9　[自己ベスト] ✓ 3/3」。タップで開く。 */
@Composable
private fun CollapsedRow(exercise: ExerciseUi, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "${exercise.name}のセットを開く", onClick = onToggle)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(exercise.name, style = AppTheme.typography.heading, color = colors.fg, maxLines = 1)
            if (exercise.summary.isNotEmpty()) {
                Text(exercise.summary, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1)
            }
        }
        if (exercise.hasPersonalBest) {
            PbBadge()
            Spacer(Modifier.width(8.dp))
        }
        Icon(AppIcons.Check, contentDescription = "完了", tint = colors.sub, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("${exercise.doneCount}/${exercise.totalCount}", style = AppTheme.typography.caption, color = colors.sub)
    }
}

@Composable
private fun ExerciseHeader(
    exercise: ExerciseUi,
    onOpenExercise: () -> Unit,
    onOpenForm: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onOpenExercise),
            ) {
                Text(exercise.name, style = AppTheme.typography.heading, color = colors.fg)
                if (exercise.meta.isNotEmpty()) {
                    Text(exercise.meta, style = AppTheme.typography.caption, color = colors.sub)
                }
            }
            exercise.formLabel?.let { label ->
                // フォームを選べる種目。まだ選んでいないときは、目立つ色にして、タップすると選べる（F-RT-10）
                Text(
                    "$label ›",
                    style = AppTheme.typography.caption,
                    color = if (exercise.formUnselected) colors.primary else colors.sub,
                    fontWeight = if (exercise.formUnselected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onOpenForm)
                        .padding(vertical = 6.dp),
                )
            }
            exercise.previous?.let { PreviousLine(it, chevron = true, modifier = Modifier.clickable(role = Role.Button, onClick = onOpenExercise)) }
        }
        ExerciseMenu(exercise = exercise, onOpenForm = onOpenForm, onOpenDetail = onOpenExercise, onRemove = onRemove)
    }
}

/** 種目の「⋮」メニュー。フォームを選ぶ・種目の詳細・外す（外したあとは、元に戻せる）。 */
@Composable
private fun ExerciseMenu(exercise: ExerciseUi, onOpenForm: () -> Unit, onOpenDetail: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(AppIcons.More, "${exercise.name}のメニュー", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (exercise.formLabel != null) "フォームを選ぶ" else "フォームを追加") },
                onClick = {
                    open = false
                    onOpenForm()
                },
            )
            DropdownMenuItem(
                text = { Text("種目の詳細") },
                onClick = {
                    open = false
                    onOpenDetail()
                },
            )
            DropdownMenuItem(
                text = { Text("外す", color = AppTheme.colors.error) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** 「前回 11/7　60 kg 〔合計〕× 10 · 9 · 8 ›」。[chevron] を true にすると、タップできることを示す › が付く。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviousLine(previous: PreviousUi, modifier: Modifier = Modifier, chevron: Boolean = false) {
    val colors = AppTheme.colors
    val line = previous.line
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${previous.label}　${line.weightText}".trimEnd(),
            style = AppTheme.typography.caption,
            color = colors.sub,
        )
        line.tag?.let { Tag(it) }
        Text(line.repsText + if (chevron) " ›" else "", style = AppTheme.typography.caption, color = colors.sub)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSetRow(
    set: SetRowUi,
    scrollRequested: Boolean,
    onScrolled: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    // 元に戻したとき、同じ行（同じ id）が「削除済み」の状態のまま復元されないように、出てきたら戻す
    LaunchedEffect(Unit) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(colors.error).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(AppIcons.Trash, contentDescription = "削除", tint = colors.surface)
            }
        },
    ) {
        SetRow(set = set, scrollRequested = scrollRequested, onScrolled = onScrolled, onClick = onClick)
    }
}

@Composable
private fun SetRow(set: SetRowUi, scrollRequested: Boolean, onScrolled: () -> Unit, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val requester = remember { BringIntoViewRequester() }
    // 下のパネルで入力するセットになったら、画面の中に入れる（種目の行が長いときも見える）
    LaunchedEffect(scrollRequested) {
        if (scrollRequested) {
            requester.bringIntoView()
            onScrolled()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .defaultMinSize(minHeight = 52.dp)
            .bringIntoViewRequester(requester)
            .clip(shape)
            .then(if (set.isCurrent) Modifier.background(colors.container) else Modifier.border(1.dp, colors.line.copy(alpha = if (set.completed) 0f else 1f), shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            set.number.toString(),
            style = AppTheme.typography.bodyStrong,
            color = if (set.isCurrent) colors.onContainer else colors.sub,
            modifier = Modifier.width(28.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val textColor = if (set.isCurrent) colors.onContainer else if (set.completed) colors.fg else colors.sub
            if (set.weightText.isNotEmpty()) {
                Text(set.weightText, style = AppTheme.typography.bodyStrong, color = textColor, fontWeight = FontWeight.Bold)
                set.weightTag?.let { Tag(it) }
            }
            Text(set.repsText, style = AppTheme.typography.bodyStrong, color = textColor, fontWeight = FontWeight.Bold)
        }
        if (set.isPersonalBest) {
            PbBadge()
            Spacer(Modifier.width(8.dp))
        }
        when {
            set.completed -> Icon(AppIcons.Check, contentDescription = "完了", tint = colors.primary, modifier = Modifier.size(22.dp))
            set.isCurrent -> Text("入力中", style = AppTheme.typography.caption, color = colors.onContainer, fontWeight = FontWeight.Bold)
            else -> Box(Modifier.size(20.dp).border(1.5.dp, colors.line, CircleShape))
        }
    }
}

/** 「★ 自己ベスト」の印。 */
@Composable
private fun PbBadge() {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.pb.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Star, contentDescription = null, tint = colors.pb, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text("自己ベスト", style = AppTheme.typography.caption, color = colors.pb, fontWeight = FontWeight.Bold)
    }
}

/** 自己ベストのセットの下の説明。「☆ 最大重量の更新　これまで 62.5 kg 合計」 */
@Composable
private fun PbNote(text: String) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Star, contentDescription = null, tint = colors.pb, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = AppTheme.typography.caption, color = colors.sub)
    }
}
