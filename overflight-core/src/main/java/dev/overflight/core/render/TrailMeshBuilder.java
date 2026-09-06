package dev.overflight.core.render;

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
     * @param daylight 1 in full day, 0 at night. A contrail is only visible
     *                 because it scatters sunlight, so with the sun down there
     *                 is nothing to see.
     */
    public void build(Trail trail, double camX, double camY, double camZ,
                      double sunX, double sunY, double sunZ, double daylight,
                      SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        if (daylight <= 0.0) {
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
        float red = (float) (1.0 - 0.86 * soot);
        float green = (float) (1.0 - 0.87 * soot);
        float blue = (float) (1.0 - 0.88 * soot);

        // While the ribbons are separate they share the trail between them, so
        // four engines do not draw four times the substance.
        double share = ribbons > 1 ? 1.0 / Math.sqrt(ribbons) : 1.0;

        for (int r = 0; r < ribbons; r++) {
            double lateral = (r - (ribbons - 1) * 0.5) * trail.ribbonSpacingM;

            for (int i = 0; i < points.size() - 1; i++) {
                TrailPoint p0 = points.get(i);
                TrailPoint p1 = points.get(i + 1);

                double off0 = lateral * mergeFactor(p0.age, settings);
                double off1 = lateral * mergeFactor(p1.age, settings);

                double d0 = projection.project(p0.x + perpX * off0, p0.y, p0.z + perpZ * off0,
                        camX, camY, camZ, a);
                double d1 = projection.project(p1.x + perpX * off1, p1.y, p1.z + perpZ * off1,
                        camX, camY, camZ, b);
                if (d0 < 1.0 || d1 < 1.0) {
                    continue;
                }

                double hw0 = p0.halfWidth * share * projection.scaleFor(d0);
                double hw1 = p1.halfWidth * share * projection.scaleFor(d1);
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
                        viewX * sunX + viewY * sunY + viewZ * sunZ, soot) * daylight;
                float alpha0 = clamp01((float) (p0.opacity * glow));
                float alpha1 = clamp01((float) (p1.opacity * glow));
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
