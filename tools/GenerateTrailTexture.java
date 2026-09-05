import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the trail texture.
 *
 * Kept as source rather than committed as an opaque image so the shape of the
 * falloff can be argued with. Two things matter: the edges have to be soft, or
 * a trail reads as a drawn line, and there has to be structure along the length,
 * or a spreading trail turns into a flat grey band.
 *
 * Run with:  java tools/GenerateTrailTexture.java
 */
public final class GenerateTrailTexture {
    private static final int WIDTH = 32;
    private static final int HEIGHT = 128;
    private static final String OUT =
            "backends/fabric-26.2/src/main/resources/assets/overflight/textures/trail.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < HEIGHT; y++) {
            double v = (double) y / HEIGHT;
            // Wispiness along the trail. Several periods that do not share a
            // common multiple, so the texture repeats without visibly tiling.
            double strands = 0.72
                    + 0.16 * Math.sin(v * Math.PI * 2.0 * 3.0)
                    + 0.08 * Math.sin(v * Math.PI * 2.0 * 7.0 + 1.3)
                    + 0.04 * Math.sin(v * Math.PI * 2.0 * 17.0 + 2.7);

            for (int x = 0; x < WIDTH; x++) {
                double u = (x + 0.5) / WIDTH * 2.0 - 1.0;
                // Gaussian across the width: no hard edge anywhere, which is what
                // keeps a distant trail from looking like a ruled line.
                double across = Math.exp(-u * u * 3.4);
                // Thin it right at the rim so neighbouring quads blend instead of
                // showing a seam.
                across *= 1.0 - Math.pow(Math.abs(u), 6.0);

                int alpha = (int) Math.round(clamp(across * strands, 0.0, 1.0) * 255.0);
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

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
