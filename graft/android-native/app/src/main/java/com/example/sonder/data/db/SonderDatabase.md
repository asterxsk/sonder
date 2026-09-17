# android-native/app/src/main/java/com/example/sonder/data/db/SonderDatabase.kt

- SonderDatabase · class · L6-L23 — @Database( entities = [ TargetEntity::class, GrantEntity::class, DebtEntity::class, LockoutEntity::class, HandEntity::class, ], version = 1, exportSchema = false, ) abstract class SonderDatabase : RoomDatabase()
- targetDao · method · L18-L18 — abstract fun targetDao(): TargetDao
- grantDao · method · L19-L19 — abstract fun grantDao(): GrantDao
- debtDao · method · L20-L20 — abstract fun debtDao(): DebtDao
- lockoutDao · method · L21-L21 — abstract fun lockoutDao(): LockoutDao
- handDao · method · L22-L22 — abstract fun handDao(): HandDao
