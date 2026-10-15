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
 *
 * Two things are decided per piece of exhaust rather than per trail: whether it
 * formed at all, and how long it lasts. Both depend on the air it was emitted
 * into. Deciding them from the air the aircraft is in now meant that crossing
 * into dry air wiped out half an hour of trail behind it in one frame, when
 * what really happens is that the trail stops and what already hangs there
 * stays.
 */
public final class TrailSampler {

    /**
     * Seconds between samples just behind the aircraft. A power of two, so that
     * every coarser step is an exact multiple of it and the grid stays exact in
     * floating point however large the clock gets.
     */
    private static final double BASE_STEP_S = 2.0;
    /** Age span of the finest level; each level after it is twice as long. */
    private static final double FIRST_LEVEL_S = 60.0;

    /** Air a piece of exhaust was emitted into, as the sampler needs it. */
    public interface Conditions {
        /**
         * Writes how readily a trail forms there into {@code out[0]}, 0 to 1,
         * and how well the air holds it into {@code out[1]}, 0 to 1. Both
         * should change smoothly across their thresholds rather than switching.
         */
        void at(Flight flight, double x, double z, double timeS, double[] out);
    }

    private final double[] air = new double[2];

    /** Fixed conditions along the whole trail, for tests and diagnostics. */
    public Trail sample(Flight flight, double timeS, boolean forms, boolean persistent,
                        TrailSettings settings) {
        final double formation = forms ? 1.0 : 0.0;
        final double persistence = persistent ? 1.0 : 0.0;
        return sample(flight, timeS, new Conditions() {
            @Override
            public void at(Flight f, double x, double z, double t, double[] out) {
                out[0] = formation;
                out[1] = persistence;
            }
        }, settings);
    }

    public Trail sample(Flight flight, double timeS, Conditions conditions,
                        TrailSettings settings) {
        List<TrailPoint> points = new ArrayList<TrailPoint>();
        boolean smoke = flight.condition == Flight.Condition.SMOKING
                || flight.condition == Flight.Condition.BURNING;
        double soot = sootFraction(flight.condition);

        double wingspan = flight.type.wingspanM;
        // The plume has to cool before its moisture freezes, so the trail starts
        // a few wingspans behind the aircraft rather than at the nozzle.
        double onsetAge = settings.onsetGapWingspans * wingspan / Math.max(flight.groundSpeedMs, 1.0);

        double shortLifetime = smoke ? SMOKE_LIFETIME_S : Math.max(settings.shortLifetimeSeconds, 1.0);
        double longLifetime = smoke ? SMOKE_LIFETIME_S : Math.max(
                settings.persistentLifetimeSeconds * settings.persistenceMultiplier, shortLifetime);

        // Nothing is emitted before the leg starts or after it ends. The
        // aircraft is gone at the end of its leg; what it left keeps ageing.
        double legEnd = flight.startTimeS + flight.durationS;
        double newest = Math.min(timeS - onsetAge, legEnd);
        double oldest = Math.max(flight.startTimeS, timeS - longLifetime);
        if (newest <= oldest) {
            return new Trail(flight, points, 0, 0.0, false, soot);
        }

        // No two trails age alike. The air a flight is crossing has its own
        // shear and its own humidity, so one spreads fast and leans hard while
        // the next stays narrow and straight. Derived from the flight, so it
        // stays the same for that flight on every client.
        double character = (double) ((flight.id * 0x9E3779B97F4A7C15L) >>> 11)
                / (double) (1L << 53);
        double spreadBias = 0.65 + 1.0 * character;

        double windRad = Math.toRadians(settings.windDirectionDeg);
        double windX = Math.sin(windRad) * settings.windSpeedMs;
        double windZ = -Math.cos(windRad) * settings.windSpeedMs;

        // Across the flight path, for the meander.
        double sideX = Math.cos(flight.heading);
        double sideZ = Math.sin(flight.heading);

        double levelSpan = levelSpan(longLifetime, settings.maxPoints);
        boolean anyFormed = false;

        // Newest first. The two ends move with time; everything between sits on
        // a grid of emission times, so a given sample is the same piece of
        // exhaust from one frame to the next and the shape of the trail does
        // not shimmer as it is re-sampled. The grid coarsens with age, a level
        // at a time, by dropping every other point.
        double emitTime = newest;
        while (true) {
            double age = timeS - emitTime;
            TrailPoint p = point(flight, emitTime, age, onsetAge, shortLifetime, longLifetime,
                    smoke, conditions, settings, spreadBias, windX, windZ, sideX, sideZ);
            if (p.opacity > FAINT) {
                anyFormed = true;
            }
            add(points, p);

            if (emitTime <= oldest) {
                break;
            }
            double step = stepFor(age, levelSpan);
            double next = Math.floor(emitTime / step) * step;
            if (next >= emitTime) {
                next -= step;
            }
            emitTime = Math.max(next, oldest);
        }

        trimFaintEnds(points);
        if (!anyFormed) {
            points.clear();
        }
        return new Trail(flight, points, ribbonCount(flight, settings),
                flight.type.wingspanM * 0.34, longLifetime > shortLifetime, soot);
    }

