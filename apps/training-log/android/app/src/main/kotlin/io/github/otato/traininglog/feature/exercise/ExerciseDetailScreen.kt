package io.github.otato.traininglog.feature.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.AppIconButton
import io.github.otato.traininglog.designsystem.components.AppSegmented
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.ChartMetric
import io.github.otato.traininglog.domain.logic.ChartPeriod
import io.github.otato.traininglog.domain.logic.ChartPoint
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.logic.ExerciseStats
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.platform.AdBanner
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.SetsLine
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.formatMonthDay
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** これまでの記録の 1 行。「11/11（水）　膝をベンチにつく」「26 kg 片手 × 10 · 10 · 10 · リストストラップ」 */
data class SessionUi(
    val dateText: String,
    /** フォーム（バリエーション）の名前。まとめて見ているときだけ */
    val formName: String?,
    val line: SetsLine,
    /** ギアと調整項目。「リストストラップ · ベンチ角度 30°」 */
    val extras: String,
)

/** グラフ。[points] は古い順。値は、表示する単位に直してある。 */
data class ChartUi(
    val points: List<ChartPoint> = emptyList(),
    /** 縦軸の単位（kg・回・秒・km） */
    val unitLabel: String = "",
)

/** 「★ 自己ベスト　26 kg 片手 × 10　10/14」 */
data class BestUi(val line: SetsLine, val dateText: String)

data class ExerciseDetailUiState(
    val loaded: Boolean = false,
    val exerciseId: String = "",
    val name: String = "",
    val meta: String = "",
    val memo: String = "",
    /** フォームの切り替え（「まとめて」と、各フォーム）。フォームがなければ空 */
    val variations: List<Pair<String?, String>> = emptyList(),
    val selectedVariationId: String? = null,
    val metrics: List<ChartMetric> = emptyList(),
    val metric: ChartMetric = ChartMetric.Best,
    val period: ChartPeriod = ChartPeriod.ThreeMonths,
    val chart: ChartUi = ChartUi(),
    val best: BestUi? = null,
    val sessions: List<SessionUi> = emptyList(),
    /** 記録中に開いたときは、広告を出さない（F-HI-07） */
    val showAds: Boolean = false,
)

/** 画面で選ぶもの（フォーム・グラフの種類・期間）。 */
private data class Selection(
    val variationId: String? = null,
    val metric: ChartMetric = ChartMetric.Best,
    val period: ChartPeriod = ChartPeriod.ThreeMonths,
)

/** 種目と、そのフォーム・調整項目（記録を読むのに要るもの）。 */
private data class Family(
    val exercise: Exercise?,
    val children: List<Exercise> = emptyList(),
    val fields: Map<String, AdjustmentField> = emptyMap(),
)

