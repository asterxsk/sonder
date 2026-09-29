package com.example.sonder.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sonder.data.db.DailyUsageDao
import com.example.sonder.data.db.DebtDao
import com.example.sonder.data.db.GrantDao
import com.example.sonder.data.db.HandDao
import com.example.sonder.data.db.LockoutDao
import com.example.sonder.data.db.SonderDatabase
import com.example.sonder.data.db.TargetDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SonderDatabase =
        Room.databaseBuilder(context, SonderDatabase::class.java, "sonder.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideTargetDao(db: SonderDatabase): TargetDao = db.targetDao()
    @Provides fun provideGrantDao(db: SonderDatabase): GrantDao = db.grantDao()
    @Provides fun provideDebtDao(db: SonderDatabase): DebtDao = db.debtDao()
    @Provides fun provideLockoutDao(db: SonderDatabase): LockoutDao = db.lockoutDao()
    @Provides fun provideHandDao(db: SonderDatabase): HandDao = db.handDao()
    @Provides fun provideDailyUsageDao(db: SonderDatabase): DailyUsageDao = db.dailyUsageDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
