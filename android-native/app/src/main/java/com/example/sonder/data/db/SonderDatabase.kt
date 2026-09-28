package com.example.sonder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TargetEntity::class,
        GrantEntity::class,
        DebtEntity::class,
        LockoutEntity::class,
        HandEntity::class,
        DailyUsageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SonderDatabase : RoomDatabase() {
    abstract fun targetDao(): TargetDao
    abstract fun grantDao(): GrantDao
    abstract fun debtDao(): DebtDao
    abstract fun lockoutDao(): LockoutDao
    abstract fun handDao(): HandDao
    abstract fun dailyUsageDao(): DailyUsageDao
}
