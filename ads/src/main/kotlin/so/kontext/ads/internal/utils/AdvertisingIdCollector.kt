package so.kontext.ads.internal.utils

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AdvertisingIdCollector {
    suspend fun collect(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (info.isLimitAdTrackingEnabled) null else info.id
        } catch (_: Exception) {
            null
        }
    }
}
