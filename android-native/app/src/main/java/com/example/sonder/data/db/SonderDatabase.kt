package com.example.sonder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TargetEntity::class,
        TimeBankEntity::class,
        HandEntity::class,
    ],
    version = 5,
    // The schema is exported so the hand-written migrations have a checked reference:
    // without it, a column added to the DDL but not to the migration compiles, passes every
    // test, and only fails on a device upgrading from the previous version.
    exportSchema = true,
)
abstract class SonderDatabase : RoomDatabase() {
    abstract fun targetDao(): TargetDao
    abstract fun timeBankDao(): TimeBankDao
    abstract fun handDao(): HandDao
}
