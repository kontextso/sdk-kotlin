package so.kontext.ads

import android.content.Context
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.ChatMessage
import java.io.Closeable

public interface AdsProvider : Closeable {

    public class Builder(
        context: Context,
        publisherToken: String,
        userId: String,
        conversationId: String,
        messages: List<ChatMessage>,
    ) : AdsBuilder(
        context = context,
        publisherToken = publisherToken,
        userId = userId,
        conversationId = conversationId,
        messages = messages,
    )

    public suspend fun addMessage(message: ChatMessage): List<AdConfig>?

    public fun isDisabled(isDisabled: Boolean)
}
