package so.kontext.ads
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import so.kontext.ads.model.Character
import so.kontext.ads.model.Regulatory
import so.kontext.ads.model.SessionOptions
import java.net.URI

class ConfigurationTest {

    @Test
    fun `resolveConfig applies defaults`() {
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals("tok", config.publisherToken)
        assertEquals("user", config.userId)
        assertEquals("conv", config.conversationId)
        assertEquals(listOf("inlineAd"), config.enabledPlacementCodes)
        assertEquals(Constants.DEFAULT_AD_SERVER_URL, config.adServerUrl)
        assertEquals(TEST_INSTALL_ID, config.installId)
        assertNull(config.character)
        assertNull(config.regulatory)
    }

    @Test
    fun `resolveConfig passes adServerUrl through unchanged`() {
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                adServerUrl = "https://example.com",
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals("https://example.com", config.adServerUrl)
    }

    @Test
    fun `resolveConfig fills empty placement codes with default`() {
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                enabledPlacementCodes = emptyList(),
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals(listOf("inlineAd"), config.enabledPlacementCodes)
    }

    @Test
    fun `resolveConfig fills null placement codes with default`() {
        // `null` and `emptyList()` both indicate "publisher didn't specify"
        // — both must resolve to the default placement code. The null case
        // exists because `SessionOptions.enabledPlacementCodes` is nullable
        // (defaults applied at the resolution boundary, mirroring sdk-swift).
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                enabledPlacementCodes = null,
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals(listOf("inlineAd"), config.enabledPlacementCodes)
    }

    @Test
    fun `resolveConfig keeps non-empty placement codes unchanged`() {
        // The passthrough branch (`?.takeIf { it.isNotEmpty() }`): a publisher
        // that supplies a real list must get it back verbatim, not the default.
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                enabledPlacementCodes = listOf("inlineAd", "sidebar"),
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals(listOf("inlineAd", "sidebar"), config.enabledPlacementCodes)
    }

    @Test
    fun `resolveConfig fills null adServerUrl with default`() {
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                adServerUrl = null,
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals(Constants.DEFAULT_AD_SERVER_URL, config.adServerUrl)
    }

    @Test
    fun `resolveConfig passes through all optional fields`() {
        val character = Character(id = "c1", name = "Bot", avatarUrl = URI.create("https://example.com/bot.png"))
        val regulatory = Regulatory(gdpr = 1, gdprConsent = "consent")

        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                character = character,
                regulatory = regulatory,
                variantId = "v1",
                userEmail = "user@example.com",
                advertisingId = "gaid",
            ),
            installId = TEST_INSTALL_ID,
        )

        assertEquals(character, config.character)
        assertEquals(regulatory, config.regulatory)
        assertEquals("v1", config.variantId)
        assertEquals("user@example.com", config.userEmail)
        assertEquals("gaid", config.advertisingId)
    }

    @Test
    fun `resolveConfig passes through onEvent and onDebugEvent callbacks`() {
        val onEvent: so.kontext.ads.model.AdEventHandler = { _ -> }
        val onDebugEvent: so.kontext.ads.model.DebugEventHandler = { _, _ -> }

        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                onEvent = onEvent,
                onDebugEvent = onDebugEvent,
            ),
            installId = TEST_INSTALL_ID,
        )

        assertSame(onEvent, config.onEvent)
        assertSame(onDebugEvent, config.onDebugEvent)
    }

    @Test
    fun `resolveConfig passes installId through unchanged`() {
        // Production: `KontextAds.createSession` resolves the ID via
        // `InstallIdProvider.getOrCreate(appContext)` and forwards it
        // here; the resolver itself stays Context-free.
        val custom = "01890000-0000-7000-8000-deadbeef0001"
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
            ),
            installId = custom,
        )

        assertEquals(custom, config.installId)
    }

    @Test
    fun `constants have expected values`() {
        assertEquals("sdk-kotlin", SDKInfo.NAME)
        assertEquals("4.0.4", SDKInfo.VERSION)
        assertEquals("android", SDKInfo.PLATFORM)
        assertEquals(30, Constants.MAX_MESSAGES)
    }
}
