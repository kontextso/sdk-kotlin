package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.Constants

class AdOptionsTest {

    @Test
    fun `defaults match Constants DEFAULT_PLACEMENT_CODE`() {
        // Pin the default to the Constants value so a publisher who omits
        // `code` lands on the same placement that resolveConfig defaults to.
        val opts = AdOptions()

        assertEquals(Constants.DEFAULT_PLACEMENT_CODE, opts.code)
        assertNull(opts.theme)
    }
}
