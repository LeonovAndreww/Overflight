package dev.overflight.core.trail;

/**
 * Smooth value noise in one dimension.
 *
 * Everything uneven about a trail is driven from here, and always against the
 * moment the exhaust left the engine. That is the coordinate that stays with a
 * given piece of trail: anything keyed to age slides along at flying speed,
 * because a wisp is one second older every second.
 */
public final class Noise {

    private Noise() {}

    /** Smoothly interpolated, 0 to 1. */
    public static double value(double x) {
        double floor = StrictMath.floor(x);
        long cell = (long) floor;
        double t = x - floor;
        double a = hashToUnit(cell);
        double b = hashToUnit(cell + 1);
        double smooth = t * t * (3.0 - 2.0 * t);
        return a + (b - a) * smooth;
    }

    /** Smoothly interpolated, -1 to 1. */
    public static double signed(double x) {
        return value(x) * 2.0 - 1.0;
    }

    private static double hashToUnit(long value) {
        long h = value * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }
}
