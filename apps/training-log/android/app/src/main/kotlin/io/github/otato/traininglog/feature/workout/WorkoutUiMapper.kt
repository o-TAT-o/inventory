package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.domain.logic.PersonalBests
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutExerciseDetail
import io.github.otato.traininglog.ui.common.PreviousUi
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.formatMmSs
import io.github.otato.traininglog.ui.common.formatMonthDay
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag
import java.time.LocalDate

/** 種目の前回の記録と、これまでの自己ベスト（このワークアウトより前のもの）。 */
data class ExerciseContext(
    val previous: PreviousRecord?,
    val bestBefore: Double?,
)

/** 下のパネルで入力する対象（どの種目の、どのセットか）。 */
data class DockTarget(
    val exercise: WorkoutExerciseDetail,
    val set: SetRecord,
    val editingCompleted: Boolean,
)

/** 入力途中の値（± を押すたびに画面は即座に変わり、保存は少し遅れて追いつく）。 */
data class Draft(
    val setId: String,
    val weightKg: Double,
    val reps: Int,
    val durationSec: Int?,
    val distanceM: Double?,
) {
    fun toValues() = SetValues(setId, weightKg, reps, durationSec, distanceM)

    companion object {
        fun of(set: SetRecord) = Draft(set.id, set.weightKg, set.reps, set.durationSec, set.distanceM)
    }
}

/**
 * ワークアウトの詳細（DB から読んだもの）を、画面に出す形に変える。DB にも Android にも触れない純粋な関数なので、そのままテストできる。
 */
object WorkoutUiMapper {

    /**
     * 下のパネルの対象を決める。
     * 1. 選んだセット（タップしたもの。完了したセットも直せる）
     * 2. いま見ている種目の、最初の未完了のセット
     * 3. 先頭から数えて、未完了のセットがある最初の種目のセット
     */
    fun resolveTarget(detail: WorkoutDetail, focusedWorkoutExerciseId: String?, selectedSetId: String?): DockTarget? {
        if (selectedSetId != null) {
            detail.exercises.forEach { exercise ->
                exercise.sets.firstOrNull { it.id == selectedSetId }?.let { set ->
                    return DockTarget(exercise, set, editingCompleted = set.completed)
                }
            }
        }
        val order = buildList {
            detail.exercises.firstOrNull { it.workoutExercise.id == focusedWorkoutExerciseId }?.let(::add)
            addAll(detail.exercises.filter { it.workoutExercise.id != focusedWorkoutExerciseId })
        }
        order.forEach { exercise ->
            exercise.currentSet?.let { return DockTarget(exercise, it, editingCompleted = false) }
        }
        return null
    }

    fun map(
        detail: WorkoutDetail,
        settings: AppSettings,
        contexts: Map<String, ExerciseContext>,
        focusedWorkoutExerciseId: String?,
        selectedSetId: String?,
        draft: Draft?,
        gearCatalog: List<Gear>,
        routineName: String?,
        routineMemo: String?,
        today: LocalDate,
    ): WorkoutUiState {
        val unit = settings.weightUnit
        val target = resolveTarget(detail, focusedWorkoutExerciseId, selectedSetId)
        val gearNames = gearCatalog.associate { it.id to it.name }

        val exercises = detail.exercises.map { exercise ->
            val context = contexts[exercise.exercise.id]
            val bestIds = PersonalBests.newBestSetIds(
                sets = exercise.sets,
                recordType = exercise.exercise.recordType,
                useAddedWeight = exercise.exercise.useAddedWeight,
                previousBest = context?.bestBefore,
            )
            ExerciseUi(
                workoutExerciseId = exercise.workoutExercise.id,
                exerciseId = exercise.exercise.id,
                name = exercise.exercise.name,
                meta = (listOf(exercise.exercise.equipment.label()) + exercise.gearIds.mapNotNull(gearNames::get)).joinToString(" · "),
                previous = context?.previous?.let { previousUi(it, exercise.exercise, unit) },
                sets = exercise.sets.mapIndexed { index, set ->
                    val isCurrent = target?.set?.id == set.id
                    val shown = if (isCurrent && draft?.setId == set.id) set.copy(
                        weightKg = draft.weightKg,
                        reps = draft.reps,
                        durationSec = draft.durationSec,
                        distanceM = draft.distanceM,
                    ) else set
                    setRow(shown, index + 1, exercise, unit, isCurrent, set.id in bestIds)
                },
            )
        }

        val allSets = detail.exercises.flatMap { it.sets }
        val dock = target?.let { dockUi(it, detail, settings, contexts, draft, gearCatalog) }
        return WorkoutUiState(
            loaded = true,
            workoutId = detail.workout.id,
            finished = detail.workout.isFinished,
            title = routineName ?: "自由に記録",
            date = detail.workout.date,
            dateLabel = detail.workout.date.takeIf { it != today }?.let(::formatDateWithDow),
            startedAt = detail.workout.startedAt,
            finishedAt = detail.workout.finishedAt,
            routineMemo = routineMemo?.takeIf { it.isNotBlank() },
            unit = unit,
            exercises = exercises,
            dock = dock,
            allDone = allSets.isNotEmpty() && allSets.all { it.completed },
            hasIncompleteSets = allSets.any { !it.completed },
            summaryText = "${detail.exercises.count { it.completedSets.isNotEmpty() }} 種目 · ${allSets.count { it.completed }} セット",
            availableGear = gearCatalog.map { GearChipUi(it.id, it.name, selected = false) },
        )
    }

