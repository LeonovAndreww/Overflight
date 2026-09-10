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
    /**
     * When it left, on the shared clock. Unlike the age this never changes for a
     * given piece of exhaust, which is what makes it the coordinate to hang any
     * along-trail pattern on: anything keyed to age slides as the trail ages.
     */
    public double emitTime;
    /** Half-width of the ribbon here, in metres. */
    public double halfWidth;
    /**
     * 1 along the body of the trail, falling to 0 at the oldest end.
     *
     * A trail does not stop: it thins to a point and dissolves, and the strands
     * it has frayed into draw back together as they go. Photographs of a
     * short-lived trail show a spindle -- narrow at the aircraft, widest in the
     * middle, tapering away at the end -- rather than a ribbon cut off square.
     */
    public double tailFade = 1.0;
    /** 0 to 1, before any view-dependent brightening. */
    public double opacity;
    /** Crow instability: 0 while the trail is smooth, 1 once it has broken into puffs. */
    public double breakup;
}
