package dev.overflight.core.render;

import dev.overflight.core.trail.Noise;
import dev.overflight.core.trail.Scattering;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailPoint;
import dev.overflight.core.trail.TrailSettings;

import java.util.List;

/**
 * The part of an ageing trail a ribbon cannot draw.
 *
 * A ribbon is a strip with a picture in it: however the picture is painted,
 * the trail stays a strip of constant shape. Photographs show something else.
 * One edge of an old trail stays a fairly clean line while the other spreads
 * like cloud; ice that has grown heavy falls out of it in streaks that lean
 * back with the wind shear; its density is uneven along its length, and as it
 * dissolves it breaks into pieces, leaving short remnants hanging on their own.
 *
 * So the ribbon is kept as the line, and this lays soft sprites over it for
 * the rest: puffs drifting off to one side, and fall streaks hanging below.
 *
 * Every sprite belongs to a moment of emission, on a grid of those moments,
 * so it is the same sprite from one frame to the next and nothing crawls. The
 * grid thins as the trail spreads, since a wide trail needs fewer, larger
 * sprites; a sprite leaving the grid fades out over a stretch of spreading
 * rather than vanishing, while its neighbours have grown to cover it.
 */
public final class TrailPuffBuilder {
    /** Seconds of flight between sprites on the finest grid. A power of two. */
    private static final double BASE_STEP_S = 0.5;
    /** Sprite spacing as a fraction of the trail's half-width. */
    private static final double SPACING = 0.55;
    /** Atlas columns: three wisps, a streak and a clump of granules. */
    private static final int VARIANTS = 5;
    private static final int STREAK = 3;
    private static final int GRANULES = 4;
    /** The share of flights whose trail winds into a coil as it breaks up. */
    private static final double COILING_SHARE = 0.4;
    /** Sprites to a turn of the coil, close up. A power of two. */
    private static final double COIL_SAMPLES = 16.0;
    /** Smallest sprite worth drawing, as an angle in radians. */
    private static final double MIN_ANGULAR_HALF_SIZE = 1.5e-4;
    /** Opacity below which a sprite is not worth its quad. */
    private static final float FAINT = 0.004f;

    private final TrailPoint at = new TrailPoint();
    private final double[] centre = new double[3];
    private final double[] a = new double[3];
    private final double[] b = new double[3];
    private final float[] v0 = new float[3];
    private final float[] v1 = new float[3];
    private final float[] v2 = new float[3];
    private final float[] v3 = new float[3];

    /**
     * How far one side of the trail has spread, 0 to 1.
     *
     * By age, since spreading is what time does to a trail; and by how much of
     * its life it has lived, so that a trail made to last only minutes still
     * gets to spread before it goes.
     */
    public static double spread(double age, double life) {
        return Math.max(smoothstep(120.0, 900.0, age), smoothstep(0.2, 0.7, life));
    }

