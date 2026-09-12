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

        // No two trails age alike. The air a flight is crossing has its own
        // shear and its own humidity, so one spreads fast and leans hard while
        // the next stays narrow and straight. Derived from the flight, so it
        // stays the same for that flight on every client.
        double character = (double) ((flight.id * 0x9E3779B97F4A7C15L) >>> 11)
                / (double) (1L << 53);
        double spreadBias = 0.65 + 1.0 * character;
        double shearBias = -1.0 + 2.0 * ((character * 7.0) % 1.0);

        double windRad = Math.toRadians(settings.windDirectionDeg);
        double windX = Math.sin(windRad) * settings.windSpeedMs;
        double windZ = -Math.cos(windRad) * settings.windSpeedMs;

        // Across the flight path, for the meander.
        double sideX = Math.cos(flight.heading);
        double sideZ = Math.sin(flight.heading);

        // Spend samples in proportion to how much trail there is. A stub that
        // sublimates in fifteen seconds is a few kilometres long and does not
        // deserve the same budget as a trail that has been spreading for half an
        // hour, and on a sky full of short trails that is most of the cost.
        int used = (int) (maxAge / 8.0);
        used = Math.max(12, Math.min(settings.maxPoints, used));

        for (int i = 0; i < used; i++) {
            double t = (double) i / (used - 1);
            // Nearly uniform, leaning only slightly towards the aircraft. It was
            // squared before, which put the samples where the trail is sharp and
            // left the old end twenty seconds apart -- and the old end is
            // precisely where a trail stops being a smooth band and breaks into
            // lumps. Nothing shorter than about four samples can be drawn there
            // without turning into beads and gaps, so the samples have to go
            // where the structure is.
            double age = onsetAge + (maxAge - onsetAge) * Math.pow(t, 1.3);
            double emitTime = timeS - age;
            if (emitTime < flight.startTimeS) {
                break;
            }

            double grown = age - onsetAge;

            // The air is not uniform, so neighbouring stretches are pushed by
            // slightly different amounts and grow at slightly different rates.
            // Both differences accumulate with age, which is why a fresh trail is
            // a straight even ribbon and an old one wanders and bulges. Every
            // term is keyed to the moment of emission, so a given stretch keeps
            // whatever it was dealt instead of the pattern sliding along.
            // Two fields, not three. How damp a stretch of air is decides both
            // how fast the trail spreads there and how thick it looks, so those
            // share one field; where the air is going is unrelated, so the
            // meander gets its own.
            double moisture = variation(emitTime, age, 41.9);
            double drift = variation(emitTime, age, 3.3)
                    * settings.shearVariationMs * grown;

            TrailPoint p = new TrailPoint();
            p.age = age;
            p.emitTime = emitTime;
            p.x = flight.xAt(emitTime) + windX * age + sideX * drift;
            p.z = flight.zAt(emitTime) + windZ * age + sideZ * drift;
            // The vortex pair drags the trail down before it levels off.
            p.y = flight.altitudeM - settings.vortexSinkM
                    * (1.0 - Math.exp(-grown / Math.max(settings.vortexSinkTimeS, 1.0)));

            double spreadRate = (1.0 + settings.spreadVariation * moisture) * spreadBias;
            double halfWidth = settings.initialHalfWidthWingspans * wingspan
                    + settings.spreadRateMPerSec * grown * Math.max(spreadRate, 0.1);
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
            // The vortex pair pinches the trail at intervals. Keyed to emission
            // time, so a given bulge belongs to a piece of exhaust and stays
            // with it; keyed to age or to the sample index it would slide along
            // the trail as the trail grew. The wavelength is deliberately long
            // enough for the sampling above to carry: short ones only alias into
            // a row of disconnected beads, which is how an earlier attempt at
            // this failed.
            double lump = (Noise.value(emitTime / 55.0 + 3.9) - 0.5) * 2.0;
            double lumpAmount = settings.lumpiness * p.breakup;
            p.halfWidth *= 1.0 + p.breakup * 0.35 + lumpAmount * 0.55 * lump;

            // The oldest end dissolves rather than stopping: it closes back
            // towards a point and whatever it had frayed into draws together
            // with it, which is what makes a short-lived trail read as a spindle
            // rather than a ribbon cut off square.
            //
            // Both terms are keyed to the piece of exhaust rather than to its
            // place in the sample list. Age over lifetime is how far along its
            // own dissolution this piece is, and the seconds between the leg's
            // start and when this piece was emitted do not change at all once it
            // exists. Keying either to the list position looks the same in a
            // still frame and is wrong in motion: the list is re-spaced as the
            // trail grows, so a fixed piece of exhaust would be narrowed by a
            // different amount every frame.
            double dissolving = 1.0 - smoothstep(0.72, 1.0, age / lifetime);
            double fromLegStart = smoothstep(0.0, 30.0, emitTime - flight.startTimeS);
            p.tailFade = Math.min(dissolving, fromLegStart);
            // Narrowing is done by fading, not by contracting the ribbon. Ice
            // does not shrink back, it disperses: the edges thin out until they
            // fall below anything the eye can pick out, and the trail reads as
            // closing to a point. Contracting the geometry instead would mean a
            // fixed piece of exhaust getting narrower every frame, which is both
            // wrong and the sort of thing that shows up as crawling.
            // Thinner where it has been pinched, which is what separates the
            // puffs from each other instead of leaving a wavy band.
            p.opacity = (1.0 - p.breakup * 0.30) * (0.86 + 0.14 * moisture)
                    * (1.0 - lumpAmount * 0.45 * (1.0 - lump) * 0.5);

            double remaining = Math.max(1.0 - age / lifetime, 0.0);
            double dilution = settings.initialHalfWidthWingspans * wingspan / p.halfWidth;

            if (persistent) {
                // A persistent trail is not being diluted. It is growing on the
                // moisture already in the air around it, which is why one stays
                // solid white for minutes and only pales once it has spread into
                // cirrus. Fading it in proportion to its width, as though the
                // same ice were being smeared thinner, left a trail you could
                // see straight through within a minute -- nothing like the sky.
                p.opacity *= settings.opacity
                        * Math.pow(remaining, 0.8)
                        * Math.pow(dilution, 0.08);
            } else {
                // One that cannot persist really is disappearing, and fast.
                p.opacity *= settings.opacity
                        * Math.pow(remaining, 1.4)
                        * Math.pow(dilution, 0.5);
            }

            p.opacity *= p.tailFade;

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

    /**
     * How this stretch of trail differs from its neighbours, between -1 and 1.
     *
     * Three octaves, with the short ones fading out as the trail ages. That is
     * two things at once. Physically it is diffusion: fine structure in a trail
     * really is smoothed away within a few minutes, leaving only broad
     * variation. Practically it keeps every wavelength that survives longer than
     * the gap between samples there, and samples along an old trail are twenty
     * seconds apart -- an earlier version varied the spreading rate on a
     * thirty-second wavelength and the width jumped four-fold between
     * neighbours, which is a row of lumps, not a contrail.
     *
     * The amplitude decays with age but the pattern does not move, so a given
     * stretch keeps its own character and simply loses its detail.
     */
    private static double variation(double emitTime, double age, double phase) {
        double coarse = (Noise.value(emitTime / 190.0 + phase) - 0.5) * 2.0;
        double medium = (Noise.value(emitTime / 65.0 + phase + 7.3) - 0.5) * 2.0;
        double fine = (Noise.value(emitTime / 30.0 + phase + 19.1) - 0.5) * 2.0;
        return 0.60 * coarse
                + 0.28 * medium * smoothedAway(age, 500.0)
                + 0.12 * fine * smoothedAway(age, 180.0);
    }

    /** 1 while a scale still exists, falling towards 0 once diffusion has eaten it. */
    private static double smoothedAway(double age, double lifetimeS) {
        double ratio = age / lifetimeS;
        return 1.0 / (1.0 + ratio * ratio);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }
}
