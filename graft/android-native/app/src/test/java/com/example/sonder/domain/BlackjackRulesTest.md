# android-native/app/src/test/java/com/example/sonder/domain/BlackjackRulesTest.kt

- BlackjackRulesTest · class · L14-L146 — class BlackjackRulesTest
- card · method · L16-L16 — private fun card(rank: Rank, suit: Suit = Suit.SPADES)
- `hard hand totals correctly` · method · L20-L23 — @Test fun `hard hand totals correctly`()
- `soft ace counts as 11 when it fits` · method · L25-L29 — @Test fun `soft ace counts as 11 when it fits`()
- `ace degrades to 1 to avoid bust` · method · L31-L35 — @Test fun `ace degrades to 1 to avoid bust`()
- `double ace hand counts one as 11` · method · L37-L41 — @Test fun `double ace hand counts one as 11`()
- `bust detection` · method · L43-L46 — @Test fun `bust detection`()
- `natural blackjack detection` · method · L48-L52 — @Test fun `natural blackjack detection`()
- `deal distributes four cards in order` · method · L56-L66 — @Test fun `deal distributes four cards in order`()
- `deck has 52 unique cards` · method · L68-L73 — @Test fun `deck has 52 unique cards`()
- `dealer stands on hard 17` · method · L77-L84 — @Test fun `dealer stands on hard 17`()
- `dealer stands on soft 17` · method · L86-L93 — @Test fun `dealer stands on soft 17`()
- `dealer draws until 17 from low total` · method · L95-L100 — @Test fun `dealer draws until 17 from low total`()
- `player bust always loses` · method · L104-L109 — @Test fun `player bust always loses`()
- `dealer bust with live player wins` · method · L111-L116 — @Test fun `dealer bust with live player wins`()
- `higher total wins` · method · L118-L134 — @Test fun `higher total wins`()
- `equal total pushes` · method · L136-L145 — @Test fun `equal total pushes`()
