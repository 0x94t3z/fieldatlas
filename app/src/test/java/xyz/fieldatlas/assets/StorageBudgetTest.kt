package xyz.fieldatlas.assets

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageBudgetTest {
    @Test fun exactlyFiftyDecimalGigabytesIsAllowed() {
        assertEquals(BudgetDecision.Allowed,
            StorageBudget.evaluate(49_000_000_000, 1_000_000_000, 2_000_000_000))
    }

    @Test fun oneByteOverIsRejected() {
        assertEquals(BudgetDecision.ExceedsGlobalLimit,
            StorageBudget.evaluate(49_000_000_000, 1_000_000_001, Long.MAX_VALUE))
    }

    @Test fun overflowFailsClosed() {
        assertEquals(BudgetDecision.InvalidSize,
            StorageBudget.evaluate(Long.MAX_VALUE, 1, Long.MAX_VALUE))
    }

    @Test fun lowFreeSpaceFailsBeforeCopy() {
        assertEquals(BudgetDecision.InsufficientFreeSpace,
            StorageBudget.evaluate(0, 200, 199))
    }

    @Test fun negativeInputsFailClosed() {
        assertEquals(BudgetDecision.InvalidSize, StorageBudget.evaluate(-1, 1, 1))
        assertEquals(BudgetDecision.InvalidSize, StorageBudget.evaluate(1, -1, 1))
        assertEquals(BudgetDecision.InvalidSize, StorageBudget.evaluate(1, 1, -1))
    }
}
