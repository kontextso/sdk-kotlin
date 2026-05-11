package so.kontext.ads.model

/**
 * Callback for SDK-internal debug / diagnostic events. Receives a
 * namespaced event name (e.g. `"Session: message-added"`,
 * `"Ad: mount"`, `"Preload: error-preloading-ads"`) and an optional
 * structured payload. Used by `Session`, `Ad`, `Preload`, and `Init`
 * to surface internal state transitions and errors to publishers who
 * opt in via `SessionOptions.onDebugEvent`.
 *
 * Distinct from [AdEventHandler], which delivers the publisher-facing
 * ad lifecycle events. Use this for diagnostic logging during
 * development; don't drive product behaviour off these events — names
 * and payloads can change between releases.
 *
 * Payload type is `Any?` rather than a typed value because debug
 * payloads are heterogeneous (often `Map<String, Any?>` blobs that
 * include raw response objects). The trade-off is conscious: type
 * safety in exchange for the diagnostic flexibility this callback
 * needs. Handlers are expected not to throw — an exception thrown
 * here propagates back into the SDK's lifecycle.
 *
 * Mirrors iOS `DebugEventHandler` (`KontextSwiftSDK/Model/DebugEvent.swift`).
 * Swift's typealias is also `@Sendable`; Kotlin doesn't have an
 * equivalent annotation (threading is enforced by dispatchers, not
 * the type system).
 */
public typealias DebugEventHandler = (String, Any?) -> Unit
