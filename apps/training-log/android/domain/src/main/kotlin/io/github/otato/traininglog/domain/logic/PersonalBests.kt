package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.SetRecord

/**
 * 自己ベスト。最大重量で決める。加重のある自重の種目は最大加重、加重のない自重の種目は最大回数で決める（6.2）。
 * 加重はマイナス（補助）のままでも「大きいほど良い」が成り立つので、同じ扱いにできる。
 */
object PersonalBests {
    /** 自己ベストの比較に使う値（大きいほど良い）。使えない種目・セットなら null。 */
    fun score(set: SetRecord, recordType: RecordType, useAddedWeight: Boolean): Double? = when (recordType) {
        RecordType.WeightReps -> set.weightKg
        RecordType.Bodyweight -> if (useAddedWeight) set.weightKg else set.reps.toDouble()
        RecordType.Duration -> set.durationSec?.toDouble()
        RecordType.DistanceTime -> set.distanceM
    }

    /** 完了したセットの中の最高値。 */
    fun bestOf(sets: List<SetRecord>, recordType: RecordType, useAddedWeight: Boolean): Double? =
        sets.filter { it.completed }.mapNotNull { score(it, recordType, useAddedWeight) }.maxOrNull()

    /**
     * このワークアウトで自己ベストを更新したセットの id（印を付けるセット）。
     * [previousBest] は、このワークアウトより前の最高値。同じ値は更新に含めない。
     * 以前の記録が 1 つもないとき（[previousBest] が null）は、初回の記録に印は付けない。
     * 同じワークアウトで何度か更新したときは、最終的に最高値になったセットだけに印を付ける。
     */
    fun newBestSetIds(
        sets: List<SetRecord>,
        recordType: RecordType,
        useAddedWeight: Boolean,
        previousBest: Double?,
    ): Set<String> {
        if (previousBest == null) return emptySet()
        val best = sets
            .filter { it.completed }
            .sortedBy { it.sortOrder }
            .mapNotNull { set -> score(set, recordType, useAddedWeight)?.let { set to it } }
            .maxByOrNull { it.second }
            ?: return emptySet()
        return if (best.second > previousBest) setOf(best.first.id) else emptySet()
    }
}
