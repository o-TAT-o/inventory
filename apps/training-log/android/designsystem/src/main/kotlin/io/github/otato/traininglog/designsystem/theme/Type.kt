package io.github.otato.traininglog.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 文字のスタイル。デザインの大きさに合わせる。数字は、桁がそろう（tabular）字形を使う。
 * フォントは端末の標準（Roboto / Noto Sans JP）。フォントファイルを同梱しない（軽さのため）。
 */
@Immutable
data class AppTypography(
    /** アプリバーの題名 */
    val title: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    /** 見出し（カードの題名・種目名） */
    val heading: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val bodyStrong: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    /** 小さな見出し（セクションの名前） */
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, lineHeight = 17.sp),
    /** ボタンの文字 */
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    /** 記録画面の大きな数字（重量・回数） */
    val number: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", lineHeight = 28.sp),
    /** 休憩タイマーの時間 */
    val timer: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
)

/** Material3 の部品（スイッチ・ダイアログなど）の文字も、同じ大きさにそろえる。 */
internal fun AppTypography.toMaterial(): Typography = Typography(
    titleLarge = title,
    titleMedium = heading,
    bodyLarge = bodyStrong,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = button.copy(fontSize = 14.sp),
    labelMedium = label,
    labelSmall = caption,
)
