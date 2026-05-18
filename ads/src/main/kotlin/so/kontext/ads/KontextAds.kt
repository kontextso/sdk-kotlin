package so.kontext.ads

import android.content.Context
import so.kontext.ads.model.SessionOptions
import so.kontext.kit.deviceinfo.InstallIdProvider

/**
 * Public entry point for creating Kontext Ads sessions.
 *
 * Declared as an `object` so the type cannot be instantiated — it's a pure
 * namespace, the Kotlin equivalent of sdk-swift's case-less `enum KontextAds`.
 *
 * The supplied `context` is unwrapped to its `applicationContext` before
 * being held by `Session`, so an Activity / Fragment context isn't leaked
 * into the long-lived session.
 *
 * Usage:
 * ```kotlin
 * val session = KontextAds.createSession(
 *     context = applicationContext,
 *     options = SessionOptions(
 *         publisherToken = "...",
 *         userId = "...",
 *         conversationId = "...",
 *     )
 * )
 *
 * session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
 * session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "Hi there"))
 * val ad = session.createAd("a1")
 * ```
 */
public object KontextAds {

    public fun createSession(context: Context, options: SessionOptions): Session {
        val appContext = context.applicationContext
        val installId = InstallIdProvider.getOrCreate(appContext)
        val config = resolveConfig(options, installId)
        return Session(context = appContext, config = config)
    }
}
