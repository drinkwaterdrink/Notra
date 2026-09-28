package com.notra.app

import com.notra.app.data.NoteEntity
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class IdentityTest {
    @Test fun noteDefaultsUseStableUuidAndVersionZero() {
        val first = NoteEntity(title = "a", createdAt = 1, updatedAt = 1)
        val second = NoteEntity(title = "b", createdAt = 1, updatedAt = 1)
        UUID.fromString(first.id)
        assertNotEquals(first.id, second.id)
        assertEquals(0, first.documentSchemaVersion)
        assertEquals(0L, first.revision)
    }
}
