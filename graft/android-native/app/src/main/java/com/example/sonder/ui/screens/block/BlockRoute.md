# android-native/app/src/main/java/com/example/sonder/ui/screens/block/BlockRoute.kt

- BlockRoute · function · L55-L232 — @Composable fun BlockRoute( targetPackage: String, onAccessGranted: () -> Unit, onDismiss: () -> Unit, viewModel: BlackjackViewModel = hiltViewModel(), )
- PixelDivider · function · L234-L242 — @Composable private fun PixelDivider()
- CardSlot · class · L245-L245 — private data class CardSlot(val card: Card, val faceDown: Boolean)
- HandRow · function · L252-L278 — @Composable private fun HandRow(label: String, cards: List<CardSlot>, total: Int?)
- PlayingCard · function · L283-L328 — @Composable fun PlayingCard(card: Rank, suit: Suit, faceDown: Boolean)
- PlayingCard · function · L331-L334 — @Composable fun PlayingCard(card: com.example.sonder.domain.model.Card, faceDown: Boolean)
- suitGlyph · function · L336-L341 — private fun suitGlyph(suit: Suit): String
