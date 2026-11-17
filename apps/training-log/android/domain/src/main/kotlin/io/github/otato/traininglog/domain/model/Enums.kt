package io.github.otato.traininglog.domain.model

/** 種目の記録の種類（F-EX-04）。 */
enum class RecordType {
    /** 重量 × 回数 */
    WeightReps,

    /** 自重（回数）。加重を使う種目では、重量の欄が加重になる（F-EX-19） */
    Bodyweight,

    /** 時間 */
    Duration,

    /** 距離 × 時間 */
    DistanceTime,
}

/** 重量の数え方（F-EX-08）。 */
enum class WeightMode {
    /** 合計（バーベル・マシンなど） */
    Total,

    /** 片側（ダンベル・片手ずつのケーブルなど） */
    PerSide,
}

/** 回数の数え方（F-EX-13）。 */
enum class RepMode {
    /** 通常（両手・両脚を同時に動かす） */
    Normal,

    /** 片側（片側ずつ行い、片側の回数を入れる） */
    PerSide,

    /** 左右合計（左右の回数を足して入れる） */
    BothSidesTotal,
}

/** 器具（F-EX-09）。 */
enum class Equipment {
    Barbell,
    Dumbbell,
    Machine,
    Cable,
    SmithMachine,
    Kettlebell,
    Bodyweight,
    Other,
}

/**
 * 器具ごとの、重量の数え方の初期値。ダンベルは片手ずつ持つので「片側」（画面では「片手」）、それ以外は「合計」。
 * 種目を新しく作るとき、器具を選ぶと、これに合わせる。
 */
val Equipment.defaultWeightMode: WeightMode
    get() = if (this == Equipment.Dumbbell) WeightMode.PerSide else WeightMode.Total

/** 部位（F-EX-03）。腕は、二頭と三頭に分けて持つ。 */
enum class BodyPart {
    Chest,
    Back,
    Legs,
    Shoulders,
    Biceps,
    Triceps,
    Abs,
    FullBody,
    Cardio,
    Other,
    ;

    companion object {
        /**
         * 保存された名前から部位を求める。昔の「腕」（`Arms`）は二頭として読み、知らない名前は「その他」にする。
         * データベースは移行で変換するが、昔の名前が残ったファイルなどがあっても、読めなくならないようにする。
         */
        fun fromName(name: String): BodyPart =
            entries.firstOrNull { it.name == name } ?: if (name == "Arms") Biceps else Other
    }
}

/** セットの種類（F-WO-09）。 */
enum class SetType {
    Normal,
    WarmUp,
    Drop,
}

/** 調整項目の値の種類（F-EX-10）。 */
enum class AdjustmentValueType {
    Number,
    Text,
}
