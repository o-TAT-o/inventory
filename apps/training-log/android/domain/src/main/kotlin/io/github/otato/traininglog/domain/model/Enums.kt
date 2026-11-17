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

/**
 * 部位（F-EX-03）。あらかじめ用意したもの（胸・背中・…。腕は、二頭・三頭・前腕に分ける）と、
 * 利用者が自分で足したもの（「ふくらはぎ」「お尻」など）がある。
 *
 * [name] は、保存に使う名前。用意した部位は英語の名前（`Chest`）、利用者が足した部位は、付けた名前そのもの。
 * 利用者が足した部位を管理する一覧は持たない。その部位の種目がある間だけ、選択肢に出る（[available]）。
 */
@JvmInline
value class BodyPart private constructor(val name: String) {
    /** あらかじめ用意した部位か（利用者が足した部位なら false）。 */
    val isPreset: Boolean get() = this in entries

    override fun toString(): String = name

    companion object {
        val Chest = BodyPart("Chest")
        val Back = BodyPart("Back")
        val Legs = BodyPart("Legs")
        val Shoulders = BodyPart("Shoulders")
        val Biceps = BodyPart("Biceps")
        val Triceps = BodyPart("Triceps")
        val Forearms = BodyPart("Forearms")
        val Abs = BodyPart("Abs")
        val FullBody = BodyPart("FullBody")
        val Cardio = BodyPart("Cardio")
        val Other = BodyPart("Other")

        /** あらかじめ用意した部位（画面に並べる順）。 */
        val entries: List<BodyPart> = listOf(Chest, Back, Legs, Shoulders, Biceps, Triceps, Forearms, Abs, FullBody, Cardio, Other)

        /** 利用者が足す部位の、名前の長さの上限（チップや一覧に収まるように）。 */
        const val MAX_CUSTOM_LENGTH = 10

        /**
         * 保存された名前から部位を求める。用意した部位の名前なら、その部位。昔の「腕」（`Arms`）は二頭として読む。
         * それ以外は、利用者が足した部位として、名前のまま持つ。空なら「その他」。
         */
        fun fromName(name: String): BodyPart {
            val trimmed = name.trim()
            return when {
                trimmed.isEmpty() -> Other
                trimmed == "Arms" -> Biceps
                else -> entries.firstOrNull { it.name == trimmed } ?: BodyPart(trimmed)
            }
        }

        /** 利用者が付けた名前から、部位を作る。前後の空白を除き、長すぎる名前は切る。空なら「その他」。 */
        fun custom(label: String): BodyPart = fromName(label.trim().take(MAX_CUSTOM_LENGTH))

        /**
         * 選択肢に出す部位。用意した部位に、いま種目で使っている、利用者が足した部位（[used] に出てきた順）を足す。
         * 「その他」は、最後に置く。
         */
        fun available(used: Iterable<BodyPart>): List<BodyPart> {
            val custom = used.filter { !it.isPreset }.distinct()
            return entries.filter { it != Other } + custom + Other
        }
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
