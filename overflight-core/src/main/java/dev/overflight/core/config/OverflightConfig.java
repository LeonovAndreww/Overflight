package dev.overflight.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the mod lets you change.
 *
 * Plain public fields with sane defaults, so a backend can hand this object to
 * whatever JSON library its Minecraft version already ships and neither the core
 * nor the config file needs a dependency of its own.
 *
 * A preset overwrites the sections it cares about when the config loads, which
 * means editing a field by hand only sticks if the preset is left at "custom" --
 * that is deliberate, and said as much in the file.
 */
public final class OverflightConfig {

    public boolean enabled = true;

    /**
     * realistic, busy, quiet, chemtrail, coldwar, abandoned, or custom to leave
     * every value below exactly as written.
     */
    public String preset = "realistic";

    public Traffic traffic = new Traffic();
    public Atmosphere atmosphere = new Atmosphere();
    public Trails trails = new Trails();
    public Graphics graphics = new Graphics();

    public static final class Traffic {
        /** Flights entering a 1000 by 1000 km area each hour. Busy European airspace is near 120. */
        public double densityPerHour = 45.0;
        public int maxAircraft = 32;
        public boolean navigationLights = true;
        /**
         * Weights per aircraft category, overriding the built-in mix. Anything
         * left out keeps its default; a weight of zero removes the category.
         */
        public Map<String, Double> mix = new LinkedHashMap<String, Double>();
    }

    public static final class Atmosphere {
        /** Fraction of the sky that is supersaturated over ice, where trails persist. */
        public double supersaturatedFraction = 0.22;
        /** Size of one damp or dry region, in metres. */
        public double patchSizeM = 180000.0;
        /** How fast those regions drift across the map. */
        public double driftSpeedMs = 22.0;
        /** How long a region takes to change character. */
        public double evolutionSeconds = 2400.0;
        /** How strongly rain and thunder raise humidity aloft. */
        public double weatherInfluence = 0.5;
    }

    public static final class Trails {
        /** Multiplies how long a persistent trail survives. */
        public double persistenceMultiplier = 1.0;
        /** Metres of half-width gained per second. */
        public double spreadRateMPerSec = 0.9;
        public double maxHalfWidthM = 1750.0;
        public double opacity = 0.85;
        public double windSpeedMs = 8.0;
        public double windDirectionDeg = 270.0;
        /**
         * How much the air varies from place to place, in metres per second of
         * sideways drift. This is what bends an old trail into a meander rather
         * than leaving it ruler-straight. 0 for straight lines.
         */
        public double shearVariationMs = 2.6;
        /**
         * How unevenly the trail spreads along its length. 0 gives a ribbon of
         * uniform width; 1 lets one stretch grow roughly three times faster than
         * another, which is what makes an old trail lumpy.
         */
        public double spreadVariation = 0.85;
        /** How far the vortex pair drags the trail down before it levels off, in metres. */
        public double vortexSinkM = 170.0;
        /**
         * How visible trails stay after dark, against 1 for full daylight. A
         * Minecraft night is nowhere near black and the moon lights the sky, so
         * trails do not simply go out. Scaled by the phase of the moon. 0 hides
         * them at night entirely.
         */
        public double nightVisibility = 0.45;
        /** The bulging and breaking of an ageing trail. Costs a little; looks like a lot. */
        public boolean crowInstability = true;
    }

    public static final class Graphics {
        /** Samples along one trail. The knob that actually costs frames. */
        public int trailDetail = 192;
        /** How far out aircraft and trails are considered, in metres. */
        public double visibleRangeM = 150000.0;
        /**
         * Radius of the shell everything is drawn on. Angular sizes come out
         * right whatever this is, but it is the depth the rest of the pipeline
         * sees, so it decides what the sky ends up in front of and behind.
         * Larger puts trails behind a shader pack's clouds; rarely worth
         * touching otherwise.
         */
        public double shellRadius = 512.0;
    }

    /**
     * Applies the named preset. Unknown names, and "custom", leave everything
     * alone, so a typo cannot silently wipe out a hand-tuned file.
     */
    public OverflightConfig applyPreset() {
        if (preset == null) {
            return this;
        }
        String name = preset.trim().toLowerCase();

        if (name.equals("busy")) {
            traffic.densityPerHour = 120.0;
            atmosphere.supersaturatedFraction = 0.30;
        } else if (name.equals("quiet")) {
            traffic.densityPerHour = 8.0;
            atmosphere.supersaturatedFraction = 0.18;
        } else if (name.equals("chemtrail")) {
            // The look rather than the physics: trails everywhere, all day, all
            // crossing each other.
            traffic.densityPerHour = 220.0;
            traffic.maxAircraft = 64;
            atmosphere.supersaturatedFraction = 0.95;
            trails.persistenceMultiplier = 2.5;
            trails.spreadRateMPerSec = 1.4;
            trails.opacity = 0.95;
            // A sky this full is the expensive case, and a trail spread this wide
            // is diffuse enough that the samples are not buying anything. Measured
            // at 12500 quads a frame before this, 8700 after.
            graphics.trailDetail = 120;
        } else if (name.equals("coldwar")) {
            traffic.densityPerHour = 30.0;
            traffic.mix.put("airliner_narrowbody", 12.0);
            traffic.mix.put("airliner_widebody", 3.0);
            traffic.mix.put("regional_jet", 3.0);
            traffic.mix.put("bizjet", 1.0);
            traffic.mix.put("military_transport", 18.0);
            traffic.mix.put("military_fighter", 16.0);
            traffic.mix.put("military_tanker", 6.0);
            traffic.mix.put("high_altitude_recon", 2.0);
        } else if (name.equals("abandoned")) {
            traffic.densityPerHour = 0.0;
        }
        return this;
    }

    /** Clamps anything a hand-edited file could have made nonsensical. */
    public OverflightConfig sanitise() {
        traffic.densityPerHour = clamp(traffic.densityPerHour, 0.0, 5000.0);
        traffic.maxAircraft = (int) clamp(traffic.maxAircraft, 0, 512);
        atmosphere.supersaturatedFraction = clamp(atmosphere.supersaturatedFraction, 0.0, 1.0);
        atmosphere.patchSizeM = clamp(atmosphere.patchSizeM, 1000.0, 5.0e6);
        atmosphere.evolutionSeconds = clamp(atmosphere.evolutionSeconds, 1.0, 1.0e6);
        trails.persistenceMultiplier = clamp(trails.persistenceMultiplier, 0.0, 100.0);
        trails.spreadRateMPerSec = clamp(trails.spreadRateMPerSec, 0.0, 100.0);
        trails.maxHalfWidthM = clamp(trails.maxHalfWidthM, 1.0, 50000.0);
        trails.opacity = clamp(trails.opacity, 0.0, 1.0);
        graphics.trailDetail = (int) clamp(graphics.trailDetail, 8, 1024);
        graphics.visibleRangeM = clamp(graphics.visibleRangeM, 1000.0, 1.0e6);
        graphics.shellRadius = clamp(graphics.shellRadius, 16.0, 4096.0);
        return this;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
