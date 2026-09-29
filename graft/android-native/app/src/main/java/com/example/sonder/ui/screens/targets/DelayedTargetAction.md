# android-native/app/src/main/java/com/example/sonder/ui/screens/targets/DelayedTargetAction.kt

- TargetActionKind · enum · L9-L12 — enum class TargetActionKind(val holdMillis: Long)
- PendingTargetAction · class · L18-L22 — data class PendingTargetAction( val packageName: String, val kind: TargetActionKind, val remainingMillis: Long, )
- remainingSeconds · function · L25-L26 — fun PendingTargetAction.remainingSeconds(): Long
- formatHoldClock · function · L29-L32 — fun formatHoldClock(seconds: Long): String
- label · function · L35-L35 — fun PendingTargetAction.label(): String
- tick · function · L42-L45 — fun PendingTargetAction.tick(tickMillis: Long = 1_000L): PendingTargetAction?
- startPendingAction · function · L48-L49 — fun startPendingAction(packageName: String, kind: TargetActionKind): PendingTargetAction
