package io.github.otato.traininglog.data

import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.RepMode
import io.github.otato.traininglog.domain.model.WeightMode

/**
 * 初回起動時に用意するプリセット（F-EX-01、F-GR-01）。
 * 種目名は一般的な呼び名で、説明文は持たない。id は固定にして、あとで追加するときも突き合わせられるようにする。
 */
internal object PresetData {
    private fun ex(
        key: String,
        name: String,
        bodyPart: BodyPart,
        equipment: Equipment,
        weightMode: WeightMode = WeightMode.Total,
        repMode: RepMode = RepMode.Normal,
        recordType: RecordType = RecordType.WeightReps,
        useAddedWeight: Boolean = false,
    ) = Exercise(
        id = "preset:$key",
        name = name,
        bodyPart = bodyPart,
        recordType = recordType,
        equipment = equipment,
        weightMode = weightMode,
        repMode = repMode,
        useAddedWeight = useAddedWeight,
    )

    fun exercises(): List<Exercise> = listOf(
        // 胸
        ex("bench_press", "ベンチプレス", BodyPart.Chest, Equipment.Barbell),
        ex("incline_bench_press", "インクラインベンチプレス", BodyPart.Chest, Equipment.Barbell),
        ex("dumbbell_press", "ダンベルプレス", BodyPart.Chest, Equipment.Dumbbell, WeightMode.PerSide),
        ex("incline_dumbbell_press", "インクラインダンベルプレス", BodyPart.Chest, Equipment.Dumbbell, WeightMode.PerSide),
        ex("dumbbell_fly", "ダンベルフライ", BodyPart.Chest, Equipment.Dumbbell, WeightMode.PerSide),
        ex("chest_press", "チェストプレス", BodyPart.Chest, Equipment.Machine),
        ex("cable_crossover", "ケーブルクロスオーバー", BodyPart.Chest, Equipment.Cable, WeightMode.PerSide),
        ex("dips", "ディップス", BodyPart.Chest, Equipment.Bodyweight, recordType = RecordType.Bodyweight, useAddedWeight = true),
        // 背中
        ex("deadlift", "デッドリフト", BodyPart.Back, Equipment.Barbell),
        ex("lat_pulldown", "ラットプルダウン", BodyPart.Back, Equipment.Cable),
        ex("bent_over_row", "ベントオーバーロウ", BodyPart.Back, Equipment.Barbell),
        ex("one_hand_dumbbell_row", "ワンハンドダンベルロウ", BodyPart.Back, Equipment.Dumbbell, WeightMode.PerSide, RepMode.PerSide),
        ex("seated_row", "シーテッドロウ", BodyPart.Back, Equipment.Cable),
        ex("pull_up", "懸垂", BodyPart.Back, Equipment.Bodyweight, recordType = RecordType.Bodyweight, useAddedWeight = true),
        // 脚
        ex("squat", "スクワット", BodyPart.Legs, Equipment.Barbell),
        ex("leg_press", "レッグプレス", BodyPart.Legs, Equipment.Machine),
        ex("leg_extension", "レッグエクステンション", BodyPart.Legs, Equipment.Machine),
        ex("leg_curl", "レッグカール", BodyPart.Legs, Equipment.Machine),
        ex("romanian_deadlift", "ルーマニアンデッドリフト", BodyPart.Legs, Equipment.Barbell),
        ex("bulgarian_squat", "ブルガリアンスクワット", BodyPart.Legs, Equipment.Dumbbell, WeightMode.PerSide, RepMode.PerSide),
        ex("calf_raise", "カーフレイズ", BodyPart.Legs, Equipment.Machine),
        // 肩
        ex("shoulder_press", "ショルダープレス", BodyPart.Shoulders, Equipment.Dumbbell, WeightMode.PerSide),
        ex("side_raise", "サイドレイズ", BodyPart.Shoulders, Equipment.Dumbbell, WeightMode.PerSide),
        ex("rear_raise", "リアレイズ", BodyPart.Shoulders, Equipment.Dumbbell, WeightMode.PerSide),
        ex("face_pull", "フェイスプル", BodyPart.Shoulders, Equipment.Cable),
        // 腕
        ex("barbell_curl", "バーベルカール", BodyPart.Arms, Equipment.Barbell),
        ex("dumbbell_curl", "ダンベルカール", BodyPart.Arms, Equipment.Dumbbell, WeightMode.PerSide),
        ex("hammer_curl", "ハンマーカール", BodyPart.Arms, Equipment.Dumbbell, WeightMode.PerSide),
        ex("triceps_pushdown", "トライセプスプッシュダウン", BodyPart.Arms, Equipment.Cable),
        ex("skull_crusher", "スカルクラッシャー", BodyPart.Arms, Equipment.Barbell),
        // 腹
        ex("crunch", "クランチ", BodyPart.Abs, Equipment.Bodyweight, recordType = RecordType.Bodyweight),
        ex("leg_raise", "レッグレイズ", BodyPart.Abs, Equipment.Bodyweight, recordType = RecordType.Bodyweight),
        ex("plank", "プランク", BodyPart.Abs, Equipment.Bodyweight, recordType = RecordType.Duration),
        // 有酸素
        ex("running", "ランニング", BodyPart.Cardio, Equipment.Other, recordType = RecordType.DistanceTime),
        ex("bike", "バイク", BodyPart.Cardio, Equipment.Machine, recordType = RecordType.DistanceTime),
    ).mapIndexed { index, exercise -> exercise.copy(sortOrder = index) }

    fun gear(): List<Gear> = listOf(
        "ベルト",
        "パワーグリップ",
        "リストストラップ",
        "リストラップ",
        "ニースリーブ",
        "エルボースリーブ",
    ).mapIndexed { index, name -> Gear(id = "preset:gear_$index", name = name, sortOrder = index) }
}
