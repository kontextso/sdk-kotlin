package so.kontext.ads.ui.iframe

import org.json.JSONObject
import so.kontext.ads.Constants

/**
 * Internal protocol vocabulary between `AdWebView` (parser) and `Ad`
 * (consumer). Models the iframe → SDK direction of the postMessage
 * protocol defined in `sdk-common/src/iframe-messaging.ts`. Mirrors
 * iOS `WebView/Iframe/Inbound.swift`.
 *
 * Convention: every case ends in `Iframe` (matches the wire-protocol
 * names verbatim) and carries a single typed payload data class. Cases
 * without a payload are `object`s.
 *
 * **Defensive parsing.** Parser methods read all wire fields via safe
 * casts (`as? String` etc.); type mismatches return null fields and the
 * SDK silently drops the invalid event rather than crashing.
 *
 * Wire shapes intentionally not modelled here:
 *  - `view-iframe` — same data arrives via `event-iframe`'s `ad.viewed`.
 *  - `set-styles-iframe` — not used on mobile.
 *  - SKOverlay (`open-skoverlay-iframe` / `close-skoverlay-iframe`) —
 *    iOS-only (StoreKit). Android attribution uses Play Install Referrer
 *    in a different shape; no Android counterpart needed.
 */
internal sealed class IframeEvent {

    public object Init : IframeEvent()
    public data class Resize(val height: Float) : IframeEvent()
    public object Show : IframeEvent()
    public object Hide : IframeEvent()
    public data class Event(val name: String, val payload: Map<String, Any?>?) : IframeEvent()
    public object Error : IframeEvent()
    public data class Click(
        val id: String?,
        val content: String?,
        val messageId: String?,
        val url: String?,
        val target: Target,
        val fallbackUrl: String?,
        val appStoreId: String?,
    ) : IframeEvent()
    public data class AdDone(
        val id: String?,
        val content: String?,
        val messageId: String?,
    ) : IframeEvent()

    // Modal lifecycle. Component-tagged in the wire protocol but
    // modal-only in practice (iOS doc note carries over).
    public data class OpenComponent(
        val code: String?,
        val timeout: Int,
        val brightnessDelta: Double?,
        val componentParams: Map<String, Any?>?,
    ) : IframeEvent()

    public object InitComponent : IframeEvent()
    public data class CloseComponent(val data: Map<String, Any?>) : IframeEvent()
    public data class ErrorComponent(val message: String?, val errorType: String?) : IframeEvent()
    public data class AdDoneComponent(val data: Map<String, Any?>) : IframeEvent()

    /**
     * Click destination preference. `BROWSER` (default) routes to the
     * system browser; `IN_APP` requests Custom Tabs and falls back to
     * the system browser if the Custom Tab can't be presented.
     */
    public enum class Target(public val wireValue: String) {
        BROWSER("browser"),
        IN_APP("in-app"),
        ;

        public companion object {
            /** Parses the wire value; missing / unknown decays to `BROWSER`. */
            public fun fromString(raw: Any?): Target = when (raw) {
                "in-app" -> IN_APP
                else -> BROWSER
            }
        }
    }

    public companion object {
        /**
         * Parses an inbound message envelope into a typed [IframeEvent].
         * Returns null when the envelope is missing required fields,
         * uses an unknown `type`, or fails JSON parse — caller should
         * drop the event silently. Defensive throughout: malformed
         * data must never crash the SDK.
         */
        @Suppress("UNCHECKED_CAST")
        public fun parse(json: String): IframeEvent? {
            val obj = try {
                JSONObject(json)
            } catch (_: Exception) {
                return null
            }
            val type = obj.optString("type", "").takeIf { it.isNotEmpty() } ?: return null
            val data = obj.optJSONObject("data")?.let { jsonObjectToMap(it) } ?: emptyMap()

            return when (type) {
                "init-iframe" -> Init
                "show-iframe" -> Show
                "hide-iframe" -> Hide
                "error-iframe" -> Error
                "init-component-iframe" -> InitComponent
                "ad-done-component-iframe" -> AdDoneComponent(data)
                "close-component-iframe" -> CloseComponent(data)
                "resize-iframe" -> {
                    val height = (data["height"] as? Number)?.toFloat() ?: return null
                    Resize(height)
                }
                "event-iframe" -> {
                    val name = data["name"] as? String ?: return null

                    @Suppress("UNCHECKED_CAST")
                    val payload = data["payload"] as? Map<String, Any?>
                    Event(name, payload)
                }
                "click-iframe" -> Click(
                    id = data["id"] as? String,
                    content = data["content"] as? String,
                    messageId = data["messageId"] as? String,
                    url = data["url"] as? String,
                    target = Target.fromString(data["target"]),
                    fallbackUrl = data["fallbackUrl"] as? String,
                    appStoreId = data["appStoreId"] as? String,
                )
                "ad-done-iframe" -> AdDone(
                    id = data["id"] as? String,
                    content = data["content"] as? String,
                    messageId = data["messageId"] as? String,
                )
                "open-component-iframe" -> OpenComponent(
                    code = data["code"] as? String,
                    timeout = (data["timeout"] as? Number)?.toInt()
                        ?.takeIf { it > 0 }
                        ?: Constants.DEFAULT_MODAL_TIMEOUT_MS,
                    brightnessDelta = (data["brightnessDelta"] as? Number)?.toDouble(),
                    componentParams = data["componentParams"] as? Map<String, Any?>,
                )
                "error-component-iframe" -> ErrorComponent(
                    message = data["message"] as? String,
                    errorType = data["errorType"] as? String,
                )
                else -> null
            }
        }

        /** Recursively converts a JSONObject tree into a `Map<String, Any?>`. */
        public fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { key ->
                val value = obj.opt(key)
                map[key] = when (value) {
                    is JSONObject -> jsonObjectToMap(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            }
            return map
        }
    }
}
