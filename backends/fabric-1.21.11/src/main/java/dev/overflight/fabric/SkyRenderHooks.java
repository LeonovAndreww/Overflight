package dev.overflight.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.overflight.core.render.MeshBuffer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Where the sky joins the game's renderer, for 1.21.11.
 *
 * The same two-phase split as on 26.x -- extraction reads the world, drawing
 * only puts finished numbers on screen -- under the names it had before Mojang's
 * rename of world to level carried through Fabric's API.
 *
 * The one real difference is how the drawing is handed over. 26.x collects
 * geometry into a node collector for the renderer to schedule; here the buffer
 * source is open during the event and is written to directly. The vertices are
 * identical, so both call the same emitter.
 */
final class SkyRenderHooks {

	private SkyRenderHooks() {}

	static void register(SkyRenderer renderer) {
		WorldRenderEvents.END_EXTRACTION.register(context -> extract(renderer, context));
		// Before the terrain's own translucency rather than after it, so that
		// water and glass in front of the player still draw over a trail.
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> draw(renderer, context));
	}

	private static void extract(SkyRenderer renderer, WorldExtractionContext context) {
		renderer.extract(context.world(), context.camera(),
				context.tickCounter().getGameTimeDeltaPartialTick(false));
	}

	private static void draw(SkyRenderer renderer, WorldRenderContext context) {
		PoseStack poseStack = context.matrices();
		drawMesh(context, poseStack, renderer.readyAircraft(), SkyRenderer.AIRCRAFT_TEXTURE);
		drawMesh(context, poseStack, renderer.readyTrails(), SkyRenderer.TRAIL_TEXTURE);
	}

	private static void drawMesh(WorldRenderContext context, PoseStack poseStack,
			MeshBuffer mesh, Identifier texture) {
		if (mesh == null || mesh.quadCount() == 0) {
			return;
		}
		RenderType type = SkyRenderer.renderTypeFor(texture);
		SkyRenderer.emit(mesh, poseStack.last(), context.consumers().getBuffer(type));
	}
}
