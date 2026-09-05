import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the mod icon: a sky with two crossing trails and the aircraft that left
 * the newer one.
 *
 * Generated rather than drawn by hand so it stays consistent with the trail
 * texture, and so the composition can be adjusted without opening an editor.
 *
 * Run with:  java tools/GenerateIcon.java
 */
public final class GenerateIcon {
    private static final int SIZE = 128;
    private static final String OUT =
            "backends/fabric-26.2/src/main/resources/assets/overflight/icon.png";

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < SIZE; y++) {
            double v = (double) y / (SIZE - 1);
            // Sky gradient: deeper overhead, paler towards the horizon. Held in
            // its own variables per pixel, since the trails paint over a copy.
            double skyR = lerp(0.20, 0.60, v);
            double skyG = lerp(0.44, 0.75, v);
            double skyB = lerp(0.80, 0.92, v);

            for (int x = 0; x < SIZE; x++) {
                double u = (double) x / (SIZE - 1);
                double r = skyR;
                double g = skyG;
                double b = skyB;

                // An old trail, spread wide and faint, and a fresh sharp one
                // crossing it. Both run corner to corner but at different angles.
                double old = ribbon(u, v, 0.02, 0.30, 0.98, 0.52, 0.019, 0.45);
                double fresh = ribbon(u, v, 0.10, 0.95, 0.80, 0.25, 0.0060, 1.0);

                double ink = Math.min(1.0, old + fresh);
                r = lerp(r, 1.0, ink);
                g = lerp(g, 1.0, ink);
                b = lerp(b, 1.0, ink);

                // The aircraft, at the leading end of the fresh trail.
                double aircraft = plan(u, v, 0.82, 0.23);
                r = lerp(r, 0.16, aircraft);
                g = lerp(g, 0.18, aircraft);
                b = lerp(b, 0.22, aircraft);

                image.setRGB(x, y, 0xFF000000
                        | (channel(r) << 16) | (channel(g) << 8) | channel(b));
            }
        }

        File file = new File(OUT);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("could not create " + parent);
        }
        ImageIO.write(image, "PNG", file);
        System.out.println("wrote " + file.getPath() + " (" + SIZE + "x" + SIZE + ")");
    }

    /** A soft band between two points, fading out past the far end. */
    private static double ribbon(double u, double v, double x0, double y0,
                                 double x1, double y1, double halfWidth, double strength) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        double alongScaled = ((u - x0) * dx + (v - y0) * dy) / (length * length);
        double t = clamp(alongScaled, 0.0, 1.0);
        double px = x0 + dx * t;
        double py = y0 + dy * t;
        double distance = Math.hypot(u - px, v - py);

        double across = Math.exp(-(distance * distance) / (halfWidth * halfWidth));
        // Thin towards the older end, the way a trail does as it disperses.
        double taper = 0.45 + 0.55 * alongScaled;
        return clamp(across * strength * clamp(taper, 0.0, 1.0), 0.0, 1.0);
    }

    /** A small plan view: fuselage plus wings. */
    private static double plan(double u, double v, double cx, double cy) {
        double dx = u - cx;
        double dy = v - cy;
        // Rotated to sit along the fresh trail.
        double angle = Math.atan2(0.25 - 0.95, 0.80 - 0.10);
        double along = dx * Math.cos(angle) + dy * Math.sin(angle);
        double across = -dx * Math.sin(angle) + dy * Math.cos(angle);

        boolean fuselage = Math.abs(along) < 0.105 && Math.abs(across) < 0.016;
        boolean wing = Math.abs(along + 0.014) < 0.021 && Math.abs(across) < 0.090;
        boolean tail = Math.abs(along + 0.082) < 0.013 && Math.abs(across) < 0.038;
        return (fuselage || wing || tail) ? 1.0 : 0.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int channel(double value) {
        return (int) Math.round(clamp(value, 0.0, 1.0) * 255.0);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
