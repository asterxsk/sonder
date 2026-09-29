# android-native/app/src/test/java/com/example/sonder/domain/GateDeciderTest.kt

- GateDeciderTest · class · L12-L115 — class GateDeciderTest
- grant · method · L18-L21 — private fun grant( endAt: Long = t0 + win5, lastSeen: Long = t0, )
- lockout · method · L23-L23 — private fun lockout(until: Long = t0 + 30_000L)
- `non-target packages always pass` · method · L25-L30 — @Test fun `non-target packages always pass`()
- `active grant lets the app run` · method · L32-L38 — @Test fun `active grant lets the app run`()
- `expired grant passes through to gating` · method · L40-L47 — @Test fun `expired grant passes through to gating`()
- `absence beyond the window revokes the grant` · method · L49-L53 — @Test fun `absence beyond the window revokes the grant`()
- `absence within the window keeps the grant` · method · L55-L59 — @Test fun `absence within the window keeps the grant`()
- `absence boundary is exclusive at exactly sixty seconds` · method · L61-L65 — @Test fun `absence boundary is exclusive at exactly sixty seconds`()
- `active lockout shows the lockout blocker instead of the table` · method · L67-L73 — @Test fun `active lockout shows the lockout blocker instead of the table`()
- `expired lockout gates again` · method · L75-L81 — @Test fun `expired lockout gates again`()
- `lockout wins over a stale grant row` · method · L83-L89 — @Test fun `lockout wins over a stale grant row`()
- `enabled target without grant or lockout gates` · method · L91-L94 — @Test fun `enabled target without grant or lockout gates`()
- `active grant beats an active lockout row` · method · L96-L103 — @Test fun `active grant beats an active lockout row`()
- decide · method · L105-L114 — private fun decide( targetEnabled: Boolean, grant: GrantSnapshot?, lockout: LockoutSnapshot?, ): GateDecision
