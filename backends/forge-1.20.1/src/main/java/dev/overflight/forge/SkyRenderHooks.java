package dev.overflight.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.overflight.core.render.MeshBuffer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.resources.ResourceLocation;

/**
 * Where the trails join the game's renderer, for 1.20.1.
 *
 * Extraction and drawing both happen on the render thread here, so there is no
 * split to speak of: the world is read at the start of the frame's world
 * render and the mesh is written during the translucent pass, unless the sky
 * hook has already drawn it.
 */
final class SkyRenderHooks {

	private SkyRenderHooks() {}

	static void register(SkyRenderer renderer) {
		// Extraction happens at the head of the level renderer (see the mixin);
		// only the fallback draw is an event.
		MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
			if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
				draw(renderer, event);
			}
		});
	}

	private static void draw(SkyRenderer renderer, RenderLevelStageEvent event) {
		if (renderer.drawsInSky()) {
			return;
		}
		// The vertices are camera-relative, so the camera's rotation goes on the
		// model-view stack for the length of the draw, as in the sky hook.
		com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
		stack.pushPose();
		stack.mulPoseMatrix(event.getPoseStack().last().pose());
		RenderSystem.applyModelViewMatrix();
		try {
			drawMesh(renderer.readyAircraft(), SkyRenderer.AIRCRAFT_TEXTURE);
			drawMesh(renderer.readyTrails(), SkyRenderer.TRAIL_TEXTURE);
			drawMesh(renderer.readyPuffs(), SkyRenderer.PUFF_TEXTURE);
		} finally {
			stack.popPose();
			RenderSystem.applyModelViewMatrix();
		}
	}

	private static void drawMesh(MeshBuffer mesh, ResourceLocation texture) {
		SkyDraw.draw(mesh, texture, true);
	}
}
