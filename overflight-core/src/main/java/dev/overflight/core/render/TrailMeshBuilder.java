package dev.overflight.core.render;

import dev.overflight.core.traffic.Flight;
import dev.overflight.core.trail.Noise;
import dev.overflight.core.trail.Scattering;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailPoint;
import dev.overflight.core.trail.TrailSettings;

import java.util.List;

/**
 * Turns a sampled trail into camera-facing ribbon quads on the sky shell.
 *
 * The ribbons start apart, one per engine, and converge as the wingtip vortices
 * draw them together; each quad is turned to face the camera and brightened by
 * how close it sits to the sun.
 */
public final class TrailMeshBuilder {
    /**
     * Smallest half-width worth drawing, as an angle in radians. Roughly a third
     * of an arc minute, which is under one pixel at any sensible field of view.
     */
    private static final double MIN_ANGULAR_HALF_WIDTH = 1.0e-4;
    /**
     * Length of trail one repeat of the texture covers, in metres. Long enough
     * that a repeat is hard to pick out, short enough that the fibres in it
     * are a few hundred metres across rather than smeared over kilometres.
     */
    private static final double TEXTURE_TILE_M = 16000.0;

    /**
     * Draw only the line of the trail, for {@link TrailPuffBuilder} to lay the
     * rest over. Off, the ribbon draws the whole trail on its own.
     */
    public boolean spine;

    private final double[] a = new double[3];
    private final double[] b = new double[3];
    private final float[] v0 = new float[3];
    private final float[] v1 = new float[3];
    private final float[] v2 = new float[3];
    private final float[] v3 = new float[3];

    /**
     * @param sunX,sunY,sunZ the direction of the sun, scaled by how far above the
     *                       horizon it is. Below the horizon this goes to zero and
     *                       the forward-scattering peak flattens out with it,
     *                       which is what should happen: there is no sun to
     *                       scatter towards.
     * @param illumination   overall brightness, 1 in full day and a good deal
     *                       less but never nothing at night
     */
    public void build(Trail trail, double camX, double camY, double camZ,
                      double sunX, double sunY, double sunZ, double illumination,
                      SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        build(trail, camX, camY, camZ, sunX, sunY, sunZ, illumination,
                1.0, 1.0, 1.0, projection, settings, out);
    }

