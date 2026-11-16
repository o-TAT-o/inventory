package io.github.otato.traininglog.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import io.github.otato.traininglog.designsystem.components.AppChip
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
        onStart = viewModel::start,
        onOpenWorkout = onOpenWorkout,
        onOpenSettings = onOpenSettings,
        onOpenRoutines = onOpenRoutines,
        onOpenMigration = onOpenMigration,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: (routineId: String?, date: LocalDate) -> Unit,
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
        WeekStrip(cells = state.strip, onDayClick = { cell ->
            if (cell.workoutId != null) onOpenWorkout(cell.workoutId) else pendingDay = cell
        })
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
                    onClick = { onStart(next?.routineId, today) },
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
                            onClick = { onStart(null, today) },
                            textStyle = AppTheme.typography.button.copy(fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showRoutinePicker) {
        RoutineSheet(
            title = "ルーティンを選んで始める",
            routines = state.routines,
            confirmText = null,
            onDismiss = { showRoutinePicker = false },
            onPick = { id ->
                showRoutinePicker = false
                onStart(id, today ?: LocalDate.now())
            },
        )
    }

    pendingDay?.let { cell ->
        RoutineSheet(
            title = "${formatDateWithDow(cell.date)}の記録を始める",
            subtitle = if (cell.isToday) "今日 · 記録はまだありません" else "記録はまだありません",
            routines = state.routines,
            confirmText = "この日の記録を始める",
            onDismiss = { pendingDay = null },
            onPick = { id ->
                pendingDay = null
                onStart(id, cell.date)
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

/** ホームの日付の帯。日付をタップすると、その日の記録を開く（記録がなければ、新しく始める）。 */
@Composable
private fun WeekStrip(cells: List<DayCellUi>, onDayClick: (DayCellUi) -> Unit) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        cells.forEach { cell ->
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(shape)
                    .then(if (cell.isToday) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                    .clickable(role = Role.Button, onClick = { onDayClick(cell) })
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(cell.dowLabel, style = AppTheme.typography.caption, color = colors.sub)
                Text(cell.dayNumber.toString(), style = AppTheme.typography.bodyStrong, color = colors.fg)
                Text(
                    cell.label ?: "",
                    style = AppTheme.typography.caption.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(14.dp),
                )
            }
        }
    }
}

/** ルーティンを選ぶ下からのシート。「ほかのルーティン」と、記録がない日を始めるときに使う。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineSheet(
    title: String,
    routines: List<RoutineChoice>,
    confirmText: String?,
    onDismiss: () -> Unit,
    onPick: (routineId: String?) -> Unit,
    subtitle: String? = null,
) {
    val colors = AppTheme.colors
    // 確認ボタンがあるときは、選んでから「始める」。ないときは、タップした時点で始める
    var selected by remember { mutableStateOf<String?>(routines.firstOrNull()?.id) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(title, style = AppTheme.typography.title, color = colors.fg)
                subtitle?.let { Text(it, style = AppTheme.typography.caption, color = colors.sub) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("ルーティン")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routines.forEach { routine ->
                        AppChip(
                            routine.name,
                            selected = confirmText != null && selected == routine.id,
                            onClick = { if (confirmText == null) onPick(routine.id) else selected = routine.id },
                        )
                    }
                    AppChip(
                        "自由に記録",
                        selected = confirmText != null && selected == null,
                        onClick = { if (confirmText == null) onPick(null) else selected = null },
                    )
                }
            }
            if (confirmText != null) {
                PrimaryButton(
                    confirmText,
                    onClick = { onPick(selected) },
                    minHeight = 60.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
