package dev.overflight.core.traffic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sky has to be the same for everyone.
 *
 * Nothing about the traffic is stored or sent: every client works it out from
 * the dimension and the clock. That only holds if the generator is a pure
 * function of its inputs, so determinism is not a nicety here, it is the whole
 * mechanism by which two players see the same aircraft.
 */
class TrafficGeneratorTest {

    private static final long SEED = 0xC0FFEEL;
    private static final double RADIUS = 150000.0;

    private final TrafficGenerator generator = new TrafficGenerator(AircraftCatalog.defaults());

    @Test
    @DisplayName("the same question always gives the same sky")
    void deterministic() {
        List<Flight> first = generator.collect(SEED, 12345.0, 0.0, 0.0, RADIUS, 45.0, 64);
        List<Flight> second = generator.collect(SEED, 12345.0, 0.0, 0.0, RADIUS, 45.0, 64);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).id, second.get(i).id);
            assertEquals(first.get(i).xAt(12345.0), second.get(i).xAt(12345.0), 0.0);
            assertEquals(first.get(i).altitudeM, second.get(i).altitudeM, 0.0);
        }
        assertTrue(first.size() > 0, "the default density should put something up");
    }

    @Test
    @DisplayName("a different dimension gets a different sky")
    void seedChangesEverything() {
        List<Flight> here = generator.collect(SEED, 12345.0, 0.0, 0.0, RADIUS, 45.0, 64);
        List<Flight> elsewhere = generator.collect(SEED + 1, 12345.0, 0.0, 0.0, RADIUS, 45.0, 64);

        boolean identical = here.size() == elsewhere.size();
        for (int i = 0; identical && i < here.size(); i++) {
            identical = here.get(i).id == elsewhere.get(i).id;
        }
        assertTrue(!identical, "two seeds produced the same traffic");
    }

    @Test
    @DisplayName("eastbound on odd levels, westbound on even, without exception")
    void semicircularRule() {
        int checked = 0;
        for (double time = 3600.0; time < 200000.0; time += 311.0) {
            for (Flight flight : generator.collect(SEED, time, 0.0, 0.0, RADIUS, 45.0, 64)) {
                int flightLevel = (int) Math.round(flight.altitudeM / 30.48);
                boolean eastbound = Math.toDegrees(flight.heading) < 180.0;
                boolean odd = ((flightLevel / 10) % 2) != 0;

                assertEquals(eastbound, odd,
                        "FL" + flightLevel + " on heading "
                                + Math.toDegrees(flight.heading) + " breaks the rule");
                checked++;
            }
        }
        assertTrue(checked > 500, "expected a decent sample, saw " + checked);
    }

    @Test
    @DisplayName("no traffic means no traffic")
    void zeroDensityIsEmpty() {
        for (double time = 3600.0; time < 40000.0; time += 137.0) {
            assertTrue(generator.collect(SEED, time, 0.0, 0.0, RADIUS, 0.0, 64).isEmpty());
        }
    }

    @Test
    @DisplayName("busier airspace really is busier")
    void densityScales() {
        assertTrue(averageCount(4.0) < averageCount(45.0));
        assertTrue(averageCount(45.0) < averageCount(200.0));
    }

    @Test
    @DisplayName("the cap is honoured however dense the sky gets")
    void respectsTheCap() {
        for (double time = 3600.0; time < 40000.0; time += 211.0) {
            assertTrue(generator.collect(SEED, time, 0.0, 0.0, RADIUS, 2000.0, 12).size() <= 12);
        }
    }

    @Test
    @DisplayName("every aircraft sits inside its type's cruise band")
    void cruiseLevelsAreSensible() {
        for (double time = 3600.0; time < 80000.0; time += 271.0) {
            for (Flight flight : generator.collect(SEED, time, 0.0, 0.0, RADIUS, 120.0, 64)) {
                int flightLevel = (int) Math.round(flight.altitudeM / 30.48);
                AircraftType type = flight.type;
                // The semicircular rule can nudge a level by ten either way.
                assertTrue(flightLevel >= type.minFlightLevel - 10
                                && flightLevel <= type.maxFlightLevel + 10,
                        type.id + " at FL" + flightLevel + ", band is FL"
                                + type.minFlightLevel + " to FL" + type.maxFlightLevel);
            }
        }
    }

    private double averageCount(double densityPerHour) {
        int total = 0;
        int samples = 0;
        for (double time = 3600.0; time < 60000.0; time += 137.0) {
            total += generator.collect(SEED, time, 0.0, 0.0, RADIUS, densityPerHour, 256).size();
            samples++;
        }
        return (double) total / samples;
    }
}
