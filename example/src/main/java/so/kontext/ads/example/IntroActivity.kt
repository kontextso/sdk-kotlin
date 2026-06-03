package so.kontext.ads.example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity

/**
 * Launcher screen for the character-switch crash repro.
 *
 * Lists three characters; tapping one opens [CharacterChatActivity] on a
 * fresh [so.kontext.ads.Session]. The reproduction is: open a character →
 * view a Kontext ad → press Back (the chat Activity is destroyed and
 * `session.close()` runs) → open any character again → the app crashes in
 * OMID's TreeWalker when the next ad reaches its display timing.
 *
 * Mirrors the speakmaster flow: one Activity (and one Session) per character
 * entry, destroyed on exit, with a process-global OMID SDK surviving across
 * Activities.
 */
class IntroActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        fun openCharacter(name: String) {
            startActivity(
                Intent(this, CharacterChatActivity::class.java)
                    .putExtra(CharacterChatActivity.EXTRA_CHARACTER, name),
            )
        }

        findViewById<Button>(R.id.characterAria).setOnClickListener { openCharacter("Aria") }
        findViewById<Button>(R.id.characterMilo).setOnClickListener { openCharacter("Milo") }
        findViewById<Button>(R.id.characterNova).setOnClickListener { openCharacter("Nova") }

        findViewById<Button>(R.id.legacyDemo).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
