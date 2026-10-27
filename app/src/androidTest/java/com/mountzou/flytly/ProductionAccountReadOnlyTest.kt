package com.mountzou.flytly

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mountzou.flytly.account.AccountCacheFile
import com.mountzou.flytly.atlas.AtlasRepository
import com.mountzou.flytly.atlas.PlaceStatus
import com.mountzou.flytly.atlas.TravelSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in audit of the already signed-in account. No sign-in, sign-out or cloud writes. */
@RunWith(AndroidJUnit4::class)
class ProductionAccountReadOnlyTest {
    @Test fun existingSessionReadsItsOwnTravelDataFromTheServer() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("production_read_only") == "true")
        withTimeout(45_000) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val auth = FirebaseAuth.getInstance()
            val db = FirebaseFirestore.getInstance()
            val project = db.app.options.projectId
            assertEquals("flytly-ddc3c", project)
            val user = auth.currentUser
            assertNotNull("Complete Google login in Flytly before running this audit.", user)
            val uid = user!!.uid
            val owner = db.collection("users").document(uid)
            val places = owner.collection("travelPlaces").get(Source.SERVER).await()
            val profile = owner.collection("profile").document("travelPreferences").get(Source.SERVER).await()
            assertEquals("The account changed during the audit.", uid, auth.currentUser?.uid)
            val records = places.documents.associate { document ->
                document.id to PlaceStatus.entries.first { it.value == document.getString("status") }
            }
            val snapshot = TravelSnapshot(records)
            val atlas = AtlasRepository.load(context)
            val cache = AccountCacheFile(context, uid, project!!).load()
            val result = JSONObject()
                .put("projectID", project)
                .put("provider", if (user.providerData.any { it.providerId == "google.com" }) "google.com" else "apple.com")
                .put("serverReadSucceeded", true)
                .put("visitedCountries", snapshot.countryIDs(PlaceStatus.VISITED, atlas).size)
                .put("visitedCities", snapshot.cities(PlaceStatus.VISITED, atlas).size)
                .put("wishlistCountries", snapshot.countryIDs(PlaceStatus.WISHLIST, atlas).size)
                .put("wishlistCities", snapshot.cities(PlaceStatus.WISHLIST, atlas).size)
                .put("profileExists", profile.exists())
                .put("pendingLocalChanges", cache.pending.size)
                .put("localPlacesMatchServer", cache.places == records)
            val expectations = listOf("expected_visited_city" to PlaceStatus.VISITED,
                "expected_wishlist_city" to PlaceStatus.WISHLIST).mapNotNull { (argument, status) ->
                InstrumentationRegistry.getArguments().getString(argument)?.let { id ->
                    val city = requireNotNull(atlas.cityForSavedID(id)) { "Unknown expected catalog city" }
                    val serverMatches = snapshot.status(city, atlas) == status
                    val localMatches = cache.visiblePlaces().status(city, atlas) == status
                    result.put("${status.value}ExpectationOnServer", serverMatches)
                        .put("${status.value}ExpectationInLocalCache", localMatches)
                    serverMatches && localMatches
                }
            }
            // Only counts/status leave the device. Never print tokens, email, UID or individual places.
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nFLYTLY_ACCOUNT_AUDIT=$result\n") })
            assertTrue("A user-requested place status has not reached both server and local cache.", expectations.all { it })
        }
    }
}
