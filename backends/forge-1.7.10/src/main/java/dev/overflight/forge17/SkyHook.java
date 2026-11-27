package dev.overflight.forge17;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.Tessellator;

/** Called from the sky mixin. Milestone 1: proves the hook runs by drawing a marker quad. */
public final class SkyHook {

    private static boolean logged;

    private SkyHook() {}

    public static void onSkyEnd(float partialTicks) {
        if (!logged) {
            logged = true;
            Overflight17.LOG.info("Sky hook seen: true");
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(1f, 0f, 0f, 1f);
        t.addVertex(-30, 100, -30);
        t.addVertex(-30, 100, 30);
        t.addVertex(30, 100, 30);
        t.addVertex(30, 100, -30);
        t.draw();
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
