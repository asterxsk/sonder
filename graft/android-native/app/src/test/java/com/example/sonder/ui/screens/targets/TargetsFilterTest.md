# android-native/app/src/test/java/com/example/sonder/ui/screens/targets/TargetsFilterTest.kt

- TargetsFilterTest · class · L16-L132 — class TargetsFilterTest
- `all tab includes enabled and disabled launchable apps` · method · L21-L25 — @Test fun `all tab includes enabled and disabled launchable apps`()
- `limited tab includes enabled apps only` · method · L27-L31 — @Test fun `limited tab includes enabled apps only`()
- `query matches labels and package names case-insensitively` · method · L33-L50 — @Test fun `query matches labels and package names case-insensitively`()
- `nonmatching query produces the no-results state` · method · L52-L60 — @Test fun `nonmatching query produces the no-results state`()
- `loading is distinct from an empty loaded package list` · method · L62-L69 — @Test fun `loading is distinct from an empty loaded package list`()
- `a failed load is its own state and never reads as no launchable apps` · method · L71-L82 — @Test fun `a failed load is its own state and never reads as no launchable apps`()
- `empty limited filter is its own state` · method · L84-L88 — @Test fun `empty limited filter is its own state`()
- `enabled count ignores the tab and the query` · method · L90-L94 — @Test fun `enabled count ignores the tab and the query`()
- `merge keeps package-name keys and defaults unknown apps to off` · method · L96-L106 — @Test fun `merge keeps package-name keys and defaults unknown apps to off`()
- `ui recomputes only when picks tab or normalized query change` · method · L108-L131 — @OptIn(ExperimentalCoroutinesApi::class) @Test fun `ui recomputes only when picks tab or normalized query change`()
