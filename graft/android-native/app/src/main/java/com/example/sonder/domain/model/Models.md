# android-native/app/src/main/java/com/example/sonder/domain/model/Models.kt

- Suit · enum · L5-L5 — enum class Suit
- Rank · enum · L7-L12 — enum class Rank(val display: String, val baseValue: Int)
- Card · class · L14-L24 — data class Card(val rank: Rank, val suit: Suit)
- suitGlyph · method · L18-L23 — private fun suitGlyph(s: Suit): String
- Hand · class · L26-L51 — data class Hand(val cards: List<Card>)
- HandOutcome · enum · L53-L53 — enum class HandOutcome
