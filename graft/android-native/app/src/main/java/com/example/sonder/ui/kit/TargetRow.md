# android-native/app/src/main/java/com/example/sonder/ui/kit/TargetRow.kt

- TargetRowPending · class · L42-L47 — data class TargetRowPending( val label: String, val description: String, val tone: Color, val onClick: () -> Unit, )
- TargetRow · function · L61-L147 — @Composable fun TargetRow( appName: String, packageName: String, onEdit: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier, pending: TargetRowPending? = null, iconGlyph: String = "▣", iconBitmap: Bitmap? = null, )
- RowGlyphButton · function · L154-L177 — @Composable private fun RowGlyphButton( glyph: String, description: String, tone: Color, onClick: () -> Unit, modifier: Modifier = Modifier, )
- IconBoxSize · variable · L180-L180 — private val IconBoxSize = 40.dp
- DelayedStripMinWidth · variable · L183-L183 — private val DelayedStripMinWidth = 112.dp
