package so.kontext.ads.app

import android.app.Application
import android.webkit.WebView
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import so.kontext.ads.BuildConfig
import so.kontext.ads.app.di.appModule

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        startKoin {
            androidContext(this@MainApplication)
            modules(appModule)
        }
    }
}
