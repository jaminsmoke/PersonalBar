package com.jaminsmoke.personalbar

import android.app.Application
import com.jaminsmoke.personalbar.lan.BarLanServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application del nodo de sala. Arranca el servidor LAN en [onCreate].
 * El FGS «Sala activa» es un ítem separado; aquí solo el bind del health.
 */
class PersonalBarApp : Application() {

    val lanServer: BarLanServer by lazy { BarLanServer() }

    private val _roomActive = MutableStateFlow(false)
    val roomActive: StateFlow<Boolean> = _roomActive.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        _roomActive.value = lanServer.startServer()
    }

    override fun onTerminate() {
        lanServer.stopServer()
        _roomActive.value = false
        super.onTerminate()
    }

    companion object {
        @Volatile
        private var instance: PersonalBarApp? = null

        fun get(): PersonalBarApp =
            instance ?: error("PersonalBarApp not initialized")
    }
}
