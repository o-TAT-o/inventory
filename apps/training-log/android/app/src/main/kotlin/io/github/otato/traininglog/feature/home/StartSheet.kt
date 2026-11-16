package io.github.otato.traininglog.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.components.AppChip
import io.github.otato.traininglog.designsystem.components.PrimaryButton
import io.github.otato.traininglog.designsystem.components.SectionLabel
import io.github.otato.traininglog.designsystem.theme.AppTheme
import io.github.otato.traininglog.feature.workout.RecordingStyleChoices

/**
 * ルーティンを選ぶ下からのシート。「ほかのルーティン」と、記録がない日を始めるときに使う（ホーム・履歴）。
 *
 * - [confirmText] があるときは、ルーティンを選んでから、そのボタンで始める。ないときは、タップした時点で始める
 * - [showRecordingStyle] が true のとき（今日以外の日）は、記録のしかた（ふつうに記録 / あとから入力）も選べる（F-HI-05、F-WO-21）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartSheet(
    title: String,
    routines: List<RoutineChoice>,
    confirmText: String?,
    onDismiss: () -> Unit,
    onPick: (routineId: String?, quickEntry: Boolean) -> Unit,
    subtitle: String? = null,
    showRecordingStyle: Boolean = false,
) {
    val colors = AppTheme.colors
    var selected by remember { mutableStateOf<String?>(routines.firstOrNull()?.id) }
    var quickEntry by remember { mutableStateOf(false) }
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
                            onClick = { if (confirmText == null) onPick(routine.id, quickEntry) else selected = routine.id },
                        )
                    }
                    AppChip(
                        "自由に記録",
                        selected = confirmText != null && selected == null,
                        onClick = { if (confirmText == null) onPick(null, quickEntry) else selected = null },
                    )
                }
            }
            if (showRecordingStyle) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("記録のしかた")
                    RecordingStyleChoices(quickEntry = quickEntry, onSelect = { quickEntry = it })
                }
            }
            if (confirmText != null) {
                PrimaryButton(
                    confirmText,
                    onClick = { onPick(selected, quickEntry) },
                    minHeight = 60.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
