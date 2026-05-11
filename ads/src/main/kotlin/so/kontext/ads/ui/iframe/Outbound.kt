package so.kontext.ads.ui.iframe

import org.json.JSONArray
import org.json.JSONObject
import so.kontext.ads.SDKInfo
import so.kontext.ads.model.Message

/**
 * Wire DTOs for SDK → iframe `postMessage` traffic. Counterpart to
 * `Inbound.kt` (which models the iframe → SDK direction). Mirrors
 * iOS `WebView/Iframe/Outbound.swift`.
 *
 * Each builder corresponds to one wire `type` and returns the full
 * envelope JSONObject ready for `evaluateJavascript("window.postMessage(...)")`.
 * The `type` field is set inside the builder so call sites can't
 * accidentally produce a malformed envelope.
 */

/**
 * `update-iframe` — pushes the conversation snapshot, SDK identity, and
 * publisher-supplied `otherParams` (e.g. `theme`) into the iframe after
 * `init-iframe` is observed.
 */
internal fun buildUpdateIframeMessage(
    messages: List<Message>,
    messageId: String,
    code: String,
    theme: String?,
): JSONObject {
    val messagesJson = JSONArray().apply {
        messages.forEach { msg ->
            put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role.value)
                    put("content", msg.content)
                },
            )
        }
    }

    val data = JSONObject().apply {
        put("sdk", SDKInfo.NAME)
        put("code", code)
        put("messageId", messageId)
        put("messages", messagesJson)
        if (theme != null) {
            put("otherParams", JSONObject().apply { put("theme", theme) })
        }
    }

    return JSONObject().apply {
        put("type", "update-iframe")
        put("data", data)
    }
}

/**
 * `update-dimensions-iframe` — periodic viewport / container geometry
 * snapshot used by the iframe for visibility tracking. `windowWidth` /
 * `windowHeight` are the visible app viewport (excluding system bars);
 * `screenWidth` / `screenHeight` are the full physical display. They
 * differ in multi-window / split-screen / freeform / on foldables, so
 * both pairs are always sent.
 */
@Suppress("LongParameterList")
internal fun buildUpdateDimensionsMessage(
    windowWidth: Float,
    windowHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    containerWidth: Float,
    containerHeight: Float,
    containerX: Float,
    containerY: Float,
    keyboardHeight: Float,
): JSONObject {
    val data = JSONObject().apply {
        put("windowWidth", windowWidth.toDouble())
        put("windowHeight", windowHeight.toDouble())
        put("screenWidth", screenWidth.toDouble())
        put("screenHeight", screenHeight.toDouble())
        put("containerWidth", containerWidth.toDouble())
        put("containerHeight", containerHeight.toDouble())
        put("containerX", containerX.toDouble())
        put("containerY", containerY.toDouble())
        put("keyboardHeight", keyboardHeight.toDouble())
    }

    return JSONObject().apply {
        put("type", "update-dimensions-iframe")
        put("data", data)
    }
}

/**
 * `user-event-iframe` — publisher → ad. Carries the typed event name
 * plus a free-form publisher-supplied payload (any JSON-shaped value).
 * `code` is the targeted placement; iframes whose configured code
 * differs ignore the event (mirrors sdk-js + sdk-swift).
 */
internal fun buildUserEventMessage(
    name: String,
    payload: Map<String, Any>?,
    code: String,
): JSONObject {
    val data = JSONObject().apply {
        put("name", name)
        if (payload != null) put("payload", JSONObject(payload))
    }

    return JSONObject().apply {
        put("type", "user-event-iframe")
        put("data", data)
        put("code", code)
    }
}
