package so.kontext.ads.internal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
import so.kontext.ads.internal.data.mapper.toInternalMessage
import so.kontext.ads.internal.data.repository.AdsRepository
import so.kontext.ads.internal.data.repository.AdsRepositoryImpl
import so.kontext.ads.internal.di.AdsModule
import so.kontext.ads.internal.ui.InlineAdWebViewPool
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfoProvider
import so.kontext.ads.internal.utils.om.OmSdk
import so.kontext.ads.internal.utils.om.WebViewOmSession
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val PreloadTimeoutDefault = 5.seconds

@OptIn(FlowPreview::class)
@Suppress("LongParameterList")
internal class AdsProviderImpl(
    context: Context,
    initialMessages: List<MessageRepresentable>,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val adsConfiguration: AdsConfiguration,
    private var adsModule: AdsModule = AdsModule(adsConfiguration.adServerUrl),
    private val repository: AdsRepository = AdsRepositoryImpl(adsModule.adsApi),
    private val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(context),
) : AdsProvider {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var preloadJob: Job? = null
    private var sessionId: String? = null
    private var isDisabled: Boolean = adsConfiguration.isDisabled
    private var preloadTimeout: Duration = PreloadTimeoutDefault

    private val messagesFlow = MutableStateFlow(initialMessages.map { it.toInternalMessage() })
    private var bidsCache: List<Bid>? = null
    private val lastError = MutableStateFlow<ApiError?>(null)

    private var lastUserMessageId: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override val ads: Flow<AdResult> =
        combine(messagesFlow, lastError) { messages, error ->
            messages to error
        }
            .debounce(300.milliseconds)
            .flatMapLatest { (currentMessages, currentError) ->
                flow {
                    if (currentError != null) {
                        emit(AdResult.Error(KontextError.NetworkError(cause = currentError)))
                        return@flow
                    }
                    if (isDisabled) {
                        emit(AdResult.Error(KontextError.AdUnavailable()))
                        return@flow
                    }
                    if (currentMessages.isEmpty()) {
                        emit(AdResult.Success(emptyMap()))
                        return@flow
                    }

                    val newLastUserMessage = currentMessages.lastOrNull { it.role == Role.User }

                    if (newLastUserMessage != null && newLastUserMessage.id != lastUserMessageId) {
                        lastUserMessageId = newLastUserMessage.id
                        preloadJob?.cancel()
                        preloadJob = scope.launch {
                            bidsCache = preload(currentMessages)
                        }
                    }
                    preloadJob?.join()

                    val bidsCache = bidsCache
                    if (bidsCache.isNullOrEmpty()) {
                        emit(AdResult.Error(KontextError.AdUnavailable()))
                        return@flow
                    }

                    val adResultMap = currentMessages.mapNotNull { message ->
                        val adConfigs = getAdConfigs(bidsCache, message.id, currentMessages)
                        if (adConfigs.isNullOrEmpty()) {
                            null
                        } else {
                            message.id to adConfigs
                        }
                    }.toMap()

                    emit(AdResult.Success(adResultMap))
                }
            }.distinctUntilChanged()

    init {
        OmSdk.init(context)
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

    override fun close() {
        preloadJob?.cancel()
        scope.cancel()
        adsModule.close()
        InlineAdWebViewPool.clearAll()
        OmSdk.close()
        WebViewOmSession.close()
    }
}