/**
 * 種目の詳細（これまでの記録と推移のグラフ。F-HI-03、F-HI-07、F-ST-01〜04、F-ST-06）。記録中の画面からも開ける。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    exerciseId: String,
    exercises: ExerciseRepository,
    workouts: WorkoutRepository,
    gear: GearRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selection = MutableStateFlow(Selection())

    private val family = exercises.observeExercise(exerciseId).flatMapLatest { exercise ->
        if (exercise == null) {
            flowOf(Family(null))
        } else {
            exercises.observeChildren(exercise.id).map { children ->
                val fields = (listOf(exercise) + children).flatMap { exercises.getAdjustmentFields(it.id) }.associateBy { it.id }
                Family(exercise, children, fields)
            }
        }
    }

    val state: StateFlow<ExerciseDetailUiState> = combine(
        family,
        workouts.observeExerciseHistory(exerciseId, includeVariations = true),
        combine(gear.observeGear(includeHidden = true), workouts.observeActiveWorkout()) { list, active -> list.associate { it.id to it.name } to (active == null) },
        settings.observe(),
        selection,
    ) { fam, history, (gearNames, noActive), s, sel ->
        if (fam.exercise == null) ExerciseDetailUiState(loaded = true) else build(fam, history, gearNames, s, sel, noActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun selectVariation(id: String?) = selection.update { it.copy(variationId = id) }
    fun selectMetric(metric: ChartMetric) = selection.update { it.copy(metric = metric) }
    fun selectPeriod(period: ChartPeriod) = selection.update { it.copy(period = period) }

    private fun MutableStateFlow<Selection>.update(transform: (Selection) -> Selection) {
        value = transform(value)
    }

    private fun build(
        family: Family,
        allHistory: List<PreviousRecord>,
        gearNames: Map<String, String>,
        s: AppSettings,
        sel: Selection,
        noActiveWorkout: Boolean,
    ): ExerciseDetailUiState {
        val parent = family.exercise!!
        val unit = s.weightUnit
        val byId = (listOf(parent) + family.children).associateBy { it.id }
        val variationId = sel.variationId?.takeIf { it in byId }
        // 見ている種目（フォームを選んでいれば、そのフォーム。数え方や記録の種類は、フォームごとに違うことがある）
        val shown = variationId?.let(byId::get) ?: parent
        val history = allHistory
            .filter { !it.isBaseline }
            .filter { variationId == null || it.exerciseId == variationId }
        val metrics = ExerciseStats.metricsFor(shown)
        val metric = sel.metric.takeIf { it in metrics } ?: ChartMetric.Best
        val today = DayBoundary.today(clock, s.dayBoundaryHour)
        val from = sel.period.months?.let { today.minusMonths(it) }

        val usesWeightValue = metric != ChartMetric.Best || shown.usesWeight
        val points = ExerciseStats.chart(history, shown, metric, from).map { point ->
            point.copy(value = displayValue(point.value, shown, usesWeightValue, unit))
        }
        val best = ExerciseStats.bestSet(history, shown)?.let { (record, set) ->
            val owner = byId[record.exerciseId] ?: shown
            RecordFormat.setsLine(listOf(set), owner, record.weightMode, record.repMode, unit)?.let { BestUi(it, formatMonthDay(record.date)) }
        }
        return ExerciseDetailUiState(
            loaded = true,
            exerciseId = parent.id,
            name = parent.name,
            meta = metaOf(parent),
            memo = shown.memo.ifBlank { parent.memo },
            variations = if (family.children.isEmpty()) emptyList() else listOf<Pair<String?, String>>(null to "まとめて") + family.children.map { it.id to it.name },
            selectedVariationId = variationId,
            metrics = metrics,
            metric = metric,
            period = sel.period,
            chart = ChartUi(points, unitLabel = unitLabel(shown, usesWeightValue, s)),
            best = best,
            sessions = history.mapNotNull { record ->
                val owner = byId[record.exerciseId] ?: shown
                RecordFormat.setsLine(record.sets, owner, record.weightMode, record.repMode, unit)?.let { line ->
                    val gearText = record.gearIds.mapNotNull(gearNames::get)
                    val adjustText = record.adjustments.mapNotNull { (fieldId, value) ->
                        family.fields[fieldId]?.let { field -> "${field.name} $value${field.unit}" }
                    }
                    SessionUi(
                        dateText = formatDateWithDow(record.date),
                        formName = owner.name.takeIf { variationId == null && owner.parentId != null },
                        line = line,
                        extras = (gearText + adjustText).joinToString(" · "),
                    )
                }
            },
            showAds = noActiveWorkout,
        )
    }

    private fun metaOf(exercise: Exercise): String = buildList {
        add(exercise.bodyPart.label())
        add(exercise.equipment.label())
        if (exercise.recordType == RecordType.WeightReps) add("重量は${exercise.weightMode.tag(exercise.equipment)}")
        if (exercise.repMode != RepMode.Normal) exercise.repMode.tag()?.let { add("回数は$it") }
    }.joinToString(" · ")

    private fun displayValue(value: Double, exercise: Exercise, weightValue: Boolean, unit: io.github.otato.traininglog.domain.model.WeightUnit): Double = when {
        weightValue -> WeightUnits.toDisplay(value, unit)
        exercise.recordType == RecordType.DistanceTime -> value / 1000.0
        else -> value
    }

    private fun unitLabel(exercise: Exercise, weightValue: Boolean, s: AppSettings): String = when {
        weightValue -> WeightUnits.label(s.weightUnit)
        exercise.recordType == RecordType.Bodyweight -> "回"
        exercise.recordType == RecordType.Duration -> "秒"
        else -> "km"
    }
}

@Composable
fun ExerciseDetailRoute(exerciseId: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val viewModel = appViewModel<ExerciseDetailViewModel>(key = "exercise-detail-$exerciseId") {
        ExerciseDetailViewModel(exerciseId, it.data.exercises, it.data.workouts, it.data.gear, it.data.settings, it.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseDetailScreen(state, onBack, onEdit, viewModel::selectVariation, viewModel::selectMetric, viewModel::selectPeriod)
}

private fun ChartMetric.label(exercise: ExerciseDetailUiState): String = when (this) {
    ChartMetric.Best -> when (exercise.chart.unitLabel) {
        "回" -> "最大回数"
        "秒" -> "最長時間"
        "km" -> "最長距離"
        else -> "最大重量"
    }
    ChartMetric.OneRepMax -> "推定 1RM"
    ChartMetric.Volume -> "ボリューム"
}

private fun ChartPeriod.label(): String = when (this) {
    ChartPeriod.OneMonth -> "1か月"
    ChartPeriod.ThreeMonths -> "3か月"
    ChartPeriod.OneYear -> "1年"
    ChartPeriod.All -> "全期間"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSelectVariation: (String?) -> Unit,
    onSelectMetric: (ChartMetric) -> Unit,
    onSelectPeriod: (ChartPeriod) -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = state.name.ifEmpty { "種目の詳細" },
            onBack = onBack,
            actions = { if (state.exerciseId.isNotEmpty()) AppIconButton(AppIcons.Edit, "編集", { onEdit(state.exerciseId) }) },
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.meta, style = AppTheme.typography.caption, color = colors.sub)
                if (state.variations.isNotEmpty()) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.variations.forEach { (id, name) ->
                            AppChip(name, selected = id == state.selectedVariationId, onClick = { onSelectVariation(id) })
                        }
                    }
                }
                if (state.metrics.size > 1) {
                    AppSegmented(
                        options = state.metrics.map { it.label(state) },
                        selectedIndex = state.metrics.indexOf(state.metric).coerceAtLeast(0),
                        onSelect = { onSelectMetric(state.metrics[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartPeriod.entries.forEach { period ->
                        AppChip(period.label(), selected = period == state.period, onClick = { onSelectPeriod(period) })
                    }
                }
                AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    if (state.chart.points.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.loaded) "この期間の記録はありません" else "",
                                style = AppTheme.typography.body,
                                color = colors.sub,
                            )
                        }
                    } else {
                        LineChart(
                            chart = state.chart,
                            description = "${state.metric.label(state)}の推移",
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(12.dp),
                        )
                    }
                }
                state.best?.let { best ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.5.dp, colors.fg, RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Star, contentDescription = null, tint = colors.fg, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("自己ベスト", style = AppTheme.typography.caption, color = colors.fg, fontWeight = FontWeight.Bold)
                        }
                        if (best.line.weightText.isNotEmpty()) Text(best.line.weightText, style = AppTheme.typography.bodyStrong, color = colors.fg)
                        best.line.tag?.let { Text(it, style = AppTheme.typography.body, color = colors.fg) }
                        Text(best.line.repsText, style = AppTheme.typography.body, color = colors.fg)
                        Text(best.dateText, style = AppTheme.typography.body, color = colors.sub)
                    }
                }
                if (state.memo.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Note, contentDescription = "メモ", tint = colors.sub, modifier = Modifier.size(18.dp))
                        Text(state.memo, style = AppTheme.typography.body, color = colors.sub)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AppDivider()
            if (state.loaded && state.sessions.isEmpty()) {
                Text("まだ記録がありません", style = AppTheme.typography.body, color = colors.sub, modifier = Modifier.padding(16.dp))
            }
            state.sessions.forEach { session -> SessionRow(session) }
        }
        AdBanner(enabled = state.showAds)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(session: SessionUi) {
    val colors = AppTheme.colors
    Column {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(session.dateText, style = AppTheme.typography.bodyStrong, color = colors.fg)
                session.formName?.let { Text(it, style = AppTheme.typography.bodyStrong, color = colors.fg) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (session.line.weightText.isNotEmpty()) Text(session.line.weightText, style = AppTheme.typography.caption, color = colors.sub)
                session.line.tag?.let { Tag(it) }
                Text(session.line.repsText, style = AppTheme.typography.caption, color = colors.sub)
                if (session.extras.isNotEmpty()) Text("· ${session.extras}", style = AppTheme.typography.caption, color = colors.sub)
            }
        }
        AppDivider()
    }
}

/**
 * 折れ線グラフ。ライブラリは使わず、Canvas で描く（要件 9 章）。
 * 横の目盛り線と値、下に日付（最初と最後）、自己ベストを更新した点に ★ を出す。
 */
