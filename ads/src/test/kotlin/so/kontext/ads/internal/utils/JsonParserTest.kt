package so.kontext.ads.internal.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonParserTest {

    @Test
    fun `jsonToMap parses a flat object`() {
        val m = """{"k": "v", "n": 42, "b": true}""".jsonToMap()!!
        assertEquals("v", m["k"])
        assertEquals(42L, m["n"]) // longOrNull wins over doubleOrNull for integers
        assertEquals(true, m["b"])
    }

    @Test
    fun `jsonToMap parses nested objects`() {
        val m = """{"outer": {"inner": 1}}""".jsonToMap()!!
        val outer = m["outer"] as Map<*, *>
        assertEquals(1L, outer["inner"])
    }

    @Test
    fun `jsonToMap parses arrays`() {
        val m = """{"list": [1, "two", true, null]}""".jsonToMap()!!
        val list = m["list"] as List<*>
        assertEquals(4, list.size)
        assertEquals(1L, list[0])
        assertEquals("two", list[1])
        assertEquals(true, list[2])
        assertNull(list[3])
    }

    @Test
    fun `jsonToMap represents JSON null as Kotlin null`() {
        val m = """{"k": null}""".jsonToMap()!!
        assertTrue(m.containsKey("k"))
        assertNull(m["k"])
    }

    @Test
    fun `jsonToMap parses double for fractional numbers`() {
        val m = """{"pi": 3.14}""".jsonToMap()!!
        assertEquals(3.14, m["pi"])
    }

    @Test
    fun `jsonToMap returns null for malformed JSON`() {
        assertNull("not-json".jsonToMap())
        assertNull("{bad".jsonToMap())
    }

    @Test
    fun `jsonToMap returns null for a JSON array at the root`() {
        assertNull("""[1, 2, 3]""".jsonToMap())
    }

    @Test
    fun `jsonToMap handles empty object`() {
        val m = "{}".jsonToMap()!!
        assertTrue(m.isEmpty())
    }

    @Test
    fun `jsonToMap preserves key order is not guaranteed but all keys are present`() {
        val m = """{"a": 1, "b": 2, "c": 3}""".jsonToMap()!!
        assertEquals(setOf("a", "b", "c"), m.keys)
    }
}
