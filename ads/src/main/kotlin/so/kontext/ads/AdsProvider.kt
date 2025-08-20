package so.kontext.ads

import android.content.Context
import kotlinx.coroutines.flow.Flow
import so.kontext.ads.domain.AdChatMessage
import so.kontext.ads.domain.AdConfig
import java.io.Closeable

public interface AdsProvider : Closeable {

    public val ads: Flow<Map<String, List<AdConfig>>>

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

    public fun isDisabled(isDisabled: Boolean)
}
