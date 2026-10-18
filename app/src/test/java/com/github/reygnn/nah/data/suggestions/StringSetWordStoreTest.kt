package com.github.reygnn.nah.data.suggestions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-only: a fake [DataStore] stands in for the file-backed one, so no Robolectric is needed. */
class StringSetWordStoreTest {

    private val key = stringSetPreferencesKey("words")

    private class FakeDataStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("read-only fake")
    }

    private fun store(data: Flow<Preferences>) = object : StringSetWordStore(FakeDataStore(data), key) {}

    private val failingRead: Flow<Preferences> = flow { throw IOException("disk read failed") }

    @Test
    fun `read returns the stored words`() = runTest {
        val words = store(flowOf(preferencesOf(key to setOf("Hallo", "Zürich")))).read()

        assertEquals(setOf("Hallo", "Zürich"), words)
    }

    @Test
    fun `read returns empty when nothing is stored`() = runTest {
        assertTrue(store(flowOf(preferencesOf())).read().isEmpty())
    }

    @Test(expected = IOException::class)
    fun `read propagates a read error instead of reporting an empty list`() = runTest {
        store(failingRead).read()
    }

    @Test
    fun `words still falls back to empty on a read error (keyboard must keep working)`() = runTest {
        assertTrue(store(failingRead).words.first().isEmpty())
    }
}
