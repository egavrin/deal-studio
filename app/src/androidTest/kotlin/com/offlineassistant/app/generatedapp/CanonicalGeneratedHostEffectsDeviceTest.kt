package com.offlineassistant.app.generatedapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalGeneratedHostEffectsDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun typedNavigationCarriesBothCoordinateEndpoints() {
        val executor = CanonicalHostEffectExecutor(context, "intent-test")
        val intent = executor.mapIntent(request(CanonicalHostEffectRequest.MAP_NAVIGATE))

        assertEquals("31.2304,121.4737", intent.data?.getQueryParameter("origin"))
        assertEquals("31.2401,121.49", intent.data?.getQueryParameter("destination"))
    }

    @Test
    fun completionRoutesDurableCalendarIdentityIntoDealState() {
        val runtime = CanonicalDealToolchain(context).createRuntime(DEAL)
        val completion = CanonicalHostEffectContract.completion(
            request(CanonicalHostEffectRequest.CALENDAR_INSERT),
            CanonicalHostEffectContract.STATUS_SUCCESS,
            "created",
            "4815"
        )

        val next = runtime.dispatch("onPlatformHostAction", completion.type, completion.fields)

        assertEquals("success", next.getValue("status").jsonPrimitive.content)
        assertEquals("4815", next.getValue("eventId").jsonPrimitive.content)
    }

    @Test
    fun calendarRemovalRejectsIdentityOwnedByAnotherGeneratedApp() {
        val owner = CanonicalHostEffectExecutor(context, "owner-a").also { it.recordOwnedEvent("4815") }
        val other = CanonicalHostEffectExecutor(context, "owner-b")
        val removal = request(CanonicalHostEffectRequest.CALENDAR_REMOVE, eventId = "4815")

        val rejected = other.execute(removal)

        assertEquals(CanonicalHostEffectContract.STATUS_ERROR, rejected.fields["status"])
        assertTrue(rejected.fields["message"].toString().contains("not owned"))
        assertTrue(owner !== other)
    }

    @Test
    fun exactHeldOutPromptIsPreservedAsAnEvaluationAsset() {
        val actual = InstrumentationRegistry.getInstrumentation().context.assets
            .open("shanghai-host-effects-v1.txt").bufferedReader().use { it.readText() }
        assertEquals(SHANGHAI_PROMPT + "\n", actual)
    }

    private fun request(operation: String, eventId: String = "") = CanonicalHostEffectRequest(
        operation = operation,
        requestId = "visit-3",
        originLatitudeE6 = 31_230_400,
        originLongitudeE6 = 121_473_700,
        destinationLatitudeE6 = 31_240_100,
        destinationLongitudeE6 = 121_490_000,
        title = "Reserved time",
        startEpochMinute = 30_000_000,
        durationMinutes = 45,
        reminderMinutes = 15,
        eventId = eventId,
        confirmed = true
    )

    private companion object {
        val DEAL = """
            // generated-capability: calendar.events.owned
            export class AppState { status: string = ""; eventId: string = ""; }
            export class PlatformHostAction {
              operation: string = ""; requestId: string = ""; status: string = ""; message: string = "";
              originLatitudeE6: int = 0; originLongitudeE6: int = 0;
              destinationLatitudeE6: int = 0; destinationLongitudeE6: int = 0;
              title: string = ""; startEpochMinute: int = 0; durationMinutes: int = 0;
              reminderMinutes: int = 0; eventId: string = ""; confirmed: boolean = false;
            }
            export function initialState(): AppState { return { status: "", eventId: "" }; }
            // @ui-update
            export function onPlatformHostAction(state: AppState, action: PlatformHostAction): AppState {
              return { status: action.status, eventId: action.eventId };
            }
        """.trimIndent()

        val SHANGHAI_PROMPT = """
            Shanghai layover: I land at Pudong International Airport at 09:00 with 11 hours before my next flight. Plan the day hour by hour around the sights worth seeing, each stop with its name,
              how long to spend there and its coordinates, a button that starts navigation to it from the last stop I reached, a button that puts the stop in my phone's calendar with a reminder, and
              the latest time I must leave for the airport. Keep a note of which stops I actually reached, and give me a button that wipes every calendar event this app added and one that opens the
              phone's calendar.
        """.trimIndent()
    }
}
