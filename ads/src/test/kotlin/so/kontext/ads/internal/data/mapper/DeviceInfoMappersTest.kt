package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.utils.deviceinfo.AppInfo
import so.kontext.ads.internal.utils.deviceinfo.AudioInfo
import so.kontext.ads.internal.utils.deviceinfo.AudioOutputType
import so.kontext.ads.internal.utils.deviceinfo.BatteryState
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.deviceinfo.DeviceType
import so.kontext.ads.internal.utils.deviceinfo.HardwareInfo
import so.kontext.ads.internal.utils.deviceinfo.NetworkDetailType
import so.kontext.ads.internal.utils.deviceinfo.NetworkInfo
import so.kontext.ads.internal.utils.deviceinfo.NetworkType
import so.kontext.ads.internal.utils.deviceinfo.OsInfo
import so.kontext.ads.internal.utils.deviceinfo.PowerInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenOrientation
import so.kontext.ads.internal.utils.deviceinfo.SdkInfo

class DeviceInfoMappersTest {

    @Test
    fun `OsInfo toDto copies every field`() {
        val dto = OsInfo(
            osName = "Android",
            osVersion = "14",
            locale = "cs-CZ",
            timezone = "Europe/Prague",
        ).toDto()
        assertEquals("Android", dto.name)
        assertEquals("14", dto.version)
        assertEquals("cs-CZ", dto.locale)
        assertEquals("Europe/Prague", dto.timezone)
    }

    @Test
    fun `HardwareInfo toDto copies every field and maps DeviceType`() {
        val dto = HardwareInfo(
            brand = "Samsung",
            model = "Galaxy S24",
            type = DeviceType.Handset,
            bootTime = 1_700_000_000_000,
            sdCardAvailable = true,
        ).toDto()
        assertEquals("Samsung", dto.brand)
        assertEquals("Galaxy S24", dto.model)
        assertEquals("handset", dto.type)
        assertEquals(1_700_000_000_000, dto.bootTime)
        assertEquals(true, dto.sdCardAvailable)
    }

    @Test
    fun `ScreenInfo toDto copies every field and maps ScreenOrientation`() {
        val dto = ScreenInfo(
            screenWidth = 1080,
            screenHeight = 2400,
            dpr = 3.0f,
            screenOrientation = ScreenOrientation.Portrait,
            isDarkMode = true,
        ).toDto()
        assertEquals(1080, dto.width)
        assertEquals(2400, dto.height)
        assertEquals(3.0f, dto.dpr)
        assertEquals("portrait", dto.orientation)
        assertEquals(true, dto.darkMode)
    }

    @Test
    fun `PowerInfo toDto copies every field and maps BatteryState`() {
        val dto = PowerInfo(
            batteryLevel = 83,
            batteryState = BatteryState.Charging,
            isLowPowerMode = false,
        ).toDto()
        assertEquals(83, dto.batteryLevel)
        assertEquals("charging", dto.batteryState)
        assertEquals(false, dto.lowPowerMode)
    }

    @Test
    fun `AudioInfo toDto copies every field and maps every AudioOutputType`() {
        val dto = AudioInfo(
            volume = 65,
            isMuted = false,
            isAudioOutputPluggedIn = true,
            audioOutputTypes = listOf(AudioOutputType.Wired, AudioOutputType.Bluetooth),
        ).toDto()
        assertEquals(65, dto.volume)
        assertEquals(false, dto.muted)
        assertEquals(true, dto.outputPluggedIn)
        assertEquals(listOf("wired", "bluetooth"), dto.outputType)
    }

    @Test
    fun `NetworkInfo toDto copies every field and maps NetworkType + NetworkDetailType`() {
        val dto = NetworkInfo(
            userAgent = "UA",
            networkType = NetworkType.Cellular,
            networkDetail = NetworkDetailType.FiveG,
            carrier = "T-Mobile",
        ).toDto()
        assertEquals("UA", dto.userAgent)
        assertEquals("cellular", dto.type)
        assertEquals("5g", dto.detail)
        assertEquals("T-Mobile", dto.carrier)
    }

    @Test
    fun `DeviceType toDto maps every value`() {
        assertEquals("handset", DeviceType.Handset.toDto())
        assertEquals("tablet", DeviceType.Tablet.toDto())
        assertEquals("tv", DeviceType.Tv.toDto())
    }

