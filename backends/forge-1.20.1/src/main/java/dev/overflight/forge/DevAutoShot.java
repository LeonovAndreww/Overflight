package dev.overflight.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A development aid: looks at the sky and takes screenshots on its own, so
 * rendering can be checked in a real game without anyone at the keyboard.
 *
 * Does nothing unless a file named {@code overflight-autoshot} sits in the
 * game directory, which only a development run directory ever has. With it,
 * once a world is joined, it sets a busy fancy sky, looks up at it and takes a
 * screenshot with trails drawn the old way and one with them drawn with the
 * sky, looks towards the horizon for a third, and quits.
 */
final class DevAutoShot {
    private static final Logger LOGGER = LoggerFactory.getLogger("overflight-autoshot");
    private static int ticks = -1;
    private static boolean opened;

    private DevAutoShot() {}

    static void installIfAsked() {
        Path flag = Minecraft.getInstance().gameDirectory.toPath().resolve("overflight-autoshot");
        if (!Files.exists(flag)) {
            return;
        }
        LOGGER.info("Autoshot armed");
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                tick(Minecraft.getInstance());
            }
        });
    }

    private static void tick(Minecraft client) {
        SkyRenderer renderer = OverflightClient.renderer();
        // No world yet (a clean CI checkout): make a creative one with cheats from the title screen.
        if (!opened && client.level == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            opened = true;
            net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                    "Autoshot", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                    net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("Autoshot", settings,
                    net.minecraft.world.level.levelgen.WorldOptions.defaultWithRandomSeed(),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions);
            return;
        }
        if (client.player == null || client.level == null || renderer == null) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            renderer.config().preset = "fancy";
            renderer.config().traffic.preset = "busy";
            renderer.config().applyPreset();
            renderer.applyConfig(renderer.config());
            runOnServer(client, "time set " + startTime(client));
            runOnServer(client, "weather clear");
            // Above the trees and the clouds' shadows: the spawn point of a fresh world is often in a forest.
            runOnServer(client, "tp @s ~ 200 ~");
            spawnFlights(client, renderer);
        }
        if (ticks >= 2 && ticks < 330) {
            client.player.setYRot(ticks < 300 ? 0.0f : 90.0f);
            client.player.setXRot(ticks < 300 ? -70.0f : -15.0f);
        }
        if (ticks == 150) {
            renderer.config().graphics.drawInSky = false;
        }
        if (ticks == 220) {
            shoot(client, "1-with-world");
            renderer.config().graphics.drawInSky = true;
        }
        if (ticks == 290) {
            LOGGER.info("Sky hook seen: {}", renderer.skyHookSeen());
            shoot(client, "2-with-sky");
        }
        if (ticks == 360) {
            shoot(client, "3-horizon-with-sky");
        }
        if (ticks == 400) {
            client.stop();
        }
    }

    /** Two airliners crossing close overhead, already trailing, so the shots have something to show. */
    private static void spawnFlights(Minecraft client, SkyRenderer renderer) {
        dev.overflight.core.traffic.AircraftCatalog catalog = dev.overflight.core.traffic.AircraftCatalog.defaults();
        double timeS = client.level.getGameTime() / 20.0;
        String[] ids = {"airliner_narrowbody", "airliner_widebody"};
        double[] headings = {90.0, 100.0};
        for (int i = 0; i < ids.length; i++) {
            dev.overflight.core.traffic.AircraftType type = catalog.byId(ids[i]);
            double altitude = dev.overflight.core.atmo.Isa.flightLevelToMetres(
                    (type.minFlightLevel + type.maxFlightLevel) / 2);
            double speed = type.trueAirspeed(dev.overflight.core.atmo.Isa.temperature(altitude));
            renderer.manualTraffic().add(dev.overflight.core.traffic.ManualTraffic.overhead(
                    System.nanoTime(), type, client.player.getX(), client.player.getZ() + 2000.0 * i, altitude,
                    speed, Math.toRadians(headings[i]), timeS, 3000.0, 420.0, 3600.0,
                    dev.overflight.core.traffic.Flight.Condition.NORMAL));
        }
    }

    /**
     * Runs a command on the integrated server with full permission, so it works in
     * worlds made without cheats (the dev worlds are created with allowCommands=0).
     */
    private static void runOnServer(Minecraft client, String command) {
        net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    /** Game time to shoot at: the flag file may hold a tick number, default 1000. */
    private static String startTime(Minecraft client) {
        try {
            String text = Files.readString(client.gameDirectory.toPath().resolve("overflight-autoshot")).trim();
            if (text.matches("\\d{1,6}")) {
                return text;
            }
        } catch (java.io.IOException ignored) {
            // fall through to the default
        }
        return "1000";
    }

    /** By reflection, since every version names its screenshot method differently. */
    private static void shoot(Minecraft client, String name) {
        LOGGER.info("Autoshot {}", name);
        try {
            try {
                Screenshot.class.getMethod("grab", Minecraft.class, boolean.class)
                        .invoke(null, client, false);
            } catch (NoSuchMethodException older) {
                Object target = Minecraft.class.getMethod("getMainRenderTarget").invoke(client);
                java.util.function.Consumer<Object> ignore = message -> { };
                for (java.lang.reflect.Method method : Screenshot.class.getMethods()) {
                    if (method.getName().equals("grab") && method.getParameterCount() == 3) {
                        method.invoke(null, client.gameDirectory, target, ignore);
                        return;
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Autoshot could not take a screenshot", e);
        }
    }
}
