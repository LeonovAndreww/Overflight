package dev.overflight.core.render;

import dev.overflight.core.atmo.Isa;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailSampler;
import dev.overflight.core.trail.TrailSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sprites an ageing trail is drawn with.
 *
 * What matters most is that they hold still. Each belongs to a moment of
 * emission; if that link broke they would be rebuilt in new places every frame
 * and the cloud would boil.
 */
class TrailPuffBuilderTest {

    private final TrailSampler sampler = new TrailSampler();
    private final TrailPuffBuilder builder = new TrailPuffBuilder();
    private final SkyProjection projection = new SkyProjection(512.0);

    private static Flight overhead(double startTime) {
        AircraftType type = AircraftCatalog.defaults().byId("airliner_widebody");
        double altitude = Isa.flightLevelToMetres(350);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        return new Flight(42L, type, -200000.0, 0.0, Math.toRadians(90.0), altitude, speed,
                startTime, 3600.0, 0, 1);
    }

    private MeshBuffer build(Flight flight, double timeS) {
        TrailSettings settings = new TrailSettings();
        Trail trail = sampler.sample(flight, timeS, true, true, settings);
        MeshBuffer out = new MeshBuffer();
        builder.build(trail, 0, 64, 0, 0.3, 0.8, 0.5, 1.0, 1.0, 1.0, 1.0,
                projection, settings, out);
        return out;
    }

    @Test
    @DisplayName("a fresh trail is a line and gets no sprites")
    void freshTrailHasNone() {
        assertEquals(0, build(overhead(0.0), 90.0).quadCount());
    }

    @Test
    @DisplayName("an old trail spreads into sprites")
    void oldTrailHasThem() {
        assertTrue(build(overhead(0.0), 1800.0).quadCount() > 100);
    }

    @Test
    @DisplayName("the sprites hold still from one frame to the next")
    void spritesHoldStill() {
        Flight flight = overhead(0.0);
        MeshBuffer before = build(flight, 1800.0);
        MeshBuffer after = build(flight, 1800.05);

        // A twentieth of a second later: the same sprites, near enough in the
        // same places. Anything else would be the cloud boiling.
        int difference = Math.abs(before.quadCount() - after.quadCount());
        assertTrue(difference <= before.quadCount() / 50,
                before.quadCount() + " sprites became " + after.quadCount());

        float[] a = before.positions();
        float[] b = after.positions();
        int compared = Math.min(before.quadCount(), after.quadCount()) * 4;
        int moved = 0;
        for (int v = 0; v < compared; v++) {
            double dx = a[v * 3] - b[v * 3];
            double dy = a[v * 3 + 1] - b[v * 3 + 1];
            double dz = a[v * 3 + 2] - b[v * 3 + 2];
            // On a shell of 512 blocks, a tenth of a block is about a pixel.
            if (dx * dx + dy * dy + dz * dz > 0.1 * 0.1) {
                moved++;
            }
        }
        assertTrue(moved <= compared / 20, moved + " of " + compared + " corners jumped");
    }
}
