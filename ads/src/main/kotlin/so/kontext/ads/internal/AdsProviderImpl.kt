package so.kontext.ads.internal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import so.kontext.ads.AdsProvider
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.AdDisplayPosition
import so.kontext.ads.domain.AdResult
import so.kontext.ads.domain.Bid
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.MessageRepresentable
import so.kontext.ads.domain.Role
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.error.KontextError
import so.kontext.ads.internal.data.repository.AdsRepository
import so.kontext.ads.internal.data.repository.AdsRepositoryImpl
import so.kontext.ads.internal.di.AdsModule
import so.kontext.ads.internal.ui.InlineAdPool
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfoProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val PreloadTimeoutDefault = 5.seconds

internal class AdsProviderImpl(
    context: Context,
    initialMessages: List<MessageRepresentable>,
    private val adsConfiguration: AdsConfiguration,
) : AdsProvider {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preloadJob: Job? = null

    private var adsModule: AdsModule = AdsModule(adsConfiguration.adServerUrl)
    private val repository: AdsRepository = AdsRepositoryImpl(adsModule.adsApi)
    private val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(context)
    private var sessionId: String? = null
    private var isDisabled: Boolean = adsConfiguration.isDisabled
    private var preloadTimeout: Duration = PreloadTimeoutDefault

    private val messagesFlow = MutableStateFlow(initialMessages.map { it.toInternalMessage() })
    private val bidsCacheFlow = MutableStateFlow<List<Bid>?>(null)
    private val isPreloading = MutableStateFlow(false)
    private val lastError = MutableStateFlow<ApiError?>(null)

    init {
        scope.launch {
            var lastUserMessageCount = messagesFlow.value.count { it.role == Role.User }

            messagesFlow.collect { currentMessages ->
                val currentUserMessageCount = currentMessages.count { it.role == Role.User }
                if (currentUserMessageCount > lastUserMessageCount) {
                    lastUserMessageCount = currentUserMessageCount

                    preloadJob?.cancel()
                    preloadJob = launch {
                        isPreloading.value = true
                        val newBids = preload(currentMessages)
                        bidsCacheFlow.value = newBids
                        isPreloading.value = false
                    }
                }
            }
        }
    }

    override val ads: Flow<AdResult> =
        combine(messagesFlow, bidsCacheFlow, isPreloading, lastError) { messages, bids, loading, error ->
            if (error != null) {
                return@combine AdResult.Error(KontextError.NetworkError(cause = error))
            }

            if (isDisabled || bids.isNullOrEmpty()) {
                return@combine AdResult.Error(KontextError.AdUnavailable())
            }

            val lastUserMessage = messages.lastOrNull { it.role == Role.User }
            val boundaryIndex = if (lastUserMessage != null) messages.indexOf(lastUserMessage) else -1

            messages.mapNotNull { message ->
                val messageIndex = messages.indexOf(message)

                if (loading && boundaryIndex != -1 && messageIndex >= boundaryIndex) {
                    return@mapNotNull null
                }

                val adConfigs = getAdConfigs(bids, message.id, messages)
                if (adConfigs.isNullOrEmpty()) {
                    return@mapNotNull null
                } else {
                    message.id to adConfigs
                }
            }
                .toMap()
                .let {
                    AdResult.Success(it)
                }
        }

    override suspend fun setMessages(messages: List<MessageRepresentable>) {
        if (isDisabled) return
        this.messagesFlow.value = messages.map { it.toInternalMessage() }
    }

    override fun isDisabled(isDisabled: Boolean) {
        this.isDisabled = isDisabled
    }

    private suspend fun preload(messages: List<ChatMessage>): List<Bid>? {
        lastError.value = null

        val response = repository.preload(
            sessionId = sessionId,
            messages = messages,
            deviceInfo = deviceInfoProvider.deviceInfo,
            adsConfiguration = adsConfiguration,
            timeout = preloadTimeout.inWholeMilliseconds,
        )

        return when (response) {
            is ApiResponse.Error -> {
                Log.d("Kontext SDK", response.error.cause.toString())
                lastError.value = response.error
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

    private fun getAdConfigs(bids: List<Bid>, messageId: String, messages: List<ChatMessage>): List<AdConfig>? {
        val enabledCodes = adsConfiguration.enabledPlacementCodes

        val targetMessage = messages.firstOrNull { it.id == messageId } ?: return null
        val lastUserMessage = messages.lastOrNull { it.role == Role.User }
        val lastAssistantMessage = messages.lastOrNull { it.role == Role.Assistant }

        val relevantBids = bids.filter { bid ->
            val codeIsValid = enabledCodes.contains(bid.code)

            val placementIsValid = when (bid.adDisplayPosition) {
                AdDisplayPosition.AfterAssistantMessage -> targetMessage.role == Role.Assistant && targetMessage.id == lastAssistantMessage?.id
                AdDisplayPosition.AfterUserMessage -> targetMessage.role == Role.User && targetMessage.id == lastUserMessage?.id
            }

            codeIsValid && placementIsValid
        }

        return relevantBids.map { bid ->
            val otherParams = createOtherParams(
                theme = adsConfiguration.theme,
            )
            val iframeUrl = AdsProperties.baseIFrameUrl(
                baseUrl = adsConfiguration.adServerUrl,
                bidId = bid.bidId,
                bidCode = bid.code,
                messageId = messageId,
                otherParams = otherParams,
            )
            AdConfig(
                adServerUrl = adsConfiguration.adServerUrl,
                iFrameUrl = iframeUrl,
                messages = messages.takeLast(AdsProperties.NumberOfMessages),
                messageId = messageId,
                sdk = AdsProperties.SdkName,
                bid = bid,
                otherParams = otherParams,
            )
        }
    }

    private fun createOtherParams(
        theme: String?,
    ): Map<String, String> {
        return buildMap {
            theme?.let { put("theme", it) }
        }
    }

    private fun MessageRepresentable.toInternalMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            role = role,
            content = content,
            createdAt = createdAt,
        )
    }

    override fun close() {
        preloadJob?.cancel()
        scope.cancel()
        adsModule.close()
        InlineAdPool.clearAll()
    }
}
