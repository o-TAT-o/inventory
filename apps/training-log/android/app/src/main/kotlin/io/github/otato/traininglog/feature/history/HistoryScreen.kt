package io.github.otato.traininglog.feature.history

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
)

data class HistoryUiState(
    val loaded: Boolean = false,
    val monthLabel: String = "",
    val weekdayLabels: List<String> = emptyList(),
    val weeks: List<List<CalendarCellUi>> = emptyList(),
    val selectedDate: LocalDate? = null,
    val selectedLabel: String = "",
    val items: List<HistoryItemUi> = emptyList(),
)

/** 履歴（月のカレンダーと、選んだ日の記録。F-HI-01、F-HI-02）。 */
class HistoryViewModel(
    private val workouts: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val startWorkout: StartWorkout,
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

    fun startOnSelected() {
        val date = state.value.selectedDate ?: return
        viewModelScope.launch { _events.emit(startWorkout(date, routineId = null)) }
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
            )
        }
        return HistoryUiState(
            loaded = true,
            monthLabel = formatYearMonth(shown.atDay(1)),
            weekdayLabels = weekdayLabels,
            weeks = weeks,
            selectedDate = selectedDate,
            selectedLabel = formatDateWithDow(selectedDate),
            items = items,
        )
    }
}

@Composable
fun HistoryRoute(onOpenWorkout: (String) -> Unit) {
    val viewModel = appViewModel<HistoryViewModel> {
        HistoryViewModel(it.data.workouts, it.data.settings, it.startWorkout, it.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openWorkout.collect(onOpenWorkout) }
    HistoryScreen(
        state = state,
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
    onSelect: (LocalDate) -> Unit,
    onShiftMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onStartOnSelected: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("履歴", actions = { TextAction("今日", onClick = onToday) })
        if (!state.loaded) return@Column
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(state.selectedLabel)
                if (state.items.isEmpty()) {
                    Text("この日の記録はありません", style = AppTheme.typography.body, color = colors.sub)
                }
                state.items.forEach { item ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenWorkout(item.workoutId) },
                        cornerRadius = 16.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title + if (item.inProgress) "（記録中）" else "", style = AppTheme.typography.heading, color = colors.fg)
                            Text(item.stats, style = AppTheme.typography.caption, color = colors.sub)
                            Spacer(Modifier.height(4.dp))
                            Text(item.exercises, style = AppTheme.typography.body, color = colors.fg)
                        }
                    }
                }
                OutlineButton("この日の記録を始める", onClick = onStartOnSelected, icon = AppIcons.Plus, modifier = Modifier.fillMaxWidth())
            }
        }
    }
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
