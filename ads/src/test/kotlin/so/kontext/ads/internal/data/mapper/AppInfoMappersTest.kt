package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.utils.deviceinfo.AppInfo

class AppInfoMappersTest {

    @Test
    fun `toDto copies every field`() {
        val dto = AppInfo(
            appBundleId = "com.example.app",
            appVersion = "1.0.0",
            appStoreUrl = "https://play.google.com",
            firstInstallTime = 1_700_000_000,
            lastUpdateTime = 1_701_000_000,
            startTime = 1_702_000_000,
        ).toDto()

        assertEquals("com.example.app", dto.bundleId)
        assertEquals("1.0.0", dto.version)
        assertEquals("https://play.google.com", dto.storeUrl)
        assertEquals(1_700_000_000, dto.firstInstallTime)
        assertEquals(1_701_000_000, dto.lastUpdateTime)
        assertEquals(1_702_000_000, dto.startTime)
    }

    @Test
    fun `toDto preserves null storeUrl`() {
        val dto = AppInfo(
            appBundleId = "b",
            appVersion = "v",
            appStoreUrl = null,
            firstInstallTime = 0,
            lastUpdateTime = 0,
            startTime = 0,
        ).toDto()
        assertNull(dto.storeUrl)
    }
}
