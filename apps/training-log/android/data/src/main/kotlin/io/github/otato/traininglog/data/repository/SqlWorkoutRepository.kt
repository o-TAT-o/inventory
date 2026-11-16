package io.github.otato.traininglog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.data.toDomain
import io.github.otato.traininglog.data.toLong
import io.github.otato.traininglog.domain.logic.Baselines
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PlannedWorkoutExercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutSummary
import io.github.otato.traininglog.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

internal class SqlWorkoutRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) : WorkoutRepository {
    private val queries = db.workoutQueries
    private val exerciseQueries = db.exerciseQueries
    private val routineQueries = db.routineQueries
    private val baselineQueries = db.baselineQueries

    // ---- 観察 ----

    override fun observeWorkout(id: String): Flow<WorkoutDetail?> = combine(
        queries.selectWorkout(id).asFlow().mapToOneOrNull(io),
        queries.selectExercisesOf(id).asFlow().mapToList(io),
        queries.selectSetsOfWorkout(id).asFlow().mapToList(io),
        queries.selectGearOfWorkout(id).asFlow().mapToList(io),
        exerciseQueries.selectAll().asFlow().mapToList(io),
    ) { workout, exercises, sets, gear, catalog ->
        workout?.let {
            WorkoutAssembler.detail(
                workout = it.toDomain(),
                exercises = exercises.map { row -> row.toDomain() },
                sets = sets.map { row -> row.toDomain() },
                gearByWorkoutExercise = gear.groupBy({ row -> row.workout_exercise_id }, { row -> row.gear_id })
                    .mapValues { (_, ids) -> ids.toSet() },
                catalog = catalog.map { row -> row.toDomain() }.associateBy { exercise -> exercise.id },
            )
        }
    }

    override fun observeActiveWorkout(): Flow<Workout?> =
        queries.selectActive().asFlow().mapToOneOrNull(io).map { it?.toDomain() }

