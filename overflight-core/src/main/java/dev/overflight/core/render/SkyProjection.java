package dev.overflight.core.render;

/**
 * Maps things that are genuinely kilometres away onto a shell drawn a few
 * hundred blocks from the camera.
 *
 * An aircraft at FL350 is 10 668 m up, thirty times the build limit, and no
 * renderer will draw geometry out there. Instead everything is placed on a
 * sphere of fixed radius in the direction it really lies, and scaled so it
 * subtends the angle it really would. The sky looks correct, nothing approaches
 * the far clip plane, and level-of-detail mods that rewrite the terrain depth
 * range never come into it.
 */
public final class SkyProjection {
    /** Mean radius of the Earth, for the curvature of the sky. */
    private static final double EARTH_RADIUS_M = 6371000.0;

    public final double shellRadius;

    public SkyProjection(double shellRadius) {
        this.shellRadius = shellRadius;
    }

    /**
     * Projects a world point onto the shell, in coordinates relative to the
     * camera. Writes x, y, z into {@code out} and returns the true distance,
     * which callers need to scale sizes by.
     */
    public double project(double px, double py, double pz,
                          double camX, double camY, double camZ, double[] out) {
        double dx = px - camX;
        double dz = pz - camZ;
        // The world is flat and the planet is not. Something at a constant
        // height over a sphere drops away below the observer's level by the
        // square of its distance over twice the radius: 1.8 km at 150 km, a
        // sixth of a cruising altitude. That is what bends a long trail into
        // a slight arc and draws its far end down towards the horizon; on a
        // flat world it would run off straight to a vanishing point instead.
        double dy = py - camY - (dx * dx + dz * dz) / (2.0 * EARTH_RADIUS_M);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0e-6) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            return distance;
        }
        double k = shellRadius / distance;
        out[0] = dx * k;
        out[1] = dy * k;
        out[2] = dz * k;
        return distance;
    }

    /**
     * How large something of the given real size should be drawn on the shell to
     * subtend the correct angle from where it actually is.
     */
    public double scaleFor(double distance) {
        return distance < 1.0e-6 ? 0.0 : shellRadius / distance;
    }
}
