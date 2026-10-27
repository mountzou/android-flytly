package com.mountzou.flytly

import com.mountzou.flytly.account.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccountDeletionTest {
    @Test fun confirmsOriginalOwnerBeforeEveryDestructiveStage() = runBlocking {
        val events = mutableListOf<String>();var owner: String? = "original"
        val completed = performAccountDeletion("original",{owner},{ events += "reauth" },{events += "pause"},{ events += "delete";owner = null },{events += "cleanup"})
        assertTrue(completed);assertEquals(listOf("reauth","pause","delete","cleanup"),events)
    }
    @Test fun providerCancellationOrFailureDoesNotPauseOrRemoveAnything() = runBlocking {
        for (error in listOf(IllegalStateException("provider offline"),CancellationException("cancelled"))) {
            val events = mutableListOf<String>()
            try {
                performAccountDeletion("original",{"original"},{throw error},{events += "pause"},{events += "delete"},{events += "cleanup"});fail("Request should stop")
            } catch (caught: Exception) { assertSame(error,caught) }
            assertTrue(events.isEmpty())
        }
    }
    @Test fun differentIdentityCannotAdvanceToDeletion() = runBlocking {
        var owner: String? = "original";var removed = false
        try { performAccountDeletion("original",{owner},{owner="different"},{removed=true},{removed=true},{removed=true});fail("Owner mismatch accepted") }
        catch (_: AccountOwnerMismatch) { }
        assertFalse(removed)
    }
    @Test fun ownerSwitchDuringPauseStopsDeletion() = runBlocking {
        var owner: String? = "original";var removed = false
        try { performAccountDeletion("original",{owner},{},{owner="different"},{removed=true},{removed=true});fail("Owner changed") }
        catch (_: AccountOwnerMismatch) { }
        assertFalse(removed)
    }
    @Test fun failedRemoteDeletionKeepsLocalDataForRetry() = runBlocking {
        var erased = false
        try { performAccountDeletion("original",{"original"},{},{},{error("network failed")},{erased=true});fail("Remote failure ignored") }
        catch (_: IllegalStateException) { }
        assertFalse(erased)
    }
    @Test fun localCleanupFailureReportsCompletedRemoteDeletionSeparately() = runBlocking {
        var removed = false
        assertFalse(performAccountDeletion("original",{"original"},{},{},{removed=true},{error("disk failed")}))
        assertTrue(removed)
    }
    @Test fun loginMethodLabelsMatchIOSAndIgnoreUnknownProviders() {
        assertEquals("Apple, Google",AccountDetails(null,null,listOf("google.com","firebase","apple.com","google.com")).loginMethods)
        assertEquals("",AccountDetails(null,null,emptyList()).loginMethods)
    }
}