    /** See {@link TrailMeshBuilder#build} for the lighting parameters. */
    public void build(Trail trail, double camX, double camY, double camZ,
                      double sunX, double sunY, double sunZ, double illumination,
                      double tintR, double tintG, double tintB,
                      SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        List<TrailPoint> points = trail.points;
        if (illumination <= 0.0 || points.size() < 2) {
            return;
        }

        double heading = trail.flight.heading;
        double speed = Math.max(trail.flight.groundSpeedMs, 1.0);
        double forwardX = Math.sin(heading);
        double forwardZ = -Math.cos(heading);
        // Which way the shear carries the spreading side. One per flight.
        double sideSign = ((trail.flight.id * 0x9E3779B97F4A7C15L) & (1L << 33)) != 0 ? 1.0 : -1.0;
        double sideX = Math.cos(heading) * sideSign;
        double sideZ = Math.sin(heading) * sideSign;

        double soot = trail.sootFraction;
        float red = (float) ((1.0 - 0.86 * soot) * tintR);
        float green = (float) ((1.0 - 0.87 * soot) * tintG);
        float blue = (float) ((1.0 - 0.88 * soot) * tintB);
        long flightSeed = trail.flight.id;

        double newest = points.get(0).emitTime;
        double oldest = points.get(points.size() - 1).emitTime;

        if (hash(0, flightSeed, 90) < COILING_SHARE) {
            coils(trail, newest, oldest, forwardX, forwardZ, red, green, blue, soot,
                    camX, camY, camZ, sunX, sunY, sunZ, illumination, projection, settings, out);
        }

        double emitTime = Math.floor(newest / BASE_STEP_S) * BASE_STEP_S;
        int segment = 0;

        while (emitTime > oldest) {
            while (segment + 2 < points.size() && points.get(segment + 1).emitTime > emitTime) {
                segment++;
            }
            interpolate(points.get(segment), points.get(segment + 1), emitTime, at);

            double level = log2(Math.max(SPACING * at.halfWidth / speed / BASE_STEP_S, 1.0));
            int whole = Math.min((int) level, 40);
            double step = BASE_STEP_S * (1L << whole);

            double spread = spread(at.age, at.life);
            if (spread > 0.02 && at.opacity > FAINT
                    && inView(at.x, at.y, at.z, at.halfWidth * 3.5, camX, camY, camZ,
                            projection, settings)) {
                long index = Math.round(emitTime / BASE_STEP_S);
                int rank = index == 0 ? 63 : Long.numberOfTrailingZeros(index);
                // Leaving the grid: fade over a whole level of spreading.
                double presence = 1.0 - smoothstep(rank, rank + 1.0, level);
                if (presence > 0.01) {
                    puffs(index, flightSeed, presence, spread, forwardX, forwardZ, sideX, sideZ,
                            red, green, blue, soot, camX, camY, camZ, sunX, sunY, sunZ,
                            illumination, projection, settings, out);
                }
            }

            double next = Math.floor(emitTime / step) * step;
            if (next >= emitTime) {
                next -= step;
            }
            emitTime = next;
        }
    }

    private void puffs(long index, long seed, double presence, double spread,
                       double forwardX, double forwardZ, double sideX, double sideZ,
                       float red, float green, float blue, double soot,
                       double camX, double camY, double camZ,
                       double sunX, double sunY, double sunZ, double illumination,
                       SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        double hw = at.halfWidth;

        // Uneven along its length, always; and as it dissolves the thin
        // stretches go first, leaving gaps with remnants hanging between them.
        double density = 0.55 + 0.45 * (0.5 + 0.5 * fbm(at.emitTime / 36.0, 17.0));
        double dissolve = smoothstep(0.55, 1.0, at.life);
        density = clamp01((density - 0.85 * dissolve) / Math.max(1.0 - 0.85 * dissolve, 0.05));
        double base = at.opacity * density * presence;
        if (base <= FAINT) {
            return;
        }

        // Puffs drifting off the spreading side: wider, fainter, sagging a
        // little, each lying at its own angle between the trail and the side.
        int count = spread > 0.4 ? 2 : 1;
        for (int k = 0; k < count; k++) {
            double q = hash(index, seed, 30 + k);
            double reach = spread * hw * (0.6 + 1.4 * q);
            double along = (hash(index, seed, 40 + k) - 0.5) * reach * 0.8;
            double out1 = reach * (0.5 + 0.5 * q);
            double cx = at.x + sideX * out1 + forwardX * along;
            double cy = at.y - reach * 0.25 * q;
            double cz = at.z + sideZ * out1 + forwardZ * along;

            double size = (hw * 0.45 + reach * 0.35) * (0.35 + 0.5 * hash(index, seed, 45 + k));
            double angle = 0.25 + 0.4 * hash(index, seed, 50 + k);
            double axisX = forwardX * Math.cos(angle) + sideX * Math.sin(angle);
            double axisZ = forwardZ * Math.cos(angle) + sideZ * Math.sin(angle);
            double strength = 0.3 + 1.2 * Math.max(0.0,
                    Noise.signed(at.emitTime / 3.6 + k * 13.1));
            float alpha = (float) (base * 0.9 * spread * strength);
            int variant = (int) (hash(index, seed, 55 + k) * STREAK);

            sprite(cx, cy, cz, axisX, 0.0, axisZ, size * (1.6 + q), size * (0.5 + 0.4 * q),
                    variant, red, green, blue, alpha, soot, camX, camY, camZ,
                    sunX, sunY, sunZ, illumination, projection, settings, out);
        }

        // Fall streaks: ice heavy enough to drop out, leaning back along the
        // trail as the wind below it moves differently.
        if (spread > 0.2 && hash(index, seed, 70) < 0.3) {
            double length = hw * (0.8 + 1.5 * hash(index, seed, 71)) * spread;
            double lean = 0.82 - 0.35 * hash(index, seed, 72);
            double dirX = -forwardX * Math.cos(lean) + sideX * 0.25;
            double dirY = -Math.sin(lean);
            double dirZ = -forwardZ * Math.cos(lean) + sideZ * 0.25;
            double norm = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            dirX /= norm;
            dirY /= norm;
            dirZ /= norm;
            double start = hw * 0.3;
            double cx = at.x + sideX * start + dirX * length * 0.5;
            double cy = at.y + dirY * length * 0.5;
            double cz = at.z + sideZ * start + dirZ * length * 0.5;
            sprite(cx, cy, cz, dirX, dirY, dirZ, length * 0.6, Math.max(hw * 0.07, 4.0),
                    STREAK, red, green, blue, (float) (base * 0.7 * spread), soot,
                    camX, camY, camZ, sunX, sunY, sunZ, illumination, projection, settings, out);
        }
    }

