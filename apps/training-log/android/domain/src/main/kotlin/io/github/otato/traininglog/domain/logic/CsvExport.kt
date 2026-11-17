package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.BackupSnapshot
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.WeightMode

/**
 * 記録を CSV にする（F-DA-04）。1 行が 1 セットで、表計算ソフトで開いて、並べ替えたり集計したりできる。
 * 完了したセットだけを出す。重量は kg（lb で記録している人にも、同じ単位で渡す）。
 * 先頭に BOM を付けるので、Excel でも文字化けしない。
 */
object CsvExport {
    private const val BOM = "﻿"

    private val header = listOf(
        "日付", "ルーティン", "種目", "部位", "器具", "セット", "重量(kg)", "重量の数え方", "回数", "時間(秒)", "距離(m)", "ギア", "調整項目",
    )

    fun build(snapshot: BackupSnapshot): String {
        val exercises = snapshot.exercises.associateBy { it.id }
        val routineNames = snapshot.routines.associate { it.id to it.name }
        val gearNames = snapshot.gear.associate { it.id to it.name }
        val fields = snapshot.adjustmentFields.associateBy { it.id }
        val setsByExercise = snapshot.sets.filter { it.completed }.groupBy { it.workoutExerciseId }
        val gearByExercise = snapshot.workoutGear.groupBy({ it.workoutExerciseId }, { it.gearId })
        val adjustmentsByExercise = snapshot.adjustmentValues.groupBy { it.workoutExerciseId }
        val exercisesByWorkout = snapshot.workoutExercises.groupBy { it.workoutId }

        val rows = mutableListOf<List<String>>()
        snapshot.workouts.filter { it.isFinished }.sortedWith(compareBy({ it.date }, { it.startedAt })).forEach { workout ->
            exercisesByWorkout[workout.id].orEmpty().sortedBy { it.sortOrder }.forEach { we ->
                val exercise = exercises[we.exerciseId] ?: return@forEach
                val gear = gearByExercise[we.id].orEmpty().mapNotNull(gearNames::get).joinToString("・")
                val adjustments = adjustmentsByExercise[we.id].orEmpty().mapNotNull { value ->
                    fields[value.fieldId]?.let { "${it.name} ${value.value}${it.unit}" }
                }.joinToString("・")
                setsByExercise[we.id].orEmpty().sortedBy { it.sortOrder }.forEachIndexed { index, set ->
                    rows += listOf(
                        workout.date.toString(),
                        workout.routineId?.let(routineNames::get).orEmpty(),
                        exercise.name,
                        bodyPartLabel(exercise.bodyPart),
                        equipmentLabel(exercise.equipment),
                        (index + 1).toString(),
                        if (exercise.usesWeight) formatNumber(set.weightKg) else "",
                        if (exercise.usesWeight) weightModeLabel(we.weightMode, exercise.equipment) else "",
                        if (exercise.usesReps) set.reps.toString() else "",
                        set.durationSec?.toString().orEmpty(),
                        set.distanceM?.let(::formatNumber).orEmpty(),
                        gear,
                        adjustments,
                    )
                }
            }
        }
        return BOM + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } } + "\r\n"
    }

    /** カンマ・改行・引用符を含む値は、引用符で囲む。 */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun bodyPartLabel(part: BodyPart): String = when (part) {
        BodyPart.Chest -> "胸"
        BodyPart.Back -> "背中"
        BodyPart.Legs -> "脚"
        BodyPart.Shoulders -> "肩"
        BodyPart.Biceps -> "二頭"
        BodyPart.Triceps -> "三頭"
        BodyPart.Forearms -> "前腕"
        BodyPart.Abs -> "腹"
        BodyPart.FullBody -> "全身"
        BodyPart.Cardio -> "有酸素"
        BodyPart.Other -> "その他"
        // 利用者が足した部位は、付けた名前のまま
        else -> part.name
    }

    private fun equipmentLabel(equipment: Equipment): String = when (equipment) {
        Equipment.Barbell -> "バーベル"
        Equipment.Dumbbell -> "ダンベル"
        Equipment.Machine -> "マシン"
        Equipment.Cable -> "ケーブル"
        Equipment.SmithMachine -> "スミスマシン"
        Equipment.Kettlebell -> "ケトルベル"
        Equipment.Bodyweight -> "自重"
        Equipment.Other -> "その他"
    }

    private fun weightModeLabel(mode: WeightMode, equipment: Equipment): String = when (mode) {
        WeightMode.Total -> "合計"
        WeightMode.PerSide -> if (equipment == Equipment.Dumbbell || equipment == Equipment.Cable || equipment == Equipment.Kettlebell) "片手" else "片側"
    }
}
