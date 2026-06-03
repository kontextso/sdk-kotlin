package so.kontext.ads.example

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
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

/**
 * Per-character chat screen for the crash repro. Each entry creates its OWN
 * [Session] (fresh conversationId) and destroys it in [onDestroy] via
 * `session.close()` — exactly the speakmaster lifecycle: one Activity + one
 * Session per character, torn down on Back.
 *
 * Identical chat mechanics to [MainActivity]; the only difference is that the
 * screen is reachable from [IntroActivity], parameterized by character, and
 * always destroyed when the user goes Back — which is what exercises the
 * process-global OMID teardown across Activities.
 */
class CharacterChatActivity : ComponentActivity() {

    private lateinit var session: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val character = intent.getStringExtra(EXTRA_CHARACTER) ?: "Aria"
        findViewById<TextView>(R.id.characterTitle).text = "Chat with $character"

        val adServerOverride: String? = BuildConfig.AD_SERVER_URL.takeIf { it.isNotBlank() }
        session = KontextAds.createSession(
            context = applicationContext,
            options = SessionOptions(
                publisherToken = BuildConfig.PUBLISHER_TOKEN,
                userId = "user-1",
                // Fresh conversation per character entry — a brand-new Session
                // each time, so nothing carries over except the process-global
                // OMID SDK state we're testing.
                conversationId = "conv-$character-${System.currentTimeMillis()}",
                adServerUrl = adServerOverride,
                onEvent = { event -> Log.d("KontextExample", "[$character] Event: $event") },
                onDebugEvent = { str, data ->
                    if ((data as? Map<*, *>)?.get("type") != "update-dimensions-iframe") {
                        Log.d("KontextExample", "[$character] Debug: $str $data")
                    }
                },
            ),
        )

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val input: EditText = findViewById(R.id.messageEditText)
        val send: Button = findViewById(R.id.sendButton)
        val trackOnlySwitch: Switch = findViewById(R.id.trackOnlySwitch)
        val back: Button = findViewById(R.id.backButton)

        back.setOnClickListener { finish() }

        val adapter = MessageAdapter(session)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val messages = mutableListOf<ChatMessage>()
        var counter = 0
        var loading = false

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

        adapter.onAdResized = {
            if (isNearBottom()) recyclerView.post { pinLastItemBottom() }
        }

        fun publish() {
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

        // Seed a greeting so the first /preload fires on entry and the reply
        // shows an ad without the user having to type — makes the repro a
        // two-tap loop (open character → ad appears → Back → repeat).
        lifecycleScope.launch {
            messages.add(ChatMessage("u-seed", Role.USER, "Hi $character!"))
            messages.add(ChatMessage("a-seed", Role.ASSISTANT, "Hi! I'm $character, your friendly AI companion."))
            publish()
            session.addMessage(Message(id = "u-seed", role = Role.USER, content = "Hi $character!"))
            session.addMessage(
                Message(id = "a-seed", role = Role.ASSISTANT, content = "Hi! I'm $character, your friendly AI companion."),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.close()
    }

    companion object {
        const val EXTRA_CHARACTER: String = "character"
    }
}
