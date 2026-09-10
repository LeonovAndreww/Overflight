package dev.overflight.fabric;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Not yet the pipeline of its own that 26.2 uses.
 *
 * The reasoning in the 26.2 copy of this file applies here too, but 26.1 sits on
 * the intermediate pipeline builder -- withSampler and withVertexFormat rather
 * than bind group layouts and vertex bindings -- so the same pipeline has to be
 * written again rather than shared.
 *
 * It is worth writing once 26.2 has been seen working and not before, so this
 * backend keeps the emissive type it had.
 */
final class SkyPipeline {

	private SkyPipeline() {}

	static RenderType of(Identifier texture) {
		return RenderTypes.entityTranslucentEmissive(texture);
	}
}
