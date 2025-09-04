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

        internal const val PosterStartScript = """
            (function(){
              // 1x1 transparent PNG
              const T = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIW2NkYGBgAAAABAABJzQnCgAAAABJRU5ErkJggg==";
              // Make videos render on black, no placeholder
              const css = document.createElement('style');
              css.textContent = "video{background:#000!important;}";
              document.documentElement.appendChild(css);

              const apply = () => {
                document.querySelectorAll('video').forEach(v => {
                  // Overwrite any poster to guarantee no placeholder image
                  v.setAttribute('poster', T);
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
