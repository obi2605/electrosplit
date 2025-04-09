/*package com.example.electrosplitapp

import android.app.Application
import android.content.Context
import androidx.annotation.Keep

@Keep  // Prevents obfuscation if using ProGuard/R8
class App : Application() {

    // Use AppContainer pattern for dependency management
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

// Centralized container for application dependencies
class AppContainer(private val appContext: Context) {

    // Initialize your VisionService here
    val visionService: VisionService by lazy {
        VisionService(appContext)
    }

    // Add other app-wide dependencies here as needed
    // val someOtherService: SomeOtherService by lazy { ... }
}*/