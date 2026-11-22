package dev.overflight.core.trail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScatteringTest {
    @Test
    void iceBlazesTowardsTheSunAndFadesAway() {
        assertEquals(4.0, Scattering.brightness(1.0, 0.0), 1e-9);
        assertEquals(1.0, Scattering.brightness(0.0, 0.0), 1e-9);
        assertTrue(Scattering.brightness(-1.0, 0.0) < 1.0);
        assertTrue(Scattering.brightness(-1.0, 0.0) >= 0.55);
    }

    @Test
    void sootBarelyBrightens() {
        assertTrue(Scattering.brightness(1.0, 1.0) <= 1.2 + 1e-9);
        assertTrue(Scattering.brightness(-1.0, 1.0) >= 0.8 - 1e-9);
    }

    @Test
    void brightnessIsMonotonicInAngle() {
        double last = 0;
        for (double c = -1; c <= 1.0001; c += 0.05) {
            double b = Scattering.brightness(c, 0.0);
            assertTrue(b >= last - 1e-12);
            last = b;
        }
    }

    @Test
    void shadeStaysGreyWhiteAndReachesWhite() {
        assertEquals(0.76, Scattering.shade(0.55), 1e-9);
        assertEquals(0.76, Scattering.shade(0.0), 1e-9);
        assertEquals(1.0, Scattering.shade(2.0), 1e-9);
        assertEquals(1.0, Scattering.shade(4.0), 1e-9);
    }
}
