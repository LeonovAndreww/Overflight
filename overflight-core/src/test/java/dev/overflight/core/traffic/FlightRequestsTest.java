package dev.overflight.core.traffic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand-made flights: where they fly, and when they are forgotten. */
class FlightRequestsTest {

    private static final AircraftType TYPE = AircraftCatalog.defaults().byId("airliner_widebody");

    @Test
    @DisplayName("a lone aircraft passes over the point it was aimed at, at the predicted time")
    void leaderArrivesOverhead() {
        double now = 1000.0;
        List<Flight> flights = FlightRequests.build(TYPE, 500.0, -300.0, 350, 70.0,
                Flight.Condition.NORMAL, 1, "vee", now, 1L);
        assertEquals(1, flights.size());
        Flight f = flights.get(0);
        long eta = FlightRequests.secondsToOverhead(TYPE, 350);
        assertEquals(500.0, f.xAt(now + FlightRequests.APPROACH_M / f.groundSpeedMs), 1.0e-6);
        assertEquals(-300.0, f.zAt(now + FlightRequests.APPROACH_M / f.groundSpeedMs), 1.0e-6);
        assertEquals(FlightRequests.APPROACH_M / f.groundSpeedMs, eta, 0.5);
        assertTrue(f.airborneAt(now), "already flying, so it arrives trailing");
        assertEquals(now - FlightRequests.PREROLL_S, f.startTimeS, 1.0e-9);
    }

    @Test
    @DisplayName("a line spreads across the track, a vee sweeps back")
    void formations() {
        List<Flight> line = FlightRequests.build(TYPE, 0.0, 0.0, 350, 0.0,
                Flight.Condition.NORMAL, 3, "line", 0.0, 10L);
        // Heading north: across is east-west, all abreast at the same Z.
        double t = 100.0;
        assertEquals(line.get(0).zAt(t), line.get(2).zAt(t), 1.0e-6);
        assertFalse(line.get(0).xAt(t) == line.get(2).xAt(t));
        assertEquals(10L, line.get(0).id);
        assertEquals(12L, line.get(2).id);

        List<Flight> vee = FlightRequests.build(TYPE, 0.0, 0.0, 350, 0.0,
                Flight.Condition.NORMAL, 3, "vee", 0.0, 20L);
        // Wingmen are further back (larger Z when heading north) than the leader.
        assertTrue(vee.get(1).zAt(t) > vee.get(0).zAt(t));
        assertTrue(vee.get(2).zAt(t) > vee.get(0).zAt(t));
        assertTrue(vee.get(1).xAt(t) * vee.get(2).xAt(t) < 0, "wingmen on opposite sides");
    }

    @Test
    @DisplayName("bad requests give nothing, a missing condition means normal")
    void edgeCases() {
        assertTrue(FlightRequests.build(null, 0, 0, 350, 0, null, 1, "vee", 0, 1L).isEmpty());
        assertTrue(FlightRequests.build(TYPE, 0, 0, 350, 0, null, 0, "vee", 0, 1L).isEmpty());
        Flight f = FlightRequests.build(TYPE, 0, 0, 0, 0, null, 1, "vee", 0, 1L).get(0);
        assertEquals(Flight.Condition.NORMAL, f.condition);
    }

    @Test
    @DisplayName("manual traffic forgets flights once their trail window has passed")
    void manualTrafficExpiry() {
        ManualTraffic traffic = new ManualTraffic();
        Flight f = new Flight(5L, TYPE, 0, 0, 0, 10000, 250, 0.0, 100.0, 0, 1);
        traffic.add(f);
        assertEquals(1, traffic.collect(50.0).size());
        assertEquals(0, traffic.collect(-10.0).size(), "not started yet, but kept");
        assertEquals(1, traffic.size());
        assertEquals(1, traffic.collect(150.0, 100.0).size(), "landed, trail still hanging");
        assertEquals(0, traffic.collect(250.0, 100.0).size());
        assertEquals(0, traffic.size());
        traffic.add(f);
        assertTrue(traffic.remove(5L));
        assertFalse(traffic.remove(5L));
        traffic.add(f);
        assertEquals(1, traffic.clear());
    }
}
