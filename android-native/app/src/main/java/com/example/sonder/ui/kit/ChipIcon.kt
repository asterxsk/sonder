package com.example.sonder.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelPalette
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Which chip a stake is played with.
 *
 * A chip is a bet drawn on the bank, and the four stakes are four denominations — so each one
 * wears its own hue rather than the whole row sharing one. The ladder is the ordinary casino
 * order ([ChipTier.TEN] blue, [ChipTier.TWENTY] orange, [ChipTier.TWENTY_FIVE] green,
 * [ChipTier.HIGH] purple for the whole bank), which is a convention players already read
 * without being told: the chips sort themselves by size before any number is read.
 *
 * Orange stands in for the $20 chip, which is yellow in a real rack. On this screen the chip
 * sits on a button that is the palette's amber, and yellow-on-amber separates by almost
 * nothing — so the tier keeps its rank and takes the hue that stays legible.
 */
enum class ChipTier(val hue: Color, val label: String) {
    TEN(PixelPalette.ChipTen, "2 MIN"),
    TWENTY(PixelPalette.ChipTwenty, "5 MIN"),
    TWENTY_FIVE(PixelPalette.ChipTwentyFive, "10 MIN"),
    HIGH(PixelPalette.ChipHigh, "ALL IN");

    companion object {
        /**
         * The tier for a stake of [stakeMillis].
         *
         * A stake below the smallest chip takes the smallest chip: the table's own seat is
         * dealt from an empty bank, and a bank clamped to a few seconds is still a bet the row
         * has to draw something for. A stake *between* two chips takes the one at or below it,
         * so the icon never claims a bigger bet than the one being played. Never null — the
         * row always has four buttons in it, so every one of them has a chip.
         */
        fun forStake(stakeMillis: Long): ChipTier =
            listOf(120_000L to TEN, 300_000L to TWENTY, 600_000L to TWENTY_FIVE)
                .lastOrNull { (millis, _) -> millis <= stakeMillis }
                ?.second
                ?: TEN
    }
}

/**
 * The chip, drawn on the same 24-unit pixel grid the rest of the app's art uses.
 *
 * Everything is quantised to [GRID] units and drawn as filled squares rather than as paths, so
 * the edge stays hard at any size: a circle drawn with arcs would come out anti-aliased and
 * would be the one soft shape in a pixel interface. At 64dp the unit is under a device pixel,
 * which is the point — the steps disappear and it reads as a struck chip, while a screenshot
 * taken at 3x still shows them.
 *
 * [state] picks the fill:
 *  - [ChipState.READY] is the chip as it is on the rack: its tier hue, cream inserts.
 *  - [ChipState.SELECTED] inverts it to a dark body with the hue left in the rim and the
 *    inserts' edge. The button underneath a selected chip is already the palette's amber, and
 *    a coloured chip on it is two colours arguing; a dark one reads as chosen at a glance.
 *  - [ChipState.DEAD] is the chip at an empty bank: desaturated and dimmed toward the panel,
 *    but the inserts keep their contrast, so a chip nobody can afford is still legibly a chip
 *    rather than a dark blob.
 */
enum class ChipState { READY, SELECTED, DEAD }

/** The logical grid the chip is drawn on; every measurement below is in these units. */
private const val GRID = 24

/** Radius of the outer edge of the rim, in grid units. */
private const val OUTER = 11.8f

/** Radius of the inner edge of the rim — the boundary between rim and centre field. */
private const val INNER = 8.2f

/** How many inserts sit around the rim. */
private const val SPOTS = 8

/** Half-width of an insert, in degrees either side of its spoke. */
private const val SPREAD = 9.5f

/**
 * A stake's chip: [tier] picks the colours, [state] picks the treatment.
 *
 * @param size the drawn square. 64dp is the size the gate uses beside a stake's label.
 */
@Composable
fun ChipIcon(
    tier: ChipTier,
    state: ChipState,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val colors = chipColors(tier, state)
    Canvas(modifier = modifier.size(size)) { drawChip(colors, size.toPx()) }
}

/** The four colours one chip is drawn from. */
private data class ChipColors(
    val rim: Color,
    val insert: Color,
    val field: Color,
    val accent: Color,
)

