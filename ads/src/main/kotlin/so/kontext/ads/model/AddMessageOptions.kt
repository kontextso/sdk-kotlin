package so.kontext.ads.model

/**
 * Per-call options for `Session.addMessage(...)`.
 *
 * When [trackOnly] is `true`, the preload request is still sent (so the
 * server can keep pacing / frequency-cap state up to date) but bids are
 * not processed — no ad will be shown for this message and no
 * `AdEvent.Filled` is emitted. Use this when the publisher knows the
 * user is in a paid tier or otherwise ineligible for an ad on this
 * message but you still want Kontext to retain conversation context.
 *
 * Mirrors `addMessage(msg, { trackOnly: true })` in sdk-js and iOS
 * `AddMessageOptions(trackOnly:)`.
 */
public data class AddMessageOptions(
    val trackOnly: Boolean = false,
)
