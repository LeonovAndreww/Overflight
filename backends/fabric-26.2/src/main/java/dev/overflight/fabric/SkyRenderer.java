package dev.overflight.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.overflight.core.atmo.HumidityField;
import dev.overflight.core.atmo.Isa;
import dev.overflight.core.atmo.SchmidtAppleman;
import dev.overflight.core.config.OverflightConfig;
import dev.overflight.core.render.AircraftMeshBuilder;
import dev.overflight.core.render.MeshBuffer;
import dev.overflight.core.render.SkyProjection;
import dev.overflight.core.render.TrailMeshBuilder;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.ManualTraffic;
import dev.overflight.core.traffic.TrafficGenerator;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailSampler;
import dev.overflight.core.trail.TrailSettings;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws the sky.
 *
 * The work is split the way 26.2 wants it: everything that reads the world
 * happens in the extraction phase, and the draw phase does nothing but hand
 * finished numbers to a vertex consumer. Because those two phases can run on
 * different threads, geometry is built into one buffer and published through
 * another.
 *
 * Nothing here decides what the sky looks like -- that all lives in the core,
 * which knows nothing about Minecraft.
 */
public final class SkyRenderer {
    private static final Identifier TRAIL_TEXTURE =
            Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID, "textures/trail.png");
    private static final Identifier AIRCRAFT_TEXTURE =
            Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID, "textures/aircraft.png");
    /** Sky light and block light both at maximum: a contrail is lit by the sun, not the world. */
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final long DAY_LENGTH_TICKS = 24000L;

    private final TrailSampler sampler = new TrailSampler();
    private final TrailMeshBuilder meshBuilder = new TrailMeshBuilder();
    private final AircraftMeshBuilder aircraftBuilder = new AircraftMeshBuilder();
    private final ManualTraffic manual = new ManualTraffic();

    /** Two buffers so the draw phase can read one while the next frame fills the other. */
    private final MeshBuffer[] trailBuffers = {new MeshBuffer(), new MeshBuffer()};
    private final MeshBuffer[] aircraftBuffers = {new MeshBuffer(), new MeshBuffer()};
    private int writeIndex;
    private volatile MeshBuffer readyTrails;
    private volatile MeshBuffer readyAircraft;

    private OverflightConfig config;
    private TrafficGenerator traffic;
    private TrailSettings trailSettings;
    private SkyProjection projection;

    private HumidityField humidity;
    private long humiditySeed = Long.MIN_VALUE;
    private double shellRadiusInUse;

    private volatile int lastFlightCount;
    private volatile int lastTrailCount;
    private volatile int lastQuadCount;

    public SkyRenderer(OverflightConfig config) {
        applyConfig(config);
    }

    /** Rebuilds everything derived from the config. Safe to call while running. */
    public void applyConfig(OverflightConfig config) {
        this.config = config;
        this.traffic = new TrafficGenerator(
                AircraftCatalog.defaults().withWeights(config.traffic.mix));
        this.projection = new SkyProjection(config.graphics.shellRadius);

        TrailSettings settings = new TrailSettings();
        settings.persistenceMultiplier = config.trails.persistenceMultiplier;
        settings.spreadRateMPerSec = config.trails.spreadRateMPerSec;
        settings.maxHalfWidthM = config.trails.maxHalfWidthM;
        settings.opacity = config.trails.opacity;
        settings.windSpeedMs = config.trails.windSpeedMs;
        settings.windDirectionDeg = config.trails.windDirectionDeg;
        settings.shearVariationMs = config.trails.shearVariationMs;
        settings.spreadVariation = config.trails.spreadVariation;
        settings.vortexSinkM = config.trails.vortexSinkM;
        settings.maxPoints = config.graphics.trailDetail;
        if (!config.trails.crowInstability) {
            // Pushing onset past any trail's lifetime switches the bulging off
            // without a second code path through the sampler.
            settings.crowOnsetSeconds = Double.MAX_VALUE / 4.0;
            settings.crowFullSeconds = Double.MAX_VALUE / 2.0;
        }
        this.trailSettings = settings;

        // Force a rebuild of the humidity field against the new numbers.
        this.humidity = null;
        this.humiditySeed = Long.MIN_VALUE;
    }

    public OverflightConfig config() {
        return config;
    }

    public ManualTraffic manualTraffic() {
        return manual;
    }

    public TrafficGenerator traffic() {
        return traffic;
    }

    public TrailSettings trailSettings() {
        return trailSettings;
    }

    public HumidityField humidity() {
        return humidity;
    }

    public double densityPerHour() {
        return config.traffic.densityPerHour;
    }

    public void densityPerHour(double value) {
        config.traffic.densityPerHour = value;
    }

    public int maxAircraft() {
        return config.traffic.maxAircraft;
    }

    /** The shell radius actually in use, which depends on whether a pack is drawing. */
    public double shellRadiusInUse() {
        return shellRadiusInUse;
    }

    public double visibleRadius() {
        return config.graphics.visibleRangeM;
    }

    public int lastFlightCount() {
        return lastFlightCount;
    }

    public int lastTrailCount() {
        return lastTrailCount;
    }

    public int lastQuadCount() {
        return lastQuadCount;
    }

    public static long seedForLevel(ClientLevel level) {
        return seedFor(level);
    }

    public void register() {
        LevelExtractionEvents.END_EXTRACTION.register(this::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
    }

    private void extract(LevelExtractionContext context) {
        ClientLevel level = context.level();
        Camera camera = context.camera();
        if (!config.enabled || level == null || camera == null) {
            readyTrails = null;
            readyAircraft = null;
            return;
        }

        long seed = seedFor(level);
        if (humidity == null || humiditySeed != seed) {
            humidity = new HumidityField(seed);
            humidity.supersaturatedFraction = config.atmosphere.supersaturatedFraction;
            humidity.patchSizeM = config.atmosphere.patchSizeM;
            humidity.driftSpeedMs = config.atmosphere.driftSpeedMs;
            humidity.evolutionSeconds = config.atmosphere.evolutionSeconds;
            humidity.weatherInfluence = config.atmosphere.weatherInfluence;
            humiditySeed = seed;
        }

        float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        // Game time is the same number on every client, so two players standing
        // together see the same aircraft without a packet passing between them.
        double timeS = (level.getGameTime() + partialTick) / TICKS_PER_SECOND;

        Vec3 eye = camera.position();
        double rain = level.getRainLevel(partialTick);

        // Minecraft puts noon at 6000 ticks, so the sun's height above the
        // horizon is simply the sine of the day angle.
        double dayAngle = (level.getOverworldClockTime() % DAY_LENGTH_TICKS)
                / (double) DAY_LENGTH_TICKS * 2.0 * Math.PI;
        double sunX = Math.cos(dayAngle);
        double sunY = Math.sin(dayAngle);
        double daylight = smoothstep(-0.12, 0.18, sunY);
        double lightsDaylight = config.traffic.navigationLights ? daylight : 1.0;

        // A Minecraft night is nowhere near black, and a contrail under a moon is
        // visible in life too, so trails dim after dark rather than going out.
        float[] phases = DimensionType.MOON_BRIGHTNESS_PER_PHASE;
        int moonPhase = (int) ((level.getOverworldClockTime() / DAY_LENGTH_TICKS)
                % phases.length);
        double moon = phases[moonPhase];
        double nightGlow = config.trails.nightVisibility * (0.45 + 0.55 * moon);
        double illumination = Math.max(daylight, nightGlow);

        updateShellRadius();

        MeshBuffer trailMesh = trailBuffers[writeIndex];
        MeshBuffer aircraftMesh = aircraftBuffers[writeIndex];
        writeIndex ^= 1;
        trailMesh.clear();
        aircraftMesh.clear();

        List<Flight> flights = traffic.collect(seed, timeS, eye.x, eye.z,
                config.graphics.visibleRangeM, config.traffic.densityPerHour,
                config.traffic.maxAircraft);
        flights.addAll(manual.collect(timeS));

        int trailsDrawn = 0;
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
            if (!trail.isEmpty()) {
                trailsDrawn++;
            }
            // Scaling the sun vector by its height flattens the forward-scattering
            // peak as the sun sets, instead of leaving trails brightest towards a
            // sun that is no longer there.
            meshBuilder.build(trail, eye.x, eye.y, eye.z,
                    sunX * daylight, sunY * daylight, 0.0, illumination,
                    projection, trailSettings, trailMesh);
            aircraftBuilder.build(flight, timeS, eye.x, eye.y, eye.z, sunX, sunY, 0.0,
                    lightsDaylight, projection, aircraftMesh);
        }

        lastFlightCount = flights.size();
        lastTrailCount = trailsDrawn;
        lastQuadCount = trailMesh.quadCount() + aircraftMesh.quadCount();

        readyTrails = trailMesh.quadCount() > 0 ? trailMesh : null;
        readyAircraft = aircraftMesh.quadCount() > 0 ? aircraftMesh : null;
    }

    /**
     * Puts the sky shell where the rest of the pipeline will treat it correctly.
     *
     * The radius is arbitrary as far as the sky itself goes -- angular sizes come
     * out right whatever it is -- but it is the depth everything downstream sees,
     * and the two renderers want opposite things.
     *
     * A shader pack replaces Minecraft's fog with its own atmosphere, so the
     * shell can sit far out, which is also what puts trails behind the pack's
     * clouds. Vanilla blends anything past its fog end into the fog colour
     * outright, so the same shell comes out flat grey. Under vanilla the shell
     * therefore comes in to where the fog has barely started.
     */
    private void updateShellRadius() {
        double radius = config.graphics.shellRadius;
        if (config.graphics.keepInsideVanillaFog && !ShaderPacks.inUse()) {
            Minecraft client = Minecraft.getInstance();
            int chunks = client == null ? 8 : client.options.getEffectiveRenderDistance();
            radius = Math.min(radius, Math.max(48.0, chunks * 16.0 * 0.45));
        }
        if (radius != shellRadiusInUse) {
            projection = new SkyProjection(radius);
            shellRadiusInUse = radius;
        }
    }

    private void submit(LevelRenderContext context) {
        PoseStack poseStack = context.poseStack();
        submitMesh(context, poseStack, readyAircraft, AIRCRAFT_TEXTURE);
        submitMesh(context, poseStack, readyTrails, TRAIL_TEXTURE);
    }

    private void submitMesh(LevelRenderContext context, PoseStack poseStack,
                            MeshBuffer mesh, Identifier texture) {
        if (mesh == null || mesh.quadCount() == 0) {
            return;
        }
        // A vanilla render type, so shader packs route it through their own
        // programs rather than needing anything written for them.
        //
        // Emissive specifically. A contrail is scattered sunlight, not a surface,
        // and the ordinary entity path had packs shading it by normal and shadow
        // map -- a quad ten kilometres up with no block light around it came out
        // darker than the sky it was supposed to be brighter than. Its brightness
        // is worked out here instead, from the angle to the sun.
        context.submitNodeCollector().submitCustomGeometry(
                poseStack, RenderTypes.entityTranslucentEmissive(texture),
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

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
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
