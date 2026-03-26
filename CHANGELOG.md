# Changelog

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
