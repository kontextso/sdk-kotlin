package so.kontext.ads.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import so.kontext.ads.model.AddMessageOptions
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
        // BuildConfig.AD_SERVER_URL is empty when the publisher hasn't set
        // an override in local.properties — pass `null` in that case so the
        // SDK falls back to its production default.
        // (`Constants.DEFAULT_AD_SERVER_URL`).
        val adServerOverride: String? = BuildConfig.AD_SERVER_URL.takeIf { it.isNotBlank() }
        KontextAds.createSession(
            context = context.applicationContext,
            options = SessionOptions(
                publisherToken = BuildConfig.PUBLISHER_TOKEN,
                userId = "user-1",
                conversationId = "conv-${System.currentTimeMillis()}",
                adServerUrl = adServerOverride,
                onEvent = { event -> Log.d("KontextExample", "Event: $event") },
                // Drop the per-tick `update-dimensions-iframe` heartbeat
                // (200 ms cadence) — useful for the SDK's viewability
                // tracking but pure noise in the example's logcat. Every
                // other SDK-internal diagnostic still flows through.
                onDebugEvent = { event, data ->
                    val typeStr = (data as? Map<*, *>)?.get("type") as? String
                    if (typeStr != "update-dimensions-iframe") {
                        Log.d("KontextExample", "Debug: $event $data")
                    }
                },
            ),
        )
    }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var lastAssistantId by remember { mutableStateOf<String?>(null) }
    var msgCounter by remember { mutableIntStateOf(0) }
    var trackOnly by remember { mutableStateOf(false) }

    DisposableEffect(session) {
        onDispose { session.close() }
    }

    // Seed one user + one assistant message so the first /preload fires
    // immediately on launch (rather than waiting for the publisher to
    // type something). Mirrors sdk-swift's seedDemoMessages.
    LaunchedEffect(Unit) {
        val seedUser = ChatMessage("u-seed", Role.USER, "Hi Aria!")
        val seedAssistant = ChatMessage(
            "a-seed",
            Role.ASSISTANT,
            "Hi! I'm Aria, your friendly AI companion.",
        )
        messages = listOf(seedUser, seedAssistant)
        lastAssistantId = seedAssistant.id
        session.addMessage(Message(id = seedUser.id, role = Role.USER, content = seedUser.content))
        session.addMessage(
            Message(id = seedAssistant.id, role = Role.ASSISTANT, content = seedAssistant.content),
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // When an ad becomes visible after the assistant message is already
    // on screen, scroll back to the bottom — BUT only when the user is
    // already pinned to the bottom. A reader who scrolled up to re-read
    // an earlier message shouldn't get yanked back down by an ad-grow
    // event. Listens to: Filled (bid resolved), AdHeight (height grew),
    // RenderCompleted (iframe fully laid out).
    LaunchedEffect(session) {
        session.events.collect { event ->
            when (event) {
                is so.kontext.ads.model.AdEvent.Filled,
                is so.kontext.ads.model.AdEvent.AdHeight,
                is so.kontext.ads.model.AdEvent.RenderCompleted,
                -> {
                    if (messages.isNotEmpty() && listState.isAtBottom()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kontext v4 — Kotlin") },
                actions = {
                    Text(
                        text = "Track only",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    // Mirrors sdk-swift's Track-only switch on the example's
                    // nav bar: user messages route through
                    // AddMessageOptions(trackOnly = true), which marks the
                    // resulting /preload as analytics-only (no bid resolution).
                    Switch(
                        checked = trackOnly,
                        onCheckedChange = { trackOnly = it },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Pulls the input bar above the IME so a long thread doesn't
                // hide the latest message behind the keyboard. The system-bar
                // inset is already handled by Scaffold's padding above.
                .imePadding(),
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
                    // Column groups the bubble + its (optional) ad so they
                    // share one LazyColumn slot. The 8 dp Spacer below is
                    // only emitted when the ad actually renders, so we get
                    // breathing room between the assistant bubble and the
                    // ad without adding dead space to other rows.
                    Column {
                        MessageBubble(msg)
                        if (msg.role == Role.ASSISTANT && msg.id == lastAssistantId && !loading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InlineAd(messageId = msg.id, session = session)
                        }
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

                        // Snapshot the trackOnly switch state at send time
                        // so a mid-flight toggle doesn't apply retroactively
                        // to the assistant message handler.
                        val trackOnlyAtSendTime = trackOnly

                        scope.launch {
                            // Fire-and-forget: preload runs in background
                            launch {
                                session.addMessage(
                                    Message(id = userId, role = Role.USER, content = trimmed),
                                    AddMessageOptions(trackOnly = trackOnlyAtSendTime),
                                )
                            }

                            // Simulate assistant response
                            delay(500)
                            msgCounter++
                            val assistantId = "a$msgCounter"
                            val assistantContent = "This is a response from the assistant."

                            messages = messages + ChatMessage(assistantId, Role.ASSISTANT, assistantContent)
                            lastAssistantId = assistantId

                            // Fire-and-forget: notify SDK of assistant message.
                            // Assistant messages don't carry trackOnly — only
                            // user messages trigger preloads, so flag-on-user
                            // is the right scope (matches sdk-swift).
                            launch {
                                session.addMessage(
                                    Message(id = assistantId, role = Role.ASSISTANT, content = assistantContent),
                                )
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

/**
 * Whether the LazyColumn is currently pinned at (or within one item of)
 * the bottom. Read at event-collect time to gate the auto-scroll on ad
 * height changes — keeps a user who scrolled up from being yanked
 * back down.
 */
private fun LazyListState.isAtBottom(): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisible >= info.totalItemsCount - 2
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
