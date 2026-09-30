package com.example.sonder.platform.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Optional means optional: what the wizard and the on-open reminder still ask about. */
class SonderPermissionTest {

    private val all = SonderPermission.entries.toSet()

    @Test
    fun `only notifications may be declined`() {
        // The other three are the enforcement itself; a skip on any of them would leave the
        // wizard able to finish with an app that cannot block anything.
        assertEquals(
            setOf(SonderPermission.NOTIFICATIONS),
            SonderPermission.entries.filter { it.optional }.toSet(),
        )
    }

    @Test
    fun `an unskipped miss is still outstanding`() {
        val outstanding = outstandingPermissions(all, emptySet())

        assertEquals(all, outstanding)
    }

    @Test
    fun `a declined optional permission is not outstanding`() {
        val outstanding = outstandingPermissions(all, setOf(SonderPermission.NOTIFICATIONS.name))

        assertEquals(all - SonderPermission.NOTIFICATIONS, outstanding)
        assertFalse(SonderPermission.NOTIFICATIONS in outstanding)
    }

    @Test
    fun `a required permission cannot be skipped out of the way`() {
        // The skip list is storage, and storage can say anything — a stale flag naming
        // accessibility must not be read as "the user is fine without the gate".
        val outstanding = outstandingPermissions(
            all,
            setOf(
                SonderPermission.ACCESSIBILITY.name,
                SonderPermission.OVERLAY.name,
                SonderPermission.USAGE_ACCESS.name,
            ),
        )

        assertEquals(all, outstanding)
    }

    @Test
    fun `a granted permission is not outstanding whatever the skip list says`() {
        // outstandingPermissions only ever removes; it never adds a permission back, so a
        // skip recorded for something the user then granted cannot resurrect it.
        val outstanding = outstandingPermissions(
            setOf(SonderPermission.NOTIFICATIONS),
            setOf(SonderPermission.NOTIFICATIONS.name),
        )

        assertTrue(outstanding.isEmpty())
    }

    @Test
    fun `stored names map back to the entries that still exist`() {
        assertEquals(
            setOf(SonderPermission.NOTIFICATIONS),
            skippedPermissionsNamed(setOf(SonderPermission.NOTIFICATIONS.name)),
        )
        // A name this build dropped is not resurrected as an unknown entry.
        assertEquals(emptySet<SonderPermission>(), skippedPermissionsNamed(setOf("CAMERA")))
    }
}
