package xyz.fieldatlas.assets

sealed interface BudgetDecision {
    data object Allowed : BudgetDecision
    data object InvalidSize : BudgetDecision
    data object ExceedsGlobalLimit : BudgetDecision
    data object InsufficientFreeSpace : BudgetDecision
}

object StorageBudget {
    const val MAX_TOTAL_BYTES = 50_000_000_000L

    fun evaluate(currentBytes: Long, incomingBytes: Long, freeBytes: Long): BudgetDecision {
        if (currentBytes < 0 || incomingBytes < 0 || freeBytes < 0) return BudgetDecision.InvalidSize
        val total = try {
            Math.addExact(currentBytes, incomingBytes)
        } catch (_: ArithmeticException) {
            return BudgetDecision.InvalidSize
        }
        if (total > MAX_TOTAL_BYTES) return BudgetDecision.ExceedsGlobalLimit
        if (incomingBytes > freeBytes) return BudgetDecision.InsufficientFreeSpace
        return BudgetDecision.Allowed
    }
}
