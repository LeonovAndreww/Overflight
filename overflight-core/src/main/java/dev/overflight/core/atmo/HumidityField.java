package dev.overflight.core.atmo;

import dev.overflight.core.traffic.Rng;

/**
 * Humidity at cruise altitude, invented but not arbitrary.
 *
 * Minecraft has no atmosphere above the clouds, so one is synthesised: a smooth,
 * slowly drifting field of patches. The patches matter because they are what
 * ice-supersaturated regions are in reality -- ragged areas hundreds of
 * kilometres across, drifting and evolving over hours, which is why one aircraft
 * leaves a trail across the whole sky while the next one along the same airway
 * leaves nothing.
 *
 * Derived from a seed rather than stored, like everything else here, so every
 * client agrees on which part of the sky is holding trails up today.
 */
public final class HumidityField {
    /** Roughly how wide one patch is, in metres. */
    public double patchSizeM = 180000.0;
    /** How fast the field drifts across the map. */
    public double driftSpeedMs = 22.0;
    /** Seconds for the pattern to reshape itself into something new. */
    public double evolutionSeconds = 2400.0;
    /** Fraction of the sky that is supersaturated over ice on an ordinary day. */
    public double supersaturatedFraction = 0.22;
    /** How much rain and thunder push the whole field upwards. */
    public double weatherInfluence = 0.5;

    /** Standard deviation of the two-octave noise, measured over a large sample. */
    private static final double NOISE_SPREAD = 0.149;
    /** How far humidity swings from end to end of the noise range. */
    private static final double HUMIDITY_RANGE = 0.55;

    private final long seed;

    public HumidityField(long seed) {
        this.seed = seed;
    }

    /**
     * Relative humidity over water at a point, as a fraction. Values above ice
     * saturation are what let a trail persist; at cruise temperatures that
     * threshold sits near 0.6, well below saturation over water.
     *
     * @param rainLevel 0 in clear weather, 1 in a downpour
     */
    public double relativeHumidity(double x, double z, double altitudeM, double timeS,
                                   double rainLevel) {
        double drift = driftSpeedMs * timeS;
        double u = (x + drift) / patchSizeM;
        double v = (z + drift * 0.4) / patchSizeM;
        double w = timeS / evolutionSeconds;

        // Two octaves: broad regions with ragged edges, which is what the real
        // ones look like on satellite imagery.
        double value = noise(u, v, w) * 0.68 + noise(u * 2.7, v * 2.7, w * 1.6) * 0.32;

        // Place the distribution so that exactly the configured fraction of the
        // sky clears the ice-saturation threshold. Guessing at the offset put a
        // twentieth of the sky over the line where a fifth was asked for, so the
        // threshold is solved for instead.
        double spread = NOISE_SPREAD * HUMIDITY_RANGE;
        double centre = iceThresholdApprox(altitudeM)
                - normalQuantile(1.0 - supersaturatedFraction) * spread;
        double humidity = centre + (value - 0.5) * HUMIDITY_RANGE;

        humidity += rainLevel * weatherInfluence * 0.25;

        // Thin, dry air is the rule up here; the field varies around that.
        return clamp(humidity, 0.02, 1.35);
    }

    /**
     * Roughly the relative humidity over water at which the air becomes saturated
     * over ice, at the temperature found at this altitude. Trails persist above
     * it and sublimate below.
     */
    public static double iceThresholdApprox(double altitudeM) {
        double tempK = Isa.temperature(altitudeM);
        return Saturation.overIce(tempK) / Saturation.overWater(tempK);
    }

    /** Smooth value noise on a lattice, seeded so all clients see the same sky. */
    private double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double xf = fade(x - xi);
        double yf = fade(y - yi);
        double zf = fade(z - zi);

        double c000 = lattice(xi, yi, zi);
        double c100 = lattice(xi + 1, yi, zi);
        double c010 = lattice(xi, yi + 1, zi);
        double c110 = lattice(xi + 1, yi + 1, zi);
        double c001 = lattice(xi, yi, zi + 1);
        double c101 = lattice(xi + 1, yi, zi + 1);
        double c011 = lattice(xi, yi + 1, zi + 1);
        double c111 = lattice(xi + 1, yi + 1, zi + 1);

        double x00 = lerp(c000, c100, xf);
        double x10 = lerp(c010, c110, xf);
        double x01 = lerp(c001, c101, xf);
        double x11 = lerp(c011, c111, xf);
        return lerp(lerp(x00, x10, yf), lerp(x01, x11, yf), zf);
    }

    private double lattice(int x, int y, int z) {
        long h = Rng.seedOf(seed, x, y, z);
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Inverse normal cumulative distribution, by the standard rational
     * approximation. Used once per sample to turn "a fifth of the sky" into the
     * threshold that actually delivers a fifth of the sky.
     */
    private static double normalQuantile(double p) {
        if (p <= 0.0) {
            return -6.0;
        }
        if (p >= 1.0) {
            return 6.0;
        }
        boolean upper = p > 0.5;
        double tail = upper ? 1.0 - p : p;
        double t = Math.sqrt(-2.0 * Math.log(tail));
        double z = t - (2.515517 + 0.802853 * t + 0.010328 * t * t)
                / (1.0 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t);
        return upper ? z : -z;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