    private static final double SMOKE_LIFETIME_S = 90.0;
    /** Opacity below which a point contributes nothing to the picture. */
    private static final double FAINT = 0.002;

    private TrailPoint point(Flight flight, double emitTime, double age, double onsetAge,
                             double shortLifetime, double longLifetime, boolean smoke,
                             Conditions conditions, TrailSettings settings, double spreadBias,
                             double windX, double windZ, double sideX, double sideZ) {
        double wingspan = flight.type.wingspanM;
        double grown = Math.max(age - onsetAge, 0.0);

        double formation;
        double persistence;
        if (smoke) {
            // Smoke is not ice and does not care whether the air is saturated.
            formation = 1.0;
            persistence = 1.0;
        } else {
            conditions.at(flight, flight.xAt(emitTime), flight.zAt(emitTime), emitTime, air);
            formation = air[0];
            persistence = air[1];
        }
        double lifetime = shortLifetime + (longLifetime - shortLifetime) * persistence;

        // The air is not uniform, so neighbouring stretches are pushed by
        // slightly different amounts and grow at slightly different rates.
        // Both differences accumulate with age, which is why a fresh trail is a
        // straight even ribbon and an old one wanders and bulges. Every term is
        // keyed to the moment of emission, so a given stretch keeps whatever it
        // was dealt instead of the pattern sliding along. How damp a stretch is
        // decides both how fast it spreads and how thick it looks, so those
        // share one field; where the air is going is unrelated, so the meander
        // gets its own.
        double moisture = variation(emitTime, age, 41.9);
        double drift = variation(emitTime, age, 3.3) * settings.shearVariationMs * grown;

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

        // Crow instability: the vortex pair sinks unevenly and the trail
        // bulges before breaking into puffs. The puffs themselves are some
        // eight wingspans apart, far closer than the samples, so they are in the
        // texture; what is done here is the slow swelling and pinching on a
        // wavelength the samples can carry. Shorter ones only alias into beads
        // that jump about, which is how an earlier attempt failed.
        p.breakup = smoothstep(settings.crowOnsetSeconds, settings.crowFullSeconds, age);
        double lump = (Noise.value(emitTime / 55.0 + 3.9) - 0.5) * 2.0;
        double lumpAmount = settings.lumpiness * p.breakup;
        p.halfWidth *= 1.0 + p.breakup * 0.35 + lumpAmount * 0.55 * lump;

        // The oldest end dissolves rather than stopping, which is what makes a
        // short-lived trail read as a spindle rather than a ribbon cut off
        // square. Both terms are keyed to the piece of exhaust, never to its
        // place in the sample list: age over lifetime is how far along its own
        // dissolution this piece is, and the time since the leg started does
        // not change once the piece exists. Narrowing is done by fading, not by
        // contracting the ribbon: ice does not shrink back, it disperses.
        double dissolving = 1.0 - smoothstep(0.72, 1.0, age / lifetime);
        double fromLegStart = smoothstep(0.0, 30.0, emitTime - flight.startTimeS);
        p.tailFade = Math.min(dissolving, fromLegStart);

        // Thinner where it has been pinched, which is what separates the puffs
        // from each other instead of leaving a wavy band.
        p.opacity = (1.0 - p.breakup * 0.30) * (0.86 + 0.14 * moisture)
                * (1.0 - lumpAmount * 0.45 * (1.0 - lump) * 0.5);

        double remaining = Math.max(1.0 - age / lifetime, 0.0);
        double dilution = settings.initialHalfWidthWingspans * wingspan / p.halfWidth;
        // A persistent trail is not being diluted. It grows on the moisture
        // already in the air around it, which is why one stays solid white for
        // minutes and only pales once it has spread into cirrus. One that
        // cannot persist really is disappearing, and fast. Air in between
        // gives something in between.
        double holding = Math.pow(remaining, 0.8) * Math.pow(dilution, 0.08);
        double fading = Math.pow(remaining, 1.4) * Math.pow(dilution, 0.5);
        p.opacity *= settings.opacity * (fading + (holding - fading) * persistence);

        p.opacity *= p.tailFade * formation;
        return p;
    }