    private fun previousUi(record: PreviousRecord, exercise: Exercise, unit: io.github.otato.traininglog.domain.model.WeightUnit): PreviousUi? {
        val line = RecordFormat.setsLine(record.sets, exercise, record.weightMode, record.repMode, unit) ?: return null
        return PreviousUi(dateText = formatMonthDay(record.date), line = line)
    }

    private fun setRow(
        set: SetRecord,
        number: Int,
        exercise: WorkoutExerciseDetail,
        unit: io.github.otato.traininglog.domain.model.WeightUnit,
        isCurrent: Boolean,
        isPersonalBest: Boolean,
    ): SetRowUi {
        val ex = exercise.exercise
        val repTag = exercise.workoutExercise.repMode.tag()
        val (weightText, weightTag, repsText) = when (ex.recordType) {
            RecordType.Duration -> Triple("", null, "${set.durationSec ?: 0} 秒")
            RecordType.DistanceTime -> Triple(
                "",
                null,
                "${RecordFormat.distanceKmText(set.distanceM ?: 0.0)} km · ${formatMmSs((set.durationSec ?: 0).toLong())}",
            )
            else -> Triple(
                RecordFormat.weightText(set.weightKg, ex, unit),
                RecordFormat.weightTag(set.weightKg, ex, exercise.workoutExercise.weightMode),
                "× ${set.reps} 回" + (repTag?.let { "（$it）" } ?: ""),
            )
        }
        return SetRowUi(
            id = set.id,
            number = number,
            weightText = weightText,
            weightTag = weightTag,
            repsText = repsText,
            completed = set.completed,
            isCurrent = isCurrent,
            isPersonalBest = isPersonalBest && set.completed,
        )
    }

