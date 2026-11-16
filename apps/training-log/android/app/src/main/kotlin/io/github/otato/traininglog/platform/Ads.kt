package io.github.otato.traininglog.platform

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import io.github.otato.traininglog.ui.common.rememberContainer
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 広告（F-AD-01〜03、F-AD-07）。
 *
 * - 出すのは、画面の下のバナーだけ。記録中の画面には置かない
 * - 広告の SDK は、バナーを初めて出すときに読み込む（起動と記録画面を遅くしない）
 * - 広告を消す購入をした人には、SDK を読み込まない
 */
object Ads {
    /** Google が公開している、テスト用のバナーの ID。公開の前に、自分の ID に差し替える。 */
    const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            // 初期化は SDK が別のスレッドで行う（マニフェストの OPTIMIZE_INITIALIZATION）
            MobileAds.initialize(context.applicationContext) {}
        }
    }
}

/**
 * 画面の下の広告バナー。広告を消す購入をしていれば、何も出さない（場所も取らない）。
 * [enabled] が false のときも出さない（記録中に開いた画面など。F-HI-07）。
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val container = rememberContainer()
    val removedFlow = remember(container) { container.data.settings.observe().map { it.adsRemoved } }
    // 設定を読み終えるまでは、出さない（購入した人に、一瞬でも広告を見せない）
    val removed by removedFlow.collectAsStateWithLifecycle(initialValue = true)
    if (removed || !enabled) return

    Box(modifier = modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                Ads.ensureInitialized(context)
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = Ads.BANNER_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
