package dev.overflight.core.atmo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invented atmosphere.
 *
 * One number here decides how the sky looks: the fraction of it that is
 * supersaturated over ice, and so able to hold a trail up. The field is placed
 * so that fraction comes out as configured rather than as whatever the noise
 * happened to give, and that placement is what these tests hold to.
 */
class HumidityFieldTest {

    private static final double CRUISE = Isa.flightLevelToMetres(350);

    /** Measured share of the sky where a contrail would persist. */
    private static double supersaturatedShare(HumidityField field) {
        double temperature = Isa.temperature(CRUISE);
        int supersaturated = 0;
        int samples = 0;

        for (int i = 0; i < 120; i++) {
            for (int j = 0; j < 120; j++) {
                double x = i * 23000.0;
                double z = j * 19000.0;
                double humidity = field.relativeHumidity(x, z, CRUISE, i * 37.0, 0.0);
                if (SchmidtAppleman.persistenceRatio(temperature, humidity) > 1.0) {
                    supersaturated++;
                }
                samples++;
            }
        }
        return (double) supersaturated / samples;
    }

    @Test
    @DisplayName("the sky holds trails up as often as it was told to")
    void calibratedToTheConfiguredFraction() {
        for (double target : new double[]{0.10, 0.22, 0.45}) {
            HumidityField field = new HumidityField(0xC0FFEEL);
            field.supersaturatedFraction = target;

            double measured = supersaturatedShare(field);
            assertEquals(target, measured, 0.08,
                    "asked for " + target + " of the sky, measured " + measured);
        }
    }

    @Test
    @DisplayName("a sky set to never hold a trail does not")
    void zeroMeansNever() {
        HumidityField field = new HumidityField(1L);
        field.supersaturatedFraction = 0.0;
        assertTrue(supersaturatedShare(field) < 0.02);
    }

    @Test
    @DisplayName("the field is smooth, not speckled")
    void neighbouringAirIsSimilar() {
        HumidityField field = new HumidityField(99L);
        double worst = 0.0;

        for (int i = 0; i < 500; i++) {
            double x = i * 3100.0;
            double here = field.relativeHumidity(x, 0.0, CRUISE, 0.0, 0.0);
            double nearby = field.relativeHumidity(x + 2000.0, 0.0, CRUISE, 0.0, 0.0);
            worst = Math.max(worst, Math.abs(here - nearby));
        }

        // Patches are hundreds of kilometres across, so two kilometres apart the
        // air should barely differ. Anything larger would put a hard edge in the
        // sky where one aircraft trails and the one beside it does not.
        assertTrue(worst < 0.05, "humidity jumped by " + worst + " over two kilometres");
    }

    @Test
    @DisplayName("the same sky for everyone, and a different one per dimension")
    void deterministicPerSeed() {
        HumidityField first = new HumidityField(5L);
        HumidityField same = new HumidityField(5L);
        HumidityField other = new HumidityField(6L);

        boolean differs = false;
        for (int i = 0; i < 200; i++) {
            double x = i * 7000.0;
            assertEquals(first.relativeHumidity(x, 0.0, CRUISE, 0.0, 0.0),
                    same.relativeHumidity(x, 0.0, CRUISE, 0.0, 0.0), 0.0);
            differs |= Math.abs(first.relativeHumidity(x, 0.0, CRUISE, 0.0, 0.0)
                    - other.relativeHumidity(x, 0.0, CRUISE, 0.0, 0.0)) > 1.0e-6;
        }
        assertTrue(differs, "two seeds produced the same weather");
    }

    @Test
    @DisplayName("rain raises the humidity aloft")
    void weatherPushesItUp() {
        HumidityField field = new HumidityField(17L);
        double dry = 0.0;
        double wet = 0.0;

        for (int i = 0; i < 300; i++) {
            double x = i * 11000.0;
            dry += field.relativeHumidity(x, 0.0, CRUISE, 0.0, 0.0);
            wet += field.relativeHumidity(x, 0.0, CRUISE, 0.0, 1.0);
        }
        assertTrue(wet > dry, "a downpour should leave the air aloft damper, not drier");
    }
}