    /**
     * How far a trail has wound itself into a coil, 0 to 1, for the flights
     * that do it at all.
     *
     * Once its vortex pair goes unstable some trails wind round their own
     * axis like a spring for a few minutes: seen from the side a chain of
     * linked loops, with the near halves of the turns piling up into a lumpy
     * band along the top. Not every trail does it visibly, so only some
     * flights do here.
     */
    public static double coil(long flightId, double age, double life) {
        if (hash(0, flightId, 90) >= COILING_SHARE) {
            return 0.0;
        }
        return smoothstep(45.0, 150.0, age) * (1.0 - smoothstep(450.0, 900.0, age))
                * (1.0 - smoothstep(0.6, 0.95, life));
    }

    /**
     * The coil itself: a tube wound round the trail's axis, one turn per Crow
     * wavelength, built from lumpy sprites overlapping along it.
     *
     * The turns sit on a fine grid of emission times, so a given turn stays
     * with its piece of trail. Far away, where a turn is only a few pixels
     * across, every other point on the grid fades out, level by level, as the
     * sprites elsewhere do; close up there are sixteen to a turn.
     */
    private void coils(Trail trail, double newest, double oldest,
                       double forwardX, double forwardZ,
                       float red, float green, float blue, double soot,
                       double camX, double camY, double camZ,
                       double sunX, double sunY, double sunZ, double illumination,
                       SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        List<TrailPoint> points = trail.points;
        long seed = trail.flight.id;
        double speed = Math.max(trail.flight.groundSpeedMs, 1.0);
        double wavelength = settings.crowWavelengthWingspans * trail.flight.type.wingspanM;
        double fine = wavelength / COIL_SAMPLES / speed;
        double turnSign = hash(0, seed, 94) < 0.5 ? 1.0 : -1.0;
        double sideX = Math.cos(trail.flight.heading);
        double sideZ = Math.sin(trail.flight.heading);
        int segment = 0;

        double emitTime = Math.floor(newest / fine) * fine;
        while (emitTime > oldest) {
            while (segment + 2 < points.size() && points.get(segment + 1).emitTime > emitTime) {
                segment++;
            }
            interpolate(points.get(segment), points.get(segment + 1), emitTime, at);
            double coil = coil(seed, at.age, at.life);
            double step = fine;

            if (coil > 0.01 && at.opacity > FAINT) {
                double radius = 0.3 * wavelength * smoothstep(45.0, 200.0, at.age);
                double distance = projection.project(at.x, at.y, at.z, camX, camY, camZ, centre);
                // How many sprites a turn needs to read as a curve here: one
                // every few pixels of its circumference, at most sixteen.
                double circumference = 2.0 * Math.PI * Math.max(radius, at.halfWidth)
                        / Math.max(distance, 1.0) / settings.pixelAngle;
                double wanted = Math.max(2.0, Math.min(COIL_SAMPLES, circumference / 6.0));
                double level = log2(COIL_SAMPLES / wanted);
                int whole = (int) level;
                step = fine * (1L << whole);

                long index = Math.round(emitTime / fine);
                int rank = index == 0 ? 63 : Long.numberOfTrailingZeros(index);
                double presence = 1.0 - smoothstep(rank, rank + 1.0, level);
                if (presence > 0.01 && inView(at.x, at.y, at.z, radius * 2.0 + at.halfWidth,
                        camX, camY, camZ, projection, settings)) {
                    double phase = 2.0 * Math.PI * emitTime * speed / wavelength * turnSign;
                    double cos = Math.cos(phase);
                    double sin = Math.sin(phase);
                    // The vortices sink as the rings form, so each turn hangs
                    // lower and rounder below the axis than it rises above it.
                    double vertical = radius * sin * (sin < 0.0 ? 1.6 : 0.6);
                    double x = at.x + sideX * radius * cos;
                    double y = at.y + vertical;
                    double z = at.z + sideZ * radius * cos;
                    // Along the curve: forward with the trail, round with the turn.
                    double turn = 2.0 * Math.PI * radius / wavelength * turnSign;
                    double tx = forwardX - sideX * turn * sin;
                    double ty = turn * cos;
                    double tz = forwardZ - sideZ * turn * sin;
                    double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    // A fat tube, about a quarter of a turn across, as the photographs show.
                    double thickness = Math.max(at.halfWidth * 0.45, wavelength * 0.085)
                            * (0.8 + 0.4 * hash(index, seed, 95));
                    double spacing = wavelength * tl / COIL_SAMPLES * (1L << whole);
                    sprite(x, y, z, tx / tl, ty / tl, tz / tl,
                            Math.max(thickness * 1.3, spacing * 0.7), thickness,
                            GRANULES, red, green, blue,
                            (float) (at.opacity * coil * presence * 0.9), soot,
                            camX, camY, camZ, sunX, sunY, sunZ, illumination,
                            projection, settings, out);
                }
            }

            double next = Math.floor(emitTime / step) * step;
            if (next >= emitTime) {
                next -= step;
            }
            emitTime = next;
        }
    }

