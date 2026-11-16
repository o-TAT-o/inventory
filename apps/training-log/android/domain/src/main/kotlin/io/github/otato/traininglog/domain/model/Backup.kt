package io.github.otato.traininglog.domain.model

import java.time.Instant

/**
 * 全データの写し（書き出し・読み込み・自動バックアップに使う。F-DA-02、F-DA-03、F-DA-10）。
 * ファイルの形式（JSON）は、データ層が決める。ここは、中身だけを持つ。
 */
data class BackupSnapshot(
    /** ファイルの形式の版。読み込むときに、新しすぎる版は断る。 */
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: Instant,
    val exercises: List<Exercise>,
    val adjustmentFields: List<AdjustmentField>,
    val gear: List<Gear>,
    val routines: List<Routine>,
    val routineItems: List<RoutineItem>,
    val workouts: List<Workout>,
    val workoutExercises: List<WorkoutExercise>,
    val sets: List<SetRecord>,
    val workoutGear: List<WorkoutGearLink>,
    val adjustmentValues: List<AdjustmentValueRow>,
    val baselines: List<Baseline>,
    /** 設定（キーと値の組） */
    val settings: Map<String, String>,
) {
    /** 読み込む前の確認に出す、中身の数。 */
    val summary: BackupSummary
        get() = BackupSummary(
            workoutCount = workouts.size,
            exerciseCount = exercises.size,
            routineCount = routines.size,
            setCount = sets.size,
        )

    companion object {
        /** いまのファイルの形式の版。形式を変えるときは上げ、古い版も読めるようにする。 */
        const val FORMAT_VERSION = 1
    }
}

/** 読み込む前の確認に出す、中身の数。 */
data class BackupSummary(
    val workoutCount: Int,
    val exerciseCount: Int,
    val routineCount: Int,
    val setCount: Int,
)

/** ワークアウトの種目と、使ったギアのつながり。 */
data class WorkoutGearLink(val workoutExerciseId: String, val gearId: String)

/** 調整項目の、記録ごとの値。 */
data class AdjustmentValueRow(val workoutExerciseId: String, val fieldId: String, val value: String)

/** ファイルを読めなかった理由（画面に出す文言のもとになる）。 */
class BackupFormatException(message: String) : Exception(message)
