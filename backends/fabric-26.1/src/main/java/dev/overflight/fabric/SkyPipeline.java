package dev.overflight.fabric;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
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
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
					.withCull(false)
					.withDepthStencilState(Optional.empty())
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
				RenderSetup.builder(Built.PIPELINES.get(depth))
						.withTexture("Sampler0", texture)
						.useLightmap()
						.sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}
}
