package dev.overflight.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OverflightConfigTest {

    @Test
    void chemtrailKeepsItsOwnTrafficDensity() {
        OverflightConfig c = new OverflightConfig();
        c.preset = "chemtrail";
        c.applyPreset();
        assertEquals(220.0, c.traffic.densityPerHour, 0.0);
        assertEquals(64, c.traffic.maxAircraft);
    }

    @Test
    void trafficPresetAppliesUnderRealisticLook() {
        OverflightConfig c = new OverflightConfig();
        c.traffic.preset = "quiet";
        c.applyPreset();
        assertEquals(40.0, c.traffic.densityPerHour, 0.0);
    }

    @Test
    void legacyLookNameSelectsTraffic() {
        OverflightConfig c = new OverflightConfig();
        c.preset = "busy";
        c.applyPreset();
        assertEquals("busy", c.traffic.preset);
        assertEquals(700.0, c.traffic.densityPerHour, 0.0);
    }

    @Test
    void customLeavesEverythingAlone() {
        OverflightConfig c = new OverflightConfig();
        c.preset = "custom";
        c.traffic.preset = "custom";
        c.traffic.densityPerHour = 77.0;
        c.applyPreset();
        assertEquals(77.0, c.traffic.densityPerHour, 0.0);
    }

    @Test
    void leavingChemtrailRestoresLookFields() {
        for (String look : new String[] {"realistic", "fancy"}) {
            OverflightConfig c = new OverflightConfig();
            c.preset = "chemtrail";
            c.applyPreset();
            c.preset = look;
            c.applyPreset();
            assertEquals(0.85, c.trails.opacity, 0.0, look);
            assertEquals(0.9, c.trails.spreadRateMPerSec, 0.0, look);
            assertEquals(192, c.graphics.trailDetail, look);
        }
    }

    @Test
    void sanitiseClampsRatios() {
        OverflightConfig c = new OverflightConfig();
        c.trails.nightVisibility = 7;
        c.trails.dayAlphaCap = -1;
        c.trails.windSpeedMs = 1e9;
        c.sanitise();
        assertEquals(1.0, c.trails.nightVisibility, 0.0);
        assertEquals(0.0, c.trails.dayAlphaCap, 0.0);
        assertEquals(200.0, c.trails.windSpeedMs, 0.0);
    }

    @Test
    void sanitiseReplacesNaN() {
        OverflightConfig c = new OverflightConfig();
        c.trails.opacity = Double.NaN;
        c.graphics.trailDetail = 0;
        c.sanitise();
        assertEquals(0.0, c.trails.opacity, 0.0);
        assertEquals(8, c.graphics.trailDetail);
    }

    @Test
    void sanitiseRestoresNonFiniteWindDirection() {
        OverflightConfig c = new OverflightConfig();
        c.trails.windDirectionDeg = Double.NaN;
        c.sanitise();
        assertEquals(270.0, c.trails.windDirectionDeg, 0.0);
        c.trails.windDirectionDeg = Double.POSITIVE_INFINITY;
        c.sanitise();
        assertEquals(270.0, c.trails.windDirectionDeg, 0.0);
    }

    @Test
    void leavingColdwarDropsItsMixButKeepsHandEdits() {
        OverflightConfig c = new OverflightConfig();
        c.traffic.preset = "coldwar";
        c.applyPreset();
        assertEquals(18.0, c.traffic.mix.get("military_transport"), 0.0);
        c.traffic.mix.put("bizjet", 5.0); // hand-edited
        c.traffic.preset = "modern";
        c.applyPreset();
        assertEquals(java.util.Collections.singletonMap("bizjet", 5.0), c.traffic.mix);
    }
}
