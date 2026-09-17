# android-native/app/src/test/java/com/example/sonder/ui/screens/home/HomeStateTest.kt

- HomeStateTest · class · L16-L208 — class HomeStateTest
- target · method · L20-L25 — private fun target(packageName: String, label: String, enabled: Boolean = true)
- grant · method · L27-L28 — private fun grant(packageName: String, endAtMillis: Long)
- lockout · method · L30-L31 — private fun lockout(packageName: String, untilMillis: Long)
- row · method · L33-L33 — private fun HomeState.row(packageName: String)
- `excludes disabled targets` · method · L35-L45 — @Test fun `excludes disabled targets`()
- `maps grants and lockouts to the right state at the supplied time` · method · L47-L62 — @Test fun `maps grants and lockouts to the right state at the supplied time`()
- `formats positive remaining time as MM SS` · method · L64-L74 — @Test fun `formats positive remaining time as MM SS`()
- `remaining text changes for timestamps one second apart` · method · L76-L87 — @Test fun `remaining text changes for timestamps one second apart`()
- `idle and expired rows carry no remaining text` · method · L89-L106 — @Test fun `idle and expired rows carry no remaining text`()
- `orders locked then granted then idle, then by label` · method · L108-L127 — @Test fun `orders locked then granted then idle, then by label`()
- `summary picks the locked row over a live grant` · method · L129-L141 — @Test fun `summary picks the locked row over a live grant`()
- `summary picks a live grant over idle rows` · method · L143-L153 — @Test fun `summary picks a live grant over idle rows`()
- `no targets produces the no targets summary` · method · L155-L166 — @Test fun `no targets produces the no targets summary`()
- `limited but inactive targets produce the idle summary with the enabled count` · method · L168-L182 — @Test fun `limited but inactive targets produce the idle summary with the enabled count`()
- `expired grant and lockout fall back to the idle summary` · method · L184-L194 — @Test fun `expired grant and lockout fall back to the idle summary`()
- `disabling every target leaves nothing limited` · method · L196-L207 — @Test fun `disabling every target leaves nothing limited`()
