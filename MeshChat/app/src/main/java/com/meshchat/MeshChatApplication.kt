package com.meshchat

import android.app.Application

/**
 * Класс приложения MeshChat.
 */
class MeshChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MeshChatApplication
            private set
    }
}
