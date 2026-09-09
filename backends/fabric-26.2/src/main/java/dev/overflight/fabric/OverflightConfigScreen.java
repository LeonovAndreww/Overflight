package dev.overflight.fabric;

import dev.overflight.core.config.OverflightConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen, for people who would rather not edit a file.
 *
 * Only built when Cloth Config is installed -- nothing else in the mod touches
 * these classes, so their absence is simply the screen not being offered. The
 * config file remains the whole truth either way.
 *
 * Only the settings worth reaching for are here. The rest exist in the file and
 * are documented there; putting forty numbers on a screen helps nobody.
 */
public final class OverflightConfigScreen {

    private OverflightConfigScreen() {}

    public static Screen create(Screen parent) {
        OverflightConfig config = ConfigIo.load();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Overflight"));
        ConfigEntryBuilder entry = builder.entryBuilder();

        sky(builder, entry, config);
        trails(builder, entry, config);
        performance(builder, entry, config);

        builder.setSavingRunnable(() -> {
            config.sanitise();
            ConfigIo.save(config);
            SkyRenderer renderer = OverflightClient.renderer();
            if (renderer != null) {
                renderer.applyConfig(config);
            }
        });
        return builder.build();
    }

    private static void sky(ConfigBuilder builder, ConfigEntryBuilder entry,
                            OverflightConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Sky"));

        category.addEntry(entry.startBooleanToggle(
                        Component.literal("Enabled"), config.enabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Turns the whole sky off."))
                .setSaveConsumer(value -> config.enabled = value)
                .build());

        category.addEntry(entry.startStringDropdownMenu(
                        Component.literal("Preset"), config.preset)
                .setSelections(java.util.Arrays.asList(
                        "realistic", "busy", "quiet", "chemtrail", "coldwar",
                        "abandoned", "custom"))
                .setDefaultValue("realistic")
                .setTooltip(
                        Component.literal("realistic - physics as measured"),
                        Component.literal("busy - European traffic density"),
                        Component.literal("quiet - remote ocean levels"),
                        Component.literal("chemtrail - the look rather than the physics"),
                        Component.literal("coldwar - military-heavy, low-bypass engines"),
                        Component.literal("abandoned - no traffic at all"),
                        Component.literal("custom - keep everything below as set"))
                .setSaveConsumer(value -> config.preset = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        Component.literal("Traffic density"), config.traffic.densityPerHour)
                .setDefaultValue(45.0)
                .setMin(0.0).setMax(5000.0)
                .setTooltip(Component.literal(
                        "Flights entering a 1000 x 1000 km area each hour."),
                        Component.literal("Busy European airspace is near 120."))
                .setSaveConsumer(value -> config.traffic.densityPerHour = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        Component.literal("Sky holding trails up"),
                        config.atmosphere.supersaturatedFraction)
                .setDefaultValue(0.22)
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal(
                        "How much of the sky is damp enough for a trail to survive"),
                        Component.literal("in it. The rest gives the short stub and"),
                        Component.literal("nothing more."))
                .setSaveConsumer(value -> config.atmosphere.supersaturatedFraction = value)
                .build());

        category.addEntry(entry.startBooleanToggle(
                        Component.literal("Navigation lights"), config.traffic.navigationLights)
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.traffic.navigationLights = value)
                .build());
    }

    private static void trails(ConfigBuilder builder, ConfigEntryBuilder entry,
                               OverflightConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Trails"));

        category.addEntry(entry.startDoubleField(
                        Component.literal("How long they last"),
                        config.trails.persistenceMultiplier)
                .setDefaultValue(1.0)
                .setMin(0.0).setMax(100.0)
                .setTooltip(Component.literal(
                        "1 is realistic: about forty minutes. A Minecraft day is"),
                        Component.literal("twenty, so lower this if the sky fills up."))
                .setSaveConsumer(value -> config.trails.persistenceMultiplier = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        Component.literal("Visible at night"), config.trails.nightVisibility)
                .setDefaultValue(0.45)
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal(
                        "Against 1 for full daylight, scaled by the moon."),
                        Component.literal("0 hides them after dark."))
                .setSaveConsumer(value -> config.trails.nightVisibility = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        Component.literal("Spread unevenness"), config.trails.spreadVariation)
                .setDefaultValue(0.85)
                .setMin(0.0).setMax(3.0)
                .setTooltip(Component.literal(
                        "0 gives a ribbon of constant width, which is not what"),
                        Component.literal("the real ones look like."))
                .setSaveConsumer(value -> config.trails.spreadVariation = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        Component.literal("Meander"), config.trails.shearVariationMs)
                .setDefaultValue(2.6)
                .setMin(0.0).setMax(30.0)
                .setTooltip(Component.literal("How much an ageing trail wanders. 0 keeps"),
                        Component.literal("it ruler-straight."))
                .setSaveConsumer(value -> config.trails.shearVariationMs = value)
                .build());

        category.addEntry(entry.startIntSlider(
                        Component.literal("Strands when frayed"), config.trails.fibres, 1, 8)
                .setDefaultValue(4)
                .setTooltip(Component.literal(
                        "An old trail frays into this many. 1 is a flat ribbon:"),
                        Component.literal("cheaper, and much less convincing."))
                .setSaveConsumer(value -> config.trails.fibres = value)
                .build());
    }

    private static void performance(ConfigBuilder builder, ConfigEntryBuilder entry,
                                    OverflightConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Performance"));

        category.addEntry(entry.startIntSlider(
                        Component.literal("Trail detail"), config.graphics.trailDetail, 8, 512)
                .setDefaultValue(192)
                .setTooltip(Component.literal("Samples along one trail. The setting that"),
                        Component.literal("actually costs frames."))
                .setSaveConsumer(value -> config.graphics.trailDetail = value)
                .build());

        category.addEntry(entry.startIntSlider(
                        Component.literal("Aircraft at once"), config.traffic.maxAircraft, 0, 128)
                .setDefaultValue(32)
                .setSaveConsumer(value -> config.traffic.maxAircraft = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        Component.literal("Sky shell radius"), config.graphics.shellRadius)
                .setDefaultValue(512.0)
                .setMin(16.0).setMax(100000.0)
                .setTooltip(Component.literal(
                        "How far out the sky is drawn. Sizes are right whatever"),
                        Component.literal("this is, but it decides what the sky ends up in"),
                        Component.literal("front of and behind. Raise it if trails appear"),
                        Component.literal("over far terrain drawn by Voxy or Distant"),
                        Component.literal("Horizons."))
                .setSaveConsumer(value -> config.graphics.shellRadius = value)
                .build());

        category.addEntry(entry.startBooleanToggle(
                        Component.literal("Keep inside vanilla fog"),
                        config.graphics.keepInsideVanillaFog)
                .setDefaultValue(true)
                .setTooltip(Component.literal(
                        "Without a shader pack, pull the shell in so Minecraft's"),
                        Component.literal("own fog cannot reach the trails."))
                .setSaveConsumer(value -> config.graphics.keepInsideVanillaFog = value)
                .build());
    }
}
