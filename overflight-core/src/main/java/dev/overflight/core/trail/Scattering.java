package dev.overflight.core.trail;

/**
 * How bright a piece of trail looks from where the camera is standing.
 *
 * Ice crystals scatter light strongly forward, so a trail seen towards the sun
 * blazes while an identical one in the opposite half of the sky is a faint grey
 * line. Leaving this out is what makes drawn-on contrails look flat, so it is
 * worth the handful of operations per point.
 */
public final class Scattering {
    /** Asymmetry of the phase function. Ice crystals sit near 0.8, strongly forward. */
    private static final double ICE_ASYMMETRY = 0.78;
    /** Soot scatters much more evenly than ice, and absorbs besides. */
    private static final double SOOT_ASYMMETRY = 0.35;

    private Scattering() {}

    /**
     * Brightness multiplier for one point.
     *
     * @param cosViewSun cosine of the angle between the direction the camera is
     *                   looking and the direction of the sun
     * @param soot       0 for a clean ice trail, 1 for black smoke
     */
    public static double brightness(double cosViewSun, double soot) {
        double g = ICE_ASYMMETRY * (1.0 - soot) + SOOT_ASYMMETRY * soot;
        double phase = henyeyGreenstein(cosViewSun, g);
        // Referenced against side scattering, so a trail across the sky reads as
        // 1.0 and only the ones near the sun climb above it.
        double sideways = henyeyGreenstein(0.0, g);
        double boost = phase / sideways;

        // Soot absorbs rather than glows, so it barely brightens whatever the angle.
        double ceiling = 4.0 * (1.0 - soot) + 1.2 * soot;
        double floor = 0.55 + 0.25 * soot;
        return clamp(boost, floor, ceiling);
    }

    private static double henyeyGreenstein(double cosTheta, double g) {
        double gg = g * g;
        double denom = 1.0 + gg - 2.0 * g * cosTheta;
        return (1.0 - gg) / (4.0 * Math.PI * denom * Math.sqrt(denom));
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
