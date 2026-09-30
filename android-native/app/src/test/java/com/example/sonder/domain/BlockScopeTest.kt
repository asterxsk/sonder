package com.example.sonder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored string ↔ enum mapping. The column is `TEXT NOT NULL DEFAULT 'WHOLE_APP'`, so
 * the only values that can arrive are the ones this class writes — but the mapping still
 * has to answer for a null (a row read before the migration's default applied, or a test
 * builder) and it must never throw on one.
 */
class BlockScopeTest {

    @Test
    fun `a stored value round-trips`() {
        BlockScope.entries.forEach { scope ->
            assertEquals(scope, BlockScope.fromStored(scope.stored))
        }
    }

    @Test
    fun `the stored names are the ones the database holds`() {
        assertEquals("WHOLE_APP", BlockScope.WHOLE_APP.stored)
        assertEquals("SHORTS_ONLY", BlockScope.SHORTS_ONLY.stored)
    }

    @Test
    fun `a missing value is a whole-app target`() {
        // What every row that predates the scope column reads as. Anything but WHOLE_APP
        // here would silently narrow an existing target to a surface it may not have.
        assertEquals(BlockScope.WHOLE_APP, BlockScope.fromStored(null))
    }

    @Test
    fun `an unrecognised value is a whole-app target`() {
        assertEquals(BlockScope.WHOLE_APP, BlockScope.fromStored(""))
        assertEquals(BlockScope.WHOLE_APP, BlockScope.fromStored("shorts_only"))
        assertEquals(BlockScope.WHOLE_APP, BlockScope.fromStored("WHATEVER"))
    }
}
