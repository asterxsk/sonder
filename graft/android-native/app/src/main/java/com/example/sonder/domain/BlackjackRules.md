# android-native/app/src/main/java/com/example/sonder/domain/BlackjackRules.kt

- BlackjackRules · class · L18-L67 — object BlackjackRules
- fullDeck · method · L20-L20 — fun fullDeck(): List<Card>
- shuffledDeck · method · L22-L22 — fun shuffledDeck(random: Random = Random.Default): List<Card>
- deal · method · L25-L33 — fun deal(deck: List<Card>): DealtHand
- draw · method · L35-L38 — fun draw(deck: List<Card>): Pair<Card, List<Card>>
- dealerPlay · method · L44-L55 — fun dealerPlay(hand: Hand, hole: Card, deck: List<Card>): DealerPlayResult
- settle · method · L58-L66 — fun settle(player: Hand, dealer: Hand): HandOutcome
- DealtHand · class · L69-L74 — data class DealtHand( val player: Hand, val dealerUp: Hand, val dealerHole: Card, val remainingDeck: List<Card>, )
- DealerPlayResult · class · L76-L79 — data class DealerPlayResult( val dealer: Hand, val remainingDeck: List<Card>, )
