package so.kontext.ads.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.kontext.ads.domain.AdDisplayPosition
import so.kontext.ads.domain.AdResult
import so.kontext.ads.domain.AdsMessage
import so.kontext.ads.domain.Bid
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.domain.Role
import so.kontext.ads.internal.data.api.AdsApi
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.repository.AdsRepository
import so.kontext.ads.internal.di.AdsModule
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.AppInfo
import so.kontext.ads.internal.utils.deviceinfo.AudioInfo
import so.kontext.ads.internal.utils.deviceinfo.BatteryState
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfoProvider
import so.kontext.ads.internal.utils.deviceinfo.DeviceType
import so.kontext.ads.internal.utils.deviceinfo.HardwareInfo
import so.kontext.ads.internal.utils.deviceinfo.NetworkInfo
import so.kontext.ads.internal.utils.deviceinfo.OsInfo
import so.kontext.ads.internal.utils.deviceinfo.PowerInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenOrientation
import so.kontext.ads.internal.utils.deviceinfo.SdkInfo
import java.io.IOException

/**
 * Exercises the orchestrator's setMessages → preload → AdResult pipeline
 * via real coroutines (AdsProviderImpl owns its internal scope; we can't
 * cleanly virtualize time through it). The waits below are realistic
 * (~2 s end-to-end per case) to cover the 300 ms debounce + 1 s minimum
 * delay in the source.
 *
 * Dependencies injected:
 * - DeviceInfoProvider is a fake with userAgent=null so the init-block
 *   AdsModule re-creation path does not fire.
 * - AdsModule is a mock (we never touch the real Ktor client).
 * - AdsRepository is a mock so we control the preload response shape.
 */
