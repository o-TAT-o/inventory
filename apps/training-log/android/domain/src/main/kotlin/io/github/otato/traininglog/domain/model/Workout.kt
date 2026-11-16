package io.github.otato.traininglog.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * ワークアウト（1 回のトレーニング）。
 * [date] は始めるときに選んだ日で、0 時や 1日の区切りを過ぎても変わらない（F-WO-22）。
 * 開始・終了の時刻 [startedAt] / [finishedAt] は実際の時刻。[finishedAt] が null なら記録中。
 */
data class Workout(
    val id: String,
    val date: LocalDate,
    val routineId: String? = null,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val memo: String = "",
) {
    val isFinished: Boolean get() = finishedAt != null
}

/** ワークアウトの中の種目。重量・回数の数え方は、記録したときの値をここに持つ（種目の設定を後で変えても過去の記録は変わらない）。 */
data class WorkoutExercise(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val sortOrder: Int = 0,
    val weightMode: WeightMode,
    val repMode: RepMode,
    val memo: String = "",
    /** セット間の休憩（秒）。null なら設定の値 */
    val restSeconds: Int? = null,
    /** 種目のあとの休憩（秒）。null なら設定の値 */
    val restAfterSeconds: Int? = null,
)

/**
 * セットの記録。重量と回数は入力したとおりの値で持つ（片側の種目なら片手の重さ・片側の回数）。
 * 自重の種目で加重を使うときは、[weightKg] に加重を入れる（0 は自重、マイナスは補助）。
 */
data class SetRecord(
    val id: String,
    val workoutExerciseId: String,
    val sortOrder: Int = 0,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val completed: Boolean = false,
    val setType: SetType = SetType.Normal,
    val completedAt: Instant? = null,
)

/** セットの入力値だけを更新するときの値（入力はすぐ保存する。F-WO-07）。 */
data class SetValues(
    val setId: String,
    val weightKg: Double,
    val reps: Int,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
)

/** 種目とセットをまとめたもの（記録画面・履歴で使う）。 */
data class WorkoutExerciseDetail(
    val workoutExercise: WorkoutExercise,
    val exercise: Exercise,
    val sets: List<SetRecord>,
    val gearIds: Set<String> = emptySet(),
) {
    val completedSets: List<SetRecord> get() = sets.filter { it.completed }

    /** いま入力するセット。先頭から数えて、最初の未完了のセット。 */
    val currentSet: SetRecord? get() = sets.firstOrNull { !it.completed }

    val isAllDone: Boolean get() = sets.isNotEmpty() && sets.all { it.completed }
}

data class WorkoutDetail(
    val workout: Workout,
    val exercises: List<WorkoutExerciseDetail>,
) {
    /** いま入力する種目。先頭から数えて、未完了のセットがある最初の種目。 */
    val firstOpenExercise: WorkoutExerciseDetail?
        get() = exercises.firstOrNull { it.currentSet != null }
}

/** 一覧（ホーム・履歴）に出す、ワークアウトの要約。 */
data class WorkoutSummary(
    val workout: Workout,
    val routineName: String?,
    val completedSetCount: Int,
    val volumeKg: Double,
    /** 種目名（記録した順） */
    val exerciseNames: List<String>,
    /** 鍛えた部位（重複なし） */
    val bodyParts: List<BodyPart>,
)

/**
 * 前回の記録（同じ種目で、直近のワークアウトの完了したセット）。
 * [isBaseline] が true のときは、実際の記録ではなく、乗り換え用の初期設定で入れた直近の値（F-DA-06）。
 * このとき [sets] は、同じ値のセットを、入れたセット数だけ並べたもの（セット数を入れなかったときは 1 セット）。
 * [date] は値を入れた日。
 *
 * [setCountKnown] は、[sets] の数が、実際にやったセット数か。乗り換えでセット数を入れなかったときだけ false で、
 * このときは、次のワークアウトのセット数の目安にしない（値だけを使う）。
 */
data class PreviousRecord(
    val date: LocalDate,
    val sets: List<SetRecord>,
    val weightMode: WeightMode,
    val repMode: RepMode,
    val gearIds: Set<String> = emptySet(),
    val isBaseline: Boolean = false,
    val setCountKnown: Boolean = true,
)

/** ワークアウトを始めるとき・種目を足すときに作る、種目とセットの下書き。 */
data class PlannedWorkoutExercise(
    val workoutExercise: WorkoutExercise,
    val sets: List<SetRecord>,
    val gearIds: Set<String> = emptySet(),
)
