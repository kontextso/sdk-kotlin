package so.kontext.ads.internal.utils.deviceinfo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.kontext.ads.internal.AdsProperties

/**
 * Smoke-level tests for DeviceInfoProvider — verifies that it can instantiate
 * and produce a well-formed DeviceInfo under Robolectric without throwing.
 *
 * Per-collector accuracy is platform-dependent and doesn't round-trip
 * cleanly without a real device. We only assert shape + invariants.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceInfoProviderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `can be constructed under Robolectric`() {
        val provider = DeviceInfoProvider(context)
        assertNotNull(provider)
    }

    @Test
    fun `deviceInfo returns a non-null snapshot with every sub-info populated`() {
        val info = DeviceInfoProvider(context).deviceInfo
        assertNotNull(info.osInfo)
        assertNotNull(info.hardwareInfo)
        assertNotNull(info.screenInfo)
        assertNotNull(info.powerInfo)
        assertNotNull(info.audioInfo)
        assertNotNull(info.networkInfo)
        assertNotNull(info.appInfo)
        assertNotNull(info.sdkInfo)
    }

    @Test
    fun `sdkInfo exposes the configured SDK name and platform`() {
        val info = DeviceInfoProvider(context).deviceInfo
        assertEquals(AdsProperties.SdkName, info.sdkInfo.sdkName)
        assertEquals(AdsProperties.PlatformName, info.sdkInfo.sdkPlatform)
        // Version comes from BuildConfig — we only assert it's non-empty.
        assertTrue(info.sdkInfo.sdkVersion.isNotEmpty())
    }

    @Test
    fun `osInfo has a valid os name and a non-empty timezone`() {
        val os = DeviceInfoProvider(context).deviceInfo.osInfo
        assertTrue(os.osName.isNotEmpty())
        assertTrue(os.osVersion.isNotEmpty())
        assertTrue(os.locale.isNotEmpty())
        assertTrue(os.timezone.isNotEmpty())
    }

    @Test
    fun `hardwareInfo reports a DeviceType`() {
        val hw = DeviceInfoProvider(context).deviceInfo.hardwareInfo
        assertTrue(hw.type in DeviceType.entries)
    }

    @Test
    fun `screenInfo reports non-negative dimensions and a valid orientation`() {
        val screen = DeviceInfoProvider(context).deviceInfo.screenInfo
        assertTrue(screen.screenWidth >= 0)
        assertTrue(screen.screenHeight >= 0)
        assertTrue(screen.dpr >= 0f)
        assertTrue(screen.screenOrientation in ScreenOrientation.entries)
    }

    @Test
    fun `powerInfo batteryState is a known BatteryState value`() {
        val power = DeviceInfoProvider(context).deviceInfo.powerInfo
        assertTrue(power.batteryState in BatteryState.entries)
    }

    @Test
    fun `appInfo produces a non-null bundleId`() {
        val app = DeviceInfoProvider(context).deviceInfo.appInfo
        // Under Robolectric the bundleId defaults to the test package — just
        // assert the provider doesn't throw and returns a non-null string.
        assertNotNull(app.appBundleId)
    }

    @Test
    fun `two consecutive deviceInfo reads return equivalent snapshots`() {
        val provider = DeviceInfoProvider(context)
        val a = provider.deviceInfo
        val b = provider.deviceInfo
        // Static info comes from a lazy, so these fields should be identical.
        assertEquals(a.osInfo.osName, b.osInfo.osName)
        assertEquals(a.hardwareInfo.brand, b.hardwareInfo.brand)
        assertEquals(a.sdkInfo.sdkVersion, b.sdkInfo.sdkVersion)
        assertEquals(a.appInfo.appBundleId, b.appInfo.appBundleId)
    }
}
