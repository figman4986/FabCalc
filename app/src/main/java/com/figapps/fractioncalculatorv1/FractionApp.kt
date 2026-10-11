package com.figapps.fractioncalculatorv1

import android.app.Application
import com.figapps.fractioncalculatorv1.settings.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Owns the single [AppContainer] instance so repositories (settings and
 * history) are shared app-wide and never duplicated.
 */
class FractionApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { container.wireRepository.sweepStagedFiles() }
    }
}
