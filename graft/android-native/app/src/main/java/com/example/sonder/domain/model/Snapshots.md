# android-native/app/src/main/java/com/example/sonder/domain/model/Snapshots.kt

- GrantSnapshot · class · L7-L11 — data class GrantSnapshot( val packageName: String, val endAtMillis: Long, val lastSeenMillis: Long, )
- LockoutSnapshot · class · L13-L23 — data class LockoutSnapshot( val packageName: String, val untilMillis: Long, val debtMillis: Long, /** * Mirror of `LockoutEntity.reason` as a plain string so the domain stays free of * persistence types: "DEBT" | "DAILY_CAP", with null (rows written before reasons * existed) read as DEBT. The constants live on EnforcementRepository. */ val reason: String? = null, )
