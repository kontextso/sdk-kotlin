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
import android.telephony.TelephonyManager
import android.webkit.WebSettings
import so.kontext.ads.BuildConfig
import so.kontext.ads.internal.AdsProperties

internal class DeviceInfoProvider(
    private val context: Context,
) {
    val deviceInfo: DeviceInfo by lazy {
        DeviceInfo(
            // OS
            osName = getOs(),
            osVersion = getSystemVersion(),
            locale = getLocale(),
            timezone = getTimezone(),

            // Hardware
            brand = getBrand(),
            model = getModel(),
            deviceType = getDeviceType(),
            bootTime = getBootTime(),
            sdCardAvailable = isSdCardAvailable(),

            // Screen
            screenWidth = getScreenWidth(),
            screenHeight = getScreenHeight(),
            dpr = getDpr(),
            screenOrientation = getScreenOrientation(),
            isDarkMode = isDarkMode(),

            // Power
            batteryLevel = getBatteryLevel(),
            batteryState = getBatteryState(),
            isLowPowerMode = isLowPowerMode(),

            // Audio
            volume = getVolume(),
            isMuted = isMuted(),
            isAudioOutputPluggedIn = isAudioOutputPluggedIn(),
            audioOutputTypes = getAudioOutputTypes(),

            // Network
            userAgent = getUserAgent(),
            networkType = getNetworkType(),
            networkDetail = getNetworkDetail(),
            carrier = getCarrier(),

            // App
            appBundleId = getAppBundleId(),
            appVersion = getAppVersion(),
            appStoreUrl = getPlayStoreUrl(),
            firstInstallTime = getFirstInstallTime(),
            lastUpdateTime = getLastUpdateTime(),
            startTime = getProcessStartTime(),

            // Sdk
            sdkName = "sdk-kotlin",
            sdkVersion = BuildConfig.SDK_VERSION,
            sdkPlatform = "android"
        )
    }

    private val batteryStatus: Intent? by lazy {
        IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
    }

    private val packageInfo: PackageInfo? by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private val audioOutputDevices: Array<AudioDeviceInfo> by lazy {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }

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

    private fun getDeviceType(): String {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            "tv"
        } else {
            val configuration = context.resources.configuration
            if (configuration.smallestScreenWidthDp >= 600) {
                "tablet"
            } else {
                "handset"
            }
        }
    }

    private fun getAppBundleId(): String = context.packageName

    private fun getAppVersion(): String {
        val packageName = context.packageName
        val packageInfo = context.packageManager?.getPackageInfo(packageName, 0)
        return packageInfo?.versionName.orEmpty()
    }

    private fun getPlayStoreUrl(): String? {
        return "${AdsProperties.GooglePlayStoreUrl}${getAppBundleId()}"
    }

    fun isDarkMode(): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getLocale(): String {
        val locale = context.resources.configuration.locales[0]
        return "${locale.language}-${locale.country}"
    }

    private fun getScreenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun getScreenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun getBootTime(): Long {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }

    private fun getProcessStartTime(): Long {
        return Process.getStartElapsedRealtime()
    }

    private fun getFirstInstallTime(): Long {
        return packageInfo?.firstInstallTime ?: 0L
    }

    private fun getLastUpdateTime(): Long {
        return packageInfo?.lastUpdateTime ?: 0L
    }

    private fun getDpr(): Float {
        return context.resources.displayMetrics.density
    }

    private fun getBatteryLevel(): Int? {
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level == -1 || scale == -1 || scale == 0) {
            null
        } else {
            (level * 100 / scale.toFloat()).toInt()
        }
    }

    /**
     * Returns the current charging state of the battery.
     */
    private fun getBatteryState(): String {
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?: return "unknown"

        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "unplugged"
            else -> "unknown"
        }
    }

    /**
     * Checks if the device is currently in power save mode.
     * This API was added in API level 21.
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
     * Checks if the media stream is currently muted. Requires API 23+.
     */
    private fun isMuted(): Boolean {
        return audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    /**
     * Returns a list of connected audio output types, ignoring built-in speakers.
     */
    private fun getAudioOutputTypes(): List<String> {
        val outputTypes = mutableSetOf<String>()
        audioOutputDevices.forEach { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> outputTypes.add("wired")

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> outputTypes.add("bluetooth")

                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_HDMI_EARC -> outputTypes.add("hdmi")

                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> outputTypes.add("usb")

                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_TELEPHONY -> { /* Ignore built-in devices */ }

                else -> {
                    // If it's a sink (output) and not a known built-in type, classify as "other"
                    if (device.isSink) {
                        outputTypes.add("other")
                    }
                }
            }
        }
        return outputTypes.toList()
    }

    /**
     * Checks if any external audio output device is connected.
     */
    private fun isAudioOutputPluggedIn(): Boolean {
        // An external device is plugged in if our list of types is not empty.
        return getAudioOutputTypes().isNotEmpty()
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
    private fun getNetworkType(): String? {
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager?.activeNetwork) ?: return null
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    /**
     * Returns the specific technology of the active cellular network.
     * This requires the host app to have `READ_PHONE_STATE` or `READ_BASIC_PHONE_STATE` permission.
     * @return "2g", "3g", "4g", "5g", etc. Returns null if not a cellular network or if permission is denied.
     */
    @SuppressLint("MissingPermission")
    private fun getNetworkDetail(): String? {
        if (getNetworkType() != "cellular") return null
        return try {
            when (telephonyManager?.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "gprs"
                TelephonyManager.NETWORK_TYPE_EDGE -> "edge"
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN -> "2g"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "3g"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "hspa"
                TelephonyManager.NETWORK_TYPE_LTE -> "4g"
                TelephonyManager.NETWORK_TYPE_NR -> "5g"
                else -> "cellular"
            }
        } catch (e: SecurityException) {
            // Host app lacks permissions, cannot get detailed type.
            null
        }
    }


    /**
     * Returns the network operator name for the current cellular connection.
     * Returns null if not a cellular network.
     */
    private fun getCarrier(): String? {
        if (getNetworkType() != "cellular") return null
        return telephonyManager?.networkOperatorName
    }

    /**
     * Returns the current screen orientation.
     */
    private fun getScreenOrientation(): String {
        val orientation = context.resources.configuration.orientation
        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }
    }

    /**
     * Returns the IANA time zone ID of the device (e.g., "Europe/Prague").
     */
    private fun getTimezone(): String {
        return TimeZone.getDefault().id
    }

    /**
     * Checks if an external SD card is mounted and available.
     */
    private fun isSdCardAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
}
