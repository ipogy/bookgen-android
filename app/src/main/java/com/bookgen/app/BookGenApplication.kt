package com.bookgen.app

import android.app.Application
import com.bookgen.app.core.di.AppContainer

class BookGenApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
