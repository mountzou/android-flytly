package com.mountzou.flytly

import com.mountzou.flytly.account.*
import com.mountzou.flytly.atlas.PlaceStatus
import org.junit.Assert.*
import org.junit.Test

class AccountStateTest {
    @Test fun pendingRemovalWinsOverOldServerSnapshot() {
        val cache = AccountCache(places = mapOf("city:athens" to PlaceStatus.VISITED)).edit("city:athens", "removed")
        assertEquals(PlaceStatus.REMOVED, cache.visiblePlaces().records["city:athens"])
    }
    @Test fun acknowledgementPreservesAnEditMadeDuringUpload() {
        val original = AccountCache().edit("country:GRC", "visited")
        val newer = original.edit("country:GRC", "wishlist").acknowledge(1)
        assertEquals(1, newer.pending.size)
        assertEquals(PlaceStatus.WISHLIST, newer.visiblePlaces().records["country:GRC"])
    }
    @Test fun serverTombstonesArePreservedAfterAcknowledgement() {
        val state = AccountCache().edit("city:a", "visited").acknowledge(1).copy(places = mapOf("city:a" to PlaceStatus.REMOVED))
        assertTrue(state.pending.isEmpty())
        assertEquals(PlaceStatus.REMOVED, state.visiblePlaces().records["city:a"])
    }
    @Test fun profilePatchesPreserveOtherFieldsAndUnknownSelections() {
        val state = AccountCache(profile = mapOf("travelPace" to 3, "mustHave" to listOf("Future iOS option")))
            .edit("profile:foodImportance", 1)
        assertEquals(3, state.visibleProfile()["travelPace"])
        assertEquals(listOf("Future iOS option"), state.visibleProfile()["mustHave"])
        assertEquals(1, state.visibleProfile()["foodImportance"])
    }
    @Test fun accountFilesCannotShareNamespaceAcrossUsersOrProjects() {
        assertNotEquals(accountNamespace("project:a"), accountNamespace("project:b"))
        assertNotEquals(accountNamespace("project:a"), accountNamespace("other:a"))
        assertEquals(accountNamespace("project:a"), accountNamespace("project:a"))
        assertFalse(accountNamespace("../../email@example.com").contains('/'))
    }
}
