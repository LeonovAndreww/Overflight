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
                .setTitle(new net.minecraft.network.chat.TextComponent("Overflight"));
        ConfigEntryBuilder entry = builder.entryBuilder();

        sky(builder, entry, config);
        trails(builder, entry, config);
        performance(builder, entry, config);

        builder.setSavingRunnable(() -> {
            // Same order as loading, so the screen and a restart agree on the result.
            config.applyPreset().sanitise();
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
        ConfigCategory category = builder.getOrCreateCategory(new net.minecraft.network.chat.TextComponent("Sky"));

        category.addEntry(entry.startBooleanToggle(
                        new net.minecraft.network.chat.TextComponent("Enabled"), config.enabled)
                .setDefaultValue(true)
                .setTooltip(new net.minecraft.network.chat.TextComponent("Turns the whole sky off."))
                .setSaveConsumer(value -> config.enabled = value)
                .build());

        category.addEntry(entry.startStringDropdownMenu(
                        new net.minecraft.network.chat.TextComponent("Preset"), config.preset)
                .setSelections(java.util.Arrays.asList(
                        "realistic", "fancy", "chemtrail", "custom"))
                .setDefaultValue("realistic")
                .setTooltip(
                        new net.minecraft.network.chat.TextComponent("A Minecraft day is twenty minutes, so this"),
                        new net.minecraft.network.chat.TextComponent("sky runs 72x faster than the real one. That is"),
                        new net.minecraft.network.chat.TextComponent("the difference between the first two:"),
                        new net.minecraft.network.chat.TextComponent(""),
                        new net.minecraft.network.chat.TextComponent("realistic - the sky as measured. Trails last"),
                        new net.minecraft.network.chat.TextComponent("  40 minutes and build up slowly; one flight in"),
                        new net.minecraft.network.chat.TextComponent("  five leaves one. Aircraft are drawn true to"),
                        new net.minecraft.network.chat.TextComponent("  size, so past 60 km you see the trail and not"),
                        new net.minecraft.network.chat.TextComponent("  the aircraft."),
                        new net.minecraft.network.chat.TextComponent("fancy - the same physics at a pace you can"),
                        new net.minecraft.network.chat.TextComponent("  watch. A trail forms, spreads, frays and goes"),
                        new net.minecraft.network.chat.TextComponent("  in 7 minutes. Half the flights leave one, the"),
                        new net.minecraft.network.chat.TextComponent("  sky holds trails of several ages at once, and"),
                        new net.minecraft.network.chat.TextComponent("  aircraft are drawn larger than life so there"),
                        new net.minecraft.network.chat.TextComponent("  is something to follow."),
                        new net.minecraft.network.chat.TextComponent("chemtrail - the look rather than the physics"),
                        new net.minecraft.network.chat.TextComponent("custom - keep everything below as set"),
                        new net.minecraft.network.chat.TextComponent(""),
                        new net.minecraft.network.chat.TextComponent("Who is flying is a separate setting."))
                .setSaveConsumer(value -> config.preset = value)
                .build());

        category.addEntry(entry.startStringDropdownMenu(
                        new net.minecraft.network.chat.TextComponent("Traffic"), config.traffic.preset)
                .setSelections(java.util.Arrays.asList(
                        "modern", "busy", "quiet", "coldwar", "abandoned", "custom"))
                .setDefaultValue("modern")
                .setTooltip(
                        new net.minecraft.network.chat.TextComponent("Who is flying, independent of how the sky looks."),
                        new net.minecraft.network.chat.TextComponent(""),
                        new net.minecraft.network.chat.TextComponent("modern - today's mix, world average density"),
                        new net.minecraft.network.chat.TextComponent("busy - European traffic density"),
                        new net.minecraft.network.chat.TextComponent("quiet - remote ocean levels"),
                        new net.minecraft.network.chat.TextComponent("coldwar - military-heavy, low-bypass engines"),
                        new net.minecraft.network.chat.TextComponent("abandoned - nothing flying at all"),
                        new net.minecraft.network.chat.TextComponent("custom - keep the density and mix as set"))
                .setSaveConsumer(value -> config.traffic.preset = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("Traffic density"), config.traffic.densityPerHour)
                .setDefaultValue(45.0)
                .setMin(0.0).setMax(5000.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "Flights entering a 1000 x 1000 km area each hour."),
                        new net.minecraft.network.chat.TextComponent("Busy European airspace is near 120."))
                .setSaveConsumer(value -> config.traffic.densityPerHour = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("Sky holding trails up"),
                        config.atmosphere.supersaturatedFraction)
                .setDefaultValue(0.22)
                .setMin(0.0).setMax(1.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "How much of the sky is damp enough for a trail to survive"),
                        new net.minecraft.network.chat.TextComponent("in it. The rest gives the short stub and"),
                        new net.minecraft.network.chat.TextComponent("nothing more."))
                .setSaveConsumer(value -> config.atmosphere.supersaturatedFraction = value)
                .build());

        category.addEntry(entry.startBooleanToggle(
                        new net.minecraft.network.chat.TextComponent("Navigation lights"), config.traffic.navigationLights)
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.traffic.navigationLights = value)
                .build());
    }

    private static void trails(ConfigBuilder builder, ConfigEntryBuilder entry,
                               OverflightConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(new net.minecraft.network.chat.TextComponent("Trails"));

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("How long they last"),
                        config.trails.persistenceMultiplier)
                .setDefaultValue(1.0)
                .setMin(0.0).setMax(100.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "1 is realistic: about forty minutes, which is two"),
                        new net.minecraft.network.chat.TextComponent("Minecraft days. 0.18 is what fancy uses, about"),
                        new net.minecraft.network.chat.TextComponent("seven minutes, which is long enough to watch one"),
                        new net.minecraft.network.chat.TextComponent("form, spread and disperse."))
                .setSaveConsumer(value -> config.trails.persistenceMultiplier = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("Visible at night"), config.trails.nightVisibility)
                .setDefaultValue(0.45)
                .setMin(0.0).setMax(1.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "Against 1 for full daylight, scaled by the moon."),
                        new net.minecraft.network.chat.TextComponent("0 hides them after dark."))
                .setSaveConsumer(value -> config.trails.nightVisibility = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("Spread unevenness"), config.trails.spreadVariation)
                .setDefaultValue(0.85)
                .setMin(0.0).setMax(3.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "0 gives a ribbon of constant width, which is not what"),
                        new net.minecraft.network.chat.TextComponent("the real ones look like."))
                .setSaveConsumer(value -> config.trails.spreadVariation = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("Meander"), config.trails.shearVariationMs)
                .setDefaultValue(2.6)
                .setMin(0.0).setMax(30.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent("How much an ageing trail wanders. 0 keeps"),
                        new net.minecraft.network.chat.TextComponent("it ruler-straight."))
                .setSaveConsumer(value -> config.trails.shearVariationMs = value)
                .build());

        category.addEntry(entry.startIntSlider(
                        new net.minecraft.network.chat.TextComponent("Strands when frayed"), config.trails.fibres, 1, 8)
                .setDefaultValue(4)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "An old trail frays into this many. 1 is a flat ribbon:"),
                        new net.minecraft.network.chat.TextComponent("cheaper, and much less convincing."))
                .setSaveConsumer(value -> config.trails.fibres = value)
                .build());
    }

    private static void performance(ConfigBuilder builder, ConfigEntryBuilder entry,
                                    OverflightConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(new net.minecraft.network.chat.TextComponent("Performance"));

        category.addEntry(entry.startIntSlider(
                        new net.minecraft.network.chat.TextComponent("Trail detail"), config.graphics.trailDetail, 8, 512)
                .setDefaultValue(192)
                .setTooltip(new net.minecraft.network.chat.TextComponent("Samples along one trail. The setting that"),
                        new net.minecraft.network.chat.TextComponent("actually costs frames."))
                .setSaveConsumer(value -> config.graphics.trailDetail = value)
                .build());

        category.addEntry(entry.startBooleanToggle(
                        new net.minecraft.network.chat.TextComponent("Detailed trails"), config.graphics.detailedTrails)
                .setDefaultValue(true)
                .setTooltip(new net.minecraft.network.chat.TextComponent("Old trails spread into cloud on one side, hang"),
                        new net.minecraft.network.chat.TextComponent("fall streaks and break into pieces as they go."),
                        new net.minecraft.network.chat.TextComponent("Off draws every trail as a plain ribbon, which"),
                        new net.minecraft.network.chat.TextComponent("costs far less on a busy sky."))
                .setSaveConsumer(value -> config.graphics.detailedTrails = value)
                .build());

        category.addEntry(entry.startBooleanToggle(
                        new net.minecraft.network.chat.TextComponent("Draw trails with the sky"), config.graphics.drawInSky)
                .setDefaultValue(true)
                .setTooltip(new net.minecraft.network.chat.TextComponent("Trails drawn with the sun and moon, so terrain,"),
                        new net.minecraft.network.chat.TextComponent("clouds and a shader pack's clouds cover them."),
                        new net.minecraft.network.chat.TextComponent("Off draws them with the world, as before."))
                .setSaveConsumer(value -> config.graphics.drawInSky = value)
                .build());

        category.addEntry(entry.startIntSlider(
                        new net.minecraft.network.chat.TextComponent("Aircraft at once"), config.traffic.maxAircraft, 0, 128)
                .setDefaultValue(32)
                .setSaveConsumer(value -> config.traffic.maxAircraft = value)
                .build());

        category.addEntry(entry.startDoubleField(
                        new net.minecraft.network.chat.TextComponent("Sky shell radius"), config.graphics.shellRadius)
                .setDefaultValue(512.0)
                .setMin(16.0).setMax(100000.0)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "How far out the sky is drawn. Sizes are right whatever"),
                        new net.minecraft.network.chat.TextComponent("this is, but it decides what the sky ends up in"),
                        new net.minecraft.network.chat.TextComponent("front of and behind. Raise it if trails appear"),
                        new net.minecraft.network.chat.TextComponent("over far terrain drawn by Voxy or Distant"),
                        new net.minecraft.network.chat.TextComponent("Horizons."))
                .setSaveConsumer(value -> config.graphics.shellRadius = value)
                .build());

        category.addEntry(entry.startBooleanToggle(
                        new net.minecraft.network.chat.TextComponent("Keep inside vanilla fog"),
                        config.graphics.keepInsideVanillaFog)
                .setDefaultValue(false)
                .setTooltip(new net.minecraft.network.chat.TextComponent(
                        "Without a shader pack, pull the shell in so Minecraft's"),
                        new net.minecraft.network.chat.TextComponent("own fog cannot reach the trails."))
                .setSaveConsumer(value -> config.graphics.keepInsideVanillaFog = value)
                .build());
    }
}
