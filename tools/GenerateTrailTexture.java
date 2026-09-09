import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the trail texture: a cross-section, and nothing else.
 *
 * The texture is deliberately uniform along its length. An earlier version put
 * wisps along it, which tiled -- one repeat every couple of kilometres of trail,
 * a couple of degrees across the sky -- and read as a zebra of regular stripes
 * perpendicular to the trail. Real contrails have no periodic structure at all.
 * Variation along the trail is applied per vertex instead, from noise anchored
 * to when the exhaust left the engine, so it neither repeats nor slides.
 *
 * What is left is the profile across the ribbon, which is where the look comes
 * from: a bright dense core with soft shoulders that fade to nothing, so a trail
 * reads as a band of ice rather than a ruled line.
 *
 * Run with:  java tools/GenerateTrailTexture.java
 */
public final class GenerateTrailTexture {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 4;
    private static final String OUT =
            "common/resources/assets/overflight/textures/trail.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < WIDTH; x++) {
            double u = (x + 0.5) / WIDTH * 2.0 - 1.0;

            // Two Gaussians: a tight bright core where the ice is densest, and a
            // wide faint shoulder for the part that has already spread. One
            // Gaussian alone gives either a hard line or a smear.
            double core = Math.exp(-u * u * 11.0);
            double shoulder = Math.exp(-u * u * 1.9);
            double across = 0.62 * core + 0.38 * shoulder;

            // Take the rim to exactly zero, so neighbouring ribbons blend
            // instead of showing an edge where the quad ends.
            across *= 1.0 - Math.pow(Math.abs(u), 4.0);

            int alpha = (int) Math.round(clamp(across, 0.0, 1.0) * 255.0);
            int argb = (alpha << 24) | 0x00FFFFFF;
            for (int y = 0; y < HEIGHT; y++) {
                image.setRGB(x, y, argb);
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

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