@Composable
private fun LineChart(chart: ChartUi, description: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = AppTheme.typography.caption.copy(color = colors.sub)
    val points = chart.points
    val summary = "$description。${points.size} 回の記録。最新は ${formatNumber(points.last().value)} ${chart.unitLabel}"
    Canvas(modifier = modifier.semantics { contentDescription = summary }) {
        val rawMin = points.minOf { it.value }
        val rawMax = points.maxOf { it.value }
        val (low, high, step) = niceScale(rawMin, rawMax)
        val ticks = generateSequence(low) { it + step }.takeWhile { it <= high + step / 1000 }.toList()

        val labelWidth = ticks.maxOf { measurer.measure(formatNumber(it), labelStyle).size.width } + 8.dp.toPx()
        val topPad = 26.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val left = labelWidth
        val right = size.width - 8.dp.toPx()
        val top = topPad
        val bottom = size.height - bottomPad
        fun y(value: Double): Float = (bottom - (value - low) / (high - low) * (bottom - top)).toFloat()
        fun x(index: Int): Float = if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1)

        drawText(measurer, chart.unitLabel, Offset(0f, 0f), labelStyle)
        ticks.forEach { tick ->
            val ty = y(tick)
            drawLine(colors.line, Offset(left, ty), Offset(right, ty), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(formatNumber(tick), labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), ty - text.size.height / 2))
        }

        val path = Path().apply {
            points.indices.forEach { i -> if (i == 0) moveTo(x(i), y(points[i].value)) else lineTo(x(i), y(points[i].value)) }
        }
        drawPath(path, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, point ->
            val center = Offset(x(i), y(point.value))
            drawCircle(colors.primary, radius = 4.dp.toPx(), center = center)
            if (point.isPersonalBest) drawStar(center.copy(y = center.y - 14.dp.toPx()), 7.dp.toPx(), colors.pb)
        }

        // 下の日付は、最初と最後だけ（点が多くても重ならない）
        val first = measurer.measure(formatMonthDay(points.first().date), labelStyle)
        drawText(first, topLeft = Offset(left, size.height - first.size.height))
        if (points.size > 1) {
            val last = measurer.measure(formatMonthDay(points.last().date), labelStyle)
            drawText(last, topLeft = Offset(right - last.size.width, size.height - last.size.height))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(center: Offset, radius: Float, color: androidx.compose.ui.graphics.Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = Math.toRadians(-90.0 + i * 36.0)
        val px = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val py = center.y + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}

/** 目盛りの下限・上限・刻み。きりのよい数にし、線が 3〜5 本になるようにする。 */
internal fun niceScale(min: Double, max: Double): Triple<Double, Double, Double> {
    val span = (max - min).takeIf { it > 0 } ?: maxOf(kotlin.math.abs(max) * 0.2, 1.0)
    val rough = span / 3
    val magnitude = Math.pow(10.0, floor(Math.log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    var low = floor(min / step) * step
    var high = ceil(max / step) * step
    if (low == high) {
        low -= step
        high += step
    }
    return Triple(low, high, step)
}

private fun formatNumber(value: Double): String =
    if (kotlin.math.abs(value) >= 1000) "%,d".format(value.roundToLong()) else WeightUnits.format(value)
