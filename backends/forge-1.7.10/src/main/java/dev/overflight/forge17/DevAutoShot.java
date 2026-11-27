package dev.overflight.forge17;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development aid, armed only by a file named overflight-autoshot in the game directory (CI creates it):
 * opens a creative world from the title screen, looks up at the sky, takes screenshots and quits.
 */
public final class DevAutoShot {

    private boolean opened;
    private int ticks = -1;
    private int shots;

    public static void installIfAsked() {
        File flag = new File(Minecraft.getMinecraft().mcDataDir, "overflight-autoshot");
        if (flag.exists()) {
            Overflight17.LOG.info("Autoshot armed");
            FMLCommonHandler.instance().bus().register(new DevAutoShot());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!opened && mc.theWorld == null && mc.currentScreen instanceof GuiMainMenu) {
            opened = true;
            mc.launchIntegratedServer("Autoshot", "Autoshot", new WorldSettings(1L, WorldSettings.GameType.CREATIVE, true, false, WorldType.FLAT));
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        ticks++;
        // The window never has focus under Xvfb, which would pop up the pause menu.
        mc.gameSettings.pauseOnLostFocus = false;
        if (ticks >= 60 && mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        mc.gameSettings.hideGUI = true;
        if (ticks == 2) {
            // The flag file may carry a time of day to shoot at.
            net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
            if (server != null) {
                server.worldServers[0].setWorldTime(startTime(mc));
            }
        }
        if (ticks == 20) {
            spawnFlights(mc);
        }
        if (ticks == 30) {
            // Exercise the client command the way a player would.
            int handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight status");
            Overflight17.LOG.info("Command status handled: " + handled);
            handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight spawn airliner_narrowbody 350");
            Overflight17.LOG.info("Command spawn handled: " + handled);
            handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight list");
            Overflight17.LOG.info("Command list handled: " + handled);
            handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight probe 350");
            Overflight17.LOG.info("Command probe handled: " + handled);
            handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight set opacity 0.7");
            Overflight17.LOG.info("Command set handled: " + handled + ", opacity " + SkyRenderer.get().config().trails.opacity);
            handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight preset fancy");
            Overflight17.LOG.info("Command preset handled: " + handled + ", look " + SkyRenderer.get().config().preset);
            handled = net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/overflight get");
            Overflight17.LOG.info("Command get handled: " + handled);
        }
        if (ticks == 45) {
            mc.displayGuiScreen(new ConfigScreen(null));
        }
        if (ticks == 50 && mc.currentScreen instanceof ConfigScreen) {
            Overflight17.LOG.info("Autoshot: config screenshot");
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        }
        if (ticks == 54 && mc.currentScreen instanceof ConfigScreen) {
            // Type a value and press Done the way a player would.
            Overflight17.LOG.info("Config screen result: " + ((ConfigScreen) mc.currentScreen).selfTest("maxAircraft", "37"));
        }
        if (ticks == 40) {
            // The server command, over the integrated server and the mod's own channel back to this client.
            net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
            if (server != null) {
                int ran = server.getCommandManager().executeCommand(server, "airspace spawn airliner_widebody 380 90 smoking");
                Overflight17.LOG.info("Server command handled: " + ran);
            }
        }
        if (ticks == 140) {
            Overflight17.LOG.info("Manual flights: " + SkyRenderer.get().manualTraffic().size());
            Overflight17.LOG.info("Sky quads: " + SkyRenderer.get().lastQuadCount());
        }
        // Wait for chunks, then look straight up and at a slant; two shots, then quit.
        if (ticks == 100) {
            mc.thePlayer.rotationPitch = -80f;
            mc.thePlayer.rotationYaw = 0f;
        }
        if (ticks == 140 || ticks == 180) {
            Overflight17.LOG.info("Autoshot: screenshot " + (++shots));
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            mc.thePlayer.rotationPitch = -30f;
        }
        if (ticks == 220) {
            Overflight17.LOG.info("Autoshot: done");
            mc.shutdown();
        }
    }

    private static long startTime(Minecraft mc) {
        try {
            String text = new String(java.nio.file.Files.readAllBytes(new File(mc.mcDataDir, "overflight-autoshot").toPath()),
                java.nio.charset.StandardCharsets.UTF_8).trim();
            if (text.matches("\\d{1,6}")) {
                return Long.parseLong(text);
            }
        } catch (java.io.IOException ignored) {
            // fall through to the default
        }
        return 1000L;
    }

    /** Two airliners crossing close overhead, already trailing, so the shots have something to show. */
    private static void spawnFlights(Minecraft mc) {
        dev.overflight.core.traffic.AircraftCatalog catalog = dev.overflight.core.traffic.AircraftCatalog.defaults();
        SkyRenderer renderer = SkyRenderer.get();
        double timeS = mc.theWorld.getTotalWorldTime() / 20.0;
        String[] ids = {"airliner_narrowbody", "airliner_widebody"};
        double[] headings = {90.0, 100.0};
        for (int i = 0; i < ids.length; i++) {
            dev.overflight.core.traffic.AircraftType type = catalog.byId(ids[i]);
            double altitude = dev.overflight.core.atmo.Isa.flightLevelToMetres((type.minFlightLevel + type.maxFlightLevel) / 2);
            double speed = type.trueAirspeed(dev.overflight.core.atmo.Isa.temperature(altitude));
            renderer.manualTraffic().add(
                dev.overflight.core.traffic.ManualTraffic.overhead(
                    System.nanoTime(), type, mc.thePlayer.posX, mc.thePlayer.posZ + 2000.0 * i, altitude, speed,
                    Math.toRadians(headings[i]), timeS, 3000.0, 420.0, 3600.0,
                    i == 0 ? dev.overflight.core.traffic.Flight.Condition.NORMAL
                        : dev.overflight.core.traffic.Flight.Condition.SMOKING));
        }
    }
}
