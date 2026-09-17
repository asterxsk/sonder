# android-native/app/src/main/java/com/example/sonder/domain/model/Snapshots.kt

- GrantSnapshot · class · L7-L11 — data class GrantSnapshot( val packageName: String, val endAtMillis: Long, val lastSeenMillis: Long, )
- LockoutSnapshot · class · L13-L17 — data class LockoutSnapshot( val packageName: String, val untilMillis: Long, val debtMillis: Long, )
