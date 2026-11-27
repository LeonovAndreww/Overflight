package dev.overflight.forge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.overflight.forge.OverflightClient;
import dev.overflight.forge.SkyDraw;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the trails with the sky, at the very end of the game's sky rendering,
 * so that whatever is drawn over the sun is drawn over them too.
 *
 * The 1.20.1 form: the sky is a method of the level renderer, and the camera's
 * rotation arrives as an argument rather than being already applied, so it is
 * put on the model-view stack for the length of the draw.
 */
@Mixin(LevelRenderer.class)
abstract class SkyRendererMixin {

	@Shadow
	private net.minecraft.client.multiplayer.ClientLevel level;

	/** The start of the frame's world render: read the sky once, here. */
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void overflight$extract(com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick,
			long finishNanoTime, boolean renderBlockOutline, Camera camera,
			net.minecraft.client.renderer.GameRenderer gameRenderer,
			net.minecraft.client.renderer.LightTexture lightTexture, Matrix4f projectionMatrix,
			CallbackInfo info) {
		dev.overflight.forge.SkyRenderer renderer = OverflightClient.renderer();
		if (renderer != null && level != null) {
			renderer.extract(level, camera, partialTick);
		}
	}

	@Inject(method = "renderSky", at = @At("TAIL"))
	private void overflight$drawTrails(com.mojang.blaze3d.vertex.PoseStack poseStack, Matrix4f projectionMatrix,
			float partialTick, Camera camera, boolean foggy, Runnable skyFogSetup,
			CallbackInfo info) {
		dev.overflight.forge.SkyRenderer renderer = OverflightClient.renderer();
		if (renderer == null) {
			return;
		}
		com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
		stack.pushPose();
		stack.mulPoseMatrix(poseStack.last().pose());
		RenderSystem.applyModelViewMatrix();
		try {
			renderer.drawSky((mesh, texture) -> SkyDraw.draw(mesh, texture));
		} finally {
			stack.popPose();
			RenderSystem.applyModelViewMatrix();
		}
	}
}
