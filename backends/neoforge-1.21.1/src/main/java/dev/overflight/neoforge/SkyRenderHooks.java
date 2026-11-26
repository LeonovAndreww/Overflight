package dev.overflight.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.overflight.core.render.MeshBuffer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;

/**
 * Where the trails join the game's renderer, for 1.21.1.
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
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
			if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
				draw(renderer, event.getModelViewMatrix());
			}
		});
	}

	private static void draw(SkyRenderer renderer, org.joml.Matrix4f modelView) {
		if (renderer.drawsInSky()) {
			return;
		}
		// The vertices are camera-relative, so the camera's rotation goes on the
		// model-view stack for the length of the draw, as in the sky hook.
		org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
		stack.pushMatrix();
		stack.mul(modelView);
		RenderSystem.applyModelViewMatrix();
		try {
			drawMesh(renderer.readyAircraft(), SkyRenderer.AIRCRAFT_TEXTURE);
			drawMesh(renderer.readyTrails(), SkyRenderer.TRAIL_TEXTURE);
			drawMesh(renderer.readyPuffs(), SkyRenderer.PUFF_TEXTURE);
		} finally {
			stack.popMatrix();
			RenderSystem.applyModelViewMatrix();
		}
	}

	private static void drawMesh(MeshBuffer mesh, ResourceLocation texture) {
		SkyDraw.draw(mesh, texture, true);
	}
}
