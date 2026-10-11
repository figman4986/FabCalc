package com.figapps.fractioncalculatorv1.settings

import android.content.Context
import com.figapps.fractioncalculatorv1.history.HistoryDatabase
import com.figapps.fractioncalculatorv1.history.HistoryRepository
import com.figapps.fractioncalculatorv1.wire.WireDatasheetRepository

/**
 * Very small manual DI container. Constructed once by [com.figapps.fractioncalculatorv1.FractionApp]
 * and handed down through [com.figapps.fractioncalculatorv1.ui.LocalAppContainer].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: HistoryDatabase = HistoryDatabase.get(appContext)

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val historyRepository: HistoryRepository = HistoryRepository(database.historyDao())

    val wireRepository: WireDatasheetRepository = WireDatasheetRepository(appContext, database)
}
