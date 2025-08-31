package so.kontext.ads.internal.utils.deviceinfo

internal data class DeviceInfo(
    // OS
    val osName: String,
    val osVersion: String,
    val locale: String,
    val timezone: String,

    // Hardware
    val brand: String,
    val model: String,
    val deviceType: String,
    val bootTime: Long,
    val sdCardAvailable: Boolean,

    // Screen
    val screenWidth: Int,
    val screenHeight: Int,
    val dpr: Float,
    val screenOrientation: String,
    val isDarkMode: Boolean,

    // Power
    val batteryLevel: Int?,
    val batteryState: String,
    val isLowPowerMode: Boolean,

    // Audio
    val volume: Int?,
    val isMuted: Boolean,
    val isAudioOutputPluggedIn: Boolean,
    val audioOutputTypes: List<String>,

    // Network
    val userAgent: String?,
    val networkType: String?,
    val networkDetail: String?,
    val carrier: String?,

    // App
    val appBundleId: String,
    val appVersion: String,
    val appStoreUrl: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val startTime: Long,

    // Sdk
    val sdkName: String,
    val sdkVersion: String,
    val sdkPlatform: String
)
