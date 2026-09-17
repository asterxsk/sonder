# android-native/app/src/main/java/com/example/sonder/domain/AccessPolicy.kt

- AccessPolicy · class · L21-L76 — object AccessPolicy
- onHandResult · method · L28-L47 — fun onHandResult( outcome: HandOutcome, debtMillis: Long, nowMillis: Long, ): PolicyResult
- lockoutUntil · method · L50-L50 — fun lockoutUntil(debtMillis: Long, nowMillis: Long): Long
- shouldRevokeForAbsence · method · L53-L54 — fun shouldRevokeForAbsence(grant: GrantSnapshot, nowMillis: Long): Boolean
- isGrantActive · method · L57-L57 — fun isGrantActive(grant: GrantSnapshot, nowMillis: Long): Boolean
- canPlay · method · L60-L61 — fun canPlay(lockout: LockoutSnapshot?, nowMillis: Long): Boolean
- stateFor · method · L64-L75 — fun stateFor( packageName: String, enabled: Boolean, grant: GrantSnapshot?, lockout: LockoutSnapshot?, nowMillis: Long, ): EnforcementState
- PolicyResult · class · L78-L81 — data class PolicyResult( val debtMillis: Long, val grantedUntil: Long?, )
