package io.github.otato.traininglog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.otato.traininglog.designsystem.components.AppTopBar
import io.github.otato.traininglog.designsystem.theme.AppTheme

/** 設定の「このアプリについて」から開く、読むだけの画面。 */
enum class InfoKind(val route: String, val title: String) {
    Terms("terms", "利用規約"),
    Privacy("privacy", "プライバシーポリシー"),
    Licenses("licenses", "ライセンス表示"),
    ;

    companion object {
        fun fromRoute(route: String): InfoKind = entries.firstOrNull { it.route == route } ?: Terms
    }
}

/** 見出しと本文の組。 */
private data class InfoSection(val heading: String, val body: String)

/**
 * 利用規約・プライバシーポリシー・ライセンス表示。
 * 文章はアプリの中に持つ（通信しない）。公開の前に、内容を見直して確定する。
 */
@Composable
fun InfoScreen(kind: InfoKind, onBack: () -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(kind.title, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            sectionsOf(kind).forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section.heading, style = AppTheme.typography.bodyStrong, color = colors.fg)
                    Text(section.body, style = AppTheme.typography.body, color = colors.sub)
                }
            }
        }
    }
}

private fun sectionsOf(kind: InfoKind): List<InfoSection> = when (kind) {
    InfoKind.Terms -> listOf(
        InfoSection("このアプリについて", "このアプリは、筋力トレーニングの記録を、端末の中に残すためのものです。無料で、すべての機能を使えます。"),
        InfoSection("記録の扱い", "記録は、この端末の中だけに保存されます。機種変更や、アプリを消す前には、「データ管理」から書き出して、ご自身で保管してください。書き出しをしないまま失われた記録は、元に戻せません。"),
        InfoSection("トレーニングについて", "このアプリは、記録のための道具で、医療や指導の助言をするものではありません。トレーニングは、ご自身の体調と責任で行ってください。"),
        InfoSection("広告と購入", "画面の下に、広告を出すことがあります（記録中の画面には出しません）。広告は、買い切りの購入で消せます。購入と払い戻しは、Google Play の決まりに従います。"),
        InfoSection("免責", "このアプリを使ったこと、または使えなかったことで生じた損害について、開発者は、法律で認められる範囲で、責任を負いません。"),
        InfoSection("変更", "この規約は、アプリの更新にあわせて、変えることがあります。"),
    )
    InfoKind.Privacy -> listOf(
        InfoSection("集めないもの", "このアプリは、アカウントを作りません。名前・メールアドレス・トレーニングの記録を、開発者や外部のサーバーに送りません。記録は、この端末の中だけに保存されます。"),
        InfoSection("書き出したファイル", "「データ管理」で書き出したファイルは、あなたが選んだ場所（メール・ファイル・クラウドなど）にだけ渡ります。"),
        InfoSection("広告", "広告の表示には、Google AdMob を使います。広告の配信のために、Google が、広告 ID・端末の情報・おおよその位置などを扱うことがあります。くわしくは、Google のプライバシーポリシーをご覧ください。広告を消す購入をすると、広告の仕組みは読み込まれません。"),
        InfoSection("購入", "広告を消す購入は、Google Play を通して行います。このアプリが受け取るのは、購入したかどうかだけです。お支払いの情報は受け取りません。"),
        InfoSection("通知", "休憩が終わったことを知らせるために、通知を使います。通知の中身は、端末の外に送りません。"),
    )
    InfoKind.Licenses -> listOf(
        InfoSection("Apache License 2.0", "このアプリは、次のソフトウェアを使っています。いずれも Apache License, Version 2.0 で公開されています。\nhttps://www.apache.org/licenses/LICENSE-2.0"),
        InfoSection("Kotlin / kotlinx.coroutines / kotlinx.serialization", "Copyright JetBrains s.r.o. and contributors"),
        InfoSection("AndroidX / Jetpack Compose", "Copyright The Android Open Source Project"),
        InfoSection("SQLDelight", "Copyright Square, Inc."),
        InfoSection("Reorderable", "Copyright Calvin Liang"),
        InfoSection("Google Mobile Ads SDK / Google Play Billing Library", "Google が、それぞれの利用規約のもとで提供しています。\nhttps://developers.google.com/admob/terms\nhttps://developer.android.com/studio/terms"),
    )
}
