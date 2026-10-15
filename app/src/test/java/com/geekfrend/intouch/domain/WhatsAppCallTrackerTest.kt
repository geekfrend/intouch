package com.geekfrend.intouch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sequences taken from the InTouch Lab results on the Galaxy Z Flip 6 (Oct 15). */
class WhatsAppCallTrackerTest {
    private val t0 = 1_000_000_000L
    private fun ev(sec: Int, text: String, key: String = "k", title: String = "Sam", category: String? = "call") =
        WaEvent(key, title, text, category, t0 + sec * 1000L)

    @Test fun outgoingCallFromCallingToRemoved() {
        val t = WhatsAppCallTracker()
        t.onPosted(ev(0, "Calling…"))
        t.onPosted(ev(4, "Ringing…"))
        t.onPosted(ev(11, "Ongoing voice call"))
        t.onPosted(ev(11, "Ongoing voice call")) // posted twice, counts once
        val ended = t.onRemoved("k", t0 + 34_000)
        assertNotNull(ended)
        assertEquals("Sam", ended!!.title)
        assertEquals(23L, ended.durationSec)
        assertTrue(ended.outgoing)
    }

    @Test fun ongoingWithoutCallingIsIncoming() {
        val t = WhatsAppCallTracker()
        t.onPosted(ev(0, "Ongoing voice call"))
        val ended = t.onRemoved("k", t0 + 120_000)
        assertFalse(ended!!.outgoing)
        assertEquals(120L, ended.durationSec)
    }

    @Test fun onHoldDoesNotRestartTheCall() {
        val t = WhatsAppCallTracker()
        t.onPosted(ev(0, "Ongoing voice call"))
        t.onPosted(ev(900, "On hold"))
        t.onPosted(ev(902, "Ongoing voice call"))
        assertEquals(940L, t.onRemoved("k", t0 + 940_000)!!.durationSec)
    }

    @Test fun cancelledCallIsNotACall() {
        val t = WhatsAppCallTracker()
        t.onPosted(ev(0, "Calling…"))
        assertNull(t.onRemoved("k", t0 + 1_000))
    }

    @Test fun nonCallNotificationsAreIgnored() {
        val t = WhatsAppCallTracker()
        t.onPosted(ev(0, "Ongoing voice call", category = "msg"))
        t.onPosted(ev(0, "Missed voice call", category = "missed_call"))
        assertTrue(t.active.isEmpty())
    }

    @Test fun reportsActiveCallByName() {
        val t = WhatsAppCallTracker()
        t.onPosted(ev(0, "Ongoing voice call"))
        assertTrue(t.isActiveFor(" sam "))
        assertFalse(t.isActiveFor("Alex"))
    }
}
