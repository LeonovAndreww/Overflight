package dev.overflight.core.trail;

/**
 * One sample along a trail: where that piece of exhaust is now, how old it is,
 * and what it has turned into since.
 */
public final class TrailPoint {
    /** Position after drifting with the wind, in world metres. */
    public double x;
    public double y;
    public double z;
    /** Seconds since this piece left the engine. */
    public double age;
    /** Half-width of the ribbon here, in metres. */
    public double halfWidth;
    /** 0 to 1, before any view-dependent brightening. */
    public double opacity;
    /** Crow instability: 0 while the trail is smooth, 1 once it has broken into puffs. */
    public double breakup;
}