    private fun dockUi(
        target: DockTarget,
        detail: WorkoutDetail,
        settings: AppSettings,
        contexts: Map<String, ExerciseContext>,
        draft: Draft?,
        gearCatalog: List<Gear>,
    ): DockUi {
        val exercise = target.exercise
        val ex = exercise.exercise
        val unit = settings.weightUnit
        val set = target.set
        val values = if (draft?.setId == set.id) draft else Draft.of(set)
        val number = exercise.sets.indexOfFirst { it.id == set.id } + 1

        val withOpen = detail.exercises.filter { it.currentSet != null }
        val position = withOpen.indexOfFirst { it.workoutExercise.id == exercise.workoutExercise.id }

        val previousSets = contexts[ex.id]?.previous?.sets.orEmpty()
        val reference = previousSets.getOrNull(number - 1) ?: previousSets.lastOrNull()
        val subtitle = reference?.let { previousShort(it, ex, unit) } ?: "前回の記録はありません"

        val fields = buildList {
            when (ex.recordType) {
                RecordType.WeightReps, RecordType.Bodyweight -> {
                    if (ex.usesWeight) {
                        val display = WeightUnits.toDisplay(values.weightKg, unit)
                        val modeTag = RecordFormat.weightTag(values.weightKg, ex, exercise.workoutExercise.weightMode)
                        add(
                            FieldUi(
                                key = FieldKey.Weight,
                                valueText = WeightUnits.format(display),
                                unitText = WeightUnits.label(unit) + (modeTag?.let { " $it" } ?: ""),
                                inputText = WeightUnits.format(display),
                                allowDecimal = true,
                                minusLabel = "重量を下げる",
                                plusLabel = "重量を上げる",
                            ),
                        )
                    }
                    val repTag = exercise.workoutExercise.repMode.tag()
                    add(
                        FieldUi(
                            key = FieldKey.Reps,
                            valueText = values.reps.toString(),
                            unitText = "回" + (repTag?.let { " $it" } ?: ""),
                            inputText = values.reps.toString(),
                            allowDecimal = false,
                            minusLabel = "回数を減らす",
                            plusLabel = "回数を増やす",
                        ),
                    )
                }
                RecordType.Duration -> add(
                    FieldUi(
                        key = FieldKey.Duration,
                        valueText = (values.durationSec ?: 0).toString(),
                        unitText = "秒",
                        inputText = (values.durationSec ?: 0).toString(),
                        allowDecimal = false,
                        minusLabel = "時間を減らす",
                        plusLabel = "時間を増やす",
                    ),
                )
                RecordType.DistanceTime -> {
                    add(
                        FieldUi(
                            key = FieldKey.Distance,
                            valueText = RecordFormat.distanceKmText(values.distanceM ?: 0.0),
                            unitText = "km",
                            inputText = RecordFormat.distanceKmText(values.distanceM ?: 0.0),
                            allowDecimal = true,
                            minusLabel = "距離を減らす",
                            plusLabel = "距離を増やす",
                        ),
                    )
                    add(
                        FieldUi(
                            key = FieldKey.Duration,
                            valueText = formatMmSs((values.durationSec ?: 0).toLong()),
                            unitText = "分:秒",
                            inputText = ((values.durationSec ?: 0) / 60.0).let { WeightUnits.format(it) },
                            allowDecimal = true,
                            minusLabel = "時間を減らす",
                            plusLabel = "時間を増やす",
                        ),
                    )
                }
            }
        }

        val selectedGear = exercise.gearIds
        return DockUi(
            setId = set.id,
            workoutExerciseId = exercise.workoutExercise.id,
            exerciseId = ex.id,
            title = "${ex.name}　$number セット目",
            subtitle = subtitle,
            fields = fields,
            editingCompleted = target.editingCompleted,
            canGoPrevious = position > 0,
            canGoNext = position in 0 until withOpen.lastIndex,
            detail = DetailUi(
                workoutExerciseId = exercise.workoutExercise.id,
                exerciseName = ex.name,
                subtitle = ex.equipment.label() + " · 重量は" + exercise.workoutExercise.weightMode.label(ex.equipment),
                equipment = ex.equipment,
                gear = gearCatalog.map { GearChipUi(it.id, it.name, selected = it.id in selectedGear) },
                weightMode = exercise.workoutExercise.weightMode,
                repMode = exercise.workoutExercise.repMode,
                showWeightMode = ex.recordType == RecordType.WeightReps,
                showRepMode = ex.usesReps,
                memo = exercise.workoutExercise.memo,
            ),
        )
    }

    /** 「前回 60 kg × 8」 */
    private fun previousShort(set: SetRecord, exercise: Exercise, unit: io.github.otato.traininglog.domain.model.WeightUnit): String = when (exercise.recordType) {
        RecordType.Duration -> "前回 ${set.durationSec ?: 0} 秒"
        RecordType.DistanceTime -> "前回 ${RecordFormat.distanceKmText(set.distanceM ?: 0.0)} km"
        else -> {
            val weight = RecordFormat.weightText(set.weightKg, exercise, unit)
            "前回 " + listOf(weight, "× ${set.reps}").filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
}
