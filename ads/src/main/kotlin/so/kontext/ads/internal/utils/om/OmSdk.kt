package so.kontext.ads.internal.utils.om

import android.content.Context
import com.iab.omid.library.megabrainco.Omid
import com.iab.omid.library.megabrainco.adsession.Partner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import so.kontext.ads.BuildConfig

private const val PartnerName = "megabrainco"

internal object OmSdk {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var partner: Partner
        private set

    fun init(context: Context) {
        mainScope.launch {
            if (Omid.isActive().not()) {
                Omid.activate(context)
            }
            partner = Partner.createPartner(PartnerName, BuildConfig.SDK_VERSION)
        }
    }

    fun close() {
        mainScope.cancel()
    }
}
