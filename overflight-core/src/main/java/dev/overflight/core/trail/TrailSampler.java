package dev.overflight.core.trail;

import dev.overflight.core.traffic.Flight;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the trail behind an aircraft.
 *
 * Like the traffic itself, a trail is derived rather than accumulated: the piece
 * of sky 300 seconds old is simply where the aircraft was 300 seconds ago, moved
 * by the wind since. Nothing is stored between frames, the result is identical
 * on every client, and a trail is correct the moment an aircraft comes into view
 * instead of having to grow in from nothing.
 */
public final class TrailSampler {

    public Trail sample(Flight flight, double timeS, boolean forms, boolean persistent,
                        TrailSettings settings) {
        List<TrailPoint> points = new ArrayList<TrailPoint>();
        if (!forms && flight.condition == Flight.Condition.NORMAL) {
            return new Trail(flight, points, 0, 0.0, false, 0.0);
        }

        double wingspan = flight.type.wingspanM;
        // The plume has to cool before its moisture freezes, so the trail starts
        // a few wingspans behind the aircraft rather than at the nozzle.
        double onsetAge = settings.onsetGapWingspans * wingspan / Math.max(flight.groundSpeedMs, 1.0);

        double soot = sootFraction(flight.condition);
        double lifetime = lifetimeSeconds(flight.condition, persistent, settings);
        double maxAge = Math.min(lifetime, timeS - flight.startTimeS);
        if (maxAge <= onsetAge) {
            return new Trail(flight, points, 0, 0.0, persistent, soot);
        }

        double windRad = Math.toRadians(settings.windDirectionDeg);
        double windX = Math.sin(windRad) * settings.windSpeedMs;
        double windZ = -Math.cos(windRad) * settings.windSpeedMs;

        // Spend samples in proportion to how much trail there is. A stub that
        // sublimates in fifteen seconds is a few kilometres long and does not
        // deserve the same budget as a trail that has been spreading for half an
        // hour, and on a sky full of short trails that is most of the cost.
        int used = (int) (maxAge / 12.0);
        used = Math.max(12, Math.min(settings.maxPoints, used));

        for (int i = 0; i < used; i++) {
            double t = (double) i / (used - 1);
            // Squared spacing: dense where the trail is sharp and detail shows,
            // sparse where it has spread into a diffuse band anyway.
            double age = onsetAge + (maxAge - onsetAge) * t * t;
            double emitTime = timeS - age;
            if (emitTime < flight.startTimeS) {
                break;
            }

            TrailPoint p = new TrailPoint();
            p.age = age;
            p.x = flight.xAt(emitTime) + windX * age;
            p.y = flight.altitudeM;
            p.z = flight.zAt(emitTime) + windZ * age;

            double grown = age - onsetAge;
            double halfWidth = settings.initialHalfWidthWingspans * wingspan
                    + settings.spreadRateMPerSec * grown;
            p.halfWidth = Math.min(halfWidth, settings.maxHalfWidthM);

            p.breakup = smoothstep(settings.crowOnsetSeconds, settings.crowFullSeconds, age);
            // Crow instability: the vortex pair sinks unevenly and the trail
            // bulges before breaking into the string of puffs an old trail is
            // known by. The bulges are real but they are not drawn as geometry:
            // their wavelength is some eight wingspans, about two seconds of
            // flight, while samples along an old trail are tens of seconds
            // apart. Modulating width at that frequency only aliased, turning
            // the trail into a row of beads that jumped about as time moved.
            // The along-trail texture carries the structure instead, and breakup
            // widens and thins the trail the way dispersal actually does.
            p.halfWidth *= 1.0 + p.breakup * 0.35;
            p.opacity = 1.0 - p.breakup * 0.30;

            double remaining = 1.0 - age / lifetime;
            p.opacity *= settings.opacity * Math.pow(Math.max(remaining, 0.0), 1.4);
            // Spreading thins what is there, though a persistent trail claws some
            // of it back by growing on ambient moisture.
            double dilution = settings.initialHalfWidthWingspans * wingspan / p.halfWidth;
            p.opacity *= Math.pow(dilution, persistent ? 0.25 : 0.5);

            if (p.opacity > 0.002) {
                points.add(p);
            }
        }

        return new Trail(flight, points, ribbonCount(flight, settings),
                flight.type.wingspanM * 0.34, persistent, soot);
    }

    /**
     * How many ribbons leave the aircraft. One per engine at first, then the
     * wingtip vortices draw them together within a few seconds.
     */
    private static int ribbonCount(Flight flight, TrailSettings settings) {
        return flight.type.engineCount;
    }

    /** Seconds after which a piece of trail is gone. */
    private static double lifetimeSeconds(Flight.Condition condition, boolean persistent,
                                          TrailSettings settings) {
        if (condition == Flight.Condition.SMOKING || condition == Flight.Condition.BURNING) {
            // Smoke is not ice and does not care whether the air is saturated.
            return 90.0;
        }
        double base = persistent
                ? settings.persistentLifetimeSeconds * settings.persistenceMultiplier
                : settings.shortLifetimeSeconds;
        return Math.max(base, 1.0);
    }

    private static double sootFraction(Flight.Condition condition) {
        if (condition == Flight.Condition.BURNING) {
            return 1.0;
        }
        if (condition == Flight.Condition.SMOKING) {
            return 0.7;
        }
        return 0.0;
    }

    /** The age at which the separate engine ribbons have become one. */
    public static double mergeAge(TrailSettings settings) {
        return settings.vortexMergeSeconds;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }
}
