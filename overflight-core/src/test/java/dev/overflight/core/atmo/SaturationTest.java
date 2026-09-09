package dev.overflight.core.atmo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saturation vapour pressure against values from the literature.
 *
 * These are the numbers everything else rests on: get them wrong and contrails
 * form at the wrong altitude, or never. They are checked against published
 * figures rather than against whatever the code happened to return, so a
 * refactor that quietly changes the physics fails here.
 */
class SaturationTest {

    /** Published saturation pressures are quoted to three figures; allow a per cent. */
    private static final double TOLERANCE = 0.01;

    @Test
    @DisplayName("water and ice agree at the triple point, 611.7 Pa")
    void triplePoint() {
        double water = Saturation.overWater(273.16);
        double ice = Saturation.overIce(273.16);

        assertEquals(611.657, water, 611.657 * TOLERANCE);
        assertEquals(611.657, ice, 611.657 * TOLERANCE);
        // The two curves meet there and nowhere else below it.
        assertEquals(water, ice, 1.0);
    }

    @Test
    @DisplayName("at -40 C, the temperature contrails need, water reads 18.9 Pa and ice 12.8")
    void atContrailTemperatures() {
        double minus40 = 233.15;
        assertEquals(18.9, Saturation.overWater(minus40), 18.9 * 0.02);
        assertEquals(12.8, Saturation.overIce(minus40), 12.8 * 0.02);
    }

    @Test
    @DisplayName("below freezing, ice always saturates lower than water")
    void iceIsAlwaysDrier() {
        for (double tempK = 200.0; tempK < 273.0; tempK += 5.0) {
            assertTrue(Saturation.overIce(tempK) < Saturation.overWater(tempK),
                    "ice should saturate below water at " + tempK + " K");
        }
    }

    @Test
    @DisplayName("that gap is what lets dry-looking air hold a trail up")
    void waterToIceRaisesHumidity() {
        // 60% over water at cruise is past saturation over ice, which is the
        // whole reason a trail can persist in air a hygrometer calls dry.
        double overIce = Saturation.waterToIce(0.60, 218.15);
        assertTrue(overIce > 1.0,
                "60% over water at -55 C should exceed ice saturation, got " + overIce);

        // The same humidity near freezing is not.
        assertTrue(Saturation.waterToIce(0.60, 272.0) < 1.0);
    }

    @Test
    @DisplayName("both curves rise with temperature, everywhere they are used")
    void monotonic() {
        for (double tempK = 190.0; tempK < 300.0; tempK += 1.0) {
            assertTrue(Saturation.overWater(tempK + 1.0) > Saturation.overWater(tempK));
            assertTrue(Saturation.overIce(tempK + 1.0) > Saturation.overIce(tempK));
        }
    }
}
