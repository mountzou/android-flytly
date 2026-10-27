package com.mountzou.flytly

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.mountzou.flytly.atlas.atlasSettings
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SettingsStorageInstrumentedTest {
    @Test fun migratingAppPreferencesKeepsExistingChoicesAcrossAccountsAndIsolatesPreviewFixtures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString();val global = "global-test-$id";val first = "first-$id";val other = "other-$id"
        context.getSharedPreferences("atlas_settings_$first",Context.MODE_PRIVATE).edit().putString("appearance","dark").putBoolean("countryNames",false).putInt("minimumPopulation",250_000).commit()
        val settings = atlasSettings(context,first,true,global)
        assertEquals("dark",settings.getString("appearance",null));assertFalse(settings.getBoolean("countryNames",true));assertEquals(250_000,settings.getInt("minimumPopulation",0))
        context.getSharedPreferences("atlas_settings_$other",Context.MODE_PRIVATE).edit().putString("appearance","light").putBoolean("countryNames",true).putInt("minimumPopulation",50_000).commit()
        val next = atlasSettings(context,other,true,global)
        assertEquals("dark",next.getString("appearance",null));assertFalse(next.getBoolean("countryNames",true));assertEquals(250_000,next.getInt("minimumPopulation",0))
        val preview = atlasSettings(context,other,false,global)
        assertEquals("light",preview.getString("appearance",null));assertTrue(preview.getBoolean("countryNames",false))
        listOf(global,"atlas_settings_$first","atlas_settings_$other").forEach { context.deleteSharedPreferences(it) }
    }
}
