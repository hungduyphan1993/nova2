package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.network.StreamDiagnosticsService
import com.example.data.repository.NovaStreamRepository
import com.example.player.NovaPlayerManager

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.getInstance(application)
    val preferences: PreferencesRepository = PreferencesRepository(application)
    val repository: NovaStreamRepository = NovaStreamRepository(application, database, preferences)
    val playerManager: NovaPlayerManager = NovaPlayerManager(application)
    val diagnosticsService: StreamDiagnosticsService = StreamDiagnosticsService()
}

class NovaStreamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