    override fun observeSummaries(from: LocalDate, to: LocalDate): Flow<List<WorkoutSummary>> = combine(
        queries.selectBetween(from.toString(), to.toString()).asFlow().mapToList(io),
        queries.selectExercisesBetween(from.toString(), to.toString()).asFlow().mapToList(io),
        queries.selectSetsBetween(from.toString(), to.toString()).asFlow().mapToList(io),
        exerciseQueries.selectAll().asFlow().mapToList(io),
        routineQueries.selectRoutines().asFlow().mapToList(io),
    ) { workouts, exercises, sets, catalog, routines ->
        WorkoutAssembler.summaries(
            workouts = workouts.map { it.toDomain() },
            exercises = exercises.map { it.toDomain() },
            sets = sets.map { it.toDomain() },
            catalog = catalog.map { it.toDomain() }.associateBy { it.id },
            routineNames = routines.associate { it.id to it.name },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLastFinished(): Flow<WorkoutSummary?> =
        queries.selectLastFinished().asFlow().mapToOneOrNull(io).flatMapLatest { row ->
            if (row == null) {
                flowOf(null)
            } else {
                combine(
                    queries.selectExercisesOf(row.id).asFlow().mapToList(io),
                    queries.selectSetsOfWorkout(row.id).asFlow().mapToList(io),
                    exerciseQueries.selectAll().asFlow().mapToList(io),
                    routineQueries.selectRoutines().asFlow().mapToList(io),
                ) { exercises, sets, catalog, routines ->
                    WorkoutAssembler.summaries(
                        workouts = listOf(row.toDomain()),
                        exercises = exercises.map { it.toDomain() },
                        sets = sets.map { it.toDomain() },
                        catalog = catalog.map { it.toDomain() }.associateBy { it.id },
                        routineNames = routines.associate { it.id to it.name },
                    ).firstOrNull()
                }
            }
        }

    override fun observeLastRoutineId(): Flow<String?> =
        queries.selectLastRoutineId().asFlow().mapToList(io).map { it.firstOrNull() }

    override fun observeExerciseHistory(exerciseId: String): Flow<List<PreviousRecord>> = combine(
        queries.selectHistoryExercises(exerciseId).asFlow().mapToList(io),
        queries.selectHistorySets(exerciseId).asFlow().mapToList(io),
        queries.selectHistoryGear(exerciseId).asFlow().mapToList(io),
    ) { sessions, sets, gear ->
        val setsBySession = sets.map { it.toDomain() }.groupBy { it.workoutExerciseId }
        val gearBySession = gear.groupBy({ it.workout_exercise_id }, { it.gear_id })
        sessions.map { session ->
            PreviousRecord(
                date = LocalDate.parse(session.date),
                sets = setsBySession[session.workout_exercise_id].orEmpty(),
                weightMode = WeightMode.valueOf(session.weight_mode),
                repMode = RepMode.valueOf(session.rep_mode),
                gearIds = gearBySession[session.workout_exercise_id].orEmpty().toSet(),
            )
        }
    }

    // ---- ワークアウト ----

    override suspend fun createWorkout(workout: Workout, exercises: List<PlannedWorkoutExercise>) {
        withContext(io) {
            db.transaction {
                queries.insertWorkout(
                    workout.id,
                    workout.date.toString(),
                    workout.routineId,
                    workout.startedAt.toEpochMilli(),
                    workout.finishedAt?.toEpochMilli(),
                    workout.memo,
                )
                exercises.forEach { insertPlanned(it, workout.id) }
            }
        }
    }

    override suspend fun updateDate(workoutId: String, date: LocalDate) {
        withContext(io) { queries.updateDate(date = date.toString(), id = workoutId) }
    }

    override suspend fun updateMemo(workoutId: String, memo: String) {
        withContext(io) { queries.updateMemo(memo = memo, id = workoutId) }
    }

    override suspend fun finishWorkout(workoutId: String, at: Instant): Boolean = withContext(io) {
        db.transactionWithResult {
            if (queries.countCompletedSets(workoutId).executeAsOne() == 0L) {
                queries.deleteWorkout(workoutId)
                false
            } else {
                queries.deleteIncompleteSets(workoutId)
                queries.deleteEmptyExercises(workoutId)
                queries.updateFinishedAt(finishedAt = at.toEpochMilli(), id = workoutId)
                true
            }
        }
    }

    override suspend fun deleteWorkout(workoutId: String) {
        withContext(io) { queries.deleteWorkout(workoutId) }
    }

    // ---- 種目 ----

    override suspend fun addExercise(planned: PlannedWorkoutExercise, workoutId: String) {
        withContext(io) { db.transaction { insertPlanned(planned, workoutId) } }
    }

    override suspend fun removeExercise(workoutExerciseId: String) {
        withContext(io) { queries.deleteWorkoutExercise(workoutExerciseId) }
    }

    override suspend fun updateExerciseMemo(workoutExerciseId: String, memo: String) {
        withContext(io) { queries.updateWorkoutExerciseMemo(memo = memo, id = workoutExerciseId) }
    }

    override suspend fun updateExerciseModes(workoutExerciseId: String, weightMode: WeightMode, repMode: RepMode) {
        withContext(io) {
            queries.updateWorkoutExerciseModes(weightMode = weightMode.name, repMode = repMode.name, id = workoutExerciseId)
        }
    }

    override suspend fun setGear(workoutExerciseId: String, gearIds: Set<String>) {
        withContext(io) {
            db.transaction {
                queries.deleteWorkoutExerciseGear(workoutExerciseId)
                gearIds.forEach { queries.insertWorkoutExerciseGear(workoutExerciseId, it) }
            }
        }
    }

    // ---- セット ----

    override suspend fun addSet(set: SetRecord) {
        withContext(io) { insertSet(set) }
    }

    override suspend fun updateSetValues(values: SetValues) {
        withContext(io) {
            queries.updateSetValues(
                weightKg = values.weightKg,
                reps = values.reps.toLong(),
                durationSec = values.durationSec?.toLong(),
                distanceM = values.distanceM,
                id = values.setId,
            )
        }
    }

    override suspend fun setCompleted(setId: String, completed: Boolean, at: Instant?) {
        withContext(io) {
            queries.updateSetCompleted(completed = completed.toLong(), completedAt = at?.toEpochMilli(), id = setId)
        }
    }

    override suspend fun deleteSet(setId: String) {
        withContext(io) { queries.deleteSet(setId) }
    }

    // ---- 前回の記録・自己ベスト ----

    override suspend fun previousRecord(exerciseId: String, excludeWorkoutId: String?): PreviousRecord? = withContext(io) {
        val session = queries.selectPreviousExercise(exerciseId, excludeWorkoutId).executeAsOneOrNull()
            // 実際の記録がなければ、乗り換え用の初期設定で入れた直近の値を、前回の記録として使う（F-DA-06）
            ?: return@withContext baselinePrevious(exerciseId)
        PreviousRecord(
            date = LocalDate.parse(session.date),
            sets = queries.selectCompletedSetsOfExercise(session.workout_exercise_id).executeAsList().map { it.toDomain() },
            weightMode = WeightMode.valueOf(session.weight_mode),
            repMode = RepMode.valueOf(session.rep_mode),
            gearIds = queries.selectGearOfExercise(session.workout_exercise_id).executeAsList().toSet(),
        )
    }

    override suspend fun personalBest(exercise: Exercise, excludeWorkoutId: String?): Double? = withContext(io) {
        val best = queries.selectBest(exercise.id, excludeWorkoutId).executeAsOne()
        val recorded = when (exercise.recordType) {
            RecordType.WeightReps -> best.best_weight
            RecordType.Bodyweight -> if (exercise.useAddedWeight) best.best_weight else best.best_reps?.toDouble()
            RecordType.Duration -> best.best_duration?.toDouble()
            RecordType.DistanceTime -> best.best_distance
        }
        // 乗り換え用の初期設定で入れた自己ベストも含める（F-DA-06）
        val baseline = baselineQueries.selectByExercise(exercise.id).executeAsOneOrNull()
            ?.let { Baselines.effectiveBest(it.toDomain(), exercise) }
        listOfNotNull(recorded, baseline).maxOrNull()
    }

    private fun baselinePrevious(exerciseId: String): PreviousRecord? {
        val row = baselineQueries.selectByExercise(exerciseId).executeAsOneOrNull() ?: return null
        val exercise = exerciseQueries.selectById(exerciseId).executeAsOneOrNull()?.toDomain() ?: return null
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(row.updated_at), clock.zone)
        return Baselines.toPreviousRecord(row.toDomain(), exercise, date)
    }

    // ---- 内部 ----

    private fun insertPlanned(planned: PlannedWorkoutExercise, workoutId: String) {
        val we = planned.workoutExercise
        queries.insertWorkoutExercise(
            we.id,
            workoutId,
            we.exerciseId,
            we.sortOrder.toLong(),
            we.weightMode.name,
            we.repMode.name,
            we.memo,
            we.restSeconds?.toLong(),
            we.restAfterSeconds?.toLong(),
        )
        planned.sets.forEach { insertSet(it) }
        planned.gearIds.forEach { queries.insertWorkoutExerciseGear(we.id, it) }
    }

    private fun insertSet(set: SetRecord) {
        queries.insertSet(
            set.id,
            set.workoutExerciseId,
            set.sortOrder.toLong(),
            set.weightKg,
            set.reps.toLong(),
            set.durationSec?.toLong(),
            set.distanceM,
            set.completed.toLong(),
            set.setType.name,
            set.completedAt?.toEpochMilli(),
        )
    }
}
