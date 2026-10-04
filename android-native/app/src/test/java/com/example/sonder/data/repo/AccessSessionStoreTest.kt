package com.example.sonder.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessSessionStoreTest {
    private val t0 = 1_700_000_000_000L

    @Test
    fun `returning before sixty seconds resumes the session`() {
        val store = AccessSessionStore()
        store.grant("app", 5 * 60_000L)
        store.consume("app", 30_000L)
        store.leaveActive(t0)

        assertEquals(AccessEntry.RESUMED, store.enter("app", t0 + 59_999L))
        assertEquals(4 * 60_000L + 30_000L, store.sessions.value["app"]?.remainingMillis)
        assertNull(store.sessions.value["app"]?.awaySinceMillis)
    }

    @Test
    fun `returning at sixty seconds is denied and clears the session`() {
        val store = AccessSessionStore()
        store.grant("app", 5 * 60_000L)
        store.consume("app", 30_000L)
        store.leaveActive(t0)

        assertEquals(AccessEntry.DENIED, store.enter("app", t0 + 60_000L))
        assertNull(store.sessions.value["app"])
    }

    @Test
    fun `another package cannot inherit the session`() {
        val store = AccessSessionStore()
        store.grant("app.a", 2 * 60_000L)

        assertEquals(AccessEntry.DENIED, store.enter("app.b", t0))
        assertEquals("app.a", store.sessions.value["app.a"]?.packageName)
    }

    @Test
    fun `leaving parks only the named package when one is excepted`() {
        val store = AccessSessionStore()
        store.grant("app.a", 2 * 60_000L)
        store.grant("app.b", 2 * 60_000L)

        store.leaveActive(t0, except = "app.b")

        assertEquals(t0, store.sessions.value["app.a"]?.awaySinceMillis)
        assertNull(store.sessions.value["app.b"]?.awaySinceMillis)
    }
}
