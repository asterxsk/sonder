package com.example.sonder.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SonderDatabase =
        Room.databaseBuilder(context, SonderDatabase::class.java, "sonder.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideTargetDao(db: SonderDatabase): TargetDao = db.targetDao()
    @Provides fun provideGrantDao(db: SonderDatabase): GrantDao = db.grantDao()
    @Provides fun provideDebtDao(db: SonderDatabase): DebtDao = db.debtDao()
    @Provides fun provideLockoutDao(db: SonderDatabase): LockoutDao = db.lockoutDao()
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
