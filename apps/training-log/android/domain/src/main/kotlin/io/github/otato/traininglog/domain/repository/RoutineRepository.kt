package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineDetail
import io.github.otato.traininglog.domain.model.RoutineItem
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    /** ルーティンの一覧（並び順どおり。中の種目つき）。 */
    fun observeRoutines(): Flow<List<RoutineDetail>>

    fun observeRoutine(id: String): Flow<RoutineDetail?>

    suspend fun getRoutine(id: String): RoutineDetail?

    /** 追加または更新する。 */
    suspend fun saveRoutine(routine: Routine)

    suspend fun deleteRoutine(id: String)

    /** ルーティンの種目を追加または更新する。 */
    suspend fun saveItem(item: RoutineItem)

    suspend fun deleteItem(itemId: String)

    /** 次に使う並び順の番号（ルーティン一覧用）。 */
    suspend fun nextRoutineSortOrder(): Int

    /** 次に使う並び順の番号（ルーティンの中の種目用）。 */
    suspend fun nextItemSortOrder(routineId: String): Int
}
