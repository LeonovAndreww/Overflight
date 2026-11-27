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
}
