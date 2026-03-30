package so.kontext.ads.internal.utils.om

import android.content.Context
import com.iab.omid.library.megabrainco.Omid
import com.iab.omid.library.megabrainco.adsession.Partner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PartnerName = "Megabrainco"
private const val OmIntegrationVersion = "1.0.0"

internal object OmSdk {

    // @Volatile ensures the reassignment in init() is visible across threads.
    @Volatile
    private var mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Initialized eagerly — Partner.createPartner() is a pure data constructor
    // and does not require Omid.activate() to be called first. Keeping it here
    // avoids a race where WebViewOmSession.start() could access 'partner' before
    // the init() coroutine had a chance to run.
    val partner: Partner = Partner.createPartner(PartnerName, OmIntegrationVersion)

    fun init(context: Context) {
        if (!mainScope.isActive) {
            mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
        mainScope.launch {
            if (Omid.isActive().not()) {
                Omid.activate(context)
            }
        }
    }

    fun close() {
        mainScope.cancel()
    }
}