    /**
     * Adds a point, collapsing runs of invisible ones. Only the two at the
     * edges of a gap matter: they are what fades the trail out on one side and
     * back in on the other. Dropping them would bridge the gap with a quad
     * blending straight across it.
     */
    private static void add(List<TrailPoint> points, TrailPoint p) {
        int n = points.size();
        if (n >= 2 && p.opacity <= FAINT
                && points.get(n - 1).opacity <= FAINT
                && points.get(n - 2).opacity <= FAINT) {
            points.set(n - 1, p);
            return;
        }
        points.add(p);
    }

    /** Drops invisible points off both ends, keeping one to fade into. */
    private static void trimFaintEnds(List<TrailPoint> points) {
        while (points.size() >= 2 && points.get(points.size() - 1).opacity <= FAINT
                && points.get(points.size() - 2).opacity <= FAINT) {
            points.remove(points.size() - 1);
        }
        int lead = 0;
        while (lead + 1 < points.size() && points.get(lead).opacity <= FAINT
                && points.get(lead + 1).opacity <= FAINT) {
            lead++;
        }
        if (lead > 0) {
            points.subList(0, lead).clear();
        }
    }

    /**
     * How long the finest level of the grid is, in seconds of age. Every level
     * holds the same number of samples, so this is what sets the budget. It
     * depends only on the longest a trail can last and the point budget, never
     * on the time, or the grid would move.
     */
    static double levelSpan(double longestLifetimeS, int maxPoints) {
        double span = FIRST_LEVEL_S;
        while (span > BASE_STEP_S * 2.0) {
            double levels = Math.ceil(log2(1.0 + longestLifetimeS / span));
            if (levels * span / BASE_STEP_S + 2.0 <= Math.max(maxPoints, 8)) {
                break;
            }
            span /= 2.0;
        }
        return span;
    }

    /** Seconds between samples at a given age: doubling with each level. */
    static double stepFor(double age, double levelSpan) {
        int level = (int) Math.floor(log2(1.0 + Math.max(age, 0.0) / levelSpan));
        return BASE_STEP_S * (1L << Math.min(level, 30));
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    /**
     * How many ribbons leave the aircraft. One per engine at first, then the
     * wingtip vortices draw them together within a few seconds.
     */
    private static int ribbonCount(Flight flight, TrailSettings settings) {
        return flight.type.engineCount;
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

    /**
     * How this stretch of trail differs from its neighbours, between -1 and 1.
     *
     * Three octaves, with the short ones fading out as the trail ages. That is
     * two things at once. Physically it is diffusion: fine structure in a trail
     * really is smoothed away within a few minutes, leaving only broad
     * variation. Practically it keeps every wavelength that survives longer than
     * the gap between samples there.
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
