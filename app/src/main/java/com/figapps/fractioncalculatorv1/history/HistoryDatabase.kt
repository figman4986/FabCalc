package com.figapps.fractioncalculatorv1.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireDatasheetDao
import com.figapps.fractioncalculatorv1.wire.WireDatasheetSchema

/**
 * The app's local database.
 *
 *  - v2 added indices on `history` (timestamp, toolId).
 *  - v3 added the `wire_datasheets` table for the welding-wire library.
 *
 * Both upgrades are forward [Migration]s, never destructive fallbacks: the calculation history is
 * meant to be retained until the user clears it, so a schema bump must not wipe it.
 */
@Database(
    entities = [HistoryEntry::class, WireDatasheet::class],
    version = 3,
    exportSchema = true,
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    abstract fun wireDatasheetDao(): WireDatasheetDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        /** Adds the history indices introduced in schema v2. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_timestamp ON history(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_toolId ON history(toolId)")
            }
        }

        /**
         * Adds the wire-datasheet table introduced in schema v3. The SQL must match exactly what
         * Room generates for [WireDatasheet], or Room's post-migration validation throws.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                WireDatasheetSchema.ALL.forEach { statement -> db.execSQL(statement) }
            }
        }

        /** Every migration, in order — also used by the migration test. */
        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun get(context: Context): HistoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "history.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
