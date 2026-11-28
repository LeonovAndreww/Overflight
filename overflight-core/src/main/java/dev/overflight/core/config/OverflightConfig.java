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
     * How the sky looks and how fast it moves: realistic, fancy, chemtrail, or
     * custom to leave the trail and graphics settings exactly as written.
     *
     * Separate from traffic.preset, which decides who is flying. These are
     * independent questions and used to share one list, which made no sense:
     * "abandoned" is a statement about traffic and says nothing about how a
     * trail ages, while "fancy" says nothing about who is up there.
     *
     * realistic is the sky as measured. fancy is the same physics wound to a
     * pace you can sit and watch, which realism cannot give: Minecraft's clock
     * runs seventy-two times faster than ours, so a trail that lasts a real
     * forty minutes lasts two whole Minecraft days.
     */
    public String preset = "realistic";

    public Traffic traffic = new Traffic();
    public Atmosphere atmosphere = new Atmosphere();
    public Trails trails = new Trails();
    public Graphics graphics = new Graphics();

    public static final class Traffic {
        /**
         * Who is flying: modern, busy, quiet, coldwar, abandoned, or custom to
         * leave the mix and density exactly as written.
         */
        public String preset = "modern";

        /** Flights entering a 1000 by 1000 km area each hour. Busy European airspace is near 120. */
        public double densityPerHour = 250.0;
        public int maxAircraft = 64;
        public boolean navigationLights = true;
        /**
         * Range at which navigation lights are still at full strength, in
         * metres. Brightness falls with the square of distance beyond it, so a
         * strobe that is unmistakable overhead is nothing a hundred kilometres
         * out, which is what life does. Raise it to keep distant aircraft
         * marked; 150000 makes every one in range a visible point.
         */
        public double lightFullRangeM = 14000.0;
        /**
         * Weights per aircraft category, overriding the built-in mix. Anything
         * left out keeps its default; a weight of zero removes the category.
         */
        public Map<String, Double> mix = new LinkedHashMap<String, Double>();
    }

    public static final class Atmosphere {
        /**
         * Fraction of the sky that is supersaturated over ice, where trails
         * persist rather than sublimating in seconds.
         *
         * This is the setting for "more trails". It maps very nearly one to one
         * onto the share of flights that leave a lasting trail -- measured over
         * four thousand samples, 0.30 gives 30%, 0.60 gives 60%, 0.90 gives 90%
         * and 1.0 leaves every flight trailing. Traffic density does not affect
         * it: that decides how many aircraft there are, not whether the air will
         * hold what they leave behind.
         */
        public double supersaturatedFraction = 0.22;
        /** Size of one damp or dry region, in metres. */
        public double patchSizeM = 180000.0;
        /** How fast those regions drift across the map. */
        public double driftSpeedMs = 22.0;
        /** How long a region takes to change character. */
        public double evolutionSeconds = 2400.0;
        /** How strongly rain and thunder raise humidity aloft. */
        public double weatherInfluence = 0.5;
        /**
         * How much the ground below shifts humidity at cruise altitude. The
         * surface reaches up there through convection and subsidence, so a
         * rainforest trails far more readily than a desert. 0 ignores the
         * ground entirely.
         */
        public double biomeInfluence = 0.5;
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
        /**
         * How many strands an ageing trail frays into. 1 draws a flat ribbon,
         * which is cheaper and reads as a stick rather than as cirrus.
         */
        public int fibres = 4;

        /** How far the vortex pair drags the trail down before it levels off, in metres. */
        public double vortexSinkM = 170.0;
        /**
         * How visible trails stay after dark, against 1 for full daylight. A
         * Minecraft night is nowhere near black and the moon lights the sky, so
         * trails do not simply go out. Scaled by the phase of the moon. 0 hides
         * them at night entirely.
         */
        public double nightVisibility = 0.45;
        /**
         * The most opaque a trail may be after dark. A dense trail at its full
         * daytime strength reads as a lit strip against a night sky; this takes
         * the glare off it without touching the spread-out ones, which look
         * right already. 1 disables the ceiling.
         */
        public double nightAlphaCap = 0.10;
        /**
         * The most opaque a trail may be in daylight.
         *
         * A contrail is an optically thin cloud: measured optical depths run
         * from about 0.1 to 0.5, and a dense young one reaches roughly 1, which
         * still lets a third of the sky through. Drawn fully opaque it reads as
         * a painted stripe rather than as ice. 1 disables the ceiling.
         */
        public double dayAlphaCap = 0.45;
        /**
         * The most of the sky trails may cover together, as a share of the
         * area within sight, counting a faint trail for less than a dense
         * one. Past it the oldest trails fade first, so a busy damp sky keeps
         * its young and middle-aged trails instead of being paved with old
         * ones. 1 lifts the ceiling.
         */
        public double maxSkyCover = 0.04;
        /** The bulging and breaking of an ageing trail. Costs a little; looks like a lot. */
        public boolean crowInstability = true;
    }

    public static final class Graphics {
        /** Samples along one trail. The knob that actually costs frames. */
        public int trailDetail = 192;
        /**
         * Draw ageing trails as they really go: one edge staying a line while
         * the rest spreads to cloud, fall streaks hanging off it, breaking into
         * pieces as it dissolves. Off draws every trail as a plain ribbon,
         * which costs a fraction as much.
         */
        public boolean detailedTrails = true;
        /**
         * Draw trails with the sky, after the sun and moon, so that terrain,
         * clouds and a shader pack's clouds and atmosphere all go over them as
         * they go over the sun. Off draws them with the world's translucent
         * geometry, as before, where a shader pack's clouds cannot cover them.
         */
        public boolean drawInSky = true;
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
        /**
         * Smallest apparent half size an aircraft may be drawn at, in radians.
         *
         * At the true size an airliner is under half a pixel past sixty
         * kilometres and simply cannot be seen without a spyglass, which is
         * what life is like -- what you see up there is the trail, not the
         * aircraft. Raising this draws them larger than they are so they read
         * as moving specks: 2.6e-4 is about two and a half times life size at
         * three hundred kilometres.
         */
        public double aircraftMinAngularSize = 3.0e-5;
        /**
         * Without a shader pack, pull the shell in far enough to stay inside
         * Minecraft's own fog.
         *
         * Off, because the sky no longer goes through a pipeline that applies
         * fog: it has one of its own, which does not. The setting stays because
         * a shell pulled in close is still the quickest way to rule fog out if
         * trails ever come back washed towards the colour of the sky.
         */
        public boolean keepInsideVanillaFog = false;
    }

    /**
     * Applies the named preset. Unknown names, and "custom", leave everything
     * alone, so a typo cannot silently wipe out a hand-tuned file.
     */
    public OverflightConfig applyPreset() {
        restoreMissingSections();
        // Look after traffic: the legacy names below rewrite traffic.preset, and
        // the chemtrail look forces its own density, which the traffic preset
        // would otherwise overwrite.
        applyLookPreset(name(preset));
        applyTrafficPreset(name(traffic.preset));
        if (name(preset).equals("chemtrail")) {
            traffic.densityPerHour = 220.0;
            traffic.maxAircraft = 64;
        }
        return this;
    }

    private static String name(String value) {
        return value == null ? "custom" : value.trim().toLowerCase();
    }

    /** How the sky looks and how fast it ages. */
    private void applyLookPreset(String look) {
        // Kept working for anyone whose file still names a traffic preset here,
        // from when the two shared one list.
        if (look.equals("busy") || look.equals("quiet") || look.equals("coldwar")
                || look.equals("abandoned") || look.equals("modern")) {
            traffic.preset = look;
            look = "realistic";
        }

        if (look.equals("realistic")) {
            // Every number as measured: patches of damp air the size real ones
            // are, one flight in five leaving a lasting trail, aircraft at the
            // angular size they really subtend, and a persistent trail lasting
            // the forty minutes one really lasts.
            atmosphere.supersaturatedFraction = 0.22;
            atmosphere.patchSizeM = 180000.0;
            trails.persistenceMultiplier = 1.0;
            graphics.aircraftMinAngularSize = 3.0e-5;
            traffic.lightFullRangeM = 14000.0;
            trails.dayAlphaCap = 0.45;
            trails.maxSkyCover = 0.04;
            resetChemtrailFields();
        } else if (look.equals("fancy")) {
            // For watching the sky rather than measuring it.
            //
            // Minecraft's day is twenty minutes, so its sky runs seventy-two
            // times faster than the real one, and a trail that lasts a realistic
            // forty minutes lasts two Minecraft days -- nothing you can watch
            // change. Scaled to the world's own clock it would last thirty-three
            // seconds, over before you had looked up. So the lifetimes stay
            // real, and fancy differs in the air instead: damp more often, in
            // patches far smaller than the view, so there is nearly always damp
            // air somewhere in sight and one glance holds trails of several
            // ages, instead of a sky that is all trails or none for an hour.
            // With shorter lifetimes there were too few old trails to look at.
            trails.persistenceMultiplier = 1.0;
            atmosphere.supersaturatedFraction = 0.6;
            atmosphere.patchSizeM = 30000.0;
            // Drawn larger than life, and their lights carried further, so there
            // is something to follow as well as something to look at.
            graphics.aircraftMinAngularSize = 2.6e-4;
            traffic.lightFullRangeM = 60000.0;
            trails.maxSkyCover = 0.06;
            trails.dayAlphaCap = 0.45;
            resetChemtrailFields();
        } else if (look.equals("chemtrail")) {
            // The look rather than the physics: trails everywhere, all day, all
            // crossing each other. The one look that also forces traffic, since
            // a crosshatched sky needs aircraft to crosshatch it.
            atmosphere.supersaturatedFraction = 0.95;
            trails.persistenceMultiplier = 2.5;
            trails.spreadRateMPerSec = 1.4;
            trails.maxSkyCover = 0.3;
            trails.opacity = 0.95;
            graphics.aircraftMinAngularSize = 2.6e-4;
            // A sky this full is the expensive case, and a trail spread this
            // wide is diffuse enough that the samples buy nothing. Measured at
            // 12500 quads a frame before this, 8700 after.
            graphics.trailDetail = 120;
        }
    }

    /** Fields only the chemtrail look changes, put back so leaving it leaves no trace. */
    private void resetChemtrailFields() {
        trails.spreadRateMPerSec = 0.9;
        trails.opacity = 0.85;
        graphics.trailDetail = 192;
    }

    private static final Map<String, Double> COLDWAR_MIX = new LinkedHashMap<String, Double>();
    static {
        COLDWAR_MIX.put("airliner_narrowbody", 12.0);
        COLDWAR_MIX.put("airliner_widebody", 3.0);
        COLDWAR_MIX.put("regional_jet", 3.0);
        COLDWAR_MIX.put("bizjet", 1.0);
        COLDWAR_MIX.put("military_transport", 18.0);
        COLDWAR_MIX.put("military_fighter", 16.0);
        COLDWAR_MIX.put("military_tanker", 6.0);
        COLDWAR_MIX.put("high_altitude_recon", 2.0);
    }

    /** Who is flying. */
    private void applyTrafficPreset(String who) {
        if (!who.equals("coldwar") && !who.equals("custom")) {
            // Leaving coldwar: take back the entries it wrote, and only those
            // still exactly as it wrote them, so a hand-edited mix survives.
            for (Map.Entry<String, Double> e : COLDWAR_MIX.entrySet()) {
                Double have = traffic.mix.get(e.getKey());
                if (have != null && have.equals(e.getValue())) {
                    traffic.mix.remove(e.getKey());
                }
            }
        }
        if (who.equals("modern")) {
            traffic.densityPerHour = 250.0;
        } else if (who.equals("busy")) {
            traffic.densityPerHour = 700.0;
        } else if (who.equals("quiet")) {
            traffic.densityPerHour = 40.0;
        } else if (who.equals("coldwar")) {
            traffic.densityPerHour = 120.0;
            traffic.mix.putAll(COLDWAR_MIX);
        } else if (who.equals("abandoned")) {
            traffic.densityPerHour = 0.0;
        }
    }

    /** Clamps anything a hand-edited file could have made nonsensical. */
    public OverflightConfig sanitise() {
        restoreMissingSections();
        dropBadMixWeights();
        traffic.densityPerHour = clamp(traffic.densityPerHour, 0.0, 5000.0);
        traffic.maxAircraft = (int) clamp(traffic.maxAircraft, 0, 512);
        atmosphere.supersaturatedFraction = clamp(atmosphere.supersaturatedFraction, 0.0, 1.0);
        atmosphere.biomeInfluence = clamp(atmosphere.biomeInfluence, 0.0, 2.0);
        atmosphere.patchSizeM = clamp(atmosphere.patchSizeM, 1000.0, 5.0e6);
        atmosphere.evolutionSeconds = clamp(atmosphere.evolutionSeconds, 1.0, 1.0e6);
        trails.maxSkyCover = clamp(trails.maxSkyCover, 0.0, 1.0);
        trails.persistenceMultiplier = clamp(trails.persistenceMultiplier, 0.0, 100.0);
        trails.fibres = (int) clamp(trails.fibres, 1, 8);
        trails.spreadRateMPerSec = clamp(trails.spreadRateMPerSec, 0.0, 100.0);
        trails.maxHalfWidthM = clamp(trails.maxHalfWidthM, 1.0, 50000.0);
        trails.opacity = clamp(trails.opacity, 0.0, 1.0);
        trails.nightVisibility = clamp(trails.nightVisibility, 0.0, 1.0);
        trails.nightAlphaCap = clamp(trails.nightAlphaCap, 0.0, 1.0);
        trails.dayAlphaCap = clamp(trails.dayAlphaCap, 0.0, 1.0);
        trails.windSpeedMs = clamp(trails.windSpeedMs, 0.0, 200.0);
        trails.windDirectionDeg = Double.isNaN(trails.windDirectionDeg) || Double.isInfinite(trails.windDirectionDeg)
            ? 270.0
            : trails.windDirectionDeg;
        trails.shearVariationMs = clamp(trails.shearVariationMs, 0.0, 50.0);
        trails.spreadVariation = clamp(trails.spreadVariation, 0.0, 1.0);
        trails.vortexSinkM = clamp(trails.vortexSinkM, 0.0, 2000.0);
        traffic.lightFullRangeM = clamp(traffic.lightFullRangeM, 100.0, 1.0e6);
        graphics.aircraftMinAngularSize = clamp(graphics.aircraftMinAngularSize, 0.0, 0.01);
        graphics.trailDetail = (int) clamp(graphics.trailDetail, 8, 1024);
        graphics.visibleRangeM = clamp(graphics.visibleRangeM, 1000.0, 1.0e6);
        graphics.shellRadius = clamp(graphics.shellRadius, 16.0, 4096.0);
        return this;
    }

    /** A hand-edited file may say "traffic": null, which JSON libraries honour. */
    private void restoreMissingSections() {
        if (traffic == null) {
            traffic = new Traffic();
        }
        if (traffic.mix == null) {
            traffic.mix = new LinkedHashMap<String, Double>();
        }
        if (atmosphere == null) {
            atmosphere = new Atmosphere();
        }
        if (trails == null) {
            trails = new Trails();
        }
        if (graphics == null) {
            graphics = new Graphics();
        }
    }

    /** A NaN weight would poison the sum the catalog rolls against. */
    private void dropBadMixWeights() {
        java.util.Iterator<Map.Entry<String, Double>> it = traffic.mix.entrySet().iterator();
        while (it.hasNext()) {
            Double w = it.next().getValue();
            if (w == null || w.isNaN() || w.isInfinite()) {
                it.remove();
            }
        }
    }

    /** NaN (a hand-edited "NaN" in lenient JSON) would slip through both comparisons, so it takes the minimum. */
    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return v < min ? min : (v > max ? max : v);
    }
}
