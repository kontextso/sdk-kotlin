package so.kontext.ads.app.di

import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import so.kontext.ads.app.MainViewModel

val appModule = module {
    viewModel { MainViewModel(androidApplication()) }
}
