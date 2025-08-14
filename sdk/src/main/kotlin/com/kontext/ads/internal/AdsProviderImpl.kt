package com.kontext.ads.internal

import android.content.Context
import com.kontext.ads.AdsConfig
import com.kontext.ads.AdsProvider
import com.kontext.ads.domain.AdConfig
import com.kontext.ads.domain.ChatMessage
import com.kontext.ads.domain.Role
import com.kontext.ads.internal.data.repository.AdsRepository
import com.kontext.ads.internal.data.repository.AdsRepositoryImpl
import com.kontext.ads.internal.utils.DeviceInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private val PreloadTimeout = 5.seconds

internal class AdsProviderImpl(
    context: Context,
    initialMessages: List<ChatMessage>,
    private val adsConfig: AdsConfig,
) : AdsProvider {

    private val lock = Mutex()
    private val repository: AdsRepository = AdsRepositoryImpl(adsConfig.adServerUrl)
    private val deviceInfoProvider = DeviceInfoProvider(context)
    private var sessionId: String = UUID.randomUUID().toString()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val messages: MutableList<ChatMessage> = initialMessages.toMutableList()
    private var preloadResultDeferred: Deferred<List<AdConfig>?>? = null

    override suspend fun addMessage(message: ChatMessage): List<AdConfig>? = lock.withLock {
        if (adsConfig.isDisabled) return null

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

    private suspend fun preload(): List<AdConfig>? {
        return repository.preload(
            sessionId = sessionId,
            messages = messages,
            deviceInfo = deviceInfoProvider.deviceInfo,
            adsConfig = adsConfig,
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
