package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.ui.common.PreviousUi
import java.time.Instant
import java.time.LocalDate

/** 記録画面が受け取る状態。画面は、これを描くだけにする（計算は ViewModel と [WorkoutUiMapper] が持つ）。 */
data class WorkoutUiState(
    val loaded: Boolean = false,
    /** ワークアウトが見つからない（消された）とき */
    val missing: Boolean = false,
    val workoutId: String = "",
    /** 終えたあとの記録を開いているとき（編集のみ。休憩タイマーは動かさない） */
    val finished: Boolean = false,
    val title: String = "",
    val date: LocalDate? = null,
    /** 今日以外の日付のとき、画面の上に出す日付（F-WO-20） */
    val dateLabel: String? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val routineMemo: String? = null,
    val unit: WeightUnit = WeightUnit.Kg,
    val exercises: List<ExerciseUi> = emptyList(),
    /** 下のパネルで入力するセット。null なら、入力するセットがない */
    val dock: DockUi? = null,
    /** 種目があり、すべてのセットが完了している */
    val allDone: Boolean = false,
    val hasIncompleteSets: Boolean = false,
    /** 「2 種目 · 5 セット」 */
    val summaryText: String = "",
    val availableGear: List<GearChipUi> = emptyList(),
)

data class ExerciseUi(
    val workoutExerciseId: String,
    val exerciseId: String,
    val name: String,
    /** 「バーベル · リストストラップ」 */
    val meta: String,
    val previous: PreviousUi?,
    val sets: List<SetRowUi>,
)

data class SetRowUi(
    val id: String,
    val number: Int,
    val weightText: String,
    val weightTag: String?,
    /** 「× 10 回」「60 秒」など */
    val repsText: String,
    val completed: Boolean,
    /** いま、下のパネルで入力・編集しているセット */
    val isCurrent: Boolean,
    val isPersonalBest: Boolean,
)

enum class FieldKey { Weight, Reps, Duration, Distance }

data class FieldUi(
    val key: FieldKey,
    val valueText: String,
    val unitText: String,
    /** 数値キーボードの入力欄の最初の文字 */
    val inputText: String,
    val allowDecimal: Boolean,
    val minusLabel: String,
    val plusLabel: String,
)

/** 下の固定パネルの内容（種目の切り替え・重量と回数・ボタン）。 */
data class DockUi(
    val setId: String,
    val workoutExerciseId: String,
    val exerciseId: String,
    /** 「ベンチプレス　2 セット目」 */
    val title: String,
    /** 「前回 60 kg × 8」 */
    val subtitle: String,
    val fields: List<FieldUi>,
    /** 完了したセットを直しているとき（ボタンは「完了」でなく「直した」になる） */
    val editingCompleted: Boolean,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val detail: DetailUi,
)

/** 詳細パネル（ギア・数え方・メモ。F-GR-02、F-WO-14、F-WO-08）。 */
data class DetailUi(
    val workoutExerciseId: String,
    val exerciseName: String,
    val subtitle: String,
    val equipment: Equipment,
    val gear: List<GearChipUi>,
    val weightMode: WeightMode,
    val repMode: RepMode,
    val showWeightMode: Boolean,
    val showRepMode: Boolean,
    val memo: String,
)

data class GearChipUi(val id: String, val name: String, val selected: Boolean)

/** 休憩タイマーの表示。 */
data class RestUi(
    val ended: Boolean,
    /** 残り時間（終わったあとは、超過した時間）。「1:12」 */
    val text: String,
    /** 0〜1 */
    val progress: Float,
)
