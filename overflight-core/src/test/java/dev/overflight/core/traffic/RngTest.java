package dev.overflight.core.traffic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generator everything else is derived from. */
class RngTest {

    @Test
    @DisplayName("the same seed replays exactly")
    void reproducible() {
        Rng first = new Rng(42L);
        Rng second = new Rng(42L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(first.nextLong(), second.nextLong());
        }
    }

    @Test
    @DisplayName("mixing is order-sensitive and stable")
    void seedMixing() {
        assertEquals(Rng.seedOf(7L, 1L, 2L), Rng.seedOf(7L, 1L, 2L));
        assertTrue(Rng.seedOf(7L, 1L, 2L) != Rng.seedOf(7L, 2L, 1L),
                "cell (1,2) and cell (2,1) must not share a seed");
        assertTrue(Rng.seedOf(7L, 1L, 2L) != Rng.seedOf(8L, 1L, 2L));
    }

    @Test
    @DisplayName("uniform draws stay in range")
    void uniformRange() {
        Rng rng = new Rng(3L);
        for (int i = 0; i < 100000; i++) {
            double value = rng.nextDouble();
            assertTrue(value >= 0.0 && value < 1.0, "out of range: " + value);
            assertTrue(rng.nextInt(10) >= 0);
            assertTrue(rng.nextInt(10) < 10);
        }
    }

    @Test
    @DisplayName("the Poisson draw has the mean it was asked for")
    void poissonMean() {
        for (double mean : new double[]{0.5, 2.0, 9.0, 19.0}) {
            assertMean(mean, 0.06);
        }
    }

    @Test
    @DisplayName("and keeps it past the point where the walk gives way to the approximation")
    void poissonStaysHonestAtHighMeans() {
        // A configuration is allowed to ask for a very dense sky. Capping the
        // count rather than drawing it would quietly hold the traffic down.
        for (double mean : new double[]{25.0, 90.0, 400.0}) {
            assertMean(mean, 0.05);
        }
    }

    @Test
    @DisplayName("a mean of nothing draws nothing")
    void poissonZero() {
        Rng rng = new Rng(11L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(0, rng.poisson(0.0));
            assertEquals(0, rng.poisson(-1.0));
        }
    }

    private static void assertMean(double mean, double tolerance) {
        Rng rng = new Rng(Double.doubleToLongBits(mean));
        long total = 0;
        int draws = 40000;
        for (int i = 0; i < draws; i++) {
            int drawn = rng.poisson(mean);
            assertTrue(drawn >= 0);
            total += drawn;
        }
        double observed = (double) total / draws;
        assertEquals(mean, observed, mean * tolerance,
                "asked for a mean of " + mean + ", drew " + observed);
    }
}
