package com.example.perfectoutfit

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.CustomTestApplication

/** The Hilt test application, built on the app's own base class so WorkManager keeps its config. */
@CustomTestApplication(PerfectOutfitBaseApp::class)
interface PerfectOutfitTestApp

/** Runs instrumented tests in [PerfectOutfitTestApp], so `@TestInstallIn` modules apply. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, PerfectOutfitTestApp_Application::class.java.name, context)
}
