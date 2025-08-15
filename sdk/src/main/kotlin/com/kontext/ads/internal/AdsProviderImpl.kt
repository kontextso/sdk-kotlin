package com.kontext.ads.internal

import android.content.Context
import com.kontext.ads.AdsProvider
import com.kontext.ads.domain.AdConfig
import com.kontext.ads.domain.ChatMessage
import com.kontext.ads.domain.Role
import com.kontext.ads.internal.data.error.ApiError
import com.kontext.ads.internal.data.repository.AdsRepository
import com.kontext.ads.internal.data.repository.AdsRepositoryImpl
import com.kontext.ads.internal.utils.ApiResponse
import com.kontext.ads.internal.utils.deviceinfo.DeviceInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val PreloadTimeout = 5.seconds

internal class AdsProviderImpl(
    context: Context,
    initialMessages: List<ChatMessage>,
    private val adsConfig: AdsConfig,
) : AdsProvider {

    private val lock = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preloadResultDeferred: Deferred<List<AdConfig>?>? = null

    private val repository: AdsRepository = AdsRepositoryImpl(adsConfig.adServerUrl)
    private val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(context)
    private var sessionId: String? = null
    private val messages: MutableList<ChatMessage> = initialMessages.toMutableList()
    private var isDisabled: Boolean = adsConfig.isDisabled

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
                val result = withTimeoutOrNull(PreloadTimeout) {
                    preloadResultDeferred?.await()
                }
                if (result == null) cancelPreload() else preloadResultDeferred = null
                return result
            }
        }
    }

    override fun isDisabled(isDisabled: Boolean) {
        this.isDisabled = isDisabled
    }

    private suspend fun preload(): List<AdConfig>? {
        val response = repository.preload(
            sessionId = sessionId,
            messages = messages,
            deviceInfo = deviceInfoProvider.deviceInfo,
            adsConfig = adsConfig,
        )

        return when (response) {
            is ApiResponse.Error -> {
                if (response.error is ApiError.PermanentError) {
                    isDisabled = true
                }
                null
            }
            is ApiResponse.Success -> {
                response.data
            }
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