@RunWith(RobolectricTestRunner::class)
class AdsProviderImplTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var repo: AdsRepository
    private lateinit var deviceInfoProvider: DeviceInfoProvider
    private lateinit var adsModule: AdsModule

    @Before
    fun setUp() {
        repo = mockk()
        deviceInfoProvider = mockk {
            every { deviceInfo } returns fakeDeviceInfo()
        }
        adsModule = mockk(relaxed = true) {
            every { adsApi } returns mockk<AdsApi>(relaxed = true)
        }
    }

    private fun config(
        isDisabled: Boolean = false,
        enabledPlacementCodes: List<String> = listOf("inlineAd"),
    ) = AdsConfiguration(
        adServerUrl = "https://ads.test",
        publisherToken = "pub-tok",
        userId = "u-1",
        conversationId = "c-1",
        enabledPlacementCodes = enabledPlacementCodes,
        character = null,
        variantId = null,
        advertisingId = null,
        isDisabled = isDisabled,
        theme = null,
        regulatory = null,
        userEmail = null,
    )

    private fun fakeDeviceInfo(): DeviceInfo = DeviceInfo(
        osInfo = OsInfo("android", "14", "en-US", "UTC"),
        hardwareInfo = HardwareInfo("Samsung", "S24", DeviceType.Handset, 0L, false),
        screenInfo = ScreenInfo(1080, 2400, 3f, ScreenOrientation.Portrait, false),
        powerInfo = PowerInfo(100, BatteryState.Full, false),
        audioInfo = AudioInfo(50, false, false, emptyList()),
        // userAgent null → AdsProviderImpl init-block skips the re-creation branch.
        networkInfo = NetworkInfo(null, null, null, null),
        appInfo = AppInfo("com.app", "1.0", null, 0, 0, 0),
        sdkInfo = SdkInfo("sdk-kotlin", "1.0.0", "android"),
    )

    private fun provider(
        config: AdsConfiguration = config(),
    ): AdsProviderImpl {
        return AdsProviderImpl(
            context = context,
            initialMessages = emptyList(),
            dispatcher = Dispatchers.IO,
            adsConfiguration = config,
            deviceInfoProvider = deviceInfoProvider,
            adsModule = adsModule,
            repository = repo,
        )
    }

    /**
     * Subscribes to the flow, fires `block()`, waits up to `timeoutMs` for
     * `count` events, then returns them. Uses real time because
     * AdsProviderImpl runs its own coroutine scope on the caller's dispatcher.
     */
    private fun <T> Flow<T>.collectN(
        count: Int,
        timeoutMs: Long = 3_000,
        block: suspend () -> Unit,
    ): List<T> = runBlocking {
        val events = mutableListOf<T>()
        val job: Job = CoroutineScope(Dispatchers.IO).launch {
            take(count).toList(events)
        }
        block()
        withTimeoutOrNull(timeoutMs) { job.join() }
        events
    }

    private fun userMsg(id: String) = AdsMessage(id, Role.User, "hi", "2025-01-01T00:00:00Z")
    private fun assistantMsg(id: String) = AdsMessage(id, Role.Assistant, "hello", "2025-01-01T00:00:01Z")

    // ---- Preload happy path ----

    @Test
    fun `new user message triggers preload and emits Cleared then Filled`() {
        val bid = Bid(
            bidId = "bid-1",
            code = "inlineAd",
            adDisplayPosition = AdDisplayPosition.AfterAssistantMessage,
        )
        coEvery { repo.preload(any(), any(), any(), any(), any(), any()) } returns ApiResponse.Success(
            PreloadResult(
                bids = listOf(bid),
                sessionId = "s-1",
                remoteLogLevel = null,
                preloadTimeout = null,
                skip = null,
                skipCode = null,
            ),
        )

        val provider = provider()
        val events = provider.ads.collectN(2, timeoutMs = 5_000) {
            provider.setMessages(listOf(userMsg("u-1"), assistantMsg("a-1")))
            // 300 ms debounce + repo.preload (instant mock) + 1 s minimumDelayJob
            delay(1_600)
        }

        assertTrue("Expected at least 2 events, got ${events.size}", events.size >= 2)
        assertTrue("First event should be Cleared, got ${events[0]}", events[0] is AdResult.Cleared)
        assertTrue("Second event should be Filled, got ${events[1]}", events[1] is AdResult.Filled)
        val filled = events[1] as AdResult.Filled
        // The ad binds to the assistant message (afterAssistantMessage position).
        assertTrue(filled.ads.containsKey("a-1"))
    }

    @Test
    fun `preload skip response emits NoFill with skipCode`() {
        coEvery { repo.preload(any(), any(), any(), any(), any(), any()) } returns ApiResponse.Success(
            PreloadResult(
                bids = null, sessionId = "s",
                remoteLogLevel = null, preloadTimeout = null,
                skip = true, skipCode = "rate_limit",
            ),
        )

        val provider = provider()
        val events = provider.ads.collectN(2, timeoutMs = 5_000) {
            provider.setMessages(listOf(userMsg("u-1")))
            delay(1_600)
        }

        assertTrue("Expected >=2 events", events.size >= 2)
        assertTrue(events[0] is AdResult.Cleared)
        val noFill = events[1] as AdResult.NoFill
        assertEquals("rate_limit", noFill.skipCode)
    }

    @Test
    fun `empty bids yields NoFill with unfilled_bid`() {
        coEvery { repo.preload(any(), any(), any(), any(), any(), any()) } returns ApiResponse.Success(
            PreloadResult(
                bids = emptyList(), sessionId = "s",
                remoteLogLevel = null, preloadTimeout = null,
                skip = null, skipCode = null,
            ),
        )

        val provider = provider()
        val events = provider.ads.collectN(2, timeoutMs = 5_000) {
            provider.setMessages(listOf(userMsg("u-1")))
            delay(1_600)
        }
        assertTrue("Expected >=2 events", events.size >= 2)
        val noFill = events[1] as AdResult.NoFill
        assertEquals("unfilled_bid", noFill.skipCode)
    }

    // ---- Preload errors ----

    @Test
    fun `network error emits AdResult_Error`() {
        coEvery { repo.preload(any(), any(), any(), any(), any(), any()) } returns ApiResponse.Error(
            ApiError.Connection(IOException("dns")),
        )

        val provider = provider()
        val events = provider.ads.collectN(2, timeoutMs = 5_000) {
            provider.setMessages(listOf(userMsg("u-1")))
            delay(1_600)
        }
        assertTrue("Expected >=2 events", events.size >= 2)
        assertTrue(events[0] is AdResult.Cleared)
        assertTrue(events[1] is AdResult.Error)
    }

    // ---- isDisabled forwarding ----

    @Test
    fun `setMessages propagates the current isDisabled flag to the repository`() {
        val isDisabledSlot = slot<Boolean>()
        coEvery {
            repo.preload(any(), any(), any(), any(), any(), capture(isDisabledSlot))
        } returns ApiResponse.Success(
            PreloadResult(null, "s", null, null, null, null),
        )

        val provider = provider(config(isDisabled = true))
        // The preload runs only when the flow is being collected — AdsProviderImpl's
        // pipeline is a cold flow. Collecting the first 2 events is enough to
        // trigger one preload.
        provider.ads.collectN(2, timeoutMs = 5_000) {
            provider.setMessages(listOf(userMsg("u-1")))
            delay(1_600)
        }

        assertTrue(isDisabledSlot.isCaptured)
        assertEquals(true, isDisabledSlot.captured)
    }

    // ---- Same user message does not re-preload ----

    @Test
    fun `setting the same last-user message twice does not trigger a second preload`() {
        coEvery { repo.preload(any(), any(), any(), any(), any(), any()) } returns ApiResponse.Success(
            PreloadResult(null, "s", null, null, null, null),
        )

        val provider = provider()
        provider.ads.collectN(4, timeoutMs = 5_000) {
            provider.setMessages(listOf(userMsg("u-1")))
            delay(1_600)
            provider.setMessages(listOf(userMsg("u-1"))) // same id — no new preload
            delay(1_000)
        }

        coVerify(exactly = 1) { repo.preload(any(), any(), any(), any(), any(), any()) }
    }
}
