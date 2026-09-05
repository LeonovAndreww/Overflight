import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the aircraft texture: two regions in one image so the whole aircraft,
 * hull and navigation lights together, costs a single draw.
 *
 * Left half is the hull, near solid with edges soft enough that an airliner two
 * hundred pixels away does not look like a cut-out. Right half is a navigation
 * light, a bright core in a wide glow, which is what a point source actually
 * looks like once the eye and the lens have finished with it.
 *
 * Run with:  java tools/GenerateAircraftTexture.java
 */
public final class GenerateAircraftTexture {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 32;
    private static final String OUT =
            "backends/fabric-26.2/src/main/resources/assets/overflight/textures/aircraft.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < 32; x++) {
                // Hull: flat white, feathered over the outermost pixels only.
                double u = (x + 0.5) / 32.0 * 2.0 - 1.0;
                double v = (y + 0.5) / HEIGHT * 2.0 - 1.0;
                double edge = Math.max(Math.abs(u), Math.abs(v));
                double alpha = 1.0 - smoothstep(0.80, 1.0, edge);
                image.setRGB(x, y, toArgb(alpha));
            }
            for (int x = 32; x < WIDTH; x++) {
                double u = (x - 32 + 0.5) / 32.0 * 2.0 - 1.0;
                double v = (y + 0.5) / HEIGHT * 2.0 - 1.0;
                double radius = Math.sqrt(u * u + v * v);
                // A hard core for the lamp itself and a much wider, much fainter
                // halo for the glare around it.
                double core = 1.0 - smoothstep(0.0, 0.30, radius);
                double halo = 0.42 * (1.0 - smoothstep(0.10, 1.0, radius));
                image.setRGB(x, y, toArgb(Math.min(1.0, core + halo)));
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

    private static int toArgb(double alpha) {
        int a = (int) Math.round(clamp(alpha, 0.0, 1.0) * 255.0);
        return (a << 24) | 0x00FFFFFF;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
