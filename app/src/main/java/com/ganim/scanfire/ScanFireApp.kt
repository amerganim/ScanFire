package com.ganim.scanfire

import android.app.Application

class ScanFireApp : Application() {
    /** Manual dependency container; small enough that a DI framework would only add weight. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
