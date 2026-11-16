package io.github.otato.traininglog.data.backup

import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueRow
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.BackupFormatException
import io.github.otato.traininglog.domain.model.BackupSnapshot
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
import io.github.otato.traininglog.domain.model.WorkoutGearLink
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * 全データの書き出しファイル（JSON）の読み書き（F-DA-02、F-DA-03）。
 *
 * - ファイルには、形式の版（`formatVersion`）を入れる。新しすぎる版は断り、知らない項目は読み飛ばす（`ignoreUnknownKeys`）。
 *   形式を変えるときは、版を上げ、古い版も読めるようにする（要件 6.2）
 * - 数え方・部位などの列挙は、名前（文字）で持つ。読めない名前は、初期値にする
 * - 日付は `yyyy-MM-dd`、時刻はエポックミリ秒
 */
object BackupJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(BackupFile.serializer(), BackupFile.from(snapshot))

    /** @throws BackupFormatException ファイルが読めないとき、このアプリより新しい形式のとき */
    fun decode(text: String): BackupSnapshot {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException("このファイルは、読み込めません（このアプリの書き出しファイルではないようです）")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("このファイルは、読み込めません（このアプリの書き出しファイルではないようです）")
        }
        if (file.app != APP_NAME) throw BackupFormatException("このファイルは、読み込めません（このアプリの書き出しファイルではないようです）")
        if (file.formatVersion > BackupSnapshot.FORMAT_VERSION) {
            throw BackupFormatException("このファイルは、新しい版のアプリで書き出したものです。アプリを新しくしてから、読み込んでください")
        }
        return file.toSnapshot()
    }

    internal const val APP_NAME = "training-log"
}

