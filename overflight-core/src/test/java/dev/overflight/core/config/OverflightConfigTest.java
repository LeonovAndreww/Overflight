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
}
