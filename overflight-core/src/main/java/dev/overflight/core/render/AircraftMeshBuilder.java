package dev.overflight.core.render;

import dev.overflight.core.traffic.Flight;

/**
 * Draws the aircraft itself.
 *
 * An airliner directly overhead at FL350 subtends about a third of a degree,
 * two thirds the width of the moon, and it is almost always seen from below.
 * That settles the approach: a plan view built from a handful of quads lying in
 * the horizontal plane, sized from the real wingspan and slant range. A detailed
 * model would cost hundreds of triangles to render something the eye reads as a
 * cross with lights on it.
 *
 * Hull quads take the left half of the texture, navigation lights the right.
 */
public final class AircraftMeshBuilder {
    /** Beyond this the aircraft is a speck and only its trail matters. */
    private static final double MAX_RANGE_M = 120000.0;
    /**
     * Smallest the hull is allowed to be drawn, in radians. Real aircraft fade
     * out around here; holding them at a pixel or so keeps distant traffic
     * legible without turning it into a swarm of dots.
     */
    private static final double MIN_ANGULAR_HALF_SIZE = 2.6e-4;
    /**
     * Navigation lights are point sources, so their apparent size comes from
     * glare rather than distance and stays fixed, the way a star does.
     *
     * It has to stay well under the angle the wingtips subtend, or the two
     * lights swell into each other and an airliner reads as one lamp. A wingtip
     * pair is about 6e-3 radians apart overhead, so this leaves a clear gap
     * while still covering a pixel or two on screen.
     */
    private static final double LIGHT_ANGULAR_RADIUS = 6.0e-4;
    /** Strobes flash roughly once a second. */
    private static final double STROBE_PERIOD_S = 1.15;
    private static final double STROBE_DUTY = 0.07;

    private static final float HULL_U0 = 0.0f;
    private static final float HULL_U1 = 0.5f;
    private static final float LIGHT_U0 = 0.5f;
    private static final float LIGHT_U1 = 1.0f;

    private final double[] corner = new double[3];
    private final float[] v0 = new float[3];
    private final float[] v1 = new float[3];
    private final float[] v2 = new float[3];
    private final float[] v3 = new float[3];

    /**
     * @param daylight 1 in full day, 0 at night; decides whether the hull is lit
     *                 and whether the navigation lights are worth drawing
     */
    public void build(Flight flight, double timeS, double camX, double camY, double camZ,
                      double sunX, double sunY, double sunZ, double daylight,
                      SkyProjection projection, MeshBuffer out) {
        if (!flight.airborneAt(timeS)) {
            return;
        }

        double x = flight.xAt(timeS);
        double z = flight.zAt(timeS);
        double y = flight.altitudeM;

        double dx = x - camX;
        double dy = y - camY;
        double dz = z - camZ;
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (range > MAX_RANGE_M || range < 1.0) {
            return;
        }

        double forwardX = Math.sin(flight.heading);
        double forwardZ = -Math.cos(flight.heading);
        double rightX = Math.cos(flight.heading);
        double rightZ = Math.sin(flight.heading);

        double span = flight.type.wingspanM;
        double length = span * 1.05;

        // Hold a floor on apparent size rather than on real size, so the aircraft
        // never grows in world terms and its trail still lines up with it.
        double scale = Math.max(1.0, MIN_ANGULAR_HALF_SIZE * range / (span * 0.5));
        span *= scale;
        length *= scale;

        // Lit from below by sky rather than sun, so an airliner reads as pale grey
        // by day and as a dark shape against a bright sky near dusk.
        float tone = (float) (0.55 + 0.35 * daylight);
        float alpha = (float) (0.30 + 0.70 * daylight);

        // Fuselage.
        quad(out, projection, camX, camY, camZ,
                x, y, z, forwardX, forwardZ, rightX, rightZ,
                length * 0.5, span * 0.055, 0.0,
                HULL_U0, HULL_U1, tone, tone, tone, alpha);

        // Wing, set a little aft of centre the way a swept wing sits.
        quad(out, projection, camX, camY, camZ,
                x, y, z, forwardX, forwardZ, rightX, rightZ,
                span * 0.085, span * 0.5, -length * 0.06,
                HULL_U0, HULL_U1, tone, tone, tone, alpha);

        // Tailplane.
        quad(out, projection, camX, camY, camZ,
                x, y, z, forwardX, forwardZ, rightX, rightZ,
                span * 0.045, span * 0.19, -length * 0.42,
                HULL_U0, HULL_U1, tone, tone, tone, alpha);

        if (daylight > 0.55) {
            return;
        }

        // Navigation lights: red on the left wingtip, green on the right, and a
        // white strobe at the tail. Their glow is fixed in angle, so they stay
        // visible after the hull itself has shrunk to nothing.
        float lightAlpha = (float) (1.0 - daylight / 0.55);
        double glow = LIGHT_ANGULAR_RADIUS * range;
        double wingBack = -length * 0.06;

        light(out, projection, camX, camY, camZ, x, y, z, forwardX, forwardZ, rightX, rightZ,
                -span * 0.5, wingBack, glow, 1.0f, 0.13f, 0.10f, lightAlpha);
        light(out, projection, camX, camY, camZ, x, y, z, forwardX, forwardZ, rightX, rightZ,
                span * 0.5, wingBack, glow, 0.16f, 1.0f, 0.24f, lightAlpha);

        double phase = (timeS / STROBE_PERIOD_S) % 1.0;
        if (phase < STROBE_DUTY) {
            light(out, projection, camX, camY, camZ, x, y, z,
                    forwardX, forwardZ, rightX, rightZ,
                    0.0, -length * 0.5, glow * 1.5, 1.0f, 1.0f, 1.0f, lightAlpha);
        }
    }

