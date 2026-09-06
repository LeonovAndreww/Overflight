package dev.overflight.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.overflight.core.atmo.HumidityField;
import dev.overflight.core.atmo.Isa;
import dev.overflight.core.atmo.SchmidtAppleman;
import dev.overflight.core.config.OverflightConfig;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.FlightRequests;
import dev.overflight.core.traffic.ManualTraffic;
import dev.overflight.core.trail.Trail;
import dev.overflight.core.trail.TrailSampler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Debug commands.
 *
 * An empty sky is the one bug this mod can produce that looks exactly like
 * correct behaviour, because most aircraft genuinely leave nothing behind them.
 * These commands exist to tell those two cases apart: they report what the
 * simulation believes is up there, why each aircraft is or is not trailing, and
 * how much of that survived into geometry.
 *
 * Client-side only, so they work on any server, including a vanilla one.
 */
public final class OverflightCommands {
    private static final TrailSampler SAMPLER = new TrailSampler();
    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
    private static final double TICKS_PER_SECOND = 20.0;
    private static final String[] PRESETS = {
        "realistic", "busy", "quiet", "chemtrail", "coldwar", "abandoned", "custom"
    };

    private final SkyRenderer renderer;

    public OverflightCommands(SkyRenderer renderer) {
        this.renderer = renderer;
    }

    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> build(dispatcher));
    }

    private void build(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SuggestionProvider<FabricClientCommandSource> typeNames = (context, builder) -> {
            List<AircraftType> types = CATALOG.types();
            for (int i = 0; i < types.size(); i++) {
                builder.suggest(types.get(i).id);
            }
            return builder.buildFuture();
        };

        dispatcher.register(ClientCommands.literal("overflight")
                .then(ClientCommands.literal("status").executes(this::status))
                .then(ClientCommands.literal("list").executes(this::list))
                .then(ClientCommands.literal("probe")
                        .executes(context -> probe(context, 350))
                        .then(ClientCommands.argument("fl", IntegerArgumentType.integer(50, 700))
                                .executes(context -> probe(context,
                                        IntegerArgumentType.getInteger(context, "fl")))))
                .then(ClientCommands.literal("spawn")
                        .then(ClientCommands.argument("type", StringArgumentType.word())
                                .suggests(typeNames)
                                .executes(context -> spawn(context,
                                        StringArgumentType.getString(context, "type"), -1,
                                        Flight.Condition.NORMAL))
                                .then(ClientCommands.argument("fl",
                                                IntegerArgumentType.integer(50, 700))
                                        .executes(context -> spawn(context,
                                                StringArgumentType.getString(context, "type"),
                                                IntegerArgumentType.getInteger(context, "fl"),
                                                Flight.Condition.NORMAL))
                                        .then(ClientCommands.literal("smoking")
                                                .executes(context -> spawn(context,
                                                        StringArgumentType.getString(context, "type"),
                                                        IntegerArgumentType.getInteger(context, "fl"),
                                                        Flight.Condition.SMOKING)))
                                        .then(ClientCommands.literal("burning")
                                                .executes(context -> spawn(context,
                                                        StringArgumentType.getString(context, "type"),
                                                        IntegerArgumentType.getInteger(context, "fl"),
                                                        Flight.Condition.BURNING))))))
                .then(ClientCommands.literal("convoy")
                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 24))
                                .then(ClientCommands.argument("type", StringArgumentType.word())
                                        .suggests(typeNames)
                                        .executes(context -> convoy(context,
                                                IntegerArgumentType.getInteger(context, "count"),
                                                StringArgumentType.getString(context, "type"),
                                                "vee"))
                                        .then(ClientCommands.argument("formation",
                                                        StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    b.suggest("line");
                                                    b.suggest("vee");
                                                    b.suggest("echelon");
                                                    return b.buildFuture();
                                                })
                                                .executes(context -> convoy(context,
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        StringArgumentType.getString(context, "type"),
                                                        StringArgumentType.getString(context, "formation")))))))
                .then(ClientCommands.literal("preset")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String name : PRESETS) {
                                        b.suggest(name);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(context -> preset(context,
                                        StringArgumentType.getString(context, "name")))))
                .then(ClientCommands.literal("reload").executes(this::reload))
                .then(ClientCommands.literal("clear").executes(this::clear))
                .then(ClientCommands.literal("density")
                        .then(ClientCommands.argument("value", DoubleArgumentType.doubleArg(0.0, 5000.0))
                                .executes(context -> density(context,
                                        DoubleArgumentType.getDouble(context, "value"))))));
    }

    private int status(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        ClientLevel level = source.getLevel();
        double timeS = timeOf(level);
        Vec3 eye = source.getPosition();

        head(source, "Overflight status");
        field(source, "dimension seed", Long.toString(SkyRenderer.seedForLevel(level)));
        field(source, "clock", String.format(Locale.ROOT, "%.1f s (game time %d)",
                timeS, level.getGameTime()));
        field(source, "density", String.format(Locale.ROOT,
                "%.1f flights/hour per 1000x1000 km", renderer.densityPerHour()));
        field(source, "search radius", String.format(Locale.ROOT, "%.0f km",
                renderer.visibleRadius() / 1000.0));
        field(source, "manual flights", Integer.toString(renderer.manualTraffic().size()));
        field(source, "shader pack drawing", ShaderPacks.inUse() ? "yes" : "no");
        field(source, "sky shell radius", String.format(Locale.ROOT, "%.0f blocks (limit %.0f)",
                renderer.shellRadiusInUse(), renderer.config().graphics.shellRadius));

        int flights = renderer.lastFlightCount();
        int trails = renderer.lastTrailCount();
        int quads = renderer.lastQuadCount();
        field(source, "last frame", flights + " aircraft, " + trails + " trailing, "
                + quads + " quads");

        if (flights == 0) {
            note(source, "No aircraft in range. Traffic is sparse by design; try /overflight spawn.");
        } else if (trails == 0) {
            note(source, "Aircraft are up but the air is too dry or too warm for any of them "
                    + "to trail. Check /overflight list.");
        } else if (quads == 0) {
            note(source, "Trails exist but produced no geometry. That is a bug in the mesh "
                    + "builder, not in the weather.");
        }
        return 1;
    }

    private int list(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        ClientLevel level = source.getLevel();
        double timeS = timeOf(level);
        Vec3 eye = source.getPosition();
        long seed = SkyRenderer.seedForLevel(level);
        float rain = level.getRainLevel(1.0f);

        List<Flight> flights = renderer.traffic().collect(seed, timeS, eye.x, eye.z,
                renderer.visibleRadius(), renderer.densityPerHour(), renderer.maxAircraft());
        flights.addAll(renderer.manualTraffic().collect(timeS));

        head(source, "Aircraft in range: " + flights.size());
        if (flights.isEmpty()) {
            note(source, "Nothing airborne within " + (int) (renderer.visibleRadius() / 1000.0)
                    + " km.");
            return 1;
        }

        HumidityField humidity = renderer.humidity();
        for (int i = 0; i < flights.size(); i++) {
            Flight flight = flights.get(i);
            double x = flight.xAt(timeS);
            double z = flight.zAt(timeS);
            double ground = Math.hypot(x - eye.x, z - eye.z);
            double bearing = (Math.toDegrees(Math.atan2(x - eye.x, -(z - eye.z))) + 360.0) % 360.0;
            double elevation = Math.toDegrees(Math.atan2(flight.altitudeM - eye.y, ground));

            double temperature = Isa.temperature(flight.altitudeM);
            double pressure = Isa.pressure(flight.altitudeM);
            double rh = humidity == null ? 0.0
                    : humidity.relativeHumidity(x, z, flight.altitudeM, timeS, rain);
            boolean forms = SchmidtAppleman.formsContrail(temperature, pressure, rh, flight.type.engine);
            boolean persists = SchmidtAppleman.persistenceRatio(temperature, rh) > 1.0;
            Trail trail = SAMPLER.sample(flight, timeS, forms, persists, renderer.trailSettings());

            String verdict = !forms ? "no trail" : (persists ? "persistent" : "short");
            ChatFormatting colour = !forms ? ChatFormatting.DARK_GRAY
                    : (persists ? ChatFormatting.AQUA : ChatFormatting.GRAY);

            source.sendFeedback(Component.literal(String.format(Locale.ROOT,
                    "  %-20s FL%03d  %5.0f km  %03.0f°  %4.1f° up  RH %3.0f%%  %s (%d pts)",
                    flight.type.id, (int) Math.round(flight.altitudeM / 30.48),
                    ground / 1000.0, bearing, elevation, rh * 100.0, verdict,
                    trail.points.size())).withStyle(colour));
        }
        return 1;
    }

    private int probe(CommandContext<FabricClientCommandSource> context, int flightLevel) {
        FabricClientCommandSource source = context.getSource();
        ClientLevel level = source.getLevel();
        double timeS = timeOf(level);
        Vec3 eye = source.getPosition();

        double altitude = Isa.flightLevelToMetres(flightLevel);
        double temperature = Isa.temperature(altitude);
        double pressure = Isa.pressure(altitude);
        HumidityField humidity = renderer.humidity();
        double rh = humidity == null ? 0.0
                : humidity.relativeHumidity(eye.x, eye.z, altitude, timeS,
                        level.getRainLevel(1.0f));
        double overIce = SchmidtAppleman.persistenceRatio(temperature, rh);

        head(source, "Air overhead at FL" + flightLevel);
        field(source, "altitude", String.format(Locale.ROOT, "%.0f m", altitude));
        field(source, "temperature", String.format(Locale.ROOT, "%.1f °C",
                temperature - 273.15));
        field(source, "pressure", String.format(Locale.ROOT, "%.0f hPa", pressure / 100.0));
        field(source, "humidity over water", String.format(Locale.ROOT, "%.0f%%", rh * 100.0));
        field(source, "humidity over ice", String.format(Locale.ROOT, "%.0f%%", overIce * 100.0));

        AircraftType airliner = CATALOG.byId("airliner_narrowbody");
        boolean forms = SchmidtAppleman.formsContrail(temperature, pressure, rh, airliner.engine);
        field(source, "an airliner here", !forms ? "leaves nothing"
                : (overIce > 1.0 ? "leaves a persistent trail" : "leaves a short trail"));
        if (!forms) {
            note(source, "Below roughly FL300 the air is never cold enough, whatever the humidity.");
        }
        return 1;
    }

    private int spawn(CommandContext<FabricClientCommandSource> context, String typeId,
                      int flightLevel, Flight.Condition condition) {
        FabricClientCommandSource source = context.getSource();
        AircraftType type = CATALOG.byId(typeId);
        if (type == null) {
            source.sendError(Component.literal("No such aircraft type: " + typeId));
            return 0;
        }

        ClientLevel level = source.getLevel();
        double timeS = timeOf(level);
        Vec3 eye = source.getPosition();

        int level100 = flightLevel > 0 ? flightLevel
                : (type.minFlightLevel + type.maxFlightLevel) / 2;
        double altitude = Isa.flightLevelToMetres(level100);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        // Comes in from the west so it crosses the sky rather than receding.
        double heading = Math.toRadians(90.0);

        Flight flight = renderer.manualTraffic().add(ManualTraffic.overhead(
                System.nanoTime(), type, eye.x, eye.z, altitude, speed, heading,
                timeS, 12000.0, 420.0, 3600.0, condition));

        head(source, "Spawned " + type.id);
        field(source, "level", "FL" + level100 + String.format(Locale.ROOT,
                " (%.0f m, %.0f blocks above you)", altitude, altitude - eye.y));
        field(source, "speed", String.format(Locale.ROOT, "%.0f m/s", speed));
        field(source, "approaching from", "west, 12 km out, overhead in "
                + Math.round(12000.0 / speed) + " s");
        if (condition != Flight.Condition.NORMAL) {
            field(source, "condition", condition.name().toLowerCase(Locale.ROOT));
        }
        note(source, "Look west and up. Run /overflight list to see whether it is trailing.");
        return 1;
    }

    private int convoy(CommandContext<FabricClientCommandSource> context, int count,
                       String typeId, String formation) {
        FabricClientCommandSource source = context.getSource();
        AircraftType type = CATALOG.byId(typeId);
        if (type == null) {
            source.sendError(Component.literal("No such aircraft type: " + typeId));
            return 0;
        }

        ClientLevel level = source.getLevel();
        double timeS = timeOf(level);
        Vec3 eye = source.getPosition();
        int flightLevel = (type.minFlightLevel + type.maxFlightLevel) / 2;
        double altitude = Isa.flightLevelToMetres(flightLevel);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        List<Flight> flights = FlightRequests.build(type, eye.x, eye.z, flightLevel, 90.0,
                Flight.Condition.NORMAL, count, formation, timeS, System.nanoTime());
        for (int i = 0; i < flights.size(); i++) {
            renderer.manualTraffic().add(flights.get(i));
        }

        head(source, "Convoy of " + count + " " + type.id);
        field(source, "formation", formation.toLowerCase(Locale.ROOT));
        field(source, "level", "FL" + flightLevel);
        note(source, "Coming in from the west, overhead in about "
                + FlightRequests.secondsToOverhead(type, flightLevel) + " s.");
        return 1;
    }

    private int preset(CommandContext<FabricClientCommandSource> context, String name) {
        FabricClientCommandSource source = context.getSource();
        if (!isKnownPreset(name)) {
            source.sendError(Component.literal("Not a preset. Known: "
                    + String.join(", ", PRESETS)));
            return 0;
        }

        OverflightConfig config = renderer.config();
        config.preset = name;
        config.applyPreset().sanitise();
        renderer.applyConfig(config);
        ConfigIo.save(config);

        head(source, "Preset: " + name);
        field(source, "density", String.format(Locale.ROOT, "%.0f flights/hour",
                config.traffic.densityPerHour));
        field(source, "sky supersaturated", String.format(Locale.ROOT, "%.0f%% of it",
                config.atmosphere.supersaturatedFraction * 100.0));
        if (name.equalsIgnoreCase("custom")) {
            note(source, "Nothing was overwritten. Edit the file and run /overflight reload.");
        }
        return 1;
    }

    private int reload(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OverflightConfig config = ConfigIo.load();
        renderer.applyConfig(config);
        head(source, "Reloaded config");
        field(source, "file", ConfigIo.path().toString());
        field(source, "preset", String.valueOf(config.preset));
        field(source, "density", String.format(Locale.ROOT, "%.0f flights/hour",
                config.traffic.densityPerHour));
        return 1;
    }

    private static boolean isKnownPreset(String name) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private int clear(CommandContext<FabricClientCommandSource> context) {
        int removed = renderer.manualTraffic().clear();
        context.getSource().sendFeedback(Component.literal(
                "Removed " + removed + " manual flight" + (removed == 1 ? "" : "s"))
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private int density(CommandContext<FabricClientCommandSource> context, double value) {
        renderer.densityPerHour(value);
        context.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
                "Traffic density set to %.1f flights/hour per 1000x1000 km", value))
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static double timeOf(ClientLevel level) {
        return level.getGameTime() / TICKS_PER_SECOND;
    }

    private static void head(FabricClientCommandSource source, String text) {
        source.sendFeedback(Component.literal(text)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void field(FabricClientCommandSource source, String name, String value) {
        source.sendFeedback(Component.literal("  " + name + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
    }

    private static void note(FabricClientCommandSource source, String text) {
        source.sendFeedback(Component.literal("  " + text).withStyle(ChatFormatting.YELLOW));
    }
}
