package dev.overflight.core.atmo;

import dev.overflight.core.atmo.SchmidtAppleman.EngineProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The criterion that decides whether an aircraft leaves anything behind it.
 *
 * The headline behaviour is the altitude threshold: contrails are a phenomenon
 * of the upper troposphere and simply do not happen lower down, whatever the
 * humidity. If that ever stops being true the sky is wrong in a way no amount of
 * tuning elsewhere will fix.
 */
class SchmidtApplemanTest {

    private static boolean formsAt(int flightLevel, double relativeHumidity, EngineProfile engine) {
        double altitude = Isa.flightLevelToMetres(flightLevel);
        return SchmidtAppleman.formsContrail(
                Isa.temperature(altitude), Isa.pressure(altitude), relativeHumidity, engine);
    }

    @Test
    @DisplayName("nothing trails in the lower troposphere, however humid")
    void nothingLowDown() {
        EngineProfile turbofan = EngineProfile.modernTurbofan();
        for (int flightLevel = 50; flightLevel <= 250; flightLevel += 50) {
            assertFalse(formsAt(flightLevel, 1.0, turbofan),
                    "FL" + flightLevel + " should never trail, even saturated");
        }
    }

    @Test
    @DisplayName("airliners trail from about FL300 upwards")
    void trailsAtCruise() {
        EngineProfile turbofan = EngineProfile.modernTurbofan();
        assertFalse(formsAt(290, 0.45, turbofan));
        assertTrue(formsAt(330, 0.45, turbofan));
        assertTrue(formsAt(350, 0.45, turbofan));
        assertTrue(formsAt(390, 0.20, turbofan));
    }

    @Test
    @DisplayName("an efficient engine trails in warmer air than a thirsty one")
    void engineEfficiencyMatters() {
        double pressure = Isa.pressure(Isa.flightLevelToMetres(330));
        double modern = SchmidtAppleman.criticalTemperature(
                SchmidtAppleman.mixingLineSlope(pressure, 1.25, 43.0e6, 0.37), 0.5);
        double lowBypass = SchmidtAppleman.criticalTemperature(
                SchmidtAppleman.mixingLineSlope(pressure, 1.25, 43.0e6, 0.22), 0.5);

        // More of the fuel's energy leaves as thrust rather than heat, so the
        // plume cools into saturation sooner.
        assertTrue(modern > lowBypass,
                "a high-bypass turbofan should trail at a warmer temperature: "
                        + modern + " vs " + lowBypass);
    }

    @Test
    @DisplayName("drier air needs colder air, and saturated air needs least")
    void humidityShiftsTheThreshold() {
        double pressure = Isa.pressure(Isa.flightLevelToMetres(350));
        double slope = SchmidtAppleman.mixingLineSlope(pressure, 1.25, 43.0e6, 0.37);

        double saturated = SchmidtAppleman.criticalTemperature(slope, 1.0);
        double half = SchmidtAppleman.criticalTemperature(slope, 0.5);
        double dry = SchmidtAppleman.criticalTemperature(slope, 0.1);

        assertTrue(saturated > half, "saturated air should trail most readily");
        assertTrue(half > dry, "drier air should need colder air still");

        // At saturation the criterion reduces to the closed-form threshold.
        assertEquals(SchmidtAppleman.thresholdTempAtWaterSaturation(slope), saturated, 0.01);
    }

    @Test
    @DisplayName("persistence is a separate question from formation")
    void persistenceIsIndependent() {
        double altitude = Isa.flightLevelToMetres(350);
        double temperature = Isa.temperature(altitude);

        // Both of these form a trail; only one keeps it.
        assertTrue(formsAt(350, 0.30, EngineProfile.modernTurbofan()));
        assertTrue(formsAt(350, 0.70, EngineProfile.modernTurbofan()));

        assertTrue(SchmidtAppleman.persistenceRatio(temperature, 0.30) < 1.0,
                "dry air should let the trail sublimate");
        assertTrue(SchmidtAppleman.persistenceRatio(temperature, 0.70) > 1.0,
                "ice-supersaturated air should hold the trail up");
    }

    @Test
    @DisplayName("the threshold sits near -40 C at cruise, as observed")
    void thresholdIsAboutMinusForty() {
        double pressure = Isa.pressure(Isa.flightLevelToMetres(350));
        double slope = SchmidtAppleman.mixingLineSlope(pressure, 1.25, 43.0e6, 0.37);
        double threshold = SchmidtAppleman.thresholdTempAtWaterSaturation(slope) - 273.15;

        assertTrue(threshold > -45.0 && threshold < -35.0,
                "expected roughly -40 C, got " + threshold);
    }
}