    /**
     * One sprite, its long axis along a direction in the world and its face
     * turned to the camera. The texture runs along the axis in V, from the
     * start of the axis to its end, which is what lets a streak fade along its
     * fall.
     */
    private void sprite(double cx, double cy, double cz,
                        double axisX, double axisY, double axisZ,
                        double halfLength, double halfWidth, int variant,
                        float red, float green, float blue, float alpha, double soot,
                        double camX, double camY, double camZ,
                        double sunX, double sunY, double sunZ, double illumination,
                        SkyProjection projection, TrailSettings settings, MeshBuffer out) {
        if (alpha <= FAINT) {
            return;
        }
        double distance = projection.project(cx, cy, cz, camX, camY, camZ, centre);
        if (distance < 1.0) {
            return;
        }
        double scale = projection.scaleFor(distance);
        double reach = Math.max(halfLength, halfWidth) * scale;
        if (reach < MIN_ANGULAR_HALF_SIZE * projection.shellRadius) {
            return;
        }
        double toward = (centre[0] * settings.viewX + centre[1] * settings.viewY
                + centre[2] * settings.viewZ) / projection.shellRadius;
        if (toward < settings.viewCos - reach / projection.shellRadius) {
            return;
        }

        double d0 = projection.project(cx - axisX * halfLength, cy - axisY * halfLength,
                cz - axisZ * halfLength, camX, camY, camZ, a);
        double d1 = projection.project(cx + axisX * halfLength, cy + axisY * halfLength,
                cz + axisZ * halfLength, camX, camY, camZ, b);
        if (d0 < 1.0 || d1 < 1.0) {
            return;
        }

        double viewX = centre[0] / projection.shellRadius;
        double viewY = centre[1] / projection.shellRadius;
        double viewZ = centre[2] / projection.shellRadius;

        double axX = b[0] - a[0];
        double axY = b[1] - a[1];
        double axZ = b[2] - a[2];
        double axLen = Math.sqrt(axX * axX + axY * axY + axZ * axZ);
        double minLen = halfWidth * scale * 0.5;
        if (axLen < minLen) {
            // Looking straight down the axis: it shows as a round puff, so give
            // it any length at all across the view and let the width match.
            double upX = Math.abs(viewY) < 0.9 ? 0.0 : 1.0;
            double upY = Math.abs(viewY) < 0.9 ? 1.0 : 0.0;
            axX = upY * viewZ;
            axY = -upX * viewZ;
            axZ = upX * viewY - upY * viewX;
            axLen = Math.sqrt(axX * axX + axY * axY + axZ * axZ);
            double half = halfWidth * scale;
            for (int i = 0; i < 3; i++) {
                double unit = (i == 0 ? axX : i == 1 ? axY : axZ) / axLen;
                a[i] = centre[i] - unit * half;
                b[i] = centre[i] + unit * half;
            }
            axX = b[0] - a[0];
            axY = b[1] - a[1];
            axZ = b[2] - a[2];
            axLen = Math.sqrt(axX * axX + axY * axY + axZ * axZ);
        }
        axX /= axLen;
        axY /= axLen;
        axZ /= axLen;

        double sideX = axY * viewZ - axZ * viewY;
        double sideY = axZ * viewX - axX * viewZ;
        double sideZ = axX * viewY - axY * viewX;
        double sideLen = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (sideLen < 1.0e-6) {
            return;
        }
        double half = halfWidth * scale / sideLen;
        sideX *= half;
        sideY *= half;
        sideZ *= half;

        double glow = Scattering.brightness(viewX * sunX + viewY * sunY + viewZ * sunZ, soot)
                * illumination;
        float shown = Math.min((float) settings.alphaCap, clamp01f((float) (alpha * glow)));
        if (shown <= FAINT) {
            return;
        }

        set(v0, a, -sideX, -sideY, -sideZ);
        set(v1, a, sideX, sideY, sideZ);
        set(v2, b, sideX, sideY, sideZ);
        set(v3, b, -sideX, -sideY, -sideZ);
        // Textures are lit from their +U side; turn that side to the sky. U
        // runs from v0 to v1, which is along +side.
        boolean flip = sideY < 0.0;
        float u0 = (float) (flip ? variant + 1 : variant) / VARIANTS;
        float u1 = (float) (flip ? variant : variant + 1) / VARIANTS;
        out.quad(v0, v1, v2, v3, u0, u1, 0.0f, 1.0f, red, green, blue, shown, shown);
    }

