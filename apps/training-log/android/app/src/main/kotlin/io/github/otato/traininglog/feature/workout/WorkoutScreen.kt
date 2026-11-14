package io.github.otato.traininglog.feature.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.ConfirmDialog
import io.github.otato.traininglog.designsystem.components.NumberInputDialog
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.ui.common.PreviousUi
import io.github.otato.traininglog.ui.common.RestAlert
import io.github.otato.traininglog.ui.common.appViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 記録画面。ViewModel を作り、状態と出来事（音・移動）を受け取って、[WorkoutScreen] に渡す。
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
            gearRepository = container.data.gear,
            settingsRepository = container.data.settings,
            addSetUseCase = container.addSet,
            clock = container.clock,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val latest by rememberUpdatedState(state)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutEvent.RestFinished -> RestAlert.notify(context, latest.soundOn, latest.vibrationOn)
                is WorkoutEvent.SetDeleted -> {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(event.message, actionLabel = "元に戻す")
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
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
        snackbar = snackbar,
        onBack = onBack,
        onFinish = viewModel::finish,
        onAddExercise = { onAddExercise(workoutId) },
        onOpenExercise = onOpenExercise,
        onSelectSet = viewModel::selectSet,
        onDeleteSet = viewModel::deleteSet,
        onRemoveExercise = viewModel::removeExercise,
        onChangeDate = viewModel::changeDate,
        onToggleGear = viewModel::toggleGear,
        onSetModes = viewModel::setModes,
        onSetMemo = viewModel::setExerciseMemo,
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
        onInput = viewModel::input,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    rest: RestUi?,
    elapsed: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onSelectSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onChangeDate: (LocalDate) -> Unit,
    onToggleGear: (workoutExerciseId: String, gearId: String) -> Unit,
    onSetModes: (workoutExerciseId: String, weight: io.github.otato.traininglog.domain.model.WeightMode, rep: io.github.otato.traininglog.domain.model.RepMode) -> Unit,
    onSetMemo: (workoutExerciseId: String, memo: String) -> Unit,
    dockActions: DockActions,
    onInput: (FieldKey, Double) -> Unit,
) {
    var editingField by remember { mutableStateOf<FieldUi?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ExerciseUi?>(null) }

    if (state.missing) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("この記録は見つかりません", style = AppTheme.typography.heading, color = AppTheme.colors.fg)
                OutlineButton("戻る", onClick = onBack)
            }
        }
        return
    }

    val actions = dockActions.copy(
        onFieldClick = { editingField = it },
        onDetail = { showDetail = true },
        onFinish = { if (state.hasIncompleteSets) showFinishConfirm = true else onFinish() },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        WorkoutTopBar(
            title = state.title,
            elapsed = elapsed,
            dateLabel = state.dateLabel,
            finished = state.finished,
            onBack = onBack,
            onDateClick = { showDatePicker = true },
            onFinish = { if (state.hasIncompleteSets) showFinishConfirm = true else onFinish() },
        )
        state.routineMemo?.let { MemoRow(it) }

        Box(modifier = Modifier.weight(1f)) {
            ExerciseList(
                state = state,
                onSelectSet = onSelectSet,
                onDeleteSet = onDeleteSet,
                onOpenExercise = onOpenExercise,
                onLongPressExercise = { removeTarget = it },
                onAddExercise = onAddExercise,
            )
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }

        WorkoutDock(
            dock = state.dock,
            allDone = state.allDone,
            summaryText = state.summaryText,
            rest = rest,
            actions = actions,
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
                onInput(field.key, it)
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
                onToggleGear = { onToggleGear(detail.workoutExerciseId, it) },
                onSetModes = { w, r -> onSetModes(detail.workoutExerciseId, w, r) },
                onSetMemo = { onSetMemo(detail.workoutExerciseId, it) },
            )
        } ?: run { showDetail = false }
    }

    if (showFinishConfirm) {
        ConfirmDialog(
            title = "ワークアウトを終えますか？",
            message = "まだ完了していないセットは、記録に含まれません。",
            confirmText = "終える",
            onConfirm = {
                showFinishConfirm = false
                onFinish()
            },
            onDismiss = { showFinishConfirm = false },
        )
    }

    removeTarget?.let { target ->
        ConfirmDialog(
            title = "この種目を外しますか？",
            message = "「${target.name}」と、入力したセットが消えます。",
            confirmText = "外す",
            destructive = true,
            onConfirm = {
                onRemoveExercise(target.workoutExerciseId)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
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
                            onChangeDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
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

@Composable
private fun WorkoutTopBar(
    title: String,
    elapsed: String,
    dateLabel: String?,
    finished: Boolean,
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
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = AppTheme.typography.title, color = colors.fg, maxLines = 1)
            if (dateLabel != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onDateClick)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Calendar, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$dateLabel の記録", style = AppTheme.typography.caption, color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(elapsed, style = AppTheme.typography.timer, color = colors.sub)
        TextAction(if (finished) "とじる" else "終える", onClick = onFinish, color = colors.primary)
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

@Composable
private fun ExerciseList(
    state: WorkoutUiState,
    onSelectSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onLongPressExercise: (ExerciseUi) -> Unit,
    onAddExercise: () -> Unit,
) {
    val listState = rememberLazyListState()

    // 入力するセットが変わったら、そのセットが見える位置までスクロールする
    val currentSetId = state.dock?.setId
    LaunchedEffect(currentSetId) {
        if (currentSetId == null) return@LaunchedEffect
        var index = 0
        for (exercise in state.exercises) {
            index += 1 // 見出し
            val position = exercise.sets.indexOfFirst { it.id == currentSetId }
            if (position >= 0) {
                val target = index + position
                val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == target }
                if (!visible) listState.animateScrollToItem((target - 1).coerceAtLeast(0))
                return@LaunchedEffect
            }
            index += exercise.sets.size
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp),
    ) {
        state.exercises.forEach { exercise ->
            item(key = "head-${exercise.workoutExerciseId}") {
                ExerciseHeader(
                    exercise = exercise,
                    onClick = { onOpenExercise(exercise.exerciseId) },
                    onLongClick = { onLongPressExercise(exercise) },
                )
            }
            exercise.sets.forEach { set ->
                item(key = "set-${set.id}") {
                    SwipeableSetRow(set = set, onClick = { onSelectSet(set.id) }, onDelete = { onDeleteSet(set.id) })
                }
            }
        }
        item(key = "add-exercise") {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlineButton(
                    text = "種目を追加",
                    onClick = onAddExercise,
                    icon = AppIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseHeader(exercise: ExerciseUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text(exercise.name, style = AppTheme.typography.heading, color = colors.fg)
        if (exercise.meta.isNotEmpty()) {
            Text(exercise.meta, style = AppTheme.typography.caption, color = colors.sub)
        }
        exercise.previous?.let { PreviousLine(it) }
    }
}

/** 「前回 11/7　60 kg 〔合計〕× 10 · 9 · 8」。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PreviousLine(previous: PreviousUi, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val line = previous.line
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "前回 ${previous.dateText}　${line.weightText}".trimEnd(),
            style = AppTheme.typography.caption,
            color = colors.sub,
        )
        line.tag?.let { Tag(it) }
        Text(line.repsText, style = AppTheme.typography.caption, color = colors.sub)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSetRow(set: SetRowUi, onClick: () -> Unit, onDelete: () -> Unit) {
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
        SetRow(set = set, onClick = onClick)
    }
}

@Composable
private fun SetRow(set: SetRowUi, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .defaultMinSize(minHeight = 52.dp)
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
            Spacer(Modifier.width(8.dp))
        }
        when {
            set.completed -> Icon(AppIcons.Check, contentDescription = "完了", tint = colors.primary, modifier = Modifier.size(22.dp))
            set.isCurrent -> Text("入力中", style = AppTheme.typography.caption, color = colors.onContainer, fontWeight = FontWeight.Bold)
            else -> Box(Modifier.size(20.dp).border(1.5.dp, colors.line, CircleShape))
        }
    }
}
