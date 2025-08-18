package so.kontext.ads.app.di

import so.kontext.ads.app.MainViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { MainViewModel(androidApplication()) }
}
