package dev.overflight.core.trail;

/**
 * Everything about how a trail forms, grows and fades. Split from the sampler so
 * a config, a preset or an admin command can hand over a different set without
 * touching the simulation.
 */
public final class TrailSettings {
    /** Distance behind the engine before anything becomes visible, in wingspans. */
    public double onsetGapWingspans = 4.0;
    /** Seconds for separate engine ribbons to be drawn into one by the wingtip vortices. */
    public double vortexMergeSeconds = 16.0;
    /** Half-width a fresh ribbon starts at, in wingspans. */
    /**
     * Half width where the trail starts, in wingspans.
     *
     * The exhaust plumes expand turbulently within a second or two and are then
     * drawn into the wingtip vortex pair, whose spacing is about 0.78 of the
     * span. So a contrail is some tens of metres across almost immediately,
     * which is why it appears to start at the engines rather than well behind
     * them. At 0.12 it started at eight metres, thinner than a pixel at any real
     * viewing distance, so the first stretch was culled and the trail looked
     * detached from the aircraft.
     */
    public double initialHalfWidthWingspans = 0.34;
    /** Metres of half-width gained per second once the trail is established. */
    public double spreadRateMPerSec = 0.9;
    public double maxHalfWidthM = 1750.0;

    /** Seconds a trail lasts when the air is too dry for it to persist. */
    public double shortLifetimeSeconds = 14.0;
    /** Seconds a trail lasts when the air is supersaturated over ice. */
    public double persistentLifetimeSeconds = 2400.0;
    public double persistenceMultiplier = 1.0;

    /** Crow instability: wavelength in wingspans, and when it starts to show. */
    public double crowWavelengthWingspans = 8.6;
    public double crowOnsetSeconds = 70.0;
    public double crowFullSeconds = 360.0;

    /** Wind that carries the trail away from the flight path. */
    public double windSpeedMs = 8.0;
    public double windDirectionDeg = 270.0;
    /**
     * How much the air varies from place to place, in metres per second of
     * sideways drift. This is what bends an old trail into a meander: neighbouring
     * stretches are pushed by slightly different amounts, and the difference
     * accumulates with age.
     */
    public double shearVariationMs = 2.6;
    /**
     * How unevenly the trail spreads. 0 gives a ribbon of uniform width, 1 lets
     * one stretch grow roughly three times faster than another.
     */
    public double spreadVariation = 0.85;
    /**
     * How far the vortex pair drags the trail down before it levels off, in
     * metres. Real trails sink a couple of hundred metres in the first minute.
     */
    public double vortexSinkM = 170.0;
    public double vortexSinkTimeS = 45.0;

    public double opacity = 0.85;
    /** Ceiling on a trail's alpha, used to take the glare off dense trails at night. */
    public double alphaCap = 1.0;

    /**
     * How many strands an ageing trail is drawn as. One flat ribbon reads as a
     * stick; a real trail frays into fibres that drift apart and droop, and that
     * is most of what makes old cirrus look like cirrus. Only trails old enough
     * to have started breaking up pay for the extra strands.
     */
    public int fibres = 4;
    /** How far the strands drift apart, as a fraction of the trail's own width. */
    public double fibreSpread = 0.58;
    /** How far the outer strands sag below the core, as a fraction of the width. */
    public double fibreSag = 0.42;

    /** Samples per trail. The one knob worth exposing as a graphics setting. */
    public int maxPoints = 192;
}