    @Test
    fun `ScreenOrientation toDto maps both values`() {
        assertEquals("portrait", ScreenOrientation.Portrait.toDto())
        assertEquals("landscape", ScreenOrientation.Landscape.toDto())
    }

    @Test
    fun `BatteryState toDto maps every value`() {
        assertEquals("charging", BatteryState.Charging.toDto())
        assertEquals("full", BatteryState.Full.toDto())
        assertEquals("unplugged", BatteryState.Unplugged.toDto())
        assertEquals("unknown", BatteryState.Unknown.toDto())
    }

    @Test
    fun `AudioOutputType toDto maps every value`() {
        assertEquals("wired", AudioOutputType.Wired.toDto())
        assertEquals("bluetooth", AudioOutputType.Bluetooth.toDto())
        assertEquals("hdmi", AudioOutputType.Hdmi.toDto())
        assertEquals("usb", AudioOutputType.Usb.toDto())
        assertEquals("other", AudioOutputType.Other.toDto())
    }

    @Test
    fun `NetworkType toDto maps every value`() {
        assertEquals("wifi", NetworkType.Wifi.toDto())
        assertEquals("cellular", NetworkType.Cellular.toDto())
        assertEquals("ethernet", NetworkType.Ethernet.toDto())
        assertEquals("other", NetworkType.Other.toDto())
    }

    @Test
    fun `NetworkDetailType toDto covers every value including aliased casing`() {
        assertEquals("gprs", NetworkDetailType.Gprs.toDto())
        assertEquals("edge", NetworkDetailType.Edge.toDto())
        assertEquals("2g", NetworkDetailType.TwoG.toDto())
        assertEquals("3g", NetworkDetailType.Three3.toDto())
        assertEquals("hspa", NetworkDetailType.Hspa.toDto())
        assertEquals("4g", NetworkDetailType.FourG.toDto())
        assertEquals("5g", NetworkDetailType.FiveG.toDto())
        assertEquals("cellular", NetworkDetailType.Cellular.toDto())
    }

    @Test
    fun `DeviceInfo toDto wires every sub-info into the DTO tree`() {
        val device = DeviceInfo(
            osInfo = OsInfo("ios", "17", "en-US", "UTC"),
            hardwareInfo = HardwareInfo("Apple", "iPhone15", DeviceType.Handset, 1, false),
            screenInfo = ScreenInfo(1, 2, 3.0f, ScreenOrientation.Landscape, false),
            powerInfo = PowerInfo(50, BatteryState.Unplugged, false),
            audioInfo = AudioInfo(10, true, false, emptyList()),
            networkInfo = NetworkInfo(null, NetworkType.Wifi, null, null),
            appInfo = AppInfo("b", "1.0", null, 0, 0, 0),
            sdkInfo = SdkInfo("sdk-kotlin", "1.0.0", "android"),
        )
        val dto = device.toDto()
        assertEquals("ios", dto.os.name)
        assertEquals("Apple", dto.hardware.brand)
        assertEquals(1, dto.screen.width)
        assertEquals(50, dto.power.batteryLevel)
        assertEquals(true, dto.audio.muted)
        assertEquals("wifi", dto.network.type)
    }

    @Test
    fun `AppInfo toDto copies every field`() {
        val dto = AppInfo(
            appBundleId = "com.example.app",
            appVersion = "1.2.3",
            appStoreUrl = "https://play.google.com/...",
            firstInstallTime = 1_700_000_000,
            lastUpdateTime = 1_701_000_000,
            startTime = 1_702_000_000,
        ).toDto()
        assertEquals("com.example.app", dto.bundleId)
        assertEquals("1.2.3", dto.version)
        assertEquals("https://play.google.com/...", dto.storeUrl)
        assertEquals(1_700_000_000, dto.firstInstallTime)
        assertEquals(1_701_000_000, dto.lastUpdateTime)
        assertEquals(1_702_000_000, dto.startTime)
    }

    @Test
    fun `SdkInfo toDto copies every field`() {
        val dto = SdkInfo("sdk-kotlin", "2.1.0", "android").toDto()
        assertEquals("sdk-kotlin", dto.name)
        assertEquals("2.1.0", dto.version)
        assertEquals("android", dto.platform)
    }
}
