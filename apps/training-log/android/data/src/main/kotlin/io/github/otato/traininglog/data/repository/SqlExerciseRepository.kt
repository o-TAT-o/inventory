package io.github.otato.traininglog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.otato.traininglog.data.PresetData
import io.github.otato.traininglog.data.db.ExerciseQueries
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.toDomain
import io.github.otato.traininglog.data.toLong
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.repository.ExerciseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqlExerciseRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
) : ExerciseRepository {
    private val queries = db.exerciseQueries
    private val gearQueries = db.gearQueries
    private val workoutQueries = db.workoutQueries
    private val adjustmentQueries = db.adjustmentQueries

    override fun observeExercises(includeHidden: Boolean): Flow<List<Exercise>> {
        val query = if (includeHidden) queries.selectAll() else queries.selectVisible()
        return query.asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeExercise(id: String): Flow<Exercise?> =
        queries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.toDomain() }

    override suspend fun getExercise(id: String): Exercise? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun save(exercise: Exercise) {
        withContext(io) {
            db.transaction {
                // INSERT OR REPLACE は、子の行（記録）を巻き込んで消すおそれがあるので使わない
                val exists = queries.selectById(exercise.id).executeAsOneOrNull() != null
                if (exists) {
                    queries.updateExercise(
                        parentId = exercise.parentId,
                        name = exercise.name,
                        bodyPart = exercise.bodyPart.name,
                        recordType = exercise.recordType.name,
                        equipment = exercise.equipment.name,
                        weightMode = exercise.weightMode.name,
                        repMode = exercise.repMode.name,
                        useAddedWeight = exercise.useAddedWeight.toLong(),
                        weightStepKg = exercise.weightStepKg,
                        memo = exercise.memo,
                        sortOrder = exercise.sortOrder.toLong(),
                        hidden = exercise.hidden.toLong(),
                        id = exercise.id,
                    )
                } else {
                    queries.insertOf(exercise)
                }
            }
        }
    }

    override suspend fun deleteOrHide(id: String) {
        withContext(io) {
            db.transaction {
                // バリエーション（フォーム）は、親の種目と一緒に消す。残すと、フォームの名前だけの種目になってしまう
                queries.selectChildren(id).executeAsList().forEach { deleteOrHideOne(it.id) }
                // 記録のあるバリエーションが（非表示で）残るなら、親も消さずに非表示にする（記録に、親の名前を出すため）
                val childrenRemain = queries.selectChildren(id).executeAsList().isNotEmpty()
                if (childrenRemain) queries.hideById(id) else deleteOrHideOne(id)
            }
        }
    }

    /** 記録やルーティンで使われていれば非表示にし、使われていなければ消す。 */
    private fun deleteOrHideOne(id: String) {
        val used = queries.usageInWorkouts(id).executeAsOne() > 0 || queries.usageInRoutines(id).executeAsOne() > 0
        if (used) queries.hideById(id) else queries.deleteById(id)
    }

    override suspend fun nextSortOrder(): Int = withContext(io) {
        queries.maxSortOrder().executeAsOne().toInt() + 1
    }

    override suspend fun seedPresetsIfEmpty() {
        withContext(io) {
            db.transaction {
                if (queries.count().executeAsOne() == 0L) {
                    PresetData.exercises().forEach { queries.insertOf(it) }
                }
                if (gearQueries.count().executeAsOne() == 0L) {
                    PresetData.gear().forEach { gear ->
                        gearQueries.insertGear(gear.id, gear.name, gear.sortOrder.toLong(), gear.hidden.toLong())
                    }
                }
            }
        }
    }

    override suspend fun reorder(orderedIds: List<String>) {
        withContext(io) {
            db.transaction { orderedIds.forEachIndexed { index, id -> queries.updateSortOrder(index.toLong(), id) } }
        }
    }

    override fun observeChildren(parentId: String): Flow<List<Exercise>> =
        queries.selectChildren(parentId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() }.filter { !it.hidden } }

    override fun observeRecordCounts(): Flow<Map<String, Int>> =
        queries.countRecords().asFlow().mapToList(io).map { rows -> rows.associate { it.exercise_id to it.records.toInt() } }

    override suspend fun moveRecords(fromExerciseId: String, toExerciseId: String) {
        withContext(io) { workoutQueries.moveRecords(toExerciseId = toExerciseId, fromExerciseId = fromExerciseId) }
    }

    override fun observeAdjustmentFields(exerciseId: String): Flow<List<AdjustmentField>> =
        adjustmentQueries.selectFieldsOf(exerciseId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAdjustmentFields(exerciseId: String): List<AdjustmentField> = withContext(io) {
        adjustmentQueries.selectFieldsOf(exerciseId).executeAsList().map { it.toDomain() }
    }

    override suspend fun saveAdjustmentFields(exerciseId: String, fields: List<AdjustmentField>) {
        withContext(io) {
            db.transaction {
                val existing = adjustmentQueries.selectFieldsOf(exerciseId).executeAsList().map { it.id }.toSet()
                // 無くなった項目は消す（記録した値も、一緒に消える）
                (existing - fields.map { it.id }.toSet()).forEach { adjustmentQueries.deleteField(it) }
                fields.forEachIndexed { index, field ->
                    if (field.id in existing) {
                        adjustmentQueries.updateField(
                            name = field.name,
                            valueType = field.valueType.name,
                            unit = field.unit,
                            sortOrder = index.toLong(),
                            id = field.id,
                        )
                    } else {
                        adjustmentQueries.insertField(field.id, exerciseId, field.name, field.valueType.name, field.unit, index.toLong())
                    }
                }
            }
        }
    }

    private fun ExerciseQueries.insertOf(exercise: Exercise) {
        insertExercise(
            exercise.id,
            exercise.parentId,
            exercise.name,
            exercise.bodyPart.name,
            exercise.recordType.name,
            exercise.equipment.name,
            exercise.weightMode.name,
            exercise.repMode.name,
            exercise.useAddedWeight.toLong(),
            exercise.weightStepKg,
            exercise.memo,
            exercise.sortOrder.toLong(),
            exercise.hidden.toLong(),
        )
    }
}
