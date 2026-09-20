package dev.overflight.fabric;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The pipeline the sky is drawn through when no shader pack is, for 26.1.
 *
 * The same reasoning as the 26.2 copy: no vanilla entity type can draw a
 * contrail, because they all run core/entity, which applies fog, discards the
 * faint edge a trail is mostly made of, and shades it grey. This runs our own
 * copy of that shader with exactly those three removed.
 *
 * It is a separate file only because 26.1's pipeline builder is the older one:
 * bind groups and the primitive topology are not set here, they come with the
 * entity snippet.
 */
final class SkyPipeline {

	/** How a variant treats the depth buffer. */
	enum Depth { OFF, TEST, WRITE }

	private static final Map<Depth, RenderPipeline> PIPELINES = new HashMap<>();
	private static final Map<String, RenderType> TYPES = new HashMap<>();

	static {
		for (Depth depth : Depth.values()) {
			PIPELINES.put(depth, build(depth));
		}
	}

	private SkyPipeline() {}

	private static RenderPipeline build(Depth depth) {
		Identifier shader = Identifier.fromNamespaceAndPath(
				OverflightClient.MOD_ID, "core/contrail");

		RenderPipeline.Builder builder =
				RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
						.withLocation(Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID,
								"pipeline/contrail_" + depth.name().toLowerCase()))
						.withVertexShader(shader)
						.withFragmentShader(shader)
						.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
						// Both sides: our quads face the camera but are wound the
						// other way round, and a contrail is seen from both sides
						// in any case.
						.withCull(false);

		switch (depth) {
			case OFF:
				builder.withDepthStencilState(Optional.empty());
				break;
			case TEST:
				// Reverse depth, so "greater or equal" means "nearer or equal".
				builder.withDepthStencilState(
						new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
				break;
			case WRITE:
			default:
				builder.withDepthStencilState(DepthStencilState.DEFAULT);
				break;
		}
		return RenderPipelines.register(builder.build());
	}

	/** Cached: a fresh render type every frame is a fresh draw group every frame. */
	static RenderType of(Identifier texture, Depth depth) {
		String key = depth.name() + ' ' + texture;
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		RenderType type = RenderType.create("overflight_contrail_" + key,
				RenderSetup.builder(PIPELINES.get(depth))
						.withTexture("Sampler0", texture)
						.useLightmap()
						.sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}
}
