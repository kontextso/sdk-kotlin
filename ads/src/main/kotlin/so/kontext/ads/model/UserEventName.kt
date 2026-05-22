package so.kontext.ads.model

/**
 * Strongly-typed identifier for a publisher → ad-iframe user event.
 *
 * Use a case directly:
 * ```kotlin
 * session.sendUserEvent(UserEventName.USER_TYPING_STARTED)
 * ```
 *
 * The set of valid events is closed at compile time — typos or unknown
 * names are rejected at the call site rather than silently broadcast.
 *
 * To add a new event: add a case here (with the matching wire value)
 * and ship a release.
 *
 * Mirrors iOS `UserEventName` (`KontextSwiftSDK/Model/UserEventName.swift`)
 * and sdk-js's `UserEventName = keyof UserEventMap` semantics.
 */
public enum class UserEventName(public val wireValue: String) {
    /** The user has started typing in the publisher's input field. */
    USER_TYPING_STARTED("user.typing.started"),
}
