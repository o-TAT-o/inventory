package io.github.otato.traininglog.feature.workout

import io.github.otato.traininglog.domain.logic.PersonalBests
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutExerciseDetail
import io.github.otato.traininglog.ui.common.PreviousUi
import io.github.otato.traininglog.ui.common.RecordFormat
import io.github.otato.traininglog.ui.common.formatDateLong
import io.github.otato.traininglog.ui.common.formatDateWithDow
import io.github.otato.traininglog.ui.common.formatMmSs
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag
import java.time.Duration
import java.time.Instant
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
        /** すべて完了してたたまれた種目のうち、自分で開いたもの */
        expandedIds: Set<String> = emptySet(),
        /** いちばん最近、セットを完了した種目。全部が終わるまでは、たたまずに残す（自己ベストの説明などを見せるため） */
        lastCompletedId: String? = null,
        /** 「いま」。ワークアウトの経過時間（分）を出すのに使う */
        now: Instant = detail.workout.finishedAt ?: detail.workout.startedAt,
    ): WorkoutUiState {
        val unit = settings.weightUnit
        val target = resolveTarget(detail, focusedWorkoutExerciseId, selectedSetId)
        val allSets = detail.exercises.flatMap { it.sets }
        val allDone = allSets.isNotEmpty() && allSets.all { it.completed }

        val exercises = detail.exercises.map { exercise ->
            exerciseUi(
                exercise = exercise,
                context = contexts[exercise.exercise.id],
                target = target,
                draft = draft,
                unit = unit,
                gearCatalog = gearCatalog,
                expanded = exercise.workoutExercise.id in expandedIds,
                justCompleted = exercise.workoutExercise.id == lastCompletedId && !allDone,
            )
        }

        val dock = target?.let { dockUi(it, detail, settings, contexts, draft, gearCatalog) }
        val minutes = if (detail.workout.quickEntry) null else Duration.between(detail.workout.startedAt, now).toMinutes().coerceAtLeast(0)
        return WorkoutUiState(
            loaded = true,
            workoutId = detail.workout.id,
            finished = detail.workout.isFinished,
            title = routineName ?: "自由に記録",
            date = detail.workout.date,
            dateLabel = detail.workout.date.takeIf { it != today }?.let(::formatDateWithDow),
            dateTitle = formatDateLong(detail.workout.date),
            isToday = detail.workout.date == today,
            quickEntry = detail.workout.quickEntry,
            startedAt = detail.workout.startedAt,
            finishedAt = detail.workout.finishedAt,
            routineMemo = routineMemo?.takeIf { it.isNotBlank() },
            unit = unit,
            soundOn = settings.soundOn,
            vibrationOn = settings.vibrationOn,
            exercises = exercises,
            dock = dock,
            allDone = allDone,
            hasIncompleteSets = allSets.any { !it.completed },
            summaryText = "${detail.exercises.count { it.completedSets.isNotEmpty() }} 種目 · ${allSets.count { it.completed }} セット" +
                (minutes?.let { " · $it 分" } ?: ""),
            availableGear = gearCatalog.map { GearChipUi(it.id, it.name, selected = false) },
        )
    }

    private fun exerciseUi(
        exercise: WorkoutExerciseDetail,
        context: ExerciseContext?,
        target: DockTarget?,
        draft: Draft?,
        unit: WeightUnit,
        gearCatalog: List<Gear>,
        expanded: Boolean,
        justCompleted: Boolean,
    ): ExerciseUi {
        val ex = exercise.exercise
        val bestIds = PersonalBests.newBestSetIds(
            sets = exercise.sets,
            recordType = ex.recordType,
            useAddedWeight = ex.useAddedWeight,
            previousBest = context?.bestBefore,
        )
        val pbNote = pbNote(exercise, context?.bestBefore, unit)
        val sets = exercise.sets.mapIndexed { index, set ->
            val isCurrent = target?.set?.id == set.id
            val shown = if (isCurrent && draft?.setId == set.id) set.copy(
                weightKg = draft.weightKg,
                reps = draft.reps,
                durationSec = draft.durationSec,
                distanceM = draft.distanceM,
            ) else set
            val isBest = set.id in bestIds
            setRow(shown, index + 1, exercise, unit, isCurrent, isBest, if (isBest && set.completed) pbNote else null)
        }

        // 完了した種目は 1 行にたたむ。開いた種目・いま入力している種目・いま完了したばかりの種目（全部が終わるまで）は、たたまない
        val editingHere = target?.exercise?.workoutExercise?.id == exercise.workoutExercise.id
        val collapsed = exercise.isAllDone && !expanded && !editingHere && !justCompleted

        val line = RecordFormat.setsLine(exercise.sets, ex, exercise.workoutExercise.weightMode, exercise.workoutExercise.repMode, unit)
        val gearNames = gearCatalog.filter { it.id in exercise.gearIds }.map { it.name }
        val adjustmentTexts = exercise.adjustmentFields.mapNotNull { field ->
            exercise.adjustments[field.id]?.takeIf { it.isNotBlank() }?.let { adjustmentText(field, it) }
        }
        return ExerciseUi(
            workoutExerciseId = exercise.workoutExercise.id,
            exerciseId = ex.id,
            name = exercise.title,
            meta = (listOf(ex.equipment.label()) + gearNames + adjustmentTexts).joinToString(" · "),
            formLabel = when {
                exercise.formName != null -> "フォーム: ${exercise.formName}"
                exercise.formUnselected -> "フォーム: 未選択"
                else -> null
            },
            formUnselected = exercise.formUnselected,
            previous = context?.previous?.let { previousUi(it, ex, unit) },
            sets = sets,
            doneCount = exercise.completedSets.size,
            totalCount = exercise.sets.size,
            collapsed = collapsed,
            summary = line?.let { listOfNotNull(it.weightText.takeIf(String::isNotEmpty), it.tag, it.repsText).joinToString(" ") }.orEmpty(),
            hasPersonalBest = bestIds.isNotEmpty(),
            dragMeta = "${ex.equipment.label()} · 目標 ${exercise.sets.size} セット",
        )
    }

    /** 調整項目の値の表記。「ベンチ角度 30°」「グリップ ワイド」 */
    fun adjustmentText(field: AdjustmentField, value: String): String =
        if (field.valueType == AdjustmentValueType.Number) "${field.name} $value${field.unit}" else "${field.name} $value"

    /** 自己ベストのセットの下に出す説明。「最大重量の更新　これまで 62.5 kg 合計」 */
    private fun pbNote(exercise: WorkoutExerciseDetail, previousBest: Double?, unit: WeightUnit): String? {
        previousBest ?: return null
        val ex = exercise.exercise
        return when (ex.recordType) {
            RecordType.WeightReps -> {
                val weight = RecordFormat.weightText(previousBest, ex, unit)
                val tag = RecordFormat.weightTag(previousBest, ex, exercise.workoutExercise.weightMode)
                "最大重量の更新　これまで " + listOfNotNull(weight, tag).joinToString(" ")
            }
            RecordType.Bodyweight ->
                if (ex.useAddedWeight) {
                    val weight = RecordFormat.weightText(previousBest, ex, unit)
                    val tag = RecordFormat.weightTag(previousBest, ex, exercise.workoutExercise.weightMode)
                    "最大加重の更新　これまで " + listOfNotNull(weight, tag).joinToString(" ")
                } else {
                    "最大回数の更新　これまで ${previousBest.toInt()} 回"
                }
            RecordType.Duration -> "最長時間の更新　これまで ${previousBest.toInt()} 秒"
            RecordType.DistanceTime -> "最長距離の更新　これまで ${RecordFormat.distanceKmText(previousBest)} km"
        }
    }

    private fun previousUi(record: PreviousRecord, exercise: Exercise, unit: WeightUnit): PreviousUi? {
        val line = RecordFormat.setsLine(record.sets, exercise, record.weightMode, record.repMode, unit) ?: return null
        return PreviousUi.of(record, line)
    }

    private fun setRow(
        set: SetRecord,
        number: Int,
        exercise: WorkoutExerciseDetail,
        unit: WeightUnit,
        isCurrent: Boolean,
        isPersonalBest: Boolean,
        pbNote: String?,
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
            pbNote = pbNote,
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

        val previous = contexts[ex.id]?.previous
        val previousSets = previous?.sets.orEmpty()
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
        val previousAdjustments = previous?.adjustments.orEmpty()
        return DockUi(
            setId = set.id,
            workoutExerciseId = exercise.workoutExercise.id,
            exerciseId = ex.id,
            title = "${exercise.title}　$number セット目",
            subtitle = subtitle,
            fields = fields,
            editingCompleted = target.editingCompleted,
            canGoPrevious = position > 0,
            canGoNext = position in 0 until withOpen.lastIndex,
            detail = DetailUi(
                workoutExerciseId = exercise.workoutExercise.id,
                exerciseName = exercise.title,
                subtitle = listOfNotNull(
                    ex.equipment.label() + " · 重量は" + exercise.workoutExercise.weightMode.label(ex.equipment),
                    exercise.formName?.let { "フォーム: $it" },
                ).joinToString(" · "),
                equipment = ex.equipment,
                gear = gearCatalog.map { GearChipUi(it.id, it.name, selected = it.id in selectedGear) },
                adjustments = exercise.adjustmentFields.map { field ->
                    AdjustmentUi(
                        fieldId = field.id,
                        name = field.name,
                        unit = field.unit,
                        isNumber = field.valueType == AdjustmentValueType.Number,
                        value = exercise.adjustments[field.id].orEmpty(),
                        previousValue = previousAdjustments[field.id]?.takeIf { it.isNotBlank() },
                        previousText = previousAdjustments[field.id]?.takeIf { it.isNotBlank() }?.let {
                            "前回 " + if (field.valueType == AdjustmentValueType.Number) "$it${field.unit}" else it
                        },
                    )
                },
                weightMode = exercise.workoutExercise.weightMode,
                repMode = exercise.workoutExercise.repMode,
                showWeightMode = ex.recordType == RecordType.WeightReps,
                showRepMode = ex.usesReps,
                memo = exercise.workoutExercise.memo,
            ),
        )
    }

    /** 「前回 60 kg × 8」 */
    private fun previousShort(set: SetRecord, exercise: Exercise, unit: WeightUnit): String = when (exercise.recordType) {
        RecordType.Duration -> "前回 ${set.durationSec ?: 0} 秒"
        RecordType.DistanceTime -> "前回 ${RecordFormat.distanceKmText(set.distanceM ?: 0.0)} km"
        else -> {
            val weight = RecordFormat.weightText(set.weightKg, exercise, unit)
            "前回 " + listOf(weight, "× ${set.reps}").filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
}
