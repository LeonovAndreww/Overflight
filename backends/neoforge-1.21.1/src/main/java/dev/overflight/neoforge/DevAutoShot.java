package dev.overflight.neoforge;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
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

    private DevAutoShot() {}

    static void installIfAsked() {
        Path flag = Minecraft.getInstance().gameDirectory.toPath().resolve("overflight-autoshot");
        if (!Files.exists(flag)) {
            return;
        }
        LOGGER.info("Autoshot armed");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        SkyRenderer renderer = OverflightClient.renderer();
        if (client.player == null || client.level == null || renderer == null) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            renderer.config().preset = "fancy";
            renderer.config().traffic.preset = "busy";
            renderer.config().applyPreset();
            renderer.applyConfig(renderer.config());
            client.player.connection.sendCommand("time set " + startTime(client));
            client.player.connection.sendCommand("weather clear");
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
