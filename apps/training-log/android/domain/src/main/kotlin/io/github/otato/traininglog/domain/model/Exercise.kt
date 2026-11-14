package io.github.otato.traininglog.domain.model

/** 種目。バリエーションも種目の一種で、[parentId] で親の種目を指す（2 段まで。F-EX-14）。 */
data class Exercise(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val bodyPart: BodyPart = BodyPart.Other,
    val recordType: RecordType = RecordType.WeightReps,
    val equipment: Equipment = Equipment.Other,
    val weightMode: WeightMode = WeightMode.Total,
    val repMode: RepMode = RepMode.Normal,
    /** 自重の種目で、加重（補助）を使うか（F-EX-19） */
    val useAddedWeight: Boolean = false,
    /** この種目だけの重量の刻み（kg）。null なら設定の値（F-SE-02） */
    val weightStepKg: Double? = null,
    val memo: String = "",
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
) {
    /** 重量の欄を使う種目か（自重で加重を使わない種目と、時間・距離の種目では使わない）。 */
    val usesWeight: Boolean
        get() = when (recordType) {
            RecordType.WeightReps -> true
            RecordType.Bodyweight -> useAddedWeight
            RecordType.Duration, RecordType.DistanceTime -> false
        }

    /** 回数の欄を使う種目か。 */
    val usesReps: Boolean
        get() = recordType == RecordType.WeightReps || recordType == RecordType.Bodyweight

    val isVariation: Boolean get() = parentId != null
}

/** ギア（ベルト・パワーグリップなど。F-GR-01）。 */
data class Gear(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
)

/** 種目ごとの調整項目の定義（F-EX-10）。例: ベンチ角度（°）。 */
data class AdjustmentField(
    val id: String,
    val exerciseId: String,
    val name: String,
    val valueType: AdjustmentValueType = AdjustmentValueType.Number,
    val unit: String = "",
    val sortOrder: Int = 0,
)
