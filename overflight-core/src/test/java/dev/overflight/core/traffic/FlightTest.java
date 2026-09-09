package dev.overflight.core.traffic;

import dev.overflight.core.atmo.Isa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flight is a function of time rather than a moving object. */
class FlightTest {

    private static final AircraftType TYPE = AircraftCatalog.defaults().byId("airliner_widebody");

    private static Flight heading(double degrees, double startX, double startZ) {
        return new Flight(1L, TYPE, startX, startZ, Math.toRadians(degrees),
                Isa.flightLevelToMetres(350), 250.0, 0.0, 3600.0, 0, 1);
    }

    @Test
    @DisplayName("north is negative Z, as it is everywhere else in Minecraft")
    void compassMatchesTheGame() {
        Flight north = heading(0.0, 0.0, 0.0);
        assertEquals(0.0, north.xAt(100.0), 1.0e-6);
        assertEquals(-25000.0, north.zAt(100.0), 1.0e-6);

        Flight east = heading(90.0, 0.0, 0.0);
        assertEquals(25000.0, east.xAt(100.0), 1.0e-6);
        assertEquals(0.0, east.zAt(100.0), 1.0e-6);

        Flight south = heading(180.0, 0.0, 0.0);
        assertEquals(25000.0, south.zAt(100.0), 1.0e-6);
    }

    @Test
    @DisplayName("position is exact at the moment the leg begins")
    void startsWhereItSaysItDoes() {
        Flight flight = heading(37.0, 1234.0, -5678.0);
        assertEquals(1234.0, flight.xAt(0.0), 1.0e-9);
        assertEquals(-5678.0, flight.zAt(0.0), 1.0e-9);
        assertEquals(0.0, flight.distanceAt(0.0), 1.0e-9);
    }

    @Test
    @DisplayName("airborne only between its start and the end of its leg")
    void lifetimeBounds() {
        Flight flight = heading(90.0, 0.0, 0.0);
        assertFalse(flight.airborneAt(-1.0));
        assertTrue(flight.airborneAt(0.0));
        assertTrue(flight.airborneAt(3600.0));
        assertFalse(flight.airborneAt(3601.0));
    }

    @Test
    @DisplayName("a track straight overhead comes within nothing of the observer")
    void closestApproachOverhead() {
        // Heading east from well to the west, passing through the origin.
        Flight flight = heading(90.0, -100000.0, 0.0);
        double approach = flight.closestApproach(0.0, 3600.0, 0.0, 0.0);
        assertEquals(0.0, approach, 1.0);
    }

    @Test
    @DisplayName("a parallel track comes within exactly its offset")
    void closestApproachOffset() {
        Flight flight = heading(90.0, -100000.0, 8000.0);
        double approach = flight.closestApproach(0.0, 3600.0, 0.0, 0.0);
        assertEquals(8000.0, approach, 1.0);
    }

    @Test
    @DisplayName("asking about a window the flight never occupied says so")
    void closestApproachOutsideTheWindow() {
        Flight flight = heading(90.0, -100000.0, 0.0);
        // Entirely before the leg began.
        assertEquals(Double.MAX_VALUE, flight.closestApproach(-500.0, -100.0, 0.0, 0.0));
    }

    @Test
    @DisplayName("the window matters: an aircraft long gone still counts while its trail hangs")
    void closestApproachRespectsTheWindow() {
        // Passes the origin early, then carries on far to the east.
        Flight flight = heading(90.0, -10000.0, 0.0);
        double now = 3000.0;

        // Where it is now is nowhere near.
        assertTrue(flight.horizontalDistanceFrom(now, 0.0, 0.0) > 500000.0);
        // But it did come overhead within the last hour, which is what decides
        // whether its trail is worth drawing.
        assertEquals(0.0, flight.closestApproach(now - 3600.0, now, 0.0, 0.0), 1.0);
        // Ask only about the last minute and it is correctly far away.
        assertTrue(flight.closestApproach(now - 60.0, now, 0.0, 0.0) > 500000.0);
    }

    @Test
    @DisplayName("ordinary traffic is never on fire")
    void conditionDefaultsToNormal() {
        assertEquals(Flight.Condition.NORMAL, heading(0.0, 0.0, 0.0).condition);
    }
}
