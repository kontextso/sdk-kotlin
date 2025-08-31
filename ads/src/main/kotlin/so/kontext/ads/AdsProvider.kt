package so.kontext.ads

import android.content.Context
import kotlinx.coroutines.flow.Flow
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.MessageRepresentable
import java.io.Closeable

public interface AdsProvider : Closeable {

    public val ads: Flow<Map<String, List<AdConfig>>>

    public class Builder(
        context: Context,
        publisherToken: String,
        userId: String,
        conversationId: String,
        enabledPlacementCodes: List<String> = listOf("inlineAd"),
    ) : AdsBuilder(
        context = context,
        publisherToken = publisherToken,
        userId = userId,
        conversationId = conversationId,
        enabledPlacementCodes = enabledPlacementCodes,
    )

    public suspend fun setMessages(messages: List<MessageRepresentable>)

    public fun isDisabled(isDisabled: Boolean)
}
