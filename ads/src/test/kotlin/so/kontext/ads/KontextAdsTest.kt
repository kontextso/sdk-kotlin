package so.kontext.ads

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import so.kontext.ads.model.SessionOptions

/**
 * `KontextAds.createSession` is mostly a thin factory, but it owns one
 * non-trivial responsibility: unwrapping the publisher's Context to
 * `applicationContext` before the long-lived `Session` retains it.
 * Forgetting this leaks Activity / Fragment contexts into the session
 * and is exactly the kind of bug that doesn't fail anything visibly —
 * pin it.
 */
class KontextAdsTest {

    @Test
    fun `createSession unwraps Activity context to applicationContext`() {
        val applicationCtx = mockk<Context>(relaxed = true)
        val activityCtx = mockk<Context>(relaxed = true)
        every { activityCtx.applicationContext } returns applicationCtx

        val session = KontextAds.createSession(
            context = activityCtx,
            options = SessionOptions(
                publisherToken = "tok",
                userId = "u",
                conversationId = "c",
                // Unreachable address: any /init / GAID coroutines fired
                // by Session.init fail fast and stay out of the assertion.
                adServerUrl = "http://127.0.0.1:1",
            ),
        )

        try {
            assertSame(applicationCtx, session.context)
        } finally {
            session.destroy()
        }
    }
}
