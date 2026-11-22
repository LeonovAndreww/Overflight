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

    @Test
    @DisplayName("edge wobble is bounded, varies in a fresh trail and vanishes in an old one")
    void edgeWobbleBounds() {
        dev.overflight.core.trail.TrailPoint p = new dev.overflight.core.trail.TrailPoint();
        double min = 10.0;
        double max = -10.0;
        p.age = 5.0;
        p.life = 0.0;
        for (int i = 0; i < 400; i++) {
            p.emitTime = i * 0.37;
            double w = TrailMeshBuilder.edgeWobble(p);
            min = Math.min(min, w);
            max = Math.max(max, w);
        }
        assertTrue(min >= 0.89 && max <= 1.11, "fresh range " + min + ".." + max);
        assertTrue(max - min > 0.05, "fresh trail should wobble, got " + (max - min));
        p.age = 5000.0;
        p.life = 1.0;
        for (int i = 0; i < 50; i++) {
            p.emitTime = i * 1.3;
            assertEquals(1.0, TrailMeshBuilder.edgeWobble(p), 1.0e-9);
        }
    }

    @Test
    @DisplayName("haze leaves near trails alone and fades far ones monotonically")
    void hazeFades() {
        assertEquals(1.0, SkyProjection.haze(10000.0), 1.0e-12);
        assertEquals(0.4, SkyProjection.haze(300000.0), 1.0e-12);
        assertEquals(0.4, SkyProjection.haze(900000.0), 1.0e-12);
        double last = 1.0;
        for (double d = 0; d < 400000.0; d += 5000.0) {
            double h = SkyProjection.haze(d);
            assertTrue(h <= last + 1.0e-12);
            last = h;
        }
    }
}
