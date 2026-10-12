import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the trail texture: U across the ribbon, V along it.
 *
 * An earlier version put wisps along the trail and they read as a zebra of
 * stripes across it, one repeat every couple of kilometres. Two things went
 * wrong there and both are avoided here. The detail ran across the trail, so
 * every repeat was a visible bar; here it runs along it, as the fibres of an
 * aged contrail do, so a repeat is a streak continuing rather than a new one
 * starting. And the repeat was short; here one tile covers several kilometres
 * (see TrailMeshBuilder), with the large-scale unevenness still coming from
 * per-vertex noise that never repeats.
 *
 * The layers, from coarse to fine:
 *  - the cross-section: a dense core with soft shoulders, as before;
 *  - the edges bulge and pinch along the length, a few hundred metres apart,
 *    roughly the Crow instability's spacing;
 *  - fibres along the length, faint in the core and strong in the shoulders,
 *    where the ice is thin enough for its structure to show.
 *
 * Nothing finer than about ten texels: the texture has no mipmaps, and finer
 * detail would sparkle on a trail a few pixels wide.
 *
 * Tileable along V only. Across U it does not need to be, since it fades to
 * nothing at both edges.
 *
 * Run with:  java tools/GenerateTrailTexture.java
 */
public final class GenerateTrailTexture {
    private static final int WIDTH = 128;
    private static final int HEIGHT = 1024;
    private static final String OUT =
            "common/resources/assets/overflight/textures/trail.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < HEIGHT; y++) {
            double v = (y + 0.5) / HEIGHT;

            // The edges bulge and pinch, and the centre wanders a little.
            double bulge = 1.0 + 0.16 * signed(fbm1(v, 24, 2, 1));
            double wander = 0.035 * signed(fbm1(v, 6, 3, 2));

            for (int x = 0; x < WIDTH; x++) {
                double u = (x + 0.5) / WIDTH * 2.0 - 1.0;
                double local = (u - wander) / bulge;

                // Two Gaussians: a tight bright core where the ice is densest,
                // and a wide faint shoulder for the part that has already spread.
                double core = Math.exp(-local * local * 11.0);
                double shoulder = Math.exp(-local * local * 1.9);
                double across = 0.62 * core + 0.38 * shoulder;

                // Fibres along the trail. The warp braids them, so they wander
                // across one another instead of running ruler-straight.
                double warp = 0.9 * signed(fbm1(v, 10, 2, 3));
                double fibres = fbm2(local * 3.5 + warp, v, 6, 2, 4);
                fibres = smooth(clamp((fibres - 0.25) * 2.0, 0.0, 1.0));
                double contrast = 0.25 + 0.6 * Math.min(1.0, Math.abs(local));
                across *= 1.0 - contrast + 2.0 * contrast * fibres;

                // Take the rim to exactly zero, so neighbouring ribbons blend
                // instead of showing an edge where the quad ends.
                across *= 1.0 - Math.pow(Math.abs(u), 4.0);

                int alpha = (int) Math.round(clamp(across, 0.0, 1.0) * 255.0);
                image.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        File file = new File(OUT);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("could not create " + parent);
        }
        ImageIO.write(image, "PNG", file);
        System.out.println("wrote " + file.getPath() + " (" + WIDTH + "x" + HEIGHT + ")");
    }

    /** Value noise along V, periodic over one tile, 0 to 1. */
    private static double fbm1(double v, int cycles, int octaves, int seed) {
        double sum = 0.0;
        double weight = 0.0;
        double amplitude = 1.0;
        for (int o = 0; o < octaves; o++) {
            int period = cycles << o;
            double x = v * period;
            int i = (int) Math.floor(x);
            double t = smooth(x - i);
            double a = hash(Math.floorMod(i, period), 0, seed + o * 31);
            double b = hash(Math.floorMod(i + 1, period), 0, seed + o * 31);
            sum += amplitude * (a + (b - a) * t);
            weight += amplitude;
            amplitude *= 0.5;
        }
        return sum / weight;
    }

    /** Value noise in U and V, periodic in V only, 0 to 1. */
    private static double fbm2(double u, double v, int cyclesV, int octaves, int seed) {
        double sum = 0.0;
        double weight = 0.0;
        double amplitude = 1.0;
        double scaleU = 1.0;
        for (int o = 0; o < octaves; o++) {
            int period = cyclesV << o;
            double x = u * scaleU;
            double y = v * period;
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            double tx = smooth(x - ix);
            double ty = smooth(y - iy);
            int s = seed + o * 31;
            int y0 = Math.floorMod(iy, period);
            int y1 = Math.floorMod(iy + 1, period);
            double a = lerp(hash(ix, y0, s), hash(ix + 1, y0, s), tx);
            double b = lerp(hash(ix, y1, s), hash(ix + 1, y1, s), tx);
            sum += amplitude * lerp(a, b, ty);
            weight += amplitude;
            amplitude *= 0.5;
            scaleU *= 2.0;
        }
        return sum / weight;
    }

    private static double hash(int x, int y, int seed) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double signed(double unit) {
        return unit * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
