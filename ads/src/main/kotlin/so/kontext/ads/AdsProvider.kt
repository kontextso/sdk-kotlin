package so.kontext.ads

import android.content.Context
import so.kontext.ads.domain.AdChatMessage
import so.kontext.ads.domain.AdConfig
import java.io.Closeable

public interface AdsProvider : Closeable {

    public class Builder(
        context: Context,
        publisherToken: String,
        userId: String,
        conversationId: String,
        messages: List<AdChatMessage>,
    ) : AdsBuilder(
        context = context,
        publisherToken = publisherToken,
        userId = userId,
        conversationId = conversationId,
        messages = messages,
    )

    public suspend fun setMessages(messages: List<AdChatMessage>)

    public suspend fun retrieveAds(messageId: String): List<AdConfig>?

    public fun isDisabled(isDisabled: Boolean)
}
