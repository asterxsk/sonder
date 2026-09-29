# android-native/app/src/test/java/com/example/sonder/ui/screens/targets/TargetsFilterTest.kt

- TargetsFilterTest · class · L21-L159 — class TargetsFilterTest
- `added targets are the enabled picks only` · method · L26-L29 — @Test fun `added targets are the enabled picks only`()
- `query is trimmed and lowercased once` · method · L31-L34 — @Test fun `query is trimmed and lowercased once`()
- `search matches labels and package names case-insensitively` · method · L36-L41 — @Test fun `search matches labels and package names case-insensitively`()
- `search keeps added and not-added rows alike` · method · L43-L48 — @Test fun `search keeps added and not-added rows alike`()
- `loading is distinct from an empty loaded package list` · method · L50-L57 — @Test fun `loading is distinct from an empty loaded package list`()
- `a failed load is its own state and never reads as no launchable apps` · method · L59-L66 — @Test fun `a failed load is its own state and never reads as no launchable apps`()
- `loaded apps with nothing enabled is the empty-list state` · method · L68-L71 — @Test fun `loaded apps with nothing enabled is the empty-list state`()
- `the targets list carries enabled picks only` · method · L73-L77 — @Test fun `the targets list carries enabled picks only`()
- `picker loading and failure mirror the enumeration state` · method · L79-L83 — @Test fun `picker loading and failure mirror the enumeration state`()
- `picker distinguishes no launchable apps from no results` · method · L85-L95 — @Test fun `picker distinguishes no launchable apps from no results`()
- `picker rows are the filtered launchable set` · method · L97-L101 — @Test fun `picker rows are the filtered launchable set`()
- `merge keeps package-name keys and defaults unknown apps to off` · method · L103-L113 — @Test fun `merge keeps package-name keys and defaults unknown apps to off`()
- `add only ever materialises a row, so it cannot overwrite stored overrides` · method · L115-L134 — @Test fun `add only ever materialises a row, so it cannot overwrite stored overrides`()
- `picker recomputes only when picks or the normalized query change` · method · L136-L158 — @OptIn(ExperimentalCoroutinesApi::class) @Test fun `picker recomputes only when picks or the normalized query change`()
