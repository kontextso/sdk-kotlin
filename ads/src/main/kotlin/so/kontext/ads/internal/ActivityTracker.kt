package so.kontext.ads.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import java.lang.ref.WeakReference

/**
 * Walks the receiver's `rootView.context` `ContextWrapper` chain to find
 * the Activity it's attached to. Returns `null` if the view isn't
 * attached to an Activity (detached, mocked, etc.).
 *
 * Used for Chrome Custom Tabs at click time: the SDK's WebView is
 * created with `applicationContext` (to avoid leaks across recompose),
 * but once added to the Compose hierarchy its `rootView` is the
 * Activity's decor view — whose context IS the Activity context.
 */
/**
 * Walks the receiver's `rootView.context` `ContextWrapper` chain looking
 * for an Activity. Works pre-API-30-ish; on modern Android the DecorView
 * uses an internal `DecorContext` whose `baseContext` is a `ContextImpl`,
 * NOT the Activity — so this is unreliable as the sole lookup. Used only
 * as a defensive secondary check; primary path is [ActivityTracker].
 */
internal fun View.findActivityContext(): Activity? {
    var ctx: Context? = rootView?.context ?: context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Tracks the host app's currently-resumed Activity via
 * [Application.ActivityLifecycleCallbacks], so the SDK can resolve an
 * Activity reference even when the publisher passed `applicationContext`
 * into `KontextAds.createSession`.
 *
 * Chrome Custom Tabs ([so.kontext.kit.ui.InAppBrowserManager]) require an
 * Activity context; without this tracker, a publisher that follows the
 * usual "use applicationContext to avoid leaks" rule would silently lose
 * in-app browser on every ad click and fall back to a full system-browser
 * task switch.
 *
 * The reference is held weakly so a finished Activity is GC'd; on its
 * own `onActivityPaused` we also clear if it matches, so a backgrounded
 * app returns `null` from [current] and callers can decide their own
 * fallback (typically the next branch in `Ad.handleClick`).
 *
 * Registered process-wide on first call to [ensureRegistered]; subsequent
 * calls are no-ops. The single global registration is fine because we
 * only track one current Activity regardless of how many Sessions exist.
 */
internal object ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var currentActivityRef: WeakReference<Activity>? = null
    private var registered = false

    /** The topmost resumed Activity, or `null` if the app is in the background. */
    fun current(): Activity? = currentActivityRef?.get()

    /**
     * Idempotently registers the lifecycle callbacks on the host app's
     * [Application]. Called from `Session.init` when a non-null context
     * is provided. No-op if [context] doesn't resolve to an Application
     * (e.g., a mocked Context in unit tests).
     */
    @Synchronized
    fun ensureRegistered(context: Context) {
        if (registered) return
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(this)
        registered = true
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
