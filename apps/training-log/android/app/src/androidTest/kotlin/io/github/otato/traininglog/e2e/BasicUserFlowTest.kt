package io.github.otato.traininglog.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.otato.traininglog.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * e2e（エミュレーターや実機で、アプリを本物のまま動かして確かめる）。ユーザーの基本の操作だけを、最小限にそろえている。
 *
 * - 計算や保存のくわしい確認は、ユニットテスト（`src/test`。JVM で動く）の役目。ここでは、
 *   「画面をつないで、人が触る順に操作すると、期待どおりに進む」ことだけを見る
 * - 1 つのテストごとに、アプリのデータを空にして始める（オーケストレーターの clearPackageData）
 * - 画面の文言で探す。文言を変えたら、ここも直す
 *
 * 動かし方: エミュレーターを起動して、`./gradlew :app:connectedDebugAndroidTest`
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BasicUserFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** 記録の基本: 自由に記録を始め、種目を足し、セットを完了して終える。履歴で見られる。 */
    @Test
    fun 自由に記録して終えると履歴で見られる() = with(compose) {
        clickText("自由に記録を始める")
        clickText("種目を追加")
        clickText("ベンチプレス")

        // 下のパネルの「完了」で、最初のセットを完了する
        clickText("完了")

        // 終える → 確認。同じ文言のボタンが、画面の上と確認の中にある（確認のほうが、あとに出る）
        clickText("終える")
        clickLastText("終える")
        waitForText("おつかれさまでした")
        clickText("ホームへ")

        waitForText("前回のワークアウト")
        clickText("履歴")
        // 履歴は、今日の日付が選ばれていて、その日の記録が出る
        waitForText("ベンチプレス", substring = true)
    }

    /** ルーティン: 作って、種目を足すと目標をすぐ入れられ、そこから記録を始められる。 */
    @Test
    fun ルーティンを作り種目を足して目標を決めそこから記録を始められる() = with(compose) {
        clickText("ルーティン")
        clickText("ルーティンを作る")
        inputIntoDialogField("Push")
        clickText("決定")

        clickText("種目を追加")
        clickText("ベンチプレス")

        // 足した直後は、その種目のカードが開いていて、目標をすぐ入れられる
        waitForText("目標と休憩を設定中", substring = true)
        clickContentDescription("セット数 を増やす")
        clickContentDescription("ベンチプレスの目標をとじる")
        waitForText("3 セット", substring = true)

        // ホームに戻ると、次のルーティンとして出て、そこから始められる
        clickContentDescription("戻る")
        clickText("今日")
        clickText("Push を始める")
        waitForText("ベンチプレス", substring = true)
        waitForText("完了")
    }

    /** 乗り換え: 前のアプリの直近の値を入れると、はじめのワークアウトで「前回」として出る。 */
    @Test
    fun 乗り換え用の初期設定を入れるとワークアウトで前回の記録として出る() = with(compose) {
        clickText("ほかのアプリから乗り換える")
        clickText("種目を足す")
        clickText("ベンチプレス")
        clickText("1 種目を追加")

        inputIntoField("ベンチプレスの重量（kg）", "60")
        inputIntoField("ベンチプレスの回数", "8")
        inputIntoField("ベンチプレスのセット数", "4")
        clickText("保存する")

        clickText("自由に記録を始める")
        clickText("種目を追加")
        clickText("ベンチプレス")
        waitForText("前回（前のアプリ）", substring = true)
    }
}

// ---- 操作の部品 ----

private const val TIMEOUT_MILLIS = 15_000L

private typealias Compose = AndroidComposeTestRule<*, *>

/** 画面にその文言が出るまで待つ（データは別のスレッドで読むので、出るまで少し待つことがある）。 */
@OptIn(ExperimentalTestApi::class)
private fun Compose.waitForText(text: String, substring: Boolean = false) =
    waitUntilAtLeastOneExists(hasText(text, substring = substring), TIMEOUT_MILLIS)

private fun Compose.clickText(text: String) {
    waitForText(text)
    onAllNodesWithText(text)[0].performClick()
}

/** 同じ文言が複数あるとき、あとに出ているもの（確認の中のボタンなど）を押す。 */
private fun Compose.clickLastText(text: String) {
    waitUntil(TIMEOUT_MILLIS) { onAllNodesWithText(text).fetchSemanticsNodes().size >= 2 }
    onAllNodesWithText(text).onLast().performClick()
}

@OptIn(ExperimentalTestApi::class)
private fun Compose.waitForContentDescription(description: String) =
    waitUntilAtLeastOneExists(hasContentDescription(description), TIMEOUT_MILLIS)

private fun Compose.clickContentDescription(description: String) {
    waitForContentDescription(description)
    onNodeWithContentDescription(description).performClick()
}

private fun Compose.inputIntoField(description: String, text: String) {
    waitForContentDescription(description)
    onNodeWithContentDescription(description).performTextInput(text)
}

/** 名前を入れるダイアログの、文字を入れる欄に入力する。 */
private fun Compose.inputIntoDialogField(text: String) {
    waitUntil(TIMEOUT_MILLIS) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    onAllNodes(hasSetTextAction())[0].performTextInput(text)
}
