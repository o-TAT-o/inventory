package io.github.otato.traininglog.feature.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.components.Tag
import io.github.otato.traininglog.designsystem.components.TextAction
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.PersonalBests
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import io.github.otato.traininglog.domain.repository.GearRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.SetsLine
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SessionUi(val dateText: String, val line: SetsLine, val gear: String)

data class ExerciseDetailUiState(
    val loaded: Boolean = false,
    val exerciseId: String = "",
    val name: String = "",
    val meta: String = "",
    val bestText: String? = null,
    val sessions: List<SessionUi> = emptyList(),
    /** 推移のグラフ（古い順）。重量の種目は最大重量、加重のない自重は最大回数 */
    val chart: List<Double> = emptyList(),
    val chartTitle: String = "",
)

/** 種目の詳細（これまでの記録と推移のグラフ。F-HI-03、F-HI-07、F-ST-01）。記録中の画面からも開ける。 */
class ExerciseDetailViewModel(
    exerciseId: String,
    exercises: ExerciseRepository,
    workouts: WorkoutRepository,
    gear: GearRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ExerciseDetailUiState> = combine(
        exercises.observeExercise(exerciseId),
        workouts.observeExerciseHistory(exerciseId),
        gear.observeGear(includeHidden = true),
        settings.observe(),
    ) { exercise, history, gearList, s ->
        if (exercise == null) ExerciseDetailUiState(loaded = true) else build(exercise, history, gearList.associate { it.id to it.name }, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    private fun build(exercise: Exercise, history: List<PreviousRecord>, gearNames: Map<String, String>, s: AppSettings): ExerciseDetailUiState {
        val unit = s.weightUnit
        val scores = history.map { PersonalBests.bestOf(it.sets, exercise.recordType, exercise.useAddedWeight) }
        val best = scores.filterNotNull().maxOrNull()
        val bestText = best?.let {
            when {
                exercise.recordType == RecordType.WeightReps || (exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight) ->
                    WeightUnits.formatKg(it, unit) + " " + WeightUnits.label(unit)
                exercise.recordType == RecordType.Bodyweight -> "${it.toInt()} 回"
                else -> WeightUnits.format(it)
            }
        }
        val chart = history.reversed().mapNotNull { record ->
            PersonalBests.bestOf(record.sets, exercise.recordType, exercise.useAddedWeight)?.let { score ->
                if (exercise.usesWeight) WeightUnits.toDisplay(score, unit) else score
            }
        }
        return ExerciseDetailUiState(
            loaded = true,
            exerciseId = exercise.id,
            name = exercise.name,
            meta = listOf(exercise.bodyPart.label(), exercise.equipment.label(), exercise.weightMode.tag(exercise.equipment)).joinToString(" · "),
            bestText = bestText,
            sessions = history.mapNotNull { record ->
                RecordFormat.setsLine(record.sets, exercise, record.weightMode, record.repMode, unit)?.let { line ->
                    SessionUi(formatDateWithDow(record.date), line, record.gearIds.mapNotNull(gearNames::get).joinToString("、"))
                }
            },
            chart = chart,
            chartTitle = if (exercise.usesWeight) "最大重量の推移（${WeightUnits.label(unit)}）" else "最大回数の推移",
        )
    }
}

@Composable
fun ExerciseDetailRoute(exerciseId: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val viewModel = appViewModel<ExerciseDetailViewModel>(key = "exercise-detail-$exerciseId") {
        ExerciseDetailViewModel(exerciseId, it.data.exercises, it.data.workouts, it.data.gear, it.data.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseDetailScreen(state, onBack, onEdit)
}

@Composable
fun ExerciseDetailScreen(state: ExerciseDetailUiState, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = state.name.ifEmpty { "種目の詳細" },
            onBack = onBack,
            actions = { if (state.exerciseId.isNotEmpty()) TextAction("編集", onClick = { onEdit(state.exerciseId) }) },
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.meta, style = AppTheme.typography.body, color = colors.sub)
            state.bestText?.let {
                AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionLabel("自己ベスト")
                        Text(it, style = AppTheme.typography.number, color = colors.pb)
                    }
                }
            }
            if (state.chart.size >= 2) {
                AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionLabel(state.chartTitle)
                        LineChart(state.chart, modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp))
                    }
                }
            }
            SectionLabel("これまでの記録")
            if (state.loaded && state.sessions.isEmpty()) {
                Text("まだ記録がありません", style = AppTheme.typography.body, color = colors.sub)
            }
            state.sessions.forEach { session ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(session.dateText, style = AppTheme.typography.caption, color = colors.sub)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (session.line.weightText.isNotEmpty()) Text(session.line.weightText, style = AppTheme.typography.bodyStrong, color = colors.fg)
                        session.line.tag?.let { Tag(it) }
                        Text(session.line.repsText, style = AppTheme.typography.bodyStrong, color = colors.fg)
                    }
                    if (session.gear.isNotEmpty()) {
                        Text("ギア: ${session.gear}", style = AppTheme.typography.caption, color = colors.sub)
                    }
                }
            }
        }
    }
}

/** 折れ線グラフ。ライブラリは使わず、Canvas で描く（要件 9 章）。 */
@Composable
private fun LineChart(values: List<Double>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Canvas(modifier = modifier) {
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        fun point(index: Int): Offset =
            Offset(index * stepX, (size.height - ((values[index] - min) / span * size.height * 0.85 + size.height * 0.075)).toFloat())
        val path = Path().apply {
            values.indices.forEach { i -> if (i == 0) moveTo(point(i).x, point(i).y) else lineTo(point(i).x, point(i).y) }
        }
        drawPath(path, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        values.indices.forEach { i -> drawCircle(colors.primary, radius = 4.dp.toPx(), center = point(i)) }
    }
}
