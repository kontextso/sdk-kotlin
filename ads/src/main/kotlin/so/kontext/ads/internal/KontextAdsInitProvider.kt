package so.kontext.ads.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * No-op `ContentProvider` whose only job is to register
 * [ActivityTracker] with the host app's `Application` BEFORE any
 * Activity is created. Android initialises `ContentProvider`s between
 * `Application.attachBaseContext` and `Application.onCreate`, which is
 * the standard library auto-init mechanism (Firebase, WorkManager,
 * AndroidX App Startup, etc. all rely on it).
 *
 * Without this provider, [ActivityTracker.ensureRegistered] is called
 * from `Session.init` at composition time — AFTER `MainActivity.onResume`
 * has already fired, so we never observe the initial resume and
 * `ActivityTracker.current()` returns `null` for the entire lifetime of
 * the first Activity. With this provider, we register early enough to
 * catch the very first `onActivityResumed`.
 *
 * Declared in the SDK's `AndroidManifest.xml` with a per-app unique
 * authority (`${applicationId}.kontext-ads-init`) so multiple apps in
 * the same process or test multidex setups don't collide.
 */
internal class KontextAdsInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false
        ActivityTracker.ensureRegistered(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
