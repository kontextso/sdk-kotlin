package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserEventNameTest {

    @Test
    fun `wireValue matches the IAB spec exactly`() {
        // wireValue is what gets posted into the iframe — pin it so a
        // future enum-name refactor can't silently break the JS-side
        // event handlers.
        assertEquals("user.typing.started", UserEventName.USER_TYPING_STARTED.wireValue)
    }

    @Test
    fun `enum has exactly the cases mirrored from sdk-swift`() {
        // Locks the case set so adding / removing an event is a
        // deliberate cross-SDK action, not a quiet drift.
        assertEquals(setOf("USER_TYPING_STARTED"), UserEventName.entries.map { it.name }.toSet())
    }
}
