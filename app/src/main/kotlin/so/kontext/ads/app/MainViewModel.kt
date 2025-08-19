package so.kontext.ads.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import so.kontext.ads.AdsProvider
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Message(
    val role: Role,
    val text: String,
    val adsConfig: List<AdConfig>?,
)

class MainViewModel(
    private val application: Application,
) : ViewModel() {

    private val _messagesFlow: MutableStateFlow<List<Message>> = MutableStateFlow(emptyList())
    val messagesFlow = _messagesFlow.asStateFlow()

    private lateinit var adsProvider: AdsProvider

    fun initializeSdk() {
        adsProvider = AdsProvider.Builder(
            context = application,
            publisherToken = "nexus-dev",
            userId = UUID.randomUUID().toString(),
            conversationId = UUID.randomUUID().toString(),
            messages = emptyList()
        ).build()
    }

    fun addMessage(content: String) {
        viewModelScope.launch {
            val userMessage = Message(text = content, role = Role.User, adsConfig = null)
            _messagesFlow.update { currentMessages -> currentMessages + userMessage }

            adsProvider.addMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.User,
                    content = content,
                    createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )

            delay(1000) // Simulating network delay for Chat bot to respond

            val adsConfig = adsProvider.addMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.Assistant,
                    content = "Response from Chatbot",
                    createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )
            val assistantMessage = Message(
                text = "Response from Chatbot",
                role = Role.Assistant,
                adsConfig = adsConfig
            )
            _messagesFlow.update { currentMessages -> currentMessages + assistantMessage }
        }
    }

    override fun onCleared() {
        adsProvider.close()

        super.onCleared()
    }
}
