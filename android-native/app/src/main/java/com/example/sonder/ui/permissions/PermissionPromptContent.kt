package com.example.sonder.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel

/**
 * The on-open reminder: a pixel card over a dimmed screen, listing only the grants that are
 * still missing, one button each.
 *
 * This is the second half of the permission story and the less welcome one. The wizard is
 * the first, and it is a conversation; this lands on top of whatever the user was doing when
 * the audit noticed a grant had gone. So it stays a card rather than a screen: no dock, no
 * navigation, nothing to explore — a title, the reason in one line each, and two ways out.
 * Everything is drawn from the same kit the rest of the app uses ([PixelPanel]'s hard frame
 * and stepped corners, [PixelButton]'s amber fill and stepped press), because a reminder
 * that renders in the platform's own widgets reads as a system dialog rather than as this
 * app asking for something.
 *
 * The list is what it is given, in [SonderPermission]'s own order — accessibility, overlay,
 * usage access, notifications — which is the order the wizard asks in and the order they
 * matter in. A missing notification grant is already filtered out upstream if the user
 * declined it once; see `outstandingPermissions`.
 *
 * @param missing the grants still outstanding. The caller finishes the prompt rather than
 *   showing an empty card, so this is never empty in practice.
 * @param onGrant invoked with the permission whose button was tapped; the caller opens its
 *   settings page and re-audits on return, so the card stays up while it still has something
 *   to list and closes itself once the last outstanding grant lands.
 * @param onLater the way out that grants nothing.
 */
@Composable
fun PermissionPromptContent(
    missing: Set<SonderPermission>,
    onGrant: (SonderPermission) -> Unit,
    onLater: () -> Unit,
) {
    // Nothing to say: the caller is finishing. Rendered as an empty transparent frame rather
    // than a card, so the frame between the state changing and the finish() landing is not a
    // panel with no buttons in it.
    if (missing.isEmpty()) {
        // Opaque even here. The activity is translucent, so a transparent frame is a hole
        // onto the app behind it — and for the one frame before the first audit answers that
        // is the whole window, taps and all.
        Box(Modifier.fillMaxSize().background(PixelPalette.Bg))
        return
    }

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The app's own ground, opaque. The pixel world has no scrim tint and no blur —
            // see PixelConfirmDialog — so a card floating on an undimmed screen is not this
            // app's way of stopping someone; it is the platform dialog the rest of this file
            // was written to replace. The activity is translucent, so without this the target
            // list behind the card stays at full brightness and reads as still in charge.
            .background(PixelPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(PixelSpace.Section)
            .clickable(
                interactionSource = interaction,
                indication = null,
                // Swallows taps on the dim so they cannot reach the app underneath. Not a
                // dismissal: the card is asking for something the user asked to be reminded
                // about, and a stray tap is not an answer — the two buttons are.
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        PixelPanel(
            borderColor = PixelPalette.Primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The scroll belongs to the content, not to the panel. Hung on the panel it
            // moved the frame along with the rows: the top border, its stepped-corner nicks
            // and the hard shadow scrolled off the moment the card was taller than the
            // screen, leaving a dark block with two amber side edges and no frame at all.
            // Here the frame stays where a frame belongs and only the rows move under it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "▣ PERMISSIONS REQUIRED",
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PixelSpace.Snug))
                Text(
                    text = "Sonder cannot enforce your limits without these.\nGranting one opens its own settings page — come back and this list updates.",
                    style = MonoTypeScale.Body,
                    color = TextSoft,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(PixelSpace.Base))

                missing.forEach { permission ->
                    val copy = copyFor(permission)
                    PermissionRow(
                        title = copy.title,
                        why = copy.why,
                        onClick = { onGrant(permission) },
                    )
                    Spacer(Modifier.height(PixelSpace.Snug))
                }

                Spacer(Modifier.height(PixelSpace.Tight))
                PixelButton(
                    text = "LATER",
                    onClick = onLater,
                    style = PixelButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * One missing grant: what it is called in Settings, what breaks without it, and the button
 * that opens it.
 *
 * The button carries the row rather than sitting beside it, so the whole row is the tap
 * target — the pixel face at 10sp is a small word to hit, and the card may be listing three
 * of these.
 */
@Composable
private fun PermissionRow(
    title: String,
    why: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
    ) {
        Text(
            text = title,
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        Text(
            text = why,
            style = MonoTypeScale.Metadata,
            color = PixelPalette.Muted,
        )
        PixelButton(
            text = "GRANT",
            onClick = onClick,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
