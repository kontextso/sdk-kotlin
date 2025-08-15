package com.kontext.ads.internal.utils.deviceinfo

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import com.kontext.ads.internal.AdsProperties

internal class DeviceInfoProvider(
    private val context: Context,
) {
    val deviceInfo: DeviceInfo by lazy {
        DeviceInfo(
            os = getOs(),
            systemVersion = getSystemVersion(),
            model = getModel(),
            brand = getBrand(),
            deviceType = getDeviceType(),
            appBundleId = getAppBundleId(),
            appVersion = getAppVersion(),
            soundOn = getSoundOn(),
            appStoreUrl = getPlayStoreUrl(),
            isLightMode = isLightMode(),
        )
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
            // A simple check for tablets based on screen size
            val configuration = context.resources.configuration
            if (configuration.smallestScreenWidthDp >= 600) {
                "tablet"
            } else {
                "mobile"
            }
        }
    }

    private fun getAppBundleId(): String = context.packageName

    private fun getAppVersion(): String {
        val packageName = context.packageName
        val packageInfo = context.packageManager?.getPackageInfo(packageName, 0)
        return packageInfo?.versionName.orEmpty()
    }

    private fun getSoundOn(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private fun getPlayStoreUrl(): String? {
        return "$AdsProperties.GooglePlayStoreUrl${getAppBundleId()}"
    }

    private fun isLightMode(): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_NO
    }
}
