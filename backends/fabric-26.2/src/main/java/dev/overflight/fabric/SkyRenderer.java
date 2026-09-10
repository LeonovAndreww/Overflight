package dev.overflight.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.overflight.core.atmo.HumidityField;
import dev.overflight.core.atmo.Illumination;
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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
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
    static final Identifier TRAIL_TEXTURE =
            Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID, "textures/trail.png");
    static final Identifier AIRCRAFT_TEXTURE =
            Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID, "textures/aircraft.png");
    /**
     * Sky light and block light both at maximum: a contrail is lit by the sun,
     * not by the world around it. Taken from the game rather than written out.
     */
    private static final int FULL_BRIGHT = Compat.FULL_BRIGHT;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final long DAY_LENGTH_TICKS = 24000L;

    private final TrailSampler sampler = new TrailSampler();
    private final TrailMeshBuilder meshBuilder = new TrailMeshBuilder();
    private final AircraftMeshBuilder aircraftBuilder = new AircraftMeshBuilder();
    private final ManualTraffic manual = new ManualTraffic();
    /** Scratch for the colour of the light on a trail; extraction is one thread. */
    private final double[] tint = new double[3];
    private final double[] sun = new double[3];
    private final SurfaceConvection surface = new SurfaceConvection();

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

    /**
     * Paints every quad solid magenta at full opacity.
     *
     * There is one question the sky cannot answer by eye: whether geometry is
     * missing or merely too faint to make out against a bright sky. A contrail
     * is a low-contrast thing by nature, so "I cannot see it" and "it was never
     * drawn" look identical. This makes them look nothing alike.
     */
    private volatile boolean debugSolid;

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
        settings.fibres = config.trails.fibres;
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
        SkyRenderHooks.register(this);
    }

    /**
     * Works out what the sky looks like this frame and leaves it in a buffer.
     *
     * Takes the world and the camera rather than the render context, because the
     * context is the one thing that differs between game versions. Everything
     * here is the same on all of them.
     */
    public void extract(ClientLevel level, Camera camera, float partialTick) {
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
            humidity.biomeInfluence = config.atmosphere.biomeInfluence;
            humiditySeed = seed;
        }

        // Game time is the same number on every client, so two players standing
        // together see the same aircraft without a packet passing between them.
        double timeS = (level.getGameTime() + partialTick) / TICKS_PER_SECOND;

        Vec3 eye = camera.position();
        double rain = level.getRainLevel(partialTick);
        surface.update(level, eye.x, eye.y, eye.z, level.getGameTime());
        double convection = surface.value();

        // Minecraft puts noon at 6000 ticks, so the sun's height above the
        // horizon is simply the sine of the day angle.
        double dayAngle = (Compat.dayTime(level) % DAY_LENGTH_TICKS)
                / (double) DAY_LENGTH_TICKS * 2.0 * Math.PI;
        // Where the pack has actually put the sun. Left alone this is Minecraft's
        // own overhead track, but a shader pack usually leans it to one side, and
        // lighting a trail from a sun the player cannot see there would put the
        // bright half of the sky in the wrong place.
        Illumination.rotateSunPath(Math.cos(dayAngle), Math.sin(dayAngle),
                ShaderPacks.sunPathRotationDegrees(), sun);
        double sunX = sun[0];
        double sunY = sun[1];
        double sunZ = sun[2];
        double daylight = smoothstep(-0.12, 0.18, sunY);
        double lightsDaylight = config.traffic.navigationLights ? daylight : 1.0;
        double sunElevationDeg = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sunY))));

        // A Minecraft night is nowhere near black, and a contrail under a moon is
        // visible in life too, so trails dim after dark rather than going out.
        float[] phases = DimensionType.MOON_BRIGHTNESS_PER_PHASE;
        int moonPhase = (int) ((Compat.dayTime(level) / DAY_LENGTH_TICKS)
                % phases.length);
        double moon = phases[moonPhase];
        double nightGlow = config.trails.nightVisibility * (0.45 + 0.55 * moon);

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
                    flight.xAt(timeS), flight.zAt(timeS), altitude, timeS, rain, convection);

            boolean forms = SchmidtAppleman.formsContrail(
                    temperature, pressure, relativeHumidity, flight.type.engine);
            boolean persists =
                    SchmidtAppleman.persistenceRatio(temperature, relativeHumidity) > 1.0;

            Trail trail = sampler.sample(flight, timeS, forms, persists, trailSettings);
            if (!trail.isEmpty()) {
                trailsDrawn++;
            }
            // Worked out per aircraft, because how much sun a trail is getting
            // depends on how high it is. After sunset the high ones are still lit
            // while the low ones have gone into the earth's shadow, which is
            // exactly what the evening sky does.
            double effectiveElevation =
                    Illumination.effectiveElevationDegrees(sunElevationDeg, altitude);
            double sunlit = Illumination.sunlight(effectiveElevation);
            Illumination.tint(sunlit, Illumination.warmth(effectiveElevation), tint);
            double lightOnTrail = Math.max(sunlit, nightGlow);

            // Scaling the sun vector by how lit the trail is flattens the
            // forward-scattering peak as the sun goes, instead of leaving trails
            // brightest towards a sun no longer reaching them.
            meshBuilder.build(trail, eye.x, eye.y, eye.z,
                    sunX * sunlit, sunY * sunlit, sunZ * sunlit, lightOnTrail,
                    tint[0], tint[1], tint[2],
                    projection, trailSettings, trailMesh);
            aircraftBuilder.build(flight, timeS, eye.x, eye.y, eye.z, sunX, sunY, sunZ,
                    lightsDaylight, projection, aircraftMesh);
        }

        lastFlightCount = flights.size();
        lastTrailCount = trailsDrawn;
        lastQuadCount = trailMesh.quadCount() + aircraftMesh.quadCount();

        if (debugSolid) {
            paintSolid(trailMesh);
            paintSolid(aircraftMesh);
        }

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
            // Well inside the fog rather than merely within it. Anyone running a
            // level-of-detail mod keeps the vanilla render distance low, so the
            // fog ends within a hundred blocks and a shell at half of that still
            // collects half the fog colour. Angular sizes do not care how close
            // the shell is, so there is nothing to lose by coming right in.
            radius = Math.min(radius, Math.max(16.0, chunks * 16.0 * 0.18));
        }
        if (radius != shellRadiusInUse) {
            projection = new SkyProjection(radius);
            shellRadiusInUse = radius;
        }
    }

    public boolean debugSolid() {
        return debugSolid;
    }

    public void debugSolid(boolean value) {
        debugSolid = value;
    }

    private static void paintSolid(MeshBuffer mesh) {
        float[] colours = mesh.colours();
        for (int v = 0; v < mesh.vertexCount(); v++) {
            int c = v * 4;
            colours[c] = 1.0f;
            colours[c + 1] = 0.0f;
            colours[c + 2] = 1.0f;
            colours[c + 3] = 1.0f;
        }
    }

    /** The trail geometry the draw phase should put on screen, or null. */
    public MeshBuffer readyTrails() {
        return readyTrails;
    }

    /** The aircraft geometry the draw phase should put on screen, or null. */
    public MeshBuffer readyAircraft() {
        return readyAircraft;
    }

    /**
     * Which vanilla pipeline draws our geometry.
     *
     * Vanilla render types either way, so shader packs route these through their
     * own programs rather than needing anything written for them.
     *
     * Which one depends on who is drawing. Vanilla's entity shader applies
     * directional lighting unless the pipeline asks it not to, and the EMISSIVE
     * define only skips the lightmap, not that. With no usable normal arriving
     * the term collapsed to its ambient floor of exactly 0.4, which is what
     * turned a white trail into the grey of 105 measured against the sky. The
     * eyes pipeline is the one that carries NO_CARDINAL_LIGHTING while still
     * blending as ordinary translucency, so vanilla gets that and the colour
     * reaches the screen intact.
     *
     * Shader packs light emissive geometry themselves and never showed any of
     * this, so their path is left exactly as it was.
     */
    public static RenderType renderTypeFor(Identifier texture) {
        return ShaderPacks.inUse()
                ? RenderTypes.entityTranslucentEmissive(texture)
                : RenderTypes.eyes(texture);
    }

    static void emit(MeshBuffer mesh, PoseStack.Pose pose, VertexConsumer consumer) {
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
                    // Deliberately not through the pose. That matrix carries the
                    // camera's rotation, so an "up" normal came out pointing
                    // wherever the player happened to be looking, and vanilla's
                    // directional lighting graded the trail down to about 0.42 of
                    // white -- measured against the sky as a grey of 106 laid on
                    // at 43%. Shader packs light emissive geometry themselves and
                    // never showed it.
                    .setNormal(0.0f, 1.0f, 0.0f);
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
