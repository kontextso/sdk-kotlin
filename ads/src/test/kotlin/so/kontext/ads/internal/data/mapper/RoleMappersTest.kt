package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.Role

class RoleMappersTest {

    @Test
    fun `toDomain maps user to User`() {
        assertEquals(Role.User, Role.toDomain("user"))
    }

    @Test
    fun `toDomain maps assistant to Assistant`() {
        assertEquals(Role.Assistant, Role.toDomain("assistant"))
    }

    @Test
    fun `toDomain falls back to User for unknown value`() {
        assertEquals(Role.User, Role.toDomain("system"))
    }

    @Test
    fun `toDomain falls back to User for empty string`() {
        assertEquals(Role.User, Role.toDomain(""))
    }

    @Test
    fun `toDto produces lowercase role names`() {
        assertEquals("user", Role.User.toDto())
        assertEquals("assistant", Role.Assistant.toDto())
    }

    @Test
    fun `round-trip for every Role`() {
        for (role in Role.entries) {
            assertEquals(role, Role.toDomain(role.toDto()), "round-trip failed for $role")
        }
    }
}
