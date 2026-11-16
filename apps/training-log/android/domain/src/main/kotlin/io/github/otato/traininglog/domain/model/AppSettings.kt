package io.github.otato.traininglog.domain.model

enum class WeightUnit { Kg, Lb }

enum class ThemeMode { Light, Dark, System }

enum class WeekStart { Sunday, Monday }

/** ホームの日付の帯の並び（F-SE-06）。 */
enum class HomeStripMode {
    /** その週（週の始まりに合わせて 7 日） */
    Week,

    /** 今日を真ん中（前後 3 日ずつ） */
    CenteredToday,

    /** 直近 7 日（今日が右端） */
    Last7Days,
}

/** 設定（F-SE-01〜08）。重量の刻みは、表示する単位の値で持つ。 */
data class AppSettings(
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val weightStepKg: Double = 2.5,
    val weightStepLb: Double = 5.0,
    val themeMode: ThemeMode = ThemeMode.System,
    val weekStart: WeekStart = WeekStart.Monday,
    /** 1日の区切りの時刻（0〜6 時）。0 時から区切りまでは、前の日として扱う（F-SE-08） */
    val dayBoundaryHour: Int = 4,
    val homeStripMode: HomeStripMode = HomeStripMode.Week,
    val restBetweenSetsSec: Int = 90,
    val restAfterExerciseSec: Int = 120,
    val autoStartRest: Boolean = true,
    val soundOn: Boolean = true,
    val vibrationOn: Boolean = true,
    /** 最後に全データを書き出した時刻（エポックミリ秒）。まだなければ null（F-DA-09） */
    val lastExportAtMillis: Long? = null,
    /** 端末の中に、自動で世代バックアップを残すか（F-DA-10） */
    val autoBackup: Boolean = true,
    /** 最後に自動バックアップを作った時刻（エポックミリ秒） */
    val lastAutoBackupAtMillis: Long? = null,
    /** 広告を消す購入をしているか（F-AD-04）。購入の確認（復元）は、Google Play の仕組みで行う */
    val adsRemoved: Boolean = false,
) {
    /** いまの単位での、± ボタンの刻み。 */
    val weightStep: Double
        get() = if (weightUnit == WeightUnit.Kg) weightStepKg else weightStepLb
}
