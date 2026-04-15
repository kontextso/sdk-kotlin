package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.Role
import so.kontext.ads.internal.AdsConfiguration
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.utils.deviceinfo.AppInfo
import so.kontext.ads.internal.utils.deviceinfo.AudioInfo
import so.kontext.ads.internal.utils.deviceinfo.BatteryState
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.deviceinfo.DeviceType
import so.kontext.ads.internal.utils.deviceinfo.HardwareInfo
import so.kontext.ads.internal.utils.deviceinfo.NetworkInfo
import so.kontext.ads.internal.utils.deviceinfo.OsInfo
import so.kontext.ads.internal.utils.deviceinfo.PowerInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenOrientation
import so.kontext.ads.internal.utils.deviceinfo.SdkInfo

class PreloadRequestMappersTest {

    private fun config(
        publisherToken: String = "pub-tok",
        userId: String = "u-1",
        conversationId: String = "c-1",
    ) = AdsConfiguration(
        adServerUrl = "https://ads.example",
        publisherToken = publisherToken,
        userId = userId,
        conversationId = conversationId,
        enabledPlacementCodes = listOf("inlineAd"),
        character = null,
        variantId = null,
        advertisingId = null,
        isDisabled = false,
        theme = null,
        regulatory = null,
        userEmail = null,
    )

    private fun deviceInfo(): DeviceInfo = DeviceInfo(
        osInfo = OsInfo("android", "14", "en-US", "UTC"),
        hardwareInfo = HardwareInfo("Samsung", "S24", DeviceType.Handset, 0L, false),
        screenInfo = ScreenInfo(1080, 2400, 3.0f, ScreenOrientation.Portrait, false),
        powerInfo = PowerInfo(100, BatteryState.Full, false),
        audioInfo = AudioInfo(50, false, false, emptyList()),
        networkInfo = NetworkInfo(null, null, null, null),
        appInfo = AppInfo("com.app", "1.0", null, 0, 0, 0),
        sdkInfo = SdkInfo("sdk-kotlin", "1.0.0", "android"),
    )

    private fun chatMessage(id: String, content: String = "c") = ChatMessage(
        id = id, role = Role.User, content = content,
        createdAt = "2025-01-01T00:00:00Z",
    )

    @Test
    fun `populates every field from AdsConfiguration`() {
        val request = createPreloadRequest(
            adsConfiguration = config(
                publisherToken = "tok",
                userId = "u",
                conversationId = "c",
            ),
            deviceInfo = deviceInfo(),
            sessionId = "sess-1",
            messages = emptyList(),
            isDisabled = false,
        )

        assertEquals("tok", request.publisherToken)
        assertEquals("u", request.userId)
        assertEquals("c", request.conversationId)
        assertEquals("sess-1", request.sessionId)
        assertEquals(listOf("inlineAd"), request.enabledPlacementCodes)
        assertEquals(false, request.isDisabled)
        assertNull(request.regulatory)
        assertNull(request.character)
        assertNull(request.variantId)
    }

    @Test
    fun `forwards isDisabled=true`() {
        val request = createPreloadRequest(
            adsConfiguration = config(),
            deviceInfo = deviceInfo(),
            sessionId = null,
            messages = emptyList(),
            isDisabled = true,
        )
        assertEquals(true, request.isDisabled)
    }

    @Test
    fun `trims messages to the last AdsProperties_NumberOfMessages`() {
        // Build N+10 messages and confirm only the trailing N make the body.
        val n = AdsProperties.NumberOfMessages
        val messages = (1..n + 10).map { chatMessage("m-$it") }

        val request = createPreloadRequest(
            adsConfiguration = config(),
            deviceInfo = deviceInfo(),
            sessionId = null,
            messages = messages,
            isDisabled = false,
        )

        assertEquals(n, request.messages.size)
        assertEquals("m-${messages.first().id.substringAfter('-').toInt() + 10}", request.messages.first().id)
        assertEquals(messages.last().id, request.messages.last().id)
    }

    @Test
    fun `does not pad when fewer than NumberOfMessages exist`() {
        val request = createPreloadRequest(
            adsConfiguration = config(),
            deviceInfo = deviceInfo(),
            sessionId = null,
            messages = listOf(chatMessage("m-1"), chatMessage("m-2")),
            isDisabled = false,
        )
        assertEquals(2, request.messages.size)
    }
}
