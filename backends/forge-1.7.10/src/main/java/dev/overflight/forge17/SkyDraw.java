package dev.overflight.forge17;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import dev.overflight.core.render.MeshBuffer;

/** Draws finished sky geometry with the fixed-function pipeline: textured, blended, no depth, no fog, no culling. */
final class SkyDraw {

    private SkyDraw() {}

    /** The caller keeps the GL attribute stack; this only sets the state the meshes need. */
    static void begin() {
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    static void draw(MeshBuffer mesh, ResourceLocation texture) {
        if (mesh == null || mesh.quadCount() == 0) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        float[] positions = mesh.positions();
        float[] uvs = mesh.uvs();
        float[] colours = mesh.colours();
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int v = 0; v < mesh.vertexCount(); v++) {
            int p = v * 3;
            int u = v * 2;
            int c = v * 4;
            t.setColorRGBA_F(colours[c], colours[c + 1], colours[c + 2], colours[c + 3]);
            t.addVertexWithUV(positions[p], positions[p + 1], positions[p + 2], uvs[u], uvs[u + 1]);
        }
        t.draw();
    }
}
