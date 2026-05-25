package so.kontext.ads.example

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import so.kontext.ads.KontextAds
import so.kontext.ads.Session
import so.kontext.ads.model.AddMessageOptions
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.Message
import so.kontext.ads.model.Role
import so.kontext.ads.model.SessionOptions

/** A chat message rendered in the demo. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    // Only the latest assistant message shows the ad; flipping this off on
    // older messages makes their ad disappear when a new message arrives.
    val showAd: Boolean = false,
)

/**
 * Traditional-View (RecyclerView) demo — the integration shape publishers use
 * from an XML / RecyclerView UI (e.g. the speakmaster app). Ads render through
 * [so.kontext.ads.ui.InlineAdView], bound in [MessageAdapter]'s
 * `onBindViewHolder`, with the RecyclerView scrolling/recycling natively.
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val adServerOverride: String? = BuildConfig.AD_SERVER_URL.takeIf { it.isNotBlank() }
        session = KontextAds.createSession(
            context = applicationContext,
            options = SessionOptions(
                publisherToken = BuildConfig.PUBLISHER_TOKEN,
                userId = "user-1",
                conversationId = "conv-${System.currentTimeMillis()}",
                adServerUrl = adServerOverride,
                onEvent = { event -> Log.d("KontextExample", "Event: $event") },
                onDebugEvent = { str, data ->
                    if ((data as? Map<*, *>)?.get("type") != "update-dimensions-iframe") {
                        Log.d("KontextExample", "Debug: $str $data")
                    }
                },
            ),
        )

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val input: EditText = findViewById(R.id.messageEditText)
        val send: Button = findViewById(R.id.sendButton)
        val trackOnlySwitch: Switch = findViewById(R.id.trackOnlySwitch)

        val adapter = MessageAdapter(session)
        // Top-aligned list (messages start at the top), matching the customer's app.
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val messages = mutableListOf<ChatMessage>()
        var counter = 0
        // True between sending a user message and the assistant reply arriving.
        // While loading, no ad is shown — so the previous ad disappears the
        // instant the user sends, and the new one appears with the reply.
        var loading = false

        // Reveal the bottom edge of the last item (the ad sits at the bottom of
        // its message and may exceed the viewport). Only ever scrolls DOWN, so
        // it can't yank a user who scrolled up.
        fun pinLastItemBottom() {
            val lm = recyclerView.layoutManager as LinearLayoutManager
            val last = adapter.itemCount - 1
            if (last < 0) return
            val view = lm.findViewByPosition(last) ?: return
            val overflow = view.bottom - (recyclerView.height - recyclerView.paddingBottom)
            if (overflow > 0) recyclerView.scrollBy(0, overflow)
        }

        fun scrollToBottom() {
            val last = adapter.itemCount - 1
            if (last < 0) return
            recyclerView.scrollToPosition(last)
            recyclerView.post { pinLastItemBottom() }
        }

        fun isNearBottom(): Boolean {
            val lm = recyclerView.layoutManager as LinearLayoutManager
            return lm.findLastVisibleItemPosition() >= adapter.itemCount - 2
        }

        // The ad's height is applied asynchronously by InlineAdView's poll, so
        // reveal the row's bottom once it has actually grown (onHeightChange) —
        // not on the earlier AdHeight event, which fires before the row resizes
        // and so leaves the ad's top peeking above the input. Posted so it runs
        // after the layout pass, and gated on near-bottom so a user who scrolled
        // up to read isn't yanked down.
        adapter.onAdResized = {
            if (isNearBottom()) recyclerView.post { pinLastItemBottom() }
        }

        fun publish() {
            // Only the latest assistant message shows the ad (and only when not
            // loading) — older ones flip to showAd=false, so DiffUtil rebinds
            // them and clears their ad.
            val lastAssistantId = messages.lastOrNull { it.role == Role.ASSISTANT }?.id
            val display = messages.map {
                it.copy(showAd = !loading && it.role == Role.ASSISTANT && it.id == lastAssistantId)
            }
            adapter.submitList(display) { scrollToBottom() }
        }

        fun sendUserMessage(text: String, trackOnly: Boolean) {
            counter++
            val userId = "u$counter"
            messages.add(ChatMessage(userId, Role.USER, text))
            loading = true
            publish()
            lifecycleScope.launch {
                // Only user messages carry trackOnly — they're what triggers
                // the /preload. trackOnly => analytics-only, no bid resolution.
                session.addMessage(
                    Message(id = userId, role = Role.USER, content = text),
                    AddMessageOptions(trackOnly = trackOnly),
                )
                delay(500)
                counter++
                val assistantId = "a$counter"
                val reply = "This is a response from the assistant."
                messages.add(ChatMessage(assistantId, Role.ASSISTANT, reply))
                loading = false
                publish()
                session.addMessage(Message(id = assistantId, role = Role.ASSISTANT, content = reply))
            }
        }

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                input.text.clear()
                sendUserMessage(text, trackOnlySwitch.isChecked)
            }
        }

        // The ad fills/resizes asynchronously after its message is added, so
        // reveal its bottom when the height changes — but only if the user is
        // already near the bottom (don't yank them up if they scrolled to read).
        lifecycleScope.launch {
            session.events.collect { event ->
                when (event) {
                    is AdEvent.Filled, is AdEvent.AdHeight, is AdEvent.RenderCompleted -> {
                        if (isNearBottom()) recyclerView.post { pinLastItemBottom() }
                    }
                    else -> Unit
                }
            }
        }

        // Seed an initial greeting (mirrors sdk-swift's seedDemoMessages) so
        // the first /preload fires on launch and the reply shows an ad.
        lifecycleScope.launch {
            messages.add(ChatMessage("u-seed", Role.USER, "Hi Aria!"))
            messages.add(ChatMessage("a-seed", Role.ASSISTANT, "Hi! I'm Aria, your friendly AI companion."))
            publish()
            session.addMessage(Message(id = "u-seed", role = Role.USER, content = "Hi Aria!"))
            session.addMessage(Message(id = "a-seed", role = Role.ASSISTANT, content = "Hi! I'm Aria, your friendly AI companion."))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.close()
    }
}
