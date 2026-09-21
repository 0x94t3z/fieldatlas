package xyz.fieldatlas

import android.app.Application
import xyz.fieldatlas.ui.AppContainer

class FieldAtlasApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
