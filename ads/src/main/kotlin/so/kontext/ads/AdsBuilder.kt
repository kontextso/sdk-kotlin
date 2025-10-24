package so.kontext.ads

import android.content.Context
import so.kontext.ads.domain.Character
import so.kontext.ads.domain.MessageRepresentable
import so.kontext.ads.domain.Regulatory
import so.kontext.ads.internal.AdsConfiguration
import so.kontext.ads.internal.AdsProviderImpl

/**
 * Builder for AdsProvider
 *
 * @param context Application context
 * @param publisherToken This token is not a secret.Publishers typically use two types of tokens:
 *        - Developer token: `{publisher}-dev` (for testing)
 *        - Production token: `{publisher}-unique`
 * @param userId A unique string that should remain the same during the user’s lifetime
 *        (used for retargeting and rewarded ads)
 * @param conversationId Unique ID of the conversation. Represents the entire conversation between
 *        the user and the assistant. For example, in apps like ChatGPT, every new chat thread has
 *        a unique conversationId. This ID remains the same even if the user refreshes the page or
 *        returns to the same conversation later.
 * @param messages creation of the message timestamp according to ISO 8601 format
 * @param enabledPlacementCodes A list of placement codes that should be enabled for the conversation
 */
public open class AdsBuilder(
    private val context: Context,
    private val publisherToken: String,
    private val userId: String,
    private val conversationId: String,
    private var enabledPlacementCodes: List<String>,
) {
    private var messages: List<MessageRepresentable> = emptyList()
    private var character: Character? = null
    private var variantId: String? = null
    private var advertisingId: String? = null
    private var isDisabled: Boolean = false
    private var adServerUrl: String = "https://server.megabrain.co"
    private var theme: String? = null
    private var regulatory: Regulatory? = null

    public fun initialMessages(messages: List<MessageRepresentable>): AdsBuilder = apply { this.messages = messages }

    /**
     * @param character The character object used in this conversation
     */
    public fun character(character: Character): AdsBuilder = apply { this.character = character }

    /**
     * A variant ID that helps determine which type of ad to render.
     *
     * @param id This ID is typically unique for each publisher and is defined based on an agreement
     *        between the publisher and Kontext.so.
     */
    public fun variantId(id: String): AdsBuilder = apply { this.variantId = id }

    /**
     * @param id Device-specific identifier provided by the operating systems (IDFA), only if available.
     */
    public fun advertisingId(id: String): AdsBuilder = apply { this.advertisingId = id }

    /**
     * @param isDisabled enables or disables generation of ads
     */
    public fun disabled(isDisabled: Boolean): AdsBuilder = apply { this.isDisabled = isDisabled }

    /**
     * @param url URL of the server from which the ads are served. If no value is provided, this
     *        url us used: https://server.megabrain.co
     */
    public fun adServerUrl(url: String): AdsBuilder = apply { this.adServerUrl = url }

    public fun addTheme(theme: String): AdsBuilder = apply { this.theme = theme }

    public fun regulatory(regulatory: Regulatory): AdsBuilder = apply { this.regulatory = regulatory }

    public fun build(): AdsProvider = AdsProviderImpl(
        context = context.applicationContext,
        initialMessages = messages,
        adsConfiguration = AdsConfiguration(
            publisherToken = publisherToken,
            userId = userId,
            conversationId = conversationId,
            enabledPlacementCodes = enabledPlacementCodes,
            character = character,
            variantId = variantId,
            advertisingId = advertisingId,
            isDisabled = isDisabled,
            adServerUrl = adServerUrl,
            theme = theme,
            regulatory = regulatory,
        ),
    )
}
