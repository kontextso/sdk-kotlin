# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kontext Kotlin SDK — an Android SDK for integrating AI-powered contextual ads into chat/conversation apps. Published to Maven Central as `so.kontext:ads`.

## Modules

- **`:ads`** — the publishable SDK library (min SDK 24, JVM 17)
- **`:example`** — demo Android app showing v4 integration (single `MainActivity` with a chat-like UI that calls `KontextAds.createSession(...)` and renders `InlineAd`s)

## Common Commands

```bash
# Build
./gradlew :ads:build
./gradlew :ads:assembleDebug
./gradlew :example:assembleDebug

# Test
./gradlew :ads:testDebug                                          # all unit tests
./gradlew :ads:testDebug --tests "SessionTest"                   # single test class
./gradlew :ads:testDebug --tests "SessionTest.addMessageTriggersPreload"

# Code quality (both must pass in CI)
./gradlew spotlessCheck   # verify formatting
./gradlew spotlessApply   # auto-fix formatting
./gradlew detekt          # static analysis (config: extras/detekt.yml)

# Full CI sequence
./gradlew spotlessCheck detekt assembleDebug testDebug

# Publish (CI does this on tag; locally, populate ~/.gradle/gradle.properties first)
./gradlew :ads:publish -PsdkVersion=X.Y.Z
```

## Architecture

The v4 SDK delivers AI-powered ads into Android chat UIs. Distributed via Maven Central. Min SDK 24, compileSdk 36, JVM 17.

### Entry Point & Public API (`ads/src/main/kotlin/so/kontext/ads/`)

**`KontextAds.createSession(...)`** is the entry point. One session per chat conversation. Returns a `Session`.

**`Session`** lifecycle: `createSession → addMessage → createAd`. Key methods:
- `addMessage(message)` — append a user or assistant message. User messages trigger a debounced preload; assistant messages assign pending bids.
- `createAd(messageId)` — returns an `Ad` (or null) bound to a specific assistant message.
- `events: Flow<AdEvent>` — sealed event stream (`Filled`, `NoFill`, `Error`, `Viewed`, `Clicked`, `RenderStarted`/`RenderCompleted`, `VideoStarted`/`VideoCompleted`, `RewardGranted`, etc.).
- `destroy()` — cancels in-flight work and releases WebView resources.

**`Configuration`** holds immutable per-session settings: `publisherToken`, `userId`, `conversationId`, `enabledPlacementCodes`, `character`, `variantId`, `advertisingId`, `adServerUrl`, `regulatory`, `userEmail`, plus the debug-callback and error-callback hooks.

### Internal Structure

- **`Session.kt`** orchestrates `/init` (preload-timeout and reporting toggles), debounced preloads, bid assignment, and event fan-out.
- **`network/`** — `HttpClient` (native `HttpURLConnection`), `Preload`, `Init`, `ErrorCapture`, `DebugCapture`, plus DTOs. No Ktor.
- **`network/collectors/`** — device / app / TCF collectors that wrap KontextKit's `*InfoProvider` and `TCFDataProvider` for the preload request body.
- **`ui/`** — `InlineAd` Composable, `InlineAdView` View interop wrapper, `AdWebView` + `WebViewPool` (keyed by `messageId`), `InterstitialAdActivity` for full-screen video ads.
- **`model/`** — `Bid`, `Message`, `Role`, `Character`, `Regulatory`, `AdEvent`, etc. Pure data classes with `kotlinx-serialization`.
- **`utils/`** — JSON parsing helpers and a couple of small utilities.

### KontextKit dependency

`so.kontext.kit:kontext-kit-android` ([github.com/kontextso/kontextkit-android](https://github.com/kontextso/kontextkit-android)) provides the platform primitives that aren't worth re-deriving per-SDK:

- `deviceinfo/` — `AdvertisingIdProvider` (GAID), `AppInfoProvider`, `Audio/Battery/HW/Network/OS/ScreenInfoProvider`, `BrightnessManager`
- `privacy/` — `TCFDataProvider`
- `ui/` — `InAppBrowserManager` (Chrome Custom Tabs)
- `omsdk/` — `OmManager` / `OmSession` / `OmPartner` / `OmCreativeType` (IAB OMID lifecycle, loads the OMID AAR reflectively)

The OMID AAR itself is vendored in `local-maven/iab/omsdk-android/1.6.4/` (IAB ships OMID outside of public Maven), and `ads/build.gradle.kts` explicitly `implementation`s it so it's on the runtime classpath for `OmManager`'s reflective loader.

### Key Patterns

- **`explicitApi()` mode** is enabled on `:ads` — every public declaration must spell out its visibility.
- **`BuildConfig.SDK_VERSION`** is baked in at build time from `libs.versions.kit` (or `-PsdkVersion=X.Y.Z` at publish), so the SDK version sent to the ad server cannot drift from the published Maven version.
- Tests use **JUnit 5** + **mockk** + **kotlinx-coroutines-test**. No Robolectric (`isReturnDefaultValues = true` covers Android framework stubs).
- **Spotless** enforces ktlint 0.50.0 on `src/**/*.kt` and `*.gradle.kts`.
- **Detekt** thresholds in `extras/detekt.yml`: `LongMethod=150`, `TooManyFunctions=20`; Compose functions excluded from naming rules.

## Release Process

See `RELEASING.md` for full steps. In short:
1. Branch `release/X.Y.Z` from `main`
2. Update `CHANGELOG.md` and `gradle/libs.versions.toml` (`sdk-kotlin`)
3. PR to `main`
4. Annotated tag: `git tag -a X.Y.Z -m "Release X.Y.Z"` — triggers `publish_sdk.yml` which publishes to Maven Central via Vanniktech maven-publish.

Version strings must not have a `v` prefix (e.g., `4.0.0`, not `v4.0.0`).
