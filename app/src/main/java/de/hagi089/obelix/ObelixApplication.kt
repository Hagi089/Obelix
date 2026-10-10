package de.hagi089.obelix

import android.app.Application
import de.hagi089.obelix.notifications.AndroidChangePoster

class ObelixApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AndroidChangePoster.createChannel(this)
    }
}
