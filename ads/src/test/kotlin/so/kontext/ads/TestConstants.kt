package so.kontext.ads

/**
 * Fixed install-ID stub used by every test that constructs a
 * `ResolvedConfig`, `ErrorContext`, `DebugContext`, or one of the
 * four request DTOs. Mirrors sdk-swift's `TestInstallId` pattern.
 *
 * Production resolves `installId` via
 * `InstallIdProvider.getOrCreate(context)` in `KontextAds.createSession`,
 * but tests don't always have a Robolectric `Context` and shouldn't
 * depend on filesystem state for an assertion to pass — pinning a
 * literal string keeps test fixtures deterministic and self-evident.
 */
internal const val TEST_INSTALL_ID: String = "01890000-0000-7000-8000-000000000001"
