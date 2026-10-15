import dev.overflight.core.atmo.Isa;
import dev.overflight.core.render.MeshBuffer;
import dev.overflight.core.render.SkyProjection;
import dev.overflight.core.render.TrailMeshBuilder;
import dev.overflight.core.render.TrailPuffBuilder;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailSampler;
import dev.overflight.core.trail.TrailSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Renders the sky the mod would draw, without Minecraft, to a PNG.
 *
 * Runs the real sampler and mesh builders, then rasterises their quads in
 * software with the real textures: perspective-correct, bilinear, blended in
 * the order given. No fog, no lightmap, no shader pack -- what the mod's own
 * pipeline does with a white tint at full daylight. For looking at shapes, not
 * for judging colour.
 *
 * Build the core first, then:
 *   java -cp overflight-core/build/classes/java/main tools/RenderPreview.java out.png [fov] [pitch] [yaw] [detailed]
 */
public final class RenderPreview {
    private static final int W = 1280;
    private static final int H = 720;
    private static final double SHELL = 512.0;

    public static void main(String[] args) throws IOException {
        String out = args.length > 0 ? args[0] : "preview.png";
        double fov = Math.toRadians(args.length > 1 ? Double.parseDouble(args[1]) : 70.0);
        double pitch = Math.toRadians(args.length > 2 ? Double.parseDouble(args[2]) : 35.0);
        double yaw = Math.toRadians(args.length > 3 ? Double.parseDouble(args[3]) : 0.0);
        boolean detailed = args.length <= 4 || Boolean.parseBoolean(args[4]);

        float[] trailTex = alpha("common/resources/assets/overflight/textures/trail.png");
        int trailW = width, trailH = height;
        float[] puffTex = alpha("common/resources/assets/overflight/textures/puff.png");
        int puffW = width, puffH = height;

        AircraftType type = AircraftCatalog.defaults().byId("airliner_widebody");
        double altitude = Isa.flightLevelToMetres(350);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        double now = 3000.0;

        // Flights crossing the view at different times, so one picture holds
        // trails of every age.
        // Each passed over a point near the observer at a different time, so
        // one picture holds trails of every age overhead.
        double[][] legs = {
                // over x, over z, heading deg, minutes ago
                {-6000, -4000, 80, 1.5},
                {3000, 2000, 100, 5},
                {-2000, 9000, 290, 12},
                {8000, -9000, 20, 25},
                {-9000, 5000, 200, 35},
        };

        TrailSettings settings = new TrailSettings();
        settings.alphaCap = 0.45;
        settings.pixelAngle = fov / H;
        settings.viewX = Math.sin(yaw) * Math.cos(pitch);
        settings.viewY = Math.sin(pitch);
        settings.viewZ = -Math.cos(yaw) * Math.cos(pitch);
        settings.viewCos = Math.cos(Math.atan(Math.tan(fov * 0.5) * Math.sqrt(1.0 + (double) W * W / H / H)));
        TrailSampler sampler = new TrailSampler();
        TrailMeshBuilder ribbons = new TrailMeshBuilder();
        ribbons.spine = detailed;
        TrailPuffBuilder puffs = new TrailPuffBuilder();
        SkyProjection projection = new SkyProjection(SHELL);
        MeshBuffer ribbonMesh = new MeshBuffer();
        MeshBuffer puffMesh = new MeshBuffer();

        double sunX = 0.3, sunY = 0.8, sunZ = 0.5;
        for (int i = 0; i < legs.length; i++) {
            double heading = Math.toRadians(legs[i][2]);
            double passed = now - legs[i][3] * 60.0;
            double start = passed - 900.0;
            double back = speed * (passed - start);
            Flight flight = new Flight(1000L + i * 7919L, type,
                    legs[i][0] - Math.sin(heading) * back, legs[i][1] + Math.cos(heading) * back,
                    heading, altitude, speed, start, 3600.0, 0, 1);
            Trail trail = sampler.sample(flight, now, true, true, settings);
            ribbons.build(trail, 0, 64, 0, sunX, sunY, sunZ, 1.0, projection, settings, ribbonMesh);
            if (detailed) {
                puffs.build(trail, 0, 64, 0, sunX, sunY, sunZ, 1.0, 1.0, 1.0, 1.0,
                        projection, settings, puffMesh);
            }
        }
        System.out.println("ribbon quads " + ribbonMesh.quadCount() + ", sprite quads " + puffMesh.quadCount());
        if (System.getProperty("bench") != null) {
            // Time one frame's worth of building, after the JIT has warmed up.
            long total = 0;
            int frames = 300;
            for (int f = 0; f < frames + 100; f++) {
                ribbonMesh.clear();
                puffMesh.clear();
                long t0 = System.nanoTime();
                for (int i = 0; i < legs.length; i++) {
                    double heading = Math.toRadians(legs[i][2]);
                    double passed = now - legs[i][3] * 60.0;
                    double start = passed - 900.0;
                    double back = speed * (passed - start);
                    Flight flight = new Flight(1000L + i * 7919L, type,
                            legs[i][0] - Math.sin(heading) * back, legs[i][1] + Math.cos(heading) * back,
                            heading, altitude, speed, start, 3600.0, 0, 1);
                    Trail trail = sampler.sample(flight, now + f * 0.05, true, true, settings);
                    ribbons.build(trail, 0, 64, 0, sunX, sunY, sunZ, 1.0, projection, settings, ribbonMesh);
                    if (detailed) {
                        puffs.build(trail, 0, 64, 0, sunX, sunY, sunZ, 1.0, 1.0, 1.0, 1.0,
                                projection, settings, puffMesh);
                    }
                }
                if (f >= 100) {
                    total += System.nanoTime() - t0;
                }
            }
            System.out.printf("%.3f ms per frame for %d trails%n", total / 1e6 / frames, legs.length);
        }

        float[][] frame = new float[3][W * H];
        for (int p = 0; p < W * H; p++) {
            double y = (double) (p / W) / H;
            frame[0][p] = (float) (0.47 + 0.10 * y);
            frame[1][p] = (float) (0.62 + 0.10 * y);
            frame[2][p] = (float) (0.92 + 0.04 * y);
        }

        Camera camera = new Camera(fov, pitch, yaw);
        draw(ribbonMesh, trailTex, trailW, trailH, camera, frame);
        draw(puffMesh, puffTex, puffW, puffH, camera, frame);

        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int p = 0; p < W * H; p++) {
            int r = (int) Math.round(Math.min(1, frame[0][p]) * 255);
            int g = (int) Math.round(Math.min(1, frame[1][p]) * 255);
            int b = (int) Math.round(Math.min(1, frame[2][p]) * 255);
            image.setRGB(p % W, p / W, (r << 16) | (g << 8) | b);
        }
        ImageIO.write(image, "PNG", new File(out));
    }

    private static int width;
    private static int height;

    private static float[] alpha(String path) throws IOException {
        BufferedImage image = ImageIO.read(new File(path));
        width = image.getWidth();
        height = image.getHeight();
        float[] alpha = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                alpha[y * width + x] = (image.getRGB(x, y) >>> 24) / 255.0f;
            }
        }
        return alpha;
    }

    private static final class Camera {
        final double fx, fy, fz, rx, ry, rz, ux, uy, uz, focal;

        Camera(double fov, double pitch, double yaw) {
            fx = Math.sin(yaw) * Math.cos(pitch);
            fy = Math.sin(pitch);
            fz = -Math.cos(yaw) * Math.cos(pitch);
            // right = forward x up(0,1,0)
            double rxx = -fz, rzz = fx;
            double rl = Math.sqrt(rxx * rxx + rzz * rzz);
            rx = rxx / rl;
            ry = 0;
            rz = rzz / rl;
            ux = ry * fz - rz * fy;
            uy = rz * fx - rx * fz;
            uz = rx * fy - ry * fx;
            focal = (H / 2.0) / Math.tan(fov / 2.0);
        }
    }

    private static void draw(MeshBuffer mesh, float[] tex, int tw, int th, Camera c, float[][] frame) {
        float[] pos = mesh.positions();
        float[] uv = mesh.uvs();
        float[] col = mesh.colours();
        double[] sx = new double[4], sy = new double[4], iw = new double[4];
        for (int q = 0; q < mesh.quadCount(); q++) {
            boolean behind = false;
            for (int k = 0; k < 4; k++) {
                int i = (q * 4 + k) * 3;
                double x = pos[i], y = pos[i + 1], z = pos[i + 2];
                double d = x * c.fx + y * c.fy + z * c.fz;
                if (d < 1.0) {
                    behind = true;
                    break;
                }
                sx[k] = W / 2.0 + (x * c.rx + y * c.ry + z * c.rz) / d * c.focal;
                sy[k] = H / 2.0 - (x * c.ux + y * c.uy + z * c.uz) / d * c.focal;
                iw[k] = 1.0 / d;
            }
            if (behind) {
                continue;
            }
            triangle(q, 0, 1, 2, sx, sy, iw, uv, col, tex, tw, th, frame);
            triangle(q, 0, 2, 3, sx, sy, iw, uv, col, tex, tw, th, frame);
        }
    }

    private static void triangle(int q, int a, int b, int d, double[] sx, double[] sy, double[] iw,
                                 float[] uv, float[] col, float[] tex, int tw, int th, float[][] frame) {
        int minX = (int) Math.max(0, Math.floor(Math.min(sx[a], Math.min(sx[b], sx[d]))));
        int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[d]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(sy[a], Math.min(sy[b], sy[d]))));
        int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[d]))));
        double area = (sx[b] - sx[a]) * (sy[d] - sy[a]) - (sx[d] - sx[a]) * (sy[b] - sy[a]);
        if (Math.abs(area) < 1e-9 || maxX < minX || maxY < minY) {
            return;
        }
        int[] v = {a, b, d};
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double cx = px + 0.5, cy = py + 0.5;
                double w0 = ((sx[b] - cx) * (sy[d] - cy) - (sx[d] - cx) * (sy[b] - cy)) / area;
                double w1 = ((sx[d] - cx) * (sy[a] - cy) - (sx[a] - cx) * (sy[d] - cy)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                double[] w = {w0, w1, w2};
                double norm = 0, u = 0, t = 0, alpha = 0, r = 0, g = 0, bl = 0;
                for (int k = 0; k < 3; k++) {
                    int vi = q * 4 + v[k];
                    double pw = w[k] * iw[v[k]];
                    norm += pw;
                    u += pw * uv[vi * 2];
                    t += pw * uv[vi * 2 + 1];
                    alpha += pw * col[vi * 4 + 3];
                    r += pw * col[vi * 4];
                    g += pw * col[vi * 4 + 1];
                    bl += pw * col[vi * 4 + 2];
                }
                u /= norm;
                t /= norm;
                alpha /= norm;
                double texel = sample(tex, tw, th, u, t);
                double aa = Math.min(1.0, texel * alpha);
                int p = py * W + px;
                frame[0][p] = (float) (frame[0][p] * (1 - aa) + r / norm * aa);
                frame[1][p] = (float) (frame[1][p] * (1 - aa) + g / norm * aa);
                frame[2][p] = (float) (frame[2][p] * (1 - aa) + bl / norm * aa);
            }
        }
    }

    /** Bilinear, repeating, like the game's sampler for these textures. */
    private static double sample(float[] tex, int tw, int th, double u, double v) {
        double x = u * tw - 0.5, y = v * th - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        double a = at(tex, tw, th, x0, y0), b = at(tex, tw, th, x0 + 1, y0);
        double c = at(tex, tw, th, x0, y0 + 1), d = at(tex, tw, th, x0 + 1, y0 + 1);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static double at(float[] tex, int tw, int th, int x, int y) {
        return tex[Math.floorMod(y, th) * tw + Math.floorMod(x, tw)];
    }
}
