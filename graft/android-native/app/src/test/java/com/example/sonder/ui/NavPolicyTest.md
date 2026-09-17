# android-native/app/src/test/java/com/example/sonder/ui/NavPolicyTest.kt

- NavPolicyTest · class · L13-L82 — class NavPolicyTest
- `home clears a secondary destination` · method · L15-L18 — @Test fun `home clears a secondary destination`()
- `home from home keeps only the root` · method · L20-L23 — @Test fun `home from home keeps only the root`()
- `a secondary tab with only home on the stack adds it` · method · L25-L28 — @Test fun `a secondary tab with only home on the stack adds it`()
- `a secondary tab replaces another secondary tab` · method · L30-L36 — @Test fun `a secondary tab replaces another secondary tab`()
- `selecting the visible tab does not duplicate it` · method · L38-L45 — @Test fun `selecting the visible tab does not duplicate it`()
- `back from a secondary destination reveals home` · method · L47-L50 — @Test fun `back from a secondary destination reveals home`()
- `back at the root leaves the stack unchanged` · method · L52-L57 — @Test fun `back at the root leaves the stack unchanged`()
- `tabOf reads the top destination and defaults to home` · method · L59-L64 — @Test fun `tabOf reads the top destination and defaults to home`()
- `every tab round-trips through its key` · method · L66-L71 — @Test fun `every tab round-trips through its key`()
- `select and back never mutate the input stack` · method · L73-L81 — @Test fun `select and back never mutate the input stack`()
