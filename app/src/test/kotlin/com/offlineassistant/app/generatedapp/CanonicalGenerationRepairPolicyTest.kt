package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalGenerationRepairPolicyTest {
    @Test
    fun `artifact specific inspect call must select one compiler path`() {
        val calls = listOf(
            DeepSeekFunctionCall("one", "inspect_deal_change", "{}"),
            DeepSeekFunctionCall("two", "inspect_deal_ui_change", "{}")
        )

        assertFalse(calls.isValidCompilerBatch())
        assertTrue(calls.compilerBatchError().orEmpty().contains("only call"))
        assertTrue(listOf(calls.first()).isValidCompilerBatch())
        val mixed = calls + DeepSeekFunctionCall("three", "apply_deal_changes", "{}")
        assertFalse(mixed.isValidCompilerBatch())
        assertTrue(mixed.compilerBatchError().orEmpty().contains("inspect_deal_change"))
        assertTrue(emptyList<DeepSeekFunctionCall>().compilerBatchError().orEmpty().contains("no compiler tool calls"))
    }

    @Test
    fun `compiler transport batches do not mix helpers with updates`() {
        assertEquals(
            2,
            preferredDealGraphBatchSize(
                pendingHoleIds = listOf("helper:first", "helper:second", "update:onSave", "update:onDelete"),
                currentMaximum = 6
            )
        )
        assertEquals(
            2,
            preferredDealGraphBatchSize(
                pendingHoleIds = listOf("update:onSave", "update:onDelete", "update:onReset"),
                currentMaximum = 6
            )
        )
        assertEquals(3, reducedDealGraphBatchSize(6))
        assertEquals(1, reducedDealGraphBatchSize(3))
    }

    @Test
    fun `semantic repair depth belongs to each unresolved hole`() {
        val attempts = mapOf(
            "update:onOldFailure" to 3,
            "update:onCurrent" to 1,
            "update:onFresh" to 0
        )

        assertEquals(
            1,
            dealGraphRepairDepth(
                pendingHoleIds = listOf("update:onCurrent", "update:onFresh"),
                repairAttemptsByHole = attempts
            )
        )
        assertEquals(
            0,
            dealGraphRepairDepth(
                pendingHoleIds = listOf("update:onFresh"),
                repairAttemptsByHole = attempts
            )
        )
    }

    @Test
    fun `flash gets one flash repair followed by one pro escalation`() {
        assertEquals(3, CanonicalGenerationRepairPolicy.maxRounds(DeepSeekGenerationModel.FLASH))
        assertEquals(
            DeepSeekGenerationModel.FLASH,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 0)
        )
        assertEquals(
            DeepSeekGenerationModel.FLASH,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 1)
        )
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 2)
        )
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 3)
        )
    }

    @Test
    fun `pro generation has one repair and no model switch`() {
        assertEquals(2, CanonicalGenerationRepairPolicy.maxRounds(DeepSeekGenerationModel.PRO))
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.PRO, 0)
        )
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.PRO, 1)
        )
        assertEquals(3, CanonicalGenerationRepairPolicy.maxAttemptsPerHole(DeepSeekGenerationModel.PRO))
        assertEquals(4, CanonicalGenerationRepairPolicy.maxAttemptsPerHole(DeepSeekGenerationModel.FLASH))
    }

    @Test
    fun `borrowed mutation diagnostic produces a concrete repair invariant`() {
        val directive = dealRepairDirective(
            "update:onMove: error UI2050: parameter 'state' is borrowed immutable"
        ).orEmpty()

        assertTrue(directive.contains("no assignment"))
        assertTrue(directive.contains("fresh complete root-state object literal"))
        assertTrue(directive.contains("Do not reuse the rejected body"))
    }

    @Test
    fun `accepted progress at the budget boundary earns one bounded repair round`() {
        assertEquals(
            3,
            CanonicalGenerationRepairPolicy.extendAfterProgress(
                currentBudget = 2,
                completedRounds = 2,
                acceptedChanges = 1
            )
        )
        assertEquals(
            2,
            CanonicalGenerationRepairPolicy.extendAfterProgress(
                currentBudget = 2,
                completedRounds = 2,
                acceptedChanges = 0
            )
        )
        assertEquals(
            CanonicalGenerationRepairPolicy.HARD_MAX_ROUNDS,
            CanonicalGenerationRepairPolicy.extendAfterProgress(
                currentBudget = CanonicalGenerationRepairPolicy.HARD_MAX_ROUNDS,
                completedRounds = CanonicalGenerationRepairPolicy.HARD_MAX_ROUNDS,
                acceptedChanges = 2
            )
        )
    }

    @Test
    fun `ui repair budget covers compiler deferred sections without becoming unbounded`() {
        assertEquals(
            7,
            CanonicalGenerationRepairPolicy.extendForUiPendingWork(
                currentBudget = 3,
                completedRounds = 1,
                pendingRepair = true,
                deferredSections = 4
            )
        )
        assertEquals(
            CanonicalGenerationRepairPolicy.UI_HARD_MAX_ROUNDS,
            CanonicalGenerationRepairPolicy.extendForUiPendingWork(
                currentBudget = 3,
                completedRounds = 4,
                pendingRepair = true,
                deferredSections = 20
            )
        )
        assertEquals(
            3,
            CanonicalGenerationRepairPolicy.extendForUiPendingWork(
                currentBudget = 3,
                completedRounds = 2,
                pendingRepair = false,
                deferredSections = 0
            )
        )
    }

    @Test
    fun `rejected candidate guard detects only byte-identical repeats`() {
        val guard = RejectedCandidateGuard()

        assertFalse(guard.observe(listOf("first", "second")))
        assertFalse(guard.observe(listOf("third")))
        assertTrue(guard.observe(listOf("second")))
    }
}
