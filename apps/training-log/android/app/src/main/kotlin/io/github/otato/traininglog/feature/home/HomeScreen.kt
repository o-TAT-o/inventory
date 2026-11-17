package io.github.otato.traininglog.feature.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.otato.traininglog.designsystem.components.TextAction
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.feature.workout.PreviousLine
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateWithDow
import java.time.LocalDate

@Composable
fun HomeRoute(
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenMigration: () -> Unit,
) {
    val viewModel = appViewModel<HomeViewModel> { container ->
        HomeViewModel(
            workouts = container.data.workouts,
            routines = container.data.routines,
            settingsRepository = container.data.settings,
            startWorkout = container.startWorkout,
            clock = container.clock,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenWorkout -> onOpenWorkout(event.workoutId)
            }
        }
    }
    HomeScreen(
        state = state,
        onStart = { routineId, date, quickEntry -> viewModel.start(routineId, date, quickEntry) },
        onShiftStrip = viewModel::shiftStrip,
        onResetStrip = viewModel::resetStrip,
        onOpenWorkout = onOpenWorkout,
        onOpenSettings = onOpenSettings,
        onOpenRoutines = onOpenRoutines,
        onOpenMigration = onOpenMigration,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: (routineId: String?, date: LocalDate, quickEntry: Boolean) -> Unit,
    onShiftStrip: (Int) -> Unit,
    onResetStrip: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenMigration: () -> Unit,
) {
    var showRoutinePicker by remember { mutableStateOf(false) }
    var pendingDay by remember { mutableStateOf<DayCellUi?>(null) }
    val today = state.today

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "今日",
            actions = { AppIconButton(AppIcons.Tune, "設定", onOpenSettings) },
        )
        if (!state.loaded || today == null) {
            Spacer(Modifier.weight(1f))
            return@Column
        }

        // 読むだけの情報は上。画面が低い端末では、ここだけがスクロールする（F-RT-13）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            state.active?.let { ResumeCard(it, onResume = { onOpenWorkout(it.workoutId) }) }
            state.last?.let { LastWorkoutInfo(it) }
            state.next?.let { NextRoutineCard(it) }
            if (!state.hasRoutines) NoRoutineHint(onOpenRoutines)
        }

        // 操作するものは下。日付の帯と、開始ボタン
        state.stripRange?.let { range ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(range, style = AppTheme.typography.caption, color = AppTheme.colors.sub, modifier = Modifier.weight(1f))
                TextAction("今日へ", onClick = onResetStrip)
            }
        }
        WeekStrip(
            cells = state.strip,
            onShift = onShiftStrip,
            onDayClick = { cell -> if (cell.workoutId != null) onOpenWorkout(cell.workoutId) else pendingDay = cell },
        )
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            val active = state.active
            val next = state.next
            if (active != null) {
                PrimaryButton(
                    text = "ワークアウトを再開",
                    onClick = { onOpenWorkout(active.workoutId) },
                    icon = AppIcons.Play,
                    minHeight = 64.dp,
                    textStyle = AppTheme.typography.button.copy(fontSize = 18.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton(
                    text = if (next != null) "${next.name} を始める" else "自由に記録を始める",
                    onClick = { onStart(next?.routineId, today, false) },
                    icon = AppIcons.Play,
                    minHeight = 64.dp,
                    textStyle = AppTheme.typography.button.copy(fontSize = 18.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isFirstTime) {
                    // はじめて開いたとき（記録もルーティンもない）だけ、前のアプリから乗り換える入口を出す
                    TextButton(onClick = onOpenMigration, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                        Icon(AppIcons.Import, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ほかのアプリから乗り換える", style = AppTheme.typography.bodyStrong)
                    }
                }
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(
                            "ほかのルーティン",
                            onClick = { showRoutinePicker = true },
                            textStyle = AppTheme.typography.button.copy(fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlineButton(
                            "自由に記録",
                            onClick = { onStart(null, today, false) },
                            textStyle = AppTheme.typography.button.copy(fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showRoutinePicker) {
        StartSheet(
            title = "ルーティンを選んで始める",
            routines = state.routines,
            confirmText = null,
            onDismiss = { showRoutinePicker = false },
            onPick = { id, _ ->
                showRoutinePicker = false
                onStart(id, today ?: LocalDate.now(), false)
            },
        )
    }

    pendingDay?.let { cell ->
        StartSheet(
            title = "${formatDateWithDow(cell.date)}の記録を始める",
            subtitle = if (cell.isToday) "今日 · 記録はまだありません" else "記録はまだありません",
            routines = state.routines,
            confirmText = "この日の記録を始める",
            // 今日以外の日は、記録のしかた（ふつうに記録 / あとから入力）も選べる（F-HI-05、F-WO-21）
            showRecordingStyle = !cell.isToday,
            onDismiss = { pendingDay = null },
            onPick = { id, quickEntry ->
                pendingDay = null
                onStart(id, cell.date, quickEntry)
            },
        )
    }
}

/** 前回のワークアウト（読むだけ）。 */
@Composable
private fun LastWorkoutInfo(last: LastWorkoutUi) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        SectionLabel("前回のワークアウト")
        Spacer(Modifier.height(8.dp))
        Text(last.dateText, style = AppTheme.typography.caption, color = colors.sub)
        Text(last.headline, style = AppTheme.typography.heading, color = colors.fg)
        last.highlight?.let { highlight ->
            Row(
                modifier = Modifier.padding(top = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${highlight.exerciseName} ${highlight.line.weightText}".trim(),
                    style = AppTheme.typography.caption,
                    color = colors.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                highlight.line.tag?.let { Tag(it) }
                Text(
                    highlight.line.repsText + if (highlight.hasMore) " ほか" else "",
                    style = AppTheme.typography.caption,
                    color = colors.sub,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 次のルーティンの中身（読むだけのカード。F-RT-13）。余白を広くとり、ほかの表示と同じ内容は重ねない。 */
@Composable
private fun NextRoutineCard(next: NextRoutineUi) {
    val colors = AppTheme.colors
    AppCard(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            SectionLabel("次のルーティン")
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                next.sequence.forEachIndexed { index, chip ->
                    if (index > 0) {
                        Icon(AppIcons.Right, contentDescription = null, tint = colors.sub, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        chip.name,
                        style = AppTheme.typography.body,
                        fontWeight = if (chip.isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (chip.isNext) colors.onContainer else colors.sub,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .then(if (chip.isNext) Modifier.background(colors.container) else Modifier)
                            .padding(horizontal = if (chip.isNext) 12.dp else 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
        next.items.forEach { item ->
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 60.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = AppTheme.typography.bodyStrong, color = colors.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.previous?.let { PreviousLine(it) }
                }
                Spacer(Modifier.width(12.dp))
                Text(item.setsText, style = AppTheme.typography.caption.copy(fontSize = 13.sp), color = colors.sub)
            }
        }
        if (next.moreCount > 0) {
            AppDivider()
            Box(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("ほか ${next.moreCount} 種目", style = AppTheme.typography.caption.copy(fontSize = 13.sp), color = colors.sub)
            }
        }
        if (next.items.isEmpty()) {
            AppDivider()
            Box(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("まだ種目が入っていません", style = AppTheme.typography.caption, color = colors.sub)
            }
        }
    }
}

@Composable
private fun ResumeCard(active: ActiveUi, onResume: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onResume),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel("記録の途中です")
            Text(
                active.title + (active.dateText?.let { "　$it" } ?: ""),
                style = AppTheme.typography.heading,
                color = colors.fg,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("タップすると、続きから記録できます", style = AppTheme.typography.caption, color = colors.sub)
        }
    }
}

@Composable
private fun NoRoutineHint(onOpenRoutines: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ルーティンを作ると、毎回の種目が自動で並びます", style = AppTheme.typography.body, color = AppTheme.colors.sub, textAlign = TextAlign.Center)
        OutlineButton("ルーティンを作る", onClick = onOpenRoutines)
    }
}

/**
 * ホームの日付の帯。日付をタップすると、その日の記録を開く（記録がなければ、新しく始める）。
 * 左右にスワイプすると、7 日ずつ前後へ動く（右へ払うと前の週。F-HI-06）。
 */
@Composable
private fun WeekStrip(cells: List<DayCellUi>, onShift: (Int) -> Unit, onDayClick: (DayCellUi) -> Unit) {
    val colors = AppTheme.colors
    val shift by rememberUpdatedState(onShift)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                val threshold = 56.dp.toPx()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > threshold) shift(-1) else if (dragged < -threshold) shift(1)
                    },
                ) { _, amount -> dragged += amount }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("前の 7 日") { shift(-1); true },
                    CustomAccessibilityAction("次の 7 日") { shift(1); true },
                )
            },
    ) {
        cells.forEach { cell ->
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(shape)
                    .then(if (cell.isToday) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                    .clickable(enabled = !cell.isFuture, role = Role.Button, onClick = { onDayClick(cell) })
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(cell.dowLabel, style = AppTheme.typography.caption, color = colors.sub)
                // 先の日は、押せないことが分かるように、薄くする
                Text(cell.dayNumber.toString(), style = AppTheme.typography.bodyStrong, color = if (cell.isFuture) colors.sub else colors.fg)
                Text(
                    cell.label ?: "",
                    // 行の高さは、文字の大きさに合わせて決める（端末の文字を大きくしていると、決め打ちの高さでは切れる）
                    style = AppTheme.typography.caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
