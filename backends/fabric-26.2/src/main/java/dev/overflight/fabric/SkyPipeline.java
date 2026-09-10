package dev.overflight.fabric;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
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
 * Every vanilla entity pipeline was tried against a live game, and none can draw
 * a contrail. They all run core/entity, which applies fog unconditionally,
 * discards fragments fainter than ALPHA_CUTOUT, and shades by normal unless the
 * pipeline carries NO_CARDINAL_LIGHTING. A trail wants none of the three: fog
 * paints it the colour of the sky it is meant to stand against, the cutout eats
 * the soft edge it is mostly made of, and the shading floors it at 0.4 grey.
 *
 * So this runs core/position_tex_color instead, which is a texture multiplied by
 * a vertex colour and nothing else. That is the same choice the Contrail mod
 * made on 1.20.4, where it drove a position/texture/colour buffer by hand, and
 * the same one vanilla makes for END_SKY today.
 *
 * The depth state is left switchable because it is the one part that could not
 * be settled by reading the game. Two things have failed to draw so far, the
 * eyes render type and the first version of this pipeline, and the only property
 * they share is a depth state that tests without writing.
 */
final class SkyPipeline {

	/** How a variant treats the depth buffer. */
	enum Depth {
		/** No depth state at all, as the sky itself is drawn: nothing can hide a trail. */
		OFF,
		/** Test but do not write, so terrain hides a trail and trails blend together. */
		TEST,
		/** Test and write, which is what every vanilla type that draws us does. */
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
		RenderPipeline.Builder builder =
				RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
						.withLocation(Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID,
								"pipeline/sky_" + depth.name().toLowerCase()))
						.withVertexShader("core/position_tex_color")
						.withFragmentShader("core/position_tex_color")
						.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
						.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
						.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
						.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
						.withPrimitiveTopology(PrimitiveTopology.QUADS);

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
	 * object every frame is a fresh group every frame. Vanilla's own accessors
	 * are memoised for the same reason.
	 */
	static RenderType of(Identifier texture, Depth depth) {
		String key = depth.name() + ' ' + texture;
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		RenderType type = RenderType.create("overflight_sky_" + key,
				RenderSetup.builder(PIPELINES.get(depth))
						.withTexture("Sampler0", texture)
						// Back to front, since one trail can lie behind another.
						.sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}
}
