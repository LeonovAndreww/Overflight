package dev.overflight.core.atmo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why contrails are still burning overhead after the ground has gone dark.
 *
 * The effect is geometric and worth getting right rather than approximating:
 * from ten kilometres up you can see further round the earth, so the sun sets
 * later there. It is the reason an evening sky keeps its trails, and the reason
 * the highest ones are the last to go out.
 */
class IlluminationTest {

    @Test
    @DisplayName("standing on the ground, the sun sets when it sets")
    void noOffsetAtSeaLevel() {
        assertEquals(0.0, Illumination.shadowOffsetDegrees(0.0), 1.0e-9);
    }

    @Test
    @DisplayName("at airliner cruise the sun is visible three degrees further down")
    void offsetAtCruise() {
        // acos(R / (R + 10668 m)) for a 6371 km earth.
        assertEquals(3.32, Illumination.shadowOffsetDegrees(Isa.flightLevelToMetres(350)), 0.05);
    }

    @Test
    @DisplayName("higher stays lit longer, without exception")
    void higherSeesFurther() {
        double previous = -1.0;
        for (int flightLevel = 0; flightLevel <= 700; flightLevel += 50) {
            double offset = Illumination.shadowOffsetDegrees(
                    Isa.flightLevelToMetres(flightLevel));
            assertTrue(offset > previous,
                    "FL" + flightLevel + " should see further than the level below");
            previous = offset;
        }
    }

    @Test
    @DisplayName("after sunset the high trails are lit and the low ones are not")
    void theSkyEmptiesFromTheBottom() {
        // The sun two and a half degrees below the observer's horizon: gone from
        // the ground, still up as far as the traffic is concerned.
        double sunElevation = -2.5;

        double high = Illumination.sunlight(Illumination.effectiveElevationDegrees(
                sunElevation, Isa.flightLevelToMetres(410)));
        double cruise = Illumination.sunlight(Illumination.effectiveElevationDegrees(
                sunElevation, Isa.flightLevelToMetres(350)));
        double low = Illumination.sunlight(Illumination.effectiveElevationDegrees(
                sunElevation, Isa.flightLevelToMetres(150)));
        double ground = Illumination.sunlight(
                Illumination.effectiveElevationDegrees(sunElevation, 0.0));

        assertTrue(high > 0.0, "a trail at FL410 should still be in sunlight");
        assertTrue(high > cruise, "and brighter than one below it");
        assertTrue(cruise > low, "which is in turn brighter than one lower still");
        assertEquals(0.0, ground, 1.0e-9,
                "while the observer underneath has lost the sun entirely");
    }

    @Test
    @DisplayName("full daylight is full daylight")
    void middayIsUnshaded() {
        assertEquals(1.0, Illumination.sunlight(
                Illumination.effectiveElevationDegrees(45.0, Isa.flightLevelToMetres(350))),
                1.0e-9);
    }

    @Test
    @DisplayName("deep night lights nothing")
    void nightIsNight() {
        assertEquals(0.0, Illumination.sunlight(
                Illumination.effectiveElevationDegrees(-30.0, Isa.flightLevelToMetres(410))),
                1.0e-9);
    }

    @Test
    @DisplayName("the light reddens as the sun drops and is white overhead")
    void warmthFollowsTheSun() {
        assertEquals(0.0, Illumination.warmth(30.0), 1.0e-9);
        assertTrue(Illumination.warmth(3.0) > Illumination.warmth(8.0));
        assertTrue(Illumination.warmth(0.0) > Illumination.warmth(3.0));
        assertEquals(1.0, Illumination.warmth(-2.0), 1.0e-9);
    }

    @Test
    @DisplayName("midday is white, sunset is orange, and the shadow is moonlit")
    void colours() {
        double[] tint = new double[3];

        Illumination.tint(1.0, Illumination.warmth(40.0), tint);
        assertEquals(1.0, tint[0], 1.0e-6);
        assertEquals(1.0, tint[1], 1.0e-6);
        assertEquals(1.0, tint[2], 1.0e-6);

        Illumination.tint(1.0, Illumination.warmth(-0.5), tint);
        assertTrue(tint[0] > tint[1] && tint[1] > tint[2],
                "low sunlight should run red through orange, got "
                        + tint[0] + "/" + tint[1] + "/" + tint[2]);

        Illumination.tint(0.0, 1.0, tint);
        assertTrue(tint[2] > tint[0], "moonlight should read cooler than daylight");
    }

    @Test
    @DisplayName("the colour moves smoothly, with no step anywhere")
    void noSuddenChanges() {
        double[] tint = new double[3];
        double[] previous = null;

        for (double elevation = 20.0; elevation > -8.0; elevation -= 0.1) {
            double sunlit = Illumination.sunlight(elevation);
            Illumination.tint(sunlit, Illumination.warmth(elevation), tint);
            if (previous != null) {
                for (int channel = 0; channel < 3; channel++) {
                    assertTrue(Math.abs(tint[channel] - previous[channel]) < 0.05,
                            "colour jumped at " + elevation + " degrees");
                }
            }
            previous = tint.clone();
        }
    }
}
