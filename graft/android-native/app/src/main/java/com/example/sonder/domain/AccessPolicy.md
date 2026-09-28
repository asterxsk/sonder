# android-native/app/src/main/java/com/example/sonder/domain/AccessPolicy.kt

- AccessPolicy · class · L23-L129 — object AccessPolicy
- onHandResult · method · L36-L58 — fun onHandResult( outcome: HandOutcome, debtMillis: Long, nowMillis: Long, rules: AccessRules = AccessRules(), grantedTodayMillis: Long = 0L, zoneId: ZoneId = ZoneId.systemDefault(), ): PolicyResult
- grantForWin · method · L65-L87 — private fun grantForWin( rules: AccessRules, nowMillis: Long, grantedTodayMillis: Long, zoneId: ZoneId, ): PolicyResult
- lockoutUntil · method · L90-L90 — fun lockoutUntil(debtMillis: Long, nowMillis: Long): Long
- shouldRevokeForAbsence · method · L93-L97 — fun shouldRevokeForAbsence( grant: GrantSnapshot, nowMillis: Long, rules: AccessRules = AccessRules(), ): Boolean
- nextLocalMidnight · method · L100-L107 — fun nextLocalMidnight(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long
- isGrantActive · method · L110-L110 — fun isGrantActive(grant: GrantSnapshot, nowMillis: Long): Boolean
- canPlay · method · L113-L114 — fun canPlay(lockout: LockoutSnapshot?, nowMillis: Long): Boolean
- stateFor · method · L117-L128 — fun stateFor( packageName: String, enabled: Boolean, grant: GrantSnapshot?, lockout: LockoutSnapshot?, nowMillis: Long, ): EnforcementState
- PolicyResult · class · L131-L136 — data class PolicyResult( val debtMillis: Long, val grantedUntil: Long?, /** Set when the daily cap is spent: locked out until this epoch millis. */ val capLockoutUntil: Long? = null, )
