package dev.overflight.core.render;

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
            double fan = strands > 1 ? (r / (strands - 1.0)) * 2.0 - 1.0 : 0.0;

            for (int i = 0; i < points.size() - 1; i++) {
                TrailPoint p0 = points.get(i);
                TrailPoint p1 = points.get(i + 1);

                if (r >= ribbons && p0.breakup < 0.02 && p1.breakup < 0.02) {
                    continue;
                }

                double off0 = engineLateral * mergeFactor(p0.age, settings)
                        + fibreOffset(p0, fan, r, settings);
                double off1 = engineLateral * mergeFactor(p1.age, settings)
                        + fibreOffset(p1, fan, r, settings);

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
                        * projection.scaleFor(d0);
                double hw1 = p1.halfWidth * strandWidth(p1, r, ribbons, share)
                        * projection.scaleFor(d1);
                // A trail three hundred kilometres off is thinner than a pixel:
                // nothing to look at, but quads to build, sort and blend.
                if (hw0 < MIN_ANGULAR_HALF_WIDTH * projection.shellRadius
                        && hw1 < MIN_ANGULAR_HALF_WIDTH * projection.shellRadius) {
                    continue;
                }

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

                double glow = Scattering.brightness(
                        viewX * sunX + viewY * sunY + viewZ * sunZ, soot) * illumination;
                // Overlapping strands would otherwise pile up into something
                // denser than the trail ever was.
                float alpha0 = clamp01((float) (p0.opacity * glow * share(p0, strands)));
                float alpha1 = clamp01((float) (p1.opacity * glow * share(p1, strands)));
                if (alpha0 < 0.004f && alpha1 < 0.004f) {
                    continue;
                }

                set(v0, a, -sideX * hw0, -sideY * hw0, -sideZ * hw0);
                set(v1, a, sideX * hw0, sideY * hw0, sideZ * hw0);
                set(v2, b, sideX * hw1, sideY * hw1, sideZ * hw1);
                set(v3, b, -sideX * hw1, -sideY * hw1, -sideZ * hw1);

                // The texture is a cross-section and carries nothing along its
                // length, so there is no mapping to get wrong here. Variation
                // along the trail rides on the vertex alpha instead.
                out.quad(v0, v1, v2, v3, 0.0f, 1.0f, 0.0f, 1.0f,
                        red, green, blue, alpha0, alpha1);
            }
        }
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
