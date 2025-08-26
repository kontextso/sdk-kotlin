package so.kontext.ads.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import so.kontext.ads.AdsProvider
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.MessageRepresentable
import so.kontext.ads.domain.Role
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class MessageRepresentableUi(
    override val id: String,
    override val role: Role,
    override val createdAt: String,
    val text: String,
    val adsConfig: List<AdConfig>? = null,
) : MessageRepresentable {
    override val content: String get() = text
}

class MainViewModel(
    private val application: Application,
) : ViewModel() {

    private val _messagesFlow: MutableStateFlow<List<MessageRepresentableUi>> = MutableStateFlow(emptyList())
    val messagesFlow = _messagesFlow.asStateFlow()

    private lateinit var adsProvider: AdsProvider

    init {
        initializeSdk()

        viewModelScope.launch {
            adsProvider.ads.collect { adMap ->
                updateMessagesWithAds(adMap)
            }
        }
    }

    fun initializeSdk() {
        adsProvider = AdsProvider.Builder(
            context = application,
            publisherToken = "polybuzz-dev",
            userId = UUID.randomUUID().toString(),
            conversationId = UUID.randomUUID().toString(),
            enabledPlacementCodes = listOf("inlineAd"),
        ).build()
    }

    fun addMessage(content: String) {
        viewModelScope.launch {
            val userMessageUi = createChatMessage(content, Role.User)
            _messagesFlow.update { currentMessages -> currentMessages + userMessageUi }

            adsProvider.setMessages(_messagesFlow.value)

            delay(500) // Simulating network delay for Chat bot to respond

            val assistantMessageUi = createChatMessage(role = Role.Assistant, content = "Response from Chatbot")
            _messagesFlow.update { currentMessages -> currentMessages + assistantMessageUi }

            adsProvider.setMessages(_messagesFlow.value)
        }
    }

    private fun updateMessagesWithAds(adMap: Map<String, List<AdConfig>>) {
        _messagesFlow.update { currentMessages ->
            currentMessages.map { message ->
                message.copy(adsConfig = adMap[message.id])
            }
        }
    }

    private fun createChatMessage(content: String, role: Role): MessageRepresentableUi {
        return MessageRepresentableUi(
            id = UUID.randomUUID().toString(),
            role = role,
            text = content,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
        )
    }

    override fun onCleared() {
        adsProvider.close()

        super.onCleared()
    }
}
