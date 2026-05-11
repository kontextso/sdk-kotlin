package so.kontext.ads

/**
 * Compile-time SDK identity (name + platform + version) sent in `/init`,
 * `/preload`, error reports, and iframe URLs. Mirrors iOS `SDKInfo`
 * (`KontextSwiftSDK/SDKInfo.swift`) so the server can attribute analytics
 * + bug reports per SDK.
 *
 * Wire encoding goes through `SdkDto` at call sites — this object stays a
 * plain constants holder rather than `@Serializable`, matching Swift's
 * split between `SDKInfo` and `SDKDTO`.
 *
 * `VERSION` is sourced from `BuildConfig.SDK_VERSION`, which is set by
 * `build.gradle.kts` from the `sdkVersion` property (default value lives
 * in `gradle/libs.versions.toml`; CI patch releases override via
 * `-PsdkVersion=X.Y.Z`). Single source of truth — the published Maven
 * artifact version and the version reported to the ad server can't drift.
 */
internal object SDKInfo {
    const val NAME: String = "sdk-kotlin"
    val VERSION: String = BuildConfig.SDK_VERSION
    const val PLATFORM: String = "android"
}
