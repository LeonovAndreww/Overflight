package dev.overflight.core.trail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseTest {
    @Test
    void valueStaysInUnitRangeIncludingNegativeAndHugeInputs() {
        double[] xs = {-1.0e9, -37.25, -0.5, 0.0, 0.5, 12345.678, 1.0e12};
        for (double x : xs) {
            double v = Noise.value(x);
            assertTrue(v >= 0.0 && v <= 1.0, "value(" + x + ") = " + v);
            double s = Noise.signed(x);
            assertTrue(s >= -1.0 && s <= 1.0, "signed(" + x + ") = " + s);
        }
    }

    @Test
    void isDeterministicAndContinuousAcrossCellBoundaries() {
        assertEquals(Noise.value(3.7), Noise.value(3.7));
        for (int cell = -5; cell <= 5; cell++) {
            double below = Noise.value(cell - 1.0e-9);
            double at = Noise.value(cell);
            assertEquals(below, at, 1.0e-6, "jump at " + cell);
        }
    }

    @Test
    void nonFiniteInputDoesNotThrow() {
        double v = Noise.value(Double.NaN);
        assertTrue(Double.isNaN(v) || (v >= 0.0 && v <= 1.0));
    }
}
