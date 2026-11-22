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

/** The ribbon a trail is drawn with: stage crossfade and basic output sanity. */
class TrailMeshBuilderTest {

    private static Flight overhead() {
        AircraftType type = AircraftCatalog.defaults().byId("airliner_widebody");
        double altitude = Isa.flightLevelToMetres(350);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        return new Flight(42L, type, -200000.0, 0.0, Math.toRadians(90.0), altitude, speed,
                0.0, 3600.0, 0, 1);
    }

    private static MeshBuffer build(double timeS, double illumination, boolean spine) {
        TrailSettings settings = new TrailSettings();
        Trail trail = new TrailSampler().sample(overhead(), timeS, true, true, settings);
        TrailMeshBuilder builder = new TrailMeshBuilder();
        builder.spine = spine;
        MeshBuffer out = new MeshBuffer();
        builder.build(trail, 0, 64, 0, 0.3, 0.8, 0.5, illumination,
                new SkyProjection(512.0), settings, out);
        return out;
    }

    @Test
    @DisplayName("stage weights always sum to one")
    void weightsSumToOne() {
        for (boolean spine : new boolean[] {false, true}) {
            for (double breakup = 0.0; breakup <= 1.0; breakup += 0.05) {
                float sum = 0;
                for (int stage = 0; stage < 3; stage++) {
                    float w = TrailMeshBuilder.stageWeight(stage, breakup, spine);
                    assertTrue(w >= 0.0f && w <= 1.0f);
                    sum += w;
                }
                assertEquals(1.0f, sum, 1.0e-5f);
            }
        }
        // A spine never takes the aged stage.
        assertEquals(0.0f, TrailMeshBuilder.stageWeight(2, 1.0, true));
    }

    @Test
    @DisplayName("a trail yields quads, and none in the dark")
    void drawsOnlyWhenLit() {
        assertTrue(build(600.0, 1.0, false).quadCount() > 0);
        assertEquals(0, build(600.0, 0.0, false).quadCount());
    }

    @Test
    @DisplayName("every vertex is finite")
    void finiteOutput() {
        for (double t : new double[] {60.0, 600.0, 1800.0}) {
            MeshBuffer m = build(t, 1.0, false);
            for (float v : m.positions()) {
                assertTrue(Float.isFinite(v));
            }
            for (float v : m.colours()) {
                assertTrue(Float.isFinite(v));
            }
        }
    }
}
