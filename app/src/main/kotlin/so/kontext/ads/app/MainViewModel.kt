package so.kontext.ads.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import so.kontext.ads.AdsProvider
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import so.kontext.ads.domain.AdChatMessage
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class MessageUi(
    override val id: String,
    override val role: Role,
    override val createdAt: String,
    val text: String,
    val adsConfig: List<AdConfig>? = null,
) : AdChatMessage {
    override val content: String get() = text
}

class MainViewModel(
    private val application: Application,
) : ViewModel() {

    private val _messagesFlow: MutableStateFlow<List<MessageUi>> = MutableStateFlow(emptyList())
    val messagesFlow = _messagesFlow.asStateFlow()

    private lateinit var adsProvider: AdsProvider

    fun initializeSdk() {
        adsProvider = AdsProvider.Builder(
            context = application,
            publisherToken = "nexus-dev",
            userId = UUID.randomUUID().toString(),
            conversationId = UUID.randomUUID().toString(),
            messages = emptyList()
        )
            .enabledPlacementCodes(listOf("inlineAd"))
            .build()
    }

    fun addMessage(content: String) {
        viewModelScope.launch {
            val userMessageUi = createChatMessage(content, Role.User)
            _messagesFlow.update { currentMessages -> currentMessages + userMessageUi }

            adsProvider.setMessages(_messagesFlow.value)
            val adUserConfigs = adsProvider.retrieveAds(messageId = userMessageUi.id)
            if (adUserConfigs != null) {
                updateMessageWithAds(messageId = userMessageUi.id, adConfigs = adUserConfigs)
            }

            delay(500) // Simulating network delay for Chat bot to respond

            val assistantMessageUi = createChatMessage(role = Role.Assistant, content = "Response from Chatbot")
            _messagesFlow.update { currentMessages -> currentMessages + assistantMessageUi }

            adsProvider.setMessages(_messagesFlow.value)
            val adAssistantConfigs = adsProvider.retrieveAds(messageId = assistantMessageUi.id)
            if (adAssistantConfigs != null) {
                updateMessageWithAds(messageId = assistantMessageUi.id, adConfigs = adAssistantConfigs)
            }
        }
    }

    private fun updateMessageWithAds(messageId: String, adConfigs: List<AdConfig>) {
        _messagesFlow.update { currentMessages ->
            currentMessages.map { message ->
                if (message.id == messageId) {
                    message.copy(adsConfig = adConfigs)
                } else {
                    message
                }
            }
        }
    }

    private fun createChatMessage(content: String, role: Role): MessageUi {
        return MessageUi(
            id = UUID.randomUUID().toString(),
            role = role,
            text = content,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        )
    }

    override fun onCleared() {
        adsProvider.close()

        super.onCleared()
    }
}
