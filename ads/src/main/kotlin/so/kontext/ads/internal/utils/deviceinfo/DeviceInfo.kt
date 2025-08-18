package so.kontext.ads.internal.utils.deviceinfo

internal data class DeviceInfo(
    val os: String,
    val systemVersion: String,
    val model: String,
    val brand: String,
    val deviceType: String,
    val appBundleId: String,
    val appVersion: String,
    val soundOn: Boolean,
    val appStoreUrl: String?,
    val locale: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val isDarkMode: Boolean,
)
