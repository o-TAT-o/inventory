package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.Gear
import kotlinx.coroutines.flow.Flow

interface GearRepository {
    /** ギアの一覧（並び順どおり）。非表示のものは、[includeHidden] が true のときだけ含める。 */
    fun observeGear(includeHidden: Boolean = false): Flow<List<Gear>>

    /** 追加または更新する。 */
    suspend fun save(gear: Gear)

    suspend fun nextSortOrder(): Int

    /** ギアの並びを変える（F-GR-01）。[orderedIds] は、ギアの id を、新しい順に並べたもの。 */
    suspend fun reorder(orderedIds: List<String>)

    /** ギアごとの、使った回数（ワークアウトの種目の数）。 */
    fun observeUsage(): Flow<Map<String, Int>>
}