    /**
     * @param tintR,tintG,tintB the colour of the light falling on the trail. White
     *                          by day; the low sun reddens as its light crosses
     *                          more atmosphere, which is why a trail at sunset is
     *                          orange while the sky behind it has gone blue.
     */
    public void build(Trail trail, double camX, double camY, double camZ,
                      double sunX, double sunY, double sunZ, double illumination,
                      double tintR, double tintG, double tintB,
                      SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        if (illumination <= 0.0) {
            return;
        }
        List<TrailPoint> points = trail.points;
        if (points.size() < 2) {
            return;
        }

        // Engine ribbons sit either side of the centreline, perpendicular to the
        // flight path and level with it.
        double heading = trail.flight.heading;
        double perpX = Math.cos(heading);
        double perpZ = Math.sin(heading);

        // Each flight reads the texture at its own offset, scale and handedness,
        // so two trails side by side never show the same fibres.
        long hash = trail.flight.id * 0x9E3779B97F4A7C15L;
        double tileOffset = ((hash >>> 40) & 0xFFFF) / 65536.0;
        double tileM = TEXTURE_TILE_M * (0.8 + 0.45 * (((hash >>> 24) & 0xFFFF) / 65536.0));
        boolean mirrored = (hash & (1L << 20)) != 0;

        int ribbons = Math.max(1, trail.ribbonCount);
        double soot = trail.sootFraction;
        // Ice takes the colour of whatever is lighting it; soot stays dark
        // whatever falls on it.
        float red = (float) ((1.0 - 0.86 * soot) * tintR);
        float green = (float) ((1.0 - 0.87 * soot) * tintG);
        float blue = (float) ((1.0 - 0.88 * soot) * tintB);

        // While the ribbons are separate they share the trail between them, so
        // four engines do not draw four times the substance.
        double share = ribbons > 1 ? 1.0 / Math.sqrt(ribbons) : 1.0;

        // One flat ribbon reads as a stick. A trail old enough to have started
        // breaking up is drawn as a bundle of strands instead: they drift apart,
        // the outer ones sag below the core, and each wanders on its own. That
        // fraying is most of what makes old cirrus look like cirrus, and only
        // trails that have begun to fray pay for the extra strands.
        int strands = Math.max(ribbons, Math.max(1, settings.fibres));

        for (int r = 0; r < strands; r++) {
            double engineLateral = r < ribbons
                    ? (r - (ribbons - 1) * 0.5) * trail.ribbonSpacingM : 0.0;
            // Only a bundle of fibres fans out. The engines' own ribbons merge
            // and stay merged: fanning them too split every twin-engined
            // trail into two parallel lines once it began to break up.
            double fan = settings.fibres > ribbons && strands > 1
                    ? (r / (strands - 1.0)) * 2.0 - 1.0 : 0.0;

            for (int i = 0; i < points.size() - 1; i++) {
                TrailPoint p0 = points.get(i);
                TrailPoint p1 = points.get(i + 1);

                if (r >= ribbons && p0.breakup < 0.02 && p1.breakup < 0.02) {
                    continue;
                }

                // Strands draw back together as the tail dissolves; left to fan
                // out they end in a splay of separate tentacles.
                double off0 = engineLateral * mergeFactor(p0.age, settings)
                        + fibreOffset(p0, fan, r, settings) * p0.tailFade;
                double off1 = engineLateral * mergeFactor(p1.age, settings)
                        + fibreOffset(p1, fan, r, settings) * p1.tailFade;

                double d0 = projection.project(p0.x + perpX * off0,
                        p0.y - fibreSag(p0, fan, settings), p0.z + perpZ * off0,
                        camX, camY, camZ, a);
                double d1 = projection.project(p1.x + perpX * off1,
                        p1.y - fibreSag(p1, fan, settings), p1.z + perpZ * off1,
                        camX, camY, camZ, b);
                if (d0 < 1.0 || d1 < 1.0) {
                    continue;
                }

                double hw0 = p0.halfWidth * strandWidth(p0, r, ribbons, share)
                        * spineWidth(p0) * projection.scaleFor(d0);
                double hw1 = p1.halfWidth * strandWidth(p1, r, ribbons, share)
                        * spineWidth(p1) * projection.scaleFor(d1);
                // A trail three hundred kilometres off is thinner than a pixel:
                // nothing to look at, but quads to build, sort and blend.
                if (hw0 < MIN_ANGULAR_HALF_WIDTH * projection.shellRadius
                        && hw1 < MIN_ANGULAR_HALF_WIDTH * projection.shellRadius) {
                    continue;
                }
                // Nor is anything drawn thinner than two pixels. Minecraft does
                // no antialiasing: a quad narrower than a pixel lands on pixel
                // centres only now and then, and one a pixel wide still misses
                // them wherever its centre line runs between two, so a thin
                // slanting trail came out dotted. Widened that far and made
                // fainter by as much, it covers the same amount of sky,
                // continuously. See below for what happens to the texture.
                double minHalf = 1.0 * settings.pixelAngle * projection.shellRadius;
                double cover0 = Math.min(1.0, hw0 / minHalf);
                double cover1 = Math.min(1.0, hw1 / minHalf);
                hw0 = Math.max(hw0, minHalf);
                hw1 = Math.max(hw1, minHalf);

                double axX = b[0] - a[0];
                double axY = b[1] - a[1];
                double axZ = b[2] - a[2];
                double axLen = Math.sqrt(axX * axX + axY * axY + axZ * axZ);
                if (axLen < 1.0e-4) {
                    continue;
                }
                axX /= axLen;
                axY /= axLen;
                axZ /= axLen;

                // Face the camera: the ribbon widens along the axis crossed with
                // the direction we are looking.
                double midX = (a[0] + b[0]) * 0.5;
                double midY = (a[1] + b[1]) * 0.5;
                double midZ = (a[2] + b[2]) * 0.5;
                double midLen = Math.sqrt(midX * midX + midY * midY + midZ * midZ);
                if (midLen < 1.0e-4) {
                    continue;
                }
                double viewX = midX / midLen;
                double viewY = midY / midLen;
                double viewZ = midZ / midLen;

                double sideX = axY * viewZ - axZ * viewY;
                double sideY = axZ * viewX - axX * viewZ;
                double sideZ = axX * viewY - axY * viewX;
                double sideLen = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
                if (sideLen < 1.0e-4) {
                    continue;
                }
                sideX /= sideLen;
                sideY /= sideLen;
                sideZ /= sideLen;

                double scatter = Scattering.brightness(
                        viewX * sunX + viewY * sunY + viewZ * sunZ, soot);
                double glow = scatter * illumination;
                float shade = (float) Scattering.shade(scatter);
                // Overlapping strands would otherwise pile up into something
                // denser than the trail ever was.
                float cap = (float) settings.alphaCap;
                float alpha0 = (float) (Math.min(cap, clamp01((float) (p0.opacity * glow
                        * share(p0, strands) * spineOpacity(p0, trail.flight)))) * cover0);
                float alpha1 = (float) (Math.min(cap, clamp01((float) (p1.opacity * glow
                        * share(p1, strands) * spineOpacity(p1, trail.flight)))) * cover1);
                if (alpha0 < 0.004f && alpha1 < 0.004f) {
                    continue;
                }

                set(v0, a, -sideX * hw0, -sideY * hw0, -sideZ * hw0);
                set(v1, a, sideX * hw0, sideY * hw0, sideZ * hw0);
                set(v2, b, sideX * hw1, sideY * hw1, sideZ * hw1);
                set(v3, b, -sideX * hw1, -sideY * hw1, -sideZ * hw1);

                // Along the trail the texture is pinned to where the exhaust
                // left the engine, like every other irregularity, so its fibres
                // stay put instead of streaming along at flying speed. The
                // whole part is taken off before narrowing to float.
                //
                // The same for every engine's ribbon. Each once had its own
                // offset, and where the ribbons had merged into one, four
                // copies of a centreline that wanders slightly differently
                // showed as four parallel lines.
                double tile0 = alongTrack(p0, trail) / tileM + tileOffset;
                double tile1 = alongTrack(p1, trail) / tileM + tileOffset;
                double whole = Math.floor(Math.min(tile0, tile1));
                float vLow = (float) (tile0 - whole);
                float vHigh = (float) (tile1 - whole);

                // The texture holds three stages of a trail side by side, and
                // each piece is drawn as whichever it has reached, crossfading
                // through the change.
                for (int stage = 0; stage < STAGES; stage++) {
                    float w0 = stageWeight(stage, p0.breakup, spine);
                    float w1 = stageWeight(stage, p1.breakup, spine);
                    if (alpha0 * w0 < 0.004f && alpha1 * w1 < 0.004f) {
                        continue;
                    }
                    // A widened ribbon takes only the middle of the texture's
                    // profile, so the trail's narrow bright core is spread over
                    // the width rather than drawn as a sub-pixel line inside it,
                    // which would be dotted all over again. Fainter by as much
                    // as the middle is denser than the whole profile.
                    float cover = (float) (0.5 * (cover0 + cover1));
                    float mid = (stage + 0.5f) / STAGES;
                    float half = 0.5f / STAGES * cover;
                    float dense = (float) (PROFILE_MEAN / (PROFILE_MEAN + (1.0 - PROFILE_MEAN) * (1.0 - cover)));
                    out.quad(v0, v1, v2, v3,
                            mirrored ? mid + half : mid - half, mirrored ? mid - half : mid + half,
                            vLow, vHigh, red * shade, green * shade, blue * shade,
                            alpha0 * w0 * dense, alpha1 * w1 * dense);
                }
            }
        }
    }

