package com.figapps.fractioncalculatorv1

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.figapps.fractioncalculatorv1.history.HistoryDatabase
import com.figapps.fractioncalculatorv1.history.HistoryDraft
import com.figapps.fractioncalculatorv1.history.HistoryEntry
import com.figapps.fractioncalculatorv1.settings.AppContainer
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.ui.HistoryScreen
import com.figapps.fractioncalculatorv1.ui.LocalAppContainer
import com.figapps.fractioncalculatorv1.ui.WireLibraryScreen
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme
import com.figapps.fractioncalculatorv1.wire.MetalType
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireStageResult
import com.figapps.fractioncalculatorv1.wire.WireTags
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryAndWireLibraryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: HistoryDatabase
    private lateinit var container: AppContainer
    private var originalDatabase: HistoryDatabase? = null
    private lateinit var instanceField: java.lang.reflect.Field
    private lateinit var initialWireFiles: Set<String>
    private val sourceFiles = mutableListOf<File>()
    private val resultPrefix = "ui-test-${UUID.randomUUID()}"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        instanceField = HistoryDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        originalDatabase = instanceField.get(null) as? HistoryDatabase
        database = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        instanceField.set(null, database)
        container = AppContainer(context)
        initialWireFiles = container.wireRepository.directory().listFiles().orEmpty().map { it.name }.toSet()
    }

    @After
    fun tearDown() {
        composeRule.waitForIdle()
        container.wireRepository.directory().listFiles().orEmpty()
            .filter { it.name !in initialWireFiles }
            .forEach(File::delete)
        sourceFiles.forEach(File::delete)
        database.close()
        instanceField.set(null, originalDatabase)
    }

    @Test
    fun historyDeleteConfirmationCancelAndConfirm() {
        val draft = historyEntry("${resultPrefix}-delete", "History Delete Result", 100L)
        val entry = draft.copy(id = runBlocking { database.historyDao().insert(draft) })
        setScreen { HistoryScreen() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(entry.result).performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete this entry?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertNotNull(runBlocking { database.historyDao().findById(entry.id) })

        composeRule.onNodeWithText(entry.result).performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { runBlocking { database.historyDao().findById(entry.id) == null } }
        composeRule.onNodeWithText(entry.result).assertDoesNotExist()
    }

    @Test
    fun historyClearAllConfirmationCancelAndConfirm() {
        val entries = listOf(
            historyEntry("${resultPrefix}-clear-a", "Clear Result A", 100L),
            historyEntry("${resultPrefix}-clear-b", "Clear Result B", 200L),
        )
        runBlocking { entries.forEach { database.historyDao().insert(it) } }
        setScreen { HistoryScreen() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Clear history").performClick()
        composeRule.onNodeWithText("Clear history?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
            assertEquals(2, runBlocking { database.historyDao().observeAll().first().size })

        composeRule.onNodeWithText("Clear history").performClick()
        composeRule.onNodeWithText("Clear", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { runBlocking { database.historyDao().observeAll().first().isEmpty() } }
        composeRule.onNodeWithText("No calculations yet.", substring = true).assertExists()
    }

    @Test
    fun historyGroupsByNewestToolAndRendersEntryDetails() {
        val aOld = historyEntry("${resultPrefix}-a-old", "A Older Result", 100L, "Tool A")
        val bNew = historyEntry("${resultPrefix}-b-new", "B New Result", 300L, "Tool B")
        val aNew = historyEntry("${resultPrefix}-a-new", "A Newest Result", 500L, "Tool A")
        runBlocking {
            database.historyDao().insert(aOld)
            database.historyDao().insert(bNew)
            database.historyDao().insert(aNew)
        }
        setScreen { HistoryScreen() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tool A").assertExists()
        composeRule.onNodeWithText("Tool B").assertExists()
        val toolATop = composeRule.onNodeWithText("Tool A").fetchSemanticsNode().boundsInRoot.top
        val toolBTop = composeRule.onNodeWithText("Tool B").fetchSemanticsNode().boundsInRoot.top
        assertTrue("group with newest entry is listed first", toolATop < toolBTop)

        composeRule.onNodeWithText(aNew.result).performClick()
        composeRule.onNodeWithText("Inputs").assertExists()
        assertTrue(composeRule.onAllNodesWithText(aNew.inputs).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Detail").assertExists()
        composeRule.onNodeWithText(aNew.detail).assertExists()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(aNew.result).assertExists()
    }

    @Test
    fun wireDeleteConfirmationPreservesOnCancelAndDeletesOnConfirm() = runBlocking {
        val (entry, source) = importWire("${resultPrefix}-delete-wire")
        setScreen { WireLibraryScreen(settings = AppSettings()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Actions").performClick()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Delete this datasheet?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertNotNull(runBlocking { database.wireDatasheetDao().findById(entry.id) })
        assertTrue(container.wireRepository.fileFor(entry.fileName).isFile)

        composeRule.onNodeWithContentDescription("Actions").performClick()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Delete this datasheet?").assertExists()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { runBlocking { database.wireDatasheetDao().findById(entry.id) == null } }
        assertTrue("source picker file remains caller-owned", source.isFile)
        assertTrue("stored PDF is removed after confirmed delete", !container.wireRepository.fileFor(entry.fileName).exists())
    }

    @Test
    fun missingPdfViewerShowsErrorAndRemoveRepairsRow() = runBlocking {
        val entry = insertMissingWire("${resultPrefix}-missing-wire")
        setScreen { WireLibraryScreen(settings = AppSettings()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(entry.productName).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Can't open this datasheet").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Remove").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { runBlocking { database.wireDatasheetDao().findById(entry.id) == null } }
        composeRule.onNodeWithText(entry.productName).assertDoesNotExist()
    }

    @Test
    fun importCancelDiscardsStagedPdfAndDuplicateShowsExistingEntry() {
        val existingName = "${resultPrefix}-existing"
        val (existing, existingSource) = runBlocking { importWire(existingName) }
        val newSource = pdfSource("${resultPrefix}-cancel")
        val owner = RecordingRegistryOwner()
        setScreen(owner) { WireLibraryScreen(settings = AppSettings()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Import a datasheet PDF").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { owner.registry.dispatchFile(Uri.fromFile(newSource)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Tag datasheet").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Import failed").fetchSemanticsNodes().isNotEmpty()
        }
        val reachedTags = composeRule.onAllNodesWithText("Tag datasheet").fetchSemanticsNodes().isNotEmpty()
        val notPdf = composeRule.onAllNodesWithText("That file isn't a PDF", substring = true).fetchSemanticsNodes().isNotEmpty()
        val cannotOpen = composeRule.onAllNodesWithText("Could not open the selected file", substring = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue("valid local PDF should reach tag form (notPdf=$notPdf, cannotOpen=$cannotOpen)", reachedTags)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(1, runBlocking { container.wireRepository.entries.first().size })
        assertTrue(existingSource.isFile)
        assertTrue(newSource.isFile)

        composeRule.onNodeWithContentDescription("Import a datasheet PDF").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { owner.registry.dispatchFile(Uri.fromFile(existingSource)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Already in the library").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Already in the library").assertExists()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
        assertEquals(1, runBlocking { container.wireRepository.entries.first().size })
        assertNotNull(runBlocking { database.wireDatasheetDao().findById(existing.id) })
    }

    @Test
    fun largeRealDatasheetFixtureRendersPageOne() {
        val source = copyPdfFixture("pdf-fixtures/12_DW-50.pdf", "${resultPrefix}-large.pdf")
        val staged = runBlocking {
            container.wireRepository.stagePdf(Uri.fromFile(source)) {} as WireStageResult.Staged
        }
        val entry = runBlocking {
            container.wireRepository.commit(
                staged.file,
                staged.sha256,
                WireTags(brand = "Kobelco", productName = "Renderer Fixture"),
            )
        }
        setScreen { WireLibraryScreen(settings = AppSettings()) }
        composeRule.waitForIdle()

        val tapStarted = SystemClock.elapsedRealtime()
        composeRule.onNodeWithText(entry.productName).performClick()
        composeRule.onNodeWithContentDescription("Back to library").assertExists()
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("Datasheet page 1").fetchSemanticsNode()
            }.isSuccess
        }
        val elapsed = SystemClock.elapsedRealtime() - tapStarted
        Log.i("WirePdfRegression", "fixture=12_DW-50.pdf page=1 renderMs=$elapsed")
        assertTrue("page one must render within ten seconds (actual ${elapsed}ms)", elapsed < 10_000)
        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(entry.productName).assertExists()
    }

    private fun setScreen(
        registryOwner: RecordingRegistryOwner? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val localContent: @Composable () -> Unit = {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    FractionCalculatorV1Theme(darkTheme = false, dynamicColor = false, content = content)
                }
            }
            if (registryOwner == null) {
                localContent()
            } else {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    localContent()
                }
            }
        }
    }

    private suspend fun importWire(name: String): Pair<WireDatasheet, File> {
        val source = pdfSource(name)
        val staged = container.wireRepository.stagePdf(Uri.fromFile(source)) {} as WireStageResult.Staged
        val entry = container.wireRepository.commit(
            staged.file,
            staged.sha256,
            WireTags(brand = "Test Brand", productName = name, metalType = MetalType.CARBON_STEEL),
        )
        return entry to source
    }

    private fun insertMissingWire(name: String): WireDatasheet {
        val row = WireDatasheet(
            fileName = "$name.pdf",
            sha256 = UUID.randomUUID().toString().replace("-", ""),
            brand = "Test Brand",
            productName = name,
            awsClassification = "ER70S-6",
            metalType = MetalType.CARBON_STEEL,
            process = com.figapps.fractioncalculatorv1.wire.WireProcess.SOLID_GMAW,
            wireSizeMm = 0.9,
            notes = null,
            createdAt = 1,
            updatedAt = 1,
        )
        val id = runBlocking { database.wireDatasheetDao().insert(row) }
        return row.copy(id = id)
    }

    private fun pdfSource(name: String): File = File(context.cacheDir, "$name.pdf").apply {
        writeBytes("%PDF-1.4\ncompose-test-$name\n%%EOF".toByteArray())
    }.also(sourceFiles::add)

    private fun copyPdfFixture(assetName: String, outputName: String): File {
        val output = File(context.cacheDir, outputName)
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
            output.outputStream().use { input.copyTo(it) }
        }
        sourceFiles += output
        return output
    }

    private fun historyEntry(result: String, value: String, time: Long, tool: String = "Tool Test") = HistoryEntry(
        toolId = result,
        toolName = tool,
        inputs = "Input $result",
        result = value,
        detail = "Detail $result",
        timestamp = time,
    )

    private class RecordingRegistryOwner : ActivityResultRegistryOwner {
        val registry = RecordingRegistry()
        override val activityResultRegistry: ActivityResultRegistry get() = registry
    }

    private class RecordingRegistry : ActivityResultRegistry() {
        private var currentRequestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            currentRequestCode = requestCode
        }

        fun dispatchFile(uri: Uri) {
            val requestCode = checkNotNull(currentRequestCode) { "Document picker was not launched" }
            check(dispatchResult(requestCode, uri)) { "No picker callback was registered" }
        }
    }
}
