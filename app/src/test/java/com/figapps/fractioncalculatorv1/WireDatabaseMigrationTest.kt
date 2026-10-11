package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireDatasheetSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the v2 → v3 migration DDL to the [WireDatasheet] entity.
 *
 * The end-to-end proof that the upgrade works on a real database is the instrumented
 * `WireDatabaseMigrationInstrumentedTest` (no device here, so it only compiles). This JVM test
 * catches the most common way a migration rots — the entity gaining or losing a column while the
 * migration SQL stays behind — and it does so offline, using only `java.lang.reflect`.
 */
class WireDatabaseMigrationTest {

    private val entityFields = entityFieldNames()

    @Test
    fun declaresAColumnForEveryEntityField() {
        for (name in entityFields) {
            assertTrue(
                "The v2→v3 DDL has no column for WireDatasheet.$name — the migration is stale.",
                declaredColumns().contains(name),
            )
        }
    }

    @Test
    fun declaresNoColumnsBeyondTheEntity() {
        assertEquals(entityFields.size, declaredColumns().size)
        assertEquals(entityFields, declaredColumns().toSet())
    }

    @Test
    fun theOptionalColumnsAreExactlyTheNullableOnes() {
        val nullable = declaredColumns().filterNot { isNotNull(it) }.toSet()
        assertEquals(WireDatasheetSchema.NULLABLE_COLUMNS, nullable)
    }

    @Test
    fun theIdColumnIsAnAutoIncrementIntegerPrimaryKey() {
        assertTrue(WireDatasheetSchema.CREATE_TABLE.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
    }

    @Test
    fun theHashIndexIsUniqueSoTheSamePdfCannotBeStoredTwice() {
        assertTrue(WireDatasheetSchema.CREATE_SHA256_INDEX.contains("UNIQUE INDEX"))
        assertTrue(WireDatasheetSchema.CREATE_SHA256_INDEX.contains("(`sha256`)"))
    }

    @Test
    fun theMetalTypeColumnIsIndexedForTheBrowseGrouping() {
        assertTrue(WireDatasheetSchema.CREATE_METAL_TYPE_INDEX.contains("(`metalType`)"))
        assertTrue(WireDatasheetSchema.CREATE_METAL_TYPE_INDEX.contains(WireDatasheetSchema.TABLE))
    }

    @Test
    fun theMigrationCreatesTheTableAndThenItsIndices() {
        assertEquals(3, WireDatasheetSchema.ALL.size)
        assertTrue(WireDatasheetSchema.ALL[0].startsWith("CREATE TABLE"))
        assertTrue(WireDatasheetSchema.ALL[1].contains("sha256"))
        assertTrue(WireDatasheetSchema.ALL[2].contains("metalType"))
    }

    @Test
    fun everyStatementIsIdempotent() {
        // "IF NOT EXISTS" everywhere, so re-running the migration cannot fail on an existing table.
        for (statement in WireDatasheetSchema.ALL) {
            assertTrue(statement, statement.contains("IF NOT EXISTS"))
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    /** Field names of the entity, read reflectively so the test needs no Kotlin reflection. */
    private fun entityFieldNames(): Set<String> =
        WireDatasheet::class.java.declaredFields
            // Skip compiler-generated members: Kotlin synthetics, a companion holder, and the
            // `$stable` field the Compose compiler adds to eligible classes.
            .filterNot { it.isSynthetic || it.name.startsWith("$") || it.name == "Companion" }
            .map { it.name }
            .toSet()

    /** Column names parsed out of the CREATE TABLE statement, in declaration order. */
    private fun declaredColumns(): List<String> =
        Regex("`([A-Za-z0-9_]+)`\\s+(INTEGER|TEXT|REAL|BLOB)")
            .findAll(WireDatasheetSchema.CREATE_TABLE)
            .map { it.groupValues[1] }
            .toList()

    private fun isNotNull(column: String): Boolean =
        Regex("`$column`\\s+(INTEGER|TEXT|REAL|BLOB)[^,)]*NOT NULL")
            .containsMatchIn(WireDatasheetSchema.CREATE_TABLE)
}