    /**
     * Narrower as the trail spreads, when drawn as the spine under the sprites:
     * the line along one edge stays a line while the rest goes to cloud.
     */
    private double spineWidth(TrailPoint p) {
        return spine ? 1.0 - 0.6 * TrailPuffBuilder.spread(p.age, p.life) : 1.0;
    }

    /**
     * And fainter, going before the sprites do as the trail dissolves, and
     * giving way to the coil where the trail winds into one: then the turns
     * are the trail, and there is no separate line along it.
     */
    private double spineOpacity(TrailPoint p, Flight flight) {
        if (!spine) {
            return 1.0;
        }
        return (1.0 - 0.3 * TrailPuffBuilder.spread(p.age, p.life))
                * unevenness(p)
                * (1.0 - smoothstep(0.55, 1.0, p.life))
                * (1.0 - 0.85 * TrailPuffBuilder.coil(flight, p));
    }

    /**
     * Density that varies along the line: slightly in a fresh trail, and in an
     * old one so much that stretches nearly vanish while others stay. Pinned to
     * the moment of emission like every other irregularity, so it does not crawl.
     */
    private static double unevenness(TrailPoint p) {
        double n = 0.65 * Noise.value(p.emitTime / 23.0 + 3.1)
                + 0.35 * Noise.value(p.emitTime / 6.5 + 9.7);
        double spread = TrailPuffBuilder.spread(p.age, p.life);
        double depth = 0.12 + 0.4 * spread;
        // Fine beads, a couple of seconds long, strongest in a young trail
        // where the larger scales are still shallow (photos show them clearly).
        double beads = 0.14 * (1.0 - spread) * (1.0 - Noise.value(p.emitTime / 1.8 + 21.3));
        return Math.max(0.0, 1.0 - depth * (1.0 - n) - beads);
    }

