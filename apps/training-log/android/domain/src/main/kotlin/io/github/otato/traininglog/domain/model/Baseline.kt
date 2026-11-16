package io.github.otato.traininglog.domain.model

/**
 * 乗り換え用の初期設定（F-DA-05）。前のアプリでの、種目ごとの直近の値と自己ベスト。
 *
 * 利用者が、前のアプリの画面を見ながら手で入れる。記録（ワークアウト）としては残さず、
 * はじめのワークアウトの「前回の記録」・自己ベスト・次のセットの初期値にだけ使う（F-DA-06）。
 * 実際の記録が付いたら、そちらが優先される。
 *
 * 入れる値は、種目の記録の種類で決まる。
 * - 重量 × 回数: [weightKg]（重量）、[reps]（回数）、[best]（最大重量）
 * - 自重（加重あり）: [weightKg]（加重。マイナスは補助）、[reps]、[best]（最大加重）
 * - 自重（加重なし）: [reps]、[best]（最大回数）
 * - 時間: [durationSec]（秒）、[best]（最大の秒数）
 */
data class Baseline(
    val exerciseId: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val best: Double? = null,
    /** 一覧に足した順 */
    val sortOrder: Int = 0,
)

/** 初期設定の 1 行（種目つき）。 */
data class BaselineDetail(
    val baseline: Baseline,
    val exercise: Exercise,
) {
    /** 1 つでも値が入っているか（「N 種目を入力済み」の数え方）。 */
    val hasValues: Boolean
        get() = baseline.weightKg != null || baseline.reps != null || baseline.durationSec != null || baseline.best != null
}
