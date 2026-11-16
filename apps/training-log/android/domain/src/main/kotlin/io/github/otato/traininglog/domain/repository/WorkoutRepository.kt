package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PlannedWorkoutExercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface WorkoutRepository {
    // ---- 観察（画面が最新の状態を受け取る） ----

    fun observeWorkout(id: String): Flow<WorkoutDetail?>

    /** 記録中（まだ終えていない）のワークアウト。複数あるときは、最後に始めたもの。 */
    fun observeActiveWorkout(): Flow<Workout?>

    /** 期間内のワークアウトの要約（日付順）。ホームの日付の帯と履歴カレンダーに使う。 */
    fun observeSummaries(from: LocalDate, to: LocalDate): Flow<List<WorkoutSummary>>

    /** 最後に終えたワークアウトの要約。まだなければ null。 */
    fun observeLastFinished(): Flow<WorkoutSummary?>

    /** 最後に終えた、ルーティンありのワークアウトのルーティン id（次のルーティンの提案に使う）。 */
    fun observeLastRoutineId(): Flow<String?>

    /** ルーティンごとの、最後にやった日（終えたワークアウト）。ルーティン一覧の「前回 11/7」に使う。 */
    fun observeLastDateByRoutine(): Flow<Map<String, LocalDate>>

    /**
     * 種目の、これまでの記録（新しい順）。種目の詳細に使う（F-HI-03）。
     * [includeVariations] が true なら、バリエーションの記録もまとめて返す（F-ST-06。各記録の [PreviousRecord.exerciseId] で見分ける）。
     */
    fun observeExerciseHistory(exerciseId: String, includeVariations: Boolean = false): Flow<List<PreviousRecord>>

    // ---- ワークアウト ----

    suspend fun createWorkout(workout: Workout, exercises: List<PlannedWorkoutExercise>)

    suspend fun updateDate(workoutId: String, date: LocalDate)

    suspend fun updateMemo(workoutId: String, memo: String)

    /** 「あとから入力」と「ふつうに記録」を行き来する（F-WO-21）。 */
    suspend fun setQuickEntry(workoutId: String, quickEntry: Boolean)

    /**
     * ワークアウトを終える。未完了のセットと、完了したセットのない種目は記録に含めない（F-WO-18）。
     * 完了したセットが 1 つもなければ、ワークアウトごと破棄する。
     * @return 記録を残したら true、破棄したら false
     */
    suspend fun finishWorkout(workoutId: String, at: Instant): Boolean

    suspend fun deleteWorkout(workoutId: String)

    // ---- 種目 ----

    suspend fun addExercise(planned: PlannedWorkoutExercise, workoutId: String)

    suspend fun removeExercise(workoutExerciseId: String)

    suspend fun updateExerciseMemo(workoutExerciseId: String, memo: String)

    /** その日の記録だけ、重量・回数の数え方を変える（F-WO-14）。種目の設定は変わらない。 */
    suspend fun updateExerciseModes(workoutExerciseId: String, weightMode: WeightMode, repMode: RepMode)

    suspend fun setGear(workoutExerciseId: String, gearIds: Set<String>)

    /** ワークアウトの中の種目の並びを変える（F-WO-13）。[orderedIds] は、ワークアウトの種目の id を、新しい順に並べたもの。 */
    suspend fun reorderExercises(workoutId: String, orderedIds: List<String>)

    /** 調整項目の値を入れる（F-WO-12）。[value] が null か空なら、値を消す。 */
    suspend fun setAdjustment(workoutExerciseId: String, fieldId: String, value: String?)

    /**
     * 記録の種目を、別の種目（バリエーション）に付け替える（F-RT-10）。数え方は、付け替え先の種目の値にする。
     * 完了していないセットの値は、[sets] が渡されたとき、それに置き換える（バリエーションの前回の値で用意し直すため）。
     */
    suspend fun changeExercise(workoutExerciseId: String, exercise: Exercise, sets: List<SetValues> = emptyList())

    // ---- セット ----

    suspend fun addSet(set: SetRecord)

    /** セットの入力値（重量・回数・時間・距離）を更新する。入力はすぐ保存する（F-WO-07）。 */
    suspend fun updateSetValues(values: SetValues)

    suspend fun setCompleted(setId: String, completed: Boolean, at: Instant?)

    suspend fun deleteSet(setId: String)

    // ---- 前回の記録・自己ベスト ----

    /** 同じ種目の、直近のワークアウト（[excludeWorkoutId] を除く）の完了したセット。 */
    suspend fun previousRecord(exerciseId: String, excludeWorkoutId: String?): PreviousRecord?

    /** 種目の、[excludeWorkoutId] より前の最高値（自己ベスト。6.2）。記録がなければ null。 */
    suspend fun personalBest(exercise: Exercise, excludeWorkoutId: String?): Double?
}
