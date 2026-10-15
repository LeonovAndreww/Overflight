import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the sprites an ageing trail is built from: three wisps of cloud and a
 * fall streak, side by side, U across each and V along its long axis.
 *
 * The wisps are soft, with a ragged edge and uneven density inside, so that a
 * few hundred of them overlapping read as cloud rather than as a row of
 * ellipses. The streak starts dense where it leaves the trail and thins along
 * its fall, which is the look of ice dropping out of it.
 *
 * Every sprite is zero at the edge of its cell, so linear filtering never
 * bleeds one into the next. Nothing finer than about six texels: there are no
 * mipmaps, and the sprites are often small on screen.
 *
 * Run with:  java tools/GeneratePuffTexture.java
 */
public final class GeneratePuffTexture {
    private static final int CELL_W = 64;
    private static final int CELL_H = 128;
    private static final int VARIANTS = 4;
    private static final String OUT =
            "common/resources/assets/overflight/textures/puff.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(CELL_W * VARIANTS, CELL_H,
                BufferedImage.TYPE_INT_ARGB);
        for (int variant = 0; variant < VARIANTS; variant++) {
            for (int y = 0; y < CELL_H; y++) {
                for (int x = 0; x < CELL_W; x++) {
                    double u = (x + 0.5) / CELL_W * 2.0 - 1.0;
                    double v = (y + 0.5) / CELL_H * 2.0 - 1.0;
                    double alpha = variant == 3 ? streak(u, v) : wisp(u, v, variant);
                    int a = (int) Math.round(clamp(alpha, 0.0, 1.0) * 255.0);
                    image.setRGB(variant * CELL_W + x, y, (a << 24) | 0x00FFFFFF);
                }
            }
        }
        File file = new File(OUT);
        ImageIO.write(image, "PNG", file);
        System.out.println("wrote " + file.getPath());
    }

    /** A soft lump of cloud with a ragged edge. */
    private static double wisp(double u, double v, int seed) {
        double r = Math.sqrt(u * u + v * v);
        double angle = Math.atan2(v, u);
        // The edge wanders in and out around the rim.
        double edge = 0.62 + 0.22 * periodic(angle / (2.0 * Math.PI) * 7.0, 7, seed * 3 + 1)
                + 0.10 * periodic(angle / (2.0 * Math.PI) * 13.0, 13, seed * 3 + 2);
        double body = 1.0 - smooth(clamp((r - edge * 0.35) / (edge * 0.75), 0.0, 1.0));
        // Uneven inside: knots and thin places, and fibres running along the
        // long axis, which is what cirrus is made of when you look closely.
        double knots = value2(u * 2.2 + seed * 7.0, v * 2.2, seed + 5);
        double fibres = 0.0;
        double weight = 0.0;
        double amplitude = 1.0;
        for (int octave = 0; octave < 3; octave++) {
            double scale = 1 << octave;
            fibres += amplitude * value2(u * 5.0 * scale + seed * 3.1,
                    v * 1.1 * scale + 0.6 * knots, seed + 11 + octave);
            weight += amplitude;
            amplitude *= 0.55;
        }
        fibres /= weight;
        double inside = (0.35 + 0.65 * knots) * (0.25 + 1.1 * smooth(clamp(fibres * 1.6 - 0.35, 0.0, 1.0)));
        double rim = 1.0 - Math.pow(Math.max(Math.abs(u), Math.abs(v)), 8.0);
        return body * inside * rim;
    }

    /** Dense at the start (v = -1), thinning out along the fall. */
    private static double streak(double u, double v) {
        double along = (v + 1.0) * 0.5;
        double width = 0.35 + 0.35 * along;
        double across = Math.exp(-u * u / (width * width) * 3.0);
        double fade = Math.pow(1.0 - along, 1.3) * smooth(clamp(along / 0.08, 0.0, 1.0));
        double fibres = 0.7 + 0.3 * value2(u * 3.0, v * 1.5, 41);
        double rim = 1.0 - Math.pow(Math.abs(u), 8.0);
        return across * fade * fibres * rim;
    }

    /** Smooth value noise in one dimension with the given period, -1 to 1. */
    private static double periodic(double x, int period, int seed) {
        int i = (int) Math.floor(x);
        double t = smooth(x - i);
        double a = hash(Math.floorMod(i, period), 0, seed);
        double b = hash(Math.floorMod(i + 1, period), 0, seed);
        return (a + (b - a) * t) * 2.0 - 1.0;
    }

    /** Smooth value noise in two dimensions, 0 to 1. */
    private static double value2(double x, double y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double tx = smooth(x - ix);
        double ty = smooth(y - iy);
        double a = hash(ix, iy, seed) + (hash(ix + 1, iy, seed) - hash(ix, iy, seed)) * tx;
        double b = hash(ix, iy + 1, seed) + (hash(ix + 1, iy + 1, seed) - hash(ix, iy + 1, seed)) * tx;
        return a + (b - a) * ty;
    }

    private static double hash(int x, int y, int seed) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
