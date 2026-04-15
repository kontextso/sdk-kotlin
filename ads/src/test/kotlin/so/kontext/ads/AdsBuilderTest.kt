package so.kontext.ads

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.Character
import so.kontext.ads.domain.Regulatory

class AdsBuilderTest {

    private fun context(): Context = mockk(relaxed = true) {
        every { applicationContext } returns this
    }

    @Test
    fun `each setter returns the same builder instance for chaining`() {
        val builder = AdsBuilder(
            context = context(),
            publisherToken = "tok",
            userId = "u",
            conversationId = "c",
            enabledPlacementCodes = listOf("inlineAd"),
        )

        assertSame(builder, builder.initialMessages(emptyList()))
        assertSame(builder, builder.character(Character(id = "c-1", name = "Max")))
        assertSame(builder, builder.variantId("v-1"))
        assertSame(builder, builder.advertisingId("ad-1"))
        assertSame(builder, builder.disabled(true))
        assertSame(builder, builder.adServerUrl("https://custom.example"))
        assertSame(builder, builder.addTheme("dark"))
        assertSame(builder, builder.regulatory(Regulatory(gdpr = 1)))
        assertSame(builder, builder.userEmail("x@y.z"))
    }
}
