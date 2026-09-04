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
    public double initialHalfWidthWingspans = 0.12;
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

    public double opacity = 0.85;

    /** Samples per trail. The one knob worth exposing as a graphics setting. */
    public int maxPoints = 192;
}
