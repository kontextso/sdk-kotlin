package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AddMessageOptionsTest {

    @Test
    fun `trackOnly defaults to false`() {
        assertFalse(AddMessageOptions().trackOnly)
    }
}
