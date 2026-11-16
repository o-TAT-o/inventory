package io.github.otato.traininglog.ui.common

import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.PreviousRecord
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.WeightMode
import io.github.otato.traininglog.domain.model.WeightUnit

/**
 * 「60 kg 〔合計〕× 10 · 9 · 8」のような、セットの要約。数値の横には、必ず数え方の印を付ける（P-4）。
 * [weightText] [tag] [repsText] を順に並べて表示する。
 */
data class SetsLine(
    val weightText: String,
    val tag: String?,
    val repsText: String,
)

/**
 * 前回の記録の表示（「前回 11/7　60 kg 〔合計〕× 10 · 9 · 8」）。
 * [label] は「前回 11/7」。乗り換え用の初期設定で入れた値のときは「前回（前のアプリ）」（F-DA-06）。
 */
data class PreviousUi(
    val label: String,
    val line: SetsLine,
) {
    companion object {
        fun of(record: PreviousRecord, line: SetsLine) = PreviousUi(
            label = when {
                record.isBaseline -> "前回（前のアプリ）"
                // 初めて使うフォームは、同じ親のほかのフォームの直近の記録を目安にする。どのフォームの記録かを添える（F-EX-16）
                record.sourceExerciseName != null -> "「${record.sourceExerciseName}」の直近 ${formatMonthDay(record.date)}"
                else -> "前回 ${formatMonthDay(record.date)}"
            },
            line = line,
        )
    }
}

object RecordFormat {
    /** 完了したセットの重量の表記。自重の種目は「自重」「+10 kg」「-20 kg」。 */
    fun weightText(kg: Double, exercise: Exercise, unit: WeightUnit): String = when {
        !exercise.usesWeight -> if (exercise.recordType == RecordType.Bodyweight) "自重" else ""
        exercise.recordType == RecordType.Bodyweight && kg == 0.0 -> "自重"
        exercise.recordType == RecordType.Bodyweight && kg > 0 -> "+${WeightUnits.formatKg(kg, unit)} ${WeightUnits.label(unit)}"
        else -> "${WeightUnits.formatKg(kg, unit)} ${WeightUnits.label(unit)}"
    }

    /** 重量の横に付ける印。自重の種目は、加重なら「加重」、マイナスなら「補助」。 */
    fun weightTag(kg: Double, exercise: Exercise, weightMode: WeightMode): String? = when {
        exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight -> when {
            kg > 0 -> "加重"
            kg < 0 -> "補助"
            else -> null
        }
        !exercise.usesWeight -> null
        else -> weightMode.tag(exercise.equipment)
    }

    /**
     * セットの要約。重量が全部同じなら 1 つにまとめ、回数が全部同じなら「× 10 を 3 セット」と書く。
     * 完了したセットがなければ null。
     */
    fun setsLine(
        sets: List<SetRecord>,
        exercise: Exercise,
        weightMode: WeightMode,
        repMode: RepMode,
        unit: WeightUnit,
    ): SetsLine? {
        val done = sets.filter { it.completed }.sortedBy { it.sortOrder }
        if (done.isEmpty()) return null
        return when (exercise.recordType) {
            RecordType.Duration -> SetsLine(
                weightText = "",
                tag = null,
                repsText = done.joinToString(" · ") { "${it.durationSec ?: 0} 秒" },
            )
            RecordType.DistanceTime -> SetsLine(
                weightText = "",
                tag = null,
                repsText = done.joinToString(" · ") { "${distanceKmText(it.distanceM ?: 0.0)} km ${formatMmSs((it.durationSec ?: 0).toLong())}" },
            )
            else -> {
                // 重量が全部同じなら 1 つにまとめる。違うセットがあれば、セットごとに並べる（回数と 1 対 1 で読めるように）
                val sameWeight = done.map { it.weightKg }.distinct().size == 1
                val weights = if (sameWeight) listOf(done.first().weightKg) else done.map { it.weightKg }
                val weightText = if (exercise.usesWeight) {
                    // 自重の加重は、先頭の値の表記に合わせる（0 や補助が混ざっても読める）
                    weights.joinToString(" · ") { WeightUnits.formatKg(it, unit) }.let { numbers ->
                        if (exercise.recordType == RecordType.Bodyweight && weights.size == 1) {
                            weightText(weights.first(), exercise, unit)
                        } else {
                            "$numbers ${WeightUnits.label(unit)}"
                        }
                    }
                } else {
                    weightText(0.0, exercise, unit)
                }
                val reps = done.map { it.reps }
                val repsBody = if (sameWeight && reps.distinct().size == 1 && reps.size > 1) {
                    "× ${reps.first()} を ${reps.size} セット"
                } else {
                    "× " + reps.joinToString(" · ")
                }
                val repTag = repMode.tag()
                SetsLine(
                    weightText = weightText,
                    tag = weightTag(weights.first(), exercise, weightMode),
                    repsText = if (repTag != null) "$repsBody（$repTag）" else repsBody,
                )
            }
        }
    }

    fun distanceKmText(meters: Double): String = WeightUnits.format(meters / 1000.0)
}
