package dev.overflight.forge17;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.WorldProvider;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.block.material.Material;

import dev.overflight.core.atmo.HumidityField;
import dev.overflight.core.atmo.Illumination;
import dev.overflight.core.atmo.Isa;
import dev.overflight.core.atmo.SchmidtAppleman;
import dev.overflight.core.config.OverflightConfig;
import dev.overflight.core.render.AircraftMeshBuilder;
import dev.overflight.core.render.MeshBuffer;
import dev.overflight.core.render.SkyProjection;
import dev.overflight.core.render.TrailMeshBuilder;
import dev.overflight.core.render.TrailPuffBuilder;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.ManualTraffic;
import dev.overflight.core.traffic.TrafficGenerator;
import dev.overflight.core.trail.SkyCover;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailSampler;
import dev.overflight.core.trail.TrailSettings;

/**
 * The sky for 1.7.10: reads the world and builds the meshes ({@link #extract}), then {@link SkyDraw} draws them.
 * Both run on the render thread, from the end of {@code RenderGlobal.renderSky}. Nothing here decides what the sky
 * looks like; that all lives in the core.
 */
public final class SkyRenderer {

    static final ResourceLocation TRAIL_TEXTURE = new ResourceLocation(Overflight17.MODID, "textures/trail.png");
    static final ResourceLocation AIRCRAFT_TEXTURE = new ResourceLocation(Overflight17.MODID, "textures/aircraft.png");
    static final ResourceLocation PUFF_TEXTURE = new ResourceLocation(Overflight17.MODID, "textures/puff.png");

    private static final double TICKS_PER_SECOND = 20.0;
    private static final long DAY_LENGTH_TICKS = 24000L;
    private static final double REUSE_WINDOW_S = 0.04;
    private static final double SMOKE_LIFETIME_S = 90.0;
    /** Brightness of the moon by phase, full moon first (as in the newer versions). */
    private static final float[] MOON_BRIGHTNESS = {1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f};

    private static SkyRenderer instance;

    private final TrailSampler sampler = new TrailSampler();
    private final TrailMeshBuilder meshBuilder = new TrailMeshBuilder();
    private final TrailPuffBuilder puffBuilder = new TrailPuffBuilder();
    private final AircraftMeshBuilder aircraftBuilder = new AircraftMeshBuilder();
    private final ManualTraffic manual = new ManualTraffic();
    private final double[] tint = new double[3];
    private final double[] sun = new double[3];
    private final EmissionAir air = new EmissionAir();
    private final int[] skyColours = {0, 0xFF78A7FF, 0xFFFFFFFF};
    private final SkyCover skyCover = new SkyCover();
    private final List<Trail> trails = new ArrayList<Trail>();

    private final MeshBuffer trailMesh = new MeshBuffer();
    private final MeshBuffer aircraftMesh = new MeshBuffer();
    private final MeshBuffer puffMesh = new MeshBuffer();

    private OverflightConfig config;
    private TrafficGenerator traffic;
    private TrailSettings trailSettings;
    private SkyProjection projection;
    private HumidityField humidity;
    private long humiditySeed = Long.MIN_VALUE;
    private double shellRadiusInUse;
    private double[] lastBuildKey;
    private double lastBuildTimeS;
    private float clearSky = 1.0f;
    private long lastClearNanos;
    private double convection = 0.5;
    private long lastConvectionTick = Long.MIN_VALUE;
    private int lastFlightCount;
    private int lastQuadCount;

    private SkyRenderer(OverflightConfig config) {
        applyConfig(config);
    }

    public static synchronized SkyRenderer get() {
        if (instance == null) {
            instance = new SkyRenderer(ConfigIo.load());
        }
        return instance;
    }

