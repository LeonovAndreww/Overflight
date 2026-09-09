package dev.overflight.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.overflight.core.render.MeshBuffer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Where the sky joins the game's renderer, for 26.1 and 26.2.
 *
 * This is the only file that names the render events, and it exists so that it
 * can be the only file a new game version has to replace. Everything about what
 * the sky looks like is in {@link SkyRenderer} and in the core, neither of which
 * mentions a version.
 *
 * The split into two phases is the game's: extraction reads the world, drawing
 * only hands finished numbers to a vertex consumer, and the two can run on
 * different threads.
 */
final class SkyRenderHooks {

	private SkyRenderHooks() {}

	static void register(SkyRenderer renderer) {
		// Through LevelRenderEvents rather than LevelExtractionEvents. Both
		// versions of the API keep the field here and only differ in which
		// interface it is typed to, and since the two interfaces declare the same
		// method, a lambda is inferred against either. That one detail is what
		// lets 26.1 and 26.2 share this file too.
		LevelRenderEvents.END_EXTRACTION.register(context -> extract(renderer, context));
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> submit(renderer, context));
	}

	private static void extract(SkyRenderer renderer, LevelExtractionContext context) {
		renderer.extract(context.level(), context.camera(),
				context.deltaTracker().getGameTimeDeltaPartialTick(false));
	}

	private static void submit(SkyRenderer renderer, LevelRenderContext context) {
		PoseStack poseStack = context.poseStack();
		submitMesh(context, poseStack, renderer.readyAircraft(), SkyRenderer.AIRCRAFT_TEXTURE);
		submitMesh(context, poseStack, renderer.readyTrails(), SkyRenderer.TRAIL_TEXTURE);
	}

	private static void submitMesh(LevelRenderContext context, PoseStack poseStack,
			MeshBuffer mesh, Identifier texture) {
		if (mesh == null || mesh.quadCount() == 0) {
			return;
		}
		RenderType type = SkyRenderer.renderTypeFor(texture);
		context.submitNodeCollector().submitCustomGeometry(poseStack, type,
				(pose, consumer) -> SkyRenderer.emit(mesh, pose, consumer));
	}
}
