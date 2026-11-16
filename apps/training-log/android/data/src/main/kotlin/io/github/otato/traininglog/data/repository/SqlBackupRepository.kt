package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.toDomain
import io.github.otato.traininglog.data.toLong
import io.github.otato.traininglog.domain.model.AdjustmentValueRow
import io.github.otato.traininglog.domain.model.BackupSnapshot
import io.github.otato.traininglog.domain.model.WorkoutGearLink
import io.github.otato.traininglog.domain.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock

/** 全データの写しを作り、写しから全データを置き換える（F-DA-02、F-DA-03、F-DA-10）。 */
internal class SqlBackupRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) : BackupRepository {

    override suspend fun snapshot(): BackupSnapshot = withContext(io) {
        db.transactionWithResult {
            BackupSnapshot(
                exportedAt = clock.instant(),
                exercises = db.exerciseQueries.selectAll().executeAsList().map { it.toDomain() },
                adjustmentFields = db.adjustmentQueries.selectAllFields().executeAsList().map { it.toDomain() },
                gear = db.gearQueries.selectAll().executeAsList().map { it.toDomain() },
                routines = db.routineQueries.selectRoutines().executeAsList().map { it.toDomain() },
                routineItems = db.routineQueries.selectAllItems().executeAsList().map { it.toDomain() },
                workouts = db.workoutQueries.selectAllWorkouts().executeAsList().map { it.toDomain() },
                workoutExercises = db.workoutQueries.selectAllWorkoutExercises().executeAsList().map { it.toDomain() },
                sets = db.workoutQueries.selectAllSets().executeAsList().map { it.toDomain() },
                workoutGear = db.workoutQueries.selectAllWorkoutGear().executeAsList()
                    .map { WorkoutGearLink(it.workout_exercise_id, it.gear_id) },
                adjustmentValues = db.adjustmentQueries.selectAllValues().executeAsList()
                    .map { AdjustmentValueRow(it.workout_exercise_id, it.adjustment_field_id, it.content) },
                baselines = db.baselineQueries.selectAll().executeAsList().map { it.toDomain() },
                settings = db.settingQueries.selectAll().executeAsList().associate { it.key to it.content },
            )
        }
    }

    override suspend fun restore(snapshot: BackupSnapshot) {
        withContext(io) {
            db.transaction {
                // 親（外部キーで参照される側）の前に、子を消す
                db.adjustmentQueries.deleteAllValues()
                db.workoutQueries.deleteAllSets()
                db.workoutQueries.deleteAllWorkoutGear()
                db.workoutQueries.deleteAllWorkoutExercises()
                db.workoutQueries.deleteAllWorkouts()
                db.routineQueries.deleteAllItems()
                db.routineQueries.deleteAllRoutines()
                db.baselineQueries.deleteAllBaselines()
                db.adjustmentQueries.deleteAllFields()
                db.exerciseQueries.deleteAllExercises()
                db.gearQueries.deleteAllGear()
                // 設定は、この端末の状態（最後に書き出した日・購入）を除いて置き換える
                val keep = db.settingQueries.selectAll().executeAsList().filter { it.key in DEVICE_KEYS }
                db.settingQueries.deleteAllSettings()

                // 親（外部キーで参照される側）から入れる。バリエーションは、親の種目のあとに入れる
                snapshot.exercises.sortedBy { it.parentId != null }.forEach { e ->
                    db.exerciseQueries.insertExercise(
                        e.id, e.parentId, e.name, e.bodyPart.name, e.recordType.name, e.equipment.name, e.weightMode.name,
                        e.repMode.name, e.useAddedWeight.toLong(), e.weightStepKg, e.memo, e.sortOrder.toLong(), e.hidden.toLong(),
                    )
                }
                snapshot.adjustmentFields.forEach { f ->
                    db.adjustmentQueries.insertField(f.id, f.exerciseId, f.name, f.valueType.name, f.unit, f.sortOrder.toLong())
                }
                snapshot.gear.forEach { g -> db.gearQueries.insertGear(g.id, g.name, g.sortOrder.toLong(), g.hidden.toLong()) }
                snapshot.routines.forEach { r -> db.routineQueries.insertRoutine(r.id, r.name, r.memo, r.sortOrder.toLong()) }
                snapshot.routineItems.forEach { i ->
                    db.routineQueries.insertItem(
                        id = i.id,
                        routine_id = i.routineId,
                        exercise_id = i.exerciseId,
                        sort_order = i.sortOrder.toLong(),
                        target_sets = i.targetSets?.toLong(),
                        target_weight_kg = i.targetWeightKg,
                        target_reps = i.targetReps?.toLong(),
                        rest_seconds = i.restSeconds?.toLong(),
                        rest_after_seconds = i.restAfterSeconds?.toLong(),
                    )
                }
                snapshot.workouts.forEach { w ->
                    db.workoutQueries.insertWorkout(
                        w.id, w.date.toString(), w.routineId, w.startedAt.toEpochMilli(), w.finishedAt?.toEpochMilli(), w.memo,
                        if (w.quickEntry) 1L else 0L,
                    )
                }
                snapshot.workoutExercises.forEach { we ->
                    db.workoutQueries.insertWorkoutExercise(
                        we.id, we.workoutId, we.exerciseId, we.sortOrder.toLong(), we.weightMode.name, we.repMode.name,
                        we.memo, we.restSeconds?.toLong(), we.restAfterSeconds?.toLong(),
                    )
                }
                snapshot.sets.forEach { s ->
                    db.workoutQueries.insertSet(
                        s.id, s.workoutExerciseId, s.sortOrder.toLong(), s.weightKg, s.reps.toLong(), s.durationSec?.toLong(),
                        s.distanceM, s.completed.toLong(), s.setType.name, s.completedAt?.toEpochMilli(),
                    )
                }
                snapshot.workoutGear.forEach { db.workoutQueries.insertWorkoutExerciseGear(it.workoutExerciseId, it.gearId) }
                snapshot.adjustmentValues.forEach { db.adjustmentQueries.upsertValue(it.workoutExerciseId, it.fieldId, it.value) }
                snapshot.baselines.forEach { b ->
                    db.baselineQueries.insertBaseline(
                        b.exerciseId, b.weightKg, b.reps?.toLong(), b.durationSec?.toLong(), b.best, b.sortOrder.toLong(),
                        clock.millis(), b.sets?.toLong(),
                    )
                }
                snapshot.settings.filterKeys { it !in DEVICE_KEYS }.forEach { (key, value) -> db.settingQueries.upsert(key, value) }
                keep.forEach { db.settingQueries.upsert(it.key, it.content) }
            }
        }
    }

    private companion object {
        /** この端末の状態を表す設定。写しからは、置き換えない。 */
        val DEVICE_KEYS = setOf("last_export_at", "last_auto_backup_at", "ads_removed")
    }
}
