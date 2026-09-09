package dev.overflight.core.traffic;

/**
 * SplitMix64, used both as a hash and as a generator.
 *
 * Traffic is never stored, it is derived: every flight comes out of a seed mixed
 * from the world seed, a map cell and a time slot. That makes the sky identical
 * for every client without a byte of it being sent, and lets any past or future
 * moment be recomputed on demand.
 */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    /** Mixes values into a seed. Order matters; the same inputs always give the same seed. */
    public static long seedOf(long base, long... values) {
        long h = mix(base);
        for (int i = 0; i < values.length; i++) {
            h = mix(h ^ (values[i] + 0x9E3779B97F4A7C15L));
        }
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        return mix(state);
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [min, max). */
    public double range(double min, double max) {
        return min + nextDouble() * (max - min);
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        return (int) ((nextLong() >>> 1) % bound);
    }

    /**
     * Poisson draw.
     *
     * Knuth's product walk for the small means this is normally asked for -- a
     * map cell sees a couple of departures in a slot -- and a normal
     * approximation past twenty, where the walk needs one multiply per event and
     * exp(-mean) is close enough to zero to lose precision. A configuration can
     * legitimately ask for a mean in the hundreds, and truncating the tail there
     * would quietly cap the traffic rather than fail.
     */
    public int poisson(double mean) {
        if (mean <= 0.0) {
            return 0;
        }
        if (mean > 20.0) {
            double drawn = mean + StrictMath.sqrt(mean) * gaussian();
            return (int) StrictMath.max(0.0, StrictMath.floor(drawn + 0.5));
        }

        double limit = StrictMath.exp(-mean);
        double product = 1.0;
        int count = 0;
        // Generous rather than tight: at a mean of twenty this is roughly
        // fourteen standard deviations out, so it bounds the loop without
        // shaping the result.
        while (count < 256) {
            product *= nextDouble();
            if (product <= limit) {
                break;
            }
            count++;
        }
        return count;
    }

    /** Standard normal, by Box-Muller. */
    private double gaussian() {
        double u1 = StrictMath.max(nextDouble(), 1.0e-12);
        double u2 = nextDouble();
        return StrictMath.sqrt(-2.0 * StrictMath.log(u1))
                * StrictMath.cos(2.0 * StrictMath.PI * u2);
    }
}
