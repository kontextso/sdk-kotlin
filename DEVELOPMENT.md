# Development

This guide is for contributors working on the Kontext Kotlin SDK locally. For an end-user integration guide, see the [docs site](https://docs.kontext.so/sdk/android).

## Prerequisites

- **JDK 17** (Temurin recommended)
  - macOS: `brew install --cask temurin@17`
  - Verify: `java -version`
- **Android SDK** with platform 36 installed
  - Easiest via [Android Studio](https://developer.android.com/studio)
  - Or `sdkmanager "platforms;android-36"` if you have command-line tools
- **An Android emulator or physical device** running API 26+ (Android 8.0+) to run the example app

Gradle 8.7 is downloaded automatically by the wrapper on first build — no manual install.

## Quick start: running the example app

The `:example` module is a single-Activity Compose chat UI that exercises the public v4 API end-to-end.

### From Android Studio

1. Open the repo root as a project — wait for Gradle sync to finish
2. Pick the `example` run configuration in the toolbar
3. Run on an emulator or connected device

### From the command line

```bash
# Build the APK
./gradlew :example:assembleDebug

# Install on a running device/emulator
./gradlew :example:installDebug
```

Launch **Kontext Ads Example** from the device's app drawer. Type into the chat; the assistant replies with a canned response, and an `InlineAd` renders below each assistant message when the preload returns a bid.

### Setting your publisher token

The example reads `publisherToken` from `local.properties` at build time. That file is **gitignored** — your token never gets committed.

1. Copy the template to the real file (or append to an existing `local.properties` that Android Studio created for `sdk.dir`):

   ```bash
   cp local.properties.example local.properties
   ```

2. Edit `local.properties` and fill in your real token:

   ```
   publisherToken=...
   ```

3. Rebuild — `./gradlew :example:assembleDebug` picks up the new value via `BuildConfig.PUBLISHER_TOKEN`.

If `local.properties` is missing or the key isn't set, the app falls back to `YOUR_PUBLISHER_TOKEN`; the app still compiles and runs, but `/preload` calls fail until a real token is set.

`userId` is hardcoded to `"user-1"` and `conversationId` is generated at runtime from `System.currentTimeMillis()` (unique per launch).

The example also wires `onDebugEvent` to logcat, so you can `adb logcat -s KontextExample` (or filter by tag in Android Studio's Logcat) to see SDK-internal events while interacting with the app.

## Project structure

```
sdk-kotlin/
├── ads/                              # publishable SDK library (:ads)
│   ├── build.gradle.kts              # module config + Vanniktech maven-publish wiring
│   ├── proguard-rules.pro            # SDK's own R8 rules
│   ├── consumer-proguard-rules.pro   # rules packaged into the .aar for consumers
│   └── src/
│       ├── main/kotlin/              # public API (KontextAds, Session, Ad, ...) + internals
│       └── test/kotlin/              # unit tests (JUnit 5 + mockk)
├── example/                          # demo Android app (:example)
│   ├── build.gradle.kts
│   └── src/main/java/                # MainActivity (Compose chat UI)
├── local-maven/iab/                  # vendored OMID AAR (IAB ships off-Maven)
├── extras/detekt.yml                 # static-analysis config
├── gradle/libs.versions.toml         # version catalog — single source of truth for deps
├── settings.gradle.kts               # module declarations + repository config
├── CLAUDE.md                         # Claude Code project context
├── RELEASING.md                      # how to cut a release
└── README.md
```

## Common Gradle commands

### Build

```bash
./gradlew :ads:assembleDebug          # SDK only
./gradlew :example:assembleDebug      # example app only
./gradlew assembleDebug               # both
./gradlew :ads:build                  # SDK + tests + lint
```

### Test

```bash
./gradlew :ads:testDebug                                          # all unit tests
./gradlew :ads:testDebug --tests "SessionTest"                    # single class
./gradlew :ads:testDebug --tests "SessionTest.addMessageTriggersPreload"
```

### Lint / formatting

```bash
./gradlew spotlessCheck    # verify formatting
./gradlew spotlessApply    # auto-fix formatting
./gradlew detekt           # static analysis (config: extras/detekt.yml)
```

### Full CI sequence

The same set of tasks GitHub Actions runs on every push/PR:

```bash
./gradlew spotlessCheck detekt assembleDebug testDebug
```

## Code style

- **Kotlin 1.9.22, JDK 17, AGP 8.6.1, compileSdk 36, minSdk 26.**
- **`explicitApi()` mode is enforced on `:ads`** — every public/internal declaration spells out its visibility, and public functions need explicit return types.
- **No comments by default** — add one only when the *why* is non-obvious (hidden constraint, subtle invariant, workaround for a specific bug, behaviour that would surprise a reader). Don't restate what the code already says.
- **Spotless** runs ktlint on `.kt` and `.gradle.kts`. `import-ordering`, `filename`, and `no-wildcard-imports` are relaxed to match the v4 codebase's existing style.
- **Detekt** runs with `extras/detekt.yml` layered over the bundled defaults. `TooGenericExceptionCaught` is off for boundary code (HTTP/JSON/lifecycle); everything else uses default thresholds.

## Cross-SDK consistency

The Kotlin SDK is one of three v4 SDKs (alongside [sdk-swift](https://github.com/kontextso/sdk-swift) and the in-monorepo [sdk-flutter](https://github.com/kontextso/sdk-v4/tree/main/sdk/sdk-flutter) / sdk-react-native). They share a wire protocol and a deliberate file-structure parity: `Configuration.kt` / `Configuration.swift`, `Inbound.kt` / `Inbound.swift`, `Session.kt` / `Session.swift`, etc.

When adding a new type or renaming an existing one, **match the swift counterpart**. If you can't, leave a comment explaining the mismatch.

## KontextKit dependency

The shared Android primitives — device info, IDFA, TCF, in-app browser, OMID lifecycle — live in [`so.kontext.kit:kontext-kit-android`](https://github.com/kontextso/kontextkit-android), published to Maven Central. The version is pinned in `gradle/libs.versions.toml`:

```toml
kontext-kit = "0.0.1"
```

For local development against an unreleased KontextKit, add a composite build to `settings.gradle.kts`:

```kotlin
includeBuild("../kontextkit-android")
```

Then revert before merging. (Composite builds are intentionally not committed — they don't survive consumer Gradle resolution.)

## OMID AAR

`local-maven/iab/omsdk-android/1.6.4/omsdk-android-1.6.4.aar` is the vendored IAB Open Measurement SDK. KontextKit's `OmManager` loads it reflectively at runtime; `:ads` declares an explicit `implementation("iab:omsdk-android:1.6.4")` so the classes are on the runtime classpath for that reflective loader.

Updating OMID is a manual file replacement — drop the new AAR + POM into `local-maven/iab/omsdk-android/<version>/` and bump the version in `ads/build.gradle.kts`.

## Releasing

See [RELEASING.md](./RELEASING.md). Short version: bump `sdk-kotlin` in `gradle/libs.versions.toml`, edit `CHANGELOG.md`, PR to `main`, then `git tag -a X.Y.Z` triggers `publish_sdk.yml` which publishes to Maven Central via Vanniktech maven-publish.

## Troubleshooting

**"Could not resolve so.kontext.kit:kontext-kit-android"** — confirm `mavenCentral()` is in your local Gradle init (it is in `settings.gradle.kts`). If you're behind a proxy, the Sonatype mirror at `https://repo1.maven.org/maven2/` may be blocked.

**Gradle sync hangs in Android Studio** — bump the heap in `gradle.properties` (`org.gradle.jvmargs=-Xmx6144m`) or hit File → Invalidate Caches.

**"Unsupported class file major version"** — your local JDK is older than 17. Set `JAVA_HOME` to a JDK 17 install, or let Gradle's `jvmToolchain(17)` auto-provision it (slow first time).

**Lint passes locally but fails on CI** — your IDE's ktlint version cache may differ from the wrapper's version. Run `./gradlew --rerun-tasks spotlessCheck` to bust the cache.

**OMID reflective lookup fails at runtime** — confirm the AAR is on the consuming app's classpath. The `iab:omsdk-android:1.6.4` dep in `ads/build.gradle.kts` is `implementation` scope, so it's transitively visible to consumers; if a downstream app declares a stricter classpath, they may need to add the dep themselves.
