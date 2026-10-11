package com.figapps.fractioncalculatorv1

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.figapps.fractioncalculatorv1.history.HistoryDatabase
import com.figapps.fractioncalculatorv1.wire.MetalType
import com.figapps.fractioncalculatorv1.wire.PdfContentAccess
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireDatasheetRepository
import com.figapps.fractioncalculatorv1.wire.WireProcess
import com.figapps.fractioncalculatorv1.wire.WireStageResult
import com.figapps.fractioncalculatorv1.wire.WireTags
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Exercises URI, filesystem and Room behavior on Android with controllable document streams. */
@RunWith(AndroidJUnit4::class)
class WireDatasheetRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: HistoryDatabase
    private lateinit var repository: WireDatasheetRepository
    private lateinit var content: FakePdfContentAccess
    private lateinit var initialStageNames: Set<String>
    private val databaseName = "wire-repository-test-${UUID.randomUUID()}.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.databaseBuilder(context, HistoryDatabase::class.java, databaseName).build()
        content = FakePdfContentAccess()
        repository = WireDatasheetRepository(context, database, content)
        initialStageNames = repository.directory().listFiles().orEmpty()
            .filter { it.name.startsWith(WireDatasheetRepository.STAGED_FILE_PREFIX) }
            .map { it.name }.toSet()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        repository.directory().listFiles().orEmpty()
            .filter { it.name.startsWith(WireDatasheetRepository.STAGED_FILE_PREFIX) && it.name !in initialStageNames }
            .forEach(File::delete)
        content.sourceFiles.forEach(File::delete)
    }

    @Test
    fun validPdfStagesCopyWithoutDeletingSource() = runBlocking {
        val (uri, source) = content.add(PDF)
        val result = repository.stagePdf(uri) {}
        assertTrue(result is WireStageResult.Staged)
        val staged = (result as WireStageResult.Staged).file
        assertTrue(staged.name.startsWith(WireDatasheetRepository.STAGED_FILE_PREFIX))
        assertTrue(staged.readBytes().contentEquals(PDF))
        assertTrue(source.isFile)
        assertTrue(source.readBytes().contentEquals(PDF))
        assertEquals(0, database.wireDatasheetDao().count())
    }

    @Test
    fun defaultResolverDelegatesToRealAndroidContentResolver() = runBlocking {
        val source = File.createTempFile("fabcalc-default-resolver-", ".pdf", context.cacheDir).apply { writeBytes(PDF) }
        try {
            val defaultRepository = WireDatasheetRepository(context, database)
            val result = defaultRepository.stagePdf(Uri.fromFile(source)) {}
            assertTrue(result is WireStageResult.Staged)
            val staged = (result as WireStageResult.Staged).file
            assertTrue(staged.readBytes().contentEquals(PDF))
            assertTrue("default ContentResolver path must not delete source", source.isFile)
        } finally {
            source.delete()
        }
    }

    @Test
    fun nonPdfContentIsRejectedWithoutRowOrStagedFile() = runBlocking {
        val invalid = "not a pdf".toByteArray()
        val (uri, source) = content.add(invalid)
        val before = stagedNames()
        assertEquals(WireStageResult.NotPdf, repository.stagePdf(uri) {})
        assertEquals(before, stagedNames())
        assertTrue(source.isFile)
        assertTrue(source.readBytes().contentEquals(invalid))
        assertEquals(0, database.wireDatasheetDao().count())
    }

    @Test
    fun missingAndInvalidUrisLeaveNoStageOrRoomRow() = runBlocking {
        val before = stagedNames()
        assertEquals(WireStageResult.NotPdf, repository.stagePdf(Uri.parse("content://missing/document")) {})
        assertEquals(WireStageResult.NotPdf, repository.stagePdf(Uri.parse("not a uri")) {})
        assertEquals(before, stagedNames())
        assertEquals(0, database.wireDatasheetDao().count())
    }

    @Test
    fun streamThrowingMidCopyCleansStageAndLeavesSourceAndRoomUntouched() = runBlocking {
        val (uri, source) = content.add(PDF, throwAfterBytes = 9)
        val before = stagedNames()
        assertTrue(repository.stagePdf(uri) {} is WireStageResult.Failed)
        assertEquals(before, stagedNames())
        assertTrue(source.isFile)
        assertTrue(source.readBytes().contentEquals(PDF))
        assertEquals(0, database.wireDatasheetDao().count())
    }

    @Test
    fun shortStreamComparedWithReportedLengthCleansStageAndLeavesSourceAndRoomUntouched() = runBlocking {
        val (uri, source) = content.add(PDF, maxBytes = 8, reportedLength = PDF.size.toLong())
        val before = stagedNames()
        assertTrue(repository.stagePdf(uri) {} is WireStageResult.Failed)
        assertEquals(before, stagedNames())
        assertTrue(source.isFile)
        assertTrue(source.readBytes().contentEquals(PDF))
        assertEquals(0, database.wireDatasheetDao().count())
    }

    @Test
    fun duplicateKeepsExistingRowAndPdfAndDeletesOnlyNewStage() = runBlocking {
        val (firstUri, firstSource) = content.add(PDF)
        val first = repository.stagePdf(firstUri) {} as WireStageResult.Staged
        val existing = repository.commit(first.file, first.sha256, tags())
        val existingFile = repository.fileFor(existing.fileName)
        val oldBytes = existingFile.readBytes()
        val (secondUri, secondSource) = content.add(PDF)
        val before = stagedNames()

        val duplicate = repository.stagePdf(secondUri) {}

        assertTrue(duplicate is WireStageResult.Duplicate)
        assertEquals(existing.id, (duplicate as WireStageResult.Duplicate).existing.id)
        assertEquals(1, database.wireDatasheetDao().count())
        assertTrue(existingFile.isFile)
        assertTrue(existingFile.readBytes().contentEquals(oldBytes))
        assertEquals(before, stagedNames())
        assertTrue(firstSource.isFile && secondSource.isFile)
    }

    @Test
    fun roomInsertFailureDeletesStagedFileAndLeavesNoRow() = runBlocking {
        val (uri, _) = content.add(PDF)
        val staged = repository.stagePdf(uri) {} as WireStageResult.Staged
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_wire_insert BEFORE INSERT ON wire_datasheets " +
                "BEGIN SELECT RAISE(ABORT, 'injected insert failure'); END"
        )

        try {
            repository.commit(staged.file, staged.sha256, tags())
            fail("Room insert should fail")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Expected: failed insert must clean its unreferenced staged PDF.
        }

        assertFalse("failed commit must not leave a staged orphan", staged.file.exists())
        assertEquals("failed insert must not create a row", 0, database.wireDatasheetDao().count())
    }

    @Test
    fun cancellationDuringInsertPropagatesWithoutCleaningStagedFile() = runBlocking {
        val (uri, _) = content.add(PDF)
        val staged = repository.stagePdf(uri) {} as WireStageResult.Staged
        val cancelingRepository = WireDatasheetRepository(context, database, content) {
            throw CancellationException("injected cancellation")
        }

        try {
            cancelingRepository.commit(staged.file, staged.sha256, tags())
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            // The startup sweep, not commit failure cleanup, handles interrupted work.
        }

        assertTrue("cancellation preserves staged file for the startup sweep", staged.file.isFile)
        assertEquals(0, database.wireDatasheetDao().count())
    }

    @Test
    fun deleteExistingFileAndRowRemovesBoth() = runBlocking {
        val entry = insertEntry("delete-${UUID.randomUUID()}.pdf")
        val file = repository.fileFor(entry.fileName).apply { writeBytes(PDF) }
        repository.delete(entry)
        assertEquals(0, database.wireDatasheetDao().count())
        assertFalse(file.exists())
    }

    @Test
    fun deletingRowWithMissingPdfStillRemovesRow() = runBlocking {
        val entry = insertEntry("missing-${UUID.randomUUID()}.pdf")
        repository.delete(entry)
        assertEquals(0, database.wireDatasheetDao().count())
        assertFalse(repository.fileFor(entry.fileName).exists())
    }

    @Test
    fun fileDeleteReturningFalseAbortsAndPreservesRow() = runBlocking {
        val entry = insertEntry("nonempty-${UUID.randomUUID()}.pdf")
        val path = repository.fileFor(entry.fileName)
        assertTrue(path.mkdir())
        val child = File(path, "keep").apply { writeText("prevents File.delete") }

        var failed = false
        try {
            repository.delete(entry)
        } catch (_: IOException) {
            failed = true
        }

        assertTrue("failed filesystem delete must reach caller", failed)
        assertEquals("row must remain when file deletion fails", 1, database.wireDatasheetDao().count())
        assertTrue("non-empty directory remains", path.isDirectory)
        child.delete()
        path.delete()
        Unit
    }

    @Test
    fun rowDeleteFailureAfterFileRemovalRestoresFileAndPreservesRow() = runBlocking {
        val entry = insertEntry("rollback-${UUID.randomUUID()}.pdf")
        val file = repository.fileFor(entry.fileName).apply { writeBytes(PDF) }
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_wire_delete BEFORE DELETE ON wire_datasheets " +
                "BEGIN SELECT RAISE(ABORT, 'injected delete failure'); END"
        )

        var failed = false
        try {
            repository.delete(entry)
        } catch (_: Throwable) {
            failed = true
        }

        assertTrue(failed)
        assertEquals("failed row delete keeps metadata", 1, database.wireDatasheetDao().count())
        assertTrue("removed PDF is restored when Room deletion fails", file.isFile)
        assertTrue(file.readBytes().contentEquals(PDF))
    }

    @Test
    fun cleanupDeletesOldUnreferencedStageButKeepsNewerAndReferencedFiles() = runBlocking {
        val now = System.currentTimeMillis()
        val old = stagedFile().apply { writeBytes(PDF); setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1) }
        val fresh = stagedFile().apply { writeBytes(PDF); setLastModified(now) }
        val referenced = stagedFile().apply { writeBytes(PDF); setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1) }
        insertEntry(referenced.name)

        val deleted = repository.sweepStagedFiles(now)

        assertEquals(1, deleted)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(referenced.exists())
    }

    @Test
    fun cleanupSkipsDeletionWhenRoomReferenceCheckFails() = runBlocking {
        val now = System.currentTimeMillis()
        val old = stagedFile().apply { writeBytes(PDF); setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1) }
        database.openHelper.writableDatabase.execSQL("DROP TABLE wire_datasheets")

        assertEquals(0, repository.sweepStagedFiles(now))
        assertTrue("DB check failure must disable sweep", old.exists())
    }

    @Test
    fun cleanupMatchesStagingAndDeleteBackupNamesButRejectsMalformedNames() = runBlocking {
        val now = System.currentTimeMillis()
        val staleStage = stagedFile().apply {
            writeBytes(PDF)
            setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1)
        }
        val staleDeleteBackup = repository.deletionBackupFile().apply {
            writeBytes(PDF)
            setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1)
        }
        val referencedDeleteBackup = repository.deletionBackupFile().apply {
            writeBytes(PDF)
            setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1)
        }
        insertEntry(referencedDeleteBackup.name)
        val malformed = File(repository.directory(), ".staging-delete-not-a-uuid.pdf").apply {
            writeBytes(PDF)
            setLastModified(now - WireDatasheetRepository.STAGED_FILE_MAX_AGE_MILLIS - 1)
        }

        repository.sweepStagedFiles(now)
        assertFalse(staleStage.exists())
        assertFalse(staleDeleteBackup.exists())
        assertTrue("Room references protect deletion recovery copy", referencedDeleteBackup.exists())
        assertTrue("unknown staging names are not treated as temp files", malformed.exists())
    }

    private suspend fun insertEntry(fileName: String): WireDatasheet {
        val row = WireDatasheet(
            fileName = fileName,
            sha256 = UUID.randomUUID().toString().replace("-", ""),
            brand = "Test",
            productName = "Test wire",
            awsClassification = "ER70S-6",
            metalType = MetalType.CARBON_STEEL,
            process = WireProcess.SOLID_GMAW,
            wireSizeMm = 0.9,
            notes = null,
            createdAt = 1,
            updatedAt = 1,
        )
        return row.copy(id = database.wireDatasheetDao().insert(row))
    }

    private fun tags() = WireTags(brand = "Test", productName = "Test")
    private fun stagedNames() = repository.directory().listFiles().orEmpty()
        .filter { it.name.startsWith(WireDatasheetRepository.STAGED_FILE_PREFIX) }
        .map { it.name }.toSet()
    private fun stagedFile() = repository.stagedFile()

    private companion object {
        val PDF = "%PDF-1.4\nmock body\n%%EOF".toByteArray()
    }
}

