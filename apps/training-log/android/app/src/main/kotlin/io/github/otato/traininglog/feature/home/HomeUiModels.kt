package io.github.otato.traininglog.feature.home

import io.github.otato.traininglog.ui.common.PreviousUi
import io.github.otato.traininglog.ui.common.SetsLine
import java.time.LocalDate

/** ホーム画面の状態。読むだけの情報（前回のワークアウト・次のルーティンの中身）と、操作するもの（日付の帯・開始ボタン）。 */
data class HomeUiState(
    val loaded: Boolean = false,
    val today: LocalDate? = null,
    val last: LastWorkoutUi? = null,
    val next: NextRoutineUi? = null,
    val hasRoutines: Boolean = false,
    val routines: List<RoutineChoice> = emptyList(),
    /** 記録中のワークアウト（あれば「再開」を出す） */
    val active: ActiveUi? = null,
    val strip: List<DayCellUi> = emptyList(),
)

/** 前回のワークアウト（日付・ルーティン・時間・セット数・ボリューム・代表の種目）。 */
data class LastWorkoutUi(
    val dateText: String,
    val headline: String,
    val highlight: HighlightUi?,
)

data class HighlightUi(
    val exerciseName: String,
    val line: SetsLine,
    /** ほかにも種目があるとき、「ほか」を付ける */
    val hasMore: Boolean,
)

/** 次のルーティンの中身（読むだけ。種目ごとの前回の記録と予定のセット数。F-RT-13）。 */
data class NextRoutineUi(
    val routineId: String,
    val name: String,
    /** ルーティンの並び。次にやるものに印を付ける */
    val sequence: List<SequenceChip>,
    /** 最大 3 行（4 種目以上のときは、先頭の 2 種目 + 「ほか N 種目」） */
    val items: List<NextItemUi>,
    val moreCount: Int,
)

data class SequenceChip(val name: String, val isNext: Boolean)

data class NextItemUi(
    val name: String,
    val previous: PreviousUi?,
    /** 「3 セット」 */
    val setsText: String,
)

data class RoutineChoice(val id: String, val name: String)

data class ActiveUi(
    val workoutId: String,
    val title: String,
    /** 今日以外の日付のとき */
    val dateText: String?,
)

/** 日付の帯の 1 日分。 */
data class DayCellUi(
    val date: LocalDate,
    val dowLabel: String,
    val dayNumber: Int,
    /** その日のルーティン名（記録があるとき）。今日で記録がなければ「今日」 */
    val label: String?,
    val isToday: Boolean,
    /** その日の記録（あれば開く） */
    val workoutId: String?,
)

sealed interface HomeEvent {
    data class OpenWorkout(val workoutId: String) : HomeEvent
}
