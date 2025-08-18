package so.kontext.ads.internal

import android.content.Context
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
import so.kontext.ads.domain.AdConfig
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
    initialMessages: List<ChatMessage>,
    private val adsConfig: AdsConfig,
) : AdsProvider {

    private val lock = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preloadResultDeferred: Deferred<Unit>? = null

    private val repository: AdsRepository = AdsRepositoryImpl(adsConfig.adServerUrl)
    private val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(context)
    private var sessionId: String? = null
    private val messages: MutableList<ChatMessage> = initialMessages.toMutableList()
    private var isDisabled: Boolean = adsConfig.isDisabled
    private var preloadTimeout: Duration = PreloadTimeoutDefault
    private var bids: List<Bid>? = null

    override suspend fun addMessage(message: ChatMessage): List<AdConfig>? = lock.withLock {
        if (isDisabled) return null

        messages.add(message)

        // Remove older messages
        while (messages.size > AdsProperties.NumberOfMessages) {
            messages.removeAt(0)
        }

        when (message.role) {
            Role.User -> {
                cancelPreload()
                preloadResultDeferred = scope.async { preload() }
                return null
            }
            Role.Assistant -> {
                val result = withTimeoutOrNull(preloadTimeout) {
                    preloadResultDeferred?.await()
                }
                if (result == null) cancelPreload() else preloadResultDeferred = null
                return getAdConfigs()
            }
        }
    }

    override fun isDisabled(isDisabled: Boolean) {
        this.isDisabled = isDisabled
    }

    private suspend fun preload() {
        val response = repository.preload(
            sessionId = sessionId,
            messages = messages,
            deviceInfo = deviceInfoProvider.deviceInfo,
            adsConfig = adsConfig,
        )

        return when (response) {
            is ApiResponse.Error -> {
                isDisabled = response.error is ApiError.PermanentError
            }
            is ApiResponse.Success -> {
                val result = response.data
                sessionId = result.sessionId
                preloadTimeout = result.preloadTimeout
                    ?.toDuration(DurationUnit.SECONDS) ?: PreloadTimeoutDefault
                bids = result.bids
            }
        }
    }

    private fun getAdConfigs(): List<AdConfig>? {
        val lastMessage = messages.lastOrNull() ?: return null

        return bids?.map { bid ->
            val iframeUrl = AdsProperties.iframeUrl(
                baseUrl = adsConfig.adServerUrl,
                bidId = bid.bidId,
                bidCode = bid.code,
                messageId = lastMessage.id,
            )
            AdConfig(
                url = iframeUrl,
                messages = messages.takeLast(AdsProperties.NumberOfMessages),
                messageId = lastMessage.id,
                sdk = AdsProperties.SdkName,
                otherParams = adsConfig.theme?.let { mapOf("theme" to it) } ?: emptyMap(),
                bid = bid,
            )
        }
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
