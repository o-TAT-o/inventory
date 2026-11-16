package io.github.otato.traininglog.platform

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.github.otato.traininglog.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 購入の操作の結果（画面に案内を出す）。 */
enum class BillingMessage(val text: String) {
    Purchased("広告を消しました。ありがとうございます"),
    Restored("購入を復元しました"),
    NothingToRestore("復元できる購入は、見つかりませんでした"),
    Pending("お支払いの確認待ちです。確認できると、広告が消えます"),
    Unavailable("いまは、Google Play で購入できません。あとでもう一度お試しください"),
    Failed("購入できませんでした"),
}

/**
 * 広告を消す買い切り（F-AD-04〜06）。
 *
 * 購入は Google Play の仕組みだけで行い、アプリは、買ったかどうかだけを受け取る（メールアドレスや決済の情報は受け取らない）。
 * Google Play への接続は、設定の画面を開いたとき・購入や復元を押したときに、はじめて行う。
 */
class RemoveAdsBilling(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    private val _price = MutableStateFlow<String?>(null)

    /** 「¥300」のような値段。Google Play から取れるまで null。 */
    val price: StateFlow<String?> = _price

    private val _messages = MutableStateFlow<BillingMessage?>(null)
    val messages: StateFlow<BillingMessage?> = _messages

    private var product: ProductDetails? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                _messages.value = when (apply(purchases.orEmpty())) {
                    Outcome.Owned -> BillingMessage.Purchased
                    Outcome.Pending -> BillingMessage.Pending
                    Outcome.None -> null
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> _messages.value = BillingMessage.Failed
        }
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    fun consumeMessage() {
        _messages.value = null
    }

    /** 値段を取りにいく。あわせて、購入済みかを確かめる（別の端末で買った人・払い戻した人に合わせる）。 */
    fun refresh() {
        scope.launch {
            if (!connect()) return@launch
            loadProduct()
            syncOwned()
        }
    }

    /** 購入の画面（Google Play）を開く。 */
    fun purchase(activity: Activity) {
        scope.launch {
            if (!connect()) {
                _messages.value = BillingMessage.Unavailable
                return@launch
            }
            val details = product ?: loadProduct()
            if (details == null) {
                _messages.value = BillingMessage.Unavailable
                return@launch
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
                )
                .build()
            client.launchBillingFlow(activity, params)
        }
    }

    /** 購入を復元する（機種変更・再インストールのあと。F-AD-05）。 */
    fun restore() {
        scope.launch {
            if (!connect()) {
                _messages.value = BillingMessage.Unavailable
                return@launch
            }
            _messages.value = when (syncOwned()) {
                Outcome.Owned -> BillingMessage.Restored
                Outcome.Pending -> BillingMessage.Pending
                Outcome.None -> BillingMessage.NothingToRestore
                null -> BillingMessage.Unavailable
            }
        }
    }

    private enum class Outcome { Owned, Pending, None }

    private suspend fun loadProduct(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull() ?: return null
        product = details
        _price.value = details.oneTimePurchaseOfferDetails?.formattedPrice
        return details
    }

    /** Google Play に、いま持っている購入を聞いて、設定に合わせる。聞けなかったら null。 */
    private suspend fun syncOwned(): Outcome? {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        val outcome = apply(result.purchasesList)
        // 払い戻しなどで、購入がなくなっていたら、広告を戻す
        if (outcome == Outcome.None) settings.update { it.copy(adsRemoved = false) }
        return outcome
    }

    private suspend fun apply(purchases: List<Purchase>): Outcome {
        val mine = purchases.filter { PRODUCT_ID in it.products }
        val bought = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (bought == null) {
            return if (mine.any { it.purchaseState == Purchase.PurchaseState.PENDING }) Outcome.Pending else Outcome.None
        }
        if (!bought.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(bought.purchaseToken).build())
        }
        settings.update { it.copy(adsRemoved = true) }
        return Outcome.Owned
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // 次に使うときに、つなぎ直す
                }
            })
        }
    }

    private companion object {
        /** Google Play Console に登録する、買い切りの商品の ID。 */
        const val PRODUCT_ID = "remove_ads"
    }
}
