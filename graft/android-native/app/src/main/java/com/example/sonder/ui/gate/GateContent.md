# android-native/app/src/main/java/com/example/sonder/ui/gate/GateContent.kt

- GateContent · function · L46-L82 — @Composable fun GateContent( label: String, state: TableState, lockoutRemainingMillis: Long, onDeal: () -> Unit, onHit: () -> Unit, onStand: () -> Unit, onAccessGranted: () -> Unit, onPlayAgain: () -> Unit, )
- LockoutBlocker · function · L84-L123 — @Composable private fun LockoutBlocker(label: String, remainingMillis: Long)
- BlackjackBlocker · function · L125-L290 — @Composable private fun BlackjackBlocker( label: String, state: TableState, onDeal: () -> Unit, onHit: () -> Unit, onStand: () -> Unit, onAccessGranted: () -> Unit, onPlayAgain: () -> Unit, )
- PixelDivider · function · L292-L300 — @Composable private fun PixelDivider()
- formatRemaining · function · L302-L305 — internal fun formatRemaining(millis: Long): String
- PlayingCard · function · L310-L358 — @Composable fun PlayingCard(card: Rank, suit: Suit, faceDown: Boolean)
- PlayingCard · function · L361-L364 — @Composable fun PlayingCard(card: com.example.sonder.domain.model.Card, faceDown: Boolean)
- suitGlyph · function · L366-L371 — private fun suitGlyph(suit: Suit): String
