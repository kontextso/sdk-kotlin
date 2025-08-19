package so.kontext.ads

import android.content.Context
import so.kontext.ads.domain.AdChatMessage
import so.kontext.ads.domain.Character
import so.kontext.ads.internal.AdsConfig
import so.kontext.ads.internal.AdsProviderImpl

public open class AdsBuilder(
    private val context: Context,
    private val publisherToken: String,
    private val userId: String,
    private val conversationId: String,
    private val messages: List<AdChatMessage>,
) {
    private var enabledPlacementCodes: List<String> = emptyList()
    private var character: Character? = null
    private var variantId: String? = null
    private var advertisingId: String? = null
    private var vendorId: String? = null
    private var isDisabled: Boolean = false
    private var adServerUrl: String = "https://server.megabrain.co"
    private var theme: String? = null

    public fun enabledPlacementCodes(codes: List<String>): AdsBuilder = apply { this.enabledPlacementCodes = codes }
    public fun character(character: Character): AdsBuilder = apply { this.character = character }
    public fun variantId(id: String): AdsBuilder = apply { this.variantId = id }
    public fun advertisingId(id: String): AdsBuilder = apply { this.advertisingId = id }
    public fun vendorId(id: String): AdsBuilder = apply { this.vendorId = id }
    public fun disabled(isDisabled: Boolean): AdsBuilder = apply { this.isDisabled = isDisabled }
    public fun adServerUrl(url: String): AdsBuilder = apply { this.adServerUrl = url }
    public fun addTheme(theme: String): AdsBuilder = apply { this.theme = theme }

    public fun build(): AdsProvider = AdsProviderImpl(
        context = context,
        initialMessages = messages,
        adsConfig = AdsConfig(
            publisherToken = publisherToken,
            userId = userId,
            conversationId = conversationId,
            enabledPlacementCodes = enabledPlacementCodes,
            character = character,
            variantId = variantId,
            advertisingId = advertisingId,
            vendorId = vendorId,
            isDisabled = isDisabled,
            adServerUrl = adServerUrl,
            theme = theme,
        ),
    )
}
