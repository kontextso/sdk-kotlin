package so.kontext.ads.internal

import android.content.Context
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import so.kontext.ads.AdsProvider
import so.kontext.ads.domain.AdChatMessage
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.AdDisplayPosition
import so.kontext.ads.domain.Bid
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.Role
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.repository.AdsRepository
import so.kontext.ads.internal.data.repository.AdsRepositoryImpl
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfoProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val PreloadTimeoutDefault = 5.seconds

internal class AdsProviderImpl(
    context: Context,
    initialMessages: List<AdChatMessage>,
    private val adsConfig: AdsConfig,
) : AdsProvider {

    private val lock = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preloadResultDeferred: Deferred<List<Bid>?>? = null

    private val repository: AdsRepository = AdsRepositoryImpl(adsConfig.adServerUrl)
    private val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(context)
    private var sessionId: String? = null
    private val messages: MutableList<ChatMessage> = initialMessages.map { it.toInternalMessage() }.toMutableList()
    private var isDisabled: Boolean = adsConfig.isDisabled
    private var preloadTimeout: Duration = PreloadTimeoutDefault

    private var lastPreloadUserMessageCount: Int

    init {
        lastPreloadUserMessageCount = initialMessages.count { it.role == Role.User }
        if (lastPreloadUserMessageCount > 0) {
            preloadResultDeferred = scope.async { preload() }
        }
    }

    override suspend fun setMessages(messages: List<AdChatMessage>) = lock.withLock {
        if (isDisabled) return@withLock

        this.messages.clear()
        this.messages.addAll(messages.map { it.toInternalMessage() })

        while (this.messages.size > AdsProperties.NumberOfMessages) {
            this.messages.removeAt(0)
        }

        val currentUserMessageCount = this.messages.count { it.role == Role.User }
        if (currentUserMessageCount > lastPreloadUserMessageCount) {
            cancelPreload()
            preloadResultDeferred = scope.async { preload() }
            lastPreloadUserMessageCount = currentUserMessageCount
        }
    }

    override suspend fun retrieveAds(messageId: String): List<AdConfig>? = lock.withLock {
        if (isDisabled) {
            return null
        }
        val bids = withTimeoutOrNull(preloadTimeout) {
            preloadResultDeferred?.await()
        }
        if (bids == null) {
            cancelPreload()
            return null
        }

        return getAdConfigs(bids = bids, messageId = messageId)
    }

    override fun isDisabled(isDisabled: Boolean) {
        this.isDisabled = isDisabled
    }

    private suspend fun preload(): List<Bid>? {
        val response = repository.preload(
            sessionId = sessionId,
            messages = messages,
            deviceInfo = deviceInfoProvider.deviceInfo,
            adsConfig = adsConfig,
        )

        return when (response) {
            is ApiResponse.Error -> {
                isDisabled = response.error is ApiError.PermanentError
                null
            }
            is ApiResponse.Success -> {
                val result = response.data
                sessionId = result.sessionId
                preloadTimeout = result.preloadTimeout
                    ?.toDuration(DurationUnit.SECONDS) ?: PreloadTimeoutDefault
                result.bids
            }
        }
    }

    private fun getAdConfigs(bids: List<Bid>, messageId: String): List<AdConfig>? {
        val enabledCodes = adsConfig.enabledPlacementCodes
        val lastMessageRole = messages.firstOrNull { it.id == messageId }?.role

        val relevantBids = bids.filter { bid ->
            val codeIsValid = enabledCodes.contains(bid.code)

            val placementIsValid = when (bid.adDisplayPosition) {
                AdDisplayPosition.AfterAssistantMessage -> lastMessageRole == Role.Assistant
                AdDisplayPosition.AfterUserMessage -> lastMessageRole == Role.User
            }

            codeIsValid && placementIsValid
        }

        return relevantBids.map { bid ->
            val iframeUrl = AdsProperties.iframeUrl(
                baseUrl = adsConfig.adServerUrl,
                bidId = bid.bidId,
                bidCode = bid.code,
                messageId = messageId,
            )
            AdConfig(
                url = iframeUrl,
                messages = messages.takeLast(AdsProperties.NumberOfMessages),
                messageId = messageId,
                sdk = AdsProperties.SdkName,
                otherParams = adsConfig.theme?.let { mapOf("theme" to it) } ?: emptyMap(),
                bid = bid,
            )
        }
    }

    private fun AdChatMessage.toInternalMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            role = role,
            content = content,
            createdAt = createdAt,
        )
    }

    private fun cancelPreload() {
        preloadResultDeferred?.cancel()
        preloadResultDeferred = null
    }

    override fun close() {
        cancelPreload()
        scope.cancel()
        repository.close()
    }
}
