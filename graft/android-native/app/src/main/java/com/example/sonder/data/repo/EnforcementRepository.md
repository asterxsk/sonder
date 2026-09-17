# android-native/app/src/main/java/com/example/sonder/data/repo/EnforcementRepository.kt

- EnforcementRepository · class · L26-L171 — @Singleton class EnforcementRepository @Inject constructor( private val targetDao: TargetDao, private val grantDao: GrantDao, private val debtDao: DebtDao, private val lockoutDao: LockoutDao, private val handDao: HandDao, )
- observeState · method · L36-L50 — fun observeState(packageName: String): Flow<EnforcementState>
- currentDebt · method · L52-L53 — suspend fun currentDebt(packageName: String): Long
- isTargetEnabled · method · L56-L57 — suspend fun isTargetEnabled(packageName: String): Boolean
- hasActiveGrant · method · L60-L71 — suspend fun hasActiveGrant( packageName: String, nowMillis: Long = System.currentTimeMillis(), ): Boolean
- grantRemainingMillis · method · L74-L80 — suspend fun grantRemainingMillis( packageName: String, nowMillis: Long = System.currentTimeMillis(), ): Long
- lockoutRemainingMillis · method · L83-L89 — suspend fun lockoutRemainingMillis( packageName: String, nowMillis: Long = System.currentTimeMillis(), ): Long
- canPlay · method · L92-L93 — suspend fun canPlay(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean
- onHandResult · method · L99-L133 — suspend fun onHandResult( packageName: String, outcome: HandOutcome, nowMillis: Long = System.currentTimeMillis(), ): Long?
- recordLastSeen · method · L136-L138 — suspend fun recordLastSeen(packageName: String, nowMillis: Long = System.currentTimeMillis())
- evaluateAbsence · method · L144-L156 — suspend fun evaluateAbsence(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean
- revokeGrant · method · L159-L161 — suspend fun revokeGrant(packageName: String, reason: String)
- purgeExpired · method · L164-L167 — suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis())
- toSnapshot · method · L169-L169 — private fun GrantEntity.toSnapshot()
- toSnapshot · method · L170-L170 — private fun LockoutEntity.toSnapshot()
