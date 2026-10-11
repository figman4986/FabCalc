package com.figapps.fractioncalculatorv1.wire

/**
 * The DDL for the `wire_datasheets` table, kept as plain strings so the real migration and the
 * test that pins it down share one source of truth. Nothing here touches Android, so the schema
 * contract can be asserted from a plain JVM unit test; the end-to-end migration is covered by the
 * instrumented test.
 *
 * The statements must match what Room generates for [WireDatasheet], including the index names,
 * or Room's post-migration validation rejects the upgrade.
 */
internal object WireDatasheetSchema {

    const val TABLE = "wire_datasheets"

    /** Columns that hold no value for an unspecified size / absent note. */
    val NULLABLE_COLUMNS = setOf("wireSizeMm", "notes")

    val CREATE_TABLE: String =
        "CREATE TABLE IF NOT EXISTS `$TABLE` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`fileName` TEXT NOT NULL, " +
            "`sha256` TEXT NOT NULL, " +
            "`brand` TEXT NOT NULL, " +
            "`productName` TEXT NOT NULL, " +
            "`awsClassification` TEXT NOT NULL, " +
            "`metalType` TEXT NOT NULL, " +
            "`process` TEXT NOT NULL, " +
            "`wireSizeMm` REAL, " +
            "`notes` TEXT, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL)"

    /** Unique so the same PDF can never be stored twice. */
    val CREATE_SHA256_INDEX: String =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_${TABLE}_sha256` ON `$TABLE` (`sha256`)"

    val CREATE_METAL_TYPE_INDEX: String =
        "CREATE INDEX IF NOT EXISTS `index_${TABLE}_metalType` ON `$TABLE` (`metalType`)"

    /** Every statement the v2 → v3 migration runs, in order. */
    val ALL: List<String> = listOf(CREATE_TABLE, CREATE_SHA256_INDEX, CREATE_METAL_TYPE_INDEX)
}
