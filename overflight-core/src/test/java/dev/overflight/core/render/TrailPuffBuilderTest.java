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
        // Under 45 s: before even the vortex loops, the earliest sprites.
        assertEquals(0, build(overhead(0.0), 40.0).quadCount());
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

        // Matched by position rather than by order: a sprite appearing at the
        // young end shifts the order of everything after it without moving
        // anything on screen.
        float[] a = before.positions();
        float[] b = after.positions();
        java.util.Map<Long, java.util.List<Integer>> cells = new java.util.HashMap<>();
        for (int v = 0; v < before.vertexCount(); v++) {
            cells.computeIfAbsent(cell(a[v * 3], a[v * 3 + 1], a[v * 3 + 2]),
                    key -> new java.util.ArrayList<>()).add(v);
        }
        int compared = after.vertexCount();
        int moved = 0;
        for (int v = 0; v < compared; v++) {
            if (!nearAny(b[v * 3], b[v * 3 + 1], b[v * 3 + 2], a, cells)) {
                moved++;
            }
        }
        assertTrue(moved <= compared / 20, moved + " of " + compared + " corners jumped");
    }

    /** On a shell of 512 blocks, a tenth of a block is about a pixel. */
    private static final double TOLERANCE = 0.1;

    private static long cell(double x, double y, double z) {
        long cx = (long) Math.floor(x / TOLERANCE);
        long cy = (long) Math.floor(y / TOLERANCE);
        long cz = (long) Math.floor(z / TOLERANCE);
        return (cx * 73856093L) ^ (cy * 19349663L) ^ (cz * 83492791L);
    }

    private static boolean nearAny(double x, double y, double z, float[] a,
                                   java.util.Map<Long, java.util.List<Integer>> cells) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    java.util.List<Integer> list = cells.get(cell(x + dx * TOLERANCE,
                            y + dy * TOLERANCE, z + dz * TOLERANCE));
                    if (list == null) {
                        continue;
                    }
                    for (int v : list) {
                        double ex = a[v * 3] - x, ey = a[v * 3 + 1] - y, ez = a[v * 3 + 2] - z;
                        if (ex * ex + ey * ey + ez * ez <= TOLERANCE * TOLERANCE) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
