package dev.overflight.fabric;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The render type the ordinary (non-sky) path draws through on 1.21.1.
 *
 * Unlike 26.x this version has no pipeline objects to build, and the vanilla
 * "eyes" type is exactly what a contrail needs: ordinary translucency, no
 * cardinal lighting, no fog, no cull. So it is used as is.
 */
final class SkyPipeline {

	/** How a variant treats the depth buffer; kept for the shared diagnostics. */
	enum Depth {
		OFF,
		TEST,
		WRITE,
	}

	private SkyPipeline() {}

	static void register() {}

	static RenderType of(ResourceLocation texture, Depth depth) {
		return RenderType.eyes(texture);
	}
}
