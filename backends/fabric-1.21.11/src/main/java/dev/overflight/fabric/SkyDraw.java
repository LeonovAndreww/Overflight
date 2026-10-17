package dev.overflight.fabric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.overflight.core.render.MeshBuffer;
import net.minecraft.resources.Identifier;

/**
 * Draws finished sky geometry during the game's sky rendering. This version
 * can still draw a mesh through its render type, which sets up the pass, the
 * transforms and the texture itself.
 */
public final class SkyDraw {
	private static ByteBufferBuilder bytes;

	private SkyDraw() {}

	/** @param target unused here: the render type draws to the main target */
	public static void draw(RenderTarget target, MeshBuffer mesh, Identifier texture) {
		if (mesh == null || mesh.quadCount() == 0) {
			return;
		}
		if (bytes == null) {
			bytes = new ByteBufferBuilder(1 << 18);
		}
		BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS,
				DefaultVertexFormat.POSITION_TEX_COLOR);
		SkyRenderer.emitSky(mesh, builder);
		MeshData data = builder.build();
		if (data != null) {
			SkyPipeline.skyType(texture).draw(data);
		}
	}
}
