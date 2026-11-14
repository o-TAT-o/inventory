package io.github.otato.traininglog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.toDomain
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineDetail
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.RoutineItemDetail
import io.github.otato.traininglog.domain.repository.RoutineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqlRoutineRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
) : RoutineRepository {
    private val queries = db.routineQueries
    private val exerciseQueries = db.exerciseQueries

    override fun observeRoutines(): Flow<List<RoutineDetail>> = combine(
        queries.selectRoutines().asFlow().mapToList(io),
        queries.selectAllItems().asFlow().mapToList(io),
        exerciseQueries.selectAll().asFlow().mapToList(io),
    ) { routines, items, exercises ->
        val catalog = exercises.map { it.toDomain() }.associateBy { it.id }
        val itemsByRoutine = items.map { it.toDomain() }.groupBy { it.routineId }
        routines.map { row -> detail(row.toDomain(), itemsByRoutine[row.id].orEmpty(), catalog) }
    }

    override fun observeRoutine(id: String): Flow<RoutineDetail?> = observeRoutines().map { list ->
        list.firstOrNull { it.routine.id == id }
    }

    override suspend fun getRoutine(id: String): RoutineDetail? = withContext(io) {
        val routine = queries.selectRoutineById(id).executeAsOneOrNull()?.toDomain() ?: return@withContext null
        val catalog = exerciseQueries.selectAll().executeAsList().map { it.toDomain() }.associateBy { it.id }
        detail(routine, queries.selectItemsOf(id).executeAsList().map { it.toDomain() }, catalog)
    }

    override suspend fun saveRoutine(routine: Routine) = withContext(io) {
        db.transaction {
            val exists = queries.selectRoutineById(routine.id).executeAsOneOrNull() != null
            if (exists) {
                queries.updateRoutine(name = routine.name, memo = routine.memo, sortOrder = routine.sortOrder.toLong(), id = routine.id)
            } else {
                queries.insertRoutine(routine.id, routine.name, routine.memo, routine.sortOrder.toLong())
            }
        }
    }

    override suspend fun deleteRoutine(id: String) {
        withContext(io) { queries.deleteRoutine(id) }
    }

    override suspend fun saveItem(item: RoutineItem) = withContext(io) {
        db.transaction {
            val exists = queries.selectItemById(item.id).executeAsOneOrNull() != null
            if (exists) {
                queries.updateItem(
                    exerciseId = item.exerciseId,
                    sortOrder = item.sortOrder.toLong(),
                    targetSets = item.targetSets?.toLong(),
                    targetWeightKg = item.targetWeightKg,
                    targetReps = item.targetReps?.toLong(),
                    restSeconds = item.restSeconds?.toLong(),
                    restAfterSeconds = item.restAfterSeconds?.toLong(),
                    id = item.id,
                )
            } else {
                queries.insertItem(
                    id = item.id,
                    routine_id = item.routineId,
                    exercise_id = item.exerciseId,
                    sort_order = item.sortOrder.toLong(),
                    target_sets = item.targetSets?.toLong(),
                    target_weight_kg = item.targetWeightKg,
                    target_reps = item.targetReps?.toLong(),
                    rest_seconds = item.restSeconds?.toLong(),
                    rest_after_seconds = item.restAfterSeconds?.toLong(),
                )
            }
        }
    }

    override suspend fun deleteItem(itemId: String) {
        withContext(io) { queries.deleteItem(itemId) }
    }

    override suspend fun nextRoutineSortOrder(): Int = withContext(io) {
        queries.maxRoutineSortOrder().executeAsOne().toInt() + 1
    }

    override suspend fun nextItemSortOrder(routineId: String): Int = withContext(io) {
        queries.maxItemSortOrder(routineId).executeAsOne().toInt() + 1
    }

    private fun detail(routine: Routine, items: List<RoutineItem>, catalog: Map<String, Exercise>) = RoutineDetail(
        routine = routine,
        // 種目が（何らかの理由で）見つからない行は出さない
        items = items.sortedBy { it.sortOrder }.mapNotNull { item ->
            catalog[item.exerciseId]?.let { RoutineItemDetail(item, it) }
        },
    )
}
