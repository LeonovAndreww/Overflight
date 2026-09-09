package dev.overflight.core.atmo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The standard atmosphere, against the published table. */
class IsaTest {

    @Test
    @DisplayName("sea level is 15 C and 1013.25 hPa")
    void seaLevel() {
        assertEquals(288.15, Isa.temperature(0.0), 1.0e-6);
        assertEquals(101325.0, Isa.pressure(0.0), 1.0e-6);
    }

    @Test
    @DisplayName("the tropopause is -56.5 C at 11 km")
    void tropopause() {
        assertEquals(216.65, Isa.temperature(11000.0), 0.01);
        // Table value 22 632 Pa.
        assertEquals(22632.0, Isa.pressure(11000.0), 22632.0 * 0.01);
    }

    @Test
    @DisplayName("5 km reads -17.5 C and 540 hPa")
    void midTroposphere() {
        assertEquals(255.65, Isa.temperature(5000.0), 0.01);
        assertEquals(54020.0, Isa.pressure(5000.0), 54020.0 * 0.01);
    }

    @Test
    @DisplayName("temperature holds steady above the tropopause")
    void isothermalAbove() {
        assertEquals(Isa.temperature(11000.0), Isa.temperature(15000.0), 1.0e-9);
        assertEquals(Isa.temperature(11000.0), Isa.temperature(20000.0), 1.0e-9);
        // Pressure keeps falling even where temperature does not.
        assertTrue(Isa.pressure(20000.0) < Isa.pressure(15000.0));
        assertTrue(Isa.pressure(15000.0) < Isa.pressure(11000.0));
    }

    @Test
    @DisplayName("FL350 is 10 668 m, by definition")
    void flightLevels() {
        assertEquals(10668.0, Isa.flightLevelToMetres(350), 1.0e-6);
        assertEquals(0.0, Isa.flightLevelToMetres(0), 1.0e-9);
        // A hundred feet per flight level, and a foot is 0.3048 m exactly.
        assertEquals(30.48, Isa.flightLevelToMetres(1), 1.0e-9);
    }

    @Test
    @DisplayName("both curves fall smoothly, with no step at the tropopause")
    void continuous() {
        double justBelow = Isa.pressure(10999.0);
        double justAbove = Isa.pressure(11001.0);
        assertTrue(justAbove < justBelow);
        assertEquals(justBelow, justAbove, justBelow * 0.001);

        for (double altitude = 0.0; altitude < 20000.0; altitude += 250.0) {
            assertTrue(Isa.pressure(altitude + 250.0) < Isa.pressure(altitude));
        }
    }
}
