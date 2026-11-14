package io.github.otato.traininglog.domain.repository

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
}
