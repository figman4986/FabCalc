package com.figapps.fractioncalculatorv1.wire

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.figapps.fractioncalculatorv1.history.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.IOException
import java.util.UUID

/** Outcome of staging a picked PDF into internal storage. */
sealed interface WireStageResult {
    /** The PDF was copied (and hashed) and is awaiting tags. */
    data class Staged(val file: File, val sha256: String) : WireStageResult

    /** The same PDF is already in the library. */
    data class Duplicate(val existing: WireDatasheet) : WireStageResult

    /** The picked file did not start with the %PDF magic. */
    data object NotPdf : WireStageResult

    data class Failed(val message: String) : WireStageResult
}

/**
 * Owns the wire-datasheet library: the Room rows and the PDF files under
 * `filesDir/wireDatasheets`. Importing stages a copy and hashes it while copying; the row is only
 * written once the user confirms tags, and a duplicate hash aborts before that.
 */
class WireDatasheetRepository(
    private val context: Context,
    private val database: HistoryDatabase,
    private val contentAccess: PdfContentAccess = AndroidPdfContentAccess(context.contentResolver),
    private val insertWireRow: suspend (WireDatasheet) -> Long = { database.wireDatasheetDao().insert(it) },
) {
    private val dao = database.wireDatasheetDao()

    val entries: Flow<List<WireDatasheet>> = dao.observeAll()

    fun directory(): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun fileFor(fileName: String): File = File(directory(), fileName)

    /**
     * Sniffs the %PDF header, then stream-copies the pick to a staged file while hashing it.
     * [onProgress] gets 0..1, or null when the size is unknown (indeterminate).
     */
    suspend fun stagePdf(source: Uri, onProgress: (Float?) -> Unit): WireStageResult =
        withContext(Dispatchers.IO) {
            if (!looksLikePdf(source)) return@withContext WireStageResult.NotPdf

            val target = stagedFile()
            val total = contentLength(source)
            val digest = WireHashing.digest()
            try {
                val input = contentAccess.openInputStream(source)
                    ?: throw FileNotFoundException("Could not open the selected file.")
                input.use { stream ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                            done += read
                            if (total > 0 && done > total) {
                                throw java.io.IOException("The selected file exceeded its reported size.")
                            }
                            onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
                        }
                        if (total > 0 && done < total) {
                            throw java.io.EOFException("The selected file ended before its reported size.")
                        }
                    }
                }
            } catch (throwable: Throwable) {
                target.delete()
                return@withContext WireStageResult.Failed(throwable.message ?: "Import failed.")
            }

            onProgress(1f)
            val sha256 = digest.hexDigest()
            val existing = duplicateCheck(sha256, dao.findByHash(sha256))
            if (existing != null) {
                // Keep exactly one copy: drop the staged file and report the existing entry.
                target.delete()
                return@withContext WireStageResult.Duplicate(existing)
            }
            WireStageResult.Staged(target, sha256)
        }

    /** Commits a staged file as a library entry with the confirmed tags. */
    suspend fun commit(staged: File, sha256: String, tags: WireTags): WireDatasheet =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entity = WireDatasheet(
                fileName = staged.name,
                sha256 = sha256,
                brand = tags.brand.trim(),
                productName = tags.productName.trim(),
                awsClassification = tags.awsClassification.trim().uppercase(),
                metalType = tags.metalType,
                process = tags.process,
                wireSizeMm = tags.wireSizeMm,
                notes = tags.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            )
            try {
                entity.copy(id = insertWireRow(entity))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                runCatching { staged.delete() }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }

    /** Deletes a staged file the user cancelled. */
    suspend fun discard(staged: File) = withContext(Dispatchers.IO) { staged.delete() }

    /** Metadata-only update; the PDF is untouched. */
    suspend fun updateTags(entity: WireDatasheet, tags: WireTags) = withContext(Dispatchers.IO) {
        dao.update(entity.withTags(tags, System.currentTimeMillis()))
    }

    /** Deletes the PDF first, restoring it if the Room row deletion fails. */
    suspend fun delete(entity: WireDatasheet) = withContext(Dispatchers.IO) {
        val file = fileFor(entity.fileName)
        val wasDirectory = file.isDirectory
        val backup = if (file.isFile) deletionBackupFile().also { file.copyTo(it) } else null

        if (file.exists() && !file.delete()) {
            backup?.delete()
            throw IOException("Could not delete the datasheet PDF. The library entry was kept.")
        }

        try {
            database.withTransaction { dao.delete(entity) }
        } catch (failure: Throwable) {
            try {
                when {
                    backup != null -> backup.copyTo(file, overwrite = true)
                    wasDirectory -> file.mkdirs()
                }
                backup?.delete()
            } catch (restoreFailure: Throwable) {
                failure.addSuppressed(restoreFailure)
            }
            throw failure
        }

        backup?.delete()
    }

    /** Removes only aged, unreferenced staging files. Any database failure disables the sweep. */
    suspend fun sweepStagedFiles(nowMillis: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            val referenced = try {
                dao.referencedFileNames().toSet()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return@withContext 0
            }

            directory().listFiles().orEmpty().count { file ->
                file.isFile &&
                    file.name.matches(STAGED_FILE_PATTERN) &&
                    file.name !in referenced &&
                    nowMillis - file.lastModified() >= STAGED_FILE_MAX_AGE_MILLIS &&
                    file.delete()
            }
        }

    fun stagedFile(): File = File(directory(), "$STAGED_FILE_PREFIX${UUID.randomUUID()}.pdf")

    internal fun deletionBackupFile(): File =
        File(directory(), "${STAGED_FILE_PREFIX}delete-${UUID.randomUUID()}.pdf")

    companion object {
        private const val DIRECTORY = "wireDatasheets"
        internal const val STAGED_FILE_PREFIX = ".staging-"
        internal const val STAGED_FILE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
        private val STAGED_FILE_PATTERN = Regex("^\\.staging-(?:delete-)?[0-9a-fA-F-]{36}\\.pdf$")
    }

    private fun looksLikePdf(source: Uri): Boolean = runCatching {
        contentAccess.openInputStream(source)?.use { stream ->
            val header = ByteArray(4)
            var filled = 0
            while (filled < 4) {
                val read = stream.read(header, filled, 4 - filled)
                if (read <= 0) break
                filled += read
            }
            filled == 4 && String(header, Charsets.US_ASCII) == "%PDF"
        } ?: false
    }.getOrDefault(false)

    private fun contentLength(source: Uri): Long = runCatching { contentAccess.length(source) }.getOrDefault(-1L)
}

/** Minimal seam for Android document input; default implementation delegates to ContentResolver. */
interface PdfContentAccess {
    fun openInputStream(uri: Uri): InputStream?
    fun length(uri: Uri): Long
}

private class AndroidPdfContentAccess(private val resolver: android.content.ContentResolver) : PdfContentAccess {
    override fun openInputStream(uri: Uri): InputStream? = resolver.openInputStream(uri)

    override fun length(uri: Uri): Long =
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
}

/**
 * Pure duplicate decision: the existing row that [sha256] collides with, or null when the file is
 * new. Kept out of the repository so the "never import the same PDF twice" rule is testable
 * without Android.
 */
fun duplicateCheck(sha256: String, existing: WireDatasheet?): WireDatasheet? =
    existing?.takeIf { it.sha256.equals(sha256, ignoreCase = true) }
