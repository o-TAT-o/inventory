package io.github.otato.traininglog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.toDomain
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.BaselineDetail
import io.github.otato.traininglog.domain.repository.BaselineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Clock

internal class SqlBaselineRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) : BaselineRepository {
    private val queries = db.baselineQueries
    private val exerciseQueries = db.exerciseQueries

    override fun observeBaselines(): Flow<List<BaselineDetail>> = combine(
        queries.selectAll().asFlow().mapToList(io),
        exerciseQueries.selectAll().asFlow().mapToList(io),
    ) { rows, exercises ->
        val catalog = exercises.map { it.toDomain() }.associateBy { it.id }
        // 種目が見つからない行は出さない
        rows.mapNotNull { row -> catalog[row.exercise_id]?.let { BaselineDetail(row.toDomain(), it) } }
    }

    override suspend fun addExercises(exerciseIds: List<String>) {
        withContext(io) {
            db.transaction {
                var next = queries.maxSortOrder().executeAsOne() + 1
                exerciseIds.forEach { id ->
                    if (queries.selectByExercise(id).executeAsOneOrNull() == null) {
                        queries.insertBaseline(id, null, null, null, null, next++, clock.millis(), null)
                    }
                }
            }
        }
    }

    override suspend fun save(baseline: Baseline) {
        withContext(io) {
            db.transaction {
                // INSERT OR REPLACE は使わない（並び順を保つため）
                if (queries.selectByExercise(baseline.exerciseId).executeAsOneOrNull() == null) {
                    queries.insertBaseline(
                        baseline.exerciseId,
                        baseline.weightKg,
                        baseline.reps?.toLong(),
                        baseline.durationSec?.toLong(),
                        baseline.best,
                        queries.maxSortOrder().executeAsOne() + 1,
                        clock.millis(),
                        baseline.sets?.toLong(),
                    )
                } else {
                    queries.updateBaseline(
                        weightKg = baseline.weightKg,
                        reps = baseline.reps?.toLong(),
                        durationSec = baseline.durationSec?.toLong(),
                        best = baseline.best,
                        sets = baseline.sets?.toLong(),
                        updatedAt = clock.millis(),
                        exerciseId = baseline.exerciseId,
                    )
                }
            }
        }
    }

    override suspend fun remove(exerciseId: String) {
        withContext(io) { queries.deleteBaseline(exerciseId) }
    }
}
