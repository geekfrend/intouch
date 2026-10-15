package com.geekfrend.intouch.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Database(entities = [TrackedContact::class, Interaction::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun interactionDao(): InteractionDao
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE archived = 0 ORDER BY displayName COLLATE NOCASE")
    fun observeActive(): Flow<List<TrackedContact>>

    @Query("SELECT * FROM contacts WHERE archived = 1 ORDER BY displayName COLLATE NOCASE")
    fun observeArchived(): Flow<List<TrackedContact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observe(id: String): Flow<TrackedContact?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun get(id: String): TrackedContact?

    @Query("SELECT * FROM contacts")
    suspend fun all(): List<TrackedContact>

    @Query("SELECT * FROM contacts WHERE archived = 0")
    suspend fun allActive(): List<TrackedContact>

    // Upsert updates in place. (REPLACE would delete the row and cascade-delete its call log.)
    @Upsert
    suspend fun upsert(contact: TrackedContact)

    @Insert
    suspend fun insertAll(contacts: List<TrackedContact>)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<Interaction>>

    @Query("SELECT * FROM interactions WHERE contactId = :contactId ORDER BY occurredAt DESC")
    fun observeFor(contactId: String): Flow<List<Interaction>>

    @Query("SELECT * FROM interactions")
    suspend fun all(): List<Interaction>

    @Query("SELECT * FROM interactions WHERE id = :id")
    suspend fun get(id: String): Interaction?

    /** Returns -1 when an entry with the same externalId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(interaction: Interaction): Long

    @Insert
    suspend fun insertAll(interactions: List<Interaction>)

    @Update
    suspend fun update(interaction: Interaction)

    @Query("DELETE FROM interactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM interactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM interactions WHERE contactId = :contactId AND channel = 'WHATSAPP' AND occurredAt >= :since")
    suspend fun countWhatsAppSince(contactId: String, since: Long): Int
}
