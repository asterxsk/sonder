# android-native/app/src/test/java/com/example/sonder/ui/NavPolicyTest.kt

- NavPolicyTest · class · L14-L101 — class NavPolicyTest
- `home clears a secondary destination` · method · L16-L19 — @Test fun `home clears a secondary destination`()
- `home from home keeps only the root` · method · L21-L24 — @Test fun `home from home keeps only the root`()
- `a secondary tab with only home on the stack adds it` · method · L26-L29 — @Test fun `a secondary tab with only home on the stack adds it`()
- `a secondary tab replaces another secondary tab` · method · L31-L37 — @Test fun `a secondary tab replaces another secondary tab`()
- `selecting the visible tab does not duplicate it` · method · L39-L46 — @Test fun `selecting the visible tab does not duplicate it`()
- `back from a secondary destination reveals home` · method · L48-L51 — @Test fun `back from a secondary destination reveals home`()
- `back at the root leaves the stack unchanged` · method · L53-L58 — @Test fun `back at the root leaves the stack unchanged`()
- `tabOf reads the top destination and defaults to home` · method · L60-L65 — @Test fun `tabOf reads the top destination and defaults to home`()
- `every tab round-trips through its key` · method · L67-L72 — @Test fun `every tab round-trips through its key`()
- `select and back never mutate the input stack` · method · L74-L82 — @Test fun `select and back never mutate the input stack`()
- `the add picker keeps TARGETS lit` · method · L84-L87 — @Test fun `the add picker keeps TARGETS lit`()
- `a dock tap replaces the add picker` · method · L89-L95 — @Test fun `a dock tap replaces the add picker`()
- `back from the add picker drops it` · method · L97-L100 — @Test fun `back from the add picker drops it`()
