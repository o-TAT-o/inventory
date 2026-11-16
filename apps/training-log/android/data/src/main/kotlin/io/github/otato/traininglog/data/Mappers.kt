package io.github.otato.traininglog.data

import io.github.otato.traininglog.data.db.Gear as GearRow
import io.github.otato.traininglog.data.db.Routine as RoutineRow
import io.github.otato.traininglog.data.db.Routine_item
import io.github.otato.traininglog.data.db.SelectLastFinished
import io.github.otato.traininglog.data.db.Set_record
import io.github.otato.traininglog.data.db.Workout as WorkoutRow
import io.github.otato.traininglog.data.db.Workout_exercise
import io.github.otato.traininglog.data.db.Exercise as ExerciseRow
import io.github.otato.traininglog.data.db.Adjustment_field
import io.github.otato.traininglog.data.db.Exercise_baseline
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.SetType
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutExercise
import java.time.Instant
import java.time.LocalDate

// DB の行（SQLDelight が作る型）と、ドメインのモデルを行き来する。
// 列挙型は名前（文字列）で保存する。値の追加・並べ替えがあっても、保存したデータが壊れない。
// 真偽値は 0 / 1 の整数で保存する（SQLite に真偽値の型はない）。

internal fun Boolean.toLong(): Long = if (this) 1L else 0L

internal fun ExerciseRow.toDomain() = Exercise(
    id = id,
    parentId = parent_id,
    name = name,
    bodyPart = BodyPart.fromName(body_part),
    recordType = RecordType.valueOf(record_type),
    equipment = Equipment.valueOf(equipment),
    weightMode = WeightMode.valueOf(weight_mode),
    repMode = RepMode.valueOf(rep_mode),
    useAddedWeight = use_added_weight != 0L,
    weightStepKg = weight_step_kg,
    memo = memo,
    sortOrder = sort_order.toInt(),
    hidden = hidden != 0L,
)

internal fun Exercise_baseline.toDomain() = Baseline(
    exerciseId = exercise_id,
    weightKg = weight_kg,
    reps = reps?.toInt(),
    durationSec = duration_sec?.toInt(),
    best = best,
    sortOrder = sort_order.toInt(),
    sets = sets?.toInt(),
)

internal fun Adjustment_field.toDomain() = AdjustmentField(
    id = id,
    exerciseId = exercise_id,
    name = name,
    valueType = AdjustmentValueType.entries.firstOrNull { it.name == value_type } ?: AdjustmentValueType.Number,
    unit = unit,
    sortOrder = sort_order.toInt(),
)

internal fun GearRow.toDomain() = Gear(id = id, name = name, sortOrder = sort_order.toInt(), hidden = hidden != 0L)

internal fun RoutineRow.toDomain() = Routine(id = id, name = name, memo = memo, sortOrder = sort_order.toInt())

internal fun Routine_item.toDomain() = RoutineItem(
    id = id,
    routineId = routine_id,
    exerciseId = exercise_id,
    sortOrder = sort_order.toInt(),
    targetSets = target_sets?.toInt(),
    targetWeightKg = target_weight_kg,
    targetReps = target_reps?.toInt(),
    restSeconds = rest_seconds?.toInt(),
    restAfterSeconds = rest_after_seconds?.toInt(),
)

internal fun WorkoutRow.toDomain() = Workout(
    id = id,
    date = LocalDate.parse(date),
    routineId = routine_id,
    startedAt = Instant.ofEpochMilli(started_at),
    finishedAt = finished_at?.let(Instant::ofEpochMilli),
    memo = memo,
    quickEntry = quick_entry != 0L,
)

/** 「終えたワークアウト」の問い合わせは、finished_at が必ずある行になるので、別の型で返る。 */
internal fun SelectLastFinished.toDomain() = Workout(
    id = id,
    date = LocalDate.parse(date),
    routineId = routine_id,
    startedAt = Instant.ofEpochMilli(started_at),
    finishedAt = Instant.ofEpochMilli(finished_at),
    memo = memo,
    quickEntry = quick_entry != 0L,
)

internal fun Workout_exercise.toDomain() = WorkoutExercise(
    id = id,
    workoutId = workout_id,
    exerciseId = exercise_id,
    sortOrder = sort_order.toInt(),
    weightMode = WeightMode.valueOf(weight_mode),
    repMode = RepMode.valueOf(rep_mode),
    memo = memo,
    restSeconds = rest_seconds?.toInt(),
    restAfterSeconds = rest_after_seconds?.toInt(),
)

internal fun Set_record.toDomain() = SetRecord(
    id = id,
    workoutExerciseId = workout_exercise_id,
    sortOrder = sort_order.toInt(),
    weightKg = weight_kg,
    reps = reps.toInt(),
    durationSec = duration_sec?.toInt(),
    distanceM = distance_m,
    completed = completed != 0L,
    setType = SetType.valueOf(set_type),
    completedAt = completed_at?.let(Instant::ofEpochMilli),
)
