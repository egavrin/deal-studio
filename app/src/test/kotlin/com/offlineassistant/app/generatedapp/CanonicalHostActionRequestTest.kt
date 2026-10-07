package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalHostActionRequestTest {
    @Test
    fun `validated host requests keep the finite contract generic`() {
        assertNull(
            CanonicalHostActionRequest(
                operation = CanonicalHostActionOperation.NAVIGATION_OPEN,
                destinationLatitude = 31.2304,
                destinationLongitude = 121.4737,
                originLatitude = 31.1443,
                originLongitude = 121.8083,
                useOrigin = true
            ).validationError()
        )
        assertNull(
            CanonicalHostActionRequest(
                operation = CanonicalHostActionOperation.CALENDAR_CREATE,
                ownerKey = "owned-event-17",
                title = "A state-owned event",
                startEpochMinute = 30_000_000,
                durationMinutes = 45,
                reminderMinutes = 10
            ).validationError()
        )
        assertNull(
            CanonicalHostActionRequest(CanonicalHostActionOperation.CALENDAR_OPEN).validationError()
        )
        assertNull(
            CanonicalHostActionRequest(CanonicalHostActionOperation.CALENDAR_CLEAR_OWNED).validationError()
        )
    }

    @Test
    fun `invalid input is rejected before a platform effect can be attempted`() {
        assertEquals(
            "destination coordinates are invalid",
            CanonicalHostActionRequest(
                operation = CanonicalHostActionOperation.NAVIGATION_OPEN,
                destinationLatitude = Double.NaN,
                destinationLongitude = 121.0
            ).validationError()
        )
        assertEquals(
            "origin coordinates are invalid",
            CanonicalHostActionRequest(
                operation = CanonicalHostActionOperation.NAVIGATION_OPEN,
                destinationLatitude = 31.0,
                destinationLongitude = 121.0,
                originLatitude = 91.0,
                originLongitude = 121.0,
                useOrigin = true
            ).validationError()
        )
        assertEquals(
            "owned calendar key is invalid",
            CanonicalHostActionRequest(
                operation = CanonicalHostActionOperation.CALENDAR_CREATE,
                title = "A state-owned event",
                startEpochMinute = 30_000_000
            ).validationError()
        )
        assertEquals(
            "calendar start time is invalid",
            CanonicalHostActionRequest(
                operation = CanonicalHostActionOperation.CALENDAR_CREATE,
                ownerKey = "owned-event-17",
                title = "A state-owned event",
                startEpochMinute = 0
            ).validationError()
        )
    }
}
