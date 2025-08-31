package so.kontext.ads.internal.utils.deviceinfo

internal enum class DeviceType {
    Handset, Tablet, Tv;

    internal companion object
}

internal enum class ScreenOrientation {
    Portrait, Landscape;

    internal companion object
}

internal enum class BatteryState {
    Charging, Full, Unplugged, Unknown;

    internal companion object
}

internal enum class AudioOutputType {
    Wired, Bluetooth, Hdmi, Usb, Other;

    internal companion object
}

internal enum class NetworkType {
    Wifi, Cellular, Ethernet, Other;

    internal companion object
}

internal enum class NetworkDetailType {
    Gprs, Edge, TwoG, Three3, Hspa, FourG, FiveG, Cellular;

    internal companion object
}

internal data class OsInfo(
    val osName: String,
    val osVersion: String,
    val locale: String,
    val timezone: String,
)

internal data class HardwareInfo(
    val brand: String,
    val model: String,
    val deviceType: DeviceType,
    val bootTime: Long,
    val sdCardAvailable: Boolean,
)

internal data class ScreenInfo(
    val screenWidth: Int,
    val screenHeight: Int,
    val dpr: Float,
    val screenOrientation: ScreenOrientation,
    val isDarkMode: Boolean,
)

internal data class PowerInfo(
    val batteryLevel: Int?,
    val batteryState: BatteryState,
    val isLowPowerMode: Boolean,
)

internal data class AudioInfo(
    val volume: Int?,
    val isMuted: Boolean,
    val isAudioOutputPluggedIn: Boolean,
    val audioOutputTypes: List<AudioOutputType>,
)

internal data class NetworkInfo(
    val userAgent: String?,
    val networkType: NetworkType?,
    val networkDetail: NetworkDetailType?,
    val carrier: String?,
)

internal data class AppInfo(
    val appBundleId: String,
    val appVersion: String,
    val appStoreUrl: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val startTime: Long,
)

internal data class SdkInfo(
    val sdkName: String,
    val sdkVersion: String,
    val sdkPlatform: String,
)

internal data class DeviceInfo(
    val osInfo: OsInfo,
    val hardwareInfo: HardwareInfo,
    val screenInfo: ScreenInfo,
    val powerInfo: PowerInfo,
    val audioInfo: AudioInfo,
    val networkInfo: NetworkInfo,
    val appInfo: AppInfo,
    val sdkInfo: SdkInfo,
)
