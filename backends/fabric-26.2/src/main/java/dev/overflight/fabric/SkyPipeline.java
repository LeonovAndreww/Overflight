package dev.overflight.fabric;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * The pipeline the sky is drawn through when no shader pack is.
 *
 * Every vanilla entity pipeline was tried against a live game first, and none of
 * them can draw a contrail. They all run core/entity, and that shader does three
 * things to a trail that a trail must not have:
 *
 * <ul>
 * <li>it applies fog unconditionally -- apply_fog is not behind any define -- so
 *     geometry beyond the fog end arrives painted the colour of the sky behind
 *     it, and the shell has to be dragged in close to escape that, which then
 *     puts trails in front of far terrain drawn by Voxy or Distant Horizons;
 * <li>it shades by normal unless the pipeline carries NO_CARDINAL_LIGHTING,
 *     which floors a white trail at 0.4 grey;
 * <li>it discards fragments where the texture is fainter than ALPHA_CUTOUT,
 *     which eats the soft edge a contrail is mostly made of.
 * </ul>
 *
 * The one vanilla pipeline free of all three is END_SKY, which runs
 * core/position_tex_color: a texture multiplied by a vertex colour and nothing
 * else. That is also, in older form, exactly what the Contrail mod used on
 * 1.20.4 -- a position/texture/colour format drawn with blending switched on by
 * hand.
 *
 * This is END_SKY with one thing added. The sky pipeline does not test depth,
 * because the sky is behind everything by definition; a trail is not, and one
 * drawn over a mountain looks wrong. Testing depth without writing it lets
 * terrain hide a trail -- including terrain a level-of-detail mod drew -- while
 * still letting trails blend with each other.
 */
final class SkyPipeline {

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(
							OverflightClient.MOD_ID, "pipeline/sky"))
					.withVertexShader("core/position_tex_color")
					.withFragmentShader("core/position_tex_color")
					.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
					.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					// Test against what is already there, write nothing back.
					// Reverse depth, so "greater or equal" is "nearer or equal".
					.withDepthStencilState(
							new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	private SkyPipeline() {}

	static RenderType of(Identifier texture) {
		return RenderType.create("overflight_sky", RenderSetup.builder(PIPELINE)
				.withTexture("Sampler0", texture)
				// Back to front, since one trail can lie behind another.
				.sortOnUpload()
				.createRenderSetup());
	}
}
