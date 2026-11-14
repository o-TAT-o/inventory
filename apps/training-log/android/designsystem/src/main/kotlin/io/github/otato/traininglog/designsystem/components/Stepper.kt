package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 押している間、だんだん速く繰り返す（F-WO-19）。押した瞬間に 1 回、長押しすると繰り返す。
 */
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            val scope = this
            awaitEachGesture {
                awaitFirstDown()
                currentOnClick()
                val repeat = scope.launch {
                    delay(LONG_PRESS_MILLIS)
                    var wait = FIRST_REPEAT_MILLIS
                    while (isActive) {
                        currentOnClick()
                        delay(wait)
                        wait = (wait * 0.85).toLong().coerceAtLeast(MIN_REPEAT_MILLIS)
                    }
                }
                waitForUpOrCancellation()
                repeat.cancel()
            }
        }
    }
}

private const val LONG_PRESS_MILLIS = 400L
private const val FIRST_REPEAT_MILLIS = 160L
private const val MIN_REPEAT_MILLIS = 40L

/** ＋ − ボタン（48 × 56dp）。長押しで速く増減する。 */
@Composable
fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surfaceVariant)
            .repeatingClickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.fg, modifier = Modifier.size(24.dp))
    }
}

/**
 * 記録画面の、重量・回数の入力部品。大きな ± ボタンと、数字（タップすると数値キーボードで直接入力できる）。
 */
@Composable
fun ValueStepper(
    valueText: String,
    unitText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: () -> Unit,
    minusLabel: String,
    plusLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(AppIcons.Minus, minusLabel, onMinus)
        Column(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onValueClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(valueText, style = AppTheme.typography.number, color = AppTheme.colors.fg, textAlign = TextAlign.Center)
            Text(unitText, style = AppTheme.typography.caption, color = AppTheme.colors.sub, textAlign = TextAlign.Center)
        }
        StepButton(AppIcons.Plus, plusLabel, onPlus)
    }
}

/** 小さな横幅の ± 付きの値（設定の休憩時間など）。 */
@Composable
fun CompactStepper(
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String,
    plusLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SmallStepButton(AppIcons.Minus, minusLabel, onMinus)
        Text(
            valueText,
            style = AppTheme.typography.number.copy(fontSize = 18.sp),
            color = AppTheme.colors.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(60.dp),
        )
        SmallStepButton(AppIcons.Plus, plusLabel, onPlus)
    }
}

@Composable
private fun SmallStepButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.fg, modifier = Modifier.size(24.dp))
    }
}
