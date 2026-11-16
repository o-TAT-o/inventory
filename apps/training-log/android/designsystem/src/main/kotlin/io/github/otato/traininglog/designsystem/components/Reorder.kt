package io.github.otato.traininglog.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.icons.AppIcons
import io.github.otato.traininglog.designsystem.theme.AppTheme
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 並べ替えできる縦の一覧の状態（F-UI-01、F-UI-02）。
 *
 * 一覧の並び（[items]）は、ドラッグ中は手元で動かし、指を離したときに、変わっていれば保存する（[onCommit]）。
 * 保存した並びがデータベースから戻ってきたら、それに合わせる（ドラッグ中は、手元の並びを優先する）。
 * 画面の端の近くでは、一覧が自動でスクロールする（ライブラリの機能）。
 */
@Stable
class ReorderState<T> internal constructor(
    val listState: LazyListState,
    internal val reorderable: ReorderableLazyListState,
    private val getItems: () -> List<T>,
    private val getDragging: () -> Boolean,
    private val setDragging: (Boolean) -> Unit,
    private val commit: () -> Unit,
) {
    /** いま画面に出す並び。 */
    val items: List<T> get() = getItems()

    /** つまみを持って、動かしている最中か。種目の見出しだけにたたむ、バナーを出す、などに使う。 */
    val isDragging: Boolean get() = getDragging()

    internal fun dragStarted() = setDragging(true)

    internal fun dragStopped() {
        setDragging(false)
        commit()
    }
}

/**
 * @param source 保存されている並び
 * @param key 項目を見分ける値（一覧の `key` と同じもの）
 * @param leadingItems 並べ替えの対象より前に、一覧に入れている項目の数（見出しなど）
 * @param onCommit 指を離したとき、並びが変わっていたら、新しい並びを渡す
 */
@Composable
fun <T> rememberReorderState(
    source: List<T>,
    key: (T) -> Any,
    listState: LazyListState = rememberLazyListState(),
    leadingItems: Int = 0,
    onCommit: (List<T>) -> Unit,
): ReorderState<T> {
    var items by remember { mutableStateOf(source) }
    var dragging by remember { mutableStateOf(false) }
    val currentKey by rememberUpdatedState(key)
    val currentSource by rememberUpdatedState(source)
    val currentCommit by rememberUpdatedState(onCommit)

    // 保存された並び（ほかの更新も）に合わせる。ドラッグ中は、手元の並びを優先する
    LaunchedEffect(source) { if (!dragging) items = source }

    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index - leadingItems, removeAt(from.index - leadingItems)) }
    }
    return remember(listState, reorderable) {
        ReorderState(
            listState = listState,
            reorderable = reorderable,
            getItems = { items },
            getDragging = { dragging },
            setDragging = { dragging = it },
            commit = {
                val saved = currentSource.map(currentKey)
                if (items.map(currentKey) != saved) currentCommit(items)
            },
        )
    }
}

/**
 * 並べ替えできる 1 行。[content] には、つまみ（[ReorderHandle]）に付ける [Modifier] と、ドラッグ中かが渡る。
 * ドラッグ中の行は、浮き上がって見える。
 */
@Composable
fun <T> LazyItemScope.ReorderableRow(
    reorder: ReorderState<T>,
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable (handle: Modifier, isDragging: Boolean) -> Unit,
) {
    ReorderableItem(reorder.reorderable, key = key, modifier = modifier) { isDragging ->
        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "reorder-elevation")
        val handle = Modifier.draggableHandle(
            onDragStarted = { reorder.dragStarted() },
            onDragStopped = { reorder.dragStopped() },
        )
        Box(
            modifier = Modifier
                .shadow(elevation)
                .background(AppTheme.colors.bg),
        ) {
            content(handle, isDragging)
        }
    }
}

/** 並べ替えのつまみ（≡）。ここを押して、そのまま動かす。 */
@Composable
fun ReorderHandle(modifier: Modifier = Modifier, description: String = "ドラッグして並べ替える") {
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Drag, contentDescription = null, tint = AppTheme.colors.sub)
    }
}
