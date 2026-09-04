package dev.overflight.core.trail;

import dev.overflight.core.traffic.Flight;

import java.util.List;

/** A sampled trail, ready to be turned into ribbon geometry by a backend. */
public final class Trail {
    public final Flight flight;
    /** Ordered youngest first, so index 0 is just behind the aircraft. */
    public final List<TrailPoint> points;
    /** How many separate ribbons leave the aircraft before the vortices merge them. */
    public final int ribbonCount;
    /** Ribbon separation at the wing, in metres. */
    public final double ribbonSpacingM;
    /** True when the air is holding the trail up rather than letting it sublimate. */
    public final boolean persistent;
    /** 0 for clean ice, 1 for the black smoke of an aircraft on fire. */
    public final double sootFraction;

    public Trail(Flight flight, List<TrailPoint> points, int ribbonCount,
                 double ribbonSpacingM, boolean persistent, double sootFraction) {
        this.flight = flight;
        this.points = points;
        this.ribbonCount = ribbonCount;
        this.ribbonSpacingM = ribbonSpacingM;
        this.persistent = persistent;
        this.sootFraction = sootFraction;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
