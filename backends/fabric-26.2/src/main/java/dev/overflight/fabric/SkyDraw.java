package dev.overflight.fabric;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import dev.overflight.core.render.MeshBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws finished sky geometry during the game's sky rendering.
 *
 * 26.2 has no way to draw a mesh outside the submission system, so this does
 * what the game does for the sun: its own render pass on the main target,
 * the transforms, the texture, a vertex buffer and the shared quad indices.
 */
public final class SkyDraw {
    /** Buffers handed to the GPU, freed a few frames later rather than at once. */
    private static final ArrayDeque<GpuBuffer> IN_FLIGHT = new ArrayDeque<>();
    private static final int KEEP = 12;
    private static ByteBufferBuilder bytes;

    private SkyDraw() {}

    /** @param target the render target the game is drawing its sky into */
    public static void draw(RenderTarget target, MeshBuffer mesh, Identifier texture) {
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
            GpuDevice device = RenderSystem.getDevice();
            GpuBuffer vertices = device.createBuffer(() -> "Overflight sky vertices",
                    GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
            int indexCount = data.drawState().indexCount();
            RenderSystem.AutoStorageIndexBuffer quads =
                    RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indices = quads.getBuffer(indexCount);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    new Matrix4f(RenderSystem.getModelViewStack()), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

            Minecraft client = Minecraft.getInstance();
            AbstractTexture image = client.getTextureManager().getTexture(texture);
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Overflight sky", target.getColorTextureView(), Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(SkyPipeline.sky());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", image.getTextureView(), image.getSampler());
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indices, quads.type());
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
            IN_FLIGHT.addLast(vertices);
            while (IN_FLIGHT.size() > KEEP) {
                IN_FLIGHT.removeFirst().close();
            }
        }
    }
}
