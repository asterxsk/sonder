# android-native/app/src/main/java/com/example/sonder/data/db/SonderDatabase.kt

- SonderDatabase · class · L6-L25 — @Database( entities = [ TargetEntity::class, GrantEntity::class, DebtEntity::class, LockoutEntity::class, HandEntity::class, DailyUsageEntity::class, ], version = 2, exportSchema = false, ) abstract class SonderDatabase : RoomDatabase()
- targetDao · method · L19-L19 — abstract fun targetDao(): TargetDao
- grantDao · method · L20-L20 — abstract fun grantDao(): GrantDao
- debtDao · method · L21-L21 — abstract fun debtDao(): DebtDao
- lockoutDao · method · L22-L22 — abstract fun lockoutDao(): LockoutDao
- handDao · method · L23-L23 — abstract fun handDao(): HandDao
- dailyUsageDao · method · L24-L24 — abstract fun dailyUsageDao(): DailyUsageDao
