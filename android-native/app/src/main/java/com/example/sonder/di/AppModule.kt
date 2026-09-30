package com.example.sonder.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sonder.data.db.HandDao
import com.example.sonder.data.db.SonderDatabase
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TimeBankDao
import com.example.sonder.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * v1 → v2: per-app policy overrides (nullable, so existing targets inherit the
     * AccessPolicy defaults), a lockout reason, and the per-day usage table. Plain
     * ALTER/CREATE keeps existing targets, grants and lockouts.
     *
     * Frozen history. These four tables do not exist at version 5 — this step is kept so a
     * device that has been offline since v1 still walks forward through the same path every
     * other install did, rather than being told its database is too old.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE targets ADD COLUMN winGrantMillis INTEGER")
            db.execSQL("ALTER TABLE targets ADD COLUMN lossDebtMillis INTEGER")
            db.execSQL("ALTER TABLE targets ADD COLUMN maxDebtMillis INTEGER")
            db.execSQL("ALTER TABLE targets ADD COLUMN absenceRevokeMillis INTEGER")
            db.execSQL("ALTER TABLE targets ADD COLUMN dailyCapMillis INTEGER")
            db.execSQL("ALTER TABLE lockouts ADD COLUMN reason TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS daily_usage (" +
                    "packageName TEXT NOT NULL, " +
                    "epochDay INTEGER NOT NULL, " +
                    "grantedMillis INTEGER NOT NULL, " +
                    "PRIMARY KEY(packageName))",
            )
        }
    }

    /**
     * v2 → v3: the per-target block scope.
     *
     * `NOT NULL DEFAULT 'WHOLE_APP'` rather than a nullable column, because a target has no
     * third state to express and every existing row meant "gate the whole app" — which is
     * exactly what this default says, so the migration and Room's expected schema agree
     * without anything having to read null as a synonym for the default at every call site.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE targets ADD COLUMN blockScope TEXT NOT NULL DEFAULT 'WHOLE_APP'",
            )
        }
    }

    /**
     * v3 → v4: the hand history gains what the row needs to be worth reading — the app's
     * label and the two hands as they were dealt.
     *
     * All three default to the empty string, which is the honest answer for every hand
     * played before this: the label was never stored and the cards were never kept, and a
     * non-empty default (`'WIN'`, say) would put a hand on the screen that nobody played.
     * The Stats row reads blank as "not recorded" and drops that line.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE hands ADD COLUMN label TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE hands ADD COLUMN playerCards TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE hands ADD COLUMN dealerCards TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v4 → v5: the debt model becomes a time bank.
     *
     * One table replaces four. Grants, debt, lockouts and the daily tally were four
     * descriptions of the same thing — how much time the user has in this app — and keeping
     * them in step was the source of most of the enforcement logic. `time_bank` says it once:
     * unspent millis, the local day they belong to, when the app was last billed, and when
     * the bank last ran out (which is what the new removal lock is measured from).
     *
     * Nothing is carried across. A live grant was an absolute `endAtMillis` that ran on wall
     * clock and a debt was time owed for waiting out; neither is expressible as unspent
     * foreground time, and inventing a balance from one would hand the user a number the old
     * model never promised. Every target starts with an empty bank — the same state a fresh
     * install has, and the state the gate exists to resolve.
     *
     * `targets` is rebuilt rather than altered because four columns have to go: SQLite can
     * drop a column in modern versions, but Room's expected schema has them reordered around
     * `maxMillis`, and a table rebuild is the one form whose result matches what the exported
     * schema says regardless of the SQLite the device happens to ship. `hands` is rebuilt for
     * the same reason — `debtAfterMillis` becomes `bankAfterMillis` and gains `stakeMillis`,
     * and there is no ALTER that renames a column in place.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS time_bank (" +
                    "packageName TEXT NOT NULL, " +
                    "remainingMillis INTEGER NOT NULL, " +
                    "epochDay INTEGER NOT NULL, " +
                    "lastSeenMillis INTEGER NOT NULL, " +
                    "emptySinceMillis INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(packageName))",
            )
            db.execSQL("DROP TABLE IF EXISTS grants")
            db.execSQL("DROP TABLE IF EXISTS debt")
            db.execSQL("DROP TABLE IF EXISTS lockouts")
            db.execSQL("DROP TABLE IF EXISTS daily_usage")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS targets_new (" +
                    "packageName TEXT NOT NULL, " +
                    "label TEXT NOT NULL, " +
                    "enabled INTEGER NOT NULL, " +
                    "createdAtMillis INTEGER NOT NULL, " +
                    "maxMillis INTEGER NOT NULL DEFAULT 3600000, " +
                    "blockScope TEXT NOT NULL DEFAULT 'WHOLE_APP', " +
                    "PRIMARY KEY(packageName))",
            )
            db.execSQL(
                "INSERT INTO targets_new (packageName, label, enabled, createdAtMillis, blockScope) " +
                    "SELECT packageName, label, enabled, createdAtMillis, blockScope FROM targets",
            )
            db.execSQL("DROP TABLE targets")
            db.execSQL("ALTER TABLE targets_new RENAME TO targets")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS hands_new (" +
                    "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "packageName TEXT NOT NULL, " +
                    "outcome TEXT NOT NULL, " +
                    "stakeMillis INTEGER NOT NULL DEFAULT 0, " +
                    "bankAfterMillis INTEGER NOT NULL DEFAULT 0, " +
                    "playedAtMillis INTEGER NOT NULL, " +
                    "label TEXT NOT NULL DEFAULT '', " +
                    "playerCards TEXT NOT NULL DEFAULT '', " +
                    "dealerCards TEXT NOT NULL DEFAULT '')",
            )
            // Old rows carry no stake and no bank — the debt model had neither — so both
            // default to zero and the Stats row simply says less about those hands.
            db.execSQL(
                "INSERT INTO hands_new " +
                    "(id, packageName, outcome, playedAtMillis, label, playerCards, dealerCards) " +
                    "SELECT id, packageName, outcome, playedAtMillis, label, playerCards, dealerCards " +
                    "FROM hands",
            )
            db.execSQL("DROP TABLE hands")
            db.execSQL("ALTER TABLE hands_new RENAME TO hands")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SonderDatabase =
        Room.databaseBuilder(context, SonderDatabase::class.java, "sonder.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides fun provideTargetDao(db: SonderDatabase): TargetDao = db.targetDao()
    @Provides fun provideTimeBankDao(db: SonderDatabase): TimeBankDao = db.timeBankDao()
    @Provides fun provideHandDao(db: SonderDatabase): HandDao = db.handDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