@Serializable
internal data class BackupFile(
    val app: String = BackupJson.APP_NAME,
    val formatVersion: Int,
    val exportedAt: Long,
    val exercises: List<ExerciseDto> = emptyList(),
    val adjustmentFields: List<AdjustmentFieldDto> = emptyList(),
    val gear: List<GearDto> = emptyList(),
    val routines: List<RoutineDto> = emptyList(),
    val routineItems: List<RoutineItemDto> = emptyList(),
    val workouts: List<WorkoutDto> = emptyList(),
    val workoutExercises: List<WorkoutExerciseDto> = emptyList(),
    val sets: List<SetDto> = emptyList(),
    val workoutGear: List<WorkoutGearDto> = emptyList(),
    val adjustmentValues: List<AdjustmentValueDto> = emptyList(),
    val baselines: List<BaselineDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
) {
    fun toSnapshot() = BackupSnapshot(
        formatVersion = formatVersion,
        exportedAt = Instant.ofEpochMilli(exportedAt),
        exercises = exercises.map { it.toDomain() },
        adjustmentFields = adjustmentFields.map { it.toDomain() },
        gear = gear.map { it.toDomain() },
        routines = routines.map { it.toDomain() },
        routineItems = routineItems.map { it.toDomain() },
        workouts = workouts.map { it.toDomain() },
        workoutExercises = workoutExercises.map { it.toDomain() },
        sets = sets.map { it.toDomain() },
        workoutGear = workoutGear.map { WorkoutGearLink(it.workoutExerciseId, it.gearId) },
        adjustmentValues = adjustmentValues.map { AdjustmentValueRow(it.workoutExerciseId, it.fieldId, it.value) },
        baselines = baselines.map { it.toDomain() },
        settings = settings,
    )

    companion object {
        fun from(s: BackupSnapshot) = BackupFile(
            formatVersion = s.formatVersion,
            exportedAt = s.exportedAt.toEpochMilli(),
            exercises = s.exercises.map(ExerciseDto::from),
            adjustmentFields = s.adjustmentFields.map(AdjustmentFieldDto::from),
            gear = s.gear.map(GearDto::from),
            routines = s.routines.map(RoutineDto::from),
            routineItems = s.routineItems.map(RoutineItemDto::from),
            workouts = s.workouts.map(WorkoutDto::from),
            workoutExercises = s.workoutExercises.map(WorkoutExerciseDto::from),
            sets = s.sets.map(SetDto::from),
            workoutGear = s.workoutGear.map { WorkoutGearDto(it.workoutExerciseId, it.gearId) },
            adjustmentValues = s.adjustmentValues.map { AdjustmentValueDto(it.workoutExerciseId, it.fieldId, it.value) },
            baselines = s.baselines.map(BaselineDto::from),
            settings = s.settings,
        )
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

@Serializable
internal data class ExerciseDto(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val bodyPart: String = BodyPart.Other.name,
    val recordType: String = RecordType.WeightReps.name,
    val equipment: String = Equipment.Other.name,
    val weightMode: String = WeightMode.Total.name,
    val repMode: String = RepMode.Normal.name,
    val useAddedWeight: Boolean = false,
    val weightStepKg: Double? = null,
    val memo: String = "",
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
) {
    fun toDomain() = Exercise(
        id = id,
        parentId = parentId,
        name = name,
        bodyPart = BodyPart.fromName(bodyPart),
        recordType = enumOr(recordType, RecordType.WeightReps),
        equipment = enumOr(equipment, Equipment.Other),
        weightMode = enumOr(weightMode, WeightMode.Total),
        repMode = enumOr(repMode, RepMode.Normal),
        useAddedWeight = useAddedWeight,
        weightStepKg = weightStepKg,
        memo = memo,
        sortOrder = sortOrder,
        hidden = hidden,
    )

    companion object {
        fun from(e: Exercise) = ExerciseDto(
            e.id, e.parentId, e.name, e.bodyPart.name, e.recordType.name, e.equipment.name, e.weightMode.name,
            e.repMode.name, e.useAddedWeight, e.weightStepKg, e.memo, e.sortOrder, e.hidden,
        )
    }
}

@Serializable
internal data class AdjustmentFieldDto(
    val id: String,
    val exerciseId: String,
    val name: String,
    val valueType: String = AdjustmentValueType.Number.name,
    val unit: String = "",
    val sortOrder: Int = 0,
) {
    fun toDomain() = AdjustmentField(id, exerciseId, name, enumOr(valueType, AdjustmentValueType.Number), unit, sortOrder)

    companion object {
        fun from(f: AdjustmentField) = AdjustmentFieldDto(f.id, f.exerciseId, f.name, f.valueType.name, f.unit, f.sortOrder)
    }
}

@Serializable
internal data class GearDto(val id: String, val name: String, val sortOrder: Int = 0, val hidden: Boolean = false) {
    fun toDomain() = Gear(id, name, sortOrder, hidden)

    companion object {
        fun from(g: Gear) = GearDto(g.id, g.name, g.sortOrder, g.hidden)
    }
}

@Serializable
internal data class RoutineDto(val id: String, val name: String, val memo: String = "", val sortOrder: Int = 0) {
    fun toDomain() = Routine(id, name, memo, sortOrder)

    companion object {
        fun from(r: Routine) = RoutineDto(r.id, r.name, r.memo, r.sortOrder)
    }
}

@Serializable
internal data class RoutineItemDto(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int = 0,
    val targetSets: Int? = null,
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    val restSeconds: Int? = null,
    val restAfterSeconds: Int? = null,
) {
    fun toDomain() = RoutineItem(id, routineId, exerciseId, sortOrder, targetSets, targetWeightKg, targetReps, restSeconds, restAfterSeconds)

    companion object {
        fun from(i: RoutineItem) = RoutineItemDto(
            i.id, i.routineId, i.exerciseId, i.sortOrder, i.targetSets, i.targetWeightKg, i.targetReps, i.restSeconds, i.restAfterSeconds,
        )
    }
}

@Serializable
internal data class WorkoutDto(
    val id: String,
    val date: String,
    val routineId: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val memo: String = "",
    val quickEntry: Boolean = false,
) {
    fun toDomain() = Workout(
        id = id,
        date = LocalDate.parse(date),
        routineId = routineId,
        startedAt = Instant.ofEpochMilli(startedAt),
        finishedAt = finishedAt?.let(Instant::ofEpochMilli),
        memo = memo,
        quickEntry = quickEntry,
    )

    companion object {
        fun from(w: Workout) = WorkoutDto(w.id, w.date.toString(), w.routineId, w.startedAt.toEpochMilli(), w.finishedAt?.toEpochMilli(), w.memo, w.quickEntry)
    }
}

@Serializable
internal data class WorkoutExerciseDto(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val sortOrder: Int = 0,
    val weightMode: String = WeightMode.Total.name,
    val repMode: String = RepMode.Normal.name,
    val memo: String = "",
    val restSeconds: Int? = null,
    val restAfterSeconds: Int? = null,
) {
    fun toDomain() = WorkoutExercise(
        id, workoutId, exerciseId, sortOrder, enumOr(weightMode, WeightMode.Total), enumOr(repMode, RepMode.Normal),
        memo, restSeconds, restAfterSeconds,
    )

    companion object {
        fun from(w: WorkoutExercise) = WorkoutExerciseDto(
            w.id, w.workoutId, w.exerciseId, w.sortOrder, w.weightMode.name, w.repMode.name, w.memo, w.restSeconds, w.restAfterSeconds,
        )
    }
}

@Serializable
internal data class SetDto(
    val id: String,
    val workoutExerciseId: String,
    val sortOrder: Int = 0,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val completed: Boolean = false,
    val setType: String = SetType.Normal.name,
    val completedAt: Long? = null,
) {
    fun toDomain() = SetRecord(
        id, workoutExerciseId, sortOrder, weightKg, reps, durationSec, distanceM, completed,
        enumOr(setType, SetType.Normal), completedAt?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun from(s: SetRecord) = SetDto(
            s.id, s.workoutExerciseId, s.sortOrder, s.weightKg, s.reps, s.durationSec, s.distanceM, s.completed,
            s.setType.name, s.completedAt?.toEpochMilli(),
        )
    }
}

@Serializable
internal data class WorkoutGearDto(val workoutExerciseId: String, val gearId: String)

@Serializable
internal data class AdjustmentValueDto(val workoutExerciseId: String, val fieldId: String, val value: String)

@Serializable
internal data class BaselineDto(
    val exerciseId: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val best: Double? = null,
    val sortOrder: Int = 0,
    val sets: Int? = null,
) {
    fun toDomain() = Baseline(exerciseId, weightKg, reps, durationSec, best, sortOrder, sets)

    companion object {
        fun from(b: Baseline) = BaselineDto(b.exerciseId, b.weightKg, b.reps, b.durationSec, b.best, b.sortOrder, b.sets)
    }
}
