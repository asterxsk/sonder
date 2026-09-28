# android-native/app/src/main/java/com/example/sonder/ui/screens/block/BlockRoute.kt

- BlockRoute · function · L53-L279 — @Composable fun BlockRoute( targetPackage: String, onAccessGranted: () -> Unit, onDismiss: () -> Unit, viewModel: BlackjackViewModel = hiltViewModel(), )
- PixelDivider · function · L281-L289 — @Composable private fun PixelDivider()
- formatAccess · function · L292-L295 — private fun formatAccess(millis: Long): String
- CardSlot · class · L298-L298 — private data class CardSlot(val card: Card, val faceDown: Boolean)
- HandRow · function · L305-L331 — @Composable private fun HandRow(label: String, cards: List<CardSlot>, total: Int?)
- PlayingCard · function · L336-L380 — @Composable fun PlayingCard(card: Rank, suit: Suit, faceDown: Boolean)
- PlayingCard · function · L383-L386 — @Composable fun PlayingCard(card: com.example.sonder.domain.model.Card, faceDown: Boolean)
- suitGlyph · function · L388-L393 — private fun suitGlyph(suit: Suit): String
- CardWidth · variable · L399-L399 — private val CardWidth = 52.dp
- CardHeight · variable · L400-L400 — private val CardHeight = 74.dp
