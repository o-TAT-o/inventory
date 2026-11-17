package io.github.otato.traininglog.feature.history

import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import io.github.otato.traininglog.designsystem.components.MemoInputDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.domain.logic.PeriodStats
import io.github.otato.traininglog.domain.repository.RoutineRepository
import io.github.otato.traininglog.domain.usecase.CopyWorkout
import io.github.otato.traininglog.feature.home.RoutineChoice
import io.github.otato.traininglog.feature.home.StartSheet
import io.github.otato.traininglog.platform.AdBanner
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.OutlineButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.WeekStart
import io.github.otato.traininglog.domain.model.WorkoutSummary
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.formatVolume
import io.github.otato.traininglog.ui.common.formatYearMonth
import io.github.otato.traininglog.ui.common.jaShort
import io.github.otato.traininglog.ui.common.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class CalendarCellUi(
    val date: LocalDate,
    val inMonth: Boolean,
    val hasWorkout: Boolean,
    /** 鍛えた部位（「胸 背」）。ひと目で分かるように、先頭の 1 文字ずつ */
    val bodyParts: String,
    val isToday: Boolean,
    val isSelected: Boolean,
)

data class HistoryItemUi(
    val workoutId: String,
    val title: String,
    val stats: String,
    val exercises: String,
    val inProgress: Boolean,
    /** ワークアウトのメモ（完了画面で書いたもの。F-WO-08）。なければ空 */
    val memo: String = "",
)

data class HistoryUiState(
    val loaded: Boolean = false,
    val monthLabel: String = "",
    val weekdayLabels: List<String> = emptyList(),
    val weeks: List<List<CalendarCellUi>> = emptyList(),
    val selectedDate: LocalDate? = null,
    val selectedLabel: String = "",
    val items: List<HistoryItemUi> = emptyList(),
    /** 選んだ日が、今日か（今日以外なら、始めるときに「記録のしかた」も選べる） */
    val selectedIsToday: Boolean = true,
    /** 選んだ日が、今日より先か（先の日には、記録を始められない） */
    val selectedIsFuture: Boolean = false,
    /** 見ている月のまとめ。「今月 8 回 · 胸 3 · 背中 3 · 脚 2」。記録がなければ null（F-ST-05） */
    val monthStats: String? = null,
    /** 始めるときに選べるルーティン */
    val routines: List<RoutineChoice> = emptyList(),
)

