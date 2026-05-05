package so.kontext.ads.internal.ui

import android.webkit.JavascriptInterface
import org.intellij.lang.annotations.Language
import so.kontext.ads.internal.ui.model.IFrameEvent

internal const val IFrameBridgeName = "AndroidBridge"

internal class IFrameBridge(
    private val eventParser: IFrameEventParser,
    private val onEvent: (IFrameEvent) -> Unit,
) {
    @JavascriptInterface
    fun onMessage(json: String) {
        val inlineAdEvent = eventParser.parse(json)
        onEvent(inlineAdEvent)
    }

    companion object {
        @Language("JavaScript")
        internal const val DocumentStartScript = """
            (function() {
              if (window.__androidBridgeInstalled) return;
              window.__androidBridgeInstalled = true;
            
              window.addEventListener('message', function(e) {
                try {
                  var data = e && e.data !== undefined ? e.data : null;
                  if (data == null) return;
                  $IFrameBridgeName.onMessage(typeof data === 'string' ? data : JSON.stringify(data));
                } catch (e) {}
              }, true);
            })();
            """

        // Forces every <video> on the page to render on a black background and to play
        // inline with eager preload. Previously also set a 1×1 transparent PNG as `poster`
        // to suppress Android's default loader; that collapsed videoEl's bounding rect to
        // 1×1 which OMID's `setVideoElement` then reported as `adView.geometry`, failing
        // IAB compliance (KON-1714). Keeping the CSS + attributes; dropping the poster.
        internal const val PosterStartScript = """
            (function(){
              const css = document.createElement('style');
              css.textContent = "video{background:#000!important;}";
              document.documentElement.appendChild(css);

              const apply = () => {
                document.querySelectorAll('video').forEach(v => {
                  v.setAttribute('playsinline','');
                  v.setAttribute('preload','auto');
                });
              };
              apply();
              new MutationObserver(apply).observe(document.documentElement, {childList:true, subtree:true});
            })();
        """
    }
}
