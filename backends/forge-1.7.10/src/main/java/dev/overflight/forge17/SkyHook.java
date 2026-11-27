package dev.overflight.forge17;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;

/** Called from the sky mixin, at the end of the game's own sky pass: extracts the frame and draws it. */
public final class SkyHook {

    private static boolean logged;
    private static boolean failed;

    private SkyHook() {}

    public static boolean seen() {
        return logged;
    }

    public static void onSkyEnd(float partialTicks) {
        if (!logged) {
            logged = true;
            Overflight17.LOG.info("Sky hook seen: true");
        }
        if (failed) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            SkyRenderer renderer = SkyRenderer.get();
            renderer.extract(Minecraft.getMinecraft(), partialTicks);
            if (!renderer.config().graphics.drawInSky) {
                return;
            }
            SkyDraw.begin();
            SkyDraw.draw(renderer.aircraft(), SkyRenderer.AIRCRAFT_TEXTURE);
            SkyDraw.draw(renderer.trails(), SkyRenderer.TRAIL_TEXTURE);
            SkyDraw.draw(renderer.puffs(), SkyRenderer.PUFF_TEXTURE);
        } catch (RuntimeException | LinkageError e) {
            // A broken frame must not take the game down; say so once and stop drawing.
            failed = true;
            Overflight17.LOG.error("Overflight sky rendering failed and is switched off", e);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
