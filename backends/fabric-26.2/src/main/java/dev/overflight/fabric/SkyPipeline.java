package dev.overflight.fabric;

import com.mojang.blaze3d.PrimitiveTopology;
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
 * The pipeline the sky is drawn through when no shader pack is.
 *
 * No vanilla entity type can draw a contrail. They all run core/entity, which
 * applies fog unconditionally, discards fragments fainter than ALPHA_CUTOUT, and
 * shades by normal unless the pipeline carries NO_CARDINAL_LIGHTING. A trail
 * wants none of the three: fog paints it the colour of the sky it is meant to
 * stand against, the cutout eats the soft edge it is mostly made of, and the
 * shading floors it at 0.4 grey.
 *
 * So this runs a copy of core/entity with exactly those three removed, built on
 * the entity snippet so that everything else about it -- vertex format, the
 * texture and lightmap bind group -- matches the types that are known to draw.
 *
 * What made this take several attempts is the culling, below. Every vanilla type
 * that drew our geometry turns it off; the eyes type and two earlier versions of
 * this pipeline left it on, and those three are the only things that have drawn
 * nothing at all.
 */
final class SkyPipeline {

	/** How a variant treats the depth buffer. */
	enum Depth {
		/** No depth state at all: nothing can hide a trail. */
		OFF,
		/** Test but do not write, so terrain hides a trail and trails blend freely. */
		TEST,
		/** Test and write, as every vanilla entity type does. */
		WRITE,
	}

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
						.withPrimitiveTopology(PrimitiveTopology.QUADS)
						// Draw both sides. Every vanilla translucent entity type
						// turns culling off, and the ones that leave it on are
						// exactly the ones that drew nothing here: our quads face
						// the camera but are wound the other way round, so with
						// culling on every one of them is discarded. A contrail is
						// seen from both sides in any case.
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

	/**
	 * The render type for one texture.
	 *
	 * Cached, because the renderer groups geometry by render type and a fresh
	 * object every frame is a fresh group every frame. Vanilla memoises its own
	 * accessors for the same reason.
	 */
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
						// Back to front, since one trail can lie behind another.
						.sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}
}
