package dev.intelisystems.potrosackakorpa

import org.junit.Assert.*
import org.junit.Test

class CatalogPageStateTest {
    private val old = "11111111-1111-1111-1111-111111111111:20"
    private val new = "22222222-2222-2222-2222-222222222222:1"
    @Test fun newGenerationRequiresFullResetAcrossAllPages() {
        val state = CatalogPageState(old)
        state.accept(new, true, "a".repeat(32), 0)
        state.accept(new, true, null, 0)
        assertTrue(state.reset); assertEquals(new, state.version); assertEquals(2, state.pages)
        assertThrows(IllegalStateException::class.java) { CatalogPageState(old).accept(new, false, null, 0) }
    }
    @Test fun deltaCanContainWithdrawalsAndNoPriceUpdates() {
        val state = CatalogPageState(old)
        state.accept(old.substringBefore(':') + ":21", false, null, 2)
        assertFalse(state.reset)
        CatalogPageState(old).unchanged()
    }
    @Test fun inconsistentPagesAndNonAdvancingCursorCannotBeCommitted() {
        val state = CatalogPageState(old)
        state.accept(new, true, "a".repeat(32), 0)
        assertThrows(IllegalStateException::class.java) { state.accept(new, true, "a".repeat(32), 0) }
        assertThrows(IllegalStateException::class.java) { state.accept(new, false, null, 0) }
        assertThrows(IllegalStateException::class.java) { state.accept(new.dropLast(1) + "2", true, null, 0) }
        assertThrows(IllegalStateException::class.java) { state.unchanged() }
        assertThrows(IllegalStateException::class.java) { CatalogPageState("").unchanged() }
        assertThrows(IllegalStateException::class.java) { CatalogPageState(old).accept(new, true, null, 1) }
    }
    @Test fun revisionRollbackRequiresReset() {
        val earlier = old.substringBefore(':') + ":1"
        assertThrows(IllegalStateException::class.java) { CatalogPageState(old).accept(earlier, false, null, 0) }
        CatalogPageState(old).accept(earlier, true, null, 0)
    }
}
