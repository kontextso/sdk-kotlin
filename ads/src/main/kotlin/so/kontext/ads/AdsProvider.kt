package so.kontext.ads

import android.content.Context
import kotlinx.coroutines.flow.Flow
import so.kontext.ads.domain.AdLoadEvent
import so.kontext.ads.domain.AdResult
import so.kontext.ads.domain.MessageRepresentable
import java.io.Closeable

public interface AdsProvider : Closeable {

    public val ads: Flow<AdResult>

    public val loadEvents: Flow<AdLoadEvent>

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
