package dev.overflight.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.overflight.core.render.MeshBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;

/**
 * Draws finished sky geometry into the game's own sky render pass, the way
 * the game draws the sun into it: the pipeline, the transforms, the texture,
 * a vertex buffer and the shared quad indices.
 */
public final class SkyDraw {
	/** Buffers handed to the GPU, freed a few frames later rather than at once. */
	private static final ArrayDeque<GpuBuffer> IN_FLIGHT = new ArrayDeque<>();
	private static final int KEEP = 12;
	private static ByteBufferBuilder bytes;

	private SkyDraw() {}

	public static void draw(RenderPass pass, MeshBuffer mesh, Identifier texture) {
		if (mesh == null || mesh.quadCount() == 0) {
			return;
		}
		if (bytes == null) {
			bytes = new ByteBufferBuilder(1 << 18);
		}
		BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS,
				DefaultVertexFormat.POSITION_TEX_COLOR);
		SkyRenderer.emitSky(mesh, builder);
		try (MeshData data = builder.build()) {
			if (data == null) {
				return;
			}
			GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
					() -> "Overflight sky vertices", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
			int indexCount = data.drawState().indexCount();
			RenderSystem.AutoStorageIndexBuffer quads =
					RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indices = quads.getBuffer(indexCount);
			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
					new Matrix4f(RenderSystem.getModelViewStack()), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
			AbstractTexture image = Minecraft.getInstance().getTextureManager().getTexture(texture);

			pass.pushDebugGroup(() -> "Overflight sky");
			pass.setPipeline(RenderSystem.getCompiledPipeline(SkyPipeline.sky()));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("Sampler0", image.getTextureView(), image.getSampler());
			pass.setVertexBuffer(0, vertices.slice());
			pass.setIndexBuffer(indices, quads.type());
			pass.drawIndexed(indexCount, 1, 0, 0, 0);
			pass.popDebugGroup();

			IN_FLIGHT.addLast(vertices);
			while (IN_FLIGHT.size() > KEEP) {
				IN_FLIGHT.removeFirst().close();
			}
		}
	}
}
