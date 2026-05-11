package so.kontext.ads.ui.iframe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.json.JSONObject
import so.kontext.ads.SDKInfo
import so.kontext.ads.model.Message
import so.kontext.ads.model.Role

/**
 * The three SDK → iframe message builders. Mirror iOS `Outbound.swift`
 * Encodable structs. Each builder returns a `JSONObject` ready for
 * `evaluateJavascript("window.postMessage(...)")` — the `type` field
 * is set inside the builder so call sites can't accidentally produce
 * a malformed envelope.
 */
class OutboundTest {

    // update-iframe ------------------------------------------------------------

    @Test
    fun `buildUpdateIframeMessage sets type, sdk, code, messageId`() {
        val json = buildUpdateIframeMessage(
            messages = listOf(Message(id = "u1", role = Role.USER, content = "Hello")),
            messageId = "a1",
            code = "inlineAd",
            theme = null,
        )
        assertEquals("update-iframe", json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals(SDKInfo.NAME, data.getString("sdk"))
        assertEquals("inlineAd", data.getString("code"))
        assertEquals("a1", data.getString("messageId"))
    }

    @Test
    fun `buildUpdateIframeMessage emits messages array with id, role, content`() {
        val json = buildUpdateIframeMessage(
            messages = listOf(
                Message(id = "u1", role = Role.USER, content = "Hello"),
                Message(id = "a1", role = Role.ASSISTANT, content = "Hi"),
            ),
            messageId = "a1",
            code = "inlineAd",
            theme = null,
        )
        val arr = json.getJSONObject("data").getJSONArray("messages")
        assertEquals(2, arr.length())
        assertEquals("u1", arr.getJSONObject(0).getString("id"))
        assertEquals("user", arr.getJSONObject(0).getString("role"))
        assertEquals("Hello", arr.getJSONObject(0).getString("content"))
        assertEquals("assistant", arr.getJSONObject(1).getString("role"))
    }

    @Test
    fun `buildUpdateIframeMessage omits otherParams when theme is null`() {
        val json = buildUpdateIframeMessage(
            messages = emptyList(),
            messageId = "a1",
            code = "inlineAd",
            theme = null,
        )
        // The data envelope should NOT contain otherParams when no theme is
        // supplied — keeps wire payload minimal.
        assertFalse(json.getJSONObject("data").has("otherParams"))
    }

    @Test
    fun `buildUpdateIframeMessage includes otherParams_theme when theme present`() {
        val json = buildUpdateIframeMessage(
            messages = emptyList(),
            messageId = "a1",
            code = "inlineAd",
            theme = "dark",
        )
        val otherParams = json.getJSONObject("data").getJSONObject("otherParams")
        assertEquals("dark", otherParams.getString("theme"))
    }

    // update-dimensions-iframe -------------------------------------------------

    @Test
    fun `buildUpdateDimensionsMessage emits all 9 geometry fields as Doubles`() {
        val json = buildUpdateDimensionsMessage(
            windowWidth = 360f, windowHeight = 800f,
            screenWidth = 360f, screenHeight = 880f,
            containerWidth = 320f, containerHeight = 200f,
            containerX = 20f, containerY = 100f,
            keyboardHeight = 0f,
        )
        assertEquals("update-dimensions-iframe", json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals(360.0, data.getDouble("windowWidth"))
        assertEquals(800.0, data.getDouble("windowHeight"))
        assertEquals(360.0, data.getDouble("screenWidth"))
        assertEquals(880.0, data.getDouble("screenHeight"))
        assertEquals(320.0, data.getDouble("containerWidth"))
        assertEquals(200.0, data.getDouble("containerHeight"))
        assertEquals(20.0, data.getDouble("containerX"))
        assertEquals(100.0, data.getDouble("containerY"))
        assertEquals(0.0, data.getDouble("keyboardHeight"))
    }

    @Test
    fun `buildUpdateDimensionsMessage distinguishes window vs screen dimensions`() {
        // Multi-window / split-screen / freeform / foldable scenarios:
        // window* fields are the visible app viewport, screen* fields
        // are the full physical display. The wire format keeps both
        // pairs so the iframe can tell them apart.
        val json = buildUpdateDimensionsMessage(
            windowWidth = 540f, windowHeight = 600f, // app gets half-screen
            screenWidth = 1080f, screenHeight = 1920f, // full display
            containerWidth = 540f, containerHeight = 200f,
            containerX = 0f, containerY = 0f,
            keyboardHeight = 0f,
        )
        val data = json.getJSONObject("data")
        assertNotNull(data.get("windowWidth"))
        assertNotNull(data.get("screenWidth"))
        // Pairs differ — confirms we're not bug-collapsing them into one.
        assert(data.getDouble("screenWidth") != data.getDouble("windowWidth"))
    }

    // user-event-iframe --------------------------------------------------------

    @Test
    fun `buildUserEventMessage with payload includes name and payload`() {
        val json = buildUserEventMessage(
            name = "ad.viewed",
            payload = mapOf("foo" to "bar", "n" to 42),
            code = "inlineAd",
        )
        assertEquals("user-event-iframe", json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals("ad.viewed", data.getString("name"))
        val payload = data.getJSONObject("payload")
        assertEquals("bar", payload.getString("foo"))
        assertEquals(42, payload.getInt("n"))
    }

    @Test
    fun `buildUserEventMessage with null payload omits payload field`() {
        val json = buildUserEventMessage(name = "publisher.event", payload = null, code = "inlineAd")
        val data = json.getJSONObject("data")
        assertEquals("publisher.event", data.getString("name"))
        // Wire payload kept minimal — no payload field at all (vs
        // `"payload": null` which would round-trip differently in JS).
        assertFalse(data.has("payload"), "payload should be omitted when null")
    }

    @Test
    fun `buildUserEventMessage carries code at top level for iframe-side filtering`() {
        // Iframes filter incoming events on `code` so a `sidebar`-targeted
        // event isn't acted on by an `inlineAd` iframe. Mirrors sdk-js +
        // sdk-swift wire shape.
        val json = buildUserEventMessage(name = "user.typing.started", payload = null, code = "sidebar")
        assertEquals("sidebar", json.getString("code"))
    }

    @Test
    fun `buildUserEventMessage envelope structure round-trips through JSONObject`() {
        // Sanity-check the JSON is well-formed — a stringify+reparse should
        // produce an equal envelope. Catches a category of bugs where
        // someone passes a non-JSON-encodable value at the leaf.
        val original = buildUserEventMessage("test", mapOf("x" to 1), code = "inlineAd")
        val roundTripped = JSONObject(original.toString())
        assertEquals(original.toString(), roundTripped.toString())
    }
}
