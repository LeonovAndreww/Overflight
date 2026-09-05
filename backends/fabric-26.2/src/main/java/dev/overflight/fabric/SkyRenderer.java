package dev.overflight.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.overflight.core.atmo.HumidityField;
import dev.overflight.core.atmo.Isa;
import dev.overflight.core.atmo.SchmidtAppleman;
import dev.overflight.core.render.MeshBuffer;
import dev.overflight.core.render.SkyProjection;
import dev.overflight.core.render.TrailMeshBuilder;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.TrafficGenerator;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailSampler;
import dev.overflight.core.trail.TrailSettings;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws the sky.
 *
 * The work is split the way 26.2 wants it: everything that reads the world
 * happens in the extraction phase, and the draw phase does nothing but hand
 * finished numbers to a vertex consumer. Because those two phases can run on
 * different threads, geometry is built into one buffer and published to another.
 *
 * Nothing here decides what the sky looks like -- that all lives in the core,
 * which knows nothing about Minecraft.
 */
public final class SkyRenderer {
    private static final Identifier TRAIL_TEXTURE =
            Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID, "textures/trail.png");
    /** Sky light and block light both at maximum: a contrail is lit by the sun, not the world. */
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** How far out aircraft are considered, in metres. */
    private static final double VISIBLE_RADIUS = 150000.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final long DAY_LENGTH_TICKS = 24000L;

    private final TrafficGenerator traffic = new TrafficGenerator(AircraftCatalog.defaults());
    private final TrailSampler sampler = new TrailSampler();
    private final TrailMeshBuilder meshBuilder = new TrailMeshBuilder();
    private final TrailSettings trailSettings = new TrailSettings();
    private final SkyProjection projection = new SkyProjection(512.0);

    /** Two buffers so the draw phase can read one while the next frame fills the other. */
    private final MeshBuffer[] buffers = {new MeshBuffer(), new MeshBuffer()};
    private int writeIndex;
    private volatile MeshBuffer ready;

    private HumidityField humidity;
    private long humiditySeed;

    // Until there is a config screen these stand in for it.
    private double densityPerHour = 45.0;
    private int maxAircraft = 32;

    public void register() {
        LevelExtractionEvents.END_EXTRACTION.register(this::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
    }

    private void extract(LevelExtractionContext context) {
        ClientLevel level = context.level();
        Camera camera = context.camera();
        if (level == null || camera == null) {
            ready = null;
            return;
        }

        long seed = seedFor(level);
        if (humidity == null || humiditySeed != seed) {
            humidity = new HumidityField(seed);
            humiditySeed = seed;
        }

        float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        // Game time is the same number on every client, so two players standing
        // together see the same aircraft without a packet passing between them.
        double timeS = (level.getGameTime() + partialTick) / TICKS_PER_SECOND;

        Vec3 eye = camera.position();
        double rain = level.getRainLevel(partialTick);

        // The sun travels the east-west plane: overhead at midday, on the horizon
        // at dawn and dusk.
        double dayAngle = (level.getOverworldClockTime() % DAY_LENGTH_TICKS)
                / (double) DAY_LENGTH_TICKS * 2.0 * Math.PI;
        double sunX = Math.cos(dayAngle);
        double sunY = Math.sin(dayAngle);

        MeshBuffer building = buffers[writeIndex];
        writeIndex ^= 1;
        building.clear();

        List<Flight> flights = traffic.collect(seed, timeS, eye.x, eye.z,
                VISIBLE_RADIUS, densityPerHour, maxAircraft);

        for (int i = 0; i < flights.size(); i++) {
            Flight flight = flights.get(i);
            double altitude = flight.altitudeM;
            double temperature = Isa.temperature(altitude);
            double pressure = Isa.pressure(altitude);
            double relativeHumidity = humidity.relativeHumidity(
                    flight.xAt(timeS), flight.zAt(timeS), altitude, timeS, rain);

            boolean forms = SchmidtAppleman.formsContrail(
                    temperature, pressure, relativeHumidity, flight.type.engine);
            boolean persists =
                    SchmidtAppleman.persistenceRatio(temperature, relativeHumidity) > 1.0;

            Trail trail = sampler.sample(flight, timeS, forms, persists, trailSettings);
            meshBuilder.build(trail, eye.x, eye.y, eye.z, sunX, sunY, 0.0,
                    projection, trailSettings, building);
        }

        ready = building.quadCount() > 0 ? building : null;
    }

    private void submit(LevelRenderContext context) {
        MeshBuffer mesh = ready;
        if (mesh == null || mesh.quadCount() == 0) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        // A vanilla render type, so shader packs route it through their own
        // programs and light and fog it like anything else in the world.
        context.submitNodeCollector().submitCustomGeometry(
                poseStack, RenderTypes.entityTranslucent(TRAIL_TEXTURE),
                (pose, consumer) -> emit(mesh, pose, consumer));
    }

    private static void emit(MeshBuffer mesh, PoseStack.Pose pose, VertexConsumer consumer) {
        float[] positions = mesh.positions();
        float[] uvs = mesh.uvs();
        float[] colours = mesh.colours();

        for (int v = 0; v < mesh.vertexCount(); v++) {
            int p = v * 3;
            int t = v * 2;
            int c = v * 4;
            consumer.addVertex(pose, positions[p], positions[p + 1], positions[p + 2])
                    .setColor(colours[c], colours[c + 1], colours[c + 2], colours[c + 3])
                    .setUv(uvs[t], uvs[t + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, 0.0f, 1.0f, 0.0f);
        }
    }

    /**
     * The seed the whole sky follows from.
     *
     * Taken from the dimension rather than the world seed, which a client is
     * never told. Every client on a server derives the same number from the same
     * dimension, so the ambient sky already agrees everywhere without the server
     * needing this mod at all.
     */
    private static long seedFor(ClientLevel level) {
        return level.dimension().identifier().toString().hashCode() * 0x9E3779B97F4A7C15L;
    }
}