    /** Rebuilds everything derived from the config. */
    public void applyConfig(OverflightConfig config) {
        this.config = config;
        this.traffic = new TrafficGenerator(AircraftCatalog.defaults().withWeights(config.traffic.mix));
        this.projection = new SkyProjection(config.graphics.shellRadius);
        this.shellRadiusInUse = config.graphics.shellRadius;
        this.aircraftBuilder.minAngularHalfSize = config.graphics.aircraftMinAngularSize;
        this.aircraftBuilder.lightFullRangeM = config.traffic.lightFullRangeM;

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
            settings.crowOnsetSeconds = Double.MAX_VALUE / 4.0;
            settings.crowFullSeconds = Double.MAX_VALUE / 2.0;
        }
        this.trailSettings = settings;
        this.humidity = null;
        this.humiditySeed = Long.MIN_VALUE;
        this.lastBuildKey = null;
    }

    public OverflightConfig config() {
        return config;
    }

    public ManualTraffic manualTraffic() {
        return manual;
    }

    public int lastFlightCount() {
        return lastFlightCount;
    }

    public int lastQuadCount() {
        return lastQuadCount;
    }

    MeshBuffer trails() {
        return trailMesh.quadCount() > 0 ? trailMesh : null;
    }

    MeshBuffer aircraft() {
        return aircraftMesh.quadCount() > 0 ? aircraftMesh : null;
    }

    MeshBuffer puffs() {
        return puffMesh.quadCount() > 0 ? puffMesh : null;
    }

    /** Works out what the sky looks like this frame and leaves it in the three meshes. */
    public void extract(Minecraft mc, float partialTick) {
        WorldClient level = mc.theWorld;
        Entity camera = mc.renderViewEntity;
        if (!config.enabled || level == null || camera == null) {
            lastClearNanos = 0L;
            clearMeshes();
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

        long gameTime = level.getTotalWorldTime();
        double timeS = (gameTime + partialTick) / TICKS_PER_SECOND;
        double eyeX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTick;
        double eyeY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTick;
        double eyeZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTick;
        double rain = level.getRainStrength(partialTick);
        updateConvection(level, eyeX, eyeY, eyeZ, gameTime);
        air.rain = rain;
        air.convection = convection;

        long dayTime = level.getWorldTime();
        double dayAngle = (dayTime % DAY_LENGTH_TICKS) / (double) DAY_LENGTH_TICKS * 2.0 * Math.PI;
        Illumination.rotateSunPath(Math.cos(dayAngle), Math.sin(dayAngle), 0.0, sun);
        double sunX = sun[0];
        double sunY = sun[1];
        double sunZ = sun[2];
        double daylight = smoothstep(-0.12, 0.18, sunY);
        double lightsDaylight = config.traffic.navigationLights ? daylight : 1.0;
        double sunElevationDeg = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sunY))));
        double moon = MOON_BRIGHTNESS[level.getMoonPhase() & 7];
        double nightGlow = config.trails.nightVisibility * (0.45 + 0.55 * moon);

        updateShellRadius(mc);
        aimAt(mc, camera, partialTick);
        skyColours(level, camera, partialTick);

        // Under water or in lava the sky is gone behind fog: fade the trails out with it.
        float submerged = camera.isInsideOfMaterial(Material.water) || camera.isInsideOfMaterial(Material.lava)
            ? 0.0f
            : 1.0f;
        if (camera instanceof EntityLivingBase && ((EntityLivingBase) camera).isPotionActive(Potion.blindness)) {
            submerged = 0.0f;
        }
        long now = System.nanoTime();
        float dt = lastClearNanos == 0L ? 0.0f : Math.min((now - lastClearNanos) * 1.0e-9f, 0.5f);
        lastClearNanos = now;
        clearSky += (submerged - clearSky) * (1.0f - (float) Math.exp(-dt / 0.15f));
        if (clearSky < 0.01f) {
            clearMeshes();
            return;
        }

        double[] key = {eyeX, eyeY, eyeZ, trailSettings.viewX, trailSettings.viewY, trailSettings.viewZ,
            trailSettings.pixelAngle, sunX, sunY, sunZ, clearSky, skyColours[0], skyColours[1], skyColours[2], rain,
            convection, config.graphics.detailedTrails ? 1.0 : 0.0};
        if (lastBuildKey != null && Arrays.equals(key, lastBuildKey)
            && timeS >= lastBuildTimeS
            && timeS - lastBuildTimeS < REUSE_WINDOW_S) {
            return;
        }
        lastBuildKey = key;
        lastBuildTimeS = timeS;

        clearMeshes();
        boolean detailed = config.graphics.detailedTrails;
        meshBuilder.spine = detailed;

        double trailWindow = longestTrailSeconds();
        List<Flight> flights = traffic.collect(
            seed,
            timeS,
            eyeX,
            eyeZ,
            config.graphics.visibleRangeM,
            config.traffic.densityPerHour,
            config.traffic.maxAircraft,
            trailWindow);
        flights.addAll(manual.collect(timeS, trailWindow));

        trails.clear();
        for (int i = 0; i < flights.size(); i++) {
            trails.add(sampler.sample(flights.get(i), timeS, air, trailSettings));
        }
        double range = config.graphics.visibleRangeM;
        skyCover.limit(trails, config.trails.maxSkyCover * Math.PI * range * range);

        for (int i = 0; i < flights.size(); i++) {
            Flight flight = flights.get(i);
            Trail trail = trails.get(i);
            double effectiveElevation = Illumination.effectiveElevationDegrees(sunElevationDeg, flight.altitudeM);
            double sunlit = Illumination.sunlight(effectiveElevation);
            Illumination.tintFromSky(sunlit, skyColours[0], skyColours[1], skyColours[2], tint);
            double lightOnTrail = Math.max(sunlit, nightGlow) * clearSky;
            trailSettings.alphaCap = sunlit > 0.05 ? config.trails.dayAlphaCap : config.trails.nightAlphaCap;
            meshBuilder.build(
                trail,
                eyeX,
                eyeY,
                eyeZ,
                sunX * sunlit,
                sunY * sunlit,
                sunZ * sunlit,
                lightOnTrail,
                tint[0],
                tint[1],
                tint[2],
                projection,
                trailSettings,
                trailMesh);
            if (detailed) {
                puffBuilder.build(
                    trail,
                    eyeX,
                    eyeY,
                    eyeZ,
                    sunX * sunlit,
                    sunY * sunlit,
                    sunZ * sunlit,
                    lightOnTrail,
                    tint[0],
                    tint[1],
                    tint[2],
                    projection,
                    trailSettings,
                    puffMesh);
            }
            if (clearSky > 0.5f) {
                aircraftBuilder.build(
                    flight,
                    timeS,
                    eyeX,
                    eyeY,
                    eyeZ,
                    sunX,
                    sunY,
                    sunZ,
                    lightsDaylight,
                    projection,
                    aircraftMesh);
            }
        }
        lastFlightCount = flights.size();
        lastQuadCount = trailMesh.quadCount() + aircraftMesh.quadCount() + puffMesh.quadCount();
    }

    private void clearMeshes() {
        trailMesh.clear();
        aircraftMesh.clear();
        puffMesh.clear();
        lastBuildKey = null;
    }

    private void aimAt(Minecraft mc, Entity camera, float partialTick) {
        Vec3 forward = camera instanceof EntityLivingBase
            ? ((EntityLivingBase) camera).getLook(partialTick)
            : camera.getLookVec();
        double fov = Math.toRadians(mc.gameSettings.fovSetting);
        double height = Math.max(mc.displayHeight, 1);
        double aspect = mc.displayWidth / height;
        double halfDiagonal = Math.atan(Math.tan(fov * 0.5 * 1.15) * Math.sqrt(1.0 + aspect * aspect));
        trailSettings.viewX = forward.xCoord;
        trailSettings.viewY = forward.yCoord;
        trailSettings.viewZ = forward.zCoord;
        trailSettings.viewCos = Math.cos(Math.min(halfDiagonal, Math.PI));
        trailSettings.pixelAngle = fov / height;
    }

    /** The shell sits well inside the clip planes; angular sizes do not depend on its radius. */
    private void updateShellRadius(Minecraft mc) {
        double radius = config.graphics.shellRadius;
        int chunks = mc.gameSettings.renderDistanceChunks;
        radius = Math.min(radius, Math.max(16.0, chunks * 16.0 * 0.5));
        if (radius != shellRadiusInUse) {
            projection = new SkyProjection(radius);
            shellRadiusInUse = radius;
        }
    }

    private void skyColours(WorldClient level, Entity camera, float partialTick) {
        WorldProvider provider = level.provider;
        float[] glow = provider.calcSunriseSunsetColors(level.getCelestialAngle(partialTick), partialTick);
        skyColours[0] = glow == null ? 0 : argb(glow[3], glow[0], glow[1], glow[2]);
        Vec3 sky = level.getSkyColor(camera, partialTick);
        skyColours[1] = argb(1.0f, (float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord);
        skyColours[2] = argb(
            1.0f,
            0.5f + 0.5f * (float) sky.xCoord,
            0.5f + 0.5f * (float) sky.yCoord,
            0.5f + 0.5f * (float) sky.zCoord);
    }

    private static int argb(float a, float r, float g, float b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    /**
     * How hard the ground below is pushing air upwards, read from the biomes around and eased towards, so that a
     * rainforest trails more often than a desert (see the newer backends' SurfaceConvection).
     */
    private void updateConvection(WorldClient level, double x, double y, double z, long gameTime) {
        if (gameTime - lastConvectionTick < 40L) {
            return;
        }
        lastConvectionTick = gameTime;
        double total = 0.0;
        int samples = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int bx = (int) Math.floor(x + dx * 96);
                int bz = (int) Math.floor(z + dz * 96);
                BiomeGenBase biome = level.getBiomeGenForCoords(bx, bz);
                total += convectionOf(biome, bx, (int) y, bz);
                samples++;
            }
        }
        convection += (total / samples - convection) * 0.08;
    }

    private static double convectionOf(BiomeGenBase biome, int x, int y, int z) {
        double temperature = biome.getFloatTemperature(x, y, z);
        if (biome.canSpawnLightningBolt() || biome.getEnableSnow()) {
            double warmth = Math.max(0.0, Math.min(1.0, (temperature - 0.4) / 0.7));
            return 0.62 + 0.28 * warmth;
        }
        double aridity = Math.max(0.0, Math.min(1.0, (temperature - 0.6) / 1.0));
        return 0.55 - 0.40 * aridity;
    }

    static double persistence(double overIce) {
        return smoothstep(0.98, 1.22, overIce);
    }

    private double longestTrailSeconds() {
        return Math.max(
            SMOKE_LIFETIME_S,
            Math.max(
                trailSettings.shortLifetimeSeconds,
                trailSettings.persistentLifetimeSeconds * trailSettings.persistenceMultiplier));
    }

    /** The air each piece of exhaust was emitted into, read from the humidity field. */
    private final class EmissionAir implements TrailSampler.Conditions {

        double rain;
        double convection;
        private double forAltitude = Double.NaN;
        private SchmidtAppleman.EngineProfile forEngine;
        private double criticalHumidity;
        private double iceRatio;

        @Override
        public void at(Flight flight, double x, double z, double timeS, double[] out) {
            double altitude = flight.altitudeM;
            if (altitude != forAltitude || flight.type.engine != forEngine) {
                double temperature = Isa.temperature(altitude);
                criticalHumidity = SchmidtAppleman
                    .criticalHumidity(temperature, Isa.pressure(altitude), flight.type.engine);
                iceRatio = SchmidtAppleman.persistenceRatio(temperature, 1.0);
                forAltitude = altitude;
                forEngine = flight.type.engine;
            }
            double relativeHumidity = humidity.relativeHumidity(x, z, altitude, timeS, rain, convection);
            out[0] = Double.isInfinite(criticalHumidity) ? 0.0
                : smoothstep(criticalHumidity - 0.03, criticalHumidity + 0.03, relativeHumidity);
            out[1] = persistence(relativeHumidity * iceRatio);
        }
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }

    /** The seed the sky follows from: the dimension, which every client on a server derives alike. */
    private static long seedFor(WorldClient level) {
        return ("dim" + level.provider.dimensionId).hashCode() * 0x9E3779B97F4A7C15L;
    }
}
