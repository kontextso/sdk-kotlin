package so.kontext.ads.internal.utils.consent

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs under Robolectric so `PreferenceManager.getDefaultSharedPreferences(context)`
 * returns a real `SharedPreferences` implementation.
 */
@RunWith(RobolectricTestRunner::class)
class TcfInfoGetTcfDataTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `returns all-null TcfData when no TCF keys are present`() {
        val data = TcfInfo.getTcfData(context)
        assertNull(data.gdpr)
        assertNull(data.gdprConsent)
    }

    @Test
    fun `reads gdprApplies=1 and a non-empty tcString`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt("IABTCF_gdprApplies", 1)
            .putString("IABTCF_TCString", "CONSENT-STRING")
            .commit()

        val data = TcfInfo.getTcfData(context)
        assertEquals(1, data.gdpr)
        assertEquals("CONSENT-STRING", data.gdprConsent)
    }

    @Test
    fun `reads gdprApplies=0`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt("IABTCF_gdprApplies", 0)
            .commit()
        assertEquals(0, TcfInfo.getTcfData(context).gdpr)
    }

    @Test
    fun `gdprApplies outside 0 or 1 is treated as null`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt("IABTCF_gdprApplies", 5)
            .commit()
        assertNull(TcfInfo.getTcfData(context).gdpr)
    }

    @Test
    fun `empty tcString is treated as null`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("IABTCF_TCString", "")
            .commit()
        assertNull(TcfInfo.getTcfData(context).gdprConsent)
    }

    @Test
    fun `gdprApplies stored as a numeric string is read via the string fallback`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("IABTCF_gdprApplies", "1")
            .commit()
        assertEquals(1, TcfInfo.getTcfData(context).gdpr)
    }

    @Test
    fun `gdprApplies stored as a non-numeric string is treated as null`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("IABTCF_gdprApplies", "not-a-number")
            .commit()
        assertNull(TcfInfo.getTcfData(context).gdpr)
    }

    @Test
    fun `missing gdprApplies key with a valid tcString only populates gdprConsent`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("IABTCF_TCString", "CONSENT")
            .commit()
        val data = TcfInfo.getTcfData(context)
        assertNull(data.gdpr)
        assertEquals("CONSENT", data.gdprConsent)
    }
}