/** 履歴（月のカレンダーと、選んだ日の記録。F-HI-01、F-HI-02）。 */
class HistoryViewModel(
    private val workouts: WorkoutRepository,
    routines: RoutineRepository,
    settingsRepository: SettingsRepository,
    private val startWorkout: StartWorkout,
    private val copyWorkout: CopyWorkout,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow<YearMonth?>(null)
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val settings = settingsRepository.observe()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryUiState> = combine(settings, month, selected) { s, m, sel -> Triple(s, m, sel) }
        .flatMapLatest { (s, m, sel) ->
            val today = DayBoundary.today(clock, s.dayBoundaryHour)
            val shown = m ?: YearMonth.from(sel ?: today)
            val first = shown.atDay(1).with(TemporalAdjusters.previousOrSame(s.firstDay()))
            val last = shown.atEndOfMonth().with(TemporalAdjusters.nextOrSame(s.firstDay().minus(1)))
            workouts.observeSummaries(first, last).map { summaries -> build(s, shown, sel ?: today, today, first, last, summaries) }
        }
        .combine(routines.observeRoutines()) { state, list ->
            state.copy(routines = list.sortedBy { it.routine.sortOrder }.map { RoutineChoice(it.routine.id, it.routine.name) })
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, HistoryUiState())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 記録を始めた日付の記録を開く（ワークアウトの id）。 */
    val openWorkout: SharedFlow<String> = _events.asSharedFlow()

    fun select(date: LocalDate) = selected.update { date }

    fun shiftMonth(delta: Long) {
        val current = month.value ?: YearMonth.from(selected.value ?: DayBoundary.today(clock, 4))
        month.value = current.plusMonths(delta)
    }

    fun goToToday() {
        month.value = null
        selected.value = null
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 画面の下に出す案内。 */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** ワークアウトのメモを直す。空にすると、消える。 */
    fun setMemo(workoutId: String, memo: String) {
        viewModelScope.launch { workouts.updateMemo(workoutId, memo) }
    }

    /** 選んだ日の記録を、新しく始める（F-HI-05）。 */
    fun startOnSelected(routineId: String?, quickEntry: Boolean) {
        val date = state.value.selectedDate ?: return
        if (state.value.selectedIsFuture) return
        viewModelScope.launch {
            if (blockedByActive()) return@launch
            _events.emit(startWorkout(date, routineId, quickEntry))
        }
    }

    /** 過去のワークアウトをコピーして、今日のワークアウトを始める（F-HI-04）。 */
    fun copyToToday(workoutId: String) {
        viewModelScope.launch {
            if (blockedByActive()) return@launch
            val today = DayBoundary.today(clock, settings.first().dayBoundaryHour)
            val id = copyWorkout(workoutId, today)
            if (id != null) _events.emit(id) else _messages.emit("コピーできる記録がありません")
        }
    }

    /** 記録の途中のワークアウトがあるときは、新しく始めない（途中の記録が、置き去りにならないように）。 */
    private suspend fun blockedByActive(): Boolean {
        val active = workouts.observeActiveWorkout().first() ?: return false
        _messages.emit("記録の途中のワークアウトがあります（${formatDateWithDow(active.date)}）。先に、そちらを終えてください")
        return true
    }

    private fun AppSettings.firstDay(): DayOfWeek = if (weekStart == WeekStart.Sunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

    private fun build(
        settings: AppSettings,
        shown: YearMonth,
        selectedDate: LocalDate,
        today: LocalDate,
        first: LocalDate,
        last: LocalDate,
        summaries: List<WorkoutSummary>,
    ): HistoryUiState {
        val byDate = summaries.groupBy { it.workout.date }
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val weekdayLabels = days.take(7).map { it.dayOfWeek.jaShort() }
        val weeks = days.chunked(7).map { week ->
            week.map { date ->
                val sums = byDate[date].orEmpty()
                CalendarCellUi(
                    date = date,
                    inMonth = YearMonth.from(date) == shown,
                    hasWorkout = sums.isNotEmpty(),
                    bodyParts = sums.flatMap { it.bodyParts }.distinct().take(2).joinToString(" ") { it.label().take(1) },
                    isToday = date == today,
                    isSelected = date == selectedDate,
                )
            }
        }
        val items = byDate[selectedDate].orEmpty().map { summary ->
            HistoryItemUi(
                workoutId = summary.workout.id,
                title = summary.routineName ?: "自由に記録",
                stats = listOfNotNull(
                    "${summary.completedSetCount} セット",
                    formatVolume(summary.volumeKg, settings.weightUnit).takeIf { summary.volumeKg > 0 },
                ).joinToString(" · "),
                exercises = summary.exerciseNames.joinToString("、").ifEmpty { "まだ記録がありません" },
                inProgress = !summary.workout.isFinished,
                memo = summary.workout.memo.trim(),
            )
        }
        val stats = PeriodStats.of(summaries.filter { YearMonth.from(it.workout.date) == shown })
        val monthName = if (shown == YearMonth.from(today)) "今月" else "${shown.monthValue}月"
        val monthStats = if (stats.workoutCount == 0) null else (
            listOf("$monthName ${stats.workoutCount} 回") + stats.byBodyPart.take(4).map { (part, count) -> "${part.label()} $count" }
            ).joinToString(" · ")
        return HistoryUiState(
            loaded = true,
            monthLabel = formatYearMonth(shown.atDay(1)),
            weekdayLabels = weekdayLabels,
            weeks = weeks,
            selectedDate = selectedDate,
            selectedLabel = formatDateWithDow(selectedDate),
            items = items,
            selectedIsToday = selectedDate == today,
            selectedIsFuture = selectedDate.isAfter(today),
            monthStats = monthStats,
        )
    }
}

@Composable
fun HistoryRoute(onOpenWorkout: (String) -> Unit) {
    val viewModel = appViewModel<HistoryViewModel> {
        HistoryViewModel(
            workouts = it.data.workouts,
            routines = it.data.routines,
            settingsRepository = it.data.settings,
            startWorkout = it.startWorkout,
            copyWorkout = CopyWorkout(it.data.workouts, it.data.exercises, it.clock),
            clock = it.clock,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.openWorkout.collect(onOpenWorkout) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { launch { snackbar.showSnackbar(it) } } }
    HistoryScreen(
        state = state,
        snackbar = snackbar,
        onCopyToToday = viewModel::copyToToday,
        onSetMemo = viewModel::setMemo,
        onSelect = viewModel::select,
        onShiftMonth = viewModel::shiftMonth,
        onToday = viewModel::goToToday,
        onOpenWorkout = onOpenWorkout,
        onStartOnSelected = viewModel::startOnSelected,
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    snackbar: SnackbarHostState,
    onCopyToToday: (String) -> Unit,
    onSetMemo: (workoutId: String, memo: String) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onShiftMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onStartOnSelected: (routineId: String?, quickEntry: Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    var starting by remember { mutableStateOf(false) }
    var editingMemo by remember { mutableStateOf<HistoryItemUi?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("履歴", actions = { TextAction("今日", onClick = onToday) })
        if (!state.loaded) return@Column
        Box(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppIconButton(AppIcons.Left, "前の月", { onShiftMonth(-1) })
                Text(state.monthLabel, style = AppTheme.typography.heading, color = colors.fg)
                AppIconButton(AppIcons.Right, "次の月", { onShiftMonth(1) })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                state.weekdayLabels.forEach {
                    Text(it, style = AppTheme.typography.caption, color = colors.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                state.weeks.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { cell -> CalendarCell(cell, onClick = { onSelect(cell.date) }, modifier = Modifier.weight(1f)) }
                    }
                }
            }
            state.monthStats?.let {
                Text(it, style = AppTheme.typography.caption, color = colors.sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(state.selectedLabel)
                if (state.items.isEmpty()) {
                    Text("この日の記録はありません", style = AppTheme.typography.body, color = colors.sub)
                }
                state.items.forEach { item ->
                    AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title + if (item.inProgress) "（記録中）" else "", style = AppTheme.typography.heading, color = colors.fg)
                            Text(item.stats, style = AppTheme.typography.caption, color = colors.sub)
                            Spacer(Modifier.height(4.dp))
                            Text(item.exercises, style = AppTheme.typography.body, color = colors.fg)
                            if (item.memo.isNotEmpty()) {
                                // 完了画面で書いたメモ。タップで直せる
                                Row(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Button, onClickLabel = "メモを直す") { editingMemo = item },
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(AppIcons.Note, contentDescription = "メモ", tint = colors.sub, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                                    Text(item.memo, style = AppTheme.typography.caption, color = colors.sub)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlineButton(
                                    if (item.inProgress) "続きを記録" else "開く・直す",
                                    onClick = { onOpenWorkout(item.workoutId) },
                                    minHeight = 52.dp,
                                    textStyle = AppTheme.typography.bodyStrong,
                                    horizontalPadding = 8.dp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!item.inProgress) {
                                    PrimaryButton(
                                        "コピーして今日やる",
                                        onClick = { onCopyToToday(item.workoutId) },
                                        minHeight = 52.dp,
                                        textStyle = AppTheme.typography.bodyStrong,
                                        horizontalPadding = 8.dp,
                                        modifier = Modifier.weight(1.3f),
                                    )
                                }
                            }
                        }
                    }
                }
                if (!state.selectedIsFuture) {
                    OutlineButton("この日の記録を始める", onClick = { starting = true }, icon = AppIcons.Plus, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        // 広告を消す購入をしていれば、何も出ない（F-AD-02）
        AdBanner()
    }

    editingMemo?.let { item ->
        MemoInputDialog(
            title = "${state.selectedLabel}のメモ",
            initialText = item.memo,
            onConfirm = {
                onSetMemo(item.workoutId, it)
                editingMemo = null
            },
            onDismiss = { editingMemo = null },
        )
    }

    if (starting) {
        StartOnDaySheet(
            state = state,
            onDismiss = { starting = false },
            onPick = { routineId, quickEntry ->
                starting = false
                onStartOnSelected(routineId, quickEntry)
            },
        )
    }
}

@Composable
private fun StartOnDaySheet(state: HistoryUiState, onDismiss: () -> Unit, onPick: (String?, Boolean) -> Unit) {
    StartSheet(
        title = "${state.selectedLabel}の記録を始める",
        routines = state.routines,
        confirmText = "この日の記録を始める",
        // 今日以外の日は、記録のしかた（ふつうに記録 / あとから入力）も選べる（F-HI-05、F-WO-21）
        showRecordingStyle = !state.selectedIsToday,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

@Composable
private fun CalendarCell(cell: CalendarCellUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .aspectRatio(0.8f)
            .padding(1.dp)
            .clip(shape)
            .then(if (cell.isSelected) Modifier.background(colors.container) else Modifier)
            .then(if (cell.isToday) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            style = AppTheme.typography.bodyStrong,
            color = if (cell.inMonth) colors.fg else colors.line,
            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
        )
        if (cell.hasWorkout) {
            Box(Modifier.padding(top = 2.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.primary))
            Text(cell.bodyParts, style = AppTheme.typography.caption.copy(fontSize = 10.sp), color = colors.sub, maxLines = 1)
        }
    }
}
