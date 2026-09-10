package dev.overflight.fabric;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Not yet the pipeline of its own that 26.x uses.
 *
 * The reasoning in the 26.2 copy of this file applies here too -- no vanilla
 * entity type can draw a contrail without fogging it, shading it grey, or
 * eating its soft edge -- but the way out does not carry over. 1.21.11 has the
 * older pipeline builder, its snippets are private, and RenderType.create takes
 * a composite state rather than a RenderSetup, none of which Fabric's transitive
 * access wideners open up.
 *
 * So this backend keeps the emissive type it had. Trails on it are grey rather
 * than white and fogged at distance, which is wrong but visible, and that beats
 * shipping an untested pipeline into a version nobody has run yet.
 */
final class SkyPipeline {

	private SkyPipeline() {}

	static RenderType of(Identifier texture) {
		return RenderTypes.entityTranslucentEmissive(texture);
	}
}
