# android-native/app/src/main/java/com/example/sonder/data/db/Entities.kt

- TargetEntity · class · L7-L13 — @Entity(tableName = "targets") data class TargetEntity( @PrimaryKey val packageName: String, val label: String, val enabled: Boolean = true, val createdAtMillis: Long, )
- GrantEntity · class · L16-L23 — @Entity(tableName = "grants") data class GrantEntity( @PrimaryKey val packageName: String, val endAtMillis: Long, /** Last time the accessibility service saw this package in the foreground. */ val lastSeenMillis: Long, val revokedReason: String? = null, )
- DebtEntity · class · L26-L30 — @Entity(tableName = "debt") data class DebtEntity( @PrimaryKey val packageName: String, val debtMillis: Long, )
- LockoutEntity · class · L33-L37 — @Entity(tableName = "lockouts") data class LockoutEntity( @PrimaryKey val packageName: String, val untilMillis: Long, )
- HandEntity · class · L40-L47 — @Entity(tableName = "hands") data class HandEntity( @PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val outcome: String, // WIN, LOSE, PUSH val debtAfterMillis: Long, val playedAtMillis: Long, )
