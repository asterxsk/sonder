package com.example.sonder.ui.kit

import com.example.sonder.domain.AccessPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The chips are the row of bets, one denomination each. What has to hold is that a stake maps
 * to a chip that is not another stake's chip, and that every stake the table can deal has one.
 */
class ChipTierTest {

    @Test
    fun `the stakes and the chips line up one for one, in order`() {
        // The mapping is positional in effect — the smallest stake wears the smallest chip —
        // so a stake added or reordered without a chip beside it shows up here rather than as
        // two buttons wearing the same colour on the gate.
        assertEquals(
            listOf(ChipTier.TEN, ChipTier.TWENTY, ChipTier.TWENTY_FIVE),
            AccessPolicy.CHIPS.map(ChipTier::forStake),
        )
    }

    @Test
    fun `the three chips are three different hues`() {
        val hues = ChipTier.entries.filter { it != ChipTier.HIGH }.map { it.hue }
        assertEquals("two chips share a hue", hues.size, hues.toSet().size)
    }

    @Test
    fun `the chips do not share a hue with the action button`() {
        // The winner chip once was the literal yellow of a $20 chip, which is this palette's
        // amber to within nothing — a chip and the button it sits on. The tier has to stay
        // distinguishable from the fill of a selected button.
        val amber = com.example.sonder.theme.PixelPalette.Primary
        ChipTier.entries.forEach { tier ->
            assertNotEquals("${tier.label} wears the button's own colour", amber, tier.hue)
        }
    }

    @Test
    fun `a stake between two chips takes the one at or below it`() {
        // The bank can be clamped to something that is not one of the four — a part-spent day,
        // a refill mid-session. The icon must not claim a bigger bet than the one being played.
        assertEquals(ChipTier.TEN, ChipTier.forStake(250_000L))
        assertEquals(ChipTier.TWENTY, ChipTier.forStake(599_999L))
    }

    @Test
    fun `a stake below the smallest chip still draws one`() {
        // The table's own seat is dealt from an empty bank for the smallest chip's worth, but
        // a bank clamped to less than that is still a stake the row has to draw something for.
        assertEquals(ChipTier.TEN, ChipTier.forStake(0L))
        assertEquals(ChipTier.TEN, ChipTier.forStake(1L))
    }

    @Test
    fun `the four tiers sort in stake order`() {
        assertEquals(
            listOf(
                ChipTier.TEN,
                ChipTier.TWENTY,
                ChipTier.TWENTY_FIVE,
                ChipTier.HIGH,
            ),
            ChipTier.entries.toList(),
        )
    }

    @Test
    fun `no tier is unnamed`() {
        ChipTier.entries.forEach { tier ->
            assertNull(
                "the ${tier.name} chip has no label",
                tier.label.takeIf { it.isBlank() },
            )
        }
    }
}
