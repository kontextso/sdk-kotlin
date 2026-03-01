# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kontext Kotlin SDK — a lightweight Android SDK for integrating AI-powered contextual ads into chat/conversation apps. Published to Maven Central as `so.kontext:ads`.

## Modules

- **`:ads`** — the publishable SDK library (min SDK 26, JVM 17)
- **`:example`** — demo Android app showing SDK integration

## Common Commands

```bash
# Build
./gradlew :ads:build
./gradlew :example:assembleDebug

# Test
./gradlew :ads:testDebug                                          # all unit tests
./gradlew :ads:testDebug --tests "AdsRepositoryImplTest"         # single test class
./gradlew :ads:testDebug --tests "AdsRepositoryImplTest.myTest"  # single test method

# Code quality (both must pass in CI)
./gradlew spotlessCheck   # verify formatting
./gradlew spotlessApply   # auto-fix formatting
./gradlew detekt          # static analysis (config: extras/detekt.yml)

# Full CI sequence
./gradlew spotlessCheck detekt assembleDebug testDebug

# Publish (requires Gradle properties for credentials)
./gradlew :ads:publish -PsdkVersion=X.Y.Z \
  -PmavenCentralUsername="..." -PmavenCentralPassword="..." \
  -PsigningInMemoryKey="..." -PsigningInMemoryKeyPassword="..."
```

## Architecture

### Public API Surface (`ads/src/main/kotlin/so/kontext/ads/`)

- **`AdsProvider`** — interface with `ads: Flow<AdResult>` and `setMessages()`
- **`AdsBuilder`** — fluent builder that produces an `AdsProvider` instance
- **`domain/`** — public data types: `AdResult`, `AdConfig`, `ChatMessage`, `MessageRepresentable`, etc.
- **`ui/`** — public Compose/View components: `InlineAd`, `InlineAdView`, `AdEvent`

### Internal Implementation (`ads/src/main/kotlin/so/kontext/ads/internal/`)

**`AdsProviderImpl`** is the central orchestrator:
1. Collects messages via `messagesFlow: MutableStateFlow`
2. Debounces (300ms) → detects new user messages → calls `AdsRepository.preload()`
3. Emits `AdResult` (sealed: `Filled`, `NoFill`, `Error`) on the public `ads: Flow`

**Data layer**: `AdsApi` (Ktor HTTP client) → `AdsRepository` → domain models via mappers.
DTOs use `kotlinx-serialization`. Errors chain: `ApiError` (internal) → `KontextError` (public).

**UI layer**:
- `InlineAd` Composable manages a `InlineAdWebViewPool` — WebViews are keyed by `messageId` and reused for performance.
- JavaScript bridge (`IFrameBridge` / `IFrameCommunicator`) passes config and dimensions to the ad iframe, and `IFrameEventParser` handles events back.
- Clicks open via Custom Tabs; modal ads use `ModalAdActivity`.

**DI** (`internal/di/AdsModule.kt`): Ktor `HttpClient` is configured here with content negotiation, logging, and HTTP timeouts (request 15s, connect/socket 10s).

### Key Patterns

- **`ApiResponse<T>`** sealed class wraps all API call results.
- **`explicit API mode`** is enabled on `:ads` — all public declarations need explicit visibility modifiers.
- Tests use JUnit 5 + mockk; test options use JUnit Platform.
- Spotless enforces ktlint 0.50.0 on `src/**/*.kt` and `*.gradle.kts`.
- Detekt thresholds: `LongMethod=150`, `TooManyFunctions=20`; Compose functions are excluded from naming rules.

## Publishing

Releases are triggered by pushing a git tag matching `*.*.*` (or `*.*.*-SNAPSHOT`). The GitHub Actions workflow `publish_sdk.yml` runs `./gradlew :ads:publish` with secrets injected as Gradle properties.
