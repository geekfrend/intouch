package com.geekfrend.intouch.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Channel { PHONE, WHATSAPP, TEXT, OTHER }

enum class Direction { OUTGOING, INCOMING, UNKNOWN }

/** Only CONNECTED and SKIPPED reset a contact's timer. */
enum class Outcome { CONNECTED, NO_ANSWER, LEFT_MESSAGE, SKIPPED }

enum class Source { CALL_HISTORY, WHATSAPP_NOTIFICATION, APP_PROMPT, MANUAL, SMS_HISTORY }

/** One person you want to stay in touch with. */
@Entity(tableName = "contacts")
data class TrackedContact(
    @PrimaryKey val id: String,
    /** Address-book lookup key; survives contact merges better than the numeric ID. */
    val lookupKey: String?,
    val systemContactId: Long?,
    /** Refreshed from the address book on every app open. Also used to match WhatsApp notifications. */
    val displayName: String,
    val photoUri: String?,
    /** Normalised to E.164 where possible, e.g. +15551234567. */
    val phoneNumber: String,
    val preferredChannel: Channel,
    val frequencyDays: Int,
    val snoozedUntilEpochDay: Long?,
    val createdAt: Long,
    val archived: Boolean,
    val missingFromContacts: Boolean,
)

/** One entry in a contact's call log. */
@Entity(
    tableName = "interactions",
    foreignKeys = [
        ForeignKey(
            entity = TrackedContact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId"), Index(value = ["externalId"], unique = true)],
)
data class Interaction(
    @PrimaryKey val id: String,
    val contactId: String,
    val occurredAt: Long,
    val channel: Channel,
    val direction: Direction,
    val outcome: Outcome,
    val source: Source,
    val durationSec: Long?,
    /** Call-history row ID or WhatsApp call key, so the same call is never logged twice. */
    val externalId: String?,
    /** You marked an automatic entry as "Not a real call". It stays in history but doesn't count. */
    val dismissed: Boolean,
)
