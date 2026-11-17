package io.github.otato.traininglog.ui.common

import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode

// 画面に出す日本語の名前。ドメイン層は言語を持たないので、ここで決める。

fun Equipment.label(): String = when (this) {
    Equipment.Barbell -> "バーベル"
    Equipment.Dumbbell -> "ダンベル"
    Equipment.Machine -> "マシン"
    Equipment.Cable -> "ケーブル"
    Equipment.SmithMachine -> "スミスマシン"
    Equipment.Kettlebell -> "ケトルベル"
    Equipment.Bodyweight -> "自重"
    Equipment.Other -> "その他"
}

fun BodyPart.label(): String = when (this) {
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
    else -> name
}

/**
 * 画面で打った名前から、部位を求める。用意した部位と同じ名前（「胸」など）なら、その部位にする
 * （同じ名前の部位が 2 つできないように）。それ以外は、利用者が足した部位になる。
 */
fun bodyPartOf(label: String): BodyPart {
    val trimmed = label.trim()
    return BodyPart.entries.firstOrNull { it.label() == trimmed } ?: BodyPart.custom(trimmed)
}

fun RecordType.label(): String = when (this) {
    RecordType.WeightReps -> "重量 × 回数"
    RecordType.Bodyweight -> "自重（回数）"
    RecordType.Duration -> "時間"
    RecordType.DistanceTime -> "距離 × 時間"
}

/** 重量の数え方の印。数値の横に必ず出す（P-4）。片手で持つ器具は「片手」、それ以外の片側は「片側」。 */
fun WeightMode.tag(equipment: Equipment): String = when (this) {
    WeightMode.Total -> "合計"
    WeightMode.PerSide -> when (equipment) {
        Equipment.Dumbbell, Equipment.Cable, Equipment.Kettlebell -> "片手"
        else -> "片側"
    }
}

fun WeightMode.label(equipment: Equipment): String = tag(equipment)

/** 回数の数え方の印。「通常」は印を付けない。 */
fun RepMode.tag(): String? = when (this) {
    RepMode.Normal -> null
    RepMode.PerSide -> "片側"
    RepMode.BothSidesTotal -> "左右計"
}

fun RepMode.label(): String = when (this) {
    RepMode.Normal -> "通常"
    RepMode.PerSide -> "片側"
    RepMode.BothSidesTotal -> "左右計"
}
