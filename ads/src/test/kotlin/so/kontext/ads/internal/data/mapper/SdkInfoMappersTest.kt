package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.utils.deviceinfo.SdkInfo

class SdkInfoMappersTest {

    @Test
    fun `toDto copies every field verbatim`() {
        val dto = SdkInfo(
            sdkName = "sdk-kotlin",
            sdkVersion = "2.1.0",
            sdkPlatform = "android",
        ).toDto()
        assertEquals("sdk-kotlin", dto.name)
        assertEquals("2.1.0", dto.version)
        assertEquals("android", dto.platform)
    }
}
