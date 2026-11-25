package com.example.sonder.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.OnPrimaryMuted
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale

/**
 * The button's intent. These are HeroUI's colour names because they describe the
 * consequence rather than the paint: a [DANGER] button removes something, a [SUCCESS] one
 * finishes something, and neither of them means "red" or "green".
 */
enum class PixelButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER }

/** HeroUI's three sizes, resolved to a minimum height on §19's 48dp floor. */
enum class ButtonSize(internal val minHeight: Dp, internal val horizontal: Dp) {
    SM(PixelSpace.Target, PixelSpace.Base),
    MD(PixelSpace.Target + PixelSpace.Snug, PixelSpace.Room),
    LG(PixelSpace.Target + PixelSpace.Base, PixelSpace.Section),
}

/** HeroUI's width option: a button that hugs its label, or one that fills its row. */
enum class ButtonWidth { CONTENT, FULL }

private data class Tetrad(val fill: Color, val border: Color, val text: Color, val shadow: Color)

private fun colors(style: PixelButtonStyle, enabled: Boolean) = when (style) {
    PixelButtonStyle.PRIMARY -> if (enabled) {
        Tetrad(PixelPalette.Primary, PixelPalette.Primary, PixelPalette.Bg, PixelPalette.PrimaryDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
    PixelButtonStyle.SECONDARY -> if (enabled) {
        Tetrad(PixelPalette.Surface, PixelPalette.Border, PixelPalette.Text, PixelPalette.BorderDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
    PixelButtonStyle.SUCCESS -> if (enabled) {
        Tetrad(PixelPalette.Success, PixelPalette.Success, PixelPalette.Bg, PixelPalette.SuccessDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
    PixelButtonStyle.DANGER -> if (enabled) {
        Tetrad(PixelPalette.Danger, PixelPalette.Danger, PixelPalette.Bg, PixelPalette.DangerDark)
    } else {
        // The one style whose *disabled* form is not grey, because its disabled form is not
        // "switched off" — it is a refusal, and the red is the message. Two controls use it
        // and both are saying no to something: the gate's prompt for a chip that has not been
        // picked, and REMOVE LIMIT while the removal lock is running. Dressing those in the
        // shared grey is how the gate's prompt came out as an ordinary dead button rather
        // than as the thing telling the user what to do next.
        //
        // Still unmistakably not-pressable: the fill is dead panel rather than the bright
        // danger block of the enabled form, and there is no shadow to press into. Only the
        // frame and the label carry the red, at 4.6:1 on the fill — above the body floor,
        // which the muted grey was chosen for and red would not clear at a lighter weight.
        Tetrad(PixelPalette.Panel, PixelPalette.Danger, PixelPalette.Danger, Color.Transparent)
    }
}

/**
 * design_v3.md §6 buttons, with the HeroUI model's additions: [size] and [width] are
 * declared rather than improvised per call site, and [loading] is a real state.
 *
 * The pressed state is unchanged — translate 4dp into the shadow's footprint, drop the
 * shadow, step the label to OnPrimaryMuted so the action stays legible mid-press. Disabled
 * is a dead panel with muted text and no shadow.
 *
 * [loading] is not [enabled = false] with a spinner on top: a loading button keeps its
 * colour and its frame, refuses input, and swaps its label for a progress mark. The
 * distinction matters because the two say different things — disabled is "you cannot",
 * loading is "you did, and it is happening" — and a control that greys out while it works
 * reads as one that failed.
 *
 * 48dp min touch target at every size (§19); exposes a real Button role for TalkBack;
 * draws the keyboard/switch-access focus ring inside the frame (2dp FocusRing inset 4dp).
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PixelButtonStyle = PixelButtonStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    // Legacy sizing hooks. Screens that were tuned against the old flat button still pass
    // these; named sizes are the way forward, and an explicit value here still wins so no
    // existing layout shifts underneath a refactor it did not ask for.
    minHeight: Dp? = null,
    horizontalPadding: Dp? = null,
    size: ButtonSize = ButtonSize.MD,
    width: ButtonWidth = ButtonWidth.CONTENT,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val interactive = enabled && !loading

    // Memoized: the four colours are picked from a fixed palette by (style, enabled), so
    // rebuilding the Tetrad on every recomposition allocated a data class for nothing.
    val tetrad = remember(style, enabled) { colors(style, enabled) }
    val fill = tetrad.fill
    val border = tetrad.border
    val textColor = tetrad.text

    // §18: the press is 2–4 discrete frames — not an instant jump, and not a smooth
    // slide. Stepped easing quantises the 90ms travel to four frames so the button
    // lands on the shadow's footprint the way a sprite's press frame does.
    val offset by animateDpAsState(
        targetValue = if (pressed && interactive) PixelSpace.Tight else 0.dp,
        animationSpec = tween(
            durationMillis = PixelMotion.PressMillis,
            easing = PixelMotion.Stepped,
        ),
        label = "pixelButtonPress",
    )
    val showShadow = !pressed && enabled && !loading

    Box(
        modifier = modifier
            .then(if (width == ButtonWidth.FULL) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = maxOf(minHeight ?: 0.dp, size.minHeight, PixelSpace.Target))
            .offset(x = offset, y = offset)
            .background(fill)
            .border(PixelSpace.Stroke, border)
            .pixelFocusRing(visible = focused)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            )
            // A loading button is busy, not disabled: it says so, so TalkBack does not
            // announce a live control whose tap does nothing.
            .semantics { if (!interactive) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = if (horizontalPadding != null) horizontalPadding else size.horizontal, vertical = PixelSpace.Base)
                // Shadow padding is OUTSIDE the text box; clickable covers the
                // whole button including the shadow strip, so edge taps register.
                .padding(
                    end = if (showShadow) PixelSpace.Tight else 0.dp,
                    bottom = if (showShadow) PixelSpace.Tight else 0.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Icon over label, never beside it. Beside, the two share whatever width the
            // button has — and the chips row puts four of these across one screen, so each
            // button is around 70dp and an icon on the same line as the label squeezes both.
            // Stacked, the chip takes the full width it needs and the label sits under it at
            // its natural size; the button just grows taller, which costs nothing here.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PixelSpace.Tight),
            ) {
                if (loading) {
                    // The chip loader, not a material spinner: the whole app's wait state
                    // is one chip stepping through five frames, and a second animation
                    // vocabulary inside a button would be the only place it appears.
                    PixelLoader(size = LoadingMarkSize)
                } else {
                    // A graphic that belongs to the label rather than to the button — the
                    // chips are the icon for the bet, and a stake with no chip above it reads
                    // as a plain text button. Drawn by the caller so this kit stays free of
                    // any one screen's vocabulary; the press transform above already moves it
                    // with the text, which is what makes it sit *in* the button rather than on
                    // it.
                    leading?.invoke()
                    androidx.compose.material3.Text(
                        text = text,
                        style = PixelTypeScale.Button,
                        fontFamily = PixelFont,
                        // Never wrapped. A label that breaks onto a second line grows the
                        // button and, worse, breaks unevenly: at this width "2 MIN" fits and
                        // "10 MIN" does not, so the row of four chips ends up two different
                        // heights with no reason the eye can read. The caller narrows the
                        // padding instead.
                        maxLines = 1,
                        softWrap = false,
                        color = when {
                            !enabled -> PixelPalette.Muted
                            pressed && style == PixelButtonStyle.PRIMARY -> OnPrimaryMuted
                            else -> textColor
                        },
                    )
                }
            }
        }
    }
}

/** The loading mark inside a button — one step down from the standalone loader. */
private val LoadingMarkSize = 20.dp
