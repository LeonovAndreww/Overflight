package dev.overflight.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.overflight.core.render.MeshBuffer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws finished sky geometry during the game's sky rendering, the way 1.20.1
 * draws its own sun: the position-texture-colour shader, ordinary blending, no
 * depth. The caller has already put the camera's rotation on the model-view
 * stack, since the vertices are camera-relative.
 */
public final class SkyDraw {

	private SkyDraw() {}

	public static void draw(MeshBuffer mesh, ResourceLocation texture) {
		draw(mesh, texture, false);
	}

	/** @param depthTest whether terrain in front hides the mesh (the non-sky path) */
	public static void draw(MeshBuffer mesh, ResourceLocation texture, boolean depthTest) {
		if (mesh == null || mesh.quadCount() == 0) {
			return;
		}
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		if (depthTest) {
			RenderSystem.enableDepthTest();
		} else {
			RenderSystem.disableDepthTest();
		}
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		BufferBuilder builder = Tesselator.getInstance().getBuilder();
		builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		SkyRenderer.emitSky(mesh, builder);
		BufferUploader.drawWithShader(builder.end());
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}
}
