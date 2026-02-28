package so.kontext.ads.internal.utils.deviceinfo

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.icu.util.TimeZone
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import android.telephony.TelephonyManager
import android.webkit.WebSettings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import so.kontext.ads.BuildConfig
import so.kontext.ads.internal.AdsProperties

// Holds truly static device data that never changes at runtime.
private data class StaticDeviceInfo(
    val osInfo: OsInfo,
    val hardwareInfo: HardwareInfo,
    val appInfo: AppInfo,
    val sdkInfo: SdkInfo,
    val userAgent: String?,
)

internal class DeviceInfoProvider(
    private val context: Context,
) {

    // Computed once — these values never change during the app's lifetime.
    private val staticInfo: StaticDeviceInfo by lazy {
        StaticDeviceInfo(
            osInfo = OsInfo(
                osName = getOs(),
                osVersion = getSystemVersion(),
                locale = getLocale(),
                timezone = getTimezone(),
            ),
            hardwareInfo = HardwareInfo(
                brand = getBrand(),
                model = getModel(),
                type = getDeviceType(),
                bootTime = getBootTime(),
                sdCardAvailable = isSdCardAvailable(),
            ),
            appInfo = AppInfo(
                appBundleId = getAppBundleId(),
                appVersion = getAppVersion(),
                appStoreUrl = getPlayStoreUrl(),
                firstInstallTime = getFirstInstallTime(),
                lastUpdateTime = getLastUpdateTime(),
                startTime = getProcessStartTime(),
            ),
            sdkInfo = SdkInfo(
                sdkName = "sdk-kotlin",
                sdkVersion = BuildConfig.SDK_VERSION,
                sdkPlatform = "android",
            ),
            userAgent = getUserAgent(),
        )
    }

    // Recomputed on every preload — these values change at runtime.
    val deviceInfo: DeviceInfo
        get() {
            // Capture once per call to avoid redundant system service queries.
            val networkType = getNetworkType()
            val audioOutputTypes = getAudioOutputTypes()

            return DeviceInfo(
                // Static — from cache
                osInfo = staticInfo.osInfo,
                hardwareInfo = staticInfo.hardwareInfo,
                appInfo = staticInfo.appInfo,
                sdkInfo = staticInfo.sdkInfo,

                // Dynamic — recomputed every call
                screenInfo = ScreenInfo(
                    screenWidth = getScreenWidth(),
                    screenHeight = getScreenHeight(),
                    dpr = getDpr(),
                    screenOrientation = getScreenOrientation(),
                    isDarkMode = isDarkMode(),
                ),
                powerInfo = PowerInfo(
                    batteryLevel = getBatteryLevel(),
                    batteryState = getBatteryState(),
                    isLowPowerMode = isLowPowerMode(),
                ),
                audioInfo = AudioInfo(
                    volume = getVolume(),
                    isMuted = isMuted(),
                    audioOutputTypes = audioOutputTypes,
                    isAudioOutputPluggedIn = audioOutputTypes.isNotEmpty(),
                ),
                networkInfo = NetworkInfo(
                    userAgent = staticInfo.userAgent,
                    networkType = networkType,
                    networkDetail = getNetworkDetail(networkType),
                    carrier = getCarrier(networkType),
                ),
            )
        }

    // Fresh intent on every access — battery state changes at runtime.
    private val batteryStatus: Intent?
        get() = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

    // Safe lazy — package info is static for the app's lifetime.
    private val packageInfo: PackageInfo? by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    // Fresh array on every access — audio devices change at runtime.
    private val audioOutputDevices: Array<AudioDeviceInfo>
        get() = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    // System service handles are safe to cache — the data they return is always live.
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private fun getOs() = "android"

    private fun getSystemVersion(): String = Build.VERSION.RELEASE

    private fun getModel(): String = Build.MODEL

    private fun getBrand(): String = Build.BRAND

    private fun getDeviceType(): DeviceType {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            DeviceType.Tv
        } else {
            val configuration = context.resources.configuration
            if (configuration.smallestScreenWidthDp >= 600) {
                DeviceType.Tablet
            } else {
                DeviceType.Handset
            }
        }
    }

    /**
     * Returns the package name of the host application.
     */
    private fun getAppBundleId(): String = context.packageName

    /**
     * Returns the version name of the host application.
     * Uses the safe lazy packageInfo to avoid NameNotFoundException.
     */
    private fun getAppVersion(): String = packageInfo?.versionName.orEmpty()

    private fun getPlayStoreUrl(): String =
        "${AdsProperties.GooglePlayStoreUrl}${getAppBundleId()}"

    fun isDarkMode(): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getLocale(): String {
        val locale = context.resources.configuration.locales[0]
        return locale.toLanguageTag()
    }

    private fun getScreenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun getScreenHeight(): Int = context.resources.displayMetrics.heightPixels

    /**
     * Calculates the absolute boot time of the device as a Unix timestamp.
     */
    private fun getBootTime(): Long =
        System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /**
     * Returns the absolute Unix timestamp of when this process was started.
     */
    private fun getProcessStartTime(): Long {
        val startElapsed = Process.getStartElapsedRealtime()
        val elapsedNow = SystemClock.elapsedRealtime()
        return System.currentTimeMillis() - (elapsedNow - startElapsed)
    }

    /**
     * Returns the time at which the application was first installed, in milliseconds since epoch.
     */
    private fun getFirstInstallTime(): Long = packageInfo?.firstInstallTime ?: 0L

    /**
     * Returns the time at which the application was last updated, in milliseconds since epoch.
     */
    private fun getLastUpdateTime(): Long = packageInfo?.lastUpdateTime ?: 0L

    /**
     * Returns the logical density of the display, also known as the Device Pixel Ratio (DPR).
     */
    private fun getDpr(): Float = context.resources.displayMetrics.density

    /**
     * Captures batteryStatus once to avoid calling registerReceiver twice.
     */
    private fun getBatteryLevel(): Int? {
        val status = batteryStatus ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level == -1 || scale == -1 || scale == 0) {
            null
        } else {
            (level * 100 / scale.toFloat()).toInt()
        }
    }

    /**
     * Returns the current charging state of the battery.
     * Captures batteryStatus once to avoid calling registerReceiver twice.
     */
    private fun getBatteryState(): BatteryState {
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?: return BatteryState.Unknown

        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.Charging
            BatteryManager.BATTERY_STATUS_FULL -> BatteryState.Full
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            -> BatteryState.Unplugged
            else -> BatteryState.Unknown
        }
    }

    /**
     * Checks if the device is currently in power save mode.
     */
    private fun isLowPowerMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode ?: false
    }

    /**
     * Returns the current media stream volume as a percentage from 0 to 100.
     */
    private fun getVolume(): Int? {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume == 0) return null
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (currentVolume * 100) / maxVolume
    }

    /**
     * Checks if the media stream is currently muted.
     */
    private fun isMuted(): Boolean = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)

    /**
     * Returns a list of connected audio output types, ignoring built-in speakers.
     * Caller is responsible for capturing the result to avoid calling audioOutputDevices twice.
     */
    private fun getAudioOutputTypes(): List<AudioOutputType> {
        return buildList {
            audioOutputDevices.forEach { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    -> add(AudioOutputType.Wired)

                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    -> add(AudioOutputType.Bluetooth)

                    AudioDeviceInfo.TYPE_HDMI,
                    AudioDeviceInfo.TYPE_HDMI_ARC,
                    AudioDeviceInfo.TYPE_HDMI_EARC,
                    -> add(AudioOutputType.Hdmi)

                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    -> add(AudioOutputType.Usb)

                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_TELEPHONY,
                    -> { /* Ignore built-in devices */ }

                    else -> {
                        if (device.isSink) add(AudioOutputType.Other)
                    }
                }
            }
        }
    }

    /**
     * Returns the default user agent string used by the WebView.
     * Returns null if the user agent cannot be determined.
     */
    private fun getUserAgent(): String? = try {
        WebSettings.getDefaultUserAgent(context)
    } catch (_: Exception) {
        null
    }

    /**
     * Returns the active network transport type.
     * @return "wifi", "cellular", "ethernet", or "other". Returns null if no network is active.
     */
    @SuppressLint("MissingPermission")
    private fun getNetworkType(): NetworkType? {
        val capabilities = connectivityManager
            ?.getNetworkCapabilities(connectivityManager?.activeNetwork) ?: return null
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.Ethernet
            else -> NetworkType.Other
        }
    }

    /**
     * Returns the specific technology of the active cellular network.
     * Accepts networkType to avoid a redundant getNetworkType() call.
     * Requires the host app to have READ_PHONE_STATE or READ_BASIC_PHONE_STATE permission.
     */
    @SuppressLint("MissingPermission")
    private fun getNetworkDetail(networkType: NetworkType?): NetworkDetailType? {
        if (networkType != NetworkType.Cellular) return null
        return try {
            when (telephonyManager?.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> NetworkDetailType.Gprs
                TelephonyManager.NETWORK_TYPE_EDGE -> NetworkDetailType.Edge
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                -> NetworkDetailType.TwoG
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                -> NetworkDetailType.Three3
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                -> NetworkDetailType.Hspa
                TelephonyManager.NETWORK_TYPE_LTE -> NetworkDetailType.FourG
                TelephonyManager.NETWORK_TYPE_NR -> NetworkDetailType.FiveG
                else -> NetworkDetailType.Cellular
            }
        } catch (_: SecurityException) {
            // Host app lacks permissions, cannot get detailed type.
            null
        }
    }

    /**
     * Returns the network operator name for the current cellular connection.
     * Accepts networkType to avoid a redundant getNetworkType() call.
     */
    private fun getCarrier(networkType: NetworkType?): String? {
        if (networkType != NetworkType.Cellular) return null
        return telephonyManager?.networkOperatorName
    }

    /**
     * Returns the current screen orientation.
     */
    private fun getScreenOrientation(): ScreenOrientation {
        val orientation = context.resources.configuration.orientation
        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ScreenOrientation.Landscape
        } else {
            ScreenOrientation.Portrait
        }
    }

    /**
     * Returns the IANA time zone ID of the device (e.g., "Europe/Prague").
     */
    private fun getTimezone(): String = TimeZone.getDefault().id

    /**
     * Checks if an external SD card is mounted and available.
     */
    private fun isSdCardAvailable(): Boolean {
        val storageManager = ContextCompat
            .getSystemService(context, StorageManager::class.java) ?: return false
        return storageManager.storageVolumes
            .any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
    }
}
