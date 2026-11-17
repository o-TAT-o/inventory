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
    /** 今日以外の日付のとき、画面の上に出す日付（F-WO-20）。「11/14（土）」 */
    val dateLabel: String? = null,
    /** 「11月13日（金）」。あとから入力のとき、題名の代わりに出す */
    val dateTitle: String = "",
    /** 今日の記録か。今日以外のときだけ、「あとから入力」を選べる（F-WO-21） */
    val isToday: Boolean = true,
    /** 「あとから入力」（タイマーなしの簡易入力）で記録しているか（F-WO-21） */
    val quickEntry: Boolean = false,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val routineMemo: String? = null,
    val unit: WeightUnit = WeightUnit.Kg,
    /** 休憩が終わったときに、音・バイブで知らせるか（設定） */
    val soundOn: Boolean = true,
    val vibrationOn: Boolean = true,
    val exercises: List<ExerciseUi> = emptyList(),
    /** 下のパネルで入力するセット。null なら、入力するセットがない */
    val dock: DockUi? = null,
    /** 種目があり、すべてのセットが完了している */
    val allDone: Boolean = false,
    val hasIncompleteSets: Boolean = false,
    /** 「2 種目 · 6 セット · 27 分」（あとから入力のときは、分を出さない） */
    val summaryText: String = "",
    val availableGear: List<GearChipUi> = emptyList(),
)

data class ExerciseUi(
    val workoutExerciseId: String,
    val exerciseId: String,
    /** 見出しの名前。フォーム（バリエーション）のときは、親の種目の名前 */
    val name: String,
    /** 「バーベル · リストストラップ · ベンチ角度 30°」 */
    val meta: String,
    /** 「フォーム: 未選択」「フォーム: 膝をベンチにつく」。フォームを選べる種目だけ */
    val formLabel: String?,
    /** フォームを選べる種目で、まだ選んでいないか（目立たせる） */
    val formUnselected: Boolean,
    val previous: PreviousUi?,
    val sets: List<SetRowUi>,
    /** 完了したセットの数と、セットの数（「3/3」） */
    val doneCount: Int,
    val totalCount: Int,
    /** すべて完了した種目は、1 行にたたんで出す（タップで開く） */
    val collapsed: Boolean,
    /** たたんだときの 2 行目。「62.5 kg 合計 × 10 · 9 · 9」 */
    val summary: String,
    val hasPersonalBest: Boolean,
    /** 並べ替え中（種目の見出しだけにたたんだとき）の 2 行目。「マシン · 目標 3 セット」 */
    val dragMeta: String,
    /** 前回、この種目に書いたメモ。なければ null。前回の記録の下に出して、読み返せるようにする（F-WO-08） */
    val previousMemo: String? = null,
    /** この記録に書いた、種目のメモ。なければ null */
    val memo: String? = null,
    /** 種目そのものに書いてあるメモ（フォームの注意点など。F-EX-07）。なければ null */
    val exerciseMemo: String? = null,
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
    /** 自己ベストのセットの下に出す説明。「最大重量の更新　これまで 62.5 kg 合計」 */
    val pbNote: String? = null,
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

/** 詳細パネル（ギア・調整項目・数え方・メモ。F-GR-02、F-WO-12、F-WO-14、F-WO-08）。 */
data class DetailUi(
    val workoutExerciseId: String,
    val exerciseName: String,
    val subtitle: String,
    val equipment: Equipment,
    val gear: List<GearChipUi>,
    val adjustments: List<AdjustmentUi>,
    val weightMode: WeightMode,
    val repMode: RepMode,
    val showWeightMode: Boolean,
    val showRepMode: Boolean,
    val memo: String,
    /** 前回、この種目に書いたメモ。なければ null */
    val previousMemo: String? = null,
    /** 種目そのものに書いてあるメモ（フォームの注意点など）。なければ null */
    val exerciseMemo: String? = null,
)

data class GearChipUi(val id: String, val name: String, val selected: Boolean)

/** 調整項目（ベンチ角度・シート位置など）の、この記録での値（F-WO-12）。 */
data class AdjustmentUi(
    val fieldId: String,
    val name: String,
    val unit: String,
    /** 数値の項目か（false なら、文字の項目） */
    val isNumber: Boolean,
    /** いまの値。入れていなければ空 */
    val value: String,
    /** 前回の値（「30」）。前回の値がなければ null。入れていないときに、＋ − の始まりにする */
    val previousValue: String?,
    /** 「前回 30°」。前回の値がなければ null */
    val previousText: String?,
)

/** フォーム（バリエーション）を選ぶシート（F-RT-10）。 */
data class FormSheetUi(
    val workoutExerciseId: String,
    /** 親の種目の名前 */
    val title: String,
    val options: List<FormOptionUi>,
    /** いま選ばれている選択肢の種目 id。選択肢がなければ null */
    val selectedId: String?,
)

data class FormOptionUi(
    val exerciseId: String,
    val name: String,
    /** 「前回 11/11　26 kg 片手 × 10 を 3 セット」「まだ記録がありません」「「膝をベンチにつく」の直近: 26 kg 片手 × 10」 */
    val lines: List<String>,
)

/** 休憩タイマーの表示。 */
data class RestUi(
    val ended: Boolean,
    /** 残り時間（終わったあとは、超過した時間）。「1:12」 */
    val text: String,
    /** 0〜1 */
    val progress: Float,
)