    private void light(MeshBuffer out, SkyProjection projection,
                       double camX, double camY, double camZ,
                       double x, double y, double z,
                       double forwardX, double forwardZ, double rightX, double rightZ,
                       double lateral, double alongOffset, double radius,
                       float r, float g, float b, float alpha) {
        double lx = x + rightX * lateral + forwardX * alongOffset;
        double lz = z + rightZ * lateral + forwardZ * alongOffset;
        quad(out, projection, camX, camY, camZ, lx, y, lz,
                forwardX, forwardZ, rightX, rightZ, radius, radius, 0.0,
                LIGHT_U0, LIGHT_U1, r, g, b, alpha);
    }

    /**
     * One flat rectangle in the horizontal plane at the aircraft's altitude,
     * projected corner by corner so it lands at the right angular size.
     */
    private void quad(MeshBuffer out, SkyProjection projection,
                      double camX, double camY, double camZ,
                      double x, double y, double z,
                      double forwardX, double forwardZ, double rightX, double rightZ,
                      double halfLength, double halfWidth, double alongOffset,
                      float u0, float u1, float r, float g, float b, float alpha) {
        double cx = x + forwardX * alongOffset;
        double cz = z + forwardZ * alongOffset;

        project(projection, camX, camY, camZ, cx, y, cz,
                forwardX, forwardZ, rightX, rightZ, -halfLength, -halfWidth, v0);
        project(projection, camX, camY, camZ, cx, y, cz,
                forwardX, forwardZ, rightX, rightZ, -halfLength, halfWidth, v1);
        project(projection, camX, camY, camZ, cx, y, cz,
                forwardX, forwardZ, rightX, rightZ, halfLength, halfWidth, v2);
        project(projection, camX, camY, camZ, cx, y, cz,
                forwardX, forwardZ, rightX, rightZ, halfLength, -halfWidth, v3);

        out.quad(v0, v1, v2, v3, u0, u1, 0.0f, 1.0f, r, g, b, alpha, alpha);
    }

    private void project(SkyProjection projection, double camX, double camY, double camZ,
                         double cx, double cy, double cz,
                         double forwardX, double forwardZ, double rightX, double rightZ,
                         double along, double across, float[] target) {
        double px = cx + forwardX * along + rightX * across;
        double pz = cz + forwardZ * along + rightZ * across;
        projection.project(px, cy, pz, camX, camY, camZ, corner);
        target[0] = (float) corner[0];
        target[1] = (float) corner[1];
        target[2] = (float) corner[2];
    }
}
