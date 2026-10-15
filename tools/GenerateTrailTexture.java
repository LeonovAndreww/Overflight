import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the trail texture: three stages of a contrail side by side, U across
 * each one, V along the trail.
 *
 *   U 0   - 1/3   fresh: a thin line with a crisp edge, twisting
 *   U 1/3 - 2/3   beaded: the same line, swollen at intervals by the vortex pair
 *   U 2/3 - 1     aged: a wide faint band, a denser spine along one edge and
 *                 fall streaks slanting away from it
 *
 * TrailMeshBuilder crossfades between them by the age of each piece of exhaust.
 * Taken from photographs: a young trail is a sharp line, beads appear within a
 * minute or two, and an old one is a comb of streaks hanging off a spine,
 * running across the trail rather than along it.
 *
 * An earlier version drew wisps across a 2 km repeat and read as a zebra. The
 * repeat here is 16 km (see TrailMeshBuilder), and the streaks are irregular in
 * spacing, length and strength, so nothing lines up from one repeat to the next.
 *
 * Every stage is zero at both of its edges, so linear filtering never bleeds
 * one into the next. Nothing finer than about eight texels: the texture has no
 * mipmaps, and finer detail would sparkle on a trail a few pixels wide.
 *
 * Tileable along V only.
 *
 * Run with:  java tools/GenerateTrailTexture.java
 */
public final class GenerateTrailTexture {
    private static final int STAGE = 128;
    private static final int WIDTH = STAGE * 3;
    private static final int HEIGHT = 1024;
    /** Half-turns of the twisting sheet per repeat: about 900 m each. */
    private static final int TWISTS = 18;
    /** Crow puffs per repeat: 16 km over about 400 m. */
    private static final int BEADS = 40;
    /** Fall streaks per repeat, at the spine; some 300 m apart. */
    private static final int STREAKS = 52;
    private static final String OUT =
            "common/resources/assets/overflight/textures/trail.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < HEIGHT; y++) {
            double v = (y + 0.5) / HEIGHT;
            double wander = 0.03 * signed(fbm1(v, 6, 3, 2));
            double bead = beads(v);

            for (int x = 0; x < STAGE; x++) {
                double u = (x + 0.5) / STAGE * 2.0 - 1.0;
                double rim = 1.0 - Math.pow(Math.abs(u), 6.0);

                put(image, x, y, twisted(u - wander, v) * rim);
                put(image, STAGE + x, y, beaded(u - wander, bead) * rim);
                put(image, 2 * STAGE + x, y, aged(u, v) * rim);
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

    /**
     * The fresh line as it leaves the aircraft: the wingtip vortices wind it
     * into a flat sheet that twists about its own axis, about every 900 m.
     * Face on it is wider and paler, edge on narrower and denser, which is
     * what makes a young trail look like a corkscrew when seen up close.
     */
    private static double twisted(double u, double v) {
        double face = Math.abs(Math.cos(Math.PI * TWISTS * v));
        double width = 0.4 + 0.6 * face;
        return (0.7 + 0.45 * (1.0 - face)) * fresh(u / width);
    }

    /** A thin dense line with a crisp edge and only a trace of shoulder. */
    private static double fresh(double u) {
        return 0.88 * Math.exp(-u * u * 9.0) + 0.12 * Math.exp(-u * u * 2.5);
    }

    /** The line, swollen where the vortex pair has bunched it up. */
    private static double beaded(double u, double bead) {
        double width = 0.86 + 0.26 * bead;
        double local = u / width;
        return (0.88 + 0.16 * bead) * fresh(local);
    }

    /**
     * A wide faint band. The spine sits towards one edge; streaks slant away
     * from it towards the other, fading as they go.
     */
    private static double aged(double u, double v) {
        double spineAt = -0.45;
        double spine = 0.7 * Math.exp(-sq(u - spineAt) * 22.0);

        // Distance from the spine towards the far edge, 0 at the spine.
        double hang = clamp((u - spineAt) / (1.0 - spineAt), 0.0, 1.0);
        // The streaks lean back along the trail as they fall, more the further
        // they have fallen.
        double s = v * STREAKS + Math.pow(hang, 1.4) * 5.0 + 0.6 * signed(fbm1(v, 9, 2, 7));
        double streak = ridge(periodicNoise(s, STREAKS, 11));
        // Some streaks are long and strong, some barely start.
        double reach = 0.25 + 0.75 * fbm1(v, STREAKS / 2, 1, 13);
        double falloff = Math.exp(-sq(hang / reach) * 2.0);
        double start = smooth(clamp(hang / 0.12, 0.0, 1.0));
        double streaks = 0.55 * streak * falloff * start;

        // A faint veil under it all, so the band is one thing and not a comb.
        double veil = 0.12 * Math.exp(-sq(u + 0.15) * 1.6);
        // Patchy along its length, as old cirrus is.
        double patch = 0.55 + 0.45 * fbm1(v, 8, 2, 17);
        return (spine + streaks + veil) * patch;
    }

    /** 0 between puffs, up to 1 at one, irregularly spaced and sized. */
    private static double beads(double v) {
        double x = v * BEADS;
        int cell = (int) Math.floor(x);
        double best = 0.0;
        for (int c = cell - 1; c <= cell + 1; c++) {
            int wrapped = Math.floorMod(c, BEADS);
            double centre = c + 0.25 + 0.5 * hash(wrapped, 0, 3);
            double size = 0.35 + 0.65 * hash(wrapped, 0, 5);
            double d = (x - centre) / 0.3;
            best = Math.max(best, size * Math.exp(-d * d));
        }
        return best;
    }

    private static void put(BufferedImage image, int x, int y, double alpha) {
        int a = (int) Math.round(clamp(alpha, 0.0, 1.0) * 255.0);
        image.setRGB(x, y, (a << 24) | 0x00FFFFFF);
    }

    /** Value noise along V, periodic over one tile, 0 to 1. */
    private static double fbm1(double v, int cycles, int octaves, int seed) {
        double sum = 0.0;
        double weight = 0.0;
        double amplitude = 1.0;
        for (int o = 0; o < octaves; o++) {
            int period = cycles << o;
            sum += amplitude * periodicNoise(v * period, period, seed + o * 31);
            weight += amplitude;
            amplitude *= 0.5;
        }
        return sum / weight;
    }

    /** Smooth value noise with the given period, 0 to 1. */
    private static double periodicNoise(double x, int period, int seed) {
        int i = (int) Math.floor(x);
        double t = smooth(x - i);
        double a = hash(Math.floorMod(i, period), 0, seed);
        double b = hash(Math.floorMod(i + 1, period), 0, seed);
        return a + (b - a) * t;
    }

    /** Turns smooth noise into narrow crests: 1 on a streak, falling away fast. */
    private static double ridge(double n) {
        double r = 1.0 - Math.abs(n * 2.0 - 1.0);
        return r * r * r;
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

    private static double sq(double x) {
        return x * x;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
