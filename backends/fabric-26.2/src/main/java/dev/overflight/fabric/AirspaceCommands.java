package dev.overflight.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Server commands: the sky as everyone sees it.
 *
 * Deliberately a different root from the client's own {@code /overflight}. That
 * one answers for the player who typed it and works on any server, vanilla
 * included; this one puts an aircraft over everybody. Sharing a root would mean
 * one of the two silently shadowing the other depending on what the server
 * happened to have installed.
 *
 * Nothing about the ambient sky is sent -- that already agrees everywhere. Only
 * the request for a hand-placed flight travels.
 */
public final class AirspaceCommands {
    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
    /**
     * The same bar as /time or /weather. 26.2 replaced numeric permission levels
     * with named checks, and gamemaster is where the old level two landed.
     */
    private static final net.minecraft.server.permissions.PermissionCheck PERMISSION =
            Commands.LEVEL_GAMEMASTERS;

    private AirspaceCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, selection) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("airspace")
                .requires(Commands.hasPermission(PERMISSION))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (AircraftType type : CATALOG.types()) {
                                        builder.suggest(type.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> spawn(context,
                                        StringArgumentType.getString(context, "type"),
                                        0, 90.0, Flight.Condition.NORMAL))
                                .then(Commands.argument("fl", IntegerArgumentType.integer(50, 700))
                                        .executes(context -> spawn(context,
                                                StringArgumentType.getString(context, "type"),
                                                IntegerArgumentType.getInteger(context, "fl"),
                                                90.0, Flight.Condition.NORMAL))
                                        .then(Commands.argument("heading",
                                                        DoubleArgumentType.doubleArg(0.0, 360.0))
                                                .executes(context -> spawn(context,
                                                        StringArgumentType.getString(context, "type"),
                                                        IntegerArgumentType.getInteger(context, "fl"),
                                                        DoubleArgumentType.getDouble(context, "heading"),
                                                        Flight.Condition.NORMAL))
                                                .then(Commands.literal("smoking")
                                                        .executes(context -> spawn(context,
                                                                StringArgumentType.getString(context, "type"),
                                                                IntegerArgumentType.getInteger(context, "fl"),
                                                                DoubleArgumentType.getDouble(context, "heading"),
                                                                Flight.Condition.SMOKING)))
                                                .then(Commands.literal("burning")
                                                        .executes(context -> spawn(context,
                                                                StringArgumentType.getString(context, "type"),
                                                                IntegerArgumentType.getInteger(context, "fl"),
                                                                DoubleArgumentType.getDouble(context, "heading"),
                                                                Flight.Condition.BURNING)))))))
                .then(Commands.literal("convoy")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (AircraftType type : CATALOG.types()) {
                                                builder.suggest(type.id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> convoy(context,
                                                IntegerArgumentType.getInteger(context, "count"),
                                                StringArgumentType.getString(context, "type"),
                                                "vee"))
                                        .then(Commands.argument("formation", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest("line");
                                                    builder.suggest("vee");
                                                    builder.suggest("echelon");
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> convoy(context,
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        StringArgumentType.getString(context, "type"),
                                                        StringArgumentType.getString(context, "formation")))))))
                .then(Commands.literal("clear").executes(AirspaceCommands::clear)));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String typeId,
                             int flightLevel, double heading, Flight.Condition condition) {
        return send(context, typeId, flightLevel, heading, condition, 1, "line",
                "Put a " + typeId + " over the sky");
    }

    private static int convoy(CommandContext<CommandSourceStack> context, int count,
                              String typeId, String formation) {
        return send(context, typeId, 0, 90.0, Flight.Condition.NORMAL, count, formation,
                "Put a " + formation + " of " + count + " " + typeId + " over the sky");
    }

    private static int send(CommandContext<CommandSourceStack> context, String typeId,
                            int flightLevel, double heading, Flight.Condition condition,
                            int count, String formation, String message) {
        CommandSourceStack source = context.getSource();
        if (CATALOG.byId(typeId) == null) {
            source.sendFailure(Component.literal("No such aircraft type: " + typeId));
            return 0;
        }

        Vec3 at = source.getPosition();
        AirspacePayload payload = new AirspacePayload(AirspacePayload.ACTION_SPAWN, typeId,
                at.x, at.z, flightLevel, heading, condition.ordinal(), count, formation);
        int reached = broadcast(source.getServer(), payload);

        source.sendSuccess(() -> Component.literal(message + " for " + reached
                + " player" + (reached == 1 ? "" : "s")), true);
        if (reached == 0) {
            source.sendSuccess(() -> Component.literal(
                    "Nobody on this server has Overflight installed, so nobody will see it."),
                    false);
        }
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int reached = broadcast(source.getServer(), AirspacePayload.clearAll());
        source.sendSuccess(() -> Component.literal(
                "Cleared hand-placed aircraft for " + reached + " player"
                        + (reached == 1 ? "" : "s")), true);
        return 1;
    }

    /** Sends to everyone whose client can actually receive it. */
    private static int broadcast(MinecraftServer server, AirspacePayload payload) {
        if (server == null) {
            return 0;
        }
        Collection<ServerPlayer> players = PlayerLookup.all(server);
        int reached = 0;
        for (ServerPlayer player : players) {
            if (ServerPlayNetworking.canSend(player, AirspacePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
                reached++;
            }
        }
        return reached;
    }
}
