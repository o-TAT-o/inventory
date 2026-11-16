package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    /** 種目の一覧（並び順どおり）。非表示のものは、[includeHidden] が true のときだけ含める。 */
    fun observeExercises(includeHidden: Boolean = false): Flow<List<Exercise>>

    fun observeExercise(id: String): Flow<Exercise?>

    suspend fun getExercise(id: String): Exercise?

    /** 追加または更新する。 */
    suspend fun save(exercise: Exercise)

    /** 記録がなければ消し、あれば記録を残したまま非表示にする（F-EX-05）。 */
    suspend fun deleteOrHide(id: String)

    /** 次に使う並び順の番号。 */
    suspend fun nextSortOrder(): Int

    /** 種目が 1 つもないときだけ、代表的な種目をプリセットとして入れる（F-EX-01）。 */
    suspend fun seedPresetsIfEmpty()

    // ---- 並べ替え ----

    /** 種目の並びを変える（F-UI-01）。[orderedIds] は、種目の id を、新しい順に並べたもの。 */
    suspend fun reorder(orderedIds: List<String>)

    // ---- バリエーション（F-EX-14〜18） ----

    /** 親の種目のバリエーション（非表示のものは除く）。 */
    fun observeChildren(parentId: String): Flow<List<Exercise>>

    /** 種目ごとの、記録（終えたワークアウトの種目）の数。バリエーションの一覧の「記録 24 回」に使う。 */
    fun observeRecordCounts(): Flow<Map<String, Int>>

    /**
     * 記録を、ほかの種目へまとめて移す。記録がすでにある種目に、後からバリエーションを作ったとき、
     * それまでの記録を、選んだバリエーションへ移すのに使う（F-EX-18）。
     */
    suspend fun moveRecords(fromExerciseId: String, toExerciseId: String)

    // ---- 調整項目（F-EX-10） ----

    fun observeAdjustmentFields(exerciseId: String): Flow<List<AdjustmentField>>

    suspend fun getAdjustmentFields(exerciseId: String): List<AdjustmentField>

    /**
     * 種目の調整項目を、[fields] に置き換える（無くなった項目は消え、その値も消える。新しい項目は足される）。
     */
    suspend fun saveAdjustmentFields(exerciseId: String, fields: List<AdjustmentField>)
}
