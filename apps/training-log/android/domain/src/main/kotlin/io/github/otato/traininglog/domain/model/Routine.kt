package io.github.otato.traininglog.domain.model

/** ルーティン（例: Push / Pull / Legs）。 */
data class Routine(
    val id: String,
    val name: String,
    val memo: String = "",
    val sortOrder: Int = 0,
)

/** ルーティンの中の種目と、その目標。空欄（null）のものは、前回の記録や設定の値を使う。 */
data class RoutineItem(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int = 0,
    val targetSets: Int? = null,
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    /** セット間の休憩（秒）。null なら設定の値（F-RT-08） */
    val restSeconds: Int? = null,
    /** 種目のあとの休憩（秒）。null なら設定の値（F-RT-12） */
    val restAfterSeconds: Int? = null,
)

data class RoutineItemDetail(
    val item: RoutineItem,
    val exercise: Exercise,
    /** バリエーション（フォーム）のとき、親の種目の名前 */
    val parentName: String? = null,
) {
    /** 一覧に出す名前。バリエーションは「ワンハンドダンベルロウ（膝をベンチにつく）」。 */
    val displayName: String get() = if (parentName != null) "$parentName（${exercise.name}）" else exercise.name
}

data class RoutineDetail(
    val routine: Routine,
    val items: List<RoutineItemDetail>,
)
