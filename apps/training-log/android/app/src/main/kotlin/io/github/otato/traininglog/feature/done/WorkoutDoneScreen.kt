package io.github.otato.traininglog.feature.done

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.AppCard
import io.github.otato.traininglog.designsystem.components.AppDivider
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.domain.logic.PersonalBests
import io.github.otato.traininglog.domain.logic.Volume
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.appViewModel
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.formatVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

data class DoneUiState(
    val loaded: Boolean = false,
    val dateText: String = "",
    val durationText: String = "",
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val volumeText: String = "",
    /** 自己ベストを更新した種目（「ベンチプレス 62.5 kg」） */
    val personalBests: List<String> = emptyList(),
)

/** ワークアウト完了の要約（種目数・セット数・ボリューム・自己ベスト）。 */
class WorkoutDoneViewModel(
    workoutId: String,
    workouts: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DoneUiState())
    val state: StateFlow<DoneUiState> = _state

    init {
        viewModelScope.launch {
            val settings: AppSettings = settingsRepository.get()
            val detail = workouts.observeWorkout(workoutId).first() ?: return@launch
            val unit = settings.weightUnit
            val minutes = detail.workout.finishedAt?.let { Duration.between(detail.workout.startedAt, it).toMinutes() }
            val bests = detail.exercises.mapNotNull { exercise ->
                val before = workouts.personalBest(exercise.exercise, excludeWorkoutId = workoutId)
                val ids = PersonalBests.newBestSetIds(
                    exercise.sets, exercise.exercise.recordType, exercise.exercise.useAddedWeight, before,
                )
                val set = exercise.sets.firstOrNull { it.id in ids } ?: return@mapNotNull null
                val line = RecordFormat.weightText(set.weightKg, exercise.exercise, unit)
                "${exercise.exercise.name}　$line".trim()
            }
            _state.value = DoneUiState(
                loaded = true,
                dateText = formatDateWithDow(detail.workout.date),
                durationText = minutes?.let { "$it 分" } ?: "",
                exerciseCount = detail.exercises.count { it.completedSets.isNotEmpty() },
                setCount = detail.exercises.sumOf { it.completedSets.size },
                volumeText = formatVolume(detail.exercises.sumOf { Volume.ofExercise(it) }, unit),
                personalBests = bests,
            )
        }
    }
}

@Composable
fun WorkoutDoneRoute(workoutId: String, onClose: () -> Unit) {
    val viewModel = appViewModel<WorkoutDoneViewModel>(key = "done-$workoutId") { container ->
        WorkoutDoneViewModel(workoutId, container.data.workouts, container.data.settings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutDoneScreen(state, onClose)
}

@Composable
fun WorkoutDoneScreen(state: DoneUiState, onClose: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("おつかれさまでした", style = AppTheme.typography.title, color = colors.fg)
        if (state.loaded) {
            Text(state.dateText, style = AppTheme.typography.caption, color = colors.sub)
            Spacer(Modifier.height(24.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryRow("時間", state.durationText.ifEmpty { "-" })
                    AppDivider()
                    SummaryRow("種目", "${state.exerciseCount} 種目")
                    SummaryRow("セット", "${state.setCount} セット")
                    SummaryRow("ボリューム", state.volumeText)
                    if (state.personalBests.isNotEmpty()) {
                        AppDivider()
                        SectionLabel("自己ベスト")
                        state.personalBests.forEach {
                            Text(it, style = AppTheme.typography.bodyStrong, color = colors.pb)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("ホームへ", onClick = onClose, modifier = Modifier.fillMaxWidth())
        Text(
            "記録はいつでも、履歴から見直せます",
            style = AppTheme.typography.caption,
            color = colors.sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AppTheme.typography.body, color = AppTheme.colors.sub)
        Text(value, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg)
    }
}
