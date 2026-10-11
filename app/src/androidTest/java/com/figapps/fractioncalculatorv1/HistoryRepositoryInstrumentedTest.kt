package com.figapps.fractioncalculatorv1

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.figapps.fractioncalculatorv1.history.HistoryDatabase
import com.figapps.fractioncalculatorv1.history.HistoryEntry
import com.figapps.fractioncalculatorv1.history.HistoryRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses in-memory Room to isolate data while exercising real DAO queries and invalidation flows. */
@RunWith(AndroidJUnit4::class)
class HistoryRepositoryInstrumentedTest {
    private lateinit var database: HistoryDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        repository = HistoryRepository(database.historyDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertIsObservedAndCanBeFoundById() = runBlocking {
        val (updates, collector) = observe()
        try {
            assertEquals(emptyList<HistoryEntry>(), next(updates))
            repository.record(draft("first"))
            val inserted = next(updates).single()
            assertEquals("first", inserted.result)
            assertNotNull(database.historyDao().findById(inserted.id))
            assertNull(database.historyDao().findById(Long.MAX_VALUE))
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun identicalTimestampsListNewestInsertionFirst() = runBlocking {
        val dao = database.historyDao()
        val timestamp = 123456L
        val older = dao.insert(entry("older", timestamp))
        val newer = dao.insert(entry("newer", timestamp))

        val ordered = repository.entries.first()

        // Defined contract: when timestamps tie, the later inserted record is first.
        assertEquals(listOf(newer, older), ordered.map { it.id })
    }

    @Test
    fun singleDeleteAndClearEachEmitUpdatedLists() = runBlocking {
        val (updates, collector) = observe()
        try {
            assertEquals(emptyList<HistoryEntry>(), next(updates))
            val firstId = database.historyDao().insert(entry("first", 100L))
            assertEquals(listOf(firstId), next(updates).map { it.id })

            val secondId = database.historyDao().insert(entry("second", 200L))
            assertEquals(listOf(secondId, firstId), next(updates).map { it.id })

            val first = assertNotNullAndGet(database.historyDao().findById(firstId))
            repository.delete(first)
            assertEquals(listOf(secondId), next(updates).map { it.id })
            assertNull(database.historyDao().findById(firstId))

            repository.clear()
            assertEquals(emptyList<HistoryEntry>(), next(updates))
            assertEquals(emptyList<HistoryEntry>(), repository.entries.first())
        } finally {
            collector.cancelAndJoin()
        }
    }

    private fun CoroutineScope.observe(): Pair<Channel<List<HistoryEntry>>, Job> {
        val updates = Channel<List<HistoryEntry>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.entries.collect { updates.send(it) }
        }
        return updates to collector
    }

    private suspend fun next(updates: Channel<List<HistoryEntry>>): List<HistoryEntry> =
        withTimeout(5_000) { updates.receive() }

    private fun draft(result: String) = com.figapps.fractioncalculatorv1.history.HistoryDraft(
        toolId = "test",
        toolName = "Test",
        inputs = "input",
        result = result,
        detail = "detail",
    )

    private fun entry(result: String, timestamp: Long) = HistoryEntry(
        toolId = "test",
        toolName = "Test",
        inputs = "input",
        result = result,
        detail = "detail",
        timestamp = timestamp,
    )

    private fun assertNotNullAndGet(entry: HistoryEntry?): HistoryEntry {
        assertNotNull(entry)
        return requireNotNull(entry)
    }
}