    /**
     * Whether anything within {@code radius} metres of a point could be on
     * screen. Most of the sky is not, and building sprites for it is most of
     * the cost.
     */
    private boolean inView(double x, double y, double z, double radius,
                           double camX, double camY, double camZ,
                           SkyProjection projection, TrailSettings settings) {
        double distance = projection.project(x, y, z, camX, camY, camZ, centre);
        if (distance < 1.0) {
            return true;
        }
        double toward = (centre[0] * settings.viewX + centre[1] * settings.viewY
                + centre[2] * settings.viewZ) / projection.shellRadius;
        return toward >= settings.viewCos - radius / distance;
    }

    /** The trail between two samples, at a moment of emission between theirs. */
    private static void interpolate(TrailPoint newer, TrailPoint older, double emitTime,
                                    TrailPoint into) {
        double span = newer.emitTime - older.emitTime;
        double t = span > 0.0 ? (newer.emitTime - emitTime) / span : 0.0;
        into.emitTime = emitTime;
        into.x = lerp(newer.x, older.x, t);
        into.y = lerp(newer.y, older.y, t);
        into.z = lerp(newer.z, older.z, t);
        into.age = lerp(newer.age, older.age, t);
        into.halfWidth = lerp(newer.halfWidth, older.halfWidth, t);
        into.opacity = lerp(newer.opacity, older.opacity, t);
        into.breakup = lerp(newer.breakup, older.breakup, t);
        into.life = lerp(newer.life, older.life, t);
        into.tailFade = lerp(newer.tailFade, older.tailFade, t);
    }

    private static double fbm(double x, double phase) {
        return 0.6 * Noise.signed(x + phase) + 0.3 * Noise.signed(x * 2.3 + phase + 5.7)
                + 0.1 * Noise.signed(x * 5.1 + phase + 11.3);
    }

    /** 0 to 1, fixed for a sprite: which one, on which flight, which property. */
    private static double hash(long index, long seed, int property) {
        long h = index * 0x9E3779B97F4A7C15L ^ seed * 0xC2B2AE3D27D4EB4FL ^ property * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static void set(float[] target, double[] base, double dx, double dy, double dz) {
        target[0] = (float) (base[0] + dx);
        target[1] = (float) (base[1] + dy);
        target[2] = (float) (base[2] + dz);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static float clamp01f(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }
}
