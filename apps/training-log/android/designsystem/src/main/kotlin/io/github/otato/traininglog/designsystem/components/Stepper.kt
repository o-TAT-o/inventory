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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.onClick
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
 * 押している間、だんだん速く繰り返す（F-WO-19）。長押しすると繰り返し、離すと止まる。
 *
 * [fireOnPress] が true のときは、押した瞬間に 1 回動く（記録画面の ± のように、反応の速さが大事なとき）。
 * false のときは、離したときに 1 回動く（ふつうのボタンと同じ）。スクロールする画面の中では、こちらにする
 * （スクロールを始めようと触れただけで、値が変わってしまわないように。スクロールが始まったら、動かない）。
 * どちらも、長押しの間は繰り返す。
 */
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    fireOnPress: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    pointerInput(enabled, fireOnPress) {
        if (!enabled) return@pointerInput
        coroutineScope {
            val scope = this
            awaitEachGesture {
                awaitFirstDown()
                if (fireOnPress) currentOnClick()
                var repeating = false
                val repeat = scope.launch {
                    delay(LONG_PRESS_MILLIS)
                    repeating = true
                    var wait = FIRST_REPEAT_MILLIS
                    while (isActive) {
                        currentOnClick()
                        delay(wait)
                        wait = (wait * 0.85).toLong().coerceAtLeast(MIN_REPEAT_MILLIS)
                    }
                }
                val up = waitForUpOrCancellation()
                repeat.cancel()
                // 離したときに動くボタンは、長押しで繰り返していなければ、ここで 1 回動く（スクロールになったときは up が null）
                if (!fireOnPress && up != null && !repeating) currentOnClick()
            }
        }
    }
        // 読み上げ（TalkBack）などの、押す操作でも動くように
        .semantics {
            onClick {
                currentOnClick()
                true
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
    /** false にすると、離したときに 1 回動く（スクロールする画面の中では、押した瞬間に動かさない） */
    fireOnPress: Boolean = true,
) {
    val click = rememberFocusClearingClick(onClick)
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surfaceVariant)
            .repeatingClickable(fireOnPress = fireOnPress, onClick = click)
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
                // 高さは決め打ちにしない（端末の文字を大きくしていると、下の単位が切れる）
                .defaultMinSize(minHeight = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onValueClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(valueText, style = AppTheme.typography.number, color = AppTheme.colors.fg, textAlign = TextAlign.Center)
            Text(unitText, style = AppTheme.typography.caption, color = AppTheme.colors.sub, textAlign = TextAlign.Center, maxLines = 1)
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

/** 小さな ± ボタン。長押しで、だんだん速く増減する。スクロールする画面の中で使うので、離したときに 1 回動く。 */
@Composable
private fun SmallStepButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val click = rememberFocusClearingClick(onClick)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surfaceVariant)
            .repeatingClickable(fireOnPress = false, onClick = click)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.fg, modifier = Modifier.size(24.dp))
    }
}
