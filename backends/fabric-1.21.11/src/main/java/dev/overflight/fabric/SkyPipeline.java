package dev.overflight.fabric;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The 1.21.11 form of the 26.2 pipeline: see that copy for why no vanilla
 * entity type can draw a contrail, and why culling is off.
 *
 * Two things differ. The pipeline builder is the older one, which sets depth
 * and blending through separate calls rather than state objects. And depth is
 * not reversed yet, so "nearer or equal" is LEQUAL rather than GREATER.
 * Samplers, vertex format and topology all come from the entity snippet.
 */
final class SkyPipeline {

	/** How a variant treats the depth buffer. */
	enum Depth {
		/** No depth test at all: nothing can hide a trail. */
		OFF,
		/** Test but do not write, so terrain hides a trail and trails blend freely. */
		TEST,
		/** Test and write, as every vanilla entity type does. */
		WRITE,
	}

	private static final Map<String, RenderType> TYPES = new HashMap<>();

	/** Built on first use; see {@link #register()}. */
	private static final class Built {
		static final Map<Depth, RenderPipeline> PIPELINES = new HashMap<>();

		static {
			for (Depth depth : Depth.values()) {
				PIPELINES.put(depth, build(depth));
			}
		}
	}

	private static RenderPipeline sky;
	private static final Map<Identifier, RenderType> SKY_TYPES = new HashMap<>();

	/**
	 * The pipeline the sky phase draws through: the sun's own, the game's
	 * position-texture-colour shader, with ordinary blending and no depth at
	 * all, since nothing has been drawn yet that a trail could be behind.
	 * Handed to Iris as textured sky, so a shader pack treats trails like the
	 * sun and moon.
	 */
	static RenderPipeline sky() {
		if (sky == null) {
			sky = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID, "pipeline/contrail_sky"))
					.withVertexShader("core/position_tex_color")
					.withFragmentShader("core/position_tex_color")
					.withSampler("Sampler0")
					.withBlend(BlendFunction.TRANSLUCENT)
					.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
					.withCull(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.build());
			ShaderPacks.assignSkyProgram(sky);
		}
		return sky;
	}

	/** The sky-phase render type for one texture. */
	static RenderType skyType(Identifier texture) {
		RenderType cached = SKY_TYPES.get(texture);
		if (cached == null) {
			cached = RenderType.create("overflight_sky " + texture,
					RenderSetup.builder(sky()).withTexture("Sampler0", texture).createRenderSetup());
			SKY_TYPES.put(texture, cached);
		}
		return cached;
	}

	private SkyPipeline() {}

	/**
	 * Does nothing in this version, on purpose. In 26.3 a pipeline registered at
	 * startup is one the game requires, and a shader failing there stops the
	 * whole game loading; 26.3 can mark them optional, and does. This version
	 * has no such mark, so its pipelines stay registered on first use, which is
	 * how they have always been run.
	 */
	static void register() {}

	private static RenderPipeline build(Depth depth) {
		Identifier shader = Identifier.fromNamespaceAndPath(
				OverflightClient.MOD_ID, "core/contrail");

		RenderPipeline.Builder builder =
				RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
						.withLocation(Identifier.fromNamespaceAndPath(OverflightClient.MOD_ID,
								"pipeline/contrail_" + depth.name().toLowerCase()))
						.withVertexShader(shader)
						.withFragmentShader(shader)
						.withBlend(BlendFunction.TRANSLUCENT)
						// Both sides, for the reason given in the 26.2 copy.
						.withCull(false);

		switch (depth) {
			case OFF:
				builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
						.withDepthWrite(false);
				break;
			case TEST:
				builder.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
						.withDepthWrite(false);
				break;
			case WRITE:
			default:
				builder.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
						.withDepthWrite(true);
				break;
		}
		return RenderPipelines.register(builder.build());
	}

	/** The render type for one texture, cached as in the 26.2 copy. */
	static RenderType of(Identifier texture, Depth depth) {
		String key = depth.name() + ' ' + texture;
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		RenderType type = RenderType.create("overflight_contrail_" + key,
				RenderSetup.builder(Built.PIPELINES.get(depth))
						.withTexture("Sampler0", texture)
						.useLightmap()
						// Back to front, since one trail can lie behind another.
						.sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}
}