/** Injectable I/O behavior; source files are real and never deleted by repository import. */
private class FakePdfContentAccess : PdfContentAccess {
    private data class Source(
        val file: File,
        val bytes: ByteArray,
        val throwAfterBytes: Int?,
        val maxBytes: Int?,
        val reportedLength: Long?,
    )

    private val sources = mutableMapOf<Uri, Source>()
    val sourceFiles get() = sources.values.map { it.file }

    fun add(
        bytes: ByteArray,
        throwAfterBytes: Int? = null,
        maxBytes: Int? = null,
        reportedLength: Long? = null,
    ): Pair<Uri, File> {
        val uri = Uri.parse("content://test/${UUID.randomUUID()}")
        val source = File.createTempFile("fabcalc-source-", ".pdf").apply { writeBytes(bytes) }
        sources[uri] = Source(source, bytes, throwAfterBytes, maxBytes, reportedLength)
        return uri to source
    }

    override fun openInputStream(uri: Uri): InputStream? {
        val source = sources[uri] ?: throw FileNotFoundException(uri.toString())
        val size = (source.maxBytes ?: source.bytes.size).coerceIn(0, source.bytes.size)
        return object : ByteArrayInputStream(source.bytes, 0, size) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (source.throwAfterBytes != null && pos >= source.throwAfterBytes) {
                    throw IOException("injected mid-copy failure")
                }
                val allowed = source.throwAfterBytes?.minus(pos)?.coerceAtLeast(0) ?: length
                return super.read(buffer, offset, minOf(length, allowed))
            }

            override fun read(): Int {
                if (source.throwAfterBytes != null && pos >= source.throwAfterBytes) {
                    throw IOException("injected mid-copy failure")
                }
                return super.read()
            }
        }
    }

    override fun length(uri: Uri): Long =
        sources[uri]?.let { it.reportedLength ?: it.maxBytes?.toLong() ?: it.bytes.size.toLong() }
            ?: throw FileNotFoundException(uri.toString())
}
