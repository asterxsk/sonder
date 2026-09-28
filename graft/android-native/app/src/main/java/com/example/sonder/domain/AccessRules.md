# android-native/app/src/main/java/com/example/sonder/domain/AccessRules.kt

- AccessRules · class · L7-L14 — data class AccessRules( val winGrantMillis: Long = AccessPolicy.WIN_GRANT_MILLIS, val lossDebtMillis: Long = AccessPolicy.LOSS_DEBT_MILLIS, val maxDebtMillis: Long = AccessPolicy.MAX_DEBT_MILLIS, val absenceRevokeMillis: Long = AccessPolicy.ABSENCE_REVOKE_MILLIS, /** Total access this app may be granted per local day; null means unlimited. */ val dailyCapMillis: Long? = null, )
