package io.github.otato.traininglog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.toDomain
import io.github.otato.traininglog.data.toLong
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.repository.GearRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqlGearRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
) : GearRepository {
    private val queries = db.gearQueries

    override fun observeGear(includeHidden: Boolean): Flow<List<Gear>> {
        val query = if (includeHidden) queries.selectAll() else queries.selectVisible()
        return query.asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun save(gear: Gear) {
        withContext(io) {
            db.transaction {
                val exists = queries.selectById(gear.id).executeAsOneOrNull() != null
                if (exists) {
                    queries.updateGear(
                        name = gear.name,
                        sortOrder = gear.sortOrder.toLong(),
                        hidden = gear.hidden.toLong(),
                        id = gear.id,
                    )
                } else {
                    queries.insertGear(gear.id, gear.name, gear.sortOrder.toLong(), gear.hidden.toLong())
                }
            }
        }
    }

    override suspend fun nextSortOrder(): Int = withContext(io) {
        queries.selectAll().executeAsList().maxOfOrNull { it.sort_order.toInt() }?.plus(1) ?: 0
    }

    override suspend fun reorder(orderedIds: List<String>) {
        withContext(io) {
            db.transaction { orderedIds.forEachIndexed { index, id -> queries.updateSortOrder(index.toLong(), id) } }
        }
    }

    override fun observeUsage(): Flow<Map<String, Int>> =
        db.workoutQueries.selectGearUsage().asFlow().mapToList(io).map { rows -> rows.associate { it.gear_id to it.used.toInt() } }
}
