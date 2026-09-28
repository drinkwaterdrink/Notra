package com.notra.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextEquals
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notra.app.data.NoteEntity
import com.notra.app.data.NotraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FoundationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun destinationsAreReachable() {
        listOf("Library", "Search", "Settings", "Board").forEach {
            compose.onNode(hasTestTag("destination_$it")).performClick()
            compose.onNode(hasTestTag("screen_title")).assertTextEquals(it).assertIsDisplayed()
        }
    }

    @Test fun roomSurvivesDatabaseReopen() {
      runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "foundation-test.db"
        context.deleteDatabase(name)
        val note = NoteEntity(title = "Persistence probe", createdAt = 1, updatedAt = 1)
        Room.databaseBuilder(context, NotraDatabase::class.java, name).build().also {
            it.notes().insert(note)
            it.close()
        }
        Room.databaseBuilder(context, NotraDatabase::class.java, name).build().also {
            assertEquals(note, it.notes().get(note.id))
            it.close()
        }
      }
    }

    @Test fun dataStoreSurvivesReopen() {
      runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.preferencesDataStoreFile("foundation-test")
        val key = booleanPreferencesKey("probe")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val first = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        first.edit { it[key] = true }
        assertTrue(first.data.first()[key] == true)
        scope.cancel()
        val reopenedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reopened = PreferenceDataStoreFactory.create(scope = reopenedScope, produceFile = { file })
        assertTrue(reopened.data.first()[key] == true)
        reopenedScope.cancel()
      }
    }

    @Test fun zPersistenceExistsAfterRestart() {
      runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Room.databaseBuilder(context, NotraDatabase::class.java, "foundation-test.db").build().also {
            assertEquals("Persistence probe", it.notes().observeActive().first().single().title)
            it.close()
        }
        val file = context.preferencesDataStoreFile("foundation-test")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        assertTrue(store.data.first()[booleanPreferencesKey("probe")] == true)
        scope.cancel()
      }
    }
}