    /** Roughly how dense a stage's profile is on average across its width. */
    private static final double PROFILE_MEAN = 0.4;

    /** Fresh, beaded and aged, in the order the texture holds them. */
    private static final int STAGES = 3;
    /** Breakup by which the beads have fully formed and the aged band begins. */
    private static final double BEADED = 0.3;

    /**
     * How much of a stage to draw at a given breakup. The weights always sum to
     * one, and at most two are non-zero at once.
     *
     * As a spine there is no aged stage: what an old trail turns into is drawn
     * by the sprites, and the ribbon stays the beaded line along one edge.
     */
    static float stageWeight(int stage, double breakup, boolean spine) {
        double beads = smoothstep(0.0, BEADED, breakup);
        double aged = spine ? 0.0 : smoothstep(BEADED, 1.0, breakup);
        switch (stage) {
            case 0: return (float) (1.0 - beads);
            case 1: return (float) (beads * (1.0 - aged));
            default: return (float) aged;
        }
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }

    /** How far along its leg the aircraft was when it laid this point, in metres. */
    private static double alongTrack(TrailPoint p, Trail trail) {
        return (p.emitTime - trail.flight.startTimeS) * trail.flight.groundSpeedMs;
    }

    /** Where one strand of the bundle sits, sideways, in metres. */
    private static double fibreOffset(TrailPoint p, double fan, int strand,
                                      TrailSettings settings) {
        if (p.breakup <= 0.0) {
            return 0.0;
        }
        // Keyed to the moment of emission, like everything else uneven about a
        // trail, so a strand keeps its own path instead of writhing.
        double wander = Noise.signed(p.emitTime / 47.0 + strand * 13.7) * 0.55;
        return p.halfWidth * settings.fibreSpread * p.breakup * (fan + wander);
    }

    /** How far a strand has fallen below the core. The outer ones sag furthest. */
    private static double fibreSag(TrailPoint p, double fan, TrailSettings settings) {
        if (p.breakup <= 0.0) {
            return 0.0;
        }
        return p.halfWidth * settings.fibreSag * p.breakup * Math.abs(fan);
    }

    /** Half-width of one strand as a fraction of the trail's, thinning as it frays. */
    private static double strandWidth(TrailPoint p, int strand, int ribbons, double share) {
        double whole = strand < ribbons ? share : 0.0;
        double frayed = 0.34 + 0.22 * Noise.value(p.emitTime / 39.0 + strand * 7.1);
        return whole + (frayed - whole) * p.breakup;
    }

    private static double share(TrailPoint p, int strands) {
        return 1.0 / (1.0 + (strands - 1) * 0.6 * p.breakup);
    }

    /** 1 while the engine ribbons are still apart, 0 once the vortices have merged them. */
    private static double mergeFactor(double age, TrailSettings settings) {
        double t = 1.0 - age / Math.max(settings.vortexMergeSeconds, 1.0e-3);
        return t < 0.0 ? 0.0 : t;
    }

    private static void set(float[] target, double[] base, double dx, double dy, double dz) {
        target[0] = (float) (base[0] + dx);
        target[1] = (float) (base[1] + dy);
        target[2] = (float) (base[2] + dz);
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }
}
