package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * The card, in HeroUI's model: a compound surface made of parts that arrive in a fixed
 * order — header, content, footer — rather than one composable with a dozen booleans.
 *
 * design_v3 §5's frame language is unchanged underneath: zero radius, 2dp border, hard
 * offset shadow, 4dp stepped corners. What the HeroUI model contributes is the *vocabulary*:
 * a card states its surface, its padding and its intent, and the parts name themselves,
 * rather than each screen rebuilding a Column of Text with hand-picked sizes.
 *
 * Three surfaces, and the difference is structural rather than decorative:
 *
 * - [PixelCardVariant.SOLID] sits on the screen's ground. It is the only variant that casts
 *   the hard shadow, because it is the only one standing on the ground.
 * - [PixelCardVariant.SUBTLE] sits *inside* another card — a detail block nested in a panel.
 *   It takes the rail's quieter frame, no shadow, and the host's fill behind its corner
 *   nicks, so a nested card reads as one object inside another rather than as two cards
 *   claiming the same depth.
 * - [PixelCardVariant.OUTLINED] is the ground with a line around it. An empty state wants
 *   this: a filled card would look like content that failed to arrive.
 */
enum class PixelCardVariant {
    SOLID,
    SUBTLE,
    OUTLINED,
}

/** HeroUI's padding scale, resolved against the 4dp ramp this app is drawn on. */
enum class CardPadding {
    NONE,
    SM,
    MD,
    LG;

    internal val all: androidx.compose.ui.unit.Dp
        get() = when (this) {
            NONE -> 0.dp
            SM -> PixelSpace.Base
            MD -> PixelSpace.Room
            LG -> PixelSpace.Section
        }
}

/**
 * One card surface.
 *
 * [intent] is an optional accent frame for the cards carrying a state — amber for the step
 * you are on, green for a completion, red for a refusal — and null for an ordinary card,
 * where the frame stays the neutral structural border. Accent is never the only signal:
 * every caller that tints a card also says the same thing in words, so the colour is
 * reinforcement rather than information.
 *
 * [cutColor] is the fill behind the stepped corner nicks. It defaults to the screen ground,
 * which is right for a card standing on Bg; a [PixelCardVariant.SUBTLE] card nested in a
 * Surface pass must pass its host's fill, or its corners get cut with four black squares.
 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    variant: PixelCardVariant = PixelCardVariant.SOLID,
    padding: CardPadding = CardPadding.MD,
    intent: Color? = null,
    cutColor: Color = PixelPalette.Bg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = when (variant) {
        PixelCardVariant.SOLID -> PixelPalette.Surface
        PixelCardVariant.SUBTLE -> PixelPalette.Panel
        PixelCardVariant.OUTLINED -> PixelPalette.Bg
    }
    val frame = intent ?: when (variant) {
        PixelCardVariant.SOLID -> PixelPalette.Border
        PixelCardVariant.SUBTLE -> PixelPalette.BorderDark
        PixelCardVariant.OUTLINED -> PixelPalette.BorderDark
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill)
            .then(if (variant == PixelCardVariant.SOLID) Modifier.pixelShadow() else Modifier)
            .border(PixelSpace.Stroke, frame)
            // A nested card is already inside a frame; stepping its corners again would
            // put four more cuts inside the host's, so only the standing surfaces step.
            .then(
                if (variant == PixelCardVariant.SUBTLE) {
                    Modifier
                } else {
                    Modifier.pixelSteppedCorners(cutColor = cutColor)
                },
            )
            .padding(
                padding.all + if (variant == PixelCardVariant.SUBTLE) 0.dp else SteppedCornerInset,
            ),
    ) {
        content()
    }
}

/**
 * A title that claims the header's remaining width, so whatever follows it — a status
 * chip, a value — is pushed to the far edge where a header's second column belongs.
 *
 * Top-level rather than a member of [PixelCard] because Kotlin resolves a member extension
 * only through `with(PixelCard) { }`, and `PixelCard.LeadingTitle(...)` inside a RowScope
 * lambda does not compile. Inside `PixelCard.Header { }` this reads as one name.
 */
@Composable
fun RowScope.PixelCardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PixelPalette.Text,
    maxLines: Int = 1,
) {
    androidx.compose.material3.Text(
        text = text,
        style = PixelTypeScale.SectionTitle,
        fontFamily = PixelFont,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.weight(1f),
    )
}

/**
 * The card's parts. These are members of an object rather than nested classes because
 * Compose has no namespace syntax; `PixelCard.Header { }` at a call site reads the same as
 * HeroUI's `Card.Header`.
 */
object PixelCard {

    /**
     * The title row: a name on the left, whatever qualifies it on the right. The title takes
     * the space it needs and truncates, while trailing content keeps its intrinsic width —
     * without that, a long app name pushes a status chip off the card's edge.
     */
    @Composable
    fun Header(
        modifier: Modifier = Modifier,
        content: @Composable RowScope.() -> Unit,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
        ) {
            content()
        }
    }

    /** Supporting copy: body prose at the readable mono scale. */
    @Composable
    fun Description(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = TextSoft,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MonoTypeScale.Body,
            color = color,
            modifier = modifier,
        )
    }

    /** One line of metadata under the title — a package id, a timestamp. */
    @Composable
    fun Meta(
        text: String,
        modifier: Modifier = Modifier,
        accent: Boolean = false,
        color: Color = TextSoft,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MonoTypeScale.PackageId,
            color = if (accent) PixelPalette.Primary else color,
            modifier = modifier,
        )
    }

    /** The card's body, below the header and above the footer. */
    @Composable
    fun Content(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Column(
            modifier = modifier.fillMaxWidth().padding(top = PixelSpace.Base),
            content = content,
        )
    }

    /**
     * The card's actions, along the bottom. Flush to the card's own edges rather than
     * wrapped in a tinted strip: a footer band would be a third tone on a surface that
     * already has a fill and a frame, and this app separates a card's actions with space.
     */
    @Composable
    fun Footer(
        modifier: Modifier = Modifier,
        content: @Composable RowScope.() -> Unit,
    ) {
        Row(
            modifier = modifier.fillMaxWidth().padding(top = PixelSpace.Room),
            horizontalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
