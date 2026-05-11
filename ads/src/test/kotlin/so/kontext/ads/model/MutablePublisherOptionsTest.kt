package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MutablePublisherOptionsTest {

    @Test
    fun `defaults to all null`() {
        val opts = MutablePublisherOptions()

        assertNull(opts.variantId)
        assertNull(opts.regulatory)
        assertNull(opts.userEmail)
        assertNull(opts.advertisingId)
    }
}
