package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.math.abs

/**
 * 入力欄の外に触れたら、フォーカスを外して、キーボードをしまう。入力欄のある画面の、スクロールする内容に付ける
 * （スクロールの修飾より前、つまり外側に付ける）。
 *
 * - 何もない所をタップしたとき
 * - 内容を縦に動かした（スクロールした）とき。キーボードが画面の半分を隠したまま、下の項目を選ぶことにならないように
 *
 * チップやボタンは、押されたときに自分でフォーカスを外す（[rememberFocusClearingClick]）ので、ここでは扱わない。
 * ほかの入力欄をタップしたときは、フォーカスがそちらへ移るだけで、キーボードは出たままになる。
 */
fun Modifier.dismissKeyboardOnTouch(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    this
        // 何もない所のタップ。チップ・ボタン・入力欄は、自分でタップを受け取るので、ここには来ない
        .pointerInput(focusManager) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        // 縦に動かしたとき。子より先に見るだけで、横取りはしない（スクロールは、そのまま動く）
        .pointerInput(focusManager) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                        focusManager.clearFocus()
                        break
                    }
                }
            }
        }
}

/**
 * 押したら、入力欄のフォーカスを外して（キーボードをしまって）から、[onClick] を呼ぶ。
 * チップ・ボタン・± など、アプリの押せる部品は、すべてこれを通す。名前を入力したあと、ほかの項目を選ぶと、
 * キーボードが自然に下がる。
 */
@Composable
internal fun rememberFocusClearingClick(onClick: () -> Unit): () -> Unit {
    val focusManager = LocalFocusManager.current
    val current by rememberUpdatedState(onClick)
    return remember(focusManager) {
        {
            focusManager.clearFocus()
            current()
        }
    }
}
