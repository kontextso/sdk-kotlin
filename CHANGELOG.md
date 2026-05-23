# Changelog

## 4.0.2

Lower `minSdk` to 24 and fix inline (display) OMID sessions.

* `minSdk` 26 → 24 (Android 8.0 Oreo → Android 7.0 Nougat). Nothing in the SDK requires API 26 — no `@RequiresApi` gates, no `java.time` usage — so the floor is lowered to widen device support. No public API changes.
* Bump KontextKit dependency to `0.0.7`, which also lowers its `minSdk` to 24 so the manifest merge stays consistent for consumers.
* Fix inline OMID sessions for display (banner) ads. `restartOmSessionIfRetired` fired on a fresh ad's first mount — i.e. *before* `ad-done` — so the OMID session started before the iframe and its verification script had loaded. The in-iframe script never dispatched `sessionStart`/`impression`, and teardown emitted a bare `sessionFinish` with no preceding start. The inline session now starts only from `ad-done` (iframe ready); a genuine scroll-off/scroll-back still restarts a previously-started session. Interstitial/video OMID was unaffected.

## 4.0.1

IAB OMID AAR now flows in transitively from KontextKit — no more local vendoring required on customer builds.

* Bump KontextKit dependency to `0.0.6`. KontextKit now republishes the IAB Open Measurement SDK Android AAR under its own coordinate (`so.kontext.iab:omsdk-android:1.6.4`), so customers consuming `so.kontext:ads:4.0.1` from Maven Central get OMID classes transitively. Drops the v4.0.0 requirement that consumers vendor the IAB AAR locally and declare `iab:omsdk-android:1.6.4` themselves.
* Remove the `local-maven/iab/omsdk-android/1.6.4/` vendored AAR + the corresponding `maven { url = uri("${rootDir}/local-maven") }` entry in `settings.gradle.kts`. The `implementation("iab:omsdk-android:1.6.4")` line in `ads/build.gradle.kts` is removed for the same reason; KontextKit 0.0.6 carries the dep in its POM.
* Re-enable the `includeBuild ../kontextkit-android` substitution in `settings.gradle.kts` (gated on the directory existing). Lets contributors iterate across both repos without round-tripping through Maven Central. Customers never see this — when the directory isn't present, Gradle resolves the published artifact normally.

## 4.0.0
### Breaking
Public API completely rewritten. The v3 entry points (`AdsBuilder`, `AdsProvider`) are replaced by `KontextAds.createSession(...)` which returns a `Session` exposing `addMessage(...)` and `createAd(...)`. The `:example` demo module was removed; see the documentation site for the new integration walkthrough.

* New `Session` / `Ad` API around the `createSession → addMessage → createAd` lifecycle.
* New `/init` endpoint for per-user feature gating (preload-timeout override; server-controlled `reportErrors` / `reportDebug` toggles).
* Compose-first UI (`InlineAd`) with a View interop wrapper (`InlineAdView`).
* Shared Android primitives extracted to [`so.kontext.kit:kontext-kit-android`](https://github.com/kontextso/kontextkit-android) — device info, IDFA, TCF consent, in-app browser, and the IAB OMID lifecycle.
* Replaces the v3 Ktor HTTP stack with `java.net.HttpURLConnection` for a thinner dependency footprint.
* Server-controlled `/error` and `/debug` reporting gates (debug off by default; local debug callback still fires).
* `:example` rewritten for the new `KontextAds.createSession` API (single `MainActivity` Compose chat UI; consumes the local `:ads` module via `implementation(project(":ads"))`).
* Toolchain bumped to Kotlin 2.1.0 / AGP 8.7.3 / Gradle 8.9 / detekt 1.23.8 / spotless 7.2.1. Compose compiler now wired via the `org.jetbrains.kotlin.plugin.compose` plugin (the legacy `composeOptions { kotlinCompilerExtensionVersion }` block is unsupported on Kotlin 2.x). Aligns the SDK with publisher apps already on Kotlin 2.x.
* KontextKit dependency bumped to `0.0.5`. Switches OMID HTML display impression owner to `Owner.JAVASCRIPT` (matches v3 sdk-kotlin and the IAB OMID Android v1.6.4 reference demo). Closes the IAB-flagged display-ad `adView.geometry: 1×1` issue when combined with the ad-server side fix in [kontextso/ads#2811](https://github.com/kontextso/ads/pull/2811).

## 2.0.1
* Bump Ktor from 2.3.7 to 3.2.3.
* Bump Kotlin from 1.9.0 to 2.1.0 (required by Ktor 3.x).

## 2.0.0
### Breaking
`AdResult` sealed interface updated: `Success` renamed to `Filled`, and `NoFill` added as a distinct state separate from `Error`. Update exhaustive `when` expressions to handle the new `.NoFill` case.

* Added Advertising ID (GAID) collection support.
* Added Transparency & Consent Framework (TCF/GDPR) support.
* Added `skipCode` parameter to `AdResult.NoFill`.
* Added `userEmail` parameter to `AdsBuilder`.
* Added request headers to preload API calls.
* Updated `isDisabled` — preload request still fires when disabled for session tracking, but no ad events are emitted.
* UserAgent, timestamp values and locale format (BCP-47) are now consistent across all SDKs.
* Cap outgoing messages to last 30 per preload request.
* Fix parsing of component prop values.
* Fix stale device info by recomputing dynamic fields on each preload.
* Fix deprecated `PreferenceManager` import for TCF consent reading.
* Fix thread visibility issues on shared mutable state in `AdsProviderImpl`.
* Fix `WebView.destroy()` called off main thread in `InlineAdWebViewPool.clearAll()`.

## 1.1.5
* Disable back button while a modal ad is displayed.

## 1.1.4
* Add `area` and `format` fields to `AdEvent.Clicked`.
* Add `format` field to `AdEvent.Viewed`.
* Fix WebView reloading issues in `RecyclerView`s and `LazyColumn`s.

## 1.1.3
* Report keyboard height changes to the server.
* Propagate callback events from modal ads.

## 1.1.2
* Improve ad fetching mechanism and avoid sending unnecessary callbacks.

## 1.1.1
* Trigger preload based on new user messages only.
* Add unit tests.
* Automate release process.

## 1.1.0
* `ads` flow now returns `AdResult` sealed interface with `Success` and `Error` states.
* `InlineAd` and `InlineAdView` now expose an `onEvent` callback returning `AdEvent` events.
* Add error propagation with `AdUnavailable` and `NetworkError` error types.

## 1.0.3
* Add new parameters to the preload API request.

## 1.0.2
* Add regulatory parameters (`gdpr`, `coppa`, `gppSid`) to `AdsBuilder`.
* Add support for interstitial ads.
* Add support for Kotlin 1.9.0.

## 1.0.1
* Add `InlineAdView` for View-based UI system.
* Update documentation for `Character` and other SDK initialisation fields.