/**
 * The colours for one (tier, state) pair.
 *
 * Pure and cheap — four colours from a fixed palette — so it is called straight through on
 * every recomposition rather than remembered; the chip is redrawn on the canvas either way,
 * and a remembered value here would only add a lookup to compare against.
 */
private fun chipColors(tier: ChipTier, state: ChipState): ChipColors = when (state) {
    ChipState.READY -> ChipColors(
        rim = tier.hue,
        insert = PixelPalette.ChipInsert,
        field = tier.hue,
        accent = PixelPalette.ChipInsert,
    )

    // Inverted: the hue survives only where it can still be seen against the amber button,
    // which is the rim and the thin ring around the field.
    ChipState.SELECTED -> ChipColors(
        rim = tier.hue,
        insert = PixelPalette.ChipInsert,
        field = PixelPalette.Bg,
        accent = tier.hue,
    )

    ChipState.DEAD -> ChipColors(
        rim = dim(tier.hue),
        insert = dim(PixelPalette.ChipInsert),
        field = dim(tier.hue),
        accent = dim(PixelPalette.ChipInsert),
    )
}

/**
 * An unaffordable chip: pulled toward the panel's hue and darkened, which is the same
 * treatment a disabled [PixelButton] gets.
 *
 * The mix is weighted so the *lightness* separates more than the hue does. A dead chip only
 * has to say "not this one", and a colour that keeps its hue but loses its brightness reads as
 * switched off; a colour that loses its hue but keeps its brightness reads as a different
 * chip, which is the opposite of the message.
 */
private fun dim(color: Color): Color {
    val grey = (color.red + color.green + color.blue) / 3f
    val keepHue = 0.32f
    val toward = PixelPalette.Panel
    val shade = 0.66f
    fun channel(own: Float, greyed: Float, target: Float): Float {
        val desaturated = own * keepHue + greyed * (1f - keepHue)
        return (desaturated * (1f - shade) + target * shade).coerceIn(0f, 1f)
    }
    return Color(
        red = channel(color.red, grey, toward.red),
        green = channel(color.green, grey, toward.green),
        blue = channel(color.blue, grey, toward.blue),
    )
}

/**
 * One chip, cell by cell.
 *
 * The grid is walked once and each cell is assigned to the outermost band it belongs to, so
 * the bands cannot overdraw each other in the wrong order: the rim is laid down first, then
 * the inserts that sit on it, then the thin accent ring, then the field the number would sit
 * on. Drawing them as four passes over the same predicate would need the passes to be wound
 * back-to-front by hand, which is the kind of ordering that breaks silently the next time a
 * band is added.
 */
private fun DrawScope.drawChip(colors: ChipColors, sizePx: Float) {
    val cell = sizePx / GRID
    val center = (GRID - 1) / 2f

    for (y in 0 until GRID) {
        for (x in 0 until GRID) {
            val dx = x - center
            val dy = y - center
            val radius = hypot(dx, dy)

            val color = when {
                // The rim, with its inserts.
                radius > INNER && radius <= OUTER ->
                    if (onSpoke(dx, dy)) colors.insert else colors.rim

                // A one-cell ring separating the rim from the field, so the field does not
                // read as a hole punched through the chip.
                radius > INNER - 0.9f && radius <= INNER -> colors.accent

                radius <= INNER - 0.9f -> colors.field

                else -> null
            } ?: continue

            drawRect(
                color = color,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell + 0.5f, cell + 0.5f),
            )
        }
    }
}

/**
 * Whether a cell lies on one of the [SPOTS] spokes.
 *
 * The distance is to the *nearest* spoke, and both moduli have to be the floored kind: a cell
 * just below the horizontal has a negative angle while the cell above it does not, and if the
 * sign is kept the two land on different spokes and half the wheel goes missing.
 */
private fun onSpoke(dx: Float, dy: Float): Boolean {
    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val step = 360f / SPOTS
    var nearest = Float.MAX_VALUE
    for (k in 0 until SPOTS) {
        val offset = mod360(degrees - k * step + 180f) - 180f
        nearest = minOf(nearest, abs(offset))
    }
    return nearest <= SPREAD
}

/** Floored modulo: the result is always in [0, 360) whatever the sign of [value]. */
private fun mod360(value: Float): Float = ((value % 360f) + 360f) % 360f
