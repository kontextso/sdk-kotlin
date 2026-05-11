package so.kontext.ads.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import so.kontext.ads.KontextAds
import so.kontext.ads.model.Message
import so.kontext.ads.model.Role
import so.kontext.ads.model.SessionOptions
import so.kontext.ads.ui.InlineAd

private const val PLACEMENT_CODE = "inlineAd"

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ChatScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val session = remember {
        KontextAds.createSession(
            context = context.applicationContext,
            options = SessionOptions(
                publisherToken = BuildConfig.PUBLISHER_TOKEN,
                userId = BuildConfig.USER_ID,
                conversationId = "conv-${System.currentTimeMillis()}",
                onEvent = { event -> Log.d("KontextExample", "Event: $event") },
                onDebugEvent = { event, data -> Log.d("KontextExample", "Debug: $event $data") },
            ),
        )
    }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var lastAssistantId by remember { mutableStateOf<String?>(null) }
    var msgCounter by remember { mutableIntStateOf(0) }

    DisposableEffect(session) {
        onDispose { session.close() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kontext v4 — Kotlin") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)

                    // Ad after the last assistant message, when not loading
                    if (msg.role == Role.ASSISTANT && msg.id == lastAssistantId && !loading) {
                        InlineAd(messageId = msg.id, session = session)
                    }
                }

                if (loading) {
                    item {
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !loading,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val trimmed = input.trim()
                        if (trimmed.isEmpty() || loading) return@Button

                        loading = true
                        input = ""
                        msgCounter++
                        val userId = "u$msgCounter"

                        messages = messages + ChatMessage(userId, Role.USER, trimmed)

                        scope.launch {
                            // Fire-and-forget: preload runs in background
                            launch {
                                session.addMessage(Message(id = userId, role = Role.USER, content = trimmed))
                            }

                            // Simulate assistant response
                            delay(500)
                            msgCounter++
                            val assistantId = "a$msgCounter"
                            val assistantContent = "This is a response from the assistant."

                            messages = messages + ChatMessage(assistantId, Role.ASSISTANT, assistantContent)
                            lastAssistantId = assistantId

                            // Fire-and-forget: notify SDK of assistant message
                            launch {
                                session.addMessage(Message(id = assistantId, role = Role.ASSISTANT, content = assistantContent))
                            }

                            loading = false
                        }
                    },
                    enabled = input.trim().isNotEmpty() && !loading,
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
